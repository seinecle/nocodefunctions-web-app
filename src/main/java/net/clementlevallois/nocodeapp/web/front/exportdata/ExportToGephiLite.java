/*
 * Copyright Clement Levallois 2021-2024. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.exportdata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;

/**
 *
 * @author LEVALLOIS
 */
public class ExportToGephiLite {

    public static String exportAndReturnLink(boolean shareGephiLitePublicly, String dataPersistenceUniqueId, ApplicationPropertiesBean applicationProperties) {
        Path relativePathFromProjectRootToGephiLiteFolder = applicationProperties.getRelativePathFromProjectRootToGephiLiteFolder();
        Path gephiLiteRootFullPath = applicationProperties.getGephiLiteRootFullPath();
        Path userGeneratedGephiLiteDirectoryFullPath = applicationProperties.getUserGeneratedGephiLiteDirectoryFullPath(shareGephiLitePublicly);
        Path tempFolderRelativePath = Path.of(applicationProperties.getTempFolderFullPath().toString(), dataPersistenceUniqueId + "_result");
        long nextLong = ThreadLocalRandom.current().nextLong();
        String gephiLiteGexfFileName = "gephi-lite_" + String.valueOf(Math.abs(nextLong)) + ".gexf";

        Path fullPathFileToWrite = userGeneratedGephiLiteDirectoryFullPath.resolve(Path.of(gephiLiteGexfFileName));
        if (Files.exists(tempFolderRelativePath)) {
            try {
                Files.copy(tempFolderRelativePath, fullPathFileToWrite, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                Logger.getLogger(ExportToGephiLite.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        if (RemoteLocal.isLocal()) {
            return fullPathFileToWrite.toString();
        }
        String urlWithoutParamValue = RemoteLocal.getDomain() + "/" + relativePathFromProjectRootToGephiLiteFolder + "/index.html?file=";
        Path relativePathToGephiLiteFile = gephiLiteRootFullPath.relativize(fullPathFileToWrite);
        String fullUrl = urlWithoutParamValue + relativePathToGephiLiteFile;
        return fullUrl;
    }
    
        public static String exportAndReturnLink(String gexf, Path directoryToSaveFile, Path relativePathFromProjectRootToGephiLiteFolder, Path gephiLiteRootFullPath) {
        long nextLong = ThreadLocalRandom.current().nextLong();
        String gephiLiteGexfFileName = "gephi-lite_" + String.valueOf(Math.abs(nextLong)) + ".gexf";

        Path fullPathFileToWrite = directoryToSaveFile.resolve(Path.of(gephiLiteGexfFileName));
        try {
            Files.writeString(fullPathFileToWrite, gexf, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            System.out.println("error when writing user generated gexf file to disk for export to Gephi Lite");
            System.out.println("ex: " + ex);
        }
        if (RemoteLocal.isLocal()) {
            return fullPathFileToWrite.toString();
        }

        String urlWithoutParamValue = RemoteLocal.getDomain() + "/" + relativePathFromProjectRootToGephiLiteFolder + "/index.html?file=";
        Path relativePathToGephiLiteFile = gephiLiteRootFullPath.relativize(fullPathFileToWrite);
        String fullUrl = urlWithoutParamValue + relativePathToGephiLiteFile;
        return fullUrl;
    }
}
