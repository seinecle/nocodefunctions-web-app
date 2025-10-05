package net.clementlevallois.nocodeapp.web.front.flows.umigon;

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

@Named
@ViewScoped
public class UmigonDataInputBean implements Serializable {

    private int selectedTab;
    private String jobId;
    private String exclusionTerms;
    private String url;
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
        UmigonState.AwaitingParameters awaitingParameters = new UmigonState.AwaitingParameters(
                null, // jobId to be set later
                "en", // default language
                10000 // maxCapacity default
        );
        sessionBean.setFlowState(awaitingParameters);
    }

    public void handleFileUpload(FileUploadEvent event) {
        if (event.getFile() == null || event.getFile().getFileName() == null || event.getFile().getFileName().isBlank()) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "File Upload Error", "No file was selected or the file is empty.");
            return;
        }
        UmigonDataSource dataSource = new UmigonDataSource.FileUpload(List.of(event.getFile()));
        processUmigonDataSource(dataSource);
        uploadedFileNames.add(event.getFile().getFileName());
    }

    public void processWebPage() {
        if (url == null || url.isBlank()) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Input Error", "Please provide a valid URL.");
            return;
        }
        UmigonDataSource dataSource = new UmigonDataSource.WebPage(url);
        processUmigonDataSource(dataSource);
    }

    public void processWebSite() {
        if (websiteUrl == null || websiteUrl.isBlank()) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Input Error", "Please provide a valid website URL.");
            return;
        }
        UmigonDataSource dataSource = new UmigonDataSource.WebSite(websiteUrl, maxUrlsToCrawl, exclusionTerms);
        processUmigonDataSource(dataSource);
    }

    private void processUmigonDataSource(UmigonDataSource dataSource) {
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

        sessionBean.sendFunctionPageReport(Globals.Names.UMIGON.name());

        ImportersService.PreparationResult result = switch (dataSource) {
            case UmigonDataSource.FileUpload fileUpload ->
                importersService.handleFileUpload(fileUpload.files(), jobId, Globals.Names.UMIGON);
            case UmigonDataSource.WebPage webPage ->
                importersService.parseWebPage(webPage.url(), jobId);
            case UmigonDataSource.WebSite webSite ->
                importersService.crawlWebSite(webSite.rootUrl(), webSite.maxUrls(), webSite.exclusionTerms(), jobId);
        };

        if (result instanceof ImportersService.PreparationResult.Failure failure) {
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Data Preparation Failed", failure.errorMessage());
        } else {
            String successMessage = switch (dataSource) {
                case UmigonDataSource.FileUpload f ->
                    f.files().getFirst().getFileName() + " has been added to your dataset.";
                case UmigonDataSource.WebPage w ->
                    "The web page content has been added to your dataset.";
                case UmigonDataSource.WebSite ws ->
                    "The website content has been added to your dataset.";
            };
            sessionBean.addMessage(FacesMessage.SEVERITY_INFO, "Success", successMessage);
        }
    }

    public String proceedToParameters() {
        if (jobId == null) {
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "You must first import at least one data source.");
            return null;
        }

        if (sessionBean.getFlowState() instanceof UmigonState.AwaitingParameters p) {
            sessionBean.setFlowState(p.withJobId(this.jobId));
        }

        return "umigon.xhtml?faces-redirect=true";
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
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
}
