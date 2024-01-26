package net.clementlevallois.nocodeapp.web.front.importdata;

import io.mikael.urlbuilder.UrlBuilder;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
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
    ImportSimpleLinesBean largePdfImportBean;

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

            String dataPersistenceUniqueId = largePdfImportBean.getDataPersistenceUniqueId();

            Path pathToFile = Path.of(applicationProperties.getTempFolderFullPath().toString(), dataPersistenceUniqueId + fileName);
            Files.write(pathToFile, fileAllBytes);

            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("back.import.file_successful_upload.opening") + f.getFileName() + sessionBean.getLocaleBundle().getString("back.import.file_successful_upload.closing"));

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = null;
            URI uri = null;
            if (fileName.endsWith("pdf")) {
                uri = UrlBuilder
                        .empty()
                        .withScheme("http")
                        .withPort(Integer.valueOf(privateProperties.getProperty("nocode_import_port")))
                        .withHost("localhost")
                        .withPath("api/import/pdf/simpleLines")
                        .addParameter("fileName", fileName)
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

            } else if (fileName.endsWith("txt")) {
                String readString = Files.readString(pathToFile, StandardCharsets.UTF_8);
                Path fullPathForFileContainingTextInput = Path.of(applicationProperties.getTempFolderFullPath().toString(), dataPersistenceUniqueId);
                if (Files.notExists(fullPathForFileContainingTextInput)) {
                    Files.createFile(fullPathForFileContainingTextInput);
                }
                concurrentWriting(fullPathForFileContainingTextInput, readString);
                if (!applicationProperties.getTempFolderFullPath().equals(pathToFile)) {
                    Files.deleteIfExists(pathToFile);
                }
            }

        } catch (IOException | InterruptedException ex) {
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

}
