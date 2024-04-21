/*
 * Copyright Clement Levallois 2021-2024. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.importdata;

import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.UUID;
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

    private String dataPersistenceUniqueId;

    private String jsonKey;
    private Boolean oneJsonPerLine = false;

    Path pathOfTempData;

    public Boolean getBulkData() {
        return bulkData;
    }

    public void setBulkData(Boolean bulkData) {
        this.bulkData = bulkData;
    }

    public void setDataPersistenceUniqueId(String dataPersistenceUniqueId) {
        Path tempFolderRelativePath = applicationProperties.getTempFolderFullPath();
        pathOfTempData = Path.of(tempFolderRelativePath.toString(), dataPersistenceUniqueId);
        if (!pathOfTempData.toFile().exists()) {
            setDataPersistenceUniqueId();
        } else {
            this.dataPersistenceUniqueId = dataPersistenceUniqueId;
        }
        pathOfTempData = Path.of(tempFolderRelativePath.toString(), this.dataPersistenceUniqueId);
    }

    public void setDataPersistenceUniqueId() {
        this.dataPersistenceUniqueId = UUID.randomUUID().toString().substring(0, 10);
    }

    public String getDataPersistenceUniqueId() {
        return dataPersistenceUniqueId;
    }

    public String gotToFunctionWithDataInBulk() {
        return "/" + sessionBean.getFunction() + "/" + sessionBean.getFunction() + ".xhtml?faces-redirect=true";
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
}
