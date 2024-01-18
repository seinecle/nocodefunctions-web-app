/*
 * Copyright Clement Levallois 2021-2023. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.importdata;

import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public String gotToFunctionWithDataInBulk() throws IOException {
        Path tempFolderRelativePath = applicationProperties.getTempFolderFullPath();
        pathOfTempData = Path.of(tempFolderRelativePath.toString(), dataPersistenceUniqueId);
        return "/" + sessionBean.getFunction() + "/" + sessionBean.getFunction() + ".xhtml?faces-redirect=true";
    }

    public Path getPathOfTempData() {
        return pathOfTempData;
    }

}
