package net.clementlevallois.nocodeapp.web.front.io;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import org.primefaces.model.file.UploadedFile;

/**
 * A shared, application-scoped service for handling the initial data
 * preparation steps for all workflows. This service centralizes the logic for
 * processing user-provided data sources (file uploads, web pages, websites) by
 * calling specialized backend microservices. It ensures a consistent data
 * import mechanism across the application.
 */
@ApplicationScoped
@Named
public class ImportersService {

    private static final Logger LOG = Logger.getLogger(ImportersService.class.getName());

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    MicroserviceHttpClient microserviceHttpClient;

    /**
     * Represents the outcome of a data preparation operation, which can either
     * be a success or a failure with a corresponding message.
     */
    public sealed interface PreparationResult {

        record Success(String msg) implements PreparationResult {

        }

        record Failure(String errorMessage) implements PreparationResult {

        }
    }

    /**
     * Represents the different types of supported file extensions. This is a
     * sealed interface to allow for exhaustive pattern matching.
     */
    public sealed interface FileExtension {

        record Pdf() implements FileExtension {
        }

        record Txt() implements FileExtension {
        }

        record Json() implements FileExtension {
        }

        record Csv() implements FileExtension {
        }

        record Excel() implements FileExtension {
        }

        record Unsupported(String extension) implements FileExtension {

        }
    }

    /**
     * Processes a list of uploaded files. For each file, it determines the
     * type, saves it to a temporary location, and then calls the appropriate
     * import microservice based on the file type and the calling function's
     * context.
     *
     * @param files The list of files uploaded by the user.
     * @param jobId The unique identifier for the current job.
     * @param callingFunction The enum value identifying which function is
     * requesting the import.
     * @return A {@link PreparationResult} indicating success or failure.
     */
    public PreparationResult handleFileUpload(List<UploadedFile> files, String jobId, Globals.Names callingFunction) {
        for (UploadedFile file : files) {
            try {
                String fileUniqueId = Globals.UPLOADED_FILE_PREFIX + UUID.randomUUID().toString().substring(0, 5);

                byte[] uploadedFileBytes = file.getInputStream().readAllBytes();

                FileExtension fileExtension = getFileExtension(file.getFileName());

                Path pathToFile = applicationProperties.getTempFolderFullPath().resolve(jobId).resolve(fileUniqueId);
                Files.write(pathToFile, uploadedFileBytes);

                var importClient = microserviceHttpClient.importService();

                // The requestBuilder is now determined by the file type AND the calling function
                var requestBuilder = switch (fileExtension) {
                    case FileExtension.Pdf() ->
                        importClient.get("pdf/simpleLines");
                    case FileExtension.Excel() ->
                        importClient.get("xlsx/sheetModel");
                    case FileExtension.Txt() ->
                        importClient.get("txt/simpleLines");
                    case FileExtension.Json() ->
                        importClient.get("json/appendToPersistedJsonArray");
                    case FileExtension.Csv() ->
                        importClient.get("csv/toSheets");
                    case FileExtension.Unsupported(String ext) ->
                        null;
                };

                if (requestBuilder == null) {
                    return new PreparationResult.Failure("Unsupported file type: " + ((FileExtension.Unsupported) fileExtension).extension());
                }

                requestBuilder.addQueryParameter("jobId", jobId)
                        .addQueryParameter("fileUniqueId", fileUniqueId)
                        .addQueryParameter("fileName", file.getFileName());

                // We use .join() here to wait for the import to complete before proceeding.
                // This is a design choice: if imports could run fully in the background
                // without the user waiting, we would handle the CompletableFuture differently.
                requestBuilder.sendAsync(HttpResponse.BodyHandlers.ofString()).join();

            } catch (IOException ex) {
                LOG.log(Level.SEVERE, "Error processing file upload for job " + jobId, ex);
                return new PreparationResult.Failure("Error during file processing: " + ex.getMessage());
            }
        }
        return new PreparationResult.Success("File upload handled for job " + jobId);
    }

