/*
 * Copyright Clement Levallois 2021-2024. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.importdata;

import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped

public class ImportSimpleLinesBean implements Serializable {

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    SessionBean sessionBean;

    private Boolean bulkData = false;

    private String jobId;

    private String jsonKey;
    private Boolean oneJsonPerLine = false;

    Path pathOfTempData;

    public Boolean getBulkData() {
        return bulkData;
    }

    public void setBulkData(Boolean bulkData) {
        this.bulkData = bulkData;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
        if (this.jobId == null) {
            this.jobId = UUID.randomUUID().toString().substring(0, 10);
        }
        try {
            pathOfTempData = applicationProperties.getTempFolderFullPath().resolve(this.jobId);
            Files.createDirectories(pathOfTempData);
        } catch (IOException ex) {
            Logger.getLogger(ImportSimpleLinesBean.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public String getJobId() {
        return jobId;
    }

    public String gotToFunctionWithDataInBulk() {
        return ".xhtml?faces-redirect=true";
    }

    public String getJsonKey() {
        return jsonKey;
    }

    public void setJsonKey(String jsonKey) {
        this.jsonKey = jsonKey;
    }

    public Boolean getOneJsonPerLine() {
        return oneJsonPerLine;
    }

    public void setOneJsonPerLine(Boolean oneJsonPerLine) {
        this.oneJsonPerLine = oneJsonPerLine;
    }

    public Path getPathOfTempData() {
        return pathOfTempData;
    }

}
