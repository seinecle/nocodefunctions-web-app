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
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.clementlevallois.importers.model.CellRecord;
import net.clementlevallois.importers.model.SheetModel;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.exceptions.NocodeApplicationException;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowFailed;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowHasNoAccess;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import net.clementlevallois.nocodeapp.web.front.stripe.StripeBean;
import net.clementlevallois.nocodeapp.web.front.utils.FacesUtils;
import net.clementlevallois.utils.Multiset;

@ApplicationScoped
public class CoocService {

    private static final Logger LOG = Logger.getLogger(CoocService.class.getName());

    @Inject
    private BackToFrontMessengerBean logBean;

    @Inject
    private SessionBean sessionBean;

    @Inject
    private MicroserviceHttpClient microserviceClient;

    @Inject
    private StripeBean stripeBean;

    @Inject
    private ApplicationPropertiesBean applicationProperties;

    public FlowState sheetModelToCooccurrences(CoocState.AwaitingParameters currentState) {
        try {
            Globals globals = new Globals(applicationProperties.getTempFolderFullPath());
            Path tempDataPathToSheetModel = globals.getDataSheetPath(currentState.jobId());
            byte[] byteArray = Files.readAllBytes(tempDataPathToSheetModel);
            List<SheetModel> sheets = null;
            try (ByteArrayInputStream bis = new ByteArrayInputStream(byteArray); ObjectInputStream ois = new ObjectInputStream(bis)) {
                Object obj = ois.readObject();
                sheets = (List<SheetModel>) obj;
            } catch (IOException | ClassNotFoundException ex) {
                throw new NocodeApplicationException("An error occurred", ex);
            }
            Map<Integer, Multiset<String>> lines = new HashMap();
            for (SheetModel sheet : sheets) {
                Map<Integer, List<CellRecord>> mapOfCellRecordsPerRow = sheet.getRowIndexToCellRecords();

                Iterator<Map.Entry<Integer, List<CellRecord>>> iterator = mapOfCellRecordsPerRow.entrySet().iterator();
                int i = 0;
                Multiset<String> multiset;
                while (iterator.hasNext()) {
                    Map.Entry<Integer, List<CellRecord>> next = iterator.next();
                    multiset = new Multiset();
                    for (CellRecord cr : next.getValue()) {
                        multiset.addOne(cr.getRawValue());
                    }
                    lines.put(i++, multiset);
                }
                if (currentState.hasHeaders() && !lines.isEmpty()) {
                    lines.remove(0);
                }
                // if we have found and read data on the current sheet, we stop (we don't continue visting and reading the next sheets)
                if (!lines.isEmpty()) {
                    break;
                }
            }
            if (lines.isEmpty()) {
                FlowFailed flowFailed = new FlowFailed(currentState.jobId(), currentState, "no data found");
                return flowFailed;
            }

            byte[] coocsAsByteArray = Converters.byteArraySerializerForAnyObject(lines);

            WorkflowCoocProps coocProps = new WorkflowCoocProps(applicationProperties.getTempFolderFullPath());
            Files.write(coocProps.getPathForCooccurrencesFormattedAsMap(currentState.jobId()), coocsAsByteArray);
            Files.deleteIfExists(tempDataPathToSheetModel);
            return currentState;
        } catch (IOException ex) {
            throw new NocodeApplicationException("An IO error occurred", ex);
        }
    }

    public FlowState callCoocMicroService(CoocState.AwaitingParameters currentState) {
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

        AtomicReference<FlowFailed> errorFlow = new AtomicReference<>();
        AtomicReference<Boolean> isOk = new AtomicReference<>(true);

        requestBuilder.sendAsync(HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                    if (resp.statusCode() != 200) {
                        LOG.log(Level.SEVERE, "Cooc task submission failed for job {0}. Status: {1}, Body: {2}",
                                new Object[]{jobId, resp.statusCode(), resp.body()});
                        errorFlow.set(new FlowFailed(jobId, currentState, "cooc call failed with non-200 status"));
                        isOk.set(Boolean.FALSE);
                    }
                })
                .exceptionally(ex -> {
                    LOG.log(Level.SEVERE, "Exception during cooc submission for job " + jobId, ex);
                    errorFlow.set(new FlowFailed(jobId, currentState, "cooc call threw exception"));
                    isOk.set(Boolean.FALSE);
                    return null;
                });

        return isOk.get() ? new CoocState.Processing(jobId, currentState, 0) : errorFlow.get();
    }

    public FlowState checkCompletion(CoocState.Processing currentState) {
        String jobId = currentState.jobId();
        Globals globals = new Globals(applicationProperties.getTempFolderFullPath());
        Path completeSignal = globals.getWorkflowCompleteFilePath(jobId);

        if (Files.exists(completeSignal)) {
            return processCoocResults(currentState);
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
                                logBean.addOneNotificationFromString(msg.getMessage());
                                return currentState.withProgress(msg.getProgress());
                            }
                        }
                    }
                }
            }
        }

        return currentState;
    }

    private FlowState processCoocResults(CoocState.Processing currentState) {
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
            throw new NocodeApplicationException("Error in processCoocResults method: Failed to read GEXF or JSON results for jobId: " + currentState.jobId(), e);
        }
    }

    public FlowState accessCheckPassed(CoocState.AwaitingParameters params) {
        try {
            Map<Integer, Multiset<String>> coocsAsMap = null;
            
             WorkflowCoocProps props = new WorkflowCoocProps(applicationProperties.getTempFolderFullPath());
            Path dataMapPath = props.getPathForCooccurrencesFormattedAsMap(params.jobId());
            byte[] byteArray = Files.readAllBytes(dataMapPath);
            try (ByteArrayInputStream bis = new ByteArrayInputStream(byteArray); ObjectInputStream ois = new ObjectInputStream(bis)) {
                Object obj = ois.readObject();
                coocsAsMap = (Map<Integer, Multiset<String>>) obj;
                String maxLines = applicationProperties.getLimitsProperties().getProperty("cooc.lines", "0");

                // FIRST CASE: we are below the freemium limit, so the check passes.
                if (Integer.parseInt(maxLines) > coocsAsMap.size()) {
                    return params;
                } else {
                    // SECOND CASE : has the user enough credits, or any credit at all?
                    Integer remainingCredits = stripeBean.getRemainingCredits();
                    if (remainingCredits > 0) {
                        return params;
                    } else {
                        return new FlowHasNoAccess(params.jobId(), params, "no access");
                    }
                }

            } catch (IOException | ClassNotFoundException ex) {
                logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.internal_server_error"));
                return new FlowHasNoAccess(params.jobId(), params, "no access");
            }
        } catch (IOException ex) {
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.internal_server_error"));
            return new FlowHasNoAccess(params.jobId(), params, "no access");
        }
    }
}
