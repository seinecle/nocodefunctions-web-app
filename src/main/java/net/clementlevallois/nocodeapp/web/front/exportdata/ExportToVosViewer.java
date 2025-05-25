/*
 * Copyright Clement Levallois 2021-2024. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.exportdata;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import org.primefaces.model.file.UploadedFile;

import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient.MicroserviceCallException;

import java.util.concurrent.CompletionException;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;


public class ExportToVosViewer {

    private static final Logger LOG = Logger.getLogger(ExportToVosViewer.class.getName());

    public static String exportAndReturnLinkFromGexfWithGet(MicroserviceHttpClient client, String dataPersistenceUniqueId, boolean shareVVPublicly, ApplicationPropertiesBean applicationProperties) {
        Path userGeneratedVosviewerDirectoryFullPath = applicationProperties.getUserGeneratedVosviewerDirectoryFullPath(shareVVPublicly);
        Path relativePathFromProjectRootToVosviewerFolder = applicationProperties.getRelativePathFromProjectRootToVosviewerFolder();
        Path vosviewerRootFullPath = applicationProperties.getVosviewerRootFullPath();

        try {
            String graphAsJsonVosViewer = client.api().get("/api/convert2vv")
                .addQueryParameter("item", "Term")
                .addQueryParameter("items", "Terms")
                .addQueryParameter("link", "Co-occurrence link")
                .addQueryParameter("links", "Co-occurrence links")
                .addQueryParameter("linkStrength", "Number of co-occurrences")
                .addQueryParameter("totalLinkStrength", "Total number of co-occurrences")
                .addQueryParameter("descriptionData", "Made with nocodefunctions.com")
                .addQueryParameter("dataPersistenceUniqueId", dataPersistenceUniqueId)
                .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofString()) // Send async, expect String body
                .join(); // Block and wait for the result or throw CompletionException

            return finishOpsFromGraphAsJson(graphAsJsonVosViewer, userGeneratedVosviewerDirectoryFullPath, relativePathFromProjectRootToVosviewerFolder, vosviewerRootFullPath);

        } catch (CompletionException e) {
            // Handle exceptions from the async call (network, server error, etc.)
            Throwable cause = e.getCause();
            LOG.log(Level.SEVERE, "Exception during synchronous VOSviewer conversion GET call", cause);
             String errorMessage = "Error converting to VOSviewer: " + cause.getMessage();
             if (cause instanceof MicroserviceCallException) {
                 MicroserviceCallException msce = (MicroserviceCallException) cause;
                 errorMessage = "Error converting to VOSviewer: Status " + msce.getStatusCode() + ", " + msce.getErrorBody();
             }
             // Log the error and return an empty string as per original synchronous behavior
             LOG.log(Level.SEVERE, errorMessage);
            return "";
        } catch (Exception e) {
             // Catch any other unexpected exceptions
             LOG.log(Level.SEVERE, "Unexpected exception in exportAndReturnLinkFromGexf (ID)", e);
             return "";
        }
    }

     // Refactored method to use MicroserviceHttpClient (GET request with ID and custom params) - Now Synchronous
    public static String exportAndReturnLinkForConversionToVV(MicroserviceHttpClient client, String dataPersistenceUniqueId, boolean shareVVPublicly, ApplicationPropertiesBean applicationProperties, String item, String link, String linkStrength) {
        Path userGeneratedVosviewerDirectoryFullPath = applicationProperties.getUserGeneratedVosviewerDirectoryFullPath(shareVVPublicly);
        Path relativePathFromProjectRootToVosviewerFolder = applicationProperties.getRelativePathFromProjectRootToVosviewerFolder();
        Path vosviewerRootFullPath = applicationProperties.getVosviewerRootFullPath();

        try {
            // Use the MicroserviceHttpClient for the GET request and block using .join()
            String graphAsJsonVosViewer = client.api().get("/api/convert2vv")
                .addQueryParameter("item", item)
                .addQueryParameter("items", "") // Empty as per original
                .addQueryParameter("link", link)
                .addQueryParameter("links", "") // Empty as per original
                .addQueryParameter("linkStrength", linkStrength)
                .addQueryParameter("totalLinkStrength", "Total number of co-occurrences") // Fixed string as per original
                .addQueryParameter("descriptionData", "Made with nocodefunctions.com") // Fixed string as per original
                .addQueryParameter("dataPersistenceUniqueId", dataPersistenceUniqueId)
                .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofString()) // Expect String response body
                .join(); // Block and wait

            // Process the successful response body
            return finishOpsFromGraphAsJson(graphAsJsonVosViewer, userGeneratedVosviewerDirectoryFullPath, relativePathFromProjectRootToVosviewerFolder, vosviewerRootFullPath);

        } catch (CompletionException e) {
            // Handle exceptions from the async call
            Throwable cause = e.getCause();
            LOG.log(Level.SEVERE, "Exception during synchronous VOSviewer conversion GET call with custom params", cause);
             String errorMessage = "Error converting to VOSviewer: " + cause.getMessage();
             if (cause instanceof MicroserviceCallException) {
                 MicroserviceCallException msce = (MicroserviceCallException) cause;
                 errorMessage = "Error converting to VOSviewer: Status " + msce.getStatusCode() + ", " + msce.getErrorBody();
             }
             LOG.log(Level.SEVERE, errorMessage);
            return "";
        } catch (Exception e) {
             LOG.log(Level.SEVERE, "Unexpected exception in exportAndReturnLinkForConversionToVV", e);
             return "";
        }
    }


    // Refactored method to use MicroserviceHttpClient (POST request with GEXF string) - Now Synchronous
    public static String exportAndReturnLinkFromGexfWithPost(MicroserviceHttpClient client, String gexf, boolean shareVVPublicly, ApplicationPropertiesBean applicationProperties) {
         if (gexf == null) {
             LOG.warning("GEXF string was null for VOSviewer export.");
             return ""; // Return empty string
         }
        Path userGeneratedVosviewerDirectoryFullPath = applicationProperties.getUserGeneratedVosviewerDirectoryFullPath(shareVVPublicly);
        Path relativePathFromProjectRootToVosviewerFolder = applicationProperties.getRelativePathFromProjectRootToVosviewerFolder();
        Path vosviewerRootFullPath = applicationProperties.getVosviewerRootFullPath();

        try {
            // Use the MicroserviceHttpClient for the POST request and block using .join()
            String graphAsJsonVosViewer = client.api().post("/api/convert2vv")
                .withByteArrayPayload(gexf.getBytes(StandardCharsets.UTF_8)) // Send GEXF string as byte array payload
                // Add parameters as query parameters
                .addQueryParameter("item", "Term") // Fixed string as per original
                .addQueryParameter("items", "Terms") // Fixed string as per original
                .addQueryParameter("link", "Co-occurrence link") // Fixed string as per original
                .addQueryParameter("links", "Co-occurrence links") // Fixed string as per original
                .addQueryParameter("linkStrength", "Number of co-occurrences") // Fixed string as per original
                .addQueryParameter("totalLinkStrength", "Total number of co-occurrences") // Fixed string as per original
                .addQueryParameter("descriptionData", "Made with nocodefunctions.com") // Fixed string as per original
                // Note: original method didn't send dataPersistenceUniqueId in POST from GEXF string
                .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofString()) // Expect String response body
                .join(); // Block and wait

            return finishOpsFromGraphAsJson(graphAsJsonVosViewer, userGeneratedVosviewerDirectoryFullPath, relativePathFromProjectRootToVosviewerFolder, vosviewerRootFullPath);

        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            LOG.log(Level.SEVERE, "Exception during synchronous VOSviewer conversion POST call with GEXF string", cause);
             String errorMessage = "Error converting to VOSviewer: " + cause.getMessage();
             if (cause instanceof MicroserviceCallException msce) {
                 errorMessage = "Error converting to VOSviewer: Status " + msce.getStatusCode() + ", " + msce.getErrorBody();
             }
             LOG.log(Level.SEVERE, errorMessage);
            return "";
        } catch (Exception e) {
             LOG.log(Level.SEVERE, "Unexpected exception in exportAndReturnLinkFromGexf (String)", e);
             return "";
        }
    }

    // Refactored method to use MicroserviceHttpClient (POST request with UploadedFile) - Now Synchronous
    public static String exportAndReturnLinkFromUploadedFile(MicroserviceHttpClient client, UploadedFile uploadedFile, boolean shareVVPublicly, ApplicationPropertiesBean applicationProperties, String item, String link, String linkStrength) {
        if (uploadedFile == null) {
            LOG.warning("Uploaded file was null for VOSviewer export.");
            return ""; // Return empty string
        }
        Path userGeneratedVosviewerDirectoryFullPath = applicationProperties.getUserGeneratedVosviewerDirectoryFullPath(shareVVPublicly);
        Path relativePathFromProjectRootToVosviewerFolder = applicationProperties.getRelativePathFromProjectRootToVosviewerFolder();
        Path vosviewerRootFullPath = applicationProperties.getVosviewerRootFullPath();

        try {
            byte[] fileBytes;
            try (InputStream is = uploadedFile.getInputStream()) {
                fileBytes = is.readAllBytes();
            }

            // Use the MicroserviceHttpClient for the POST request and block using .join()
            String graphAsJsonVosViewer = client.api().post("/api/convert2vv")
                .withByteArrayPayload(fileBytes) // Send file bytes as payload
                // Add parameters as query parameters
                .addQueryParameter("item", item)
                .addQueryParameter("items", "") // Empty as per original
                .addQueryParameter("link", link)
                .addQueryParameter("links", "") // Empty as per original
                .addQueryParameter("linkStrength", linkStrength)
                .addQueryParameter("descriptionData", "") // Empty as per original
                .addQueryParameter("totalLinkStrength", "") // Empty as per original
                .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofString()) // Expect String response body
                .join(); // Block and wait

            // Process the successful response body
            return finishOpsFromGraphAsJson(graphAsJsonVosViewer, userGeneratedVosviewerDirectoryFullPath, relativePathFromProjectRootToVosviewerFolder, vosviewerRootFullPath);

        } catch (IOException ex) {
             LOG.log(Level.SEVERE, "Error reading uploaded file for VOSviewer export", ex);
             return ""; // Return empty string on error
        } catch (CompletionException e) {
            // Handle exceptions from the async call
            Throwable cause = e.getCause();
            LOG.log(Level.SEVERE, "Exception during synchronous VOSviewer conversion POST call with UploadedFile", cause);
             String errorMessage = "Error converting to VOSviewer: " + cause.getMessage();
             if (cause instanceof MicroserviceCallException) {
                 MicroserviceCallException msce = (MicroserviceCallException) cause;
                 errorMessage = "Error converting to VOSviewer: Status " + msce.getStatusCode() + ", " + msce.getErrorBody();
             }
             LOG.log(Level.SEVERE, errorMessage);
            return "";
        } catch (Exception e) {
             LOG.log(Level.SEVERE, "Unexpected exception in exportAndReturnLinkFromUploadedFile", e);
             return "";
        }
    }

    public static String finishOpsFromGraphAsJson(String graphAsJsonVosViewer, Path userGeneratedVosviewerDirectoryFullPath, Path relativePathFromProjectRootToVosviewerFolder, Path vosviewerRootFullPath) {
        if (graphAsJsonVosViewer == null || graphAsJsonVosViewer.isBlank()) {
             LOG.warning("Received empty or null JSON from VOSviewer conversion service.");
             return ""; // Cannot proceed without JSON data
        }

        long nextLong = ThreadLocalRandom.current().nextLong();
        String vosviewerJsonFileName = "vosviewer_" + String.valueOf(nextLong) + ".json";

        Path fullPathFileToWrite = userGeneratedVosviewerDirectoryFullPath.resolve(Path.of(vosviewerJsonFileName));
        try {
            // Ensure parent directory exists
            Files.createDirectories(fullPathFileToWrite.getParent());
            Files.writeString(fullPathFileToWrite, graphAsJsonVosViewer, StandardCharsets.UTF_8);
            LOG.log(Level.INFO, "Wrote VOSviewer JSON file to: {0}", fullPathFileToWrite);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Error when writing user generated VV file to disk", ex);
            return ""; // Indicate failure
        }

        if (RemoteLocal.isLocal()) {
            // For local development, return the file path
            return fullPathFileToWrite.toString();
        }

        String urlWithoutParamValue = RemoteLocal.getDomain() + "/" + relativePathFromProjectRootToVosviewerFolder.toString().replace("\\", "/") + "/index.html?json="; // Use forward slashes
        Path relativePathToVosviewerFile = vosviewerRootFullPath.relativize(fullPathFileToWrite);
        String fullUrl = urlWithoutParamValue + relativePathToVosviewerFile.toString().replace("\\", "/"); // Use forward slashes
        LOG.log(Level.INFO, "Generated VOSviewer public URL: {0}", fullUrl);
        return fullUrl;
    }
}
