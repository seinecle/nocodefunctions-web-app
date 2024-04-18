package net.clementlevallois.nocodeapp.web.front.importdata;

import io.mikael.urlbuilder.UrlBuilder;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
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

    public void handleFileUpload(FileUploadEvent event) {
        try {
            UploadedFile f = event.getFile();
            if (f == null) {
                return;
            }
            byte[] fileAllBytes = f.getInputStream().readAllBytes();
            String fileName = f.getFileName();

            String currentFunction = sessionBean.getFunction();

            Properties privateProperties = applicationProperties.getPrivateProperties();

            if (currentFunction == null) {
                logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.error_function_not_set"));
                return;
            }

            String dataPersistenceUniqueId = simpleLineImportBean.getDataPersistenceUniqueId();

            String uniqueFileId = UUID.randomUUID().toString().substring(0, 10);

            Path pathToFile = Path.of(applicationProperties.getTempFolderFullPath().toString(), dataPersistenceUniqueId + uniqueFileId);
            Files.write(pathToFile, fileAllBytes);

            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("back.import.file_successful_upload.opening") + f.getFileName() + sessionBean.getLocaleBundle().getString("back.import.file_successful_upload.closing"));

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = null;
            URI uri = null;
            switch (fileName.substring(fileName.lastIndexOf(".") + 1)) {

                case "pdf" -> {
                    uri = UrlBuilder
                            .empty()
                            .withScheme("http")
                            .withPort(Integer.valueOf(privateProperties.getProperty("nocode_import_port")))
                            .withHost("localhost")
                            .withPath("api/import/pdf/simpleLines")
                            .addParameter("fileName", fileName)
                            .addParameter("uniqueFileId", uniqueFileId)
                            .addParameter("dataPersistenceId", dataPersistenceUniqueId)
                            .toUri();

                    request = HttpRequest.newBuilder()
                            .GET()
                            .uri(uri)
                            .build();

                    HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
                    String body = resp.body();
                    if (resp.statusCode() != 200) {
                        System.out.println("return of pdf reader by the API was not a 200 code");
                        String errorMessage = body;
                        System.out.println(errorMessage);
                        logBean.addOneNotificationFromString(errorMessage);
                        sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "💔", errorMessage);
                    }

                }

                case "txt" -> {
                    uri = UrlBuilder
                            .empty()
                            .withScheme("http")
                            .withPort(Integer.valueOf(privateProperties.getProperty("nocode_import_port")))
                            .withHost("localhost")
                            .withPath("api/import/txt/simpleLines")
                            .addParameter("fileName", fileName)
                            .addParameter("uniqueFileId", uniqueFileId)
                            .addParameter("dataPersistenceId", dataPersistenceUniqueId)
                            .toUri();

                    request = HttpRequest.newBuilder()
                            .GET()
                            .uri(uri)
                            .build();

                    HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
                    String body = resp.body();
                    if (resp.statusCode() != 200) {
                        System.out.println("return of txt reader by the API was not a 200 code");
                        String errorMessage = body;
                        System.out.println(errorMessage);
                        logBean.addOneNotificationFromString(errorMessage);
                        sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "💔", errorMessage);
                    }
                }

                case "json" -> {
                    String jsonKey = simpleLineImportBean.getJsonKey();
                    if (jsonKey == null || jsonKey.isBlank()) {
                        logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("back.import.json_key_missing"));
                        if (!applicationProperties.getTempFolderFullPath().equals(pathToFile)) {
                            Files.deleteIfExists(pathToFile);
                        }
                        return;
                    }
                    uri = UrlBuilder
                            .empty()
                            .withScheme("http")
                            .withPort(Integer.valueOf(privateProperties.getProperty("nocode_import_port")))
                            .withHost("localhost")
                            .withPath("api/import/json/simpleLines")
                            .addParameter("uniqueFileId", uniqueFileId)
                            .addParameter("dataPersistenceId", dataPersistenceUniqueId)
                            .addParameter("jsonKey", jsonKey)
                            .toUri();

                    request = HttpRequest.newBuilder()
                            .GET()
                            .uri(uri)
                            .build();

                    HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
                    String body = resp.body();
                    if (resp.statusCode() != 200) {
                        System.out.println("return of json reader by the API was not a 200 code");
                        String errorMessage = body;
                        System.out.println(errorMessage);
                        logBean.addOneNotificationFromString(errorMessage);
                        sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "💔", errorMessage);
                    }
                }

                default -> {
                    logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.file_extension_not_recognized"));
                    return;
                }
            }
        } catch (IOException | InterruptedException ex) {
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.encoding_error"));
            Logger.getLogger(OneFileUploadToSimpleLinesBean.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
