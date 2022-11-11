/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.functions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonBean;
import net.clementlevallois.nocodeapp.web.front.redisops.Annotator;
import net.clementlevallois.functions.model.bibd.BlockToEvaluate;
import net.clementlevallois.nocodeapp.web.front.redisops.TaskMetadata;
import net.clementlevallois.nocodeapp.web.front.redisops.Task;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.http.SendReport;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
import net.clementlevallois.nocodeapp.web.front.logview.NotificationService;
import net.clementlevallois.nocodeapp.web.front.resources.PlaceHolder;
import org.primefaces.model.chart.Axis;
import org.primefaces.model.chart.AxisType;
import org.primefaces.model.chart.BarChartModel;
import org.primefaces.model.chart.ChartSeries;
import redis.clients.jedis.Jedis;
import static redis.clients.jedis.ScanParams.SCAN_POINTER_START;
import redis.clients.jedis.ScanResult;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped

public class LabellingBean implements Serializable {

    @Inject
    NotificationService service;

    @Inject
    SessionBean sessionBean;

    @Inject
    SingletonBean singletonBean;

    @Inject
    BeanManager beanManager;

    private boolean designerLoggedIn;
    private boolean annotatorLoggedIn;

    private String actionOption = "newList";

    private String typeOfUpload = "Option1";

    private String taskId;

    private boolean publicTask;
    private String publicTaskId;

    private String paramLog;

    private boolean newUser;

    private String newAnnotatorName;

    private String newAnnotatorEmail;

    private String emailAnnotatorToDelete;
    private String emailCurrentAnnotator;
    private String emailDesigner;

    private String passwordDesigner;
    private String passwordAnnotator;
    private String generateNewPassWordForDesigner;

    private String typeOfTask = "categorization"; // bws OR categorization

    private List<SelectItem> annotators = new ArrayList();

    private List<SelectItem> tasksIdsOfTheDesigner = new ArrayList();
    private List<SelectItem> tasksIdsOfTheAnnotator = new ArrayList();
    private Map<String, TaskMetadata> tasksMetadataOfTheDesigner = new HashMap();
    private Map<String, TaskMetadata> tasksMetadataOfTheAnnotator = new HashMap();
    private TaskMetadata metadataOfCurrentTask;

    private List<BlockToEvaluate> blocksToEvaluate;
    private List<String> itemsToEvaluate;
    private BlockToEvaluate blockToEvaluate;
    private String itemToEvaluate;
    private int indexBlock = 0;
    private int indexItem = 0;
    private int elementsEvaluatedSoFar;
    private String currentSeriesIndex;
    private String instruction;
    private boolean multipleCategoriesPossible;
    private boolean freeCommentPossible;
    private String freeComment;
    private List<String> selectedCategories;
    private String selectedCategory;

    private List<String> rawItemsForChart;
    private List<String> bwsScoresForChart;
    private BarChartModel barChartModel;

    private Task task;

    public LabellingBean() {
        if (sessionBean == null) {
            sessionBean = new SessionBean();
        }
        sessionBean.setFunction("labelling");
        sessionBean.sendFunctionPageReport();
    }

    public String logoff() {
        FacesContext.getCurrentInstance().getExternalContext().invalidateSession();
        return "/labelling/role.xhtml?faces-redirect=true";
    }

    public String navigateToItemEvaluation() {
        if (designerLoggedIn) {
            emailCurrentAnnotator = emailDesigner;
        }
        if (typeOfTask.equals("bws")) {
            return "/labelling/item_eval_bws.xhtml?function=labelling&amp;faces-redirect=true";
        } else {
            return "/labelling/item_eval_categorization.xhtml?function=labelling&amp;faces-redirect=true";
        }
    }

