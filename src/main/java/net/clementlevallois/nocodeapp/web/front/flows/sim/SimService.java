/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows.sim;

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
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.WorkflowSimProps;
import net.clementlevallois.nocodeapp.web.front.MessageFromApi;
import net.clementlevallois.nocodeapp.web.front.WatchTower;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.StringReader;
import net.clementlevallois.nocodeapp.web.front.exceptions.NocodeApplicationException;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowFailed;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;

@ApplicationScoped
public class SimService {

    private static final Logger LOG = Logger.getLogger(SimService.class.getName());

    @Inject
    private MicroserviceHttpClient microserviceClient;

    @Inject
    private ApplicationPropertiesBean applicationProperties;

    public FlowState callSimMicroService(SimState.AwaitingParameters currentState) {
        String jobId = currentState.jobId();
        String callbackURL = RemoteLocal.getDomain() + RemoteLocal.getInternalMessageApiEndpoint() + WorkflowSimProps.ENDPOINT;

        var requestBuilder = microserviceClient.api().get(WorkflowSimProps.ENDPOINT);

        for (WorkflowSimProps.QueryParams param : WorkflowSimProps.QueryParams.values()) {
            String paramValue = switch (param) {
                case MIN_SHARED_TARGETS -> String.valueOf(currentState.minSharedTargets());
                case SOURCE_COL_INDEX -> currentState.sourceColIndex();
            };
            requestBuilder.addQueryParameter(param.name(), paramValue);
        }

        for (Globals.GlobalQueryParams param : Globals.GlobalQueryParams.values()) {
            String paramValue = switch (param) {
                case JOB_ID -> jobId;
                case CALLBACK_URL -> callbackURL;
            };
            requestBuilder.addQueryParameter(param.name(), paramValue);
        }

        AtomicReference<FlowFailed> errorFlow = new AtomicReference<>();
        AtomicReference<Boolean> isOk = new AtomicReference<>(true);

        requestBuilder.sendAsync(HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                    if (resp.statusCode() != 200) {
                        LOG.log(Level.SEVERE, "Sim task submission failed for job {0}. Status: {1}, Body: {2}",
                                new Object[]{jobId, resp.statusCode(), resp.body()});
                        errorFlow.set(new FlowFailed(jobId, currentState, "sim call failed with non-200 status"));
                        isOk.set(Boolean.FALSE);
                    }
                })
                .exceptionally(ex -> {
                    LOG.log(Level.SEVERE, "Exception during sim submission for job " + jobId, ex);
                    errorFlow.set(new FlowFailed(jobId, currentState, "sim call threw exception"));
                    isOk.set(Boolean.FALSE);
                    return null;
                });

        return isOk.get() ? new SimState.Processing(jobId, currentState, 0) : errorFlow.get();
    }

    public FlowState checkCompletion(SimState.Processing currentState) {
        String jobId = currentState.jobId();
        Globals globals = new Globals(applicationProperties.getTempFolderFullPath());
        Path completeSignal = globals.getWorkflowCompleteFilePath(jobId);

        if (Files.exists(completeSignal)) {
            return processSimResults(currentState);
        }

        var messages = WatchTower.getDequeAPIMessages().get(jobId);
        if (messages != null) {
            for (MessageFromApi msg : messages) {
                if (jobId.equals(msg.getjobId())) {
                    switch (msg.getInfo()) {
                        case ERROR -> {
                            messages.remove(msg);
                            return new FlowFailed(jobId, currentState.parameters(), msg.getMessage());
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

    private FlowState processSimResults(SimState.Processing currentState) {
        String jobId = currentState.jobId();
        Globals globals = new Globals(applicationProperties.getTempFolderFullPath());
        WorkflowSimProps props = new WorkflowSimProps(applicationProperties.getTempFolderFullPath());

        try {
            Path gexfPath = props.getGexfFilePath(jobId);
            String gexf = Files.readString(gexfPath, StandardCharsets.UTF_8);

            Path jsonPath = globals.getTopNetworkVivaGraphFormattedFilePath(jobId);
            String json = Files.readString(jsonPath, StandardCharsets.UTF_8);
            JsonObject jsonObject;
            try (JsonReader reader = Json.createReader(new StringReader(json))) {
                jsonObject = reader.readObject();
            }

            String nodesJson = Converters.turnJsonObjectToString(jsonObject.getJsonObject("nodes"));
            String edgesJson = Converters.turnJsonObjectToString(jsonObject.getJsonObject("edges"));

            return new SimState.ResultsReady(jobId, gexf, nodesJson, edgesJson, false, false);

        } catch (IOException e) {
            throw new NocodeApplicationException("An IO error occurred", e);
        }
    }
}
