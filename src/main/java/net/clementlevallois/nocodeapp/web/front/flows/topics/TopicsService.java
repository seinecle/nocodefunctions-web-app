package net.clementlevallois.nocodeapp.web.front.flows.topics;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import static java.util.stream.Collectors.toList;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.WorkflowTopicsProps;
import net.clementlevallois.nocodeapp.web.front.MessageFromApi;
import net.clementlevallois.nocodeapp.web.front.WatchTower;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.utils.Multiset;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.file.UploadedFile;

@ApplicationScoped
public class TopicsService {

    private static final Logger LOG = Logger.getLogger(TopicsService.class.getName());

    @Inject
    private MicroserviceHttpClient microserviceClient;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    public TopicsState.Processing startAnalysis(TopicsState.AwaitingParameters params) {
        try {
            var requestBuilder = microserviceClient.api().post(WorkflowTopicsProps.ENDPOINT);

            addJsonBody(requestBuilder, params);

            addQueryParams(requestBuilder, params);

            // Asynchronously send the request
            requestBuilder.sendAsync(HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() != 200) {
                            LOG.log(Level.SEVERE, "Microservice task submission failed for job {0}. Status: {1}, Body: {2}", new Object[]{params.jobId(), response.statusCode(), response.body()});
                        }
                    })
                    .exceptionally(e -> {
                        LOG.log(Level.SEVERE, "Exception during microservice task submission for job " + params.jobId(), e);
                        return null;
                    });
            return new TopicsState.Processing(params.jobId(), params, 0);

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error launching topics analysis for job " + params.jobId(), e);
            return null;
        }
    }

    private void addJsonBody(MicroserviceHttpClient.PostRequestBuilder requestBuilder, TopicsState.AwaitingParameters params) {
        JsonObjectBuilder overallObject = Json.createObjectBuilder();
        JsonObjectBuilder userSuppliedStopwordsBuilder = Json.createObjectBuilder();
        UploadedFile fileUserStopwords = params.fileUserStopwords();

        if (fileUserStopwords != null && fileUserStopwords.getFileName() != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(fileUserStopwords.getInputStream(), StandardCharsets.UTF_8))) {
                List<String> userSuppliedStopwords = br.lines().collect(toList());
                for (int i = 0; i < userSuppliedStopwords.size(); i++) {
                    userSuppliedStopwordsBuilder.add(String.valueOf(i), userSuppliedStopwords.get(i));
                }
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, "Error reading user-supplied stopwords file", ex);
            }
        }
        overallObject.add(WorkflowTopicsProps.BodyJsonKeys.USER_SUPPLIED_STOPWORDS.name(), userSuppliedStopwordsBuilder);
        requestBuilder.withJsonPayload(overallObject.build());
    }

    private void addQueryParams(MicroserviceHttpClient.PostRequestBuilder requestBuilder, TopicsState.AwaitingParameters params) {
        requestBuilder.addQueryParameter(WorkflowTopicsProps.QueryParams.LANG.name(), params.selectedLanguage() != null ? params.selectedLanguage() : "en");
        requestBuilder.addQueryParameter(WorkflowTopicsProps.QueryParams.REPLACE_STOPWORDS.name(), String.valueOf(params.replaceStopwords()));
        requestBuilder.addQueryParameter(WorkflowTopicsProps.QueryParams.IS_SCIENTIFIC_CORPUS.name(), String.valueOf(params.scientificCorpus()));
        requestBuilder.addQueryParameter(WorkflowTopicsProps.QueryParams.LEMMATIZE.name(), String.valueOf(params.lemmatize()));
        requestBuilder.addQueryParameter(WorkflowTopicsProps.QueryParams.REMOVE_ACCENTS.name(), String.valueOf(params.removeNonAsciiCharacters()));
        requestBuilder.addQueryParameter(WorkflowTopicsProps.QueryParams.PRECISION.name(), String.valueOf(params.precision()));
        requestBuilder.addQueryParameter(WorkflowTopicsProps.QueryParams.MIN_CHAR_NUMBER.name(), String.valueOf(params.minCharNumber()));
        requestBuilder.addQueryParameter(WorkflowTopicsProps.QueryParams.MIN_TERM_FREQ.name(), String.valueOf(params.minTermFreq()));

        String callbackURL = RemoteLocal.getDomain() + RemoteLocal.getInternalMessageApiEndpoint() + WorkflowTopicsProps.ENDPOINT;
        requestBuilder.addQueryParameter(Globals.GlobalQueryParams.JOB_ID.name(), params.jobId());
        requestBuilder.addQueryParameter(Globals.GlobalQueryParams.CALLBACK_URL.name(), callbackURL);
    }

    public TopicsState checkCompletion(TopicsState.Processing currentState) {
        String jobId = currentState.jobId();
        Globals globals = new Globals(applicationProperties.getTempFolderFullPath());
        Path pathSignalWorkflowComplete = globals.getWorkflowCompleteFilePath(jobId);

        if (Files.exists(pathSignalWorkflowComplete)) {
            return finishAnalysis(currentState);
        }

        var messagesFromApi = WatchTower.getDequeAPIMessages().get(jobId);
        if (messagesFromApi != null && !messagesFromApi.isEmpty()) {
            MessageFromApi latestMessage = messagesFromApi.peekLast();
            if (latestMessage.getInfo() == MessageFromApi.Information.ERROR) {
                messagesFromApi.clear();
                return new TopicsState.FlowFailed(jobId, currentState.parameters(), latestMessage.getMessage());
            }
            if (latestMessage.getInfo() == MessageFromApi.Information.PROGRESS && latestMessage.getProgress() != null) {
                return currentState.withProgress(latestMessage.getProgress());
            }
        }
        return currentState;
    }

    private TopicsState finishAnalysis(TopicsState.Processing currentState) {
        String jobId = currentState.jobId();
        WorkflowTopicsProps props = new WorkflowTopicsProps(applicationProperties.getTempFolderFullPath());
        try {
            Path resultGexfPath = props.getGexfFilePath(jobId);
            if (Files.exists(resultGexfPath)) {
                String gexfContent = Files.readString(resultGexfPath);
                Path jsonResults = props.getGlobalResultsJsonFilePath(jobId);

                if (!Files.exists(jsonResults)) {
                    LOG.log(Level.WARNING, "JSON result file not found for dataId: {0}", jobId);
                    return new TopicsState.FlowFailed(jobId, currentState.parameters(), "Failed to read or process results.");
                }

                String jsonResultAsString = Files.readString(jsonResults);
                JsonReader jsonReader = Json.createReader(new StringReader(jsonResultAsString));
                JsonObject jsonObject = jsonReader.readObject();

                Map<Integer, Multiset<String>> keywordsPerTopic = new TreeMap<>();
                JsonObject keywordsPerTopicAsJson = jsonObject.getJsonObject("keywordsPerTopic");
                if (keywordsPerTopicAsJson != null) {
                    for (String keyCommunity : keywordsPerTopicAsJson.keySet()) {
                        JsonObject termsAndFrequenciesForThisCommunity = keywordsPerTopicAsJson.getJsonObject(keyCommunity);
                        Multiset<String> termsAndFreqs = new Multiset<>();
                        if (termsAndFrequenciesForThisCommunity != null) {
                            termsAndFrequenciesForThisCommunity.keySet().forEach(term -> {
                                termsAndFreqs.addSeveral(term, termsAndFrequenciesForThisCommunity.getInt(term));
                            });
                        }
                        try {
                            keywordsPerTopic.put(Integer.valueOf(keyCommunity), termsAndFreqs);
                        } catch (NumberFormatException e) {
                            LOG.log(Level.WARNING, "Skipping non-integer topic key in JSON: " + keyCommunity, e);
                        }
                    }
                } else {
                    LOG.warning("JSON result did not contain 'keywordsPerTopic' object.");
                }
                return new TopicsState.ResultsReady(jobId, gexfContent, keywordsPerTopic, false, false);
            } else {
                throw new IOException("Result file not found for job " + jobId);
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to process results for job " + jobId, e);
            return new TopicsState.FlowFailed(jobId, currentState.parameters(), "Failed to read or process results.");
        }
    }
    
        public StreamedContent createExcelFileFromJsonSavedData(String jobId) {
        if (jobId == null || jobId.isEmpty()) {
            LOG.warning("Cannot create Excel file, jobId is null or empty.");
            return new DefaultStreamedContent();
        }
        try {
            CompletableFuture<byte[]> futureBytes = microserviceClient.importService().post("/api/export/xlsx/topics")
                    .addQueryParameter("nbTerms", "10")
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
            LOG.log(Level.SEVERE, "Error during asynchronous export service call (CompletionException)", cause);
            String errorMessage = "Error exporting data: " + cause.getMessage();
            if (cause instanceof MicroserviceHttpClient.MicroserviceCallException msce) {
                errorMessage = "Error exporting data: Status " + msce.getStatusCode() + ", " + msce.getErrorBody();
            }
            return new DefaultStreamedContent();
        }
    }

}
