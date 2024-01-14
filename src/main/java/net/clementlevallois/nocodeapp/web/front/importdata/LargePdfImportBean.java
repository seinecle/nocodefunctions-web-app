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
import net.clementlevallois.nocodeapp.web.front.logview.LogBean;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped

public class LargePdfImportBean implements Serializable {

    @Inject
    LogBean logBean;

    @Inject
    SessionBean sessionBean;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    DataImportBean dataImportBean;
    
    private Boolean bulkData = false;

    private String uniqueId;
    
    private Map<Integer,String> mapOfLines = new HashMap();
    
    Path pathOfTempData;

    public Boolean getBulkData() {
        return bulkData;
    }

    public void setBulkData(Boolean bulkData) {
        this.bulkData = bulkData;
    }

    public void setUniqueId() {
        uniqueId = UUID.randomUUID().toString().substring(0, 10);
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public Map<Integer, String> getMapOfLines() {
        return mapOfLines;
    }

    public void setMapOfLines(Map<Integer, String> mapOfLines) {
        this.mapOfLines = mapOfLines;
    }
    
    public String gotToFunctionWithDataInBulk() throws IOException {
        Path tempFolderRelativePath = applicationProperties.getTempFolderRelativePath();
        pathOfTempData = Path.of(tempFolderRelativePath.toString(), uniqueId);
        mapOfLines = new HashMap();
        int i = 0;
        List<String> allLines = Files.readAllLines(pathOfTempData, StandardCharsets.UTF_8);
        for (String line: allLines){
            mapOfLines.put(i++, line);
        }
        return "/" + "cowo" + "/" + "cowo" + ".xhtml?faces-redirect=true";
    }

    public Path getPathOfTempData() {
        return pathOfTempData;
    }
    
    

}
