/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.importdata;

import io.mikael.urlbuilder.UrlBuilder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import jakarta.inject.Named;
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
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.servlet.annotation.MultipartConfig;
import java.nio.charset.StandardCharsets;
import net.clementlevallois.importers.model.CellRecord;
import net.clementlevallois.importers.model.SheetModel;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonBean;
import net.clementlevallois.nocodeapp.web.front.redisops.TaskMetadata;
import net.clementlevallois.nocodeapp.web.front.functions.GazeBean;
import net.clementlevallois.nocodeapp.web.front.functions.LabellingBean;
import net.clementlevallois.nocodeapp.web.front.functions.UmigonBean;
import net.clementlevallois.nocodeapp.web.front.logview.NotificationService;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;
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
    LabellingBean labellingBean;

    @Inject
    SessionBean sessionBean;

    private Integer progress;

    private UploadedFile file;

    private FileUploaded fileUploaded;
    private List<FileUploaded> filesUploaded = new ArrayList();

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

    public String handleFileUpload(FileUploadEvent event) {
        try {
            dataInSheets = new ArrayList();
            progress = 0;
            file = event.getFile();
            if (file == null) {
                return "";
            }
            fileUploaded = new FileUploaded(file.getInputStream(), file.getFileName());
            service.create(sessionBean.getLocaleBundle().getString("back.import.file_successful_upload.opening") + file.getFileName() + sessionBean.getLocaleBundle().getString("back.import.file_successful_upload.closing"));

            if (fileUploaded.getFileName().endsWith("xlsx")) {
                setSource(Source.XLSX);
            } else if (fileUploaded.getFileName().endsWith("txt")) {
                setSource(Source.TXT);
            } else if (fileUploaded.getFileName().endsWith("csv") || fileUploaded.getFileName().endsWith("tsv")) {
                setSource(Source.CSV);
            } else if (fileUploaded.getFileName().endsWith("pdf")) {
                setSource(Source.PDF);
            } else {
                service.create(sessionBean.getLocaleBundle().getString("back.import.file_successful_upload.opening") + file.getFileName() + sessionBean.getLocaleBundle().getString("back.import.file_extension_not_recognized"));
                return "";
            }
            readButtonDisabled = false;
            renderProgressBar = true;
        } catch (IOException ex) {
            Logger.getLogger(DataImportBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "";
    }

    public String readData() throws IOException, URISyntaxException {
        dataInSheets = new ArrayList();
        if (fileUploaded == null && filesUploaded == null) {
            service.create(sessionBean.getLocaleBundle().getString("general.message.no_file_upload_again"));
            addMessage(FacesMessage.SEVERITY_WARN, "💔", sessionBean.getLocaleBundle().getString("general.message.no_file_upload_again"));
            return "";
        }

        String gazeOption = sessionBean.getGazeOption();

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
        if (fileUploaded != null) {
            InputStream is = fileUploaded.getInputStream();
            String fileName = fileUploaded.getFileName();
            this.progress = 3;
            switch (source) {
                case XLSX -> {
                    service.create(sessionBean.getLocaleBundle().getString("general.message.reading_excel_file"));
                    readExcelFile(fileUploaded);
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
        } else if (filesUploaded != null) {
            for (FileUploaded f : filesUploaded) {
                if (f == null) {
                    return "";
                }
                if (f.getFileName().endsWith("xlsx")) {
                    source = Source.XLSX;
                } else if (f.getFileName().endsWith("txt")) {
                    source = Source.TXT;
                } else if (f.getFileName().endsWith("csv") || f.getFileName().endsWith("tsv")) {
                    source = Source.CSV;
                } else if (f.getFileName().endsWith("pdf")) {
                    source = Source.PDF;
                } else {
                    service.create(sessionBean.getLocaleBundle().getString("back.import.file_successful_upload.opening") + file.getFileName() + sessionBean.getLocaleBundle().getString("back.import.file_extension_not_recognized"));
                    continue;
                }
                InputStream is = f.getInputStream();
                String fileName = f.getFileName();
                service.create(sessionBean.getLocaleBundle().getString("general.message.reading_file") + f.getFileName());
                switch (source) {
                    case XLSX -> {
                        readExcelFile(f);
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
            }
        }
        executor.shutdown();
        progress = 100;
        service.create(sessionBean.getLocaleBundle().getString("general.message.finished_reading_data"));

        fileUploaded = null;
        file = null;
        gsheeturl = null;

        renderCloseOverlay = true;
        return "";
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
                        ByteArrayInputStream bis = new ByteArrayInputStream(body); ObjectInputStream ois = new ObjectInputStream(bis)) {
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
            System.out.println("ex:" + ex.getMessage());
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
                        ByteArrayInputStream bis = new ByteArrayInputStream(body); ObjectInputStream ois = new ObjectInputStream(bis)) {
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
            System.out.println("ex:" + ex.getMessage());
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
                        ByteArrayInputStream bis = new ByteArrayInputStream(body); ObjectInputStream ois = new ObjectInputStream(bis)) {
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
            System.out.println("ex:" + ex.getMessage());
        }
    }

    private void readExcelFile(FileUploaded file) {
        try {
            InputStream is = file.getInputStream();
            HttpRequest request;
            HttpClient client = HttpClient.newHttpClient();
            Set<CompletableFuture> futures = new HashSet();
            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(is.readAllBytes());

            String gaze_option = "cooc";

            if (sessionBean.getGazeOption().equals("2")) {
                gaze_option = "sim";
            }

            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(7003)
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
                    System.out.println("problem with the reading of excel file:");
                    System.out.println(new String(body, StandardCharsets.UTF_8));
                }

            }
            );
            futures.add(future);
            CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
            combinedFuture.join();

            if (sessionBean.getGazeOption().equals("1")) {
                setSelectedColumnIndex("0");
                setSelectedSheetName(file.getFileName() + "_");
            }

        } catch (IOException ex) {
            Logger.getLogger(DataImportBean.class.getName()).log(Level.SEVERE, null, ex);
        }
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

    public Boolean getIsExcelFile() {
        return fileUploaded != null && fileUploaded.getFileName() != null && fileUploaded.getFileName().endsWith("xlsx");
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

        try (Jedis jedis = SingletonBean.getJedisPool().getResource()) {
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
                int selectedColAsInt = Integer.parseInt(selectedColumnIndex);
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

        try (Jedis jedis = SingletonBean.getJedisPool().getResource()) {
            // saving the metadata of the dataset just created
            TaskMetadata tmd = new TaskMetadata(taskId, typeOfTask, jedis);
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
        if (fileUploaded != null) {
            return "🚚 " + sessionBean.getLocaleBundle().getString("back.import.one_file_uploaded") + ": " + fileUploaded.getFileName();
        }
        if (filesUploaded != null) {
            if (filesUploaded.size() == 1) {
                return "🚚 " + sessionBean.getLocaleBundle().getString("back.import.one_file_uploaded") + ": " + filesUploaded.get(0).getFileName();
            } else {
                return "🚚 " + String.valueOf(filesUploaded.size()) + " " + sessionBean.getLocaleBundle().getString("back.import.files_uploaded");
            }
        } else {
            return sessionBean.getLocaleBundle().getString("general.message.data_not_found");
        }
    }

    public FileUploaded getFileUploaded() {
        return fileUploaded;
    }

    public void setFileUploaded(FileUploaded fileUploaded) {
        this.fileUploaded = fileUploaded;
    }

    public List<FileUploaded> getFilesUploaded() {
        return filesUploaded;
    }

    public void setFilesUploaded(List<FileUploaded> filesUploaded) {
        this.filesUploaded = filesUploaded;
    }

}
