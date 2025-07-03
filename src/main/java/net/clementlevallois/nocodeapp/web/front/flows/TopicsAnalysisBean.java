package net.clementlevallois.nocodeapp.web.front.flows;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedExecutorService;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;
import static java.util.stream.Collectors.toList;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.WorkflowTopicsProps;
import net.clementlevallois.nocodeapp.web.front.MessageFromApi;
import net.clementlevallois.nocodeapp.web.front.WatchTower;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.utils.GEXFSaver;
import net.clementlevallois.utils.Multiset;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.file.UploadedFile;

@Named
@ViewScoped
public class TopicsAnalysisBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(TopicsAnalysisBean.class.getName());

    @Inject
    private WorkflowSessionBean workflowSessionBean;
    @Inject
    private SessionBean sessionBean;
    @Inject
    private ApplicationPropertiesBean applicationProperties;
    @Inject
    private MicroserviceHttpClient microserviceClient;

    @Resource
    private ManagedExecutorService managedExecutorService;

    private TopicsState currentState;
    private Globals globals;

    @PostConstruct
    public void init() {
        currentState = workflowSessionBean.getTopicsState();
        globals = new Globals(applicationProperties.getTempFolderFullPath());
        if (currentState == null) {
            try {
                FacesContext.getCurrentInstance().getExternalContext().redirect("topics-data-import.xhtml");
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "Redirect failed when topics state was null", e);
            }
        }
    }

    public void runAnalysis() {
        if (currentState instanceof TopicsState.AwaitingParameters params) {
            try {
                TopicsState.Processing processingState = new TopicsState.Processing(params.jobId(), params, 0);
                workflowSessionBean.setTopicsState(processingState);
                this.currentState = processingState;

                var requestBuilder = microserviceClient.api().post(WorkflowTopicsProps.ENDPOINT);
                addJsonBody(requestBuilder, params);
                addQueryParams(requestBuilder, params);

                managedExecutorService.submit(() -> sendRequestToMicroserviceAsync(requestBuilder, params.jobId()));

            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Error launching topics analysis for job " + params.jobId(), e);
                workflowSessionBean.setTopicsState(new TopicsState.FlowFailed(params.jobId(), params, "An unexpected error occurred during launch."));
                this.currentState = workflowSessionBean.getTopicsState();
            }
        }
    }

    private void sendRequestToMicroserviceAsync(MicroserviceHttpClient.PostRequestBuilder requestBuilder, String jobId) {
        requestBuilder.sendAsync(HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        LOG.log(Level.SEVERE, "Microservice task submission failed for job {0}. Status: {1}, Body: {2}", new Object[]{jobId, response.statusCode(), response.body()});
                        updateStateToFailed("Microservice task submission failed.");
                    }
                })
                .exceptionally(e -> {
                    LOG.log(Level.SEVERE, "Exception during microservice task submission for job " + jobId, e);
                    updateStateToFailed("Exception communicating with microservice: " + e.getMessage());
                    return null;
                });
    }

    private void addJsonBody(MicroserviceHttpClient.PostRequestBuilder requestBuilder, TopicsState.AwaitingParameters params) {
        JsonObjectBuilder overallObject = Json.createObjectBuilder();
        JsonObjectBuilder userSuppliedStopwordsBuilder = Json.createObjectBuilder();
        if (params.fileUserStopwords() != null && params.fileUserStopwords().getFileName() != null) {
            List<String> userSuppliedStopwords = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(params.fileUserStopwords().getInputStream(), StandardCharsets.UTF_8))) {
                userSuppliedStopwords = br.lines().collect(toList());
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, "Error reading user supplied stopwords file", ex);
            }
            int index = 0;
            for (String stopword : userSuppliedStopwords) {
                userSuppliedStopwordsBuilder.add(String.valueOf(index++), stopword);
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

    public void poll() {
        if (!(currentState instanceof TopicsState.Processing processing)) {
            return;
        }

        String jobId = processing.jobId();
        Path pathSignalWorkflowComplete = globals.getWorkflowCompleteFilePath(jobId);

        if (Files.exists(pathSignalWorkflowComplete)) {
            processResults(jobId);
            return;
        }

        ConcurrentLinkedDeque<MessageFromApi> messages = WatchTower.getDequeAPIMessages().get(jobId);
        if (messages != null && !messages.isEmpty()) {
            MessageFromApi latestMessage = messages.peekLast();
            if (latestMessage.getInfo() == MessageFromApi.Information.PROGRESS && latestMessage.getProgress() != null) {
                workflowSessionBean.setTopicsState(processing.withProgress(latestMessage.getProgress()));
                this.currentState = workflowSessionBean.getTopicsState();
            } else if (latestMessage.getInfo() == MessageFromApi.Information.ERROR) {
                updateStateToFailed(latestMessage.getMessage());
                messages.clear();
            }
        }
    }

    private void processResults(String jobId) {
        try {
            Path resultGexfPath = globals.getGexfFilePath(jobId);
            if (Files.exists(resultGexfPath)) {
                String gexfContent = Files.readString(resultGexfPath);
                Map<Integer, Multiset<String>> keywords = GexfBuilder.parseTopicsFromGexf(gexfContent);
                TopicsState.ResultsReady resultsState = new TopicsState.ResultsReady(jobId, gexfContent, keywords, false);
                workflowSessionBean.setTopicsState(resultsState);
                this.currentState = resultsState;
            } else {
                throw new IOException("Result file not found for job " + jobId);
            }
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Failed to process results for job " + jobId, e);
            updateStateToFailed("Failed to read or process results.");
        }
    }
    
    private void updateStateToFailed(String message) {
        if (workflowSessionBean.getTopicsState() instanceof TopicsState.Processing p) {
            workflowSessionBean.setTopicsState(new TopicsState.FlowFailed(p.jobId(), p.parameters(), message));
            this.currentState = workflowSessionBean.getTopicsState();
        }
    }

    // Methods to update parameters in the AwaitingParameters state
    public void setLanguage(String lang) {
        if (currentState instanceof TopicsState.AwaitingParameters params) {
            workflowSessionBean.setTopicsState(params.withSelectedLanguage(lang));
            this.currentState = workflowSessionBean.getTopicsState();
        }
    }

    public void setPrecision(int precision) {
        if (currentState instanceof TopicsState.AwaitingParameters params) {
            workflowSessionBean.setTopicsState(params.withPrecision(precision));
            this.currentState = workflowSessionBean.getTopicsState();
        }
    }

    public void setMinCharNumber(int minChar) {
        if (currentState instanceof TopicsState.AwaitingParameters params) {
            workflowSessionBean.setTopicsState(params.withMinCharNumber(minChar));
            this.currentState = workflowSessionBean.getTopicsState();
        }
    }

    public void setMinTermFreq(int minFreq) {
        if (currentState instanceof TopicsState.AwaitingParameters params) {
            workflowSessionBean.setTopicsState(params.withMinTermFreq(minFreq));
            this.currentState = workflowSessionBean.getTopicsState();
        }
    }

    public void setScientificCorpus(boolean flag) {
        if (currentState instanceof TopicsState.AwaitingParameters params) {
            workflowSessionBean.setTopicsState(params.withScientificCorpus(flag));
            this.currentState = workflowSessionBean.getTopicsState();
        }
    }

    public void setReplaceStopwords(boolean flag) {
        if (currentState instanceof TopicsState.AwaitingParameters params) {
            workflowSessionBean.setTopicsState(params.withReplaceStopwords(flag));
            this.currentState = workflowSessionBean.getTopicsState();
        }
    }

    public void setLemmatize(boolean flag) {
        if (currentState instanceof TopicsState.AwaitingParameters params) {
            workflowSessionBean.setTopicsState(params.withLemmatize(flag));
            this.currentState = workflowSessionBean.getTopicsState();
        }
    }

    public void setRemoveNonAscii(boolean flag) {
        if (currentState instanceof TopicsState.AwaitingParameters params) {
            workflowSessionBean.setTopicsState(params.withRemoveNonAsciiCharacters(flag));
            this.currentState = workflowSessionBean.getTopicsState();
        }
    }

    public void setFileUserStopwords(UploadedFile file) {
        if (currentState instanceof TopicsState.AwaitingParameters params) {
            workflowSessionBean.setTopicsState(params.withFileUserStopwords(file));
            this.currentState = workflowSessionBean.getTopicsState();
        }
    }

    public TopicsState getCurrentState() {
        this.currentState = workflowSessionBean.getTopicsState();
        return currentState;
    }
    
        public StreamedContent getGexfFile() {
        if (jobId == null || jobId.isEmpty()) {
            LOG.warning("Cannot provide GEXF file, jobId is null or empty.");
            logBean.addOneNotificationFromString("Cannot download GEXF: Analysis ID not set.");
            return new DefaultStreamedContent();
        }
        try {
            Path gexfResults = props.getGexfFilePath(jobId);
            if (Files.exists(gexfResults)) {
                String gexfAsString = Files.readString(gexfResults);
                StreamedContent exportGexfAsStreamedFile = GEXFSaver.exportGexfAsStreamedFile(gexfAsString, "network_file_with_topics");
                return exportGexfAsStreamedFile;
            } else {
                LOG.log(Level.WARNING, "GEXF result file not found for dataId: {0}", jobId);
                logBean.addOneNotificationFromString("GEXF file not found.");
                return new DefaultStreamedContent();
            }

        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Error reading GEXF file", ex);
            logBean.addOneNotificationFromString("Error providing GEXF file for download.");
            return new DefaultStreamedContent();
        }
    }

}
