package net.clementlevallois.nocodeapp.web.front.backingbeans;

/**
 *
 * @author LEVALLOIS
 */
import jakarta.inject.Inject;
import java.io.Serializable;
import java.util.Locale;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;
import jakarta.servlet.http.HttpServletRequest;
import net.clementlevallois.nocodeapp.web.front.http.SendReport;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;

@Named
@SessionScoped
public class FeedbackBean implements Serializable {

    private static final long serialVersionUID = 1L;
    private String sourceLang;
    private String suggestion;
    private String freeCommentError;
    private String freeCommentSuggestion;
    private String email;
    private String captcha;

    private Locale current;

    @Inject
    SessionBean sessionBean;

    @Inject
    BackToFrontMessengerBean logBean;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @PostConstruct
    public void init() {
        current = FacesContext.getCurrentInstance().getExternalContext().getRequestLocale();
    }

    public void addMessage(FacesMessage.Severity severity, String summary, String detail) {
        FacesContext.getCurrentInstance().
                addMessage(null, new FacesMessage(severity, summary, detail));
    }

    public void sendFeedback() {
        if (captcha != null && captcha.toLowerCase().trim().equals("paris")) {
            HttpServletRequest request = (HttpServletRequest) FacesContext.getCurrentInstance().getExternalContext().getRequest();
            String url = request.getRequestURL().toString();
            SendReport send = new SendReport(applicationProperties.getMiddlewareHost(), applicationProperties.getMiddlewarePort());
            send.initFeedback(url, current.getDisplayName(), sourceLang, suggestion, freeCommentError, freeCommentSuggestion, email);
            send.start();

            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.feedback_sent"));
            addMessage(FacesMessage.SEVERITY_INFO, "👍🏼", sessionBean.getLocaleBundle().getString("general.message.feedback_sent"));
        }
    }

    public String getSourceLang() {
        return sourceLang;
    }

    public void setSourceLang(String sourceLang) {
        this.sourceLang = sourceLang;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public String getFreeCommentError() {
        return freeCommentError;
    }

    public void setFreeCommentError(String freeCommentError) {
        this.freeCommentError = freeCommentError;
    }

    public String getFreeCommentSuggestion() {
        return freeCommentSuggestion;
    }

    public void setFreeCommentSuggestion(String freeCommentSuggestion) {
        this.freeCommentSuggestion = freeCommentSuggestion;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCaptcha() {
        return captcha;
    }

    public void setCaptcha(String captcha) {
        this.captcha = captcha;
    }
}
