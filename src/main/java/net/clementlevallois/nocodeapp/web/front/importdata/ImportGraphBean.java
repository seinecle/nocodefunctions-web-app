package net.clementlevallois.nocodeapp.web.front.importdata;

import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;

import java.io.Serializable;
import java.io.StringReader;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@Named
@SessionScoped
public class ImportGraphBean implements Serializable {

    @Inject
    SessionBean sessionBean;

    @Inject
    BackToFrontMessengerBean logBean;

    @Inject
    MicroserviceHttpClient microserviceHttpClient;

    private Boolean bulkData = false;

    private String jobId;

    private List<String> namesOfNodeAttributes;


    public Boolean getBulkData() {
        return bulkData;
    }

    public void setBulkData(Boolean bulkData) {
        this.bulkData = bulkData;
    }

    public void setJobId(String jobIdParam) {
        try {
            if (jobIdParam == null) {
                this.jobId = UUID.randomUUID().toString().substring(0, 10);
            } else {
                this.jobId = jobIdParam;
            }

            namesOfNodeAttributes = new ArrayList<>();

            var response = microserviceHttpClient.importService()
                    .get("/import/graphops/getNamesOfNodeAttributes")
                    .addQueryParameter("jobId", jobId)
                    .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofString())
                    .join();

            try (JsonReader reader = Json.createReader(new StringReader(response))) {
                JsonObject jsonObject = reader.readObject();
                jsonObject.keySet().forEach(key -> namesOfNodeAttributes.add(jsonObject.getString(key)));
            }

        } catch (Exception e) {
            logBean.addOneNotificationFromString("💔 " + e.getMessage());
            Logger.getLogger(ImportGraphBean.class.getName()).log(Level.SEVERE, null, e);
        }
    }

    public String getJobId() {
        return jobId;
    }

    public String gotToFunctionWithDataInBulk() {
        return  ".xhtml?faces-redirect=true";
    }

    public List<String> getNamesOfNodeAttributes() {
        return namesOfNodeAttributes;
    }
} 
