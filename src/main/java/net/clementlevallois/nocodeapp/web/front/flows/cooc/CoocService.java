/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows.cooc;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.StringReader;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.WorkflowCoocProps;
import net.clementlevallois.nocodeapp.web.front.MessageFromApi;
import net.clementlevallois.nocodeapp.web.front.WatchTower;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

@ApplicationScoped
public class CoocService {

    private static final Logger LOG = Logger.getLogger(CoocService.class.getName());

    @Inject
    private MicroserviceHttpClient microserviceClient;

    @Inject
    private ApplicationPropertiesBean applicationProperties;

    public CoocState startAnalysis(CoocState.AwaitingParameters currentState) {
        String jobId = currentState.jobId();
        String callbackURL = RemoteLocal.getDomain() + RemoteLocal.getInternalMessageApiEndpoint() + WorkflowCoocProps.ENDPOINT;

        var requestBuilder = microserviceClient.api().get(WorkflowCoocProps.ENDPOINT);

        for (WorkflowCoocProps.QueryParams param : WorkflowCoocProps.QueryParams.values()) {
            String paramValue = switch (param) {
                case MIN_SHARED_TARGETS ->
                    String.valueOf(currentState.minSharedTargets());
            };
            requestBuilder.addQueryParameter(param.name(), paramValue);
        }

        for (Globals.GlobalQueryParams param : Globals.GlobalQueryParams.values()) {
            String paramValue = switch (param) {
                case JOB_ID ->
                    jobId;
                case CALLBACK_URL ->
                    callbackURL;
            };
            requestBuilder.addQueryParameter(param.name(), paramValue);
        }

        AtomicReference<CoocState.FlowFailed> errorFlow = new AtomicReference<>();
        AtomicReference<Boolean> isOk = new AtomicReference<>(true);

        requestBuilder.sendAsync(HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                    if (resp.statusCode() != 200) {
                        LOG.log(Level.SEVERE, "Cooc task submission failed for job {0}. Status: {1}, Body: {2}",
                                new Object[]{jobId, resp.statusCode(), resp.body()});
                        errorFlow.set(new CoocState.FlowFailed(jobId, currentState, "cooc call failed with non-200 status"));
                        isOk.set(Boolean.FALSE);
                    }
                })
                .exceptionally(ex -> {
                    LOG.log(Level.SEVERE, "Exception during cooc submission for job " + jobId, ex);
                    errorFlow.set(new CoocState.FlowFailed(jobId, currentState, "cooc call threw exception"));
                    isOk.set(Boolean.FALSE);
                    return null;
                });

        return isOk.get() ? new CoocState.Processing(jobId, currentState, 0) : errorFlow.get();
    }

    public CoocState checkCompletion(CoocState.Processing currentState) {
        String jobId = currentState.jobId();
        Globals globals = new Globals(applicationProperties.getTempFolderFullPath());
        Path completeSignal = globals.getWorkflowCompleteFilePath(jobId);

        if (Files.exists(completeSignal)) {
            return finishAnalysis(currentState);
        }

        var messages = WatchTower.getDequeAPIMessages().get(jobId);
        if (messages != null) {
            for (MessageFromApi msg : messages) {
                if (jobId.equals(msg.getjobId())) {
                    switch (msg.getInfo()) {
                        case ERROR -> {
                            messages.remove(msg);
                            return new CoocState.FlowFailed(jobId, currentState.parameters(), msg.getMessage());
                        }
                        case PROGRESS -> {
                            if (msg.getProgress() != null) {
                                messages.remove(msg);
                                return currentState.withProgress(msg.getProgress());
                            }
                        }
                    }
                }
            }
        }

        return currentState;
    }

    private CoocState finishAnalysis(CoocState.Processing currentState) {
        String jobId = currentState.jobId();
        WorkflowCoocProps props = new WorkflowCoocProps(applicationProperties.getTempFolderFullPath());

        try {
            Path gexfPath = props.getGexfFilePath(jobId);
            String gexf = Files.readString(gexfPath, StandardCharsets.UTF_8);

            Path jsonPath = props.getKeyNodesJsonFilePath(jobId);
            String json = Files.readString(jsonPath, StandardCharsets.UTF_8);
            JsonObject jsonObject;
            try (JsonReader reader = Json.createReader(new StringReader(json))) {
                jsonObject = reader.readObject();
            }

            String nodesJson = Converters.turnJsonObjectToString(jsonObject.getJsonObject("nodes"));
            String edgesJson = Converters.turnJsonObjectToString(jsonObject.getJsonObject("edges"));

            return new CoocState.ResultsReady(jobId, gexf, nodesJson, edgesJson, false, false);

        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to complete cooc analysis for job " + jobId, e);
            return new CoocState.FlowFailed(jobId, currentState.parameters(), "Result file error: " + e.getMessage());
        }
    }
}