package net.clementlevallois.nocodeapp.web.front.flows.umigon;

import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.List;
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

    @PostConstruct
    public void init() {
        if (sessionBean.getFlowState() instanceof UmigonState.ResultsReady rr) {
            this.results = rr;
        } else {
            FacesUtils.redirectTo("umigon-import.html");
        }
    }

    public List<Document> getResults() {
        return results != null ? results.results() : List.of();
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
}
