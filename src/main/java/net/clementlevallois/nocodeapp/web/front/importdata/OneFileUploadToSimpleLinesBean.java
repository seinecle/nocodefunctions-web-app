package net.clementlevallois.nocodeapp.web.front.importdata;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.Globals.Names;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;

/**
 *
 * @author LEVALLOIS
 */
@Named
@RequestScoped
public class OneFileUploadToSimpleLinesBean {

    @Inject
    ImportSimpleLinesBean simpleLineImportBean;

    @Inject
    BackToFrontMessengerBean logBean;

    @Inject
    SessionBean sessionBean;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    MicroserviceHttpClient microserviceHttpClient;

    public void handleFileUpload(FileUploadEvent event) {
        try {
            UploadedFile f = event.getFile();
            if (f == null) {
                return;
            }

            byte[] fileAllBytes = f.getInputStream().readAllBytes();
            String fileName = f.getFileName();
            Globals.Names currentFunction = null;

            if (currentFunction == null) {
                logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.error_function_not_set"));
                return;
            }

            String jobId = simpleLineImportBean.getJobId();
            Files.createDirectories(applicationProperties.getTempFolderFullPath().resolve(jobId));
            String uniqueFileId = UUID.randomUUID().toString().substring(0, 10);
            Path pathToFile = applicationProperties.getTempFolderFullPath().resolve(jobId).resolve(jobId + uniqueFileId);
            Files.write(pathToFile, fileAllBytes);

            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("back.import.file_successful_upload.opening")
                    + fileName + sessionBean.getLocaleBundle().getString("back.import.file_successful_upload.closing"));

            var importClient = microserviceHttpClient.importService();

            String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
            String apiPath;
            MicroserviceHttpClient.GetRequestBuilder builder;

            switch (extension) {
                case "pdf" ->
                    apiPath = "import/pdf/simpleLines";
                case "txt" ->
                    apiPath = "import/txt/simpleLines";
                case "json" -> {
                    String jsonKey = simpleLineImportBean.getJsonKey();
                    if (jsonKey == null || jsonKey.isBlank()) {
                        logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("back.import.json_key_missing"));
                        Files.deleteIfExists(pathToFile);
                        return;
                    }
                    apiPath = "import/json/simpleLines";
                }
                default -> {
                    logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.file_extension_not_recognized"));
                    return;
                }
            }

            builder = importClient.get(apiPath)
                    .addQueryParameter("jobId", jobId)
                    .addQueryParameter("uniqueFileId", uniqueFileId)
                    .addQueryParameter("fileName", fileName);

            if (extension.equals("json")) {
                builder.addQueryParameter("jsonKey", simpleLineImportBean.getJsonKey());
            }

            builder.sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofString()).join();

            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("back.import.api_call_success"));

        } catch (IOException | CompletionException ex) {
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.encoding_error"));
            Logger.getLogger(OneFileUploadToSimpleLinesBean.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
