package net.clementlevallois.nocodeapp.web.front.backingbeans;

import jakarta.inject.Named;
import java.io.Serializable;
import java.util.Locale;
import java.util.ResourceBundle;
import jakarta.enterprise.context.SessionScoped;
import jakarta.servlet.http.HttpServletRequest;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.http.SendReport;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;
import net.clementlevallois.nocodeapp.web.front.i18n.I18nStaticFilesResourceBundle;
import net.clementlevallois.nocodeapp.web.front.utils.UrlParamCleaner;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped
public class SessionBean implements Serializable {

    private String userAgent;
    private ResourceBundle localeBundle;
    private boolean testServer;
    private String noRobot;
    private Locale currentLocale;
    private String hash;
    private String jobId;
    private FlowState flowState;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    public SessionBean() {
    }

    @PostConstruct
    public void init() {
        System.out.println("SessionBean created: " + System.identityHashCode(this));
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
        staticFilesResourceBundle.setApplicationPropertiesBean(applicationProperties);
        localeBundle = staticFilesResourceBundle.simpleMethodToGetResourceBundle(currentLocale);
    }

    public void setApplicationProperties(ApplicationPropertiesBean applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    public void sendFunctionPageReport(String functionName) {
        SendReport send = new SendReport(applicationProperties.getMiddlewareHost(), applicationProperties.getMiddlewarePort());
        send.initAnalytics(functionName, userAgent);
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
            try {
                String port = applicationProperties.getPrivateProperties().getProperty("nocode_api_port");
                uri = new URI("http", null, "localhost", Integer.parseInt(port), null, null, null);
                return uri.toString();
            } catch (URISyntaxException ex) {
                System.getLogger(SessionBean.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                return null;
            }
        } else {
            try {
                String domain = "nocodefunctions.com";
                if ("yes".equals(System.getProperty("test"))) {
                    domain = "test.nocodefunctions.com";
                }
                uri = new URI("https", domain, null, null);
                String urlString = uri.toString();

                return urlEncode ? URLEncoder.encode(urlString, StandardCharsets.UTF_8) : urlString;
            } catch (URISyntaxException ex) {
                System.getLogger(SessionBean.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                return null;
            }
        }
    }

    public void refreshLocaleBundle() {
        I18nStaticFilesResourceBundle dbb = new I18nStaticFilesResourceBundle();
        dbb.setApplicationPropertiesBean(applicationProperties);
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

    public String getJobId() {
        return jobId;
    }

    public void createJobId() {
        this.jobId = UUID.randomUUID().toString().substring(0, 12);
    }

    public void addMessage(FacesMessage.Severity severity, String summary, String detail) {
        try {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severity, summary, detail));
        } catch (NullPointerException e) {
            System.out.println("FacesContext.getCurrentInstance was null. Detail: " + detail);
        }
    }

    public boolean isHashPresent() {
        return !getHash().isBlank();
    }

    public String getHash() {
        if (hash != null && !hash.isBlank()) {
            return hash;
        } else {
            hash = getHashValueFromCookie();
            if (hash != null && !hash.isBlank()) {
                return hash;
            } else {
                removeCookie(SingletonBean.SERVICE_NAME);
                return "";
            }
        }
    }

    public void setHash(String hash) {
        if (hash != null && !hash.isBlank()) {
//            System.out.println("hash from url: " + hash);
            this.hash = UrlParamCleaner.getRightmostPart(hash);
//            System.out.println("after cleaning: " + this.hash);
            writeValueToCookie(this.hash);
        }
    }

    public boolean isCookiePresent() {
        Map<String, Object> cookieMap = FacesContext.getCurrentInstance().getExternalContext().getRequestCookieMap();
        return cookieMap.containsKey(SingletonBean.SERVICE_NAME);
    }

    private String getHashValueFromCookie() {
        Map<String, Object> cookieMap = FacesContext.getCurrentInstance().getExternalContext().getRequestCookieMap();
        if (((Cookie) cookieMap.get(SingletonBean.SERVICE_NAME)) == null) {
            return null;
        } else {
            return ((Cookie) cookieMap.get(SingletonBean.SERVICE_NAME)).getValue();
        }
    }

    private void removeCookie(String cookieName) {
        ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
        if (externalContext.getRequestCookieMap().containsKey(cookieName)) {
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
        Map<String, Object> properties = new HashMap();
        properties.put("maxAge", 60 * 60 * 24 * 365); // 1 year
        properties.put("httpOnly", true);
        properties.put("secure", true);
        FacesContext.getCurrentInstance().getExternalContext().addResponseCookie(SingletonBean.SERVICE_NAME, value, properties);
    }

    public FlowState getFlowState() {
        return flowState;
    }

    public void setFlowState(FlowState flowState) {
        this.flowState = flowState;
        this.noRobot = "yesss";
    }
}
