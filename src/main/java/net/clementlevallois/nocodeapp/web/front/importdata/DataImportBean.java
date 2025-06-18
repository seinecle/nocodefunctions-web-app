package net.clementlevallois.nocodeapp.web.front.importdata;

import io.mikael.urlbuilder.UrlBuilder;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import jakarta.inject.Named;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.PhaseId;
import jakarta.inject.Inject;
import jakarta.servlet.annotation.MultipartConfig;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Stream;
import net.clementlevallois.functions.model.FunctionPdfMatcher;
import net.clementlevallois.functions.model.FunctionPdfRegionExtract;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.importers.model.ImagesPerFile;
import net.clementlevallois.importers.model.SheetModel;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.functions.UmigonBean;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped
@MultipartConfig
public class DataImportBean implements Serializable {

    @Inject
    BackToFrontMessengerBean logBean;

    @Inject
    SessionBean sessionBean;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    private MicroserviceHttpClient microserviceClient;

    private Integer progress = 0;

    private List<FileUploaded> filesUploaded = new ArrayList();

    private Boolean renderHeadersCheckBox = false;
    private Boolean readButtonDisabled = true;
    private Boolean renderProgressBar = false;
    private Boolean renderCloseOverlay = false;

    private List<SheetModel> dataInSheets;
    private Boolean hasHeaders = false;
    private Boolean bulkData = false;
    private String selectedColumnIndex;
    private String selectedSheetName;
    private Source source;

    private boolean twoColumnsColOneSelected = false;
    private boolean twoColumnsColTwoSelected = false;
    private String twoColumnsIndexForColOne;
    private String twoColumnsIndexForColTwo;
    private Integer countOfSelectedColOne = 0;
    private Integer countOfSelectedColTwo = 0;
    private String currColBeingSelected;
    private List<ImagesPerFile> imagesPerFiles;
    private List<String> imageNamesOfCurrentFile;
    private Map<String, String> pdfsToBeExtracted;
    private String currentFunction;

    private int tabIndex;
    private Properties privateProperties;
    private Globals globals;

    public enum Source {
        TXT,
        CSV,
        XLSX,
        PDF
    }

    public DataImportBean() {
    }

    @PostConstruct
    public void init() {
        dataInSheets = new ArrayList();
        pdfsToBeExtracted = new HashMap();
        privateProperties = applicationProperties.getPrivateProperties();
        globals = new Globals(applicationProperties.getTempFolderFullPath());

    }

    public String readData() throws IOException, URISyntaxException {
        currentFunction = sessionBean.getFunction();
        dataInSheets = new ArrayList();
        sessionBean.createJobId();
        pdfsToBeExtracted = new HashMap();
        progress = 0;
        if (filesUploaded.isEmpty()) {
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.no_file_upload_again"));
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "💔", sessionBean.getLocaleBundle().getString("general.message.no_file_upload_again"));
            return "";
        }

        String gazeOption = sessionBean.getGazeOption();

        Runnable incrementProgress = () -> {
            progress = progress + 1;
        };
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(incrementProgress, 0, 250, TimeUnit.MILLISECONDS);

