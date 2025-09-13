package net.clementlevallois.nocodeapp.web.front.flows.regionextractor;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.clementlevallois.functions.model.FunctionRegionExtract;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.exceptions.NocodeApplicationException;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.utils.FacesUtils;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

@Named
@ViewScoped
public class RegionExtractorResultsBean implements Serializable {

    @Inject
    private SessionBean sessionBean;

    @Inject
    private MicroserviceHttpClient microserviceClient;

    private RegionExtractorState.ResultsReady results;

    @PostConstruct
    public void init() {
        if (sessionBean.getFlowState() instanceof RegionExtractorState.ResultsReady rr) {
            this.results = rr;
        } else {
            FacesUtils.redirectTo("regionextractor.html");
        }
    }

    public StreamedContent getFileToSave() {
        String jobId = results.jobId();
        String callbackURL = RemoteLocal.getDomain() + RemoteLocal.getInternalMessageApiEndpoint() + FunctionRegionExtract.NAME;

        try {
            CompletableFuture<byte[]> futureBytes = microserviceClient.exportService().get("region_extractor_results")
                    .addQueryParameter("jobId", jobId)
                    .addQueryParameter("callbackURL", callbackURL)
                    .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofByteArray());

            // Block to get the result for StreamedContent
            byte[] body = futureBytes.join();

            try (InputStream is = new ByteArrayInputStream(body)) {
                return DefaultStreamedContent.builder()
                        .name("text_in_selected_region.xlsx")
                        .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .stream(() -> is)
                        .build();
            } catch (IOException e) {
                throw new NocodeApplicationException("An IO error occurred", e);
            }

        } catch (CompletionException cex) {
            Throwable cause = cex.getCause();
            String errorMessage = "Error exporting data: " + cause.getMessage();
            if (cause instanceof MicroserviceHttpClient.MicroserviceCallException msce) {
                errorMessage = "Error exporting data: Status " + msce.getStatusCode() + ", " + msce.getErrorBody();
            }
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Export Failed", errorMessage);
            return new DefaultStreamedContent();
        } catch (NocodeApplicationException ex) {
            throw new NocodeApplicationException("An IO error occurred", ex);
        }
    }

    public void setFileToSave(StreamedContent fileToSave) {
    }

}
