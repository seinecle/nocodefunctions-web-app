/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows.cowo;

import net.clementlevallois.nocodeapp.web.front.io.ImportersService;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.exceptions.NocodeApplicationException;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;
import net.clementlevallois.nocodeapp.web.front.flows.cowo.CowoState.AwaitingDataSource;
import org.primefaces.event.FileUploadEvent;

@Named
@ViewScoped
public class CowoDataInputBean implements Serializable {

    private String url;
    private String websiteUrl;
    private String jsonKey;
    private int maxUrlsToCrawl = 10;
    private final List<String> uploadedFileNames = new ArrayList<>();

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    private ImportersService importersService;

    @Inject
    private SessionBean sessionBean;

    public void init() {
        this.url = null;
        this.websiteUrl = null;
        this.uploadedFileNames.clear();
        sessionBean.setFlowState(new AwaitingDataSource(null));
    }

    public void handleFileUpload(FileUploadEvent event) {
        if (event.getFile() == null) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "File Upload Error", "No file was uploaded.");
            return;
        }
        // For multiple file uploads, the listener is called for each file.
        CowoDataSource dataSource = new CowoDataSource.FileUpload(List.of(event.getFile()));
        processCowoDataSource(dataSource);
        uploadedFileNames.add(event.getFile().getFileName());
    }

    public void processWebPage() {
        if (url == null || url.isBlank()) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Input Error", "Please provide a valid URL.");
            return;
        }
        CowoDataSource dataSource = new CowoDataSource.WebPage(url);
        processCowoDataSource(dataSource);
    }

    public void processWebSite() {
        if (websiteUrl == null || websiteUrl.isBlank()) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Input Error", "Please provide a valid website URL.");
            return;
        }
        CowoDataSource dataSource = new CowoDataSource.WebSite(websiteUrl, maxUrlsToCrawl, "");
        processCowoDataSource(dataSource);
    }

    private void processCowoDataSource(CowoDataSource dataSource) {
        String jobId = UUID.randomUUID().toString().substring(0, 10);
        Path jobDirectory = applicationProperties.getTempFolderFullPath().resolve(jobId);
        try {
            Files.createDirectories(jobDirectory);
        } catch (IOException ex) {
            throw new NocodeApplicationException("An IO error occurred", ex);
        }

        sessionBean.sendFunctionPageReport(Globals.Names.COWO.name());

        ImportersService.PreparationResult result;
        switch (dataSource) {
            case CowoDataSource.FileUpload fileUpload ->
                result = importersService.handleFileUpload(fileUpload.files(), jobId, Globals.Names.COWO);
            case CowoDataSource.WebPage webPage ->
                result = importersService.parseWebPage(webPage.url(), jobId);
            case CowoDataSource.WebSite webSite ->
                result = importersService.crawlWebSite(webSite.rootUrl(), webSite.maxUrls(), webSite.exclusionTerms(), jobId);
            default ->
                throw new IllegalArgumentException("Unsupported data source type");
        }

        if (result instanceof ImportersService.PreparationResult.Failure(String error)) {
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Data Preparation Failed", error);
            throw new NocodeApplicationException("Data Preparation Failed", new Throwable(error));
        } else {
            // Initialize with default parameters
            CowoState.AwaitingParameters awaitingParameters = new CowoState.AwaitingParameters(
                    jobId,
                    new ArrayList<>(), // selectedLanguages
                    2, // minTermFreq
                    4, // maxNGram
                    false, // removeNonAsciiCharacters
                    false, // scientificCorpus
                    true, // firstNames
                    true, // lemmatize
                    false, // replaceStopwords
                    false, // usePMI
                    null, // fileUserStopwords
                    4 // minCharNumber
            );
            sessionBean.setFlowState(awaitingParameters);
            if (dataSource instanceof CowoDataSource.FileUpload) {
                sessionBean.addMessage(FacesMessage.SEVERITY_INFO, "File Processed", "The file has been added to your dataset.");
            }
        }
    }

    public String proceedToParameters() {

        FlowState currentState = sessionBean.getFlowState();

        if (currentState instanceof CowoState.AwaitingParameters p) {
            sessionBean.setFlowState(p.withJobId(p.jobId()));
        }

        return "workflow-cowo.html?faces-redirect=true";
    }

    public boolean isDataReady() {
        return sessionBean.getFlowState() instanceof CowoState.AwaitingParameters;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl;
    }

    public int getMaxUrlsToCrawl() {
        return maxUrlsToCrawl;
    }

    public void setMaxUrlsToCrawl(int maxUrlsToCrawl) {
        this.maxUrlsToCrawl = maxUrlsToCrawl;
    }

    public List<String> getUploadedFileNames() {
        return uploadedFileNames;
    }

    public String getJsonKey() {
        return jsonKey;
    }

    public void setJsonKey(String jsonKey) {
        this.jsonKey = jsonKey;
    }
}
