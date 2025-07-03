package net.clementlevallois.nocodeapp.web.front.flows;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import org.primefaces.model.file.UploadedFile;

@Named
@ViewScoped
public class TopicsDataInputBean implements Serializable {

    @Inject
    private WorkflowSessionBean workflowSessionBean;

    @Inject
    private DataPreparationCommons dataPreparationService;
    
    @Inject
    private SessionBean sessionBean;

    private int selectedTab;
    private List<UploadedFile> uploadedFiles;
    private String url;
    private String websiteRootUrl;
    private int maxUrls = 10;
    private String exclusionTerms;

    @PostConstruct
    public void init() {
        // Initialize the workflow state when the view is first accessed.
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        workflowSessionBean.setTopicsState(new TopicsState.AwaitingParameters(uniqueId, null, 2, 3, 2, false, true, true, false, null));
    }

    public String startAnalysis() {
        DataSource dataSource;
        switch (selectedTab) {
            case 0 -> {
                if (uploadedFiles == null || uploadedFiles.isEmpty() || uploadedFiles.getFirst() == null) {
                    FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, sessionBean.getLocaleBundle().getString("general.message.warning_title"), sessionBean.getLocaleBundle().getString("data_import.error.no_file_uploaded")));
                    return "";
                }
                dataSource = new DataSource.FileUpload(uploadedFiles);
            }
            case 1 -> {
                if (url == null || url.isBlank()) {
                    FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, sessionBean.getLocaleBundle().getString("general.message.warning_title"), sessionBean.getLocaleBundle().getString("data_import.error.url_not_provided")));
                    return "";
                }
                dataSource = new DataSource.WebPage(url);
            }
            case 2 -> {
                if (websiteRootUrl == null || websiteRootUrl.isBlank()) {
                    FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, sessionBean.getLocaleBundle().getString("general.message.warning_title"), sessionBean.getLocaleBundle().getString("data_import.error.url_not_provided")));
                    return "";
                }
                List<String> exclusions = (exclusionTerms == null || exclusionTerms.isBlank())
                        ? new ArrayList<>()
                        : List.of(exclusionTerms.split("\\s*,\\s*"));
                dataSource = new DataSource.WebSite(websiteRootUrl, maxUrls, exclusions);
            }
            default -> {
                FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Error", "Invalid data source selected."));
                return "";
            }
        }

        dataPreparationService.prepare(dataSource, workflowSessionBean.getTopicsState().jobId());
        return "workflow-topics.html?faces-redirect=true";
    }

    // Getters and Setters
    public int getSelectedTab() {
        return selectedTab;
    }

    public void setSelectedTab(int selectedTab) {
        this.selectedTab = selectedTab;
    }

    public List<UploadedFile> getUploadedFiles() {
        return uploadedFiles;
    }

    public void setUploadedFiles(List<UploadedFile> uploadedFiles) {
        this.uploadedFiles = uploadedFiles;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getWebsiteRootUrl() {
        return websiteRootUrl;
    }

    public void setWebsiteRootUrl(String websiteRootUrl) {
        this.websiteRootUrl = websiteRootUrl;
    }

    public int getMaxUrls() {
        return maxUrls;
    }

    public void setMaxUrls(int maxUrls) {
        this.maxUrls = maxUrls;
    }

    public String getExclusionTerms() {
        return exclusionTerms;
    }

    public void setExclusionTerms(String exclusionTerms) {
        this.exclusionTerms = exclusionTerms;
    }
}
