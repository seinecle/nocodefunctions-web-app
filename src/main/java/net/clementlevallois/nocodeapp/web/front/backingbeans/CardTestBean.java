/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.backingbeans;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.inject.Named;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import net.clementlevallois.nocodeapp.web.front.functions.UmigonBean;
import net.clementlevallois.nocodeapp.web.front.http.SendReport;
import net.clementlevallois.umigon.model.Category;
import net.clementlevallois.umigon.model.Document;
import net.clementlevallois.umigon.model.ResultOneHeuristics;

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
    private static String baseURI;

    private Boolean showFRExplanation = false;
    private Boolean showENExplanation = false;
    private Boolean showESExplanation = false;

    @Inject
    SessionBean sessionBean;


    @Inject
    ActiveLocale activeLocale;

    public CardTestBean() {
        if (sessionBean == null) {
            sessionBean = new SessionBean();
        }
        Properties privateProperties = SingletonBean.getPrivateProperties();
        baseURI = "http://localhost:" + privateProperties.getProperty("nocode_api_port") + "/api/";
    }

    public String getUmigonTestInputFR() {
        return umigonTestInputFR;
    }

    public void setUmigonTestInputFR(String umigonTestInputFR) {
        this.umigonTestInputFR = umigonTestInputFR;
    }

    public void runUmigonTestFR() throws IOException, URISyntaxException, InterruptedException {
        showFRExplanation = false;
        SendReport send = new SendReport();
        send.initAnalytics("test: umigon fr", sessionBean.getUserAgent());
        send.start();

        StringBuilder sb = new StringBuilder();
        sb.append(baseURI);
        sb.append("sentimentForAText");
        sb.append("?text-lang=").append("fr");
        sb.append("&text=").append(URLEncoder.encode(umigonTestInputFR, StandardCharsets.UTF_8.toString()));
        sb.append("&explanation=on");
        sb.append("&shorter=true");
        sb.append("&output-format=bytes");
        sb.append("&explanation-lang=").append(activeLocale.getLanguageTag());
        String uriAsString = sb.toString();

        URI uri = new URI(uriAsString);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .build();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        byte[] body = response.body();
        try (
                 ByteArrayInputStream bis = new ByteArrayInputStream(body);  ObjectInputStream ois = new ObjectInputStream(bis)) {
            Document doc = (Document) ois.readObject();
            switch (doc.getCategoryCode()) {
                case "_12":
                    umigonResultFR = "😔 " + doc.getCategoryLocalizedPlainText();
                    break;
                case "_11":
                    umigonResultFR = "🤗 " + doc.getCategoryLocalizedPlainText();
                    break;
                default:
                    umigonResultFR = "😐 " + doc.getCategoryLocalizedPlainText();
                    break;
            }
            umigonResultFRExplanation = doc.getExplanationSentimentHtml();

        } catch (IOException | ClassNotFoundException ex) {
            Logger.getLogger(UmigonBean.class.getName()).log(Level.SEVERE, null, ex);
        }

        renderSignalFR = true;
        reportResultFRRendered = false;

    }

    public void runUmigonTestES() throws IOException, URISyntaxException, InterruptedException {
        showFRExplanation = false;
        SendReport send = new SendReport();
        send.initAnalytics("test: umigon es", sessionBean.getUserAgent());
        send.start();

        StringBuilder sb = new StringBuilder();
        sb.append(baseURI);
        sb.append("sentimentForAText");
        sb.append("?text-lang=").append("es");
        sb.append("&text=").append(URLEncoder.encode(umigonTestInputES, StandardCharsets.UTF_8.toString()));
        sb.append("&explanation=on");
        sb.append("&shorter=true");
        sb.append("&output-format=bytes");
        sb.append("&explanation-lang=").append(activeLocale.getLanguageTag());
        String uriAsString = sb.toString();

        URI uri = new URI(uriAsString);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .build();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        byte[] body = response.body();
        try (
                 ByteArrayInputStream bis = new ByteArrayInputStream(body);  ObjectInputStream ois = new ObjectInputStream(bis)) {
            Document doc = (Document) ois.readObject();
            umigonResultFR = switch (doc.getCategoryCode()) {
                case "_12" -> "😔 " + doc.getCategoryLocalizedPlainText();
                case "_11" -> "🤗 " + doc.getCategoryLocalizedPlainText();
                default -> "😐 " + doc.getCategoryLocalizedPlainText();
            };
            umigonResultESExplanation = doc.getExplanationSentimentHtml();

        } catch (IOException | ClassNotFoundException ex) {
            Logger.getLogger(UmigonBean.class.getName()).log(Level.SEVERE, null, ex);
        }

        renderSignalES = true;
        reportResultESRendered = false;
    }

    public void runOrganicTestFR() throws IOException, InterruptedException, URISyntaxException {
        SendReport send = new SendReport();
        send.initAnalytics("test: organic fr", sessionBean.getUserAgent());
        send.start();

        URI uri = new URI(baseURI + "organicForAText/bytes/fr?text=" + URLEncoder.encode(organicTestInputFR, StandardCharsets.UTF_8.toString()));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .build();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        byte[] body = response.body();
        try (
                 ByteArrayInputStream bis = new ByteArrayInputStream(body);  ObjectInputStream ois = new ObjectInputStream(bis)) {
            Document doc = (Document) ois.readObject();
            Set<ResultOneHeuristics> map1 = doc.getAllHeuristicsResultsForOneCategory(Category.CategoryEnum._61);
            Set<ResultOneHeuristics> map2 = doc.getAllHeuristicsResultsForOneCategory(Category.CategoryEnum._611);
            if (map1.isEmpty() & map2.isEmpty()) {
                organicResultFR = "🌿 " + sessionBean.getLocaleBundle().getString("organic.general.soundsorganic");
            } else {
                organicResultFR = "📢 " + sessionBean.getLocaleBundle().getString("organic.general.soundspromoted");
            }

            renderSignalOrganicFR = true;
            reportResultFROrganicRendered = false;
        } catch (IOException | ClassNotFoundException ex) {
            Logger.getLogger(UmigonBean.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void runUmigonTestEN() throws UnsupportedEncodingException, URISyntaxException, IOException, InterruptedException {
        SendReport send = new SendReport();
        send.initAnalytics("test: umigon en", sessionBean.getUserAgent());
        send.start();

        StringBuilder sb = new StringBuilder();
        sb.append(baseURI);
        sb.append("sentimentForAText");
        sb.append("?text-lang=").append("en");
        sb.append("&text=").append(URLEncoder.encode(umigonTestInputEN, StandardCharsets.UTF_8.toString()));
        sb.append("&explanation=on");
        sb.append("&output-format=bytes");
        sb.append("&shorter=true");
        sb.append("&explanation-lang=").append(activeLocale.getLanguageTag());
        String uriAsString = sb.toString();

        URI uri = new URI(uriAsString);

        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        byte[] body = response.body();
        try (
                 ByteArrayInputStream bis = new ByteArrayInputStream(body);  ObjectInputStream ois = new ObjectInputStream(bis)) {
            Document doc = (Document) ois.readObject();
            switch (doc.getCategoryCode()) {
                case "_12":
                    umigonResultEN = "😔 " + doc.getCategoryLocalizedPlainText();
                    break;
                case "_11":
                    umigonResultEN = "🤗 " + doc.getCategoryLocalizedPlainText();
                    break;
                default:
                    umigonResultEN = "😐 " + doc.getCategoryLocalizedPlainText();
                    break;
            }
            umigonResultENExplanation = doc.getExplanationSentimentHtml();

        } catch (Exception ex) {
            Logger.getLogger(CardTestBean.class.getName()).log(Level.SEVERE, null, ex);
        }

        renderSignalEN = true;
        reportResultENRendered = false;

    }

    public void runOrganicTestEN() throws UnsupportedEncodingException, URISyntaxException, IOException, InterruptedException {
        SendReport send = new SendReport();
        send.initAnalytics("test: organic en", sessionBean.getUserAgent());
        send.start();

        URI uri = new URI(baseURI + "organicForAText/bytes/en?text=" + URLEncoder.encode(organicTestInputEN, StandardCharsets.UTF_8.toString()));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .build();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        byte[] body = response.body();
        try (
                 ByteArrayInputStream bis = new ByteArrayInputStream(body);  ObjectInputStream ois = new ObjectInputStream(bis)) {
            Document doc = (Document) ois.readObject();
            Set<ResultOneHeuristics> map1 = doc.getAllHeuristicsResultsForOneCategory(Category.CategoryEnum._61);
            Set<ResultOneHeuristics> map2 = doc.getAllHeuristicsResultsForOneCategory(Category.CategoryEnum._611);
            if (map1.isEmpty() & map2.isEmpty()) {
                organicResultEN = "🌿 " + sessionBean.getLocaleBundle().getString("organic.general.soundsorganic");
            } else {
                organicResultEN = "📢 " + sessionBean.getLocaleBundle().getString("organic.general.soundspromoted");
            }
            renderSignalOrganicEN = true;
            reportResultENOrganicRendered = false;

        } catch (Exception ex) {
            Logger.getLogger(CardTestBean.class
                    .getName()).log(Level.SEVERE, null, ex);
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
        SendReport sender = new SendReport();
        sender.initErrorReport(umigonTestInputEN + " - should not be " + umigonResultEN);
        sender.start();
        reportResultENRendered = true;
        renderSignalEN = false;
        return "";
    }

    public String signalUmigonFR() {
        SendReport sender = new SendReport();
        sender.initErrorReport(umigonTestInputFR + " - should not be " + umigonResultFR);
        sender.start();
        reportResultFRRendered = true;
        renderSignalFR = false;
        return "";
    }

    public String signalUmigonES() {
        SendReport sender = new SendReport();
        sender.initErrorReport(umigonTestInputES + " - should not be " + umigonResultES);
        sender.start();
        reportResultESRendered = true;
        renderSignalES = false;
        return "";
    }

    public String signalOrganicEN() {
        SendReport sender = new SendReport();
        sender.initErrorReport(organicTestInputEN + " - should not be " + organicResultEN);
        sender.start();
        reportResultENOrganicRendered = true;
        renderSignalOrganicEN = false;
        return "";
    }

    public String signalOrganicFR() {
        SendReport sender = new SendReport();
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

}
