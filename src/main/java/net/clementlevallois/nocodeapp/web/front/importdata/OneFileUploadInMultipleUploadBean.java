/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.clementlevallois.nocodeapp.web.front.importdata;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;

/**
 *
 * @author LEVALLOIS
 */
@Named(value = "oneFileUploadInMultipleUploadBean")
@RequestScoped
public class OneFileUploadInMultipleUploadBean {

    @Inject
    DataImportBean dataImportBean;

    public void handleFileUpload(FileUploadEvent event) {
        try {
            UploadedFile f = event.getFile();
            FileUploaded oneFile = new FileUploaded(f.getInputStream(), f.getFileName());
            dataImportBean.getFilesUploaded().add(oneFile);
            dataImportBean.setReadButtonDisabled(Boolean.FALSE);
            dataImportBean.setRenderProgressBar(Boolean.TRUE);
            
        } catch (IOException ex) {
            Logger.getLogger(OneFileUploadInMultipleUploadBean.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

}
