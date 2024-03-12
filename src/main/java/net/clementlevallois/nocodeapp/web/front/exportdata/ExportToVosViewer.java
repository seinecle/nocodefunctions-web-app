/*
 * Copyright Clement Levallois 2021-2023. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.exportdata;

import io.mikael.urlbuilder.UrlBuilder;
import jakarta.json.Json;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import org.primefaces.model.file.UploadedFile;

/**
 *
 * @author LEVALLOIS
 */
public class ExportToVosViewer {

    public static String exportAndReturnLinkFromGexf(String dataPersistenceUniqueId, String apiPort, boolean shareVVPublicly, ApplicationPropertiesBean applicationProperties) {
        Path userGeneratedVosviewerDirectoryFullPath = applicationProperties.getUserGeneratedVosviewerDirectoryFullPath(shareVVPublicly);
        Path relativePathFromProjectRootToVosviewerFolder = applicationProperties.getRelativePathFromProjectRootToVosviewerFolder();
        Path vosviewerRootFullPath = applicationProperties.getVosviewerRootFullPath();

        try {
            HttpRequest request;
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMinutes(10)).build();

            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(Integer.valueOf(apiPort))
                    .withHost("localhost")
                    .withPath("api/convert2vv")
                    .addParameter("item", "Term")
                    .addParameter("items", "Terms")
                    .addParameter("link", "Co-occurrence link")
                    .addParameter("links", "Co-occurrence links")
                    .addParameter("linkStrength", "Number of co-occurrences")
                    .addParameter("totalLinkStrength", "Total number of co-occurrences")
                    .addParameter("descriptionData", "Made with nocodefunctions.com")
                    .addParameter("dataPersistenceUniqueId", dataPersistenceUniqueId)
                    .toUri();

            request = HttpRequest.newBuilder()
                    .GET()
                    .uri(uri)
                    .build();
            HttpResponse<byte[]> resp = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = resp.body();
            String graphAsJsonVosViewer = new String(body, StandardCharsets.UTF_8);
            String url = finishOpsFromGraphAsJson(graphAsJsonVosViewer, userGeneratedVosviewerDirectoryFullPath, relativePathFromProjectRootToVosviewerFolder, vosviewerRootFullPath);
            return url;
        } catch (InterruptedException | IOException ex) {
            Logger.getLogger(ExportToVosViewer.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "";
    }

    public static String exportAndReturnLinkForConversionToVV(String dataPersistenceUniqueId, String apiPort, boolean shareVVPublicly, ApplicationPropertiesBean applicationProperties, String item, String link, String linkStrength) {
        Path userGeneratedVosviewerDirectoryFullPath = applicationProperties.getUserGeneratedVosviewerDirectoryFullPath(shareVVPublicly);
        Path relativePathFromProjectRootToVosviewerFolder = applicationProperties.getRelativePathFromProjectRootToVosviewerFolder();
        Path vosviewerRootFullPath = applicationProperties.getVosviewerRootFullPath();
        try {
            HttpRequest request;
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMinutes(10)).build();

            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(Integer.valueOf(apiPort))
                    .withHost("localhost")
                    .withPath("api/convert2vv")
                    .addParameter("item", item)
                    .addParameter("items", "")
                    .addParameter("link", link)
                    .addParameter("links", "")
                    .addParameter("linkStrength", linkStrength)
                    .addParameter("totalLinkStrength", "Total number of co-occurrences")
                    .addParameter("descriptionData", "Made with nocodefunctions.com")
                    .addParameter("dataPersistenceUniqueId", dataPersistenceUniqueId)
                    .toUri();

            request = HttpRequest.newBuilder()
                    .GET()
                    .uri(uri)
                    .build();
            HttpResponse<byte[]> resp = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 200) {
                byte[] body = resp.body();
                String graphAsJsonVosViewer = new String(body, StandardCharsets.UTF_8);
                String url = finishOpsFromGraphAsJson(graphAsJsonVosViewer, userGeneratedVosviewerDirectoryFullPath, relativePathFromProjectRootToVosviewerFolder, vosviewerRootFullPath);
                return url;
            } else {
                byte[] body = resp.body();
                String error = new String(body, StandardCharsets.UTF_8);
                System.out.println("error with gexf conversion to vv, msg from API is: " + error);

            }
        } catch (InterruptedException | IOException ex) {
            Logger.getLogger(ExportToVosViewer.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "";
    }

    public static String exportAndReturnLinkFromGexf(String gexf, String apiPort, Path userGeneratedVosviewerDirectoryFullPath, Path relativePathFromProjectRootToVosviewerFolder, Path vosviewerRootFullPath) {
        try {
            if (gexf == null) {
                System.out.println("gexf was null so exportAndReturnLinkFromGexf method exited");
                return "";
            }
            HttpRequest request;
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(gexf.getBytes(StandardCharsets.UTF_8));

            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(Integer.valueOf(apiPort))
                    .withHost("localhost")
                    .withPath("api/convert2vv")
                    .addParameter("item", "Term")
                    .addParameter("items", "Terms")
                    .addParameter("link", "Co-occurrence link")
                    .addParameter("links", "Co-occurrence links")
                    .addParameter("linkStrength", "Number of co-occurrences")
                    .addParameter("totalLinkStrength", "Total number of co-occurrences")
                    .addParameter("descriptionData", "Made with nocodefunctions.com")
                    .toUri();

            request = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .uri(uri)
                    .build();
            HttpResponse<byte[]> resp = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = resp.body();
            String graphAsJsonVosViewer = new String(body, StandardCharsets.UTF_8);
            String url = finishOpsFromGraphAsJson(graphAsJsonVosViewer, userGeneratedVosviewerDirectoryFullPath, relativePathFromProjectRootToVosviewerFolder, vosviewerRootFullPath);
            return url;
        } catch (InterruptedException | IOException ex) {
            Logger.getLogger(ExportToVosViewer.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "";
    }

    public static String exportAndReturnLinkFromUploadedFile(UploadedFile uploadedFile, String apiPort, String item, String link, String linkStrength, Path userGeneratedVosviewerDirectoryFullPath, Path relativePathFromProjectRootToVosviewerFolder, Path vosviewerRootFullPath) {
        try {
            if (uploadedFile == null) {
                System.out.println("no file found for conversion to vv");
                return "";
            }
            String graphAsJsonVosViewer;
            HttpRequest request;
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMinutes(10)).build();

            InputStream is = uploadedFile.getInputStream();

            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(is.readAllBytes());
            is.close();

            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(Integer.valueOf(apiPort))
                    .withHost("localhost")
                    .withPath("api/convert2vv")
                    .addParameter("item", item)
                    .addParameter("items", "")
                    .addParameter("link", link)
                    .addParameter("links", "")
                    .addParameter("linkStrength", linkStrength)
                    .addParameter("descriptionData", "")
                    .addParameter("totalLinkStrength", "")
                    .toUri();

            request = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .uri(uri)
                    .build();

            HttpResponse<byte[]> resp = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 200) {
                byte[] body = resp.body();
                graphAsJsonVosViewer = new String(body, StandardCharsets.UTF_8);
            } else {
                graphAsJsonVosViewer = null;
            }
            String url = finishOpsFromGraphAsJson(graphAsJsonVosViewer, userGeneratedVosviewerDirectoryFullPath, relativePathFromProjectRootToVosviewerFolder, vosviewerRootFullPath);
            return url;

        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(ExportToVosViewer.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "";
    }

    private static String finishOpsFromGraphAsJson(String graphAsJsonVosViewer, Path userGeneratedVosviewerDirectoryFullPath, Path relativePathFromProjectRootToVosviewerFolder, Path vosviewerRootFullPath) {
        graphAsJsonVosViewer = Json.encodePointer(graphAsJsonVosViewer);
        long nextLong = ThreadLocalRandom.current().nextLong();
        String vosviewerJsonFileName = "vosviewer_" + String.valueOf(nextLong) + ".json";

        Path fullPathFileToWrite = userGeneratedVosviewerDirectoryFullPath.resolve(Path.of(vosviewerJsonFileName));
        try {
            Files.writeString(fullPathFileToWrite, graphAsJsonVosViewer, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            System.out.println("error when writing user generated vv file to disk");
            System.out.println("ex: " + ex);
        }
        if (RemoteLocal.isLocal()) {
            return fullPathFileToWrite.toString();
        }

        String urlWithoutParamValue = RemoteLocal.getDomain() + "/" + relativePathFromProjectRootToVosviewerFolder + "/index.html?json=";
        Path relativePathToVosviewerFile = vosviewerRootFullPath.relativize(fullPathFileToWrite);
        String fullUrl = urlWithoutParamValue + relativePathToVosviewerFile;
        return fullUrl;
    }
}
