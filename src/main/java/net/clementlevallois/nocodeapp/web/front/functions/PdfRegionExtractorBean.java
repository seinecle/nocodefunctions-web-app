package net.clementlevallois.nocodeapp.web.front.functions;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.CompletionException;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import net.clementlevallois.importers.model.SheetModel;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
import org.primefaces.model.CroppedImage;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient.MicroserviceCallException;

import org.primefaces.PrimeFaces;


@Named
@SessionScoped
public class PdfRegionExtractorBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(PdfRegionExtractorBean.class.getName());

    private Integer progress;
    private Boolean renderProgressBar = false;
    private Boolean runButtonDisabled = true;
    private StreamedContent fileToSave;
    private Boolean renderSeeResultsButton = false;
    private CroppedImage[] selectedRegions;
    private boolean allPages;
    private boolean selectPage; // This seems to be a flag, not the page number itself
    private int selectedPage; // This holds the actual page number
    private float proportionTopLeftX;
    private float proportionTopLeftY;
    private float proportionWidth;
    private float proportionHeight;
    private ConcurrentHashMap<String, SheetModel> results = new ConcurrentHashMap();

    @Inject
    BackToFrontMessengerBean logBean;

    @Inject
    SessionBean sessionBean;

    @Inject
    DataImportBean inputData;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    private MicroserviceHttpClient microserviceClient;

    public PdfRegionExtractorBean() {
    }

    @PostConstruct
    public void init() {
        sessionBean.setFunction("pdf_region_extractor");
        results = new ConcurrentHashMap();
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public String goToPdfUpload() {
        return "/import/import_your_pdf_choose_region.xhtml?function=pdf_region_extractor&amp;faces-redirect=true";
    }

    public void fillInCoordinates() {
        if (inputData.getImagesPerFiles() == null || inputData.getImagesPerFiles().isEmpty()) {
             LOG.warning("No images found in inputData.");
             sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Input Error", "No PDF images available to select region.");
             return;
        }
        if (selectedRegions == null || selectedRegions.length == 0) {
             LOG.warning("No regions selected.");
             sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Input Error", "Please select a region on at least one page.");
             return;
        }

        try {
            // Use the image from the first file, and the appropriate page (0 for allPages, selectedPage for single)
            byte[] imageBytes = inputData.getImagesPerFiles().get(0).getImages()[allPages ? 0 : selectedPage];
            if (imageBytes == null) {
                 LOG.warning("Image bytes are null for selected page.");
                 sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Input Error", "Could not load image for the selected page.");
                 return;
            }

            try (InputStream is = new ByteArrayInputStream(imageBytes)) {
                BufferedImage buf = ImageIO.read(is);
                if (buf == null) {
                     LOG.warning("Could not read BufferedImage from bytes.");
                     sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Processing Error", "Could not process image data.");
                     return;
                }
                int heightImage = buf.getHeight();
                int widthImage = buf.getWidth();

                CroppedImage selectedRegion;
                if (allPages) {
                    selectedRegion = selectedRegions[0];
                } else {
                    if (selectedPage < 0 || selectedPage >= selectedRegions.length) {
                         LOG.warning("Selected page index out of bounds for selectedRegions array.");
                         sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Input Error", "Invalid page selection.");
                         return;
                    }
                    selectedRegion = selectedRegions[selectedPage];
                }

                if (selectedRegion == null || selectedRegion.getLeft() == 0 && selectedRegion.getTop() == 0 && selectedRegion.getWidth() == 0 && selectedRegion.getHeight() == 0) {
                    LOG.warning("Selected region coordinates are zero.");
                    sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Input Error", "Please select a valid region.");
                    return;
                }

                int leftCornerX = selectedRegion.getLeft();
                int leftCornerY = selectedRegion.getTop();
                int height = selectedRegion.getHeight();
                int width = selectedRegion.getWidth();

                proportionTopLeftX = (float) leftCornerX / (float) widthImage;
                proportionTopLeftY = (float) leftCornerY / (float) heightImage;
                proportionWidth = (float) width / (float) widthImage;
                proportionHeight = (float) height / (float) heightImage;

                LOG.log(Level.INFO, "Calculated proportions: X={0}, Y={1}, W={2}, H={3}", new Object[]{proportionTopLeftX, proportionTopLeftY, proportionWidth, proportionHeight});

            }
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Error reading image or calculating proportions", ex);
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Processing Error", "Could not process image for coordinates: " + ex.getMessage());
        } catch (Exception ex) {
             LOG.log(Level.SEVERE, "Unexpected error in fillInCoordinates", ex);
             sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Processing Error", "An unexpected error occurred.");
        }
    }

    public CroppedImage getSelectedRegion() {
        FacesContext context = FacesContext.getCurrentInstance();
        String indexStr = context.getExternalContext().getRequestParameterMap().get("rowIndex2");
        int index = 0;
        if (indexStr != null) {
            try {
                index = Integer.parseInt(indexStr);
            } catch (NumberFormatException e) {
                 LOG.log(Level.WARNING, "Invalid rowIndex2 parameter: " + indexStr, e);
                 // Default to 0 or handle error
            }
        }

        if (inputData.getImagesPerFiles() == null || inputData.getImagesPerFiles().isEmpty()) {
             return null; // Cannot initialize if no images
        }

        // Initialize selectedRegions array if null or size mismatch
        int expectedSize = inputData.getImagesPerFiles().get(0).getImages().length;
        if (selectedRegions == null || selectedRegions.length != expectedSize) {
            selectedRegions = new CroppedImage[expectedSize];
            for (int i = 0; i < expectedSize; i++) {
                selectedRegions[i] = new CroppedImage(); // Initialize with empty CroppedImage
            }
             LOG.log(Level.INFO, "Initialized selectedRegions array with size: {0}", expectedSize);
        }

        if (index >= 0 && index < selectedRegions.length) {
            return selectedRegions[index];
        } else {
            LOG.log(Level.WARNING, "Requested region index {0} out of bounds (array size {1}).", new Object[]{index, selectedRegions.length});
            return new CroppedImage(); // Return empty CroppedImage on error/out of bounds
        }
    }

    public void setSelectedRegion(CroppedImage selectedRegion) {
        // This method is called by the UI component when a region is selected/changed.
        // It needs the index of the page where the region was selected.
        FacesContext context = FacesContext.getCurrentInstance();
        String indexStr = context.getExternalContext().getRequestParameterMap().get("rowIndex2");
        int index = -1;
         if (indexStr != null) {
            try {
                index = Integer.parseInt(indexStr);
            } catch (NumberFormatException e) {
                 LOG.log(Level.WARNING, "Invalid rowIndex2 parameter for setSelectedRegion: " + indexStr, e);
                 sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Input Error", "Invalid page index for selected region.");
                 return;
            }
        } else {
             LOG.warning("Missing rowIndex2 parameter for setSelectedRegion.");
             sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Input Error", "Missing page index for selected region.");
             return;
        }


        if (selectedRegions == null || index < 0 || index >= selectedRegions.length) {
             LOG.log(Level.WARNING, "selectedRegions array not initialized or index {0} out of bounds (array size {1}).", new Object[]{index, selectedRegions != null ? selectedRegions.length : "null"});
             sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Processing Error", "Could not save selected region.");
             return;
        }

        selectedRegions[index] = selectedRegion;
        LOG.log(Level.INFO, "Saved selected region for page index {0}.", index);

        // If "all pages" is selected, copy the selected region from page 0 to all other pages
        if (allPages && index == 0) {
             for (int i = 1; i < selectedRegions.length; i++) {
                 selectedRegions[i] = selectedRegion;
             }
             LOG.info("Copied selected region from page 0 to all pages.");
        }

        // Update button state if a valid region is selected
        runButtonDisabled = !(selectedRegion != null && (selectedRegion.getWidth() > 0 || selectedRegion.getHeight() > 0));
        PrimeFaces.current().ajax().update("runButtonId"); // Replace with your button's actual ID
    }


    public String extract() {
        fillInCoordinates(); // Calculate proportions based on selected region

        if (proportionWidth == 0 || proportionHeight == 0) {
             sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Input Error", "Please select a valid region before extracting.");
             return null; // Stay on the same page
        }

        sessionBean.sendFunctionPageReport();
        logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
        progress = 0;
        renderProgressBar = true;
        runButtonDisabled = true;
        renderSeeResultsButton = false;
        results = new ConcurrentHashMap();

        Map<String, String> pdfs = inputData.getPdfsToBeExtracted();
        if (pdfs == null || pdfs.isEmpty()) {
             sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Input Error", sessionBean.getLocaleBundle().getString("general.message.data_not_found"));
             runButtonDisabled = false;
             renderProgressBar = false;
             return null;
        }

        Set<CompletableFuture<Void>> futures = new HashSet();
        String owner = applicationProperties.getPrivateProperties().getProperty("pwdOwner");
         if (owner == null) {
             LOG.severe("pwdOwner property is not set!");
             sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Configuration Error", "Owner password not configured.");
             runButtonDisabled = false;
             renderProgressBar = false;
             return null;
         }

        for (Map.Entry<String, String> pdf : pdfs.entrySet()) {
             String fileName = pdf.getKey();
             String pdfBytesBase64 = pdf.getValue(); // Assuming this is base64 encoded PDF bytes

             // Create JSON payload with PDF bytes and filename
             JsonObject jsonPayload = Json.createObjectBuilder()
                 .add("pdfBytes", pdfBytesBase64)
                 .add("fileName", fileName)
                 .build();


            // Send async call for each PDF
            CompletableFuture<Void> future = microserviceClient.importService().post("/api/import/pdf/extract-region")
                .withJsonPayload(jsonPayload) // Add the PDF data payload
                .addQueryParameter("owner", owner)
                .addQueryParameter("allPages", String.valueOf(allPages))
                .addQueryParameter("selectedPage", String.valueOf(selectedPage)) // Send even if allPages is true, microservice decides
                .addQueryParameter("leftCornerX", String.valueOf(proportionTopLeftX))
                .addQueryParameter("leftCornerY", String.valueOf(proportionTopLeftY))
                .addQueryParameter("width", String.valueOf(proportionWidth))
                .addQueryParameter("height", String.valueOf(proportionHeight))
                .sendAsync(HttpResponse.BodyHandlers.ofByteArray()) // Expecting byte array (serialized SheetModel)
                .thenAccept(resp -> {
                    // This callback runs on HttpClient's thread pool
                    if (resp.statusCode() == 200) {
                        byte[] body = resp.body();
                        try (ByteArrayInputStream bis = new ByteArrayInputStream(body);
                             ObjectInputStream ois = new ObjectInputStream(bis)) {
                            SheetModel resultsAsSheet = (SheetModel) ois.readObject();
                            results.put(resultsAsSheet.getName(), resultsAsSheet); // Store results by filename
                            LOG.log(Level.INFO, "Processed PDF {0}.", resultsAsSheet.getName());
                        } catch (IOException | ClassNotFoundException ex) {
                            LOG.log(Level.SEVERE, "Error deserializing SheetModel for PDF " + fileName, ex);
                             // Handle deserialization error - maybe store an empty SheetModel or error indicator
                             results.put(fileName, new SheetModel(fileName)); // Store empty SheetModel on error
                             sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Processing Error", "Could not process results for " + fileName + ": " + ex.getMessage());
                        }
                    } else {
                        String errorBody = new String(resp.body(), StandardCharsets.UTF_8);
                        LOG.log(Level.SEVERE, "PdfRegionExtractor microservice call failed for PDF {0}. Status: {1}, Body: {2}", new Object[]{fileName, resp.statusCode(), errorBody});
                         // Handle microservice error - store an empty SheetModel or error indicator
                         results.put(fileName, new SheetModel(fileName)); // Store empty SheetModel on error
                         sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Processing Error", "Microservice error for " + fileName + ": Status " + resp.statusCode() + ", " + errorBody);
                    }
                    // Update progress after each PDF is processed
                    updateProgress();
                })
                .exceptionally(exception -> {
                    // This callback runs on HttpClient's thread pool if an exception occurs during the call
                    LOG.log(Level.SEVERE, "Exception during async PdfRegionExtractor call for PDF " + fileName, exception);
                    String errorMessage = "Communication error for " + fileName + ": " + exception.getMessage();
                     if (exception.getCause() instanceof MicroserviceCallException) {
                         MicroserviceCallException msce = (MicroserviceCallException) exception.getCause();
                         errorMessage = "Communication error for " + fileName + ": Status " + msce.getStatusCode() + ", " + msce.getErrorBody();
                     }
                     // Handle communication error - store an empty SheetModel or error indicator
                     results.put(fileName, new SheetModel(fileName)); // Store empty SheetModel on error
                     sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Processing Error", errorMessage);

                    // Update progress even on exception
                    updateProgress();
                    return null;
                });
            futures.add(future);
        }

        this.progress = 1; // Initial progress after sending requests
        logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.almost_done"));
         PrimeFaces.current().ajax().update("progressComponentId"); // Update progress component ID

        // Wait for all futures to complete
        try {
            CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            combinedFuture.join(); // This blocks until all futures are done
            LOG.info("All PdfRegionExtractor microservice calls completed.");

            this.progress = 100;
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.analysis_complete"));
            runButtonDisabled = false; // Enable button
            renderSeeResultsButton = true; // Show results button

            PrimeFaces.current().ajax().update("formComputeButton:computeButton", "notifications", "progressComponentId", "resultsButtonPanel");

            // Navigate to the results page
            return "/" + sessionBean.getFunction() + "/results.xhtml?faces-redirect=true";

        } catch (CompletionException cex) {
             Throwable cause = cex.getCause();
             LOG.log(Level.SEVERE, "Exception during completion of async PdfRegionExtractor calls", cause);
             String errorMessage = "Extraction failed: " + cause.getMessage();
              if (cause instanceof MicroserviceCallException) {
                  MicroserviceCallException msce = (MicroserviceCallException) cause;
                  errorMessage = "Extraction failed: Status " + msce.getStatusCode() + ", " + msce.getErrorBody();
              }
             sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Extraction Failed", errorMessage);
             logBean.addOneNotificationFromString(errorMessage);

             this.progress = 0; // Reset progress on failure
             renderProgressBar = false;
             runButtonDisabled = false; // Enable button on failure
             renderSeeResultsButton = false; // Hide results button
             PrimeFaces.current().ajax().update("formComputeButton:computeButton", "notifications", "progressComponentId", "resultsButtonPanel");

             return null; // Stay on the same page or navigate to error page
        } catch (Exception ex) {
             LOG.log(Level.SEVERE, "Unexpected error after sending PdfRegionExtractor calls", ex);
             sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Extraction Failed", "An unexpected error occurred: " + ex.getMessage());
             logBean.addOneNotificationFromString("An unexpected error occurred: " + ex.getMessage());

             this.progress = 0; // Reset progress on failure
             renderProgressBar = false;
             runButtonDisabled = false; // Enable button on failure
             renderSeeResultsButton = false; // Hide results button
             PrimeFaces.current().ajax().update("formComputeButton:computeButton", "notifications", "progressComponentId", "resultsButtonPanel");

             return null; // Stay on the same page
        }
    }

    private synchronized void updateProgress() {
        int totalDocs = inputData.getPdfsToBeExtracted().size(); // Assuming this is the total number of PDFs processed
        if (totalDocs > 0) {
            int currentProgress = (int) ((float) results.size() * 100 / totalDocs);
            if (currentProgress > progress) {
                progress = currentProgress;
                // Trigger UI update for progress
                 PrimeFaces.current().ajax().update("progressComponentId"); // Replace with actual ID
            }
        }
    }


    public StreamedContent getFileToSave() {
        if (results == null || results.isEmpty()) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Download Error", "No results available to export.");
            return new DefaultStreamedContent();
        }
        try {
            // Convert the ConcurrentHashMap<String, SheetModel> to a format the export service expects
            // Assuming the export service expects a serialized object containing the results map
            byte[] resultsAsByteArray = Converters.byteArraySerializerForAnyObject(results);

            // Use MicroserviceHttpClient to call the export service
            CompletableFuture<byte[]> futureBytes = microserviceClient.importService().post("/api/export/xlsx/pdf_region_extractor")
                 .withByteArrayPayload(resultsAsByteArray) // Send the serialized results
                 // Original code had placeholder query parameters, keeping them for now
                 .addQueryParameter("file name", FacesContext.getCurrentInstance().getViewRoot().getLocale().toLanguageTag()) // Placeholder?
                 .addQueryParameter("text extracted", FacesContext.getCurrentInstance().getViewRoot().getLocale().toLanguageTag()) // Placeholder?
                 .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofByteArray()); // Execute and get body as byte[]

            // Block to get the result for StreamedContent
            byte[] body = futureBytes.join();

            try (InputStream is = new ByteArrayInputStream(body)) {
                return DefaultStreamedContent.builder()
                        .name("extracted_regions.xlsx") // Use a descriptive name
                        .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .stream(() -> is)
                        .build();
            } catch (IOException e) {
                 LOG.log(Level.SEVERE, "Error creating StreamedContent from export response body", e);
                 sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Download Error", "Could not prepare download file.");
                 return new DefaultStreamedContent();
            }

        } catch (CompletionException cex) {
             Throwable cause = cex.getCause();
             LOG.log(Level.SEVERE, "Error during asynchronous export service call (CompletionException)", cause);
             String errorMessage = "Error exporting data: " + cause.getMessage();
              if (cause instanceof MicroserviceCallException msce) {
                  errorMessage = "Error exporting data: Status " + msce.getStatusCode() + ", " + msce.getErrorBody();
              }
             sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Export Failed", errorMessage);
             return new DefaultStreamedContent();
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Error serializing results before export", ex);
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Export Failed", "Error preparing data for export: " + ex.getMessage());
            return new DefaultStreamedContent();
        } catch (Exception ex) {
             LOG.log(Level.SEVERE, "Unexpected error in getFileToSave", ex);
             sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Export Failed", "An unexpected error occurred: " + ex.getMessage());
             return new DefaultStreamedContent();
        }
    }

    public void setFileToSave(StreamedContent fileToSave) {
        this.fileToSave = fileToSave;
    }

    public void onComplete() {
        // This method seems unused in the provided code
    }

    // Duplicate cancel method removed

    public Boolean getRenderSeeResultsButton() {
        return renderSeeResultsButton;
    }

    public void setRenderSeeResultsButton(Boolean renderSeeResultsButton) {
        this.renderSeeResultsButton = renderSeeResultsButton;
    }

    public Boolean getRunButtonDisabled() {
        return runButtonDisabled;
    }

    public void setRunButtonDisabled(Boolean runButtonDisabled) {
        this.runButtonDisabled = runButtonDisabled;
    }

    public void dummy() {
        // Used by PrimeFaces for AJAX updates without explicit action
    }

    public boolean isAllPages() {
        return allPages;
    }

    public void setAllPages(boolean allPages) {
        this.allPages = allPages;
    }

    public boolean isSelectPage() {
        return selectPage;
    }

    public void setSelectPage(boolean selectPage) {
        FacesContext context = FacesContext.getCurrentInstance();
        String index = context.getExternalContext().getRequestParameterMap().get("rowIndex3");
        if (index != null) {
            try {
                 selectedPage = Integer.parseInt(index);
                 this.selectPage = selectPage;
                 LOG.log(Level.INFO, "Selected page set to: {0}", selectedPage);
            } catch (NumberFormatException e) {
                 LOG.log(Level.WARNING, "Invalid rowIndex3 parameter for setSelectPage: " + index, e);
                 sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Input Error", "Invalid page number selected.");
            }
        } else {
             LOG.warning("Missing rowIndex3 parameter for setSelectPage.");
             sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Input Error", "Missing page number for selection.");
        }
    }

    public Boolean getRenderProgressBar() {
        return renderProgressBar;
    }

    public void setRenderProgressBar(Boolean renderProgressBar) {
        this.renderProgressBar = renderProgressBar;
    }
}
