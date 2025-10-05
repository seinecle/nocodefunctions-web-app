/*
 * Copyright Clement Levallois 2021-2025. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.io;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient.MicroserviceCallException;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;

@ApplicationScoped
public class ExportToVosViewer {

    private static final Logger LOG = Logger.getLogger(ExportToVosViewer.class.getName());

    @Inject
    private MicroserviceHttpClient microserviceHttpClient;

    @Inject
    private ApplicationPropertiesBean applicationProperties;

    /**
     * Converts a gexf file to a VOSviewer visualization with custom labels.
     *
     * @param jobId The unique ID of the persisted data.
     * @param shareVVPublicly         If true, the link will be publicly accessible.
     * @param item                    The label for a single item (e.g., "Author").
     * @param link                    The label for a link (e.g., "Co-authorship").
     * @param linkStrength            The label for link strength (e.g., "Collaboration count").
     * @return A URL to the VOSviewer visualization or an empty string on failure.
     */
    public String exportAndReturnLinkForConversionToVV(String jobId, boolean shareVVPublicly, String item, String link, String linkStrength) {
        Supplier<String> microserviceCall = () -> microserviceHttpClient.api().get("/api/convert2vv")
                .addQueryParameter("item", item)
                .addQueryParameter("items", "")
                .addQueryParameter("link", link)
                .addQueryParameter("links", "")
                .addQueryParameter("linkStrength", linkStrength)
                .addQueryParameter("totalLinkStrength", "Total number of co-occurrences")
                .addQueryParameter("descriptionData", "Made with nocodefunctions.com")
                .addQueryParameter("jobId", jobId)
                .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofString())
                .join();
        return handleVosViewerExport(shareVVPublicly, microserviceCall, "GET call for generic conversion");
    }

    private String handleVosViewerExport(boolean shareVVPublicly, Supplier<String> microserviceCall, String errorContext) {
        try {
            String graphAsJsonVosViewer = microserviceCall.get();
            return finishOpsFromGraphAsJson(graphAsJsonVosViewer, shareVVPublicly);
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            LOG.log(Level.SEVERE, "Exception during synchronous VOSviewer conversion " + errorContext, cause);
            String errorMessage = "Error converting to VOSviewer: " + cause.getMessage();
            if (cause instanceof MicroserviceCallException msce) {
                errorMessage = "Error converting to VOSviewer: Status " + msce.getStatusCode() + ", " + msce.getErrorBody();
            }
            LOG.log(Level.SEVERE, errorMessage);
            return "";
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Unexpected exception in " + errorContext, e);
            return "";
        }
    }

    /**
     * Writes the VOSviewer JSON to a file and constructs the final URL.
     * @param graphAsJsonVosViewer
     * @param shareVVPublicly
     * @return 
     */
    public String finishOpsFromGraphAsJson(String graphAsJsonVosViewer, boolean shareVVPublicly) {
        if (graphAsJsonVosViewer == null || graphAsJsonVosViewer.isBlank()) {
            LOG.warning("Received empty or null JSON from VOSviewer conversion service.");
            return "";
        }

        Path userGeneratedVosviewerDirectoryFullPath = applicationProperties.getUserGeneratedVosviewerDirectoryFullPath(shareVVPublicly);
        Path relativePathFromProjectRootToVosviewerFolder = applicationProperties.getRelativePathFromProjectRootToVosviewerFolder();
        Path vosviewerRootFullPath = applicationProperties.getVosviewerRootFullPath();

        long nextLong = ThreadLocalRandom.current().nextLong();
        String vosviewerJsonFileName = "vosviewer_" + nextLong + ".json";
        Path fullPathFileToWrite = userGeneratedVosviewerDirectoryFullPath.resolve(vosviewerJsonFileName);

        try {
            Files.createDirectories(fullPathFileToWrite.getParent());
            Files.writeString(fullPathFileToWrite, graphAsJsonVosViewer, StandardCharsets.UTF_8);
            LOG.log(Level.INFO, "Wrote VOSviewer JSON file to: {0}", fullPathFileToWrite);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Error when writing user generated VOSviewer file to disk", ex);
            return "";
        }

        if (RemoteLocal.isLocal()) {
            return fullPathFileToWrite.toString();
        }

        String urlWithoutParamValue = RemoteLocal.getDomain() + "/" + relativePathFromProjectRootToVosviewerFolder.toString().replace("\\", "/") + "/index.html?json=";
        Path relativePathToVosviewerFile = vosviewerRootFullPath.relativize(fullPathFileToWrite);
        String fullUrl = urlWithoutParamValue + relativePathToVosviewerFile.toString().replace("\\", "/");
        LOG.log(Level.INFO, "Generated VOSviewer public URL: {0}", fullUrl);
        return fullUrl;
    }
}