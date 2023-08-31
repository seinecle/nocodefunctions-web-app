package net.clementlevallois.nocodeapp.web.front.functions;

import io.mikael.urlbuilder.UrlBuilder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.annotation.MultipartConfig;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonBean;
import net.clementlevallois.nocodeapp.web.front.exportdata.ExportToVosViewer;
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

    public void gotoVV() {
        String linkToVosViewer = ExportToVosViewer.exportAndReturnLinkFromUploadedFile(uploadedFile, shareVVPublicly, SingletonBean.getPrivateProperties(), item, link, linkStrength);
        if (linkToVosViewer != null && !linkToVosViewer.isBlank()) {
            try {
                ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
                externalContext.redirect(linkToVosViewer);
            } catch (IOException ex) {
                System.out.println("error in ops for export to vv");
            }
        }
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

    public StreamedContent getFileToSave() {
        StreamedContent fileStream = null;
        try {
            if (is == null) {
                System.out.println("no file found for conversion to gephi");
                return null;
            }

            HttpRequest request;
            HttpClient client = HttpClient.newHttpClient();

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

            HttpResponse<byte[]> resp = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 200) {
                gexfAsByteArray = resp.body();
            } else {
                gexfAsByteArray = null;
            }

            if (gexfAsByteArray == null) {
                System.out.println("gexfAsByteArray returned by the API was not a 200 code");
                String error = sessionBean.getLocaleBundle().getString("general.nouns.error");
                FacesMessage message = new FacesMessage(error, error);
                FacesContext.getCurrentInstance().addMessage(null, message);
                return null;
            }

            InputStream inputStreamToSave = new ByteArrayInputStream(gexfAsByteArray);
            fileStream = DefaultStreamedContent.builder()
                    .name("results.gexf")
                    .contentType("application/gexf+xml")
                    .stream(() -> inputStreamToSave)
                    .build();

        } catch (IOException | InterruptedException ex) {
            System.out.println("ex:" + ex.getMessage());
        }
        return fileStream;
    }

    public void setFileToSave(StreamedContent fileToSave) {
        this.fileToSave = fileToSave;
    }

}
