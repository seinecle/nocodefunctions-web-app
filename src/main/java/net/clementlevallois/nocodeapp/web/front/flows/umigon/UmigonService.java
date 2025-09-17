package net.clementlevallois.nocodeapp.web.front.flows.umigon;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import net.clementlevallois.functions.model.FunctionUmigon;
import net.clementlevallois.umigon.model.classification.Document;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.nocodeapp.web.front.MessageFromApi;
import net.clementlevallois.nocodeapp.web.front.WatchTower;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowFailed;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

@ApplicationScoped
public class UmigonService {

    @Inject
    private MicroserviceHttpClient microserviceClient;

    @Inject
    private ApplicationPropertiesBean applicationProperties;

    @Inject
    private SessionBean sessionBean;

    public FlowState startAnalysis(UmigonState.AwaitingParameters currentState) {
        String jobId = currentState.jobId();
        String callbackURL = RemoteLocal.getDomain() + RemoteLocal.getInternalMessageApiEndpoint() + FunctionUmigon.ENDPOINT;

        var requestBuilder = microserviceClient.api().get(FunctionUmigon.ENDPOINT_DATA_FROM_FILE);
        String owner = applicationProperties.getPrivateProperties().getProperty("pwdOwner");
        for (FunctionUmigon.QueryParams param : FunctionUmigon.QueryParams.values()) {
            String value = switch (param) {
                case TEXT_LANG ->
                    currentState.selectedLanguage();
                case EXPLANATION ->
                    "on";
                case SHORTER ->
                    "true";
                case OWNER ->
                    owner;
                case OUTPUT_FORMAT ->
                    "bytes";
                case EXPLANATION_LANG ->
                    sessionBean.getCurrentLocale().toLanguageTag();
            };

            requestBuilder.addQueryParameter(param.name(), value);
        }

        for (Globals.GlobalQueryParams param : Globals.GlobalQueryParams.values()) {
            String value = switch (param) {
                case JOB_ID ->
                    jobId;
                case CALLBACK_URL ->
                    callbackURL;
            };
            requestBuilder.addQueryParameter(param.name(), value);
        }

        AtomicReference<FlowFailed> errorFlowFailed = new AtomicReference<>();
        AtomicReference<Boolean> isProcessSuccessful = new AtomicReference<>(true);

        requestBuilder.sendAsync(HttpResponse.BodyHandlers.ofByteArray())
                .thenAccept(resp -> {
                    if (resp.statusCode() != 200) {
                        errorFlowFailed.set(new FlowFailed(jobId, currentState, "Umigon task submission to remote service returned a non-200 code"));
                        isProcessSuccessful.set(Boolean.FALSE);
                    }
                })
                .exceptionally(ex -> {
                    errorFlowFailed.set(new FlowFailed(jobId, currentState, "Umigon task submission created an exceptional error"));
                    isProcessSuccessful.set(Boolean.FALSE);
                    return null;
                });

        if (isProcessSuccessful.get()) {
            return new UmigonState.Processing(jobId, currentState, 0);
        } else {
            return errorFlowFailed.get();
        }
    }

    public FlowState checkCompletion(UmigonState.Processing currentState) {
        String jobId = currentState.jobId();
        Globals globals = new Globals(applicationProperties.getTempFolderFullPath());
        Path resultsPath = globals.getResultInBinaryFormat(currentState.jobId());

        if (Files.exists(resultsPath)) {
            return processResults(currentState);
        }

        var messagesFromApi = WatchTower.getDequeAPIMessages().get(jobId);
        if (messagesFromApi != null) {
            for (MessageFromApi msg : messagesFromApi) {
                if (jobId.equals(msg.getjobId())) {
                    switch (msg.getInfo()) {
                        case ERROR -> {
                            messagesFromApi.remove(msg);
                            return new FlowFailed(jobId, currentState.parameters(), msg.getMessage());
                        }
                        case PROGRESS -> {
                            if (msg.getProgress() != null) {
                                messagesFromApi.remove(msg);
                                return currentState.withProgress(msg.getProgress());
                            }
                        }
                        case RESULT_ARRIVED -> {
                            messagesFromApi.remove(msg);
                            return processResults(currentState);
                        }
                    }
                }
            }
        }
        return currentState;
    }

    private FlowState processResults(UmigonState.Processing currentState) {
        Globals globals = new Globals(applicationProperties.getTempFolderFullPath());
        Path resultsPath = globals.getResultInBinaryFormat(currentState.jobId());
        try {
            byte[] resultsBytes = Files.readAllBytes(resultsPath);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(resultsBytes); ObjectInputStream ois = new ObjectInputStream(bais)) {

                @SuppressWarnings("unchecked")
                List<Document> documents = (List<Document>) ois.readObject();
                return new UmigonState.ResultsReady(currentState.jobId(), documents);
            }
        } catch (IOException | ClassNotFoundException e) {
            return new FlowFailed(currentState.jobId(), currentState.parameters(), "Error reading or deserializing results.");
        }
    }

    public StreamedContent createExcelFileFromBinaryResults(String lang, String jobId) {
        if (jobId == null || jobId.isEmpty()) {
            return new DefaultStreamedContent();
        }
        try {
            CompletableFuture<byte[]> futureBytes = microserviceClient.exportService().get("xlsx/umigon")
                    .addQueryParameter("lang", lang)
                    .addQueryParameter("jobId", jobId)
                    .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofByteArray());

            byte[] body = futureBytes.join();

            InputStream is = new ByteArrayInputStream(body);
            return DefaultStreamedContent.builder()
                    .name("results_topics.xlsx")
                    .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .stream(() -> is)
                    .build();

        } catch (CompletionException cex) {
            Throwable cause = cex.getCause();
            String errorMessage = "Error exporting data to Excel: " + cause.getMessage();
            if (cause instanceof MicroserviceHttpClient.MicroserviceCallException msce) {
                errorMessage = "Error exporting data: Status " + msce.getStatusCode() + ", " + msce.getErrorBody();
            }
            return new DefaultStreamedContent();
        }
    }

}
