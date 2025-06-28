/*
 * Copyright Clement Levallois 2021-2025. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.exportdata;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
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
import org.primefaces.model.file.UploadedFile;

/**
 * An application-scoped bean for handling data exports to VOSviewer.
 *
 * This bean collaborates with MicroserviceHttpClient to convert data and then
 * generates a link for the VOSviewer visualization.
 */
@ApplicationScoped
public class ExportToVosViewer {

    private static final Logger LOG = Logger.getLogger(ExportToVosViewer.class.getName());

    @Inject
    private MicroserviceHttpClient microserviceHttpClient;

    @Inject
    private ApplicationPropertiesBean applicationProperties;

    /**
     * Exports a GEXF graph to VOSviewer by providing its persistence ID.
     *
     * @param dataPersistenceUniqueId The unique ID of the persisted GEXF data.
     * @param shareVVPublicly         If true, the generated VOSviewer link will be in a publicly accessible folder.
     * @return A URL to the VOSviewer visualization or an empty string on failure.
     */
    public String exportAndReturnLinkFromGexfWithGet(String dataPersistenceUniqueId, boolean shareVVPublicly) {
        Supplier<String> microserviceCall = () -> microserviceHttpClient.api().get("/api/convert2vv")
                .addQueryParameter("item", "Term")
                .addQueryParameter("items", "Terms")
                .addQueryParameter("link", "Co-occurrence link")
                .addQueryParameter("links", "Co-occurrence links")
                .addQueryParameter("linkStrength", "Number of co-occurrences")
                .addQueryParameter("totalLinkStrength", "Total number of co-occurrences")
                .addQueryParameter("descriptionData", "Made with nocodefunctions.com")
                .addQueryParameter("dataPersistenceUniqueId", dataPersistenceUniqueId)
                .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofString())
                .join();
        return handleVosViewerExport(shareVVPublicly, microserviceCall, "GET call from GEXF ID");
    }

    /**
     * Converts a generic data file to a VOSviewer visualization with custom labels.
     *
     * @param dataPersistenceUniqueId The unique ID of the persisted data.
     * @param shareVVPublicly         If true, the link will be publicly accessible.
     * @param item                    The label for a single item (e.g., "Author").
     * @param link                    The label for a link (e.g., "Co-authorship").
     * @param linkStrength            The label for link strength (e.g., "Collaboration count").
     * @return A URL to the VOSviewer visualization or an empty string on failure.
     */
    public String exportAndReturnLinkForConversionToVV(String dataPersistenceUniqueId, boolean shareVVPublicly, String item, String link, String linkStrength) {
        Supplier<String> microserviceCall = () -> microserviceHttpClient.api().get("/api/convert2vv")
                .addQueryParameter("item", item)
                .addQueryParameter("items", "")
                .addQueryParameter("link", link)
                .addQueryParameter("links", "")
                .addQueryParameter("linkStrength", linkStrength)
                .addQueryParameter("totalLinkStrength", "Total number of co-occurrences")
                .addQueryParameter("descriptionData", "Made with nocodefunctions.com")
                .addQueryParameter("dataPersistenceUniqueId", dataPersistenceUniqueId)
                .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofString())
                .join();
        return handleVosViewerExport(shareVVPublicly, microserviceCall, "GET call for generic conversion");
    }

    /**
     * Exports a GEXF graph provided as a string to VOSviewer.
     *
     * @param gexf            The GEXF graph content as a String.
     * @param shareVVPublicly If true, the link will be publicly accessible.
     * @return A URL to the VOSviewer visualization or an empty string on failure.
     */
    public String exportAndReturnLinkFromGexfWithPost(String gexf, boolean shareVVPublicly) {
        if (gexf == null) {
            LOG.warning("GEXF string was null for VOSviewer export.");
            return "";
        }
        Supplier<String> microserviceCall = () -> microserviceHttpClient.api().post("/api/convert2vv")
                .withByteArrayPayload(gexf.getBytes(StandardCharsets.UTF_8))
                .addQueryParameter("item", "Term")
                .addQueryParameter("items", "Terms")
                .addQueryParameter("link", "Co-occurrence link")
                .addQueryParameter("links", "Co-occurrence links")
                .addQueryParameter("linkStrength", "Number of co-occurrences")
                .addQueryParameter("totalLinkStrength", "Total number of co-occurrences")
                .addQueryParameter("descriptionData", "Made with nocodefunctions.com")
                .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofString())
                .join();
        return handleVosViewerExport(shareVVPublicly, microserviceCall, "POST call with GEXF string");
    }

    /**
     * Exports a user-uploaded file to VOSviewer.
     *
     * @param uploadedFile    The file uploaded by the user.
     * @param shareVVPublicly If true, the link will be publicly accessible.
     * @param item            The label for a single item.
     * @param link            The label for a link.
     * @param linkStrength    The label for link strength.
     * @return A URL to the VOSviewer visualization or an empty string on failure.
     */
    public String exportAndReturnLinkFromUploadedFile(UploadedFile uploadedFile, boolean shareVVPublicly, String item, String link, String linkStrength) {
        if (uploadedFile == null || uploadedFile.getFileName() == null || uploadedFile.getFileName().isBlank()) {
            LOG.warning("Uploaded file was null or empty for VOSviewer export.");
            return "";
        }
        try {
            byte[] fileBytes;
            try (InputStream is = uploadedFile.getInputStream()) {
                fileBytes = is.readAllBytes();
            }
            Supplier<String> microserviceCall = () -> microserviceHttpClient.api().post("/api/convert2vv")
                    .withByteArrayPayload(fileBytes)
                    .addQueryParameter("item", item)
                    .addQueryParameter("items", "")
                    .addQueryParameter("link", link)
                    .addQueryParameter("links", "")
                    .addQueryParameter("linkStrength", linkStrength)
                    .addQueryParameter("descriptionData", "")
                    .addQueryParameter("totalLinkStrength", "")
                    .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofString())
                    .join();
            return handleVosViewerExport(shareVVPublicly, microserviceCall, "POST call with UploadedFile");
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Error reading uploaded file for VOSviewer export", ex);
            return "";
        }
    }

    /**
     * Private helper to encapsulate the microservice call and file writing logic.
     */
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