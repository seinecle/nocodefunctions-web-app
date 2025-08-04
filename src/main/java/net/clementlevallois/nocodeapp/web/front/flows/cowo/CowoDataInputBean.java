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
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.flows.topics.TopicsDataInputBean;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;

@Named
@ViewScoped
public class CowoDataInputBean implements Serializable {

    private String jobId;
    private String url;
    private String websiteUrl;
    private String jsonKey;
    private int maxUrlsToCrawl = 10;
    private final List<String> uploadedFileNames = new ArrayList<>();
    private static final Logger LOG = Logger.getLogger(TopicsDataInputBean.class.getName());

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    private ImportersService importersService;

    @Inject
    private SessionBean sessionBean;

    public void init() {
        this.jobId = null;
        this.url = null;
        this.websiteUrl = null;
        this.uploadedFileNames.clear();
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
        sessionBean.setCowoState(awaitingParameters);
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
        if (this.jobId == null) {
            this.jobId = UUID.randomUUID().toString().substring(0, 10);
        }
        Path jobDirectory = applicationProperties.getTempFolderFullPath().resolve(jobId);
        try {
            Files.createDirectories(jobDirectory);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, () -> "unable to create directories for job " + jobId);
        }

        sessionBean.sendFunctionPageReport(Globals.Names.COWO.name());

        ImportersService.PreparationResult result = switch (dataSource) {
            case CowoDataSource.FileUpload(List<UploadedFile> files) ->
                importersService.handleFileUpload(files, jobId);
            case CowoDataSource.WebPage(String url_param) ->
                importersService.parseWebPage(url, jobId);
            case CowoDataSource.WebSite(String rootUrl, int maxUrls, String exclusionTerms) ->
                importersService.crawlWebSite(rootUrl, maxUrls, exclusionTerms, jobId);
        };

        if (result instanceof ImportersService.PreparationResult.Failure(String error)) {
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Data Preparation Failed", error);
        } else if (dataSource instanceof CowoDataSource.FileUpload) {
            sessionBean.addMessage(FacesMessage.SEVERITY_INFO, "File Processed", "The file has been added to your dataset.");
        }
    }

    public String proceedToParameters() {
        if (jobId == null) {
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "You must first import at least one data source.");
            return null;
        }

        CowoState currentState = sessionBean.getCowoState();

        if (currentState instanceof CowoState.AwaitingParameters p) {
            sessionBean.setCowoState(p.withJobId(this.jobId));
        }

        return "workflow-cowo?faces-redirect=true";
    }

    public boolean isDataReady() {
        return this.jobId != null;
    }

    public String getJobId() {
        return this.jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
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
