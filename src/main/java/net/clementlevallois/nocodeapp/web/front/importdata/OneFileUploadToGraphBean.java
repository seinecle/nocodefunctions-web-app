package net.clementlevallois.nocodeapp.web.front.importdata;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
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
public class OneFileUploadToGraphBean {

    @Inject
    ImportGraphBean importGraphBean;

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

            String jobId = importGraphBean.getJobId();

            String uniqueFileId = UUID.randomUUID().toString().substring(0, 10);

            Path pathToFile = Path.of(applicationProperties.getTempFolderFullPath().toString(), jobId + uniqueFileId);
            Files.write(pathToFile, fileAllBytes);

            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("back.import.file_successful_upload.opening") + f.getFileName() + sessionBean.getLocaleBundle().getString("back.import.file_successful_upload.closing"));
        } catch (IOException ex) {
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.encoding_error"));
            Logger.getLogger(OneFileUploadToGraphBean.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
