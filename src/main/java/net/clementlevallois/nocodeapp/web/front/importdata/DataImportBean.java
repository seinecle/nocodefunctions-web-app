/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.importdata;

import com.monitorjbl.xlsx.StreamingReader;
import io.mikael.urlbuilder.UrlBuilder;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import javax.inject.Named;
import java.io.Serializable;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.enterprise.context.SessionScoped;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.servlet.annotation.MultipartConfig;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonBean;
import net.clementlevallois.nocodeapp.web.front.labelling.TaskMetadata;
import net.clementlevallois.nocodeapp.web.front.functions.GazeBean;
import net.clementlevallois.nocodeapp.web.front.functions.LabellingBean;
import net.clementlevallois.nocodeapp.web.front.io.ExcelReader;
import net.clementlevallois.nocodeapp.web.front.logview.NotificationService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.openide.util.Exceptions;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.event.FilesUploadEvent;
import org.primefaces.model.file.UploadedFile;
import org.primefaces.model.file.UploadedFiles;
import redis.clients.jedis.Jedis;
import static redis.clients.jedis.ScanParams.SCAN_POINTER_START;
import redis.clients.jedis.ScanResult;

/**
 *
 * @author LEVALLOIS
 */
@Named(value = "dataImportBean")
@SessionScoped
@MultipartConfig
public class DataImportBean implements Serializable {

    @Inject
    NotificationService service;

    @Inject
    GazeBean gazeBean;

    @Inject
    LabellingBean labellingBean;

    @Inject
    SessionBean sessionBean;

    private Integer progress;

    private UploadedFile file;
    private UploadedFiles files;

    private String gsheeturl;

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

    private String persistToDisk = "false";
    private String taskId = "";
    private String emailTaskDesigner = "";
    private String datasetDescription = "";
    private String datasetName = "";

    private boolean twoColumnsColOneSelected = false;
    private boolean twoColumnsColTwoSelected = false;
    private String twoColumnsIndexForColOne;
    private String twoColumnsIndexForColTwo;
    private Integer countOfSelectedColOne = 0;
    private Integer countOfSelectedColTwo = 0;
    private String currColBeingSelected;

    public enum Source {
        TXT,
        CSV,
        XLSX,
        PDF,
        TWITTER,
        GS
    }

    public DataImportBean() {
        dataInSheets = new ArrayList();
    }

    public void addMessage(FacesMessage.Severity severity, String summary, String detail) {
        FacesContext.getCurrentInstance().
                addMessage(null, new FacesMessage(severity, summary, detail));
    }

