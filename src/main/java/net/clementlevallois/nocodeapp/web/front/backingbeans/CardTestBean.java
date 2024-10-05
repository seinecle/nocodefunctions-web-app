package net.clementlevallois.nocodeapp.web.front.backingbeans;

import io.mikael.urlbuilder.UrlBuilder;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.inject.Named;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import net.clementlevallois.nocodeapp.web.front.functions.UmigonBean;
import net.clementlevallois.nocodeapp.web.front.http.SendReport;
import net.clementlevallois.umigon.model.classification.Document;

/**
 *
 * @author LEVALLOIS
 */
@Named("cardTestBean")
@ViewScoped
public class CardTestBean implements Serializable {

    private String umigonTestInputFR = "nocode c'est tendance :)";
    private String umigonResultFR = "";
    private String umigonResultFRExplanation = "";
    private String umigonTestInputES = "nocode esta de moda :)";
    private String umigonResultES = "";
    private String umigonResultESExplanation = "";
    private String umigonTestInputEN = "nocode is the new thing :)";
    private String umigonResultEN = "";
    private String umigonResultENExplanation = "";
    private Boolean renderSignalEN = false;
    private Boolean renderSignalFR = false;
    private Boolean renderSignalES = false;
    private Boolean reportResultENRendered = false;
    private Boolean reportResultFRRendered = false;
    private Boolean reportResultESRendered = false;
    private Boolean renderSignalOrganicEN = false;
    private Boolean renderSignalOrganicFR = false;
    private Boolean reportResultENOrganicRendered = false;
    private Boolean reportResultFROrganicRendered = false;
    private String organicTestInputFR = "Cédez à vos envies beauté sur http://loreal-paris.fr parce que vous le valez bien";
    private String organicResultFR = "";
    private String organicTestInputEN = "We\u2019re stoked to announce our new partnership with @SurfingEngland which will run over the next four years. Read more about it here: https://bit.ly/3g9xaLM";
    private String organicResultEN = "";
    private String organicResultFRExplanation = "";
    private String organicResultENExplanation = "";

    private Properties privateProperties;

    private Boolean showFRExplanation = false;
    private Boolean showENExplanation = false;
    private Boolean showESExplanation = false;
    private Boolean showENExplanationOrganic = false;
    private Boolean showFRExplanationOrganic = false;
    private Boolean usePublicDomainName = false;

    @Inject
    SessionBean sessionBean;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    public CardTestBean() {
    }

    @PostConstruct
    public void init() {
        privateProperties = applicationProperties.getPrivateProperties();
    }

    public void setSessionBean(SessionBean sessionBean) {
        this.sessionBean = sessionBean;
    }

    public void setApplicationPropertiesBean(ApplicationPropertiesBean applicationPropertiesBean) {
        this.applicationProperties = applicationPropertiesBean;
    }

    public void setPrivateProperties(Properties privateProperties) {
        this.privateProperties = privateProperties;
    }
    
    public String getUmigonTestInputFR() {
        return umigonTestInputFR;
    }

    public void setUmigonTestInputFR(String umigonTestInputFR) {
        this.umigonTestInputFR = umigonTestInputFR;
    }

    public void runUmigonTestFR() {
        URI uri = null;
        try {
            showFRExplanation = false;

            UrlBuilder urlBuilder = UrlBuilder.empty();
            if (usePublicDomainName) {
                urlBuilder = urlBuilder.withScheme("https")
                        .withHost("nocodefunctions.com");
            } else {
                urlBuilder = urlBuilder.withScheme("http")
                        .withHost("localhost")
                        .withPort((Integer.valueOf(privateProperties.getProperty("nocode_api_port"))));
            }
            uri = urlBuilder.withPath("api/sentimentForAText")
                    .addParameter("text-lang", "fr")
                    .addParameter("explanation", "on")
                    .addParameter("shorter", "true")
                    .addParameter("output-format", "bytes")
                    .addParameter("explanation-lang", sessionBean.getCurrentLocale().toLanguageTag())
                    .toUri();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .POST(HttpRequest.BodyPublishers.ofString(umigonTestInputFR))
                    .build();
            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = response.body();
            try (
                    ByteArrayInputStream bis = new ByteArrayInputStream(body); ObjectInputStream ois = new ObjectInputStream(bis)) {
                Document doc = (Document) ois.readObject();
                umigonResultFR = switch (doc.getCategoryCode()) {
                    case "_12" ->
                        "😔 " + doc.getCategoryLocalizedPlainText();
                    case "_11" ->
                        "🤗 " + doc.getCategoryLocalizedPlainText();
                    default ->
                        "😐 " + doc.getCategoryLocalizedPlainText();
                };
                umigonResultFRExplanation = doc.getExplanationHtml();

            } catch (IOException | ClassNotFoundException ex) {
                System.out.println("body of response to umigon fr test was not readable");
                System.out.println("body: " + new String(body));
                return;
            }

            renderSignalFR = true;
            reportResultFRRendered = false;

        } catch (IOException | InterruptedException ex) {
            System.out.println("connection to api not possible in umigon test fr");
            if (uri == null) {
                System.out.println("uri was not defined");
            } else {
                System.out.println("uri: " + uri.toString());
            }
        }

    }

