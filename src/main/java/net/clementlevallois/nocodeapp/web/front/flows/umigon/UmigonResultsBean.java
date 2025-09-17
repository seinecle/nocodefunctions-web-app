package net.clementlevallois.nocodeapp.web.front.flows.umigon;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
import net.clementlevallois.nocodeapp.web.front.utils.FacesUtils;
import net.clementlevallois.umigon.model.classification.Document;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

@Named
@ViewScoped
public class UmigonResultsBean implements Serializable {

    @Inject
    private SessionBean sessionBean;

    private UmigonState.ResultsReady results;

    private List<Document> filteredDocuments;

    @PostConstruct
    public void init() {
        if (sessionBean.getFlowState() instanceof UmigonState.ResultsReady rr) {
            this.results = rr;
        } else {
            FacesUtils.redirectTo("umigon-import.xhtml");
        }
    }

    public List<Document> getResults() {
        return results != null ? results.results() : List.of();
    }

    public List<SelectItem> getSentiments() {
        List<SelectItem> items = new ArrayList<>();
        ResourceBundle bundle = FacesContext.getCurrentInstance().getApplication().getResourceBundle(FacesContext.getCurrentInstance(), "text");
        items.add(new SelectItem("_11", bundle.getString("general.nouns.sentiment_positive")));
        items.add(new SelectItem("_12", bundle.getString("general.nouns.sentiment_negative")));
        items.add(new SelectItem("0", bundle.getString("general.nouns.sentiment_neutral")));
        return items;
    }

    public void showExplanation(int rowId) {
        if (results != null && results.results().size() > rowId) {
            results.results().get(rowId).setShowExplanation(true);
        }
    }

    public void hideExplanation(int rowId) {
        if (results != null && results.results().size() > rowId) {
            results.results().get(rowId).setShowExplanation(false);
        }
    }

    public void signal(int rowId) {
        if (results != null && results.results().size() > rowId) {
            results.results().get(rowId).setFlaggedAsFalseLabel(true);
        }
    }

    public StreamedContent getFileToSave() {
        if (results == null || results.results().isEmpty()) {
            return new DefaultStreamedContent();
        }
        try {
            byte[] documentsAsByteArray = Converters.byteArraySerializerForAnyObject(results.results());

            try (InputStream is = new ByteArrayInputStream(documentsAsByteArray)) {
                return DefaultStreamedContent.builder()
                        .name("results_umigon.xlsx")
                        .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .stream(() -> is)
                        .build();
            } catch (IOException e) {
                throw new RuntimeException("Error creating StreamedContent from results", e);
            }

        } catch (IOException ex) {
            throw new RuntimeException("Error serializing results", ex);
        }
    }

    public List<Document> getFilteredDocuments() {
        return filteredDocuments;
    }

    public void setFilteredDocuments(List<Document> filteredDocuments) {
        this.filteredDocuments = filteredDocuments;
    }
}
