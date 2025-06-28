/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import static java.util.stream.Collectors.toList;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.WorkflowCowoProps;
import net.clementlevallois.nocodeapp.web.front.MessageFromApi;
import net.clementlevallois.nocodeapp.web.front.WatchTower;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
import org.primefaces.model.file.UploadedFile;

@ApplicationScoped
public class WorkflowCowoService {

    private static final Logger LOG = Logger.getLogger(WorkflowCowoService.class.getName());

    @Inject
    private MicroserviceHttpClient microserviceClient;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    public CowoState.Processing startAnalysis(CowoState.AwaitingParameters currentState, String sessionId) {
        String jobId = currentState.jobId();
        String correctionType = currentState.usePMI() ? "pmi" : "none";

        JsonObjectBuilder userSuppliedStopwordsBuilder = Json.createObjectBuilder();
        UploadedFile fileUserStopwords = currentState.fileUserStopwords();

        if (fileUserStopwords != null && fileUserStopwords.getFileName() != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(fileUserStopwords.getInputStream(), StandardCharsets.UTF_8))) {
                List<String> userSuppliedStopwords = br.lines().collect(toList());
                int index = 0;
                for (String stopword : userSuppliedStopwords) {
                    userSuppliedStopwordsBuilder.add(String.valueOf(index++), stopword);
                }
            } catch (IOException exIO) {
                LOG.log(Level.SEVERE, "Error reading user supplied stopwords file", exIO);
                // This should ideally return a FlowFailed state, but for simplicity in this step, we log and proceed.
            }
        }

        JsonObject jsonPayload = Json.createObjectBuilder()
                .add("userSuppliedStopwords", userSuppliedStopwordsBuilder)
                .build();

        String callbackURL = RemoteLocal.getDomain() + RemoteLocal.getInternalMessageApiEndpoint() + WorkflowCowoProps.ENDPOINT;
        var requestBuilder = microserviceClient.api().post(WorkflowCowoProps.ENDPOINT).withJsonPayload(jsonPayload);

        for (WorkflowCowoProps.QueryParams param : WorkflowCowoProps.QueryParams.values()) {
            String paramValue = switch (param) {
                case LANG ->
                    String.join(",", currentState.selectedLanguages());
                case MIN_CHAR_NUMBER ->
                    String.valueOf(currentState.minCharNumber());
                case REPLACE_STOPWORDS ->
                    String.valueOf(currentState.replaceStopwords());
                case IS_SCIENTIFIC_CORPUS ->
                    String.valueOf(currentState.scientificCorpus());
                case LEMMATIZE ->
                    String.valueOf(true); // lemmatize is hardcoded to true in original bean
                case REMOVE_ACCENTS ->
                    String.valueOf(currentState.removeNonAsciiCharacters());
                case MIN_TERM_FREQ ->
                    String.valueOf(currentState.minTermFreq());
                case MIN_COOC_FREQ ->
                    String.valueOf(2); // minCoocFreqInt is hardcoded to 2
                case REMOVE_FIRST_NAMES ->
                    String.valueOf(currentState.firstNames());
                case MAX_NGRAMS ->
                    String.valueOf(currentState.maxNGram());
                case TYPE_CORRECTION ->
                    correctionType;
            };
            requestBuilder.addQueryParameter(param.name(), paramValue);
        }

        for (Globals.GlobalQueryParams param : Globals.GlobalQueryParams.values()) {
            String paramValue = switch (param) {
                case SESSION_ID ->
                    sessionId;
                case JOB_ID ->
                    jobId;
                case CALLBACK_URL ->
                    callbackURL;
            };
            requestBuilder.addQueryParameter(param.name(), paramValue);
        }

        requestBuilder.sendAsync(HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        LOG.log(Level.SEVERE, "Cowo task submission failed for dataId {0}. Status: {1}, Body: {2}", new Object[]{jobId, response.statusCode(), response.body()});
                    }
                })
                .exceptionally(e -> {
                    LOG.log(Level.SEVERE, "Exception during Cowo task submission for dataId " + jobId, e);
                    return null;
                });

        return new CowoState.Processing(jobId, currentState, 0);
    }

    public CowoState checkCompletion(CowoState.Processing currentState, String sessionId) {
        String jobId = currentState.jobId();
        Globals globals = new Globals(applicationProperties.getTempFolderFullPath());
        Path pathSignalWorkflowComplete = globals.getWorkflowCompleteFilePath(jobId);

        if (Files.exists(pathSignalWorkflowComplete)) {
            return finishAnalysis(currentState);
        }

        var messagesFromApi = WatchTower.getDequeAPIMessages().get(sessionId);
        if (messagesFromApi != null) {
            for (MessageFromApi msg : messagesFromApi) {
                if (jobId.equals(msg.getjobId())) {
                    if (msg.getInfo() == MessageFromApi.Information.ERROR) {
                        messagesFromApi.remove(msg);
                        return new CowoState.FlowFailed(jobId, currentState.parameters(), msg.getMessage());
                    }
                    if (msg.getInfo() == MessageFromApi.Information.PROGRESS && msg.getProgress() != null) {
                        messagesFromApi.remove(msg);
                        return currentState.withProgress(msg.getProgress());
                    }
                }
            }
        }
        return currentState; // No change
    }

    private CowoState finishAnalysis(CowoState.Processing currentState) {
        String jobId = currentState.jobId();
        Globals globals = new Globals(applicationProperties.getTempFolderFullPath());
        WorkflowCowoProps props = new WorkflowCowoProps(applicationProperties.getTempFolderFullPath());
        try {
            Path gexfFilePath = props.getGexfFilePath(jobId);
            String gexf = Files.readString(gexfFilePath, StandardCharsets.UTF_8);

            Path pathOfTopNodesData = globals.getTopNetworkVivaGraphFormattedFilePath(jobId);
            String json = Files.readString(pathOfTopNodesData);
            JsonObject jsonObject;
            try (JsonReader jsonReader = Json.createReader(new StringReader(json))) {
                jsonObject = jsonReader.readObject();
            }

            String nodesAsJson = Converters.turnJsonObjectToString(jsonObject.getJsonObject("nodes"));
            String edgesAsJson = Converters.turnJsonObjectToString(jsonObject.getJsonObject("edges"));
            int minFreqNode = jsonObject.getJsonObject("metadata").getInt("minFreqNode");
            int maxFreqNode = jsonObject.getJsonObject("metadata").getInt("maxFreqNode");

            return new CowoState.ResultsReady(jobId, gexf, nodesAsJson, edgesAsJson, minFreqNode, maxFreqNode, false, false);

        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Error finalizing analysis for job " + jobId, e);
            return new CowoState.FlowFailed(jobId, currentState.parameters(), "Could not read result files: " + e.getMessage());
        }
    }
}
