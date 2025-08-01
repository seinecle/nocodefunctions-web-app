/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows.spatialize;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.functions.model.FunctionSpatialization;
import static net.clementlevallois.functions.model.FunctionSpatialization.QueryParams.DURATION_IN_SECONDS;
import net.clementlevallois.functions.model.Globals;
import static net.clementlevallois.functions.model.Globals.GlobalQueryParams.CALLBACK_URL;
import static net.clementlevallois.functions.model.Globals.GlobalQueryParams.JOB_ID;
import net.clementlevallois.nocodeapp.web.front.MessageFromApi;
import net.clementlevallois.nocodeapp.web.front.WatchTower;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;

@ApplicationScoped
public class SpatializeService {

    private static final Logger LOG = Logger.getLogger(SpatializeService.class.getName());

    @Inject
    private MicroserviceHttpClient microserviceClient;

    @Inject
    private ApplicationPropertiesBean applicationProperties;

    public SpatializeState startAnalysis(SpatializeState.AwaitingParameters currentState) {
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

        AtomicReference<SpatializeState.FlowFailed> errorFlowFailed = new AtomicReference<>();
        AtomicReference<Boolean> isProcessSucessFul = new AtomicReference<>(true);

        requestBuilder.sendAsync(HttpResponse.BodyHandlers.ofByteArray())
                .thenAccept(resp -> {
                    if (resp.statusCode() != 200) {
                        LOG.log(Level.SEVERE, "Spatialize task submission failed for job {0}. Status: {1}, Body: {2}", new Object[]{jobId, resp.statusCode(), new String(resp.body(), StandardCharsets.UTF_8)});
                        errorFlowFailed.set(new SpatializeState.FlowFailed(jobId, currentState, "cowo task submission to remote service returned a not 200 code"));
                        isProcessSucessFul.set(Boolean.FALSE);
                    }
                })
                .exceptionally(ex -> {
                    LOG.log(Level.SEVERE, "Exception during Spatialize task submission for job " + jobId, ex);
                    errorFlowFailed.set(new SpatializeState.FlowFailed(jobId, currentState, "cowo task submission created an exceptional error"));
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

    public SpatializeState checkCompletion(SpatializeState.Processing currentState) {
        String jobId = currentState.jobId();
        Globals globals = new Globals(applicationProperties.getTempFolderFullPath());
        Path pathToSignalCompletion = globals.getWorkflowCompleteFilePath(jobId);

        if (Files.exists(pathToSignalCompletion)) {
            return finishAnalysis(currentState);
        }

        var messagesFromApi = WatchTower.getDequeAPIMessages().get(jobId);
        if (messagesFromApi != null) {
            for (MessageFromApi msg : messagesFromApi) {
                if (jobId.equals(msg.getjobId())) {
                    switch (msg.getInfo()) {
                        case ERROR -> {
                            messagesFromApi.remove(msg);
                            return new SpatializeState.FlowFailed(jobId, currentState.parameters(), msg.getMessage());
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

    private SpatializeState finishAnalysis(SpatializeState.Processing currentState) {
        String jobId = currentState.jobId();
        Globals globals = new Globals(applicationProperties.getTempFolderFullPath());
        try {
            Path gexfPath = globals.getGexfCompleteFilePath(jobId);
            String gexf = Files.readString(gexfPath, StandardCharsets.UTF_8);
            return new SpatializeState.ResultsReady(jobId, gexf);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Error finalizing spatialization analysis for job " + jobId, ex);
            return new SpatializeState.FlowFailed(jobId, currentState.parameters(), "Could not read GEXF file: " + ex.getMessage());
        }
    }
}
