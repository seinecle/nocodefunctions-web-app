package net.clementlevallois.nocodeapp.web.front.importdata;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import jakarta.inject.Named;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.inject.Inject;
import jakarta.servlet.annotation.MultipartConfig;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.Globals.Names;
import static net.clementlevallois.functions.model.Globals.Names.COOC;
import net.clementlevallois.importers.model.ImagesPerFile;
import net.clementlevallois.importers.model.SheetModel;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;

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
    private Names currentFunctionName;

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
        privateProperties = applicationProperties.getPrivateProperties();
        globals = new Globals(applicationProperties.getTempFolderFullPath());

    }

    public String readData() throws IOException, URISyntaxException {
        dataInSheets = new ArrayList();
        sessionBean.createJobId();
        progress = 0;
        if (filesUploaded.isEmpty()) {
            logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.no_file_upload_again"));
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "💔", sessionBean.getLocaleBundle().getString("general.message.no_file_upload_again"));
            return "";
        }
        
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
                    readTextFile(f);
                }
                case CSV ->
                    readCsvFile(f);
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

    private void readTextFile(FileUploaded f) {

        setSelectedSheetName(f.fileName());
        if (currentFunctionName == COOC) {
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
    private void readPdfFile(FileUploaded f) {
        String localizedEmptyLineMessage = sessionBean.getLocaleBundle().getString("general.message.empty_line");
        switch (currentFunctionName) {
            case PDF_MATCHER ->
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

    private void readCsvFile(FileUploaded f) {

        setSelectedSheetName(f.fileName());
        // for co-occurrences, we consider all columns, starting from column zero.
        if (currentFunctionName == COOC) {
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
        switch (currentFunctionName) {
            case COOC ->
                microserviceClient.importService().post("/api/import/xslx/cooc")
                        .addQueryParameter("jobId", sessionBean.getJobId())
                        .addQueryParameter("sheetName", selectedSheetName)
                        .addQueryParameter("hasHeaders", Boolean.toString(hasHeaders))
                        .sendAsync(HttpResponse.BodyHandlers.ofString());

            case SIM ->
                microserviceClient.importService().post("/api/import/xslx/sim")
                        .addQueryParameter("jobId", sessionBean.getJobId())
                        .addQueryParameter("sheetName", selectedSheetName)
                        .addQueryParameter("hasHeaders", Boolean.toString(hasHeaders))
                        .sendAsync(HttpResponse.BodyHandlers.ofString());

            default ->
                System.out.println("reading excel file but not for COOC or SIM, weird!");
        }
        // by default the selected sheet is the first one of the workbook
        // this gets changed when the user selects a different sheet in the preview
        // there is a listener in the data table in the xhtml that sets the selected sheet
        // to the one currently selected by the user

        if (!dataInSheets.isEmpty()) {
            setSelectedSheetName(file.fileName());
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
        return "/" + currentFunctionName + "/" + currentFunctionName + ".xhtml?faces-redirect=true";
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

        return "/" + currentFunctionName + "/" + currentFunctionName + ".xhtml?faces-redirect=true";
    }

    public String gotToFunctionWithDataInBulk() {
        bulkData = true;
        return "/" + currentFunctionName + "/" + currentFunctionName + ".xhtml?faces-redirect=true";
    }

    public String goToAnalysisForCooccurrences(String sheetName) {
        selectedColumnIndex = "0";
        selectedSheetName = sheetName;
        return "/" + currentFunctionName + "/" + currentFunctionName + ".xhtml?faces-redirect=true";
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


    public int getTabIndex() {
        return tabIndex;
    }

    public void setTabIndex(int tabIndex) {
        this.tabIndex = tabIndex;
    }

}