    public void runOrganicTestFR() {
        URI uri = null;
        try {
            showFRExplanationOrganic = false;

            UrlBuilder urlBuilder = UrlBuilder.empty();
            if (usePublicDomainName) {
                urlBuilder = urlBuilder.withScheme("https")
                        .withHost("nocodefunctions.com");
            } else {
                urlBuilder = urlBuilder.withScheme("http")
                        .withHost("localhost")
                        .withPort((Integer.valueOf(privateProperties.getProperty("nocode_api_port"))));
            }
            uri = urlBuilder.withPath("api/organicForAText")
                    .addParameter("text-lang", "fr")
                    .addParameter("explanation", "on")
                    .addParameter("shorter", "true")
                    .addParameter("output-format", "bytes")
                    .addParameter("explanation-lang", sessionBean.getCurrentLocale().toLanguageTag())
                    .toUri();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .POST(HttpRequest.BodyPublishers.ofString(organicTestInputFR))
                    .build();
            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = response.body();
            if (response.statusCode() == 200) {
                try (
                        ByteArrayInputStream bis = new ByteArrayInputStream(body); ObjectInputStream ois = new ObjectInputStream(bis)) {
                    Document doc = (Document) ois.readObject();
                    organicResultFR = switch (doc.getCategoryCode()) {
                        case "_61" ->
                            "📢 " + doc.getCategoryLocalizedPlainText();
                        case "_611" ->
                            "📢 " + doc.getCategoryLocalizedPlainText();
                        default ->
                            "🌿 " + doc.getCategoryLocalizedPlainText();
                    };
                    organicResultFRExplanation = doc.getExplanationHtml();

                } catch (IOException | ClassNotFoundException ex) {
                    Logger.getLogger(UmigonBean.class.getName()).log(Level.SEVERE, null, ex);
                }

            } else {
                System.out.println("body of response to organic fr test was not readable");
                System.out.println("body: " + new String(body));
                return;
            }
            renderSignalOrganicFR = true;
            reportResultFRRendered = false;

        } catch (IOException | InterruptedException ex) {
            System.out.println("connection to api not possible in organic test fr");
            if (uri == null) {
                System.out.println("uri was not defined");
            } else {
                System.out.println("uri: " + uri.toString());
            }
        }

    }

    public void runOrganicTestEN() {
        URI uri = null;
        try {
            showENExplanationOrganic = false;

            UrlBuilder urlBuilder = UrlBuilder.empty();
            if (usePublicDomainName) {
                urlBuilder = urlBuilder.withScheme("https")
                        .withHost("nocodefunctions.com");
            } else {
                urlBuilder = urlBuilder.withScheme("http")
                        .withHost("localhost")
                        .withPort((Integer.valueOf(privateProperties.getProperty("nocode_api_port"))));
            }
            uri = urlBuilder.withPath("api/organicForAText")
                    .addParameter("text-lang", "en")
                    .addParameter("explanation", "on")
                    .addParameter("shorter", "true")
                    .addParameter("output-format", "bytes")
                    .addParameter("explanation-lang", sessionBean.getCurrentLocale().toLanguageTag())
                    .toUri();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .POST(HttpRequest.BodyPublishers.ofString(organicTestInputEN))
                    .build();
            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = response.body();
            if (response.statusCode() == 200) {
                try (
                        ByteArrayInputStream bis = new ByteArrayInputStream(body); ObjectInputStream ois = new ObjectInputStream(bis)) {
                    Document doc = (Document) ois.readObject();
                    organicResultEN = switch (doc.getCategoryCode()) {
                        case "_61" ->
                            "📢 " + doc.getCategoryLocalizedPlainText();
                        case "_611" ->
                            "📢 " + doc.getCategoryLocalizedPlainText();
                        default ->
                            "🌿 " + doc.getCategoryLocalizedPlainText();
                    };
                    organicResultENExplanation = doc.getExplanationHtml();
                } catch (IOException | ClassNotFoundException ex) {
                    Logger.getLogger(UmigonBean.class.getName()).log(Level.SEVERE, null, ex);
                }
            } else {
                System.out.println("body of response to organic en test was not readable");
                System.out.println("body: " + new String(body));
                return;
            }
            renderSignalOrganicEN = true;
            reportResultENRendered = false;
        } catch (IOException | InterruptedException ex) {
            System.out.println("connection to api not possible in organic test en");
            if (uri == null) {
                System.out.println("uri was not defined");
            } else {
                System.out.println("uri: " + uri.toString());
            }
        }

    }