        for (FileUploaded f : filesUploaded) {
            if (f == null) {
                continue;
            }
            Files.write(globals.getInputFileCompletePath(sessionBean.getJobId(), f.fileUniqueId()), f.bytes());
            if (f.fileName().endsWith("xlsx")) {
                source = Source.XLSX;
            } else if (f.fileName().endsWith("txt")) {
                source = Source.TXT;
            } else if (f.fileName().endsWith("csv") || f.fileName().endsWith("tsv")) {
                source = Source.CSV;
            } else if (f.fileName().endsWith("pdf")) {
                source = Source.PDF;
            } else {
                logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("back.import.file_successful_upload.opening") + f.fileName() + sessionBean.getLocaleBundle().getString("back.import.file_extension_not_recognized"));
                continue;
            }
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.reading_file") + f.fileName());
            switch (source) {
                case XLSX -> {
                    readExcelFile(f);
                }
                case TXT -> {
                    readTextFile(f, gazeOption);
                }
                case CSV ->
                    readCsvFile(f, gazeOption);
                case PDF -> {
                    readPdfFile(f);
                }
                default -> {
                }
            }
        }
        executor.shutdown();
        progress = 100;
        logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.finished_reading_data"));

        filesUploaded = new ArrayList();
        renderCloseOverlay = true;
        return "";
    }

    private void readTextFile(FileUploaded f, String gazeOption) {

        setSelectedSheetName(f.fileName());
        if (currentFunction.equals("gaze") && gazeOption.equals("1")) {
            setSelectedColumnIndex("0");
            microserviceClient.importService().post("/api/import/txt_cooc")
                    .addQueryParameter("jobId", sessionBean.getJobId())
                    .addQueryParameter("fileUniqueId", f.fileUniqueId())
                    .addQueryParameter("fileName", f.fileName())
                    .sendAsync(HttpResponse.BodyHandlers.ofString());

        } else {
            microserviceClient.importService().post("/api/import/txt/simpleLines")
                    .addQueryParameter("jobId", sessionBean.getJobId())
                    .addQueryParameter("fileUniqueId", f.fileUniqueId())
                    .sendAsync(HttpResponse.BodyHandlers.ofString());

        }
        progress = 100;

    }

    public void storePdFile(FileUploaded f) {
        String pdfEncodedAsString = Base64.getEncoder().encodeToString(f.bytes());
        pdfsToBeExtracted.put(f.fileName(), pdfEncodedAsString);
    }

    private void readPdfFile(FileUploaded f) {
        String localizedEmptyLineMessage = sessionBean.getLocaleBundle().getString("general.message.empty_line");
        switch (currentFunction) {
            case FunctionPdfRegionExtract.NAME ->
                microserviceClient.importService().post("/api/pdf/return-png")
                        .addQueryParameter("jobId", sessionBean.getJobId())
                        .addQueryParameter("fileUniqueId", f.fileUniqueId())
                        .addQueryParameter("fileName", f.fileName())
                        .addQueryParameter("localizedEmptyLineMessage", localizedEmptyLineMessage)
                        .sendAsync(HttpResponse.BodyHandlers.ofString());

            case FunctionPdfMatcher.NAME ->
                microserviceClient.importService().post("/api/pdf/linesPerPage")
                        .addQueryParameter("jobId", sessionBean.getJobId())
                        .addQueryParameter("fileUniqueId", f.fileUniqueId())
                        .addQueryParameter("fileName", f.fileName())
                        .addQueryParameter("localizedEmptyLineMessage", localizedEmptyLineMessage)
                        .sendAsync(HttpResponse.BodyHandlers.ofString());
            default ->
                microserviceClient.importService().post("/api/pdf/simpleLines")
                        .addQueryParameter("jobId", sessionBean.getJobId())
                        .addQueryParameter("fileUniqueId", f.fileUniqueId())
                        .sendAsync(HttpResponse.BodyHandlers.ofString());
        }
    }

    private void readCsvFile(FileUploaded f, String gazeOption) {

        setSelectedSheetName(f.fileName());
        // for co-occurrences, we consider all columns, starting from column zero.
        if (currentFunction.equals("gaze") && gazeOption.equals("1")) {
            setSelectedColumnIndex("0");
        }

        if (bulkData) {
            microserviceClient.importService().post("/api/import/csv/simpleLines")
                    .addQueryParameter("jobId", sessionBean.getJobId())
                    .addQueryParameter("fileUniqueId", f.fileUniqueId())
                    .addQueryParameter("colIndex", selectedColumnIndex)
                    .addQueryParameter("hasHeaders", String.valueOf(hasHeaders))
                    .sendAsync(HttpResponse.BodyHandlers.ofString());

        } else {
            microserviceClient.importService().post("/api/import/csv")
                    .addQueryParameter("jobId", sessionBean.getJobId())
                    .addQueryParameter("fileName", f.fileName())
                    .sendAsync(HttpResponse.BodyHandlers.ofString());
        }
    }

    private void readExcelFile(FileUploaded file) {
        HttpRequest request;
        HttpClient client = HttpClient.newHttpClient();
        Set<CompletableFuture> futures = new HashSet();
        HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(file.bytes());
        String gaze_option = "cooc";
        if (sessionBean.getGazeOption().equals("2")) {
            gaze_option = "sim";
        }
        URI uri = UrlBuilder
                .empty()
                .withScheme("http")
                .withPort(Integer.valueOf(privateProperties.getProperty("nocode_import_port")))
                .withHost("localhost")
                .withPath("api/import/xlsx")
                .addParameter("gaze_option", gaze_option)
                .addParameter("separator", ",")
                .toUri();
        request = HttpRequest.newBuilder()
                .POST(bodyPublisher)
                .uri(uri)
                .build();
        CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(resp -> {
            byte[] body = resp.body();
            if (body.length >= 100 && !new String(body, StandardCharsets.UTF_8).toLowerCase().startsWith("internal") && !new String(body, StandardCharsets.UTF_8).toLowerCase().startsWith("not found")) {
                try (
                        ByteArrayInputStream bis = new ByteArrayInputStream(body); ObjectInputStream ois = new ObjectInputStream(bis)) {
                    List<SheetModel> tempResults = (List<SheetModel>) ois.readObject();
                    dataInSheets.addAll(tempResults);
                } catch (Exception ex) {
                    System.out.println("error in body:");
                    System.out.println(new String(body, StandardCharsets.UTF_8));
                    Logger.getLogger(UmigonBean.class.getName()).log(Level.SEVERE, null, ex);
                }
            } else {
                System.out.println("return of xlsx reader by the API was not a 200 code");
                String errorMessage = new String(body, StandardCharsets.UTF_8);
                System.out.println(errorMessage);
                logBean.addOneNotificationFromString(errorMessage);
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, errorMessage, errorMessage);
            }

        }
        );
        futures.add(future);
        CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
        combinedFuture.join();
        // for coocurrences the data must start at the leftest column
        if (sessionBean.getGazeOption().equals("1")) {
            setSelectedColumnIndex("0");
        }
        // by default the selected sheet is the first one of the workbook
        // this gets changed when the user selects a different sheet in the preview
        // there is a listener in the data table in the xhtml that sets the selected sheet
        // to the one currently selected by the user
        if (!dataInSheets.isEmpty()) {
            setSelectedSheetName(dataInSheets.get(0).getName());
        }
        progress = 100;
    }

    public void disableReadButton() {
        readButtonDisabled = true;
        progress = 0;
        renderProgressBar = false;
        setDataInSheets(new ArrayList());
        renderCloseOverlay = false;
    }

    public String extractFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String[] fields = url.split("/");
        return fields[fields.length - 2];
    }

    public Boolean getRenderHeadersCheckBox() {
        return renderHeadersCheckBox;
    }

    public void setRenderHeadersCheckBox(Boolean renderHeadersCheckBox) {
        this.renderHeadersCheckBox = renderHeadersCheckBox;
    }

    public Boolean getReadButtonDisabled() {
        return readButtonDisabled;
    }

    public void setReadButtonDisabled(Boolean readButtonDisabled) {
        this.readButtonDisabled = readButtonDisabled;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public void cancel() {
        progress = null;
    }

    public Boolean getRenderProgressBar() {
        return renderProgressBar;
    }

    public void setRenderProgressBar(Boolean renderProgressBar) {
        this.renderProgressBar = renderProgressBar;
    }

    public Boolean getRenderCloseOverlay() {
        return renderCloseOverlay;
    }

    public void setRenderCloseOverlay(Boolean renderCloseOverlay) {
        this.renderCloseOverlay = renderCloseOverlay;
    }

    public String selectColumn(String colIndex, String sheetName) {
        selectedColumnIndex = colIndex;
        selectedSheetName = sheetName;
        bulkData = false;
        return "/" + currentFunction + "/" + currentFunction + ".xhtml?faces-redirect=true";
    }

    public String launchAnalysisForTwoColumnsDataset() {
        bulkData = false;
        if (!twoColumnsColOneSelected || !twoColumnsColTwoSelected) {
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("back.import.two_columns_needed"));
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "😳", sessionBean.getLocaleBundle().getString("back.import.two_columns_needed"));
            return "";
        }

        if (countOfSelectedColOne != 1 || countOfSelectedColTwo != 1) {
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("back.import.select_one_column_per_type"));
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "😳", sessionBean.getLocaleBundle().getString("back.import.select_one_column_per_type"));
            return "";
        }

        if (twoColumnsIndexForColOne.equals(twoColumnsIndexForColTwo)) {
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("back.import.term_text_different_columns"));
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "😳", sessionBean.getLocaleBundle().getString("back.import.term_text_different_columns"));
            return "";
        }

        return "/" + currentFunction + "/" + currentFunction + ".xhtml?faces-redirect=true";
    }

    public String gotToFunctionWithDataInBulk() {
        bulkData = true;
        return "/" + currentFunction + "/" + currentFunction + ".xhtml?faces-redirect=true";
    }

    public String goToAnalysisForCooccurrences(String sheetName) {
        selectedColumnIndex = "0";
        selectedSheetName = sheetName;
        return "/" + currentFunction + "/" + currentFunction + ".xhtml?faces-redirect=true";
    }

    public List<SheetModel> getDataInSheets() {
        for (SheetModel sheet : dataInSheets) {
            sheet.setHasHeaders(hasHeaders);
        }
        return dataInSheets;
    }

    public void setDataInSheets(List<SheetModel> dataInSheets) {
        this.dataInSheets = dataInSheets;
    }

    public Boolean getHasHeaders() {
        return hasHeaders;
    }

    public void setHasHeaders(Boolean hasHeaders) {
        this.hasHeaders = hasHeaders;
    }

    public void setSelectedColumnIndex(String selectedColumnIndex) {
        this.selectedColumnIndex = selectedColumnIndex;
    }

    public void setSelectedSheetName(String selectedSheetName) {
        this.selectedSheetName = selectedSheetName;
    }

    public String getSelectedColumnIndex() {
        return selectedColumnIndex;
    }

    public String getSelectedSheetName() {
        return selectedSheetName;
    }

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
    }

    public Boolean getBulkData() {
        return bulkData;
    }

    public void setBulkData(Boolean bulkData) {
        this.bulkData = bulkData;
    }

    public void twoColumns(String colIndex, String dataInSheetName) {
        if (this.twoColumnsColOneSelected && currColBeingSelected.equals("col1")) {
            twoColumnsIndexForColOne = colIndex;
        }
        if (this.twoColumnsColTwoSelected && currColBeingSelected.equals("col2")) {
            twoColumnsIndexForColTwo = colIndex;
        }
        selectedSheetName = dataInSheetName;
        bulkData = false;
    }

    public boolean isTwoColumnsColOneSelected() {
        return twoColumnsColOneSelected;
    }

    public void setTwoColumnsColOneSelected(boolean twoColumnsColOneSelected) {
        if (twoColumnsColOneSelected & this.twoColumnsColOneSelected) {
            countOfSelectedColOne++;
            return;
        }

        if (twoColumnsColOneSelected) {
            currColBeingSelected = "col1";
            countOfSelectedColOne++;
        } else {
            countOfSelectedColOne--;
            currColBeingSelected = "";
            twoColumnsIndexForColOne = "";
        }
        this.twoColumnsColOneSelected = twoColumnsColOneSelected;
    }

    public boolean isTwoColumnsColTwoSelected() {
        return twoColumnsColTwoSelected;
    }

    public void setTwoColumnsColTwoSelected(boolean twoColumnsColTwoSelected) {
        if (twoColumnsColTwoSelected & this.twoColumnsColTwoSelected) {
            countOfSelectedColTwo++;
            return;
        }

        if (twoColumnsColTwoSelected) {
            countOfSelectedColTwo++;
            currColBeingSelected = "col2";
        } else {
            countOfSelectedColTwo--;
            currColBeingSelected = "";
            twoColumnsIndexForColTwo = "";
        }
        this.twoColumnsColTwoSelected = twoColumnsColTwoSelected;
    }

    public String getTwoColumnsIndexForColOne() {
        return twoColumnsIndexForColOne;
    }

    public void setTwoColumnsIndexForColOne(String twoColumnsIndexForColOne) {
        this.twoColumnsIndexForColOne = twoColumnsIndexForColOne;
    }

    public String getTwoColumnsIndexForColTwo() {
        return twoColumnsIndexForColTwo;
    }

    public void setTwoColumnsIndexForColTwo(String twoColumnsIndexForColTwo) {
        this.twoColumnsIndexForColTwo = twoColumnsIndexForColTwo;
    }

    public String displayNameForSingleUploadedFileOrSeveralFiles() {
        if (filesUploaded != null) {
            if (filesUploaded.size() == 1) {
                return "🚚 " + sessionBean.getLocaleBundle().getString("back.import.one_file_uploaded") + ": " + filesUploaded.get(0).fileName();
            } else {
                return "🚚 " + String.valueOf(filesUploaded.size()) + " " + sessionBean.getLocaleBundle().getString("back.import.files_uploaded");
            }
        } else {
            return sessionBean.getLocaleBundle().getString("general.message.data_not_found");
        }
    }

    public List<FileUploaded> getFilesUploaded() {
        return filesUploaded;
    }

    public void setFilesUploaded(List<FileUploaded> filesUploaded) {
        this.filesUploaded = filesUploaded;
    }

    public List<ImagesPerFile> getImagesPerFiles() {
        imagesPerFiles = new ArrayList<>();
        Path jobDirectory = globals.getJobDirectory(sessionBean.getJobId());
        String UPLOADED_FILE_PREFIX = Globals.UPLOADED_FILE_PREFIX;
        String PNG_EXTENSION = Globals.PNG_EXTENSION;

        try (Stream<Path> walk = Files.walk(jobDirectory)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(UPLOADED_FILE_PREFIX))
                    .filter(path -> path.getFileName().toString().endsWith(PNG_EXTENSION))
                    .forEach(path -> {
                        try (InputStream is = Files.newInputStream(path); ObjectInputStream ois = new ObjectInputStream(is)) {
                            ImagesPerFile imagesPerOneFile = (ImagesPerFile) ois.readObject();
                            imagesPerFiles.add(imagesPerOneFile);
                        } catch (IOException | ClassNotFoundException ex) {
                            Logger.getLogger(DataImportBean.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    });
        } catch (IOException ex) {
            Logger.getLogger(DataImportBean.class.getName()).log(Level.SEVERE, null, ex);
        }

        return imagesPerFiles;
    }

    public void setImagesPerFiles(List<ImagesPerFile> imagesPerFiles) {
        this.imagesPerFiles = imagesPerFiles;
    }

    public StreamedContent getImage() {
        FacesContext context = FacesContext.getCurrentInstance();

        if (context.getCurrentPhaseId() == PhaseId.RENDER_RESPONSE) {
            // So, we're rendering the view. Return a stub StreamedContent so that it will generate right URL.
            return new DefaultStreamedContent();
        }
        String index = context.getExternalContext().getRequestParameterMap().get("rowIndex");
        byte[] image = imagesPerFiles.get(tabIndex).getImage(Integer.parseInt(index));
        ByteArrayInputStream stream = new ByteArrayInputStream(image);
        String random = UUID.randomUUID().toString();
        StreamedContent imageAsStream = DefaultStreamedContent.builder().name(random).contentType("image/png").stream(() -> stream).build();
        imageNamesOfCurrentFile.add(imageAsStream.toString());
        return imageAsStream;
    }

    public int getTabIndex() {
        return tabIndex;
    }

    public void setTabIndex(int tabIndex) {
        this.tabIndex = tabIndex;
    }

    public Map<String, String> getPdfsToBeExtracted() {
        return pdfsToBeExtracted;
    }

    public void setPdfsToBeExtracted(Map<String, String> pdfsToBeExtracted) {
        this.pdfsToBeExtracted = pdfsToBeExtracted;
    }

    public List<String> getImageNamesOfCurrentFile() {
        return imageNamesOfCurrentFile;
    }
}
