package net.clementlevallois.nocodeapp.web.front.importdata;

import io.mikael.urlbuilder.UrlBuilder;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParsingException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import static java.util.stream.Collectors.toList;
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
                    }
                    StringBuilder sb = new StringBuilder();
                    List<String> lines = Files.lines(pathToFile, StandardCharsets.UTF_8).collect(toList());
                    // Detect if the root is an object or an array and handle accordingly
                    for (String line : lines) {
                        try {
                            JsonValue value = Json.createReader(new StringReader(line)).read();
                            traverse(value, "", jsonKey, sb); // Start traversal with an empty prefix for the root
                        } catch (JsonParsingException e) {
                            logBean.addOneNotificationFromString("Parsing error: Invalid JSON structure - " + e.getMessage());
                            return;
                        }
                    }
                    Path fullPathForFileContainingTextInput = Path.of(applicationProperties.getTempFolderFullPath().toString(), dataPersistenceUniqueId);
                    if (Files.notExists(fullPathForFileContainingTextInput)) {
                        Files.createFile(fullPathForFileContainingTextInput);
                    }
                    concurrentWriting(fullPathForFileContainingTextInput, sb.toString());
                    if (!applicationProperties.getTempFolderFullPath().equals(pathToFile)) {
                        Files.deleteIfExists(pathToFile);
                    }
                }

                default -> {
                    logBean.addOneNotificationFromString("Parsing error: file extension not recognized. Should be txt, json or pdf.");
                    return;
                }
            }

        } catch (IOException | InterruptedException ex) {
            logBean.addOneNotificationFromString("possible error with the encoding of your file. The text should be encoded in UTF-8.");
            Logger.getLogger(OneFileUploadToSimpleLinesBean.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private synchronized void concurrentWriting(Path path, String string) {
        File file = path.toFile();
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw"); FileChannel fileChannel = raf.getChannel()) {
            try (FileLock lock = fileChannel.lock()) {
                byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
                raf.seek(raf.length());
                raf.write(bytes);
            }
        } catch (IOException e) {
            System.out.println("error in the concurrent write to file in txt on front sides");
        }
    }

    private static void traverse(JsonValue jsonValue, String key, String TARGET_KEY, StringBuilder foundValues) {
        switch (jsonValue.getValueType()) {
            case OBJECT -> {
                JsonObject obj = jsonValue.asJsonObject();
                obj.keySet().forEach(k -> traverse(obj.get(k), key.isEmpty() ? k : key + "." + k, TARGET_KEY, foundValues));
            }
            case ARRAY -> {
                JsonArray array = jsonValue.asJsonArray();
                for (int i = 0; i < array.size(); i++) {
                    traverse(array.get(i), key + "[" + i + "]", TARGET_KEY, foundValues);
                }
            }
            case STRING -> {
                if (key.equals(TARGET_KEY)) {
                    String textualValue = jsonValue.toString();
                    textualValue = textualValue.replaceAll("\\n", ". ");
                    textualValue = textualValue.replaceAll("\\R", ". ");
                    if (textualValue.startsWith("\"")) {
                        textualValue = textualValue.substring(1);
                    }
                    if (textualValue.endsWith("\"")) {
                        textualValue = textualValue.substring(0, textualValue.length() - 1);
                    }
                    foundValues.append(textualValue).append("\n");
                }
            }
            case NUMBER, TRUE, FALSE, NULL -> {
            }
        }
        // Handle other types if necessary
    }

}
