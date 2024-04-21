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
public class ExportToGephisto {

    public static String exportAndReturnLink(boolean shareGephistoPublicly, String dataPersistenceUniqueId, ApplicationPropertiesBean applicationProperties) {
        Path relativePathFromProjectRootToGephistoFolder = applicationProperties.getRelativePathFromProjectRootToGephistoFolder();
        Path gephistoRootFullPath = applicationProperties.getGephistoRootFullPath();
        Path userGeneratedGephistoDirectoryFullPath = applicationProperties.getUserGeneratedGephistoDirectoryFullPath(shareGephistoPublicly);
        Path tempFolderRelativePath = Path.of(applicationProperties.getTempFolderFullPath().toString(), dataPersistenceUniqueId + "_result");
        long nextLong = ThreadLocalRandom.current().nextLong();
        String gephistoGexfFileName = "gephisto_" + String.valueOf(Math.abs(nextLong)) + ".gexf";

        Path fullPathFileToWrite = userGeneratedGephistoDirectoryFullPath.resolve(Path.of(gephistoGexfFileName));
        if (Files.exists(tempFolderRelativePath)) {
            try {
                Files.copy(tempFolderRelativePath, fullPathFileToWrite, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                Logger.getLogger(ExportToGephisto.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        if (RemoteLocal.isLocal()) {
            return fullPathFileToWrite.toString();
        }
        String urlWithoutParamValue = RemoteLocal.getDomain() + "/" + relativePathFromProjectRootToGephistoFolder + "/index.html?gexf-file=";
        Path relativePathToGephistoFile = gephistoRootFullPath.relativize(fullPathFileToWrite);
        String fullUrl = urlWithoutParamValue + relativePathToGephistoFile;
        return fullUrl;
    }
    
        public static String exportAndReturnLink(String gexf, Path directoryToSaveFile, Path relativePathFromProjectRootToGephistoFolder, Path gephistoRootFullPath) {
        long nextLong = ThreadLocalRandom.current().nextLong();
        String gephistoGexfFileName = "gephisto_" + String.valueOf(Math.abs(nextLong)) + ".gexf";

        Path fullPathFileToWrite = directoryToSaveFile.resolve(Path.of(gephistoGexfFileName));
        try {
            Files.writeString(fullPathFileToWrite, gexf, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            System.out.println("error when writing user generated gexf file to disk for export to Gephisto");
            System.out.println("ex: " + ex);
        }
        if (RemoteLocal.isLocal()) {
            return fullPathFileToWrite.toString();
        }

        String urlWithoutParamValue = RemoteLocal.getDomain() + "/" + relativePathFromProjectRootToGephistoFolder + "/index.html?gexf-file=";
        Path relativePathToGephistoFile = gephistoRootFullPath.relativize(fullPathFileToWrite);
        String fullUrl = urlWithoutParamValue + relativePathToGephistoFile;
        return fullUrl;
    }
}
