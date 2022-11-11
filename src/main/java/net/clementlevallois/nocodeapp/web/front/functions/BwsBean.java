/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.functions;

import io.mikael.urlbuilder.UrlBuilder;
import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonWriter;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.functions.model.Occurrence;
import net.clementlevallois.functions.model.bibd.BIBDResults;
import net.clementlevallois.functions.model.bibd.Block;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonBean;
import net.clementlevallois.nocodeapp.web.front.redisops.Annotator;
import net.clementlevallois.functions.model.bibd.DataBlocks;
import net.clementlevallois.nocodeapp.web.front.redisops.TaskMetadata;
import net.clementlevallois.nocodeapp.web.front.logview.NotificationService;
//import net.clementlevallois.testbibd.BIBDCustom;
import redis.clients.jedis.Jedis;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped

public class BwsBean implements Serializable {

    @Inject
    NotificationService service;

    @Inject
    LabellingBean labellingBean;

    @Inject
    SessionBean sessionBean;

    private String taskId;
    private String typeOfTask;
    private int blockSize_k = 4;
    private int itemTotal_Appearances_r = 4;
    private int numberOfItems_v = 0;
    private int nbOfBlocks_b;
    private Integer nbOfAnnotators = 1;
    private Map<Integer, String> items;
    private Map<Integer, List<Block>> blocksPerAnnotator;
    private BIBDResults results;

    public BwsBean() {
    }

    public void onload() {
        this.taskId = labellingBean.getTaskId();
        this.typeOfTask = labellingBean.getTypeOfTask();
        this.results = null;
        blocksPerAnnotator = new HashMap();
        items = labellingBean.getItemsToLabel(taskId);
        numberOfItems_v = items.size();
    }