    public String readDataForPdfMatcherFunction() {
        dataInSheets = new ArrayList();
        if (file == null && files == null) {
            service.create(sessionBean.getLocaleBundle().getString("general.message.no_file_upload_again"));
            addMessage(FacesMessage.SEVERITY_WARN, "💔", sessionBean.getLocaleBundle().getString("general.message.no_file_upload_again"));
            return "";
        }
        this.progress = 3;
        Runnable incrementProgress = () -> {
            progress = progress + 1;
        };
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(incrementProgress, 0, 250, TimeUnit.MILLISECONDS);

        if (file != null) {

            try {
                service.create(sessionBean.getLocaleBundle().getString("general.message.reading_pdf_file"));
                readPdfFile(file.getInputStream(), file.getFileName());
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        } else if (files != null) {
            for (UploadedFile f : files.getFiles()) {
                if (f != null && f.getFileName().endsWith("pdf")) {
                    try {
                        source = Source.PDF;
                        service.create(sessionBean.getLocaleBundle().getString("general.message.reading_file") + f.getFileName());
                        readPdfFile(f.getInputStream(), f.getFileName());
                    } catch (IOException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            }
        }
        executor.shutdown();
        progress = 100;
        service.create(sessionBean.getLocaleBundle().getString("general.message.finished_reading_data"));

        file = null;
        files = null;
        gsheeturl = null;

        renderCloseOverlay = true;
        return "";
    }

    public String readData() throws IOException, URISyntaxException {
        dataInSheets = new ArrayList();
        if (file == null && gsheeturl == null && files == null) {
            service.create(sessionBean.getLocaleBundle().getString("general.message.no_file_upload_again"));
            addMessage(FacesMessage.SEVERITY_WARN, "💔", sessionBean.getLocaleBundle().getString("general.message.no_file_upload_again"));
            return "";
        }
        InputStream is = file.getInputStream();
        String fileName = file.getFileName();
        String gazeOption = gazeBean == null ? "1" : gazeBean.getOption();
        this.progress = 3;
        Runnable incrementProgress = () -> {
            progress = progress + 1;
        };
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(incrementProgress, 0, 250, TimeUnit.MILLISECONDS);

//        if (gsheeturl != null && !gsheeturl.isBlank()) {
////            service.create(sessionBean.getLocaleBundle().getString("general.message.reading_google_spreadsheet"));
////            String spreadsheetId = extractFromUrl(gsheeturl);
////            dataInSheets = googleBean.readGSheetData(spreadsheetId);
//        } else
        if (file != null) {
            switch (source) {
                case XLSX -> {
                    service.create(sessionBean.getLocaleBundle().getString("general.message.reading_excel_file"));
                    try {
                        dataInSheets.addAll(readExcelFile(file));
                    } catch (IOException ex) {
                        Logger.getLogger(DataImportBean.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                case TXT -> {
                    service.create(sessionBean.getLocaleBundle().getString("general.message.reading_text_file"));
                    readTextFile(is, fileName, sessionBean.getFunction(), gazeOption);
                }
                case CSV -> {
                    service.create(sessionBean.getLocaleBundle().getString("general.message.reading_csv_file"));
                    readCsvFile(is, fileName, sessionBean.getFunction(), gazeOption);
                }
                case PDF -> {
                    service.create(sessionBean.getLocaleBundle().getString("general.message.reading_pdf_file"));
                    readPdfFile(is, fileName);
                }
                default -> {
                }
            }
        } else if (files != null) {
            for (UploadedFile f : files.getFiles()) {
                if (f == null) {
                    return "";
                }
                try {
                    if (f.getFileName().endsWith("xlsx")) {
                        source = Source.XLSX;
                    } else if (f.getFileName().endsWith("txt")) {
                        source = Source.TXT;
                    } else if (f.getFileName().endsWith("csv")) {
                        source = Source.CSV;
                    } else if (f.getFileName().endsWith("pdf")) {
                        source = Source.PDF;
                    }
                    is = f.getInputStream();
                    fileName = f.getFileName();
                    service.create(sessionBean.getLocaleBundle().getString("general.message.reading_file") + f.getFileName());
                    switch (source) {
                        case XLSX -> {
                            try {
                                dataInSheets.addAll(readExcelFile(f));
                            } catch (IOException ex) {
                                Logger.getLogger(DataImportBean.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                        case TXT -> {
                            readTextFile(is, fileName, sessionBean.getFunction(), gazeOption);
                        }
                        case CSV ->
                            readCsvFile(is, fileName, sessionBean.getFunction(), gazeOption);
                        case PDF ->
                            readPdfFile(is, fileName);
                        default -> {
                        }
                    }
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
        executor.shutdown();
        progress = 100;
        service.create(sessionBean.getLocaleBundle().getString("general.message.finished_reading_data"));

        file = null;
        files = null;
        gsheeturl = null;

        renderCloseOverlay = true;
        return "";
    }

    public String handleFileUpload(FileUploadEvent event) {
        progress = 0;
        file = event.getFile();
        if (file == null) {
            return "";
        }
        service.create(sessionBean.getLocaleBundle().getString("back.import.file_successful_upload.opening") + file.getFileName() + sessionBean.getLocaleBundle().getString("back.import.file_successful_upload.closing"));

        if (file.getFileName().endsWith("xlsx")) {
            setSource(Source.XLSX);
        } else if (file.getFileName().endsWith("txt")) {
            setSource(Source.TXT);
        } else if (file.getFileName().endsWith("csv")) {
            setSource(Source.CSV);
        } else if (file.getFileName().endsWith("pdf")) {
            setSource(Source.PDF);
        }
        readButtonDisabled = false;
        renderProgressBar = true;
        return "";
    }

    public void uploadMultiple() {
        if (files != null) {
            for (UploadedFile f : files.getFiles()) {
                service.create(sessionBean.getLocaleBundle().getString("back.import.file_successful_upload.opening") + f.getFileName() + sessionBean.getLocaleBundle().getString("back.import.file_successful_upload.closing"));
            }
        }
        readButtonDisabled = false;
        renderProgressBar = true;
    }

    public void handleFilesUpload(FilesUploadEvent event) {
        dataInSheets = new ArrayList();
        files = event.getFiles();
        if (files != null) {
            for (UploadedFile f : files.getFiles()) {
                service.create(sessionBean.getLocaleBundle().getString("back.import.file_successful_upload.opening") + f.getFileName() + sessionBean.getLocaleBundle().getString("back.import.file_successful_upload.closing"));
            }
        }
        readButtonDisabled = false;
        renderProgressBar = true;
    }

    public void toggleHeaders() {
    }

    public void chooseGSheet() {
        file = null;
        dataInSheets = new ArrayList();
        renderProgressBar = true;
        setSource(Source.GS);
    }

    private void readTextFile(InputStream is, String fileName, String functionName, String gazeOption) {

        try {
            if (functionName.equals("gaze") && gazeOption.equals("1")) {
                setSelectedColumnIndex("0");
                setSelectedSheetName(fileName);
            }

            HttpRequest request;
            HttpClient client = HttpClient.newHttpClient();
            Set<CompletableFuture> futures = new HashSet();

            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(is.readAllBytes());

            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(7003)
                    .withHost("localhost")
                    .withPath("api/import/txt")
                    .addParameter("fileName", fileName)
                    .addParameter("functionName", functionName)
                    .addParameter("gazeOption", gazeOption)
                    .toUri();

            request = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .uri(uri)
                    .build();

            CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(
                    resp -> {
                        byte[] body = resp.body();
                        try (
                         ByteArrayInputStream bis = new ByteArrayInputStream(body);  ObjectInputStream ois = new ObjectInputStream(bis)) {
                            List<SheetModel> tempResult = (List<SheetModel>) ois.readObject();
                            dataInSheets.addAll(tempResult);
                        } catch (IOException | ClassNotFoundException ex) {
                            Logger.getLogger(DataImportBean.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
            );

            futures.add(future);

            CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
            combinedFuture.join();
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    private void readPdfFile(InputStream is, String fileName) {
        try {
            String localizedEmptyLineMessage = sessionBean.getLocaleBundle().getString("general.message.empty_line");
            
            HttpRequest request;
            HttpClient client = HttpClient.newHttpClient();
            Set<CompletableFuture> futures = new HashSet();
            
            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(is.readAllBytes());
            
            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(7003)
                    .withHost("localhost")
                    .withPath("api/import/pdf")
                    .addParameter("fileName", fileName)
                    .addParameter("localizedEmptyLineMessage", localizedEmptyLineMessage)
                    .toUri();
            
            request = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .uri(uri)
                    .build();
            
            CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(
                    resp -> {
                        byte[] body = resp.body();
                        try (
                                ByteArrayInputStream bis = new ByteArrayInputStream(body);  ObjectInputStream ois = new ObjectInputStream(bis)) {
                            List<SheetModel> tempResult = (List<SheetModel>) ois.readObject();
                            dataInSheets.addAll(tempResult);
                        } catch (IOException | ClassNotFoundException ex) {
                            Logger.getLogger(DataImportBean.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
            );
            
            futures.add(future);
            
            CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
            combinedFuture.join();
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    private void readCsvFile(InputStream is, String fileName, String functionName, String gazeOption) {
        try {

            /* since we DONT do a bulk import in a CSV import,
            we have no concept of "selected sheet" among several sheets.
            we need to set the file name of the unique file for a sheet name
             */
            setSelectedSheetName(file.getFileName());

            // for co-occurrences, we consider all columns, starting from column zero.
            if (functionName.equals("gaze") && gazeOption.equals("1")) {
                setSelectedColumnIndex("0");
            }

            HttpRequest request;
            HttpClient client = HttpClient.newHttpClient();
            Set<CompletableFuture> futures = new HashSet();

            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(is.readAllBytes());

            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(7003)
                    .withHost("localhost")
                    .withPath("api/import/csv")
                    .addParameter("fileName", fileName)
                    .addParameter("functionName", functionName)
                    .addParameter("gazeOption", gazeOption)
                    .toUri();

            request = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .uri(uri)
                    .build();

            CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(
                    resp -> {
                        byte[] body = resp.body();
                        try (
                         ByteArrayInputStream bis = new ByteArrayInputStream(body);  ObjectInputStream ois = new ObjectInputStream(bis)) {
                            List<SheetModel> tempResult = (List<SheetModel>) ois.readObject();
                            dataInSheets.addAll(tempResult);
                        } catch (IOException | ClassNotFoundException ex) {
                            Logger.getLogger(DataImportBean.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
            );

            futures.add(future);

            CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
            combinedFuture.join();
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    private List<SheetModel> readExcelFile(UploadedFile file) throws FileNotFoundException, IOException {
        Runnable incrementProgress = () -> {
            progress = progress + 1;
        };
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(incrementProgress, 0, 250, TimeUnit.MILLISECONDS);

        List<SheetModel> sheets = new ArrayList();

        InputStream is = file.getInputStream();

        try ( Workbook wb = StreamingReader.builder()
                .rowCacheSize(100) // number of rows to keep in memory (defaults to 10)
                .bufferSize(4096) // buffer size to use when reading InputStream to file (defaults to 1024)
                .open(is)) {
            int sheetNumber = 0;
            for (Sheet sheet : wb) {
                sheetNumber++;
                service.create(sessionBean.getLocaleBundle().getString("back.import.reading_sheet") + " #" + sheetNumber);
                List<ColumnModel> headerNames = new ArrayList();
                SheetModel sheetModel = new SheetModel();
                sheetModel.setName(sheet.getSheetName());
                int rowNumber = 0;
                long lastTime = System.currentTimeMillis();

                for (Row r : sheet) {
                    if (rowNumber == 0) {
                        for (Cell cell : r) {
                            if (cell == null) {
                                continue;
                            }
                            ColumnModel cm;
                            String cellStringValue = ExcelReader.returnStringValue(cell);
                            cellStringValue = Jsoup.clean(cellStringValue, Safelist.basicWithImages().addAttributes("span", "style"));
                            cm = new ColumnModel(String.valueOf(cell.getColumnIndex()), cellStringValue);
                            headerNames.add(cm);
                        }
                        sheetModel.setTableHeaderNames(headerNames);
                    }
                    rowNumber++;

                    for (Cell cell : r) {
                        if (cell == null) {
                            continue;
                        }
                        String returnStringValue = ExcelReader.returnStringValue(cell);
                        returnStringValue = Jsoup.clean(returnStringValue, Safelist.basicWithImages().addAttributes("span", "style"));
                        CellRecord cellRecord = new CellRecord(cell.getRowIndex(), cell.getColumnIndex(), returnStringValue);
                        sheetModel.addCellRecord(cellRecord);
                    }
                }
                sheets.add(sheetModel);
                // if we are computing cooccurrences, we need to set the file name for a sheet name and the column to zero.
                if (sessionBean.getFunction().equals("gaze") && gazeBean != null && gazeBean.getOption().equals("1")) {
                    setSelectedColumnIndex("0");
                    setSelectedSheetName(file.getFileName() + "_" + sheet.getSheetName());
                }
            }

            executor.shutdown();
            this.progress = 100;
        }
        return sheets;
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

    public UploadedFile getFile() {
        return file;
    }

    public void setFile(UploadedFile file) {
        this.file = file;
    }

    public UploadedFiles getFiles() {
        return files;
    }

    public void setFiles(UploadedFiles files) {
        this.files = files;
    }

    public Boolean getIsExcelFile() {
        return file != null && file.getFileName() != null && file.getFileName().endsWith("xlsx");
    }

    public String getGsheeturl() {
        return gsheeturl;
    }

    public void setGsheeturl(String gsheeturl) {
        this.gsheeturl = gsheeturl;
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
        System.out.println("column selected: " + colIndex + ", sheet selected: " + sheetName);
        selectedColumnIndex = colIndex;
        selectedSheetName = sheetName;
        bulkData = false;
        System.out.println("function is: " + sessionBean.getFunction());
        return "/" + sessionBean.getFunction() + "/" + sessionBean.getFunction() + ".xhtml?faces-redirect=true";
    }

    public String launchAnalysisForTwoColumnsDataset() {
        bulkData = false;
        System.out.println("function is: " + sessionBean.getFunction());

        if (!twoColumnsColOneSelected || !twoColumnsColTwoSelected) {
            service.create(sessionBean.getLocaleBundle().getString("back.import.two_columns_needed"));
            addMessage(FacesMessage.SEVERITY_WARN, "😳", sessionBean.getLocaleBundle().getString("back.import.two_columns_needed"));
            return "";
        }

        if (countOfSelectedColOne != 1 || countOfSelectedColTwo != 1) {
            service.create(sessionBean.getLocaleBundle().getString("back.import.select_one_column_per_type"));
            addMessage(FacesMessage.SEVERITY_WARN, "😳", sessionBean.getLocaleBundle().getString("back.import.select_one_column_per_type"));
            return "";
        }

        if (twoColumnsIndexForColOne.equals(twoColumnsIndexForColTwo)) {
            sessionBean.getLocaleBundle().getString("back.import.term_text_different_columns");
            addMessage(FacesMessage.SEVERITY_WARN, "😳", sessionBean.getLocaleBundle().getString("back.import.term_text_different_columns"));
            return "";
        }

        return "/" + sessionBean.getFunction() + "/" + sessionBean.getFunction() + ".xhtml?faces-redirect=true";
    }

    public String gotToFunctionWithDataInBulk() {
        System.out.println("function is: " + sessionBean.getFunction());
        bulkData = true;
        return "/" + sessionBean.getFunction() + "/" + sessionBean.getFunction() + ".xhtml?faces-redirect=true";
    }

    public String goBackToLabellingPage(String colIndex, String sheetName) {
        if (datasetDescription == null || datasetDescription.isBlank()) {
            service.create(sessionBean.getLocaleBundle().getString("back.labelling_description_needed"));
            return "";
        }
        System.out.println("column selected: " + colIndex + ", sheet selected: " + sheetName);
        selectedColumnIndex = colIndex;
        selectedSheetName = sheetName;

        emailTaskDesigner = labellingBean.getEmailDesigner();
        String keyRawData = "task:" + taskId + ":rawdata";

        String typeOfTask = labellingBean.getTypeOfTask();

        String keyAnnotationCounterBwsScores = "task:" + taskId + ":annotations:indices:counter_bws";

        try ( Jedis jedis = SingletonBean.getJedisPool().getResource()) {
            if (bulkData) {
                for (SheetModel sm : dataInSheets) {
                    List<CellRecord> cellRecords = sm.getColumnIndexToCellRecords().get(0);
                    int i = 0;
                    for (CellRecord cr : cellRecords) {
                        if (hasHeaders && i++ == 0) {
                            //do nothing
                        } else {
                            jedis.lpush(keyRawData, cr.getRawValue());
                            // only for bws because this counts the + / - scores of BWS, which is specific to this task (not applicable to categorization)
                            if (typeOfTask.equals("bws")) {
                                jedis.lpush(keyAnnotationCounterBwsScores, "0");
                            }
                        }
                    }
                }
            } else {
                SheetModel sheetWithData = null;
                for (SheetModel sm : dataInSheets) {
                    if (sm.getName().equals(selectedSheetName)) {
                        sheetWithData = sm;
                        break;
                    }
                }
                if (sheetWithData == null) {
                    service.create(sessionBean.getLocaleBundle().getString("general.message.data_not_found"));
                    return "";
                }
                List<CellRecord> cellRecords = sheetWithData.getCellRecords();
                int selectedColAsInt = Integer.valueOf(selectedColumnIndex);
                int i = 0;
                for (CellRecord cr : cellRecords) {
                    if (cr.getColIndex() == selectedColAsInt) {
                        if (hasHeaders && i++ == 0) {
                            // do nothing
                        } else {
                            jedis.lpush(keyRawData, cr.getRawValue());
                            // only for bws because this counts the + / - scores of BWS, which is specific to this task (not applicable to categorization)
                            if (typeOfTask.equals("bws")) {
                                jedis.lpush(keyAnnotationCounterBwsScores, "0");
                            }
                        }
                    }
                }
            }
        }

        try ( Jedis jedis = SingletonBean.getJedisPool().getResource()) {
            // saving the metadata of the dataset just created
            TaskMetadata tmd = new TaskMetadata(taskId, typeOfTask);
            tmd.setDescription(datasetDescription);
            tmd.setName(datasetName);
            tmd.setEmailDesigner(emailTaskDesigner);
            String keyTask = "task:" + taskId;
            jedis.set(keyTask, tmd.produceJson());

            // add the id of the task to the designer key
            String cur = SCAN_POINTER_START;
            Iterator<String> iteratorScanResults;
            boolean found = false;
            do {
                ScanResult<String> scanResult = jedis.scan(cur);
                // work with result
                iteratorScanResults = scanResult.getResult().iterator();
                while (iteratorScanResults.hasNext()) {
                    String next = iteratorScanResults.next();
                    if (next.startsWith("designer") && next.contains(emailTaskDesigner)) {
                        String jsonTasks = jedis.get(next);
                        JsonReader jr = Json.createReader(new StringReader(jsonTasks));
                        JsonObject read = jr.readObject();
                        jr.close();
                        JsonObjectBuilder target = Json.createObjectBuilder();
                        read.forEach(target::add); // copy source into target
                        target.add(taskId, "{}"); // add or update values
                        JsonObject updatedJson = target.build(); // build destination
                        jedis.set(next, updatedJson.toString());
                        found = true;
                        break;
                    }
                }

                cur = scanResult.getCursor();
            } while (!cur.equals(SCAN_POINTER_START) & !found);

        }
        labellingBean.setTaskId(taskId);
        labellingBean.setEmailDesigner(emailTaskDesigner);

        return "/labelling/task_definition_" + typeOfTask + ".xhtml?dataset=" + taskId + "&faces-redirect=true";
    }

    public String goToAnalysisForCooccurrences(String sheetName) {
        System.out.println("sheet selected: " + sheetName);
        selectedColumnIndex = "0";
        selectedSheetName = sheetName;
        System.out.println("function is: " + sessionBean.getFunction());
        return "/" + sessionBean.getFunction() + "/" + sessionBean.getFunction() + ".xhtml?faces-redirect=true";
    }

    public List<SheetModel> getDataInSheets() {
        for (SheetModel sheet : dataInSheets) {
            sheet.setHasHeaders(hasHeaders);
        }
        return dataInSheets;
    }

    public void setDataInSheets(List<SheetModel> dataInSheets) {
        this.dataInSheets.addAll(dataInSheets);
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

    public String getPersistToDisk() {
        return persistToDisk;
    }

    public void setPersistToDisk(String persistToDisk) {
        if (persistToDisk == null) {
            System.out.println("persist param was null??");
            return;
        }
        if (persistToDisk.contains("=") & !persistToDisk.contains("?")) {
            System.out.println("weird url parameters decoded in persistToDisk");
            System.out.println("url param for persist To Disk is: " + persistToDisk);
            this.persistToDisk = persistToDisk.split("=")[1];
        } else if (persistToDisk.contains("=") & persistToDisk.contains("?")) {
            this.persistToDisk = persistToDisk.split("\\?")[0];
        } else {
            this.persistToDisk = persistToDisk;
        }
        if (this.persistToDisk.equals("true")) {
            taskId = UUID.randomUUID().toString().substring(0, 10);
        }

    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getEmailTaskDesigner() {
        return emailTaskDesigner;
    }

    public void setEmailTaskDesigner(String emailTaskDesigner) {
        this.emailTaskDesigner = emailTaskDesigner;
    }

    public String getDatasetDescription() {
        return datasetDescription;
    }

    public void setDatasetDescription(String datasetDescription) {
        this.datasetDescription = datasetDescription;
    }

    public String getDatasetName() {
        return datasetName;
    }

    public void setDatasetName(String datasetName) {
        this.datasetName = datasetName;
    }

    public void twoColumns(String colIndex, String dataInSheetName) {
        System.out.println("column selected: " + colIndex + ", sheet selected: " + dataInSheetName);
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
        if (file != null) {
            return "🚚 " + sessionBean.getLocaleBundle().getString("back.import.one_file_uploaded") + ": " + file.getFileName();
        }
        if (files != null) {
            if (files.getFiles().size() == 1) {
                return "🚚 " + sessionBean.getLocaleBundle().getString("back.import.one_file_uploaded") + ": " + files.getFiles().get(0).getFileName();
            } else {
                return "🚚 " + String.valueOf(files.getFiles().size()) + " " + sessionBean.getLocaleBundle().getString("back.import.files_uploaded");
            }
        } else {
            return sessionBean.getLocaleBundle().getString("general.message.data_not_found");
        }
    }

}
