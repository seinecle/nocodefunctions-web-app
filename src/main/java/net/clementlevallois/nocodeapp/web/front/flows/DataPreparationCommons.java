package net.clementlevallois.nocodeapp.web.front.flows;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.io.StringReader;
import java.net.http.HttpResponse;
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
public class DataPreparationCommons {

    private static final Logger LOG = Logger.getLogger(DataPreparationCommons.class.getName());

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

    public DataPreparationCommons.PreparationResult prepare(DataSource dataSource, String jobId) {
        try {
            Path jobDirectory = applicationProperties.getTempFolderFullPath().resolve(jobId);
            Files.createDirectories(jobDirectory);

            return switch (dataSource) {
                case DataSource.FileUpload(List<UploadedFile> files) ->
                    handleFileUpload(files, jobId);
                case DataSource.WebPage(String url) ->
                    handleWebPage(url, jobId);
                case DataSource.WebSite(String rootUrl, int maxUrls, var exclusionTerms) ->
                    handleWebSite(rootUrl, maxUrls, exclusionTerms, jobId);
            };
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Data preparation failed for job " + jobId, e);
            return new DataPreparationCommons.PreparationResult.Failure("An unexpected error occurred: " + e.getMessage());
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

        record Json(String jsonKey) implements FileExtension {

        }

        record Csv() implements FileExtension {
        }

        record Unsupported(String extension) implements FileExtension {

        }
    }

    /**
     * Processes a list of uploaded files. For each file, it determines the
     * type, saves it to a temporary location, and then calls the appropriate
     * import microservice to convert it into plain text.
     *
     * @param files The list of files uploaded by the user.
     * @param jobId The unique identifier for the current job.
     * @return A {@link PreparationResult} indicating success or failure.
     */
    public PreparationResult handleFileUpload(List<UploadedFile> files, String jobId) {
        for (UploadedFile file : files) {
            try {
                String fileUniqueId = Globals.UPLOADED_FILE_PREFIX + UUID.randomUUID().toString().substring(0, 5);
                Path pathToFile = applicationProperties.getTempFolderFullPath().resolve(jobId).resolve(fileUniqueId);
                Files.write(pathToFile, file.getInputStream().readAllBytes());

                FileExtension fileExtension = getFileExtension(file.getFileName());
                var importClient = microserviceHttpClient.importService();

                var requestBuilder = switch (fileExtension) {
                    case FileExtension.Pdf() ->
                        importClient.get("import/pdf/simpleLines");
                    case FileExtension.Txt() ->
                        importClient.get("import/txt/simpleLines");
                    case FileExtension.Json(String jsonKey) ->
                        importClient.get("import/json/simpleLines").addQueryParameter("jsonKey", jsonKey);
                    case FileExtension.Csv() ->
                        importClient.get("import/csv/simpleLines");
                    case FileExtension.Unsupported(String ext) ->
                        null;
                };

                if (requestBuilder == null) {
                    return new PreparationResult.Failure("Unsupported file type: " + ((FileExtension.Unsupported) fileExtension).extension());
                }

                requestBuilder.addQueryParameter("jobId", jobId)
                        .addQueryParameter("fileUniqueId", fileUniqueId)
                        .addQueryParameter("fileName", file.getFileName());

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
    public PreparationResult handleWebPage(String url, String jobId) {
        var response = microserviceHttpClient.importService()
                .get("import/html/getRawTextFromLinks")
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
    public PreparationResult handleWebSite(String rootUrl, int maxUrls, List<String> exclusionTerms, String jobId) {
        String exclusionTermsParam = String.join(",", exclusionTerms);
        var response = microserviceHttpClient.importService()
                .get("import/html/getPagesContainedInWebsite")
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
                        .get("import/html/getRawTextFromLinks")
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
                new FileExtension.Json("text"); // Placeholder key
            case "csv" ->
                new FileExtension.Csv();
            default ->
                new FileExtension.Unsupported(extension);
        };
    }
}
