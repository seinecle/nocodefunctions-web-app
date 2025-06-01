/*
 * Copyright Clement Levallois 2021-2024. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.importdata;

import io.mikael.urlbuilder.UrlBuilder;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped

public class ImportGraphBean implements Serializable {

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    SessionBean sessionBean;

    @Inject
    BackToFrontMessengerBean logBean;

    private Boolean bulkData = false;

    private String jobId;

    private List<String> namesOfNodeAttributes;

    Path pathOfTempDataForThisJob;

    public Boolean getBulkData() {
        return bulkData;
    }

    public void setBulkData(Boolean bulkData) {
        this.bulkData = bulkData;
    }

    public void setJobId(String jobIdParam) {
        try {
            if (jobIdParam == null){
                this.jobId = UUID.randomUUID().toString().substring(0, 10);
            }
            namesOfNodeAttributes = new ArrayList();
            Path tempFolderRelativePath = applicationProperties.getTempFolderFullPath();
            pathOfTempDataForThisJob = Path.of(tempFolderRelativePath.toString(), jobId);
            Files.createDirectories(pathOfTempDataForThisJob);
            
            Properties privateProperties = applicationProperties.getPrivateProperties();
            
            HttpClient client = HttpClient.newHttpClient();
            
            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(Integer.valueOf(privateProperties.getProperty("nocode_import_port")))
                    .withHost("localhost")
                    .withPath("api/import/graphops/getNamesOfNodeAttributes")
                    .addParameter("jobId", jobIdParam)
                    .toUri();
            
            HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .uri(uri)
                    .build();
            
            try {
                HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
                String body = resp.body();
                if (resp.statusCode() != 200) {
                    System.out.println("return of node attributes by the API was not a 200 code");
                    String errorMessage = body;
                    System.out.println(errorMessage);
                    logBean.addOneNotificationFromString(errorMessage);
                    sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "💔", errorMessage);
                } else {
                    JsonReader reader = Json.createReader(new StringReader(body));
                    JsonObject jsonObject = reader.readObject();
                    
                    for (String nextKey : jsonObject.keySet()) {
                        namesOfNodeAttributes.add(jsonObject.getString(nextKey));
                    }
                }
            } catch (HttpTimeoutException e) {
                logBean.addOneNotificationFromString("💔 " + sessionBean.getLocaleBundle().getString("general.message.error_url_timed_out"));
            } catch (ConnectException e) {
                logBean.addOneNotificationFromString("💔 " + sessionBean.getLocaleBundle().getString("general.message.error_no_connection"));
            } catch (IOException | InterruptedException ex) {
                Logger.getLogger(ImportGraphBean.class.getName()).log(Level.SEVERE, null, ex);
            }
            
        } catch (IOException ex) {
            System.getLogger(ImportGraphBean.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }

    }

    public String getJobId() {
        return jobId;
    }

    public String gotToFunctionWithDataInBulk() {
        return "/" + sessionBean.getFunction() + "/" + sessionBean.getFunction() + ".xhtml?faces-redirect=true";
    }

    public List<String> getNamesOfNodeAttributes() {
        return namesOfNodeAttributes;
    }
}
