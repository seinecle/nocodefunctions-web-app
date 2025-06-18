package net.clementlevallois.nocodeapp.web.front.functions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.http.HttpResponse;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.annotation.MultipartConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.functions.model.FunctionNetworkConverter;
import net.clementlevallois.functions.model.Globals;
import static net.clementlevallois.functions.model.Globals.GlobalQueryParams.CALLBACK_URL;
import static net.clementlevallois.functions.model.Globals.GlobalQueryParams.JOB_ID;
import static net.clementlevallois.functions.model.Globals.GlobalQueryParams.SESSION_ID;
import net.clementlevallois.functions.model.WorkflowGazeProps;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.exportdata.ExportToVosViewer;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped
@MultipartConfig
public class ConverterBean implements Serializable {

    private String fileNameUploaded = "";
    private String option = "sourceGexf";
    private String item = "item_name";
    private String link = "link_name";
    private String linkStrength = "link strength name";
    private String uploadButtonMessage;
    private boolean renderGephiWarning = true;

    private boolean shareVVPublicly;

    private StreamedContent gexfFileToSave;
    private Properties privateProperties;

    private String jobId;
    private String sessionId;

    @Inject
    SessionBean sessionBean;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    private MicroserviceHttpClient httpClient;

    public ConverterBean() {
    }

    @PostConstruct
    public void init() {
        sessionBean.setFunction(FunctionNetworkConverter.NAME);
        privateProperties = applicationProperties.getPrivateProperties();
        uploadButtonMessage = sessionBean.getLocaleBundle().getString("general.message.choose_gexf_file");
        sessionId = FacesContext.getCurrentInstance().getExternalContext().getSessionId(false);
    }

    public String logout() {
        FacesContext.getCurrentInstance().getExternalContext().invalidateSession();
        return "/index?faces-redirect=true";
    }

    public String handleFileUpload(FileUploadEvent event) {
        try {
            byte[] readAllBytes = event.getFile().getInputStream().readAllBytes();
            fileNameUploaded = event.getFile().getFileName();
            sessionBean.sendFunctionPageReport();
            jobId = UUID.randomUUID().toString().substring(0, 10);
            Path tempFolderRelativePath = applicationProperties.getTempFolderFullPath();

            // as an obscure convention, gexf files are persisted with a _result extension - but not other files like json files
            String fileNameToPersist = fileNameUploaded.endsWith("gexf") ? jobId + "_result" : jobId;
            Path fullPathForFileContainingGexf = Path.of(tempFolderRelativePath.toString(), fileNameToPersist);

            String success = sessionBean.getLocaleBundle().getString("general.nouns.success");
            String is_uploaded = sessionBean.getLocaleBundle().getString("general.verb.is_uploaded");
            sessionBean.addMessage(FacesMessage.SEVERITY_INFO, success, event.getFile().getFileName() + " " + is_uploaded + ".");
            try {
                Files.write(fullPathForFileContainingGexf, readAllBytes);
            } catch (IOException ex) {
                System.out.println("ex:" + ex.getMessage());
            }
        } catch (IOException ex) {
            Logger.getLogger(ConverterBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "";
    }

    public void gotoVV() {
        String linkToVosViewer = ExportToVosViewer.exportAndReturnLinkForConversionToVV(httpClient, jobId, shareVVPublicly, applicationProperties, item, link, linkStrength);
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

    public StreamedContent getGexfFileToSave() {
        try {
            MicroserviceHttpClient.PostRequestBuilder requestBuilder = httpClient.api()
                    .post(FunctionNetworkConverter.ENDPOINT_VV_TO_GEXF);
            addGlobalQueryParams(requestBuilder);
            HttpResponse<byte[]> response = requestBuilder.sendAsync(HttpResponse.BodyHandlers.ofByteArray())
                    .join();

            InputStream inputStreamToSave = new ByteArrayInputStream(response.body());
            return DefaultStreamedContent.builder()
                    .name("results.gexf")
                    .contentType("application/gexf+xml")
                    .stream(() -> inputStreamToSave)
                    .build();

        } catch (CompletionException e) {
            if (e.getCause() instanceof MicroserviceHttpClient.MicroserviceCallException ex) {
                System.err.println("Response Body: " + ex.getErrorBody());
            } else {
                System.err.println("An unexpected error occurred: " + e.getMessage());
            }
            String error = sessionBean.getLocaleBundle().getString("general.nouns.error");
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, error, "Failed to retrieve the GEXF file.");
            return null;
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
            String error = sessionBean.getLocaleBundle().getString("general.nouns.error");
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, error, "An unexpected error occurred while processing your request.");
            return null;
        }
    }

    private MicroserviceHttpClient.PostRequestBuilder addGlobalQueryParams(MicroserviceHttpClient.PostRequestBuilder requestBuilder) {

        String callbackURL = RemoteLocal.getDomain() + RemoteLocal.getInternalMessageApiEndpoint() + FunctionNetworkConverter.ENDPOINT;

        for (Globals.GlobalQueryParams param : Globals.GlobalQueryParams.values()) {
            String paramValue = switch (param) {
                case SESSION_ID ->
                    sessionId;
                case JOB_ID ->
                    jobId;
                case CALLBACK_URL ->
                    callbackURL;
            };
            requestBuilder.addQueryParameter(param.name(), paramValue);
        }
        return requestBuilder;
    }

    public void setGexfFileToSave(StreamedContent gexfFileToSave) {
        this.gexfFileToSave = gexfFileToSave;
    }

    public String displayNameForSingleUploadedFileOrSeveralFiles() {
        if (fileNameUploaded != null) {
            return "🚚 " + sessionBean.getLocaleBundle().getString("back.import.one_file_uploaded") + ": " + fileNameUploaded;
        } else {
            return sessionBean.getLocaleBundle().getString("general.message.data_not_found");
        }
    }

    public String getFileNameUploaded() {
        return fileNameUploaded;
    }

    public void setFileNameUploaded(String fileNameUploaded) {
        this.fileNameUploaded = fileNameUploaded;
    }

}
