package net.clementlevallois.nocodeapp.web.front.importdata;

import io.mikael.urlbuilder.UrlBuilder;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.importers.model.CellRecord;
import net.clementlevallois.importers.model.DataFormatConverter;
import net.clementlevallois.importers.model.ImagesPerFile;
import net.clementlevallois.importers.model.SheetModel;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.logview.LogBean;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;

/**
 *
 * @author LEVALLOIS
 */
@Named
@RequestScoped
public class OneLargePdfFileUploadInMultipleUploadBean {

    @Inject
    LargePdfImportBean largePdfImportBean;

    @Inject
    LogBean logBean;

    @Inject
    SessionBean sessionBean;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    public void handleFileUpload(FileUploadEvent event) {

        Properties privateProperties = applicationProperties.getPrivateProperties();
        try {
            UploadedFile f = event.getFile();
            if (f == null) {
                return;
            }

            String currentFunction = sessionBean.getFunction();

            if (currentFunction == null) {
                logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.error_function_not_set"));
                return;
            }

            byte[] fileAllBytes = f.getInputStream().readAllBytes();

            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("back.import.file_successful_upload.opening") + f.getFileName() + sessionBean.getLocaleBundle().getString("back.import.file_successful_upload.closing"));

            HttpRequest request;
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(fileAllBytes);
            URI uri;
            uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(Integer.valueOf(privateProperties.getProperty("nocode_import_port")))
                    .withHost("localhost")
                    .withPath("api/import/pdf")
                    .addParameter("fileName", f.getFileName())
                    .toUri();
            request = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .uri(uri)
                    .build();
            HttpResponse<byte[]> resp = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = resp.body();
            StringBuilder sb = new StringBuilder();
            if (resp.statusCode() == 200) {
                try (
                        ByteArrayInputStream bis = new ByteArrayInputStream(body); ObjectInputStream ois = new ObjectInputStream(bis)) {
                    List<SheetModel> tempResult = (List<SheetModel>) ois.readObject();
                    for (SheetModel sm : tempResult) {
                        List<CellRecord> cellRecords = sm.getColumnIndexToCellRecords().get(0);
                        if (cellRecords == null) {
                            break;
                        }
                        for (CellRecord cr : cellRecords) {
                            String line = cr.getRawValue();
                            if (line != null && !line.isBlank() && line.trim().contains(" ")) {
                                sb.append(cr.getRawValue()).append("\n");
                            }
                        }
                    }

                } catch (IOException | ClassNotFoundException ex) {
                    Logger.getLogger(DataImportBean.class.getName()).log(Level.SEVERE, null, ex);
                }
            } else {
                System.out.println("return of pdf reader by the API was not a 200 code");
                String errorMessage = new String(body, StandardCharsets.UTF_8);
                System.out.println(errorMessage);
                logBean.addOneNotificationFromString(errorMessage);
                sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "💔", errorMessage);

            }

            Path tempFolderRelativePath = applicationProperties.getTempFolderRelativePath();
            String uniqueId = largePdfImportBean.getUniqueId();
            Path fullPathForFileContainingTextInput = Path.of(tempFolderRelativePath.toString(), uniqueId);
            Files.writeString(fullPathForFileContainingTextInput, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(OneLargePdfFileUploadInMultipleUploadBean.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
