/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.labelling;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonBean;
import redis.clients.jedis.Jedis;

/**
 *
 * @author LEVALLOIS
 */
public class TaskMetadata {

    private int v;
    private int b;
    private int k;
    private float r;
    private int lambda;
    private float lambdaAverage;
    private int annotators;
    private String name;
    private String description;
    private String taskId;
    private String emailDesigner;
    private String typeOfTask;
    private List<String> categories;
    private boolean multipleCategories;
    private boolean comment;

    public TaskMetadata(String taskId, String typeOfTask) {
        this.taskId = taskId;
        this.typeOfTask = typeOfTask;
        if (typeOfTask.equals("categorization")) {
            categories = new ArrayList();
        }
        loadSavedFields(typeOfTask);
    }

    public TaskMetadata() {
    }

    public void loadSavedFields(String typeOfTask) {
        try (Jedis jedis = SingletonBean.getJedisPool().getResource()) {
            String key = "task:" + taskId;
            if (jedis.exists(key)) {
                String metadataAsJson = jedis.get(key);
                JsonReader jr = Json.createReader(new StringReader(metadataAsJson));
                JsonObject read = jr.readObject();
                jr.close();
                if (typeOfTask.equals("bws")) {
                    v = read.getInt("v");
                    b = read.getInt("b");
                    k = read.getInt("k");
                    r = read.getJsonNumber("lambdaAverage").bigDecimalValue().floatValue();
                }
                if (typeOfTask.equals("categorization")) {
                    JsonArray jArray = read.getJsonArray("categories");
                    if (jArray != null) {
                        for (int i = 0; i < jArray.size(); i++) {
                            categories.add(jArray.getString(i));
                        }
                    }
                }
                annotators = read.getInt("annotators");
                name = read.getString("name");
                description = read.getString("description");
                emailDesigner = read.getString("emailDesigner");
            }
        }
    }

    public int getV() {
        return v;
    }

    public void setV(int v) {
        this.v = v;
    }

    public int getB() {
        return b;
    }

    public void setB(int b) {
        this.b = b;
    }

    public int getK() {
        return k;
    }

    public void setK(int k) {
        this.k = k;
    }

    public float getR() {
        return r;
    }

    public void setR(float r) {
        this.r = r;
    }

    public int getLambda() {
        return lambda;
    }

    public void setLambda(int lambda) {
        this.lambda = lambda;
    }

    public float getLambdaAverage() {
        return lambdaAverage;
    }

    public void setLambdaAverage(float lambdaAverage) {
        this.lambdaAverage = lambdaAverage;
    }

    public int getAnnotators() {
        return annotators;
    }

    public void setAnnotators(int annotators) {
        this.annotators = annotators;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getEmailDesigner() {
        return emailDesigner;
    }

    public void setEmailDesigner(String emailDesigner) {
        this.emailDesigner = emailDesigner;
    }

    public String getTypeOfTask() {
        return typeOfTask;
    }

    public void setTypeOfTask(String typeOfTask) {
        this.typeOfTask = typeOfTask;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    public boolean isMultipleCategories() {
        return multipleCategories;
    }

    public void setMultipleCategories(boolean multipleCategories) {
        this.multipleCategories = multipleCategories;
    }

    public boolean getComment() {
        return comment;
    }

    public void setComment(boolean comment) {
        this.comment = comment;
    }

    public String produceJson() {
        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add("v", v);
        job.add("b", b);
        job.add("k", k);
        job.add("r", r);
        job.add("lambda", lambda);
        job.add("lambdaAverage", lambdaAverage);
        job.add("annotators", annotators);
        job.add("description", description);
        job.add("name", name);
        job.add("taskId", taskId);
        job.add("emailDesigner", emailDesigner);
        job.add("typeOfTask", typeOfTask);
        return job.build().toString();

    }

}
