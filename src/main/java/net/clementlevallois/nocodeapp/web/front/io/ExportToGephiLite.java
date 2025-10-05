/*
 * Copyright Clement Levallois 2021-2025. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.io;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
 * An application-scoped bean for handling data exports to Gephi Lite.
 *
 * This service takes data, either from a persisted file or a GEXF string,
 * places it in an accessible location, and generates a URL for visualization
 * in Gephi Lite.
 */
@ApplicationScoped
public class ExportToGephiLite {

    private static final Logger LOG = Logger.getLogger(ExportToGephiLite.class.getName());

    @Inject
    private ApplicationPropertiesBean applicationProperties;

    /**
     * Creates a Gephi Lite link by copying a result file identified by its unique persistence ID.
     *
     * @param dataPersistenceUniqueId The unique ID corresponding to the data file in the temp directory.
     * @param shareGephiLitePublicly  If true, the generated link will be in a publicly accessible folder.
     * @return A URL to the Gephi Lite visualization, or an empty string on failure.
     */
    public String exportAndReturnLinkFromId(String dataPersistenceUniqueId, boolean shareGephiLitePublicly) {
        Path userGeneratedGephiLiteDirectoryFullPath = applicationProperties.getUserGeneratedGephiLiteDirectoryFullPath(shareGephiLitePublicly);
        Path sourceFile = applicationProperties.getTempFolderFullPath().resolve(dataPersistenceUniqueId + "_result");

        if (!Files.exists(sourceFile)) {
            LOG.log(Level.SEVERE, "Source file for Gephi Lite export does not exist: {0}", sourceFile);
            return "";
        }

        // Generate a unique, non-negative file name
        long randomId = ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
        String gephiLiteGexfFileName = "gephi-lite_" + randomId + ".gexf";
        Path destinationFile = userGeneratedGephiLiteDirectoryFullPath.resolve(gephiLiteGexfFileName);

        try {
            Files.createDirectories(destinationFile.getParent());
            Files.copy(sourceFile, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            LOG.log(Level.INFO, "Copied Gephi Lite file to: {0}", destinationFile);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Could not copy Gephi Lite file from {0} to {1}", new Object[]{sourceFile, destinationFile});
            LOG.log(Level.SEVERE, "Exception details:", ex);
            return "";
        }

        return generateFinalUrl(destinationFile);
    }

    /**
     * Creates a Gephi Lite link from a GEXF graph provided as a string.
     *
     * @param gexf                   The GEXF graph content as a String.
     * @param shareGephiLitePublicly If true, the link will be publicly accessible.
     * @return A URL to the Gephi Lite visualization, or an empty string on failure.
     */
    public String exportAndReturnLinkFromString(String gexf, boolean shareGephiLitePublicly) {
        if (gexf == null || gexf.isBlank()) {
            LOG.warning("GEXF string provided for Gephi Lite export was null or empty.");
            return "";
        }

        Path userGeneratedGephiLiteDirectoryFullPath = applicationProperties.getUserGeneratedGephiLiteDirectoryFullPath(shareGephiLitePublicly);

        // Generate a unique, non-negative file name
        long randomId = ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
        String gephiLiteGexfFileName = "gephi-lite_" + randomId + ".gexf";
        Path destinationFile = userGeneratedGephiLiteDirectoryFullPath.resolve(gephiLiteGexfFileName);

        try {
            Files.createDirectories(destinationFile.getParent());
            Files.writeString(destinationFile, gexf, StandardCharsets.UTF_8);
            LOG.log(Level.INFO, "Wrote Gephi Lite GEXF string to: {0}", destinationFile);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Error writing user-generated GEXF file for Gephi Lite to: " + destinationFile, ex);
            return "";
        }

        return generateFinalUrl(destinationFile);
    }

    /**
     * Private helper to construct the final URL for the Gephi Lite file.
     *
     * @param fullPathToGeneratedFile The full path to the newly created GEXF file.
     * @return A URL pointing to the Gephi Lite instance with the file loaded.
     */
    private String generateFinalUrl(Path fullPathToGeneratedFile) {
        if (RemoteLocal.isLocal()) {
            // For local development, return the direct file system path.
            return fullPathToGeneratedFile.toString();
        }

        Path relativePathFromProjectRootToGephiLiteFolder = applicationProperties.getRelativePathFromProjectRootToGephiLiteFolder();
        Path gephiLiteRootFullPath = applicationProperties.getGephiLiteRootFullPath();

        // Base URL for the Gephi Lite application
        String urlWithoutParamValue = RemoteLocal.getDomain() + "/" + relativePathFromProjectRootToGephiLiteFolder.toString().replace("\\", "/") + "/index.html?file=";

        // The path to the file must be relative to the Gephi Lite web application's root
        Path relativePathToGephiLiteFile = gephiLiteRootFullPath.relativize(fullPathToGeneratedFile);

        String fullUrl = urlWithoutParamValue + relativePathToGephiLiteFile.toString().replace("\\", "/");

        LOG.log(Level.INFO, "Generated Gephi Lite public URL: {0}", fullUrl);
        return fullUrl;
    }
}