    public void createBlocks() {
        JsonObjectBuilder itemsAsJsonObjectBuilder = Json.createObjectBuilder();

        Iterator<Map.Entry<Integer, String>> iterator = items.entrySet().iterator();
        int counter = 0;
        while (iterator.hasNext()) {
            Map.Entry<Integer, String> next = iterator.next();
            itemsAsJsonObjectBuilder.add(String.valueOf(counter), next.getValue());
        }

        if (labellingBean.getPublicTask()) {
            nbOfAnnotators = 1;
        }

        HttpRequest request;
        HttpClient client = HttpClient.newHttpClient();
        Set<CompletableFuture> futures = new HashSet();

        StringWriter sw = new StringWriter(128);
        try ( JsonWriter jw = Json.createWriter(sw)) {
            jw.write(itemsAsJsonObjectBuilder.build());
        }
        String jsonString = sw.toString();

        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofString(jsonString);

        URI uri = UrlBuilder
                .empty()
                .withScheme("http")
                .withPort(7002)
                .withHost("localhost")
                .withPath("api/bibd/getblocks")
                .addParameter("nbItems", String.valueOf(numberOfItems_v))
                .addParameter("nbBlocks", String.valueOf(nbOfBlocks_b))
                .addParameter("nbAnnotators", String.valueOf(nbOfAnnotators))
                .addParameter("nbAppearances", String.valueOf(itemTotal_Appearances_r))
                .addParameter("blockSize", String.valueOf(blockSize_k))
                .addParameter("maxSamePairs", String.valueOf(itemTotal_Appearances_r))
                .toUri();

        request = HttpRequest.newBuilder()
                .POST(bodyPublisher)
                .uri(uri)
                .build();

        CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(
                resp -> {
                    byte[] body = resp.body();
                    try (
                     ByteArrayInputStream bis = new ByteArrayInputStream(body);  ObjectInputStream ois = new ObjectInputStream(bis)) {
                        results = (BIBDResults) ois.readObject();
                    } catch (IOException | ClassNotFoundException ex) {
                        Logger.getLogger(PdfMatcherBean.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
        );

        futures.add(future);

        CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
        combinedFuture.join();

        blocksPerAnnotator = spreadBlocksAmongAnnotators(results.getSeriesOfBlocks(), nbOfAnnotators);

        if (labellingBean.getPublicTask()) {
            saveBlocks();
        }
    }

    public Map<Integer, List<Block>> spreadBlocksAmongAnnotators(List<List<Block>> blocks, int annotators) {
        Map<Integer, List<Block>> blocksPerAnnotatorToReturn = new HashMap();
        if (blocks.size() == annotators) {
            int i = 1;
            for (List<Block> blockSeries : blocks) {
                blocksPerAnnotatorToReturn.put(i++, blockSeries);
            }
            return blocksPerAnnotatorToReturn;

        } else {
            // TO DO
            return blocksPerAnnotatorToReturn;
        }
    }

    public String saveBlocks() {
        try ( Jedis jedis = SingletonBean.getJedisPool().getResource()) {

            TaskMetadata metadata = new TaskMetadata(taskId, typeOfTask, jedis);
            metadata.setR(results.getNumberOfAppearances_r());
            metadata.setV(results.getNbItems_v());
            metadata.setB(results.getActualNbOfBlocks());
            metadata.setLambda(results.getActualNbOfDuplicatePairs());
            metadata.setLambdaAverage(results.getAverageNbOfDistinctPairs());
            metadata.setK(results.getNbItemsPerBlock_k());
            metadata.setAnnotators(results.getNbAnnotators());
            JsonbConfig config = new JsonbConfig().withNullValues(true);
            Jsonb jsonb = JsonbBuilder.create(config);
            String metadataAsJSon = jsonb.toJson(metadata);

            DataBlocks data = new DataBlocks();
            data.setData(results.getSeriesOfBlocks());

            String keyTaskData = "task:" + taskId + ":data";
            String keyTaskMetadata = "task:" + taskId;
            String keyTaskAnnotators = "task:" + taskId + ":annotators";
            String keyTaskAnnotatorCounterNoEmailButPublic = "task:" + taskId + ":annotations:indices:last_index:annotator:public";

            if (jedis.exists(keyTaskData)) {
                String warningTitle = sessionBean.getLocaleBundle().getString("back.bwsbean.warning.task_exists_title");
                String warningDetails = sessionBean.getLocaleBundle().getString("back.bwsbean.warning.task_exists_details");
                FacesContext.getCurrentInstance().
                        addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, warningTitle, warningDetails));
                service.create("🚨 " + warningTitle + " " + warningDetails);
            } else {

                // saving a key for the public case
                if (labellingBean.getPublicTask()) {
                    jedis.set(keyTaskAnnotatorCounterNoEmailButPublic, "0");
                }

                // saving the metadata
                jedis.set(keyTaskMetadata, metadataAsJSon);

                // saving the blocks
                jedis.set(keyTaskData, data.toJson());

                // saving the annotators for this task (empty so far EXCEPT  FOR THE DESIGNER ITSELF, but so that the key-value in redis is ready to add some)
                JsonObjectBuilder job = Json.createObjectBuilder();
                JsonArrayBuilder arrayOfAnnotators = Json.createArrayBuilder();
                arrayOfAnnotators.add(labellingBean.getEmailDesigner());
                job.add("0", arrayOfAnnotators);
                for (int i = 1; i < results.getNbAnnotators(); i++) {
                    job.add(String.valueOf(i), Json.createArrayBuilder());
                }
                jedis.set(keyTaskAnnotators, job.build().toString());
            }

            //saving the task id with the designer who is the first annotator.
            Annotator annotatorToAdd = new Annotator(taskId, labellingBean.getEmailDesigner(), labellingBean.getPasswordDesigner());
            String annotatorKey = annotatorToAdd.produceAnnotatorKey();

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

            // save the key to store annotations for all series
            String keyAnnotationsRaw = "task:" + taskId + ":annotations:raw";
            for (int i = 0; i < results.getNbAnnotators(); i++) {
                jedis.lpush(keyAnnotationsRaw, "{}");
            }

            // saving the key for the index of the latest block annotated by the annotator
            String keyAnnotatorLastIndex = "task:" + taskId + ":annotations:indices:last_index:annotator:" + labellingBean.getEmailDesigner();
            jedis.set(keyAnnotatorLastIndex, "0");

            // saving the TOTAL count of blocks annotated by the annotator for this task
            String keyAnnotatorCountTotalEvaluated = "task:" + taskId + ":annotations:indices:nb_evaluated_total:annotator:" + labellingBean.getEmailDesigner();
            jedis.set(keyAnnotatorCountTotalEvaluated, "0");

        }
        return "/labelling/role.xhtml?faces-redirect=true";
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
    }

    public Map<Integer, String> getItems() {
        return items;
    }

    public void setItems(Map<Integer, String> items) {
        this.items = items;
    }

    public int getBlockSize_k() {
        return blockSize_k;
    }

    public void setBlockSize_k(int blockSize_k) {
        this.blockSize_k = blockSize_k;
    }

    public int getItemTotal_Appearances_r() {
        return itemTotal_Appearances_r;
    }

    public void setItemTotal_Appearances_r(int itemTotal_Appearances_r) {
        this.itemTotal_Appearances_r = itemTotal_Appearances_r;
    }

    public int getNumberOfItems_v() {
        return numberOfItems_v;
    }

    public void setNumberOfItems_v(int numberOfItems_v) {
        this.numberOfItems_v = numberOfItems_v;
    }

    public int getNbOfBlocks_b() {
        nbOfBlocks_b = (numberOfItems_v * itemTotal_Appearances_r) / blockSize_k;
        return nbOfBlocks_b;
    }

    public void setNbOfBlocks_b(int nbOfBlocks_b) {
        this.nbOfBlocks_b = nbOfBlocks_b;
    }

    public int getNbOfAnnotators() {
        return nbOfAnnotators;
    }

    public void setNbOfAnnotators(int nbOfAnnotators) {
        this.nbOfAnnotators = nbOfAnnotators;
    }

    public Map<Integer, List<Block>> getBlocksPerAnnotator() {
        return blocksPerAnnotator;
    }

    public void setBlocksPerAnnotator(Map<Integer, List<Block>> blocksPerAnnotator) {
        this.blocksPerAnnotator = blocksPerAnnotator;
    }

    public BIBDResults getResults() {
        return results;
    }

    public void setResults(BIBDResults results) {
        this.results = results;
    }

}
