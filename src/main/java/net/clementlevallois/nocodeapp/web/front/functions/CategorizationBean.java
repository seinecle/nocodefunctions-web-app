/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.functions;

import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import jakarta.annotation.PostConstruct;
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
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonBean;
import net.clementlevallois.nocodeapp.web.front.redisops.Annotator;
import net.clementlevallois.nocodeapp.web.front.redisops.TaskMetadata;
import net.clementlevallois.nocodeapp.web.front.logview.NotificationService;
import redis.clients.jedis.Jedis;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped

public class CategorizationBean implements Serializable {

    @Inject
    NotificationService service;

    @Inject
    LabellingBean labellingBean;

    @Inject
    SessionBean sessionBean;

    private String taskId;
    private String typeOfTask;
    private List<Item> categories;
    private Boolean multiple;
    private Boolean comment;

    public CategorizationBean() {
    }

    public void onload() {
        this.taskId = labellingBean.getTaskId();
        this.typeOfTask = labellingBean.getTypeOfTask();
    }

    @PostConstruct
    public void init() {
        categories = new ArrayList();
        categories.add(new Item());

    }

    public void submit(int index) {
        categories.get(index).setConfirmed(true);
    }

    public void save() {
        String categories_saved = sessionBean.getLocaleBundle().getString("back.categorizationbean.categories_are_saved");
        FacesContext.getCurrentInstance().
                addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, categories_saved, ""));
    }

    public String confirmTask() {
        try ( Jedis jedis = SingletonBean.getJedisPool().getResource()) {
            TaskMetadata metadata = new TaskMetadata(taskId, typeOfTask, jedis);
            List<String> categoriesToString = new ArrayList();

            // adding this default category to all groups of categories
            Item itemNoCategoryApplies = new Item();
            itemNoCategoryApplies.setValue("no category applies");
            categories.add(itemNoCategoryApplies);

            for (Item item : categories) {
                categoriesToString.add(item.getValue().replace(":", "-")); // this is because by convention, I have deciced that the : character is reserved as a delimiter in the redis db - see the redis-key.txt file
            }
            metadata.setCategories(categoriesToString);
            metadata.setComment(comment);
            metadata.setMultipleCategories(multiple);
            metadata.setAnnotators(100);

            JsonbConfig config = new JsonbConfig().withNullValues(true);
            Jsonb jsonb = JsonbBuilder.create(config);
            String metadataAsJSon = jsonb.toJson(metadata);
            List<String> rawItems;

            String keyRawData = "task:" + taskId + ":rawdata";
            if (!jedis.exists(keyRawData)) {
                String data_not_found = sessionBean.getLocaleBundle().getString("general.message.data_not_found");
                service.create(data_not_found);
                return "";

            } else {
                rawItems = jedis.lrange(keyRawData, 0, -1);
            }

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

                // saving the data to annotate
                for (String item : rawItems) {
                    jedis.lpush(keyTaskData, item);
                }

                // saving the annotators (empty so far EXCEPT for the DESIGNER ITESELF, but so that the key-value in redis is ready to add some)
                JsonObjectBuilder job = Json.createObjectBuilder();
                JsonArrayBuilder arrayOfAnnotators = Json.createArrayBuilder();
                arrayOfAnnotators.add(labellingBean.getEmailDesigner());
                job.add("0", arrayOfAnnotators);
                for (int i = 1; i < 100; i++) {
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
            // NO NEED AS THE SET WILL be created when we will store the first value in it

            // saving the key for the index of the latest block annotated by the annotator
            String keyAnnotatorLastIndex = "task:" + taskId + ":annotations:indices:last_index:annotator:" + labellingBean.getEmailDesigner();
            jedis.set(keyAnnotatorLastIndex, "0");

            // saving the TOTAL count of items annotated by the annotator for this task
            String keyAnnotatorCountTotalEvaluated = "task:" + taskId + ":annotations:indices:nb_evaluated_total:annotator:" + labellingBean.getEmailDesigner();
            jedis.set(keyAnnotatorCountTotalEvaluated, "0");

        }
        return "/labelling/role.xhtml?faces-redirect=true";

    }

    public void addOneCategory() {
        categories.add(new Item());
    }

    public void removeOneCategory(int index) {
        categories.remove(index);
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
    }

    public String getTypeOfTask() {
        return typeOfTask;
    }

    public void setTypeOfTask(String typeOfTask) {
        this.typeOfTask = typeOfTask;
    }

    public List<Item> getCategories() {
        return categories;
    }

    public void setCategories(List<Item> categories) {
        this.categories = categories;
    }

    public Boolean getMultiple() {
        return multiple;
    }

    public void setMultiple(Boolean multiple) {
        this.multiple = multiple;
    }

    public Boolean getComment() {
        return comment;
    }

    public void setComment(Boolean comment) {
        this.comment = comment;
    }

    public class Item {

        private String value;
        private Boolean confirmed;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public Boolean getConfirmed() {
            return confirmed;
        }

        public void setConfirmed(Boolean confirmed) {
            this.confirmed = confirmed;
        }
    }
}
