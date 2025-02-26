package net.clementlevallois.nocodeapp.web.front.backingbeans;

import io.mikael.urlbuilder.UrlBuilder;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.Locale;
import java.util.ResourceBundle;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.context.FacesContext;
import jakarta.servlet.http.HttpServletRequest;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.http.SendReport;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.inject.Inject;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.clementlevallois.nocodeapp.web.front.i18n.I18nStaticFilesResourceBundle;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped
public class SessionBean implements Serializable {

    private String function;
    private String gazeOption = "1";
    private String userAgent;
    private ResourceBundle localeBundle;
    private boolean testServer;
    private String noRobot;
    private Locale currentLocale;
    private String hash;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    public SessionBean() {
    }

    @PostConstruct
    public void init() {
        FacesContext currentInstance = FacesContext.getCurrentInstance();
        if (currentInstance == null) {
            currentLocale = Locale.ENGLISH;
            userAgent = "unknown-user-agent";
        } else {
            currentLocale = currentInstance.getExternalContext().getRequestLocale();
            HttpServletRequest request = (HttpServletRequest) currentInstance.getExternalContext().getRequest();
            userAgent = request.getHeader("user-agent");
        }
        I18nStaticFilesResourceBundle staticFilesResourceBundle = new I18nStaticFilesResourceBundle();
        localeBundle = staticFilesResourceBundle.simpleMethodToGetResourceBundle(currentLocale);
    }

    public void setApplicationProperties(ApplicationPropertiesBean applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    public String getFunction() {
        return function;
    }

    public void setFunction(String function) {

        if (function == null) {
            System.out.println("function param was null??");
            return;
        }
        if (function.contains("=") & !function.contains("?")) {
            System.out.println("weird url parameters decoded in sessionBean");
            System.out.println("url param for function is: " + function);
            this.function = function.split("=")[1];
        } else if (function.contains("=") & function.contains("?")) {
            this.function = function.split("\\?")[0];
        } else {
            this.function = function;
        }
    }

    public String getGazeOption() {
        return gazeOption;
    }

    public void setGazeOption(String gazeOption) {
        this.gazeOption = gazeOption;
    }

    public void sendFunctionPageReport() {
        SendReport send = new SendReport(applicationProperties.getMiddlewareHost(), applicationProperties.getMiddlewarePort());
        send.initAnalytics(this.function, userAgent);
        send.start();
    }

    public String logout() {
        Locale persistLocale = currentLocale;
        FacesContext.getCurrentInstance().getExternalContext().invalidateSession();
        FacesContext.getCurrentInstance().getViewRoot().setLocale(persistLocale);

        return "/index?faces-redirect=true";
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String host() {
        return RemoteLocal.getDomain();
    }

    public String getHostFunctionsAPI(Boolean urlEncode) {
        URI uri;

        if (RemoteLocal.isLocal()) {
            uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withHost("localhost")
                    .withPort((Integer.valueOf(applicationProperties.getPrivateProperties().getProperty("nocode_api_port")))).toUri();
            return uri.toString();
        } else {
            String domain;
            if (System.getProperty("test") != null && System.getProperty("test").equals("yes")) {
                domain = "test.nocodefunctions.com";
            } else {
                domain = "nocodefunctions.com";
            }
            UrlBuilder urlBuilder = UrlBuilder
                    .empty()
                    .withScheme("https")
                    .withHost(domain);
            String urlString = urlBuilder.toUri().toString();

            return urlEncode ? URLEncoder.encode(urlString, StandardCharsets.UTF_8) : urlString;
        }
    }

    public void refreshLocaleBundle() {
        I18nStaticFilesResourceBundle dbb = new I18nStaticFilesResourceBundle();
        localeBundle = dbb.simpleMethodToGetResourceBundle(currentLocale);
    }

    public ResourceBundle getLocaleBundle() {
        return localeBundle;
    }

    // redudant with RemoteLocal.isTest() but for a good reason
    // we need this method here because that's going to be called in an EL in a page
    // and for that it has to be in a bean, not just a static method
    public boolean isTestServer() {
        return RemoteLocal.isTest();
    }

    public void setTestServer(boolean testServer) {
        this.testServer = testServer;
    }

    public String getNoRobot() {
        String noIndex = "<meta name=\"robots\" content=\"noindex\"/>";
        return noIndex;
    }

    public void setNoRobot(String noRobot) {
        this.noRobot = noRobot;
    }

    public Locale getCurrentLocale() {
        return currentLocale;
    }

    public void setCurrentLocale(Locale currentLocale) {
        this.currentLocale = currentLocale;
    }

    public void addMessage(FacesMessage.Severity severity, String summary, String detail) {
        try {
            FacesContext.getCurrentInstance().
                    addMessage(null, new FacesMessage(severity, summary, detail));
        } catch (NullPointerException e) {
            System.out.println("FacesContext.getCurrentInstance was null. Detail: " + detail);
        }
    }

    public String getHash() {
        if (hash != null && !hash.isBlank()) {
            return hash;
        } else {
            hash = getHashValueFromCookie();
            if (hash != null && !hash.isBlank()) {
                return hash;
            } else {
                removeCookie(SingletonBean.serviceName);
                return "";
            }
        }
    }

    public void setHash(String hash) {
        this.hash = hash;
        writeValueToCookie(hash);
    }

    public boolean isCookiePresent() {
        Map<String, Object> cookieMap = FacesContext.getCurrentInstance().getExternalContext().getRequestCookieMap();
        return cookieMap.containsKey(SingletonBean.serviceName);
    }

    private String getHashValueFromCookie() {
        Map<String, Object> cookieMap = FacesContext.getCurrentInstance().getExternalContext().getRequestCookieMap();
        if (((Cookie) cookieMap.get(SingletonBean.serviceName)) == null) {
            return null;
        } else {
            return ((Cookie) cookieMap.get(SingletonBean.serviceName)).getValue();
        }
    }

    private void removeCookie(String cookieName) {
        // Obtain the external context from the current FacesContext
        ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();

        // Check if the cookie exists in the request
        if (externalContext.getRequestCookieMap().containsKey(cookieName)) {
            // Create a cookie with the same name and set its value to null
            Cookie cookie = new Cookie(cookieName, null);
            // Set the path to the root or where the cookie was initially set
            cookie.setPath("/");
            // Mark the cookie for immediate expiration
            cookie.setMaxAge(0);

            // Add the cookie to the response to remove it from the client
            ((HttpServletResponse) externalContext.getResponse()).addCookie(cookie);
        }
    }

    private void writeValueToCookie(String value) {
        FacesContext.getCurrentInstance().getExternalContext().addResponseCookie(SingletonBean.serviceName, value, null);
    }

}
