package net.clementlevallois.nocodeapp.web.front.flows.spatialize;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import net.clementlevallois.functions.model.FunctionSpatialization;
import static net.clementlevallois.functions.model.FunctionSpatialization.QueryParams.DURATION_IN_SECONDS;
import net.clementlevallois.functions.model.Globals;
import static net.clementlevallois.functions.model.Globals.GlobalQueryParams.CALLBACK_URL;
import static net.clementlevallois.functions.model.Globals.GlobalQueryParams.JOB_ID;
import net.clementlevallois.nocodeapp.web.front.MessageFromApi;
import net.clementlevallois.nocodeapp.web.front.WatchTower;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.exceptions.NocodeApplicationException;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowFailed;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;

@ApplicationScoped
public class SpatializeService {

    @Inject
    private MicroserviceHttpClient microserviceClient;

    @Inject
    private ApplicationPropertiesBean applicationProperties;

    public FlowState startAnalysis(SpatializeState.AwaitingParameters currentState) {
        String jobId = currentState.jobId();
        String callbackURL = RemoteLocal.getDomain() + RemoteLocal.getInternalMessageApiEndpoint() + FunctionSpatialization.ENDPOINT;

        var requestBuilder = microserviceClient.api()
                .get(FunctionSpatialization.ENDPOINT);

        addQueryParams(requestBuilder, currentState);

        for (Globals.GlobalQueryParams param : Globals.GlobalQueryParams.values()) {
            String paramValue = switch (param) {
                case JOB_ID ->
                    jobId;
                case CALLBACK_URL ->
                    callbackURL;
            };
            requestBuilder.addQueryParameter(param.name(), paramValue);
        }

        AtomicReference<FlowFailed> errorFlowFailed = new AtomicReference<>();
        AtomicReference<Boolean> isProcessSucessFul = new AtomicReference<>(true);

        requestBuilder.sendAsync(HttpResponse.BodyHandlers.ofByteArray())
                .thenAccept(resp -> {
                    if (resp.statusCode() != 200) {
                        errorFlowFailed.set(new FlowFailed(jobId, currentState, "cowo task submission to remote service returned a not 200 code"));
                        isProcessSucessFul.set(Boolean.FALSE);
                    }
                })
                .exceptionally(ex -> {
                    errorFlowFailed.set(new FlowFailed(jobId, currentState, "cowo task submission created an exceptional error"));
                    isProcessSucessFul.set(Boolean.FALSE);
                    return null;
                });

        if (isProcessSucessFul.get()) {
            return new SpatializeState.Processing(jobId, currentState, 0);
        } else {
            return errorFlowFailed.get();
        }

    }

    private MicroserviceHttpClient.GetRequestBuilder addQueryParams(MicroserviceHttpClient.GetRequestBuilder requestBuilder, SpatializeState.AwaitingParameters currentState) {
        for (FunctionSpatialization.QueryParams param : FunctionSpatialization.QueryParams.values()) {
            String paramValue = switch (param) {
                case DURATION_IN_SECONDS ->
                    String.valueOf(currentState.durationInSecond());
            };
            requestBuilder.addQueryParameter(param.name(), paramValue);
        }
        return requestBuilder;

    }

    public FlowState checkCompletion(SpatializeState.Processing currentState) {
        String jobId = currentState.jobId();
        Globals globals = new Globals(applicationProperties.getTempFolderFullPath());
        Path pathToSignalCompletion = globals.getWorkflowCompleteFilePath(jobId);

        if (Files.exists(pathToSignalCompletion)) {
            return processSpatializationResults(currentState);
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
                    }
                }
            }
        }
        return currentState;
    }

    private FlowState processSpatializationResults(SpatializeState.Processing currentState) {
        String jobId = currentState.jobId();
        Globals globals = new Globals(applicationProperties.getTempFolderFullPath());
        try {
            Path gexfPath = globals.getGexfCompleteFilePath(jobId);
            String gexf = Files.readString(gexfPath, StandardCharsets.UTF_8);
            return new SpatializeState.ResultsReady(jobId, gexf);
        } catch (IOException ex) {
            throw new NocodeApplicationException("Error in processSpatializationResults method: Failed to read GEXF results for jobId: " + currentState.jobId(), ex);
        }
    }
}
