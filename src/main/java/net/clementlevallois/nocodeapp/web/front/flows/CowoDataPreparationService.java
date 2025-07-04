/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonValue;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
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
import net.clementlevallois.importers.model.UrlLink;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import org.primefaces.model.file.UploadedFile;

@ApplicationScoped
public class CowoDataPreparationService {

    private static final Logger LOG = Logger.getLogger(CowoDataPreparationService.class.getName());

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    MicroserviceHttpClient microserviceHttpClient;

    public sealed interface PreparationResult {
        record Success(String jobId) implements PreparationResult {}
        record Failure(String errorMessage) implements PreparationResult {}
    }

    /**
     * Represents the different types of supported file extensions.
     * This is a sealed interface to allow for exhaustive pattern matching.
     */
    public sealed interface FileExtension {
        record Pdf() implements FileExtension {}
        record Txt() implements FileExtension {}
        record Json(String jsonKey) implements FileExtension {} // jsonKey will be added later
        record Csv() implements FileExtension {}
        record Unsupported(String extension) implements FileExtension {}
    }

    public PreparationResult prepare(DataSource dataSource, String jobId) {
        try {
            Path jobDirectory = applicationProperties.getTempFolderFullPath().resolve(jobId);
            Files.createDirectories(jobDirectory);

            return switch (dataSource) {
                case DataSource.FileUpload(List<UploadedFile> files) -> handleFileUpload(files, jobId);
                case DataSource.WebPage(String url) -> handleWebPage(url, jobId);
                case DataSource.WebSite(String rootUrl, int maxUrls, var exclusionTerms) -> handleWebSite(rootUrl, maxUrls, exclusionTerms, jobId);
            };
        } catch (IOException e) {
            LOG.log(Level.SEVERE, "Data preparation failed for job " + jobId, e);
            return new PreparationResult.Failure("An unexpected error occurred: " + e.getMessage());
        }
    }

    private PreparationResult handleFileUpload(List<UploadedFile> files, String jobId) {
        for (UploadedFile file : files) {
            try {
                String fileUniqueId = Globals.UPLOADED_FILE_PREFIX + UUID.randomUUID().toString().substring(0, 5);
                Path pathToFile = applicationProperties.getTempFolderFullPath().resolve(jobId).resolve(fileUniqueId);
                Files.write(pathToFile, file.getInputStream().readAllBytes());
                
                FileExtension fileExtension = getFileExtension(file.getFileName());
                var importClient = microserviceHttpClient.importService();
                
                var requestBuilder = switch (fileExtension) {
                    case FileExtension.Pdf() -> importClient.get("import/pdf/simpleLines");
                    case FileExtension.Txt() -> importClient.get("import/txt/simpleLines");
                    case FileExtension.Json(String jsonKey) -> importClient.get("import/json/simpleLines").addQueryParameter("jsonKey", jsonKey);
                    case FileExtension.Csv() -> importClient.get("import/csv/simpleLines");
                    case FileExtension.Unsupported(String ext) -> null;
                };
                
                if (requestBuilder == null) {
                    return new PreparationResult.Failure("Unsupported file type: " + ((FileExtension.Unsupported) fileExtension).extension());
                }
                
                requestBuilder.addQueryParameter("jobId", jobId)
                        .addQueryParameter("fileUniqueId", fileUniqueId)
                        .addQueryParameter("fileName", file.getFileName());
                
                requestBuilder.sendAsync(HttpResponse.BodyHandlers.ofString()).join();
            } catch (IOException ex) {
                System.getLogger(CowoDataPreparationService.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
            }
        }
        return new PreparationResult.Success(jobId);
    }

    private PreparationResult handleWebPage(String url, String jobId) {
        var response = microserviceHttpClient.importService()
              .get("import/html/getRawTextFromLinks")
              .addQueryParameter("jobId", jobId)
              .addQueryParameter("url", url)
              .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofString())
              .join();
        
        if (response == null || response.isBlank()){
            return new PreparationResult.Failure("Could not retrieve content from the web page.");
        }
        return new PreparationResult.Success(jobId);
    }

    private PreparationResult handleWebSite(String rootUrl, int maxUrls, List<String> exclusionTerms, String jobId) {
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
            if (linksArray.isEmpty()){
                return new PreparationResult.Failure("Could not find any links to crawl on the website.");
            }
            
            for (JsonValue jsonValue : linksArray) {
                JsonObject jo = jsonValue.asJsonObject();
                UrlLink urlOnPage = new UrlLink();
                urlOnPage.setLink(jo.getString("linkHref"));
                urlOnPage.setLinkText(jo.getString("linkText"));
                
                microserviceHttpClient.importService()
                  .get("import/html/getRawTextFromLinks")
                  .addQueryParameter("jobId", jobId)
                  .addQueryParameter("url", urlOnPage.getLink())
                  .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofString())
                  .join();
            }
        } catch (Exception e){
            LOG.log(Level.SEVERE, "Error processing crawled website links for job " + jobId, e);
            return new PreparationResult.Failure("Error processing crawled website: " + e.getMessage());
        }
        
        return new PreparationResult.Success(jobId);
    }

    /**
     * Determines the file extension and returns the corresponding FileExtension record.
     * For JSON files, a placeholder "text" is used for the jsonKey. In a real application,
     * this key would likely come from user input or a more sophisticated parsing strategy.
     *
     * @param fileName The name of the file.
     * @return A FileExtension record representing the detected extension.
     */
    private FileExtension getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf('.') == -1) {
            return new FileExtension.Unsupported("");
        }
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        return switch (extension) {
            case "pdf" -> new FileExtension.Pdf();
            case "txt" -> new FileExtension.Txt();
            case "json" -> new FileExtension.Json("text");
            case "csv" -> new FileExtension.Csv();
            default -> new FileExtension.Unsupported(extension);
        };
    }
}