package net.clementlevallois.nocodeapp.web.front.backingbeans;

import io.mikael.urlbuilder.UrlBuilder;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.clementlevallois.functions.model.Globals.Names;
import net.clementlevallois.nocodeapp.web.front.flows.cowo.CowoState;
import net.clementlevallois.nocodeapp.web.front.flows.topics.TopicsState;
import net.clementlevallois.nocodeapp.web.front.i18n.I18nStaticFilesResourceBundle;
import net.clementlevallois.nocodeapp.web.front.utils.UrlParamCleaner;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped
public class SessionBean implements Serializable {

    private Names functionName;
    private String gazeOption = "1";
    private String userAgent;
    private ResourceBundle localeBundle;
    private boolean testServer;
    private String noRobot;
    private Locale currentLocale;
    private String hash;
    private String jobId;
    private CowoState cowoState;
    private TopicsState topicsState;

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
        staticFilesResourceBundle.setApplicationPropertiesBean(applicationProperties);
        localeBundle = staticFilesResourceBundle.simpleMethodToGetResourceBundle(currentLocale);
    }

    public void setApplicationProperties(ApplicationPropertiesBean applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    public String getFunctionName() {
        if (functionName == null) {
            return "";
        }
        return functionName.getDescription();
    }

    public void setFunctionName(String function) {
        if (function == null) {
            System.out.println("function param was null??");
            return;
        }
        if (function.contains("=") & !function.contains("?")) {
            System.out.println("weird url parameters decoded in sessionBean");
            System.out.println("url param for function is: " + function);
            this.functionName = Names.fromDescription(function.split("=")[1]);
        } else if (function.contains("=") & function.contains("?")) {
            this.functionName = Names.fromDescription(function.split("\\?")[0]);
        } else {
            this.functionName = Names.fromDescription(function);
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
        send.initAnalytics(this.functionName.getDescription(), userAgent);
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

    public CowoState getCowoState() {
        return cowoState;
    }

    public void setCowoState(CowoState cowoState) {
        this.cowoState = cowoState;
    }

    public TopicsState getTopicsState() {
        return topicsState;
    }

    public void setTopicsState(TopicsState topicsState) {
        this.topicsState = topicsState;
    }

}
