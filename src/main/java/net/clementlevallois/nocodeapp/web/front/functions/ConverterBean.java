/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.functions;

import io.mikael.urlbuilder.UrlBuilder;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.annotation.MultipartConfig;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import org.omnifaces.util.Faces;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.file.UploadedFile;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped
@MultipartConfig
public class ConverterBean implements Serializable {

    private UploadedFile uploadedFile;
    private String option = "sourceGexf";
    private String item = "item_name";
    private String link = "link_name";
    private String linkStrength = "link strength name";
    private InputStream is;
    private String uploadButtonMessage;
    private boolean renderGephiWarning = true;

    private String graphAsJsonVosViewer;
    private byte[] gexfAsByteArray;
    private boolean shareVVPublicly;

    private StreamedContent fileToSave;

    @Inject
    SessionBean sessionBean;

    public ConverterBean() {
        if (sessionBean == null) {
            sessionBean = new SessionBean();
        }
        sessionBean.setFunction("networkconverter");
    }

    @PostConstruct
    void init() {
        uploadButtonMessage = sessionBean.getLocaleBundle().getString("general.message.choose_gexf_file");
    }

    public String logout() {
        FacesContext.getCurrentInstance().getExternalContext().invalidateSession();
        return "/index?faces-redirect=true";
    }

    public UploadedFile getUploadedFile() {
        return uploadedFile;
    }

    public void setUploadedFile(UploadedFile uploadedFile) {
        this.uploadedFile = uploadedFile;
    }

    public void upload() {
        if (uploadedFile != null) {
            String success = sessionBean.getLocaleBundle().getString("general.nouns.success");
            String is_uploaded = sessionBean.getLocaleBundle().getString("general.verb.is_uploaded");
            FacesMessage message = new FacesMessage(success, uploadedFile.getFileName() + " " + is_uploaded + ".");
            FacesContext.getCurrentInstance().addMessage(null, message);
        }
    }

    public String handleFileUpload(FileUploadEvent event) {
        sessionBean.sendFunctionPageReport();
        String success = sessionBean.getLocaleBundle().getString("general.nouns.success");
        String is_uploaded = sessionBean.getLocaleBundle().getString("general.verb.is_uploaded");
        FacesMessage message = new FacesMessage(success, event.getFile().getFileName() + " " + is_uploaded + ".");
        FacesContext.getCurrentInstance().addMessage(null, message);
        uploadedFile = event.getFile();
        try {
            is = uploadedFile.getInputStream();
        } catch (IOException ex) {
            System.out.println("ex:" + ex.getMessage());
        }
        return "";
    }

    public void gotoVV() throws IOException {
        if (uploadedFile == null) {
            System.out.println("no file found for conversion to vv");
            return;
        }

        HttpRequest request;
        HttpClient client = HttpClient.newHttpClient();
        Set<CompletableFuture> futures = new HashSet();

        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(is.readAllBytes());

        URI uri = UrlBuilder
                .empty()
                .withScheme("http")
                .withPort(7002)
                .withHost("localhost")
                .withPath("api/convert2vv")
                .addParameter("item", item)
                .addParameter("items", "")
                .addParameter("link", link)
                .addParameter("links", "")
                .addParameter("linkStrength", linkStrength)
                .addParameter("descriptionData", "")
                .addParameter("totalLinkStrength", "")
                .toUri();

        request = HttpRequest.newBuilder()
                .POST(bodyPublisher)
                .uri(uri)
                .build();

        CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(
                resp -> {
                    byte[] body = resp.body();
                    graphAsJsonVosViewer = new String(body, StandardCharsets.UTF_8);
                }
        );

        futures.add(future);

        CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
        combinedFuture.join();

        String path = RemoteLocal.isLocal() ? "C:\\Users\\levallois\\open\\nocode-app-functions\\gexf-vosviewer-converter\\testswebapp\\" : "html/vosviewer/data/";
        String subfolder;
        String vosviewerJsonFileName = "vosviewer_" + Faces.getSessionId().substring(0, 20) + ".json";
        if (shareVVPublicly) {
            subfolder = "public/";
        } else {
            subfolder = "private/";
        }

        path = path + subfolder;

        BufferedWriter bw = Files.newBufferedWriter(Path.of(path + vosviewerJsonFileName), StandardCharsets.UTF_8);
        bw.write(graphAsJsonVosViewer);
        bw.close();

        ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
        String urlVV;
        if (RemoteLocal.isTest()) {
            urlVV = "https://test.nocodefunctions.com/html/vosviewer/index.html?json=data/" + subfolder + vosviewerJsonFileName;
        } else {
            urlVV = "https://nocodefunctions.com/html/vosviewer/index.html?json=data/" + subfolder + vosviewerJsonFileName;
        }
        externalContext.redirect(urlVV);
    }

    public String getOption() {
        return option;
    }

    public void setOption(String option) {
        this.option = option;
        if (option.equals("sourceGexf")) {
            setUploadButtonMessage(sessionBean.getLocaleBundle().getString("general.message.choose_gexf_file"));
            renderGephiWarning = true;
        }
        if (option.equals("sourceVV")) {
            setUploadButtonMessage(sessionBean.getLocaleBundle().getString("general.message.choose_vosviewer_file"));
            renderGephiWarning = false;
        }
    }

    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getLinkStrength() {
        return linkStrength;
    }

    public void setLinkStrength(String linkStrength) {
        this.linkStrength = linkStrength;
    }

    public String getUploadButtonMessage() {
        return uploadButtonMessage;
    }

    public void setUploadButtonMessage(String uploadButtonMessage) {
        this.uploadButtonMessage = uploadButtonMessage;
    }

    public boolean isShareVVPublicly() {
        return shareVVPublicly;
    }

    public void setShareVVPublicly(boolean shareVVPublicly) {
        this.shareVVPublicly = shareVVPublicly;
    }

    public boolean isRenderGephiWarning() {
        return renderGephiWarning;
    }

    public void setRenderGephiWarning(boolean renderGephiWarning) {
        this.renderGephiWarning = renderGephiWarning;
    }

    public StreamedContent getFileToSave() throws FileNotFoundException {
        StreamedContent fileStream = null;
        try {
            if (is == null) {
                System.out.println("no file found for conversion to gephi");
                return null;
            }

            HttpRequest request;
            HttpClient client = HttpClient.newHttpClient();
            Set<CompletableFuture> futures = new HashSet();

            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(is.readAllBytes());

            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(7002)
                    .withHost("localhost")
                    .withPath("api/convert2gexf")
                    .toUri();

            request = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .uri(uri)
                    .build();

            CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(
                    resp -> {
                        gexfAsByteArray = resp.body();
                    }
            );

            futures.add(future);

            CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
            combinedFuture.join();

            InputStream inputStreamToSave = new ByteArrayInputStream(gexfAsByteArray);
            fileStream = DefaultStreamedContent.builder()
                    .name("results.gexf")
                    .contentType("application/gexf+xml")
                    .stream(() -> inputStreamToSave)
                    .build();

        } catch (IOException ex) {
                    System.out.println("ex:"+ ex.getMessage());
        }
        return fileStream;
    }

    public void setFileToSave(StreamedContent fileToSave) {
        this.fileToSave = fileToSave;
    }

}
