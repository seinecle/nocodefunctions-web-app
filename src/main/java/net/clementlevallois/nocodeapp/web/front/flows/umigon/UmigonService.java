package net.clementlevallois.nocodeapp.web.front.flows.umigon;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicReference;
import net.clementlevallois.functions.model.FunctionUmigon;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.nocodeapp.web.front.MessageFromApi;
import net.clementlevallois.nocodeapp.web.front.WatchTower;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.exceptions.NocodeApplicationException;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowFailed;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.umigon.model.classification.Document;

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

        var requestBuilder = microserviceClient.api().post(FunctionUmigon.ENDPOINT);

        for (FunctionUmigon.QueryParams param : FunctionUmigon.QueryParams.values()) {
            String value = switch (param) {
                case TEXT_LANG -> currentState.selectedLanguage();
                case EXPLANATION -> "on";
                case SHORTER -> "true";
                case OUTPUT_FORMAT -> "bytes";
                case EXPLANATION_LANG -> sessionBean.getCurrentLocale().toLanguageTag();
            };
            requestBuilder.addQueryParameter(param.name(), value);
        }

        for (Globals.GlobalQueryParams param : Globals.GlobalQueryParams.values()) {
            String value = switch (param) {
                case JOB_ID -> jobId;
                case CALLBACK_URL -> callbackURL;
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
        // Logic to process results and return a ResultsReady state
        // This is a placeholder for the actual implementation
        return new UmigonState.ResultsReady(currentState.jobId(), List.of());
    }
}