    public String navigateToDataImportation() {
        // invalidating beans to start with a clean state:
        // dataImporterBean
        // BwsBean
        // CategorizationBean

        Bean<DataImportBean> beanForDataImporter = (Bean<DataImportBean>) beanManager.resolve(beanManager.getBeans(DataImportBean.class));
        DataImportBean dataImporterBean = (DataImportBean) beanManager.getReference(beanForDataImporter, beanForDataImporter.getBeanClass(), beanManager.createCreationalContext(beanForDataImporter));
        dataImporterBean = null;
        Bean<BwsBean> beanforBWS = (Bean<BwsBean>) beanManager.resolve(beanManager.getBeans(BwsBean.class));
        BwsBean bwsBean = (BwsBean) beanManager.getReference(beanforBWS, beanforBWS.getBeanClass(), beanManager.createCreationalContext(beanforBWS));
        bwsBean = null;
        Bean<CategorizationBean> beanforCategorization = (Bean<CategorizationBean>) beanManager.resolve(beanManager.getBeans(CategorizationBean.class));
        CategorizationBean categorizationBean = (CategorizationBean) beanManager.getReference(beanforCategorization, beanforCategorization.getBeanClass(), beanManager.createCreationalContext(beanforCategorization));
        categorizationBean = null;

        String designerKey = "designer:" + passwordDesigner + ":meta:email:" + emailDesigner;

        try ( Jedis jedis = SingletonBean.getJedisPool().getResource()) {

            String cur = SCAN_POINTER_START;
            Iterator<String> iteratorScanResults;
            boolean userAlreadyInDB = false;
            do {
                ScanResult<String> scanResult = jedis.scan(cur);
                // work with result
                iteratorScanResults = scanResult.getResult().iterator();
                while (iteratorScanResults.hasNext()) {
                    String next = iteratorScanResults.next();
                    String[] keyFields = next.split(":");
                    if (keyFields.length == 5) {
                        String typeOfKey = keyFields[0];
                        String email = keyFields[4];
                        String pass = keyFields[1];
                        if (typeOfKey.equals("designer") && email.equals(emailDesigner)) {
                            userAlreadyInDB = true;
                            if (newUser) {
                                designerLoggedIn = false;
                                String warning = sessionBean.getLocaleBundle().getString("general.message.user_exists");
                                String instructions = sessionBean.getLocaleBundle().getString("general.message.user_exists.instructions");
                                FacesContext.getCurrentInstance().
                                        addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, warning, instructions + emailDesigner));
                                service.create(warning + ". " + instructions + emailDesigner);
                                newUser = false;
                                return "";
                            } else if (passwordDesigner.equals(pass)) {
                                designerLoggedIn = true;
                                if (typeOfUpload.equals("Option1")) {
                                    return "/import_your_data_bulk_text.xhtml?function=labelling&amp;persistToDisk=true&amp;faces-redirect=true";
                                } else {
                                    return "/import_your_structured_data.xhtml?function=labelling&amp;persistToDisk=true&amp;faces-redirect=true";
                                }
                            } else {
                                designerLoggedIn = false;
                                FacesContext.getCurrentInstance().
                                        addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, sessionBean.getLocaleBundle().getString("general.message.wrong_password"), ""));
                                service.create(sessionBean.getLocaleBundle().getString("general.message.wrong_password"));
                                return "";
                            }
                        }
                    }
                }
                cur = scanResult.getCursor();
            } while (!cur.equals(SCAN_POINTER_START));
            if (!userAlreadyInDB) {
                jedis.set(designerKey, JsonValue.EMPTY_JSON_OBJECT.toString());
                designerLoggedIn = true;
                if (newUser) {
                    SendReport send = new SendReport();
                    send.initSendTaskDesignerCredentials(emailDesigner, passwordDesigner);
                    send.start();
                }
                if (typeOfUpload.equals("Option1")) {
                    return "/import_your_data_bulk_text.xhtml?function=labelling&amp;persistToDisk=true&amp;faces-redirect=true";
                } else {
                    return "/import_your_structured_data.xhtml?function=labelling&amp;persistToDisk=true&amp;faces-redirect=true";
                }
            }
        }
        return "";
    }

    public boolean isDesignerLoggedIn() {
        return designerLoggedIn;
    }

    public void setDesignerLoggedIn(boolean designerLoggedIn) {
        this.designerLoggedIn = designerLoggedIn;
    }

    public boolean isAnnotatorLoggedIn() {
        return annotatorLoggedIn;
    }

    public void setAnnotatorLoggedIn(boolean annotatorLoggedIn) {
        this.annotatorLoggedIn = annotatorLoggedIn;
    }

    public boolean checkLogin() {
        return annotatorLoggedIn | designerLoggedIn;
    }

    public String getActionOption() {
        return actionOption;
    }

    public void setActionOption(String actionOption) {
        this.actionOption = actionOption;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        if (taskId == null) {
            return;
        }
        try ( Jedis jedis = SingletonBean.getJedisPool().getResource()) {
            String key = "task:" + taskId + ":annotations:indices:last_index:annotator:public";
            if (jedis.exists(key)) {
                this.publicTask = true;
                setPublicTaskId(taskId + "jzm");
            }
        }
        this.taskId = taskId;

        String taskMetadataKey = "task:" + taskId;
        try ( Jedis jedis = SingletonBean.getJedisPool().getResource()) {
            String metadata = jedis.get(taskMetadataKey);
            JsonbConfig config = new JsonbConfig().withNullValues(true);
            Jsonb jb = JsonbBuilder.create(config);
            metadataOfCurrentTask = jb.fromJson(metadata, TaskMetadata.class);
            typeOfTask = metadataOfCurrentTask.getTypeOfTask();
        }
    }

    public void setPublicTaskId(String publicTaskId) {
        if (publicTaskId.contains("?")) {
            publicTaskId = publicTaskId.split("\\?")[0];
        }
        if (publicTaskId.equals("no")) {
            return;
        }
        this.publicTaskId = publicTaskId;
        taskId = publicTaskId.substring(0, publicTaskId.length() - 3);
        publicTask = true;
    }

    public String getPublicTaskId() {
        return publicTaskId;
    }

    public String getParamLog() {
        return paramLog;
    }

    public void setParamLog(String paramLog) {
        if (paramLog == null || paramLog.isBlank()) {
            return;
        }
        if (paramLog.contains("?")) {
            paramLog = paramLog.split("\\?")[0];
        }
        String fullParamLog = paramLog.split("°")[1];
        taskId = fullParamLog.substring(0, fullParamLog.length() - 3);
        emailCurrentAnnotator = paramLog.split("°")[0];
        annotatorLoggedIn = true;
    }

    public void setPublicTask(boolean publicTask) {
        this.publicTask = publicTask;
    }

    public boolean getPublicTask() {
        return publicTask;
    }

    public void generatePublicTaskId() {
        if (publicTask) {
            String extension = UUID.randomUUID().toString().substring(0, 3);
            setPublicTaskId(taskId + extension);
        }
    }

    public String getGenerateNewPassWordForDesigner() {
        passwordDesigner = UUID.randomUUID().toString().substring(0, 10);
        return passwordDesigner;
    }

    public void setGenerateNewPassWordForDesigner(String generateNewPassWordForDesigner) {
        this.generateNewPassWordForDesigner = generateNewPassWordForDesigner;
    }

    public Map<Integer, String> getItemsToLabel(String keyCode) {

        blocksToEvaluate = new ArrayList();
        Map<Integer, String> items = new HashMap();

        try ( Jedis jedis = SingletonBean.getJedisPool().getResource()) {
            String keyRawData = "task:" + taskId + ":rawdata";
            List<String> itemsAsList = jedis.lrange(keyRawData, 0, -1);
            int i = 0;
            for (String item : itemsAsList) {
                items.put(i++, item);
            }
        }
        return items;
    }

    public String getEmailCurrentAnnotator() {
        return emailCurrentAnnotator;
    }

    public void setEmailCurrentAnnotator(String emailCurrentAnnotator) {
        if (emailCurrentAnnotator.contains(":")) {
            return;
        }
        this.emailCurrentAnnotator = emailCurrentAnnotator.toLowerCase().trim();
    }

    public void loadAnnotatorsEmailsForAGivenTask() {
        annotators = new ArrayList();
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        try ( Jedis jedis = SingletonBean.getJedisPool().getResource()) {
            String annotatorsOfATask = "task:" + taskId + ":annotators";
            String annotatorsAsJsonString = jedis.get(annotatorsOfATask);
            JsonReader jr = Json.createReader(new StringReader(annotatorsAsJsonString));
            JsonObject read = jr.readObject();
            for (String keySeries : read.keySet()) {
                JsonArray value = read.getJsonArray(keySeries);
                if (value != null && !value.isEmpty()) {
                    for (int i = 0; i < value.size(); i++) {
                        String element = value.getString(i);
                        Annotator ann = new Annotator(taskId, element, jedis);
                        annotators.add(new SelectItem(ann.getEmail(), ann.getEmail()));
                    }
                }
            }
            jr.close();
        }
    }

    public void loadBwsScores() {
        String keyBwsCounts = "task:" + taskId + ":annotations:indices:counter_bws";
        String keyRawData = "task:" + taskId + ":rawdata";

        try ( Jedis jedis = SingletonBean.getJedisPool().getResource()) {
            rawItemsForChart = jedis.lrange(keyRawData, 0, -1);
            bwsScoresForChart = jedis.lrange(keyBwsCounts, 0, -1);
        }
    }

    private void initBarModel() {
        loadBwsScores();
        barChartModel = new BarChartModel();

        ChartSeries itemsChart = new ChartSeries();
        itemsChart.setLabel(sessionBean.getLocaleBundle().getString("general.nouns.items"));
        int maxValue = 0;
        int minValue = 0;
        Map<String, Integer> mapItemsToScores = new HashMap();

        // max value otherwise the chart takes too much time to load
        int maxItems = 100;
        int i = 0;
        for (String item : rawItemsForChart) {
            if (++i > maxItems) {
                break;
            }
            int index = rawItemsForChart.indexOf(item);
            Integer scoreValue = Integer.valueOf(bwsScoresForChart.get(index));
            mapItemsToScores.put(item, scoreValue);
        }
        Map<String, Integer> sortByValue = sortByValue(mapItemsToScores);
        for (Map.Entry<String, Integer> entry : sortByValue.entrySet()) {
            itemsChart.set(entry.getKey(), entry.getValue());
            if (entry.getValue() > maxValue) {
                maxValue = entry.getValue();
            }
            if (entry.getValue() < minValue) {
                minValue = entry.getValue();
            }

        }

        barChartModel.addSeries(itemsChart);
        barChartModel.setTitle(sessionBean.getLocaleBundle().getString("general.message.scores_by_annotators"));
        barChartModel.setLegendPosition("ne");

        Axis xAxis = barChartModel.getAxis(AxisType.X);
        xAxis.setLabel("Items");
        xAxis.setTickAngle(-30);

        Axis yAxis = barChartModel.getAxis(AxisType.Y);
        yAxis.setLabel(sessionBean.getLocaleBundle().getString("general.nouns.scores"));
        yAxis.setMin(minValue - 3);
        yAxis.setMax(maxValue + 3);
    }

    public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
        List<Entry<K, V>> list = new ArrayList<>(map.entrySet());
        list.sort(Collections.reverseOrder(Entry.comparingByValue()));

        Map<K, V> result = new LinkedHashMap<>();
        for (Entry<K, V> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }

        return result;
    }

    public void loadBlocksForAGivenAnnotator() {
        blocksToEvaluate = new ArrayList();
        itemsToEvaluate = new ArrayList();
        //gettin the series of the annotator
        if (taskId == null) {
            return;
        }

        String seriesIndexAsString = "";

        try ( Jedis jedis = SingletonBean.getJedisPool().getResource()) {

            //getting the nb of items evaluated so far:
            String keyCountEvals = "task:" + taskId + ":annotations:indices:nb_evaluated_total:annotator:" + emailCurrentAnnotator;
            elementsEvaluatedSoFar = Integer.valueOf(jedis.get(keyCountEvals));

            String keySeriesWithAnnotators = "task:" + taskId + ":annotators";
            String seriesNbAndAnnotators = jedis.get(keySeriesWithAnnotators);
            JsonReader jr = Json.createReader(new StringReader(seriesNbAndAnnotators));
            JsonObject read = jr.readObject();
            boolean found = false;
            jr.close();
            for (String keySeries : read.keySet()) {
                JsonArray value = read.getJsonArray(keySeries);
                for (int i = 0; i < value.size(); i++) {
                    String element = value.getString(i);
                    if (element.equals(emailCurrentAnnotator) || publicTask) {
                        seriesIndexAsString = keySeries;
                        currentSeriesIndex = seriesIndexAsString;
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                System.out.println(emailCurrentAnnotator + " not found in the series/annot key value for the task " + taskId);
            }

            // feching the blocks corresponding to this series
            String taskwithSeries = "task:" + taskId + ":data";
            String jsonData = jedis.get(taskwithSeries);
            jr = Json.createReader(new StringReader(jsonData));
            read = jr.readObject();
            JsonObject series = read.getJsonObject(seriesIndexAsString);
            BlockToEvaluate block;
            for (String blockNumber : series.keySet()) {
                block = new BlockToEvaluate();
                JsonObject blockJsonObject = series.getJsonObject(blockNumber);
                for (String key : blockJsonObject.keySet()) {
                    block.addItem(blockJsonObject.getString(key));
                }
                blocksToEvaluate.add(block);
            }

            //last, retrieving the latest index that the annotator had annotated
            if (!publicTask) {
                String lastIndex = jedis.get("task:" + taskId + ":annotations:indices:last_index:annotator:" + emailCurrentAnnotator);
                indexBlock = Integer.valueOf(lastIndex);
                getIndexNextBlock();
            } else {
                // generating a random index so that every annotator starts at a different point
                indexBlock = ThreadLocalRandom.current().nextInt(0, blocksToEvaluate.size());
            }
        }

    }

    public void loadItemsForAGivenAnnotator() {
        blocksToEvaluate = new ArrayList();
        itemsToEvaluate = new ArrayList();
        if (taskId == null) {
            return;
        }

        try ( Jedis jedis = SingletonBean.getJedisPool().getResource()) {

            //getting the nb of items evaluated so far:
            String keyCountEvals = "task:" + taskId + ":annotations:indices:nb_evaluated_total:annotator:" + emailCurrentAnnotator;
            String nbEvaluatedTotal = jedis.get(keyCountEvals);
            if (nbEvaluatedTotal == null) {
                nbEvaluatedTotal = "0";
            }
            elementsEvaluatedSoFar = Integer.parseInt(nbEvaluatedTotal);

            // feching the items of the task
            String tasksWithItems = "task:" + taskId + ":data";
            itemsToEvaluate = jedis.lrange(tasksWithItems, 0, -1);

            // feching the categories of the categorization task
            String taskMetadataKey = "task:" + taskId;
            String metadata = jedis.get(taskMetadataKey);
            JsonbConfig config = new JsonbConfig().withNullValues(true);
            Jsonb jb = JsonbBuilder.create(config);
            metadataOfCurrentTask = jb.fromJson(metadata, TaskMetadata.class);
            multipleCategoriesPossible = metadataOfCurrentTask.isMultipleCategories();
            freeCommentPossible = metadataOfCurrentTask.getComment();

            //last, retrieving the latest index that the annotator had annotated
            if (!publicTask) {
                String lastIndex = jedis.get("task:" + taskId + ":annotations:indices:last_index:annotator:" + emailCurrentAnnotator);
                if (lastIndex == null || lastIndex.isBlank()) {
                    indexItem = 0;
                } else {
                    indexItem = Integer.valueOf(lastIndex);
                }
                getIndexNextItem();
            } else {
                // in a public task, generating a random index so that every annotator starts at a different point
                indexItem = ThreadLocalRandom.current().nextInt(0, itemsToEvaluate.size());
            }
        }

    }

    public String goToAnnotatorsManagement() {
        return "/labelling/annotators_management.xhtml?faces-redirect=true";
    }

    public String getTypeOfUpload() {
        return typeOfUpload;
    }

    public void setTypeOfUpload(String typeOfUpload) {
        this.typeOfUpload = typeOfUpload;
    }

    public List<SelectItem> getAnnotators() {
        return annotators;
    }

    public void setAnnotators(List<SelectItem> annotators) {
        this.annotators = annotators;
    }

    public String getNewAnnotatorName() {
        return newAnnotatorName;
    }

    public void setNewAnnotatorName(String newAnnotatorName) {
        this.newAnnotatorName = newAnnotatorName;
    }

    public String getEmailAnnotatorToDelete() {
        return emailAnnotatorToDelete;
    }

    public void setEmailAnnotatorToDelete(String emailAnnotatorToDelete) {
        this.emailAnnotatorToDelete = emailAnnotatorToDelete.toLowerCase().trim().replace(":", "");
    }

    public String getNewAnnotatorEmail() {
        return newAnnotatorEmail;
    }

    public void setNewAnnotatorEmail(String newAnnotatorEmail) {
        this.newAnnotatorEmail = newAnnotatorEmail.toLowerCase().trim().replace(":", "");
    }

    public String getEmailDesigner() {
        return emailDesigner;
    }

    public void setEmailDesigner(String emailDesigner) {
        this.emailDesigner = emailDesigner.toLowerCase().trim().replace(":", "");
    }

    public String getPasswordDesigner() {
        return passwordDesigner;
    }

    public void setPasswordDesigner(String passwordDesigner) {
        this.passwordDesigner = passwordDesigner.toLowerCase().trim().replace(":", "");
    }

    public List<SelectItem> getTasksIdsOfTheDesigner() {
        return tasksIdsOfTheDesigner;
    }

    public void setTasksIdsOfTheDesigner(List<SelectItem> tasksIdsOfTheDesigner) {
        this.tasksIdsOfTheDesigner = tasksIdsOfTheDesigner;
    }

    public String getPasswordAnnotator() {
        return passwordAnnotator;
    }

    public void setPasswordAnnotator(String passwordAnnotator) {
        this.passwordAnnotator = passwordAnnotator.replace(":", "");
    }

    public List<SelectItem> getTasksIdsOfTheAnnotator() {
        return tasksIdsOfTheAnnotator;
    }

    public void setTasksIdsOfTheAnnotator(List<SelectItem> tasksIdsOfTheAnnotator) {
        this.tasksIdsOfTheAnnotator = tasksIdsOfTheAnnotator;
    }

    public Map<String, TaskMetadata> getTasksMetadataOfTheDesigner() {
        return tasksMetadataOfTheDesigner;
    }

    public void setTasksMetadataOfTheDesigner(Map<String, TaskMetadata> tasksMetadataOfTheDesigner) {
        this.tasksMetadataOfTheDesigner = tasksMetadataOfTheDesigner;
    }

    public Map<String, TaskMetadata> getTasksMetadataOfTheAnnotator() {
        return tasksMetadataOfTheAnnotator;
    }

    public void setTasksMetadataOfTheAnnotator(Map<String, TaskMetadata> tasksMetadataOfTheAnnotator) {
        this.tasksMetadataOfTheAnnotator = tasksMetadataOfTheAnnotator;
    }

    public boolean isNewUser() {
        return newUser;
    }

    public void setNewUser(boolean newUser) {
        if (!newUser) {
            passwordDesigner = "";
        }
        this.newUser = newUser;
    }

    public List<BlockToEvaluate> getBlocksToEvaluate() {
        return blocksToEvaluate;
    }

    public void setBlocksToEvaluate(List<BlockToEvaluate> blocksToEvaluate) {
        this.blocksToEvaluate = blocksToEvaluate;
    }

    public BlockToEvaluate getBlockToEvaluate() {
        BlockToEvaluate block = blocksToEvaluate.get(indexBlock);
        return block;
    }

    public void setBlockToEvaluate(BlockToEvaluate blockToEvaluate) {
        this.blockToEvaluate = blockToEvaluate;
    }

    public String getItemToEvaluate() {
        String item = itemsToEvaluate.get(indexItem);
        return item;
    }

    public void setItemToEvaluate(String itemToEvaluate) {
        this.itemToEvaluate = itemToEvaluate;
    }

    public TaskMetadata getMetadataOfCurrentTask() {
        try ( Jedis jedis = SingletonBean.getJedisPool().getResource()) {
            metadataOfCurrentTask = new TaskMetadata(taskId, typeOfTask, jedis);
            return metadataOfCurrentTask;
        }
    }

    public int getIndexNextBlock() {
        if (indexBlock >= blocksToEvaluate.size() - 1) {
            indexBlock = 0;
            return indexBlock;
        } else {
            return indexBlock++;
        }
    }

    public int getIndexPreviousBlock() {
        if (indexBlock <= 0) {
            indexBlock = blocksToEvaluate.size() - 1;
            return indexBlock;
        } else {
            return indexBlock--;
        }
    }

    public int getIndexNextItem() {
        if (indexItem >= itemsToEvaluate.size() - 1) {
            indexItem = 0;
            return indexItem;
        } else {
            return indexItem++;
        }
    }

    public int getIndexPreviousItem() {
        if (indexItem <= 0) {
            indexItem = itemsToEvaluate.size() - 1;
            return indexItem;
        } else {
            return indexItem--;
        }
    }

    public void confirmAndAdvanceOneBlock() {
        saveBlockAnnotation(blocksToEvaluate.get(indexBlock));
        getIndexNextBlock();
    }

    public void confirmAndStepBackOneBlock() {
        saveBlockAnnotation(blocksToEvaluate.get(indexBlock));
        getIndexPreviousBlock();
    }

    public void skipAndAdvanceOneBlock() {
        getIndexNextBlock();
    }

    public void skipAndStepBackOneBlock() {
        getIndexPreviousBlock();
    }

    public void confirmAndAdvanceOneItem() {
        saveCategorizationAnnotation(itemsToEvaluate.get(indexItem));
        freeComment = "";
        selectedCategories = new ArrayList();
        selectedCategory = "";
        getIndexNextItem();
    }

    public void confirmAndStepBackOneItem() {
        saveCategorizationAnnotation(itemsToEvaluate.get(indexItem));
        freeComment = "";
        selectedCategories = new ArrayList();
        selectedCategory = "";
        getIndexPreviousItem();
    }

    public void skipAndAdvanceOneItem() {
        freeComment = "";
        selectedCategories = new ArrayList();
        getIndexNextItem();
    }

    public void skipAndStepBackOneItem() {
        freeComment = "";
        selectedCategories = new ArrayList();
        getIndexPreviousItem();
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public String getTypeOfTask() {
        return typeOfTask;
    }

    public void setTypeOfTask(String typeOfTask) {
        this.typeOfTask = typeOfTask;
    }

    public String getFreeComment() {
        return freeComment;
    }

    public void setFreeComment(String freeComment) {
        this.freeComment = freeComment;
    }

    public boolean isMultipleCategoriesPossible() {
        return multipleCategoriesPossible;
    }

    public void setMultipleCategoriesPossible(boolean multipleCategoriesPossible) {
        this.multipleCategoriesPossible = multipleCategoriesPossible;
    }

    public boolean isFreeCommentPossible() {
        return freeCommentPossible;
    }

    public void setFreeCommentPossible(boolean freeCommentPossible) {
        this.freeCommentPossible = freeCommentPossible;
    }

    public List<String> getSelectedCategories() {
        return selectedCategories;
    }

    public void setSelectedCategories(List<String> selectedCategories) {
        this.selectedCategories = selectedCategories;
    }

    public String getSelectedCategory() {
        return selectedCategory;
    }

    public void setSelectedCategory(String selectedCategory) {
        this.selectedCategory = selectedCategory;
    }

    public int getElementsEvaluatedSoFar() {
        return elementsEvaluatedSoFar;
    }

    public void setElementsEvaluatedSoFar(int elementsEvaluatedSoFar) {
        this.elementsEvaluatedSoFar = elementsEvaluatedSoFar;
    }

    public void saveBlockAnnotation(BlockToEvaluate block) {
        try ( Jedis jedis = SingletonBean.getJedisPool().getResource()) {

            boolean blockAlreadyEvaluated = false;

            if (!designerLoggedIn & !annotatorLoggedIn) {
                emailCurrentAnnotator = "public";
            }

            // SAVING THE EVALUATION AS A RAW VALUE
            String keyToRetrieve = "task:" + taskId + ":annotations:raw";
            String jsonEvaluatedBlocksInThisSeries = jedis.lindex(keyToRetrieve, Integer.valueOf(currentSeriesIndex));
            JsonReader jr = Json.createReader(new StringReader(jsonEvaluatedBlocksInThisSeries));
            JsonObject read = jr.readObject();
            if (read.containsKey(String.valueOf(indexBlock))) {
                blockAlreadyEvaluated = true;
            }
            JsonObjectBuilder target = Json.createObjectBuilder();
            read.forEach(target::add); // copy source into target
            jr.close();
            target.add(String.valueOf(indexBlock), block.toJson());
            jedis.lset(keyToRetrieve, Integer.valueOf(currentSeriesIndex).longValue(), target.build().toString());

            // INCREMENTING THE VALUE OF EVALS BY THIS ANNOTATOR IF THE BLOCK WASNOT EVALUATED BEFORE
            if (!blockAlreadyEvaluated) {
                String keyCountEvals = "task:" + taskId + ":annotations:indices:nb_evaluated_total:annotator:" + emailCurrentAnnotator;
                jedis.incr(keyCountEvals);
                elementsEvaluatedSoFar = Integer.valueOf(jedis.get(keyCountEvals));
            }
            // SAVING THE INDEX OF THE LATEST BLOCK EVALUATED
            String keyCountLastIndexEvaluated = "task:" + taskId + ":annotations:indices:last_index:annotator:" + emailCurrentAnnotator;
            jedis.set(keyCountLastIndexEvaluated, String.valueOf(indexBlock));

            // SAVING BWS SCORES
            String keyBwsCounts = "task:" + taskId + ":annotations:indices:counter_bws";
            String keyRawData = "task:" + taskId + ":rawdata";
            List<String> itemsInBlock = block.getItems();
            String bestOption = itemsInBlock.get(0);
            String worstOption = itemsInBlock.get(itemsInBlock.size() - 1);
            int indexItemBestOption;
            int indexItemWorstOption;
            List<String> rawItems = jedis.lrange(keyRawData, 0, -1);
            indexItemBestOption = rawItems.indexOf(bestOption);
            indexItemWorstOption = rawItems.indexOf(worstOption);
            String countBestItem = jedis.lindex(keyBwsCounts, indexItemBestOption);
            String countWorstItem = jedis.lindex(keyBwsCounts, indexItemWorstOption);
            Integer countAsIntegerBestItem = Integer.valueOf(countBestItem);
            Integer countAsIntegerWorstItem = Integer.valueOf(countWorstItem);
            jedis.lset(keyBwsCounts, indexItemBestOption, String.valueOf(countAsIntegerBestItem + 1));
            jedis.lset(keyBwsCounts, indexItemWorstOption, String.valueOf(countAsIntegerWorstItem - 1));
        }
    }

    public void saveCategorizationAnnotation(String item) {
        try ( Jedis jedis = SingletonBean.getJedisPool().getResource()) {

            if (!designerLoggedIn & !annotatorLoggedIn) {
                emailCurrentAnnotator = "public";
            }

            // SAVING THE EVALUATION AS A RAW VALUE
            String keyToRetrieve = "task:" + taskId + ":annotations:raw";
            Set<String> setOfEvaluatedItems = jedis.smembers(keyToRetrieve);
            Iterator<String> iteratorEvaluatedItems = setOfEvaluatedItems.iterator();
            String start = indexItem + ":" + emailCurrentAnnotator;
            String[] found = new String[1];
            iteratorEvaluatedItems.forEachRemaining(x -> {
                if (x.startsWith(start)) {
                    found[0] = x;
                }
            });

            if (selectedCategories != null) {
                boolean itemAlreadyEvaluated = false;

                //removing the existing evaluation, if the annotator had already done one
                if (found[0] != null && found[0].startsWith(start)) {
                    itemAlreadyEvaluated = true;
                    jedis.srem(keyToRetrieve, found[0]);
                }
                String categoriesAsString;
                if (multipleCategoriesPossible) {
                    categoriesAsString = String.join(":", selectedCategories);
                } else {
                    categoriesAsString = selectedCategory;
                }
                jedis.sadd(keyToRetrieve, start + ":" + categoriesAsString);

                // INCREMENTING THE VALUE OF EVALS BY THIS ANNOTATOR IF THE EVAL IS A NEW ONE
                if (!itemAlreadyEvaluated) {
                    String keyCountEvals = "task:" + taskId + ":annotations:indices:nb_evaluated_total:annotator:" + emailCurrentAnnotator;
                    jedis.incr(keyCountEvals);
                    elementsEvaluatedSoFar = Integer.valueOf(jedis.get(keyCountEvals));
                }
            }

            // SAVING THE COMMENT IF COMMENTS ARE POSSIBLE AND COMMENT NOT EMPTY
            if (freeCommentPossible && !freeComment.isBlank()) {
                keyToRetrieve = "task:" + taskId + ":annotations:annotator:" + emailCurrentAnnotator + ":comment";
                jedis.sadd(keyToRetrieve, indexItem + ":" + freeComment);
            }

            // SAVING THE INDEX OF THE LATEST ITEM EVALUATED
            String keyCountLastIndexEvaluated = "task:" + taskId + ":annotations:indices:last_index:annotator:" + emailCurrentAnnotator;
            jedis.set(keyCountLastIndexEvaluated, String.valueOf(indexItem));
        }
    }

    public void setMetadataOfCurrentTask(TaskMetadata metadataOfCurrentTask) {
        this.metadataOfCurrentTask = metadataOfCurrentTask;
    }

    public void designerLogin() {
        tasksIdsOfTheDesigner = new ArrayList();
        String not_logged_in = sessionBean.getLocaleBundle().getString("general.message.you_are_not_logged_in");
        String wrong_email_or_password = sessionBean.getLocaleBundle().getString("general.message.wrong_password_or_email");
        if (passwordDesigner == null || passwordDesigner.isBlank() || emailDesigner == null || emailDesigner.isBlank()) {
            designerLoggedIn = false;
            FacesContext.getCurrentInstance().
                    addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, not_logged_in, wrong_email_or_password));
            service.create(wrong_email_or_password);
            return;
        }
        try ( Jedis jedis = SingletonBean.getJedisPool().getResource()) {
            String keyToRetrieve = "designer:" + passwordDesigner + ":meta:email:" + emailDesigner;
            if (!jedis.exists(keyToRetrieve)) {
                designerLoggedIn = false;
                FacesContext.getCurrentInstance().
                        addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, not_logged_in, wrong_email_or_password));
                service.create(wrong_email_or_password);
                return;
            } else {
                annotatorLoggedIn = false;
                designerLoggedIn = true;
            }
        }
        if (designerLoggedIn) {
            String select_task = sessionBean.getLocaleBundle().getString("back.labelling.select_task");
            String you_are_logged_in = sessionBean.getLocaleBundle().getString("general.message.you_are_logged_in");
            FacesContext.getCurrentInstance().
                    addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, you_are_logged_in, select_task));
            service.create(you_are_logged_in + ". " + select_task);
            loadTasksOfADesigner();
        }

    }

    public void annotatorLogin() {
        String not_logged_in = sessionBean.getLocaleBundle().getString("general.message.you_are_not_logged_in");
        String wrong_email_or_password = sessionBean.getLocaleBundle().getString("general.message.wrong_password_or_email");

        tasksMetadataOfTheAnnotator = new HashMap();
        if (passwordAnnotator == null || passwordAnnotator.isBlank() || emailCurrentAnnotator == null || emailCurrentAnnotator.isBlank()) {
            annotatorLoggedIn = false;
            FacesContext.getCurrentInstance().
                    addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, not_logged_in, wrong_email_or_password));
            service.create(wrong_email_or_password);
            return;
        }
        try ( Jedis jedis = SingletonBean.getJedisPool().getResource()) {
            String keyToRetrieve = "annotator:email:" + emailCurrentAnnotator + ":password:" + passwordAnnotator;
            if (!jedis.exists(keyToRetrieve)) {
                annotatorLoggedIn = false;
                FacesContext.getCurrentInstance().
                        addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, not_logged_in, wrong_email_or_password));
                service.create(wrong_email_or_password);
                return;
            } else {
                designerLoggedIn = false;
                annotatorLoggedIn = true;

            }
        }
        if (annotatorLoggedIn) {
            String select_task = sessionBean.getLocaleBundle().getString("back.labelling.select_task");
            String you_are_logged_in = sessionBean.getLocaleBundle().getString("general.message.you_are_logged_in");
            FacesContext.getCurrentInstance().
                    addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, you_are_logged_in, select_task));
            service.create(you_are_logged_in + ". " + select_task);
            loadTasksMetadataOfAnAnnotator();
        }

    }

    public void loadTasksOfADesigner() {
        tasksIdsOfTheDesigner = new ArrayList();
        tasksMetadataOfTheDesigner = new HashMap();
        if (!designerLoggedIn) {
            return;
        }
        String taskIds;
        try ( Jedis jedis = SingletonBean.getJedisPool().getResource()) {
            String keyToRetrieve = "designer:" + passwordDesigner + ":meta:email:" + emailDesigner;
            if (!jedis.exists(keyToRetrieve)) {
                return;
            }
            taskIds = jedis.get(keyToRetrieve);
            JsonReader jr = Json.createReader(new StringReader(taskIds));
            JsonObject read = jr.readObject();
            JsonObjectBuilder target = Json.createObjectBuilder();
            read.forEach(target::add); // copy source into target
            jr.close();
            for (String key : read.keySet()) {
                String keyTask = "task:" + key;
                if (!jedis.exists(keyTask)) {
                    // remove all the tasks ids from the designer key, when these tasks don't exist anymore (their keyds starting with task... could not be found)
                    // STEP 1
                    target.remove(key); // add or update values
                    continue;
                }
                String metadata = jedis.get(keyTask);
                JsonbConfig config = new JsonbConfig().withNullValues(true);
                Jsonb jb = JsonbBuilder.create(config);
                TaskMetadata md = jb.fromJson(metadata, TaskMetadata.class);
                SelectItem item = new SelectItem();
                item.setValue(md.getTaskId());
                item.setLabel(md.getName());
                tasksIdsOfTheDesigner.add(item);
                tasksMetadataOfTheDesigner.put(md.getTaskId(), md);
            }

            // remove all the tasks ids from the designer key, when these tasks don't exist anymore (their keyds starting with task... could not be found)
            // STEP 2
            JsonObject updatedJson = target.build(); // build destination
            jedis.set(keyToRetrieve, updatedJson.toString());

        }
    }

    public void loadTasksMetadataOfAnAnnotator() {
        tasksMetadataOfTheAnnotator = new HashMap();
        tasksIdsOfTheAnnotator = new ArrayList();
        if (!annotatorLoggedIn) {
            return;
        }

        String taskIds = "";
        try ( Jedis jedis = SingletonBean.getJedisPool().getResource()) {
            String keyToRetrieve = "annotator:email:" + emailCurrentAnnotator + ":password:" + passwordAnnotator;
            taskIds = jedis.get(keyToRetrieve);
            JsonReader jr = Json.createReader(new StringReader(taskIds));
            JsonObject read = jr.readObject();
            jr.close();
            Set<String> tasksIdsKeysAsSet = new HashSet();
            for (String key : read.keySet()) {
                String keyTask = "task:" + key;
                if (!jedis.exists(keyTask)) {
                    continue;
                }
                String metadata = jedis.get(keyTask);
                JsonbConfig config = new JsonbConfig().withNullValues(true);
                Jsonb jb = JsonbBuilder.create(config);
                TaskMetadata md = jb.fromJson(metadata, TaskMetadata.class);
                tasksMetadataOfTheAnnotator.put(key, md);
                SelectItem item = new SelectItem();
                item.setValue(md.getTaskId());
                item.setLabel(md.getName());
                tasksIdsOfTheAnnotator.add(item);
                tasksIdsKeysAsSet.add(keyTask);
            }
        }
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }

    public void saveNewAnnotator() {
        String error = sessionBean.getLocaleBundle().getString("general.nouns.error");
        String error_go_back = sessionBean.getLocaleBundle().getString("back.labelling.error_go_back");

        if (taskId == null || taskId.isBlank() || newAnnotatorEmail == null || newAnnotatorEmail.isBlank()) {
            FacesContext.getCurrentInstance().
                    addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, error, error_go_back));
            service.create(error_go_back);
            return;
        }

        String password = "";

        //we first check whether this user already for another task
        // in which case, we'll reuse the password
        try ( Jedis jedis = SingletonBean.getJedisPool().getResource()) {
            String cur = SCAN_POINTER_START;
            Iterator<String> iteratorScanResults;
            boolean found = false;
            do {
                ScanResult<String> scanResult = jedis.scan(cur);
                iteratorScanResults = scanResult.getResult().iterator();
                while (iteratorScanResults.hasNext()) {
                    String next = iteratorScanResults.next();
                    if (next.startsWith("annotator") && next.contains(newAnnotatorEmail)) {
                        password = next.split(":")[4];
                        found = true;
                        break;
                    }
                }
                cur = scanResult.getCursor();
            } while (!cur.equals(SCAN_POINTER_START) & !found);

            //if the password is blank, it means this user does not exist
            // in which case, we create a new password
            if (password.isBlank()) {
                try ( InputStream inputStream = PlaceHolder.class
                        .getResourceAsStream("english-nouns.txt")) {
                    List<String> nouns = new BufferedReader(new InputStreamReader(inputStream,
                            StandardCharsets.UTF_8)).lines().collect(Collectors.toList());
                    Collections.shuffle(nouns, new Random());
                    String noun1 = nouns.get(0);
                    Collections.shuffle(nouns, new Random());
                    String noun2 = nouns.get(1);
                    password = noun1 + " of " + noun2;
                } catch (IOException ex) {
                    System.out.println("ex:" + ex.getMessage());
                }
            }
            Annotator annotatorToAdd = new Annotator(taskId, newAnnotatorEmail, password);

            String annotatorKey = annotatorToAdd.produceAnnotatorKey();
            if (jedis.exists(annotatorKey) && jedis.get(annotatorKey).contains(taskId)) {
                String annotator_already_exists = sessionBean.getLocaleBundle().getString("back.labelling.warning_annotator_exists");
                String choose_another = sessionBean.getLocaleBundle().getString("back.labelling.warning_annotator_exists.instructions");
                FacesContext.getCurrentInstance().
                        addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, annotator_already_exists, choose_another));
                service.create("🚨 " + annotator_already_exists + ". " + choose_another);
                return;
            } else if (!jedis.exists(annotatorKey)) {
                SendReport send = new SendReport();
                //sending email to the annotator
                send.initSendAnnotatorCredentials(newAnnotatorEmail, password, metadataOfCurrentTask.getTaskId(), metadataOfCurrentTask.getDescription(), metadataOfCurrentTask.getEmailDesigner(), typeOfTask);
                send.start();
                jedis.set(annotatorKey, "{}");
            }
            //saving the task id with the annotator.
            String listOfTasksAsJson = jedis.get(annotatorKey);
            if (listOfTasksAsJson == null) {
                listOfTasksAsJson = "{}";
            }
            JsonReader jr = Json.createReader(new StringReader(listOfTasksAsJson));
            JsonObject read = jr.readObject();
            jr.close();
            JsonObjectBuilder target = Json.createObjectBuilder();
            read.forEach(target::add); // copy source into target
            target.add(taskId, ""); // add or update values
            JsonObject updatedJson = target.build(); // build destination
            jedis.set(annotatorKey, updatedJson.toString());

            FacesContext.getCurrentInstance().
                    addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, sessionBean.getLocaleBundle().getString("back.labelling.annotator_created"), sessionBean.getLocaleBundle().getString("back.labelling.annotator_created") + newAnnotatorName + " - " + sessionBean.getLocaleBundle().getString("general.message.check_spam")));
            service.create(sessionBean.getLocaleBundle().getString("general.message.email_sent"));

            boolean foundEmptyAnnotatorSpotLeft = true;
            String keyTaskAndItsAnnotators = "task:" + taskId + ":annotators";

            // getting all the series and their annotators as a Json file
            String seriesNbAndAnnotators = jedis.get(keyTaskAndItsAnnotators);
            jr = Json.createReader(new StringReader(seriesNbAndAnnotators));
            read = jr.readObject();
            jr.close();

            if (typeOfTask.equals("bws")) {

                // among the series, find the one with the fewer annotators, to add this new annotator to it                
                int minNumberOfAnnotators = Integer.MAX_VALUE;
                JsonArray annotatorsForTheSeriesThatHasTheLessAnnotators = Json.createArrayBuilder().build();
                String seriesWithFewerAnnotators = "";
                for (String keySeries : read.keySet()) {
                    JsonArray annotatorsForThisSeries = read.getJsonArray(keySeries);
                    if (annotatorsForThisSeries == null) {
                        annotatorsForTheSeriesThatHasTheLessAnnotators = annotatorsForThisSeries;
                        seriesWithFewerAnnotators = keySeries;
                        break;
                    } else {
                        if (annotatorsForThisSeries.size() < minNumberOfAnnotators) {
                            minNumberOfAnnotators = annotatorsForThisSeries.size();
                            annotatorsForTheSeriesThatHasTheLessAnnotators = annotatorsForThisSeries;
                            seriesWithFewerAnnotators = keySeries;
                        }
                    }
                }

                // adding the new annotator to the json of the annotators of this series
                JsonArrayBuilder updatedListOfAnnotatorsForThisSeries = Json.createArrayBuilder();
                annotatorsForTheSeriesThatHasTheLessAnnotators.forEach(updatedListOfAnnotatorsForThisSeries::add);
                updatedListOfAnnotatorsForThisSeries.add(newAnnotatorEmail);
                target = Json.createObjectBuilder();
                read.forEach(target::add); // copy source into target
                target.add(seriesWithFewerAnnotators, updatedListOfAnnotatorsForThisSeries); // add or update values
                updatedJson = target.build(); // build destination

                // committing this updated list of annotators for this series on redis
                jedis.set(keyTaskAndItsAnnotators, updatedJson.toString());

                // saving the key for the index of the latest block or item annotated by the annotator
                String keyAnnotatorLastIndex = "task:" + taskId + ":annotations:indices:last_index:annotator:" + newAnnotatorEmail;
                jedis.set(keyAnnotatorLastIndex, "0");

                // saving the TOTAL count of blocks or items annotated by the annotator for this task
                String keyAnnotatorCountTotalEvaluated = "task:" + taskId + ":annotations:indices:nb_evaluated_total:annotator:" + newAnnotatorEmail;
                jedis.set(keyAnnotatorCountTotalEvaluated, "0");
            }
            if (typeOfTask.equals("categorization")) {
                JsonArrayBuilder updatedListOfAnnotatorsForThisCategorizationTask = Json.createArrayBuilder();
                read.getJsonArray("0").forEach(updatedListOfAnnotatorsForThisCategorizationTask::add);
                updatedListOfAnnotatorsForThisCategorizationTask.add(newAnnotatorEmail);
                target = Json.createObjectBuilder();
                target.add("0", updatedListOfAnnotatorsForThisCategorizationTask); // add or update values
                updatedJson = target.build(); // build destination
                jedis.set(keyTaskAndItsAnnotators, updatedJson.toString());
                jedis.set("task:" + taskId + ":annotations:indices:last_index:annotator:" + newAnnotatorEmail, "0");
                jedis.set("task:" + taskId + ":annotations:indices:nb_evaluated_total:annotator:" + newAnnotatorEmail, "0");
            }
            if (!foundEmptyAnnotatorSpotLeft) {
                service.create(sessionBean.getLocaleBundle().getString("back.labelling.all_annotators_provisioned"));
            }
        }
    }

    public void deleteAnnotator() {
        if (emailAnnotatorToDelete.isBlank() || taskId.isBlank()) {
            FacesContext.getCurrentInstance().
                    addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, sessionBean.getLocaleBundle().getString("back.labelling.no_annotator_selected"), sessionBean.getLocaleBundle().getString("back.labelling.select_annotator")));
            return;
        }
        try ( Jedis jedis = SingletonBean.getJedisPool().getResource()) {
            Annotator annotatorToDelete = new Annotator(taskId, emailAnnotatorToDelete, jedis);

            String annotatorKeyToDelete = annotatorToDelete.produceAnnotatorKey();
            if (jedis.exists(annotatorKeyToDelete)) {
                jedis.del(annotatorKeyToDelete);
                String annotator_deleted = sessionBean.getLocaleBundle().getString("back.labelling.no_annotator_deleted");
                FacesContext.getCurrentInstance().
                        addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, annotator_deleted, annotator_deleted));
                service.create(annotator_deleted);

            } else {
                String annotator_not_found = sessionBean.getLocaleBundle().getString("back.labelling.annotator_not_found");
                FacesContext.getCurrentInstance().
                        addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, annotator_not_found, annotator_not_found));
                service.create(annotator_not_found);

            }
        }
    }

    public BarChartModel getBarChartModel() {
        initBarModel();
        return barChartModel;
    }

    public void setBarChartModel(BarChartModel barChartModel) {
        this.barChartModel = barChartModel;
    }

    public String host() {
        return RemoteLocal.getDomain();
    }

    public int getNumberOfElementsToEvaluate() {
        if (blocksToEvaluate == null || blocksToEvaluate.isEmpty()) {
            return itemsToEvaluate.size();
        } else {
            return blocksToEvaluate.size();
        }
    }

    public void setNumberOfElementsToEvaluate() {

    }
}