    public void runUmigonTestES() {
        URI uri = null;
        try {
            showESExplanation = false;

            UrlBuilder urlBuilder = UrlBuilder.empty();
            if (usePublicDomainName) {
                urlBuilder = urlBuilder.withScheme("https")
                        .withHost("nocodefunctions.com");
            } else {
                urlBuilder = urlBuilder.withScheme("http")
                        .withHost("localhost")
                        .withPort((Integer.valueOf(privateProperties.getProperty("nocode_api_port"))));
            }
            uri = urlBuilder.withPath("api/sentimentForAText")
                    .addParameter("text-lang", "es")
                    .addParameter("explanation", "on")
                    .addParameter("shorter", "true")
                    .addParameter("output-format", "bytes")
                    .addParameter("explanation-lang", sessionBean.getCurrentLocale().toLanguageTag())
                    .toUri();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .POST(HttpRequest.BodyPublishers.ofString(umigonTestInputES))
                    .build();
            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = response.body();
            if (response.statusCode() == 200) {
                try (
                        ByteArrayInputStream bis = new ByteArrayInputStream(body); ObjectInputStream ois = new ObjectInputStream(bis)) {
                    Document doc = (Document) ois.readObject();
                    umigonResultES = switch (doc.getCategoryCode()) {
                        case "_12" ->
                            "😔 " + doc.getCategoryLocalizedPlainText();
                        case "_11" ->
                            "🤗 " + doc.getCategoryLocalizedPlainText();
                        default ->
                            "😐 " + doc.getCategoryLocalizedPlainText();
                    };
                    umigonResultESExplanation = doc.getExplanationHtml();

                } catch (IOException | ClassNotFoundException ex) {
                    Logger.getLogger(UmigonBean.class
                            .getName()).log(Level.SEVERE, null, ex);
                }
            } else {
                System.out.println("body of response to umigon es test was not readable");
                System.out.println("body: " + new String(body));
                return;
            }

            renderSignalES = true;
            reportResultESRendered = false;
        } catch (IOException | InterruptedException ex) {
            System.out.println("connection to api not possible in umigon test es");
            if (uri == null) {
                System.out.println("uri was not defined");
            } else {
                System.out.println("uri: " + uri.toString());
            }
        }
    }

    public void runUmigonTestEN() {
        URI uri = null;
        try {
            showENExplanation = false;

            UrlBuilder urlBuilder = UrlBuilder.empty();
            if (usePublicDomainName) {
                urlBuilder = urlBuilder.withScheme("https")
                        .withHost("nocodefunctions.com");
            } else {
                urlBuilder = urlBuilder.withScheme("http")
                        .withHost("localhost")
                        .withPort((Integer.valueOf(privateProperties.getProperty("nocode_api_port"))));
            }
            uri = urlBuilder.withPath("api/sentimentForAText")
                    .addParameter("text-lang", "en")
                    .addParameter("text", umigonTestInputEN)
                    .addParameter("explanation", "on")
                    .addParameter("shorter", "true")
                    .addParameter("output-format", "bytes")
                    .addParameter("explanation-lang", sessionBean.getCurrentLocale().toLanguageTag())
                    .toUri();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .POST(HttpRequest.BodyPublishers.ofString(umigonTestInputEN))
                    .build();
            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = response.body();
            if (response.statusCode() == 200) {
                try (
                        ByteArrayInputStream bis = new ByteArrayInputStream(body); ObjectInputStream ois = new ObjectInputStream(bis)) {
                    Document doc = (Document) ois.readObject();
                    umigonResultEN = switch (doc.getCategoryCode()) {
                        case "_12" ->
                            "😔 " + doc.getCategoryLocalizedPlainText();
                        case "_11" ->
                            "🤗 " + doc.getCategoryLocalizedPlainText();
                        default ->
                            "😐 " + doc.getCategoryLocalizedPlainText();
                    };
                    umigonResultENExplanation = doc.getExplanationHtml();

                } catch (Exception ex) {
                    Logger.getLogger(CardTestBean.class
                            .getName()).log(Level.SEVERE, null, ex);
                }
            } else {
                System.out.println("body of response to umigon en test was not readable");
                System.out.println("body: " + new String(body));
                return;
            }

            renderSignalEN = true;
            reportResultENRendered = false;
        } catch (IOException | InterruptedException ex) {
            System.out.println("connection to api not possible in umigon test en");
            if (uri == null) {
                System.out.println("uri was not defined");
            } else {
                System.out.println("uri: " + uri.toString());
            }
        }
    }

