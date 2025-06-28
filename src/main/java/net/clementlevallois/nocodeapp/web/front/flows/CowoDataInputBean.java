/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows;

import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import org.primefaces.event.FileUploadEvent;

@Named
@SessionScoped
public class CowoDataInputBean implements Serializable {

    private String jobId;
    private String url;
    private String websiteUrl;
    private int maxUrlsToCrawl = 10;
    private final List<String> uploadedFileNames = new ArrayList<>();

    @Inject
    private CowoDataPreparationService dataPreparationService;

    @Inject
    private SessionBean sessionBean;

    @Inject
    private WorkflowSessionBean workflowSessionBean;

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
        workflowSessionBean.setCowoState(awaitingParameters);
    }

    public void handleFileUpload(FileUploadEvent event) {
        if (event.getFile() == null) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "File Upload Error", "No file was uploaded.");
            return;
        }
        // For multiple file uploads, the listener is called for each file.
        // We will handle them one by one.
        CowoDataSource dataSource = new CowoDataSource.FileUpload(List.of(event.getFile()));
        processDataSource(dataSource);
        uploadedFileNames.add(event.getFile().getFileName());
    }

    public void processWebPage() {
        if (url == null || url.isBlank()) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Input Error", "Please provide a valid URL.");
            return;
        }
        CowoDataSource dataSource = new CowoDataSource.WebPage(url);
        processDataSource(dataSource);
    }

    public void processWebSite() {
        if (websiteUrl == null || websiteUrl.isBlank()) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Input Error", "Please provide a valid website URL.");
            return;
        }
        CowoDataSource dataSource = new CowoDataSource.WebSite(websiteUrl, maxUrlsToCrawl, Collections.emptyList());
        processDataSource(dataSource);
    }

    private void processDataSource(CowoDataSource dataSource) {
        // If this is the first data processing action, create a new job ID.
        if (this.jobId == null) {
            this.jobId = UUID.randomUUID().toString().substring(0, 10);
        }

        var result = dataPreparationService.prepare(dataSource, this.jobId);

        if (result instanceof CowoDataPreparationService.PreparationResult.Failure(String error)) {
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Data Preparation Failed", error);
        } else if (dataSource instanceof CowoDataSource.FileUpload) {
            // For file uploads, we just show a success message for that file.
            sessionBean.addMessage(FacesMessage.SEVERITY_INFO, "File Processed", "The file has been added to your dataset.");
        }
    }

    public String proceedToParameters() {
        if (jobId == null) {
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "You must first import at least one data source.");
            return null;
        }

        CowoState currentState = workflowSessionBean.getCowoState();


        if (currentState instanceof CowoState.AwaitingParameters p) {
            workflowSessionBean.setCowoState(p.withJobId(this.jobId));
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
}
