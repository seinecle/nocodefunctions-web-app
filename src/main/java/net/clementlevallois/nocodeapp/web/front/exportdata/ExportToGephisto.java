/*
 * Copyright Clement Levallois 2021-2023. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.exportdata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.utils.ApplicationProperties;

/**
 *
 * @author LEVALLOIS
 */
public class ExportToGephisto {

    public static String exportAndReturnLink(String gexf, boolean shareGephistoPublicly) {
        long nextLong = ThreadLocalRandom.current().nextLong();
        String gephistoGexfFileName = "gephisto_" + String.valueOf(Math.abs(nextLong)) + ".gexf";

        Path dir = shareGephistoPublicly ? ApplicationProperties.getUserGeneratedGephistoPublicDirectoryFullPath() : ApplicationProperties.getUserGeneratedGephistoPrivateDirectoryFullPath();
        Path fullPathFileToWrite = dir.resolve(Path.of(gephistoGexfFileName));
        try {
            Files.writeString(fullPathFileToWrite, gexf, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            System.out.println("error when writing user generated gexf file to disk for export to Gephisto");
            System.out.println("ex: " + ex);
        }
        if (RemoteLocal.isLocal()) {
            return fullPathFileToWrite.toString();
        }

        Path relativePathFromProjectRootToGephistoFolder = ApplicationProperties.getRootProjectFullPath().relativize(ApplicationProperties.getGephistoRootFullPath());

        String urlWithoutParamValue = RemoteLocal.getDomain() + "/" + relativePathFromProjectRootToGephistoFolder + "/index.html?gexf-file=";
        Path relativePathToGephistoFile = ApplicationProperties.getGephistoRootFullPath().relativize(fullPathFileToWrite);
        String fullUrl = urlWithoutParamValue + relativePathToGephistoFile;
        return fullUrl;
    }

}