    public String getUmigonResultFR() {
        return umigonResultFR;
    }

    public String getUmigonResultES() {
        return umigonResultES;
    }

    public void setUmigonResultFR(String umigonResultFR) {
        this.umigonResultFR = umigonResultFR;
    }

    public void setUmigonResultES(String umigonResultES) {
        this.umigonResultES = umigonResultES;
    }

    public String getUmigonTestInputEN() {
        return umigonTestInputEN;
    }

    public void setUmigonTestInputEN(String umigonTestInputEN) {
        this.umigonTestInputEN = umigonTestInputEN;
    }

    public String getUmigonResultEN() {
        return umigonResultEN;
    }

    public void setUmigonResultEN(String umigonResultEN) {
        this.umigonResultEN = umigonResultEN;
    }

    public Boolean getRenderSignalEN() {
        return renderSignalEN;
    }

    public void setRenderSignalEN(Boolean renderSignalEN) {
        this.renderSignalEN = renderSignalEN;
    }

    public Boolean getRenderSignalFR() {
        return renderSignalFR;
    }

    public Boolean getRenderSignalES() {
        return renderSignalES;
    }

    public void setRenderSignalFR(Boolean renderSignalFR) {
        this.renderSignalFR = renderSignalFR;
    }

    public void setRenderSignalES(Boolean renderSignalES) {
        this.renderSignalES = renderSignalES;
    }

    public String signalUmigonEN() {
        SendReport sender = new SendReport(applicationProperties.getMiddlewareHost(), applicationProperties.getMiddlewarePort());
        sender.initErrorReport(umigonTestInputEN + " - should not be " + umigonResultEN);
        sender.start();
        reportResultENRendered = true;
        renderSignalEN = false;
        return "";
    }

    public String signalUmigonFR() {
        SendReport sender = new SendReport(applicationProperties.getMiddlewareHost(), applicationProperties.getMiddlewarePort());
        sender.initErrorReport(umigonTestInputFR + " - should not be " + umigonResultFR);
        sender.start();
        reportResultFRRendered = true;
        renderSignalFR = false;
        return "";
    }

    public String signalUmigonES() {
        SendReport sender = new SendReport(applicationProperties.getMiddlewareHost(), applicationProperties.getMiddlewarePort());
        sender.initErrorReport(umigonTestInputES + " - should not be " + umigonResultES);
        sender.start();
        reportResultESRendered = true;
        renderSignalES = false;
        return "";
    }

    public String signalOrganicEN() {
        SendReport sender = new SendReport(applicationProperties.getMiddlewareHost(), applicationProperties.getMiddlewarePort());
        sender.initErrorReport(organicTestInputEN + " - should not be " + organicResultEN);
        sender.start();
        reportResultENOrganicRendered = true;
        renderSignalOrganicEN = false;
        return "";
    }

    public String signalOrganicFR() {
        SendReport sender = new SendReport(applicationProperties.getMiddlewareHost(), applicationProperties.getMiddlewarePort());
        sender.initErrorReport(organicTestInputFR + " - should not be " + organicResultFR);
        sender.start();
        reportResultFROrganicRendered = true;
        renderSignalOrganicFR = false;
        return "";
    }

    public Boolean getReportResultENRendered() {
        return reportResultENRendered;
    }

    public void setReportResultENRendered(Boolean reportResultENRendered) {
        this.reportResultENRendered = reportResultENRendered;
    }

    public Boolean getReportResultFRRendered() {
        return reportResultFRRendered;
    }

    public Boolean getReportResultESRendered() {
        return reportResultESRendered;
    }

    public void setReportResultFRRendered(Boolean reportResultFRRendered) {
        this.reportResultFRRendered = reportResultFRRendered;
    }

    public void setReportResultESRendered(Boolean reportResultESRendered) {
        this.reportResultESRendered = reportResultESRendered;
    }

