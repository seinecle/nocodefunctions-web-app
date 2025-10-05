package net.clementlevallois.nocodeapp.web.front.flows.topics;

import jakarta.annotation.PostConstruct;
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
import net.clementlevallois.nocodeapp.web.front.io.ImportersService;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;

@Named
@ViewScoped
public class TopicsDataInputBean implements Serializable {

    private int selectedTab;
    private String jobId;
    private String exclusionTerms;
    private String url;
    private String jsonKey;
    private String websiteUrl;
    private int maxUrlsToCrawl = 10;
    private final List<String> uploadedFileNames = new ArrayList<>();

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    private ImportersService importersService;

    @Inject
    private SessionBean sessionBean;

    @PostConstruct
    public void init() {
        this.jobId = null;
        this.url = null;
        this.websiteUrl = null;
        this.uploadedFileNames.clear();
        // Initialize with default parameters for the topics workflow
        TopicsState.AwaitingParameters awaitingParameters = new TopicsState.AwaitingParameters(
                null, // jobId will be set later
                "en", // selectedLanguage
                50, // precision
                3, // minCharNumber
                2, // minTermFreq
                false, // scientificCorpus
                false, // replaceStopwords
                true, // lemmatize
                false, // removeNonAsciiCharacters
                null // uploaded user stopwords
        );
        sessionBean.setFlowState(awaitingParameters);
    }

    public void handleFileUpload(FileUploadEvent event) {
        if (event.getFile() == null || event.getFile().getFileName() == null || event.getFile().getFileName().isBlank()) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "File Upload Error", "No file was selected or the file is empty.");
            return;
        }
        TopicsDataSource dataSource = new TopicsDataSource.FileUpload(List.of(event.getFile()));
        processTopicsDataSource(dataSource);
        uploadedFileNames.add(event.getFile().getFileName());
    }

    public void processWebPage() {
        if (url == null || url.isBlank()) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Input Error", "Please provide a valid URL.");
            return;
        }
        TopicsDataSource dataSource = new TopicsDataSource.WebPage(url);
        processTopicsDataSource(dataSource);
    }

    public void processWebSite() {
        if (websiteUrl == null || websiteUrl.isBlank()) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Input Error", "Please provide a valid website URL.");
            return;
        }
        TopicsDataSource dataSource = new TopicsDataSource.WebSite(websiteUrl, maxUrlsToCrawl, exclusionTerms);
        processTopicsDataSource(dataSource);
    }

    private void processTopicsDataSource(TopicsDataSource dataSource) {
        // Create a new job ID if this is the first data processing action
        if (this.jobId == null) {
            this.jobId = UUID.randomUUID().toString().substring(0, 10);
        }
        Path jobDirectory = applicationProperties.getTempFolderFullPath().resolve(jobId);
        try {
            Files.createDirectories(jobDirectory);
        } catch (IOException ex) {
            throw new NocodeApplicationException("An IO error occurred", ex);
        }

        sessionBean.sendFunctionPageReport(Globals.Names.TOPICS.name());

        ImportersService.PreparationResult result = switch (dataSource) {
            case TopicsDataSource.FileUpload(List<UploadedFile> files) ->
                importersService.handleFileUpload(files, jobId, Globals.Names.TOPICS);
            case TopicsDataSource.WebPage(String url) ->
                importersService.parseWebPage(url, jobId);
            case TopicsDataSource.WebSite(String rootUrl, int maxUrls, String exclusionTermsParams) ->
                importersService.crawlWebSite(rootUrl, maxUrls, exclusionTerms, jobId);
        };

        if (result instanceof ImportersService.PreparationResult.Failure(String error)) {
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Data Preparation Failed", error);
        } else {
            String successMessage = switch (dataSource) {
                case TopicsDataSource.FileUpload f ->
                    f.files().getFirst().getFileName() + " has been added to your dataset.";
                case TopicsDataSource.WebPage w ->
                    "The web page content has been added to your dataset.";
                case TopicsDataSource.WebSite ws ->
                    "The website content has been added to your dataset.";
                default ->
                    "Data has been successfully processed.";
            };
            sessionBean.addMessage(FacesMessage.SEVERITY_INFO, "Success", successMessage);
        }
    }

    public String proceedToParameters() {
        if (jobId == null) {
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "You must first import at least one data source.");
            return null;
        }

        if (sessionBean.getFlowState() instanceof TopicsState.AwaitingParameters p) {
            sessionBean.setFlowState(p.withJobId(this.jobId));
        }

        return "workflow-topics.xhtml?faces-redirect=true";
    }

    public boolean isDataReady() {
        return this.jobId != null;
    }

    // Standard Getters and Setters
    public String getJobId() {
        return jobId;
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

    public String getExclusionTerms() {
        return exclusionTerms;
    }

    public void setExclusionTerms(String exclusionTerms) {
        this.exclusionTerms = exclusionTerms;
    }

    public List<String> getUploadedFileNames() {
        return uploadedFileNames;
    }

    public int getSelectedTab() {
        return selectedTab;
    }

    public void setSelectedTab(int selectedTab) {
        this.selectedTab = selectedTab;
    }

    public String getJsonKey() {
        return jsonKey;
    }

    public void setJsonKey(String jsonKey) {
        this.jsonKey = jsonKey;
    }
}
