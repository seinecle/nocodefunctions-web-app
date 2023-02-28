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
import jakarta.inject.Inject;
import jakarta.servlet.annotation.MultipartConfig;
import java.nio.charset.StandardCharsets;
import net.clementlevallois.importers.model.SheetModel;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.functions.UmigonBean;
import net.clementlevallois.nocodeapp.web.front.logview.NotificationService;

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
    SessionBean sessionBean;

    private Integer progress = 0;

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

    public String readData() throws IOException, URISyntaxException {
        dataInSheets = new ArrayList();
        progress = 0;
        if (filesUploaded.isEmpty()) {
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

        for (FileUploaded f : filesUploaded) {
            if (f == null) {
                continue;
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
                service.create(sessionBean.getLocaleBundle().getString("back.import.file_successful_upload.opening") + f.getFileName() + sessionBean.getLocaleBundle().getString("back.import.file_extension_not_recognized"));
                continue;
            }
            service.create(sessionBean.getLocaleBundle().getString("general.message.reading_file") + f.getFileName());
            switch (source) {
                case XLSX -> {
                    readExcelFile(f);
                }
                case TXT -> {
                    readTextFile(f, sessionBean.getFunction(), gazeOption);
                }
                case CSV ->
                    readCsvFile(f, sessionBean.getFunction(), gazeOption);
                case PDF ->
                    readPdfFile(f);
                default -> {
                }
            }
        }
        executor.shutdown();
        progress = 100;
        service.create(sessionBean.getLocaleBundle().getString("general.message.finished_reading_data"));

        filesUploaded = new ArrayList();
        gsheeturl = null;

        renderCloseOverlay = true;
        return "";
    }

    private void readTextFile(FileUploaded f, String functionName, String gazeOption) {

        try {
            if (functionName.equals("gaze") && gazeOption.equals("1")) {
                setSelectedColumnIndex("0");
                setSelectedSheetName(f.getFileName());
            }

            HttpRequest request;
            HttpClient client = HttpClient.newHttpClient();
            Set<CompletableFuture> futures = new HashSet();

            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(f.getInputStream().readAllBytes());

            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(7003)
                    .withHost("localhost")
                    .withPath("api/import/txt")
                    .addParameter("fileName", f.getFileName())
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

    private void readPdfFile(FileUploaded f) {
        try {
            String localizedEmptyLineMessage = sessionBean.getLocaleBundle().getString("general.message.empty_line");

            HttpRequest request;
            HttpClient client = HttpClient.newHttpClient();
            Set<CompletableFuture> futures = new HashSet();

            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(f.getInputStream().readAllBytes());

            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(7003)
                    .withHost("localhost")
                    .withPath("api/import/pdf")
                    .addParameter("fileName", f.getFileName())
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

    private void readCsvFile(FileUploaded f, String functionName, String gazeOption) {
        try {

            /* since we DONT do a bulk import in a CSV import,
            we have no concept of "selected sheet" among several sheets.
            we need to set the file name of the unique file for a sheet name
             */
            setSelectedSheetName(f.getFileName());

            // for co-occurrences, we consider all columns, starting from column zero.
            if (functionName.equals("gaze") && gazeOption.equals("1")) {
                setSelectedColumnIndex("0");
            }

            HttpRequest request;
            HttpClient client = HttpClient.newHttpClient();
            Set<CompletableFuture> futures = new HashSet();

            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(f.getInputStream().readAllBytes());

            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(7003)
                    .withHost("localhost")
                    .withPath("api/import/csv")
                    .addParameter("fileName", f.getFileName())
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
        selectedColumnIndex = colIndex;
        selectedSheetName = sheetName;
        bulkData = false;
        return "/" + sessionBean.getFunction() + "/" + sessionBean.getFunction() + ".xhtml?faces-redirect=true";
    }

    public String launchAnalysisForTwoColumnsDataset() {
        bulkData = false;
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
        bulkData = true;
        return "/" + sessionBean.getFunction() + "/" + sessionBean.getFunction() + ".xhtml?faces-redirect=true";
    }

    public String goToAnalysisForCooccurrences(String sheetName) {
        selectedColumnIndex = "0";
        selectedSheetName = sheetName;
        return "/" + sessionBean.getFunction() + "/" + sessionBean.getFunction() + ".xhtml?faces-redirect=true";
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
                return "🚚 " + sessionBean.getLocaleBundle().getString("back.import.one_file_uploaded") + ": " + filesUploaded.get(0).getFileName();
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

}