    /**
     * Scrapes the text content from a single web page by calling a
     * microservice.
     *
     * @param url The URL of the web page to scrape.
     * @param jobId The unique identifier for the current job.
     * @return A {@link PreparationResult} indicating success or failure.
     */
    public PreparationResult parseWebPage(String url, String jobId) {
        var response = microserviceHttpClient.importService()
                .get("html/getRawTextFromLinks")
                .addQueryParameter("jobId", jobId)
                .addQueryParameter("url", url)
                .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofString())
                .join();

        if (response == null || response.isBlank()) {
            return new PreparationResult.Failure("Could not retrieve content from the web page.");
        }
        return new PreparationResult.Success("Web page content retrieved for job " + jobId);
    }

    /**
     * Crawls a website starting from a root URL, extracts text from the found
     * pages, and saves it. It first fetches a list of all crawlable links and
     * then calls a microservice for each link to extract its text content.
     *
     * @param rootUrl The starting URL for the crawl.
     * @param maxUrls The maximum number of pages to crawl.
     * @param exclusionTerms A list of terms to filter out URLs.
     * @param jobId The unique identifier for the current job.
     * @return A {@link PreparationResult} indicating success or failure.
     */
    public PreparationResult crawlWebSite(String rootUrl, int maxUrls, String exclusionTerms, String jobId) {
        String exclusionTermsParam = String.join(",", exclusionTerms);
        var response = microserviceHttpClient.importService()
                .get("html/getPagesContainedInWebsite")
                .addQueryParameter("jobId", jobId)
                .addQueryParameter("url", rootUrl)
                .addQueryParameter("maxUrls", String.valueOf(maxUrls))
                .addQueryParameter("exclusionTerms", exclusionTermsParam)
                .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofString())
                .join();

        try (JsonReader reader = Json.createReader(new StringReader(response))) {
            JsonArray linksArray = reader.readArray();
            if (linksArray.isEmpty()) {
                return new PreparationResult.Failure("Could not find any links to crawl on the website.");
            }

            for (JsonValue jsonValue : linksArray) {
                JsonObject jo = jsonValue.asJsonObject();
                String link = jo.getString("linkHref");

                microserviceHttpClient.importService()
                        .get("html/getRawTextFromLinks")
                        .addQueryParameter("jobId", jobId)
                        .addQueryParameter("url", link)
                        .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofString())
                        .join();
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error processing crawled website links for job " + jobId, e);
            return new PreparationResult.Failure("Error processing crawled website: " + e.getMessage());
        }

        return new PreparationResult.Success("Website crawl completed for job " + jobId);
    }

    /**
     * Determines the file extension and returns the corresponding FileExtension
     * record.
     *
     * @param fileName The name of the file.
     * @return A FileExtension record representing the detected extension.
     */
    public FileExtension getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf('.') == -1) {
            return new FileExtension.Unsupported("");
        }
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        return switch (extension) {
            case "pdf" ->
                new FileExtension.Pdf();
            case "txt" ->
                new FileExtension.Txt();
            case "json" ->
                new FileExtension.Json();
            case "xlsx" ->
                new FileExtension.Excel();
            case "csv" ->
                new FileExtension.Csv();
            default ->
                new FileExtension.Unsupported(extension);
        };
    }

    public static synchronized void concurrentWriting(Path path, String string) {
        File file = path.toFile();
        if (string == null) {
            System.out.println("string is empty in concurrentWriting method of IO");
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw"); FileChannel fileChannel = raf.getChannel()) {
            try (FileLock lock = fileChannel.lock()) {
                byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
                raf.seek(raf.length());
                raf.write(bytes);
            } catch (Exception e) {
                System.out.println("problem with lock");
            }
        } catch (IOException e) {
            System.out.println("error in the concurrent write to file in one import API endpoint");
        }
    }

    public static synchronized void concurrentWriting(Path path, byte[] bytes) {
        File file = path.toFile();
        if (bytes == null) {
            System.out.println("byte array is null in concurrentWriting method of web front import service");
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw"); FileChannel fileChannel = raf.getChannel()) {
            try (FileLock lock = fileChannel.lock()) {
                raf.seek(raf.length());
                raf.write(bytes);
            } catch (Exception e) {
                System.out.println("problem with lock");
            }
        } catch (IOException e) {
            System.out.println("error in the concurrent write to file in one import API endpoint");
        }
    }

}
