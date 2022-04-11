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
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Named;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import net.clementlevallois.nocodeapp.web.front.functions.UmigonBean;
import net.clementlevallois.nocodeapp.web.front.http.SendReport;
import net.clementlevallois.umigon.model.Document;

/**
 *
 * @author LEVALLOIS
 */
@Named("cardTestBean")
@ViewScoped
public class CardTestBean implements Serializable {

    private String umigonTestInputFR = "nocode c'est tendance :)";
    private String umigonResultFR = "";
    private String umigonTestInputEN = "nocode is the new thing :)";
    private String umigonResultEN = "";
    private Boolean renderSignalEN = false;
    private Boolean renderSignalFR = false;
    private Boolean reportResultENRendered = false;
    private Boolean reportResultFRRendered = false;
    private Boolean renderSignalOrganicEN = false;
    private Boolean renderSignalOrganicFR = false;
    private Boolean reportResultENOrganicRendered = false;
    private Boolean reportResultFROrganicRendered = false;
    private String organicTestInputFR = "Cédez à vos envies beauté sur http://loreal-paris.fr parce que vous le valez bien";
    private String organicResultFR = "";
    private String organicTestInputEN = "We\u2019re stoked to announce our new partnership with @SurfingEngland which will run over the next four years. Read more about it here: https://bit.ly/3g9xaLM";
    private String organicResultEN = "";
    private static String baseURI;

    @Inject
    SessionBean sessionBean;
    
    @Inject
    SingletonBean singletonBean;

    public CardTestBean() {
        if (sessionBean == null) {
            sessionBean = new SessionBean();
        }
        if (singletonBean == null) {
            singletonBean = new SingletonBean();
        }
        Properties privateProperties = singletonBean.getPrivateProperties();
        baseURI =  "http://localhost:" + privateProperties.getProperty("nocode_api_port") + "/api/";
    }

    public String getUmigonTestInputFR() {
        return umigonTestInputFR;
    }

    public void setUmigonTestInputFR(String umigonTestInputFR) {
        this.umigonTestInputFR = umigonTestInputFR;
    }

    public void runUmigonTestFR() throws IOException, URISyntaxException, InterruptedException {
        SendReport send = new SendReport();
        send.initAnalytics("test: umigon fr", sessionBean.getUserAgent());
        send.start();

        URI uri = new URI(baseURI + "sentimentForAText/bytes/fr?text=" + URLEncoder.encode(umigonTestInputFR, StandardCharsets.UTF_8.toString()));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .build();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        byte[] body = response.body();
        try (
                 ByteArrayInputStream bis = new ByteArrayInputStream(body);  ObjectInputStream ois = new ObjectInputStream(bis)) {
            Document doc = (Document) ois.readObject();
            if (doc.isIsNegative()) {
                umigonResultFR = "😔 " + sessionBean.getLocaleBundle().getString("umigon.general.negativesentiment");
            } else if (doc.isIsPositive()) {
                umigonResultFR = "🤗 " + sessionBean.getLocaleBundle().getString("umigon.general.positivesentiment");
            } else {
                umigonResultFR = "😐 " + sessionBean.getLocaleBundle().getString("umigon.general.neutralsentiment");
            }
            renderSignalFR = true;
            reportResultFRRendered = false;
        } catch (IOException | ClassNotFoundException ex) {
            Logger.getLogger(UmigonBean.class.getName()).log(Level.SEVERE, null, ex);
        }
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
            if (doc.isPromoted()) {
                organicResultFR = "📢 " + sessionBean.getLocaleBundle().getString("organic.general.soundspromoted");
            } else {
                organicResultFR = "🌿 " + sessionBean.getLocaleBundle().getString("organic.general.soundsorganic");
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

        URI uri = new URI(baseURI + "sentimentForAText/bytes/en?text=" + URLEncoder.encode(umigonTestInputEN, StandardCharsets.UTF_8.toString()));

        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        byte[] body = response.body();
        try (
                 ByteArrayInputStream bis = new ByteArrayInputStream(body);  ObjectInputStream ois = new ObjectInputStream(bis)) {
            Document doc = (Document) ois.readObject();
            if (doc.isIsNegative()) {
                umigonResultEN = "😔 " + sessionBean.getLocaleBundle().getString("umigon.general.negativesentiment");
            } else if (doc.isIsPositive()) {
                umigonResultEN = "🤗 " + sessionBean.getLocaleBundle().getString("umigon.general.positivesentiment");
            } else {
                umigonResultEN = "😐 " + sessionBean.getLocaleBundle().getString("umigon.general.neutralsentiment");
            }
            renderSignalEN = true;
            reportResultENRendered = false;
        } catch (Exception ex) {
            Logger.getLogger(CardTestBean.class
                    .getName()).log(Level.SEVERE, null, ex);
        }
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
            if (doc.isPromoted()) {
                organicResultEN = "📢 " + sessionBean.getLocaleBundle().getString("organic.general.soundspromoted");
            } else {
                organicResultEN = "🌿 " + sessionBean.getLocaleBundle().getString("organic.general.soundsorganic");
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

    public void setUmigonResultFR(String umigonResultFR) {
        this.umigonResultFR = umigonResultFR;
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

    public void setRenderSignalFR(Boolean renderSignalFR) {
        this.renderSignalFR = renderSignalFR;
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

    public String signalOrganicEN() {
        SendReport sender = new SendReport();
        sender.initErrorReport(organicTestInputEN + " - should not be " + organicResultEN);
        sender.start();
        reportResultENRendered = true;
        renderSignalEN = false;
        return "";
    }

    public String signalOrganicFR() {
        SendReport sender = new SendReport();
        sender.initErrorReport(organicTestInputFR + " - should not be " + organicResultFR);
        sender.start();
        reportResultFRRendered = true;
        renderSignalFR = false;
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

    public void setReportResultFRRendered(Boolean reportResultFRRendered) {
        this.reportResultFRRendered = reportResultFRRendered;
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

}