    public String getOrganicTestInputFR() {
        return organicTestInputFR;
    }

    public void setOrganicTestInputFR(String organicTestInputFR) {
        this.organicTestInputFR = organicTestInputFR;
    }

    public String getOrganicResultFR() {
        return organicResultFR;
    }

    public void setOrganicResultFR(String organicResultFR) {
        this.organicResultFR = organicResultFR;
    }

    public String getOrganicTestInputEN() {
        return organicTestInputEN;
    }

    public void setOrganicTestInputEN(String organicTestInputEN) {
        this.organicTestInputEN = organicTestInputEN;
    }

    public String getOrganicResultEN() {
        return organicResultEN;
    }

    public void setOrganicResultEN(String organicResultEN) {
        this.organicResultEN = organicResultEN;
    }

    public Boolean getRenderSignalOrganicEN() {
        return renderSignalOrganicEN;
    }

    public void setRenderSignalOrganicEN(Boolean renderSignalOrganicEN) {
        this.renderSignalOrganicEN = renderSignalOrganicEN;
    }

    public Boolean getRenderSignalOrganicFR() {
        return renderSignalOrganicFR;
    }

    public void setRenderSignalOrganicFR(Boolean renderSignalOrganicFR) {
        this.renderSignalOrganicFR = renderSignalOrganicFR;
    }

    public Boolean getReportResultENOrganicRendered() {
        return reportResultENOrganicRendered;
    }

    public void setReportResultENOrganicRendered(Boolean reportResultENOrganicRendered) {
        this.reportResultENOrganicRendered = reportResultENOrganicRendered;
    }

    public Boolean getReportResultFROrganicRendered() {
        return reportResultFROrganicRendered;
    }

    public void setReportResultFROrganicRendered(Boolean reportResultFROrganicRendered) {
        this.reportResultFROrganicRendered = reportResultFROrganicRendered;
    }

    public String getUmigonResultFRExplanation() {
        return umigonResultFRExplanation;
    }

    public String getUmigonResultESExplanation() {
        return umigonResultESExplanation;
    }

    public void setUmigonResultFRExplanation(String umigonResultFRExplanation) {
        this.umigonResultFRExplanation = umigonResultFRExplanation;
    }

    public void setUmigonResultESExplanation(String umigonResultESExplanation) {
        this.umigonResultESExplanation = umigonResultESExplanation;
    }

    public String getUmigonResultENExplanation() {
        return umigonResultENExplanation;
    }

    public void setUmigonResultENExplanation(String umigonResultENExplanation) {
        this.umigonResultENExplanation = umigonResultENExplanation;
    }

    public Boolean getShowFRExplanation() {
        return showFRExplanation;
    }

    public Boolean getShowESExplanation() {
        return showESExplanation;
    }

    public void setShowFRExplanation(Boolean showFRExplanation) {
        this.showFRExplanation = showFRExplanation;
    }

    public void setShowESExplanation(Boolean showESExplanation) {
        this.showESExplanation = showESExplanation;
    }

    public Boolean getShowENExplanation() {
        return showENExplanation;
    }

    public void setShowENExplanation(Boolean showENExplanation) {
        this.showENExplanation = showENExplanation;
    }

    public String getUmigonTestInputES() {
        return umigonTestInputES;
    }

    public void setUmigonTestInputES(String umigonTestInputES) {
        this.umigonTestInputES = umigonTestInputES;
    }

    public String getOrganicResultFRExplanation() {
        return organicResultFRExplanation;
    }

    public void setOrganicResultFRExplanation(String organicResultFRExplanation) {
        this.organicResultFRExplanation = organicResultFRExplanation;
    }

    public String getOrganicResultENExplanation() {
        return organicResultENExplanation;
    }

    public void setOrganicResultENExplanation(String organicResultENExplanation) {
        this.organicResultENExplanation = organicResultENExplanation;
    }

    public Boolean getShowENExplanationOrganic() {
        return showENExplanationOrganic;
    }

    public void setShowENExplanationOrganic(Boolean showENExplanationOrganic) {
        this.showENExplanationOrganic = showENExplanationOrganic;
    }

    public Boolean getShowFRExplanationOrganic() {
        return showFRExplanationOrganic;
    }

    public void setShowFRExplanationOrganic(Boolean showFRExplanationOrganic) {
        this.showFRExplanationOrganic = showFRExplanationOrganic;
    }

    public void setUsePublicDomainName(Boolean usePublicDomainName) {
        this.usePublicDomainName = usePublicDomainName;
    }

}
