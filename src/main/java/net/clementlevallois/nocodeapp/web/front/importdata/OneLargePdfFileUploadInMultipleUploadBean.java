package net.clementlevallois.nocodeapp.web.front.importdata;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
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
public class OneFileUploadInMultipleUploadBean {

    @Inject
    DataImportBean dataImportBean;

    @Inject
    LogBean logBean;

    @Inject
    SessionBean sessionBean;

    public void handleFileUpload(FileUploadEvent event) {
        try {
            UploadedFile f = event.getFile();
            if (f == null) {
                return;
            }

            String currentFunction = sessionBean.getFunction();

            if (currentFunction == null) {
                logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.error_function_not_set"));
            }

            FileUploaded oneFile = new FileUploaded(f.getInputStream(), f.getFileName());

            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("back.import.file_successful_upload.opening") + oneFile.getFileName() + sessionBean.getLocaleBundle().getString("back.import.file_successful_upload.closing"));

            dataImportBean.getFilesUploaded().add(oneFile);
            dataImportBean.setReadButtonDisabled(Boolean.FALSE);
            dataImportBean.setRenderProgressBar(Boolean.TRUE);

            if (currentFunction.equals("pdf_region_extractor")) {
                dataImportBean.storePdFile(oneFile);
            }

        } catch (IOException ex) {
            Logger.getLogger(OneFileUploadInMultipleUploadBean.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

}
