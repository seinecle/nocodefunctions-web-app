package net.clementlevallois.nocodeapp.web.front.flows.topics;

import jakarta.annotation.PostConstruct;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.exceptions.NocodeApplicationException;
import net.clementlevallois.nocodeapp.web.front.utils.GEXFSaver;
import net.clementlevallois.utils.Multiset;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

@Named
@ViewScoped
public class TopicsResultsBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(TopicsResultsBean.class.getName());

    @Inject
    private SessionBean sessionBean;
    @Inject
    private TopicsService topicsService;

    private TopicsState.ResultsReady results;

    @PostConstruct
    public void init() {
        if (sessionBean.getFlowState() instanceof TopicsState.ResultsReady results) {
            this.results = results;
        } else {
            try {
                FacesContext.getCurrentInstance().getExternalContext().redirect("topics-data-import.xhtml?faces-redirect=true");
            } catch (IOException ex) {
                throw new NocodeApplicationException("An IO error occurred", ex);
            }
        }
    }

    public List<Map.Entry<Integer, Multiset<String>>> getKeywords() {
        if (results.keywordsPerTopic() == null) {
            return Collections.emptyList();
        }
        return new java.util.ArrayList<>(results.keywordsPerTopic().entrySet());
    }

    public StreamedContent getGexfFile() {
        String jobId = results.jobId();
        String gexf = results.gexf();
        if (jobId == null || jobId.isEmpty()) {
            LOG.warning("Cannot provide GEXF file, jobId is null or empty.");
            return new DefaultStreamedContent();
        }
        if (gexf == null) {
            LOG.warning("Cannot provide GEXF file, gexf is null");
            return new DefaultStreamedContent();
        }
        StreamedContent exportGexfAsStreamedFile = GEXFSaver.exportGexfAsStreamedFile(gexf, "network_file_with_topics");
        return exportGexfAsStreamedFile;
    }

    public StreamedContent getExcelFileToSave() {
        return topicsService.createExcelFileFromJsonSavedData(results.jobId());
    }

    public void setExcelFileToSave() {
    }

    public Boolean getShareVVPublicly() {
        return results != null && results.shareVVPublicly();
    }

    public void setShareVVPublicly(Boolean flag) {
        if (results != null) {
            sessionBean.setFlowState(results.withShareVVPublicly(flag));
            this.results = (TopicsState.ResultsReady) sessionBean.getFlowState();
        }
    }

    public Boolean getShareGephiLitePublicly() {
        return results != null && results.shareGephiLitePublicly();
    }

    public void setShareGephiLitePublicly(Boolean flag) {
        if (results != null) {
            sessionBean.setFlowState(results.withShareGephiLitePublicly(flag));
            this.results = (TopicsState.ResultsReady) sessionBean.getFlowState();
        }
    }
    
      public Map<Integer, Multiset<String>> getCommunitiesResult() {
        return results.keywordsPerTopic();
    }

    public void setCommunitiesResult(Map<Integer, Multiset<String>> communitiesResult) {
    }
    
}
