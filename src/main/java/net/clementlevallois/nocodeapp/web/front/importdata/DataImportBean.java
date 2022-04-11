/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.importdata;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor;
import com.itextpdf.kernel.pdf.canvas.parser.listener.SimpleTextExtractionStrategy;
import com.monitorjbl.xlsx.StreamingReader;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import javax.inject.Named;
import java.io.Serializable;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import static java.util.stream.Collectors.toList;
import javax.enterprise.context.SessionScoped;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
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
import net.clementlevallois.utils.StatusCleaner;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
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
    GoogleSheetsImportBean googleBean;

    @Inject
    TwitterImportBean twitterBean;

    @Inject
    GazeBean gazeBean;

    @Inject
    LabellingBean labellingBean;

    @Inject
    SessionBean sessionBean;

    private Integer progress;

    private UploadedFile file;
    private UploadedFiles files;

    private Boolean isExcelFile = false;

    private Boolean isGSheet = false;
    private String gsheeturl;

    private Boolean isTxtFile = false;
    private Boolean isCsvFile = false;
    private Boolean isPdfFile = false;

    private Boolean isTwitter = false;

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
        TWITTER,
        PDF,
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

            service.create(sessionBean.getLocaleBundle().getString("general.message.reading_pdf_file"));
            dataInSheets.addAll(readPdfFile(file));
        } else if (files != null) {
            for (UploadedFile f : files.getFiles()) {
                if (f != null && f.getFileName().endsWith("pdf")) {
                    isPdfFile = true;
                    service.create(sessionBean.getLocaleBundle().getString("general.message.reading_file") + f.getFileName());
                    dataInSheets.addAll(readPdfFile(f));
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

    public String readData() {
        dataInSheets = new ArrayList();
        if (file == null && gsheeturl == null && files == null) {
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

        if (gsheeturl != null && !gsheeturl.isBlank()) {
            service.create(sessionBean.getLocaleBundle().getString("general.message.reading_google_spreadsheet"));
            String spreadsheetId = extractFromUrl(gsheeturl);
            dataInSheets = googleBean.readGSheetData(spreadsheetId);
        } else if (file != null) {
            if (isExcelFile) {
                service.create(sessionBean.getLocaleBundle().getString("general.message.reading_excel_file"));
                try {
                    dataInSheets.addAll(readExcelFile(file));
                } catch (IOException ex) {
                    Logger.getLogger(DataImportBean.class.getName()).log(Level.SEVERE, null, ex);
                }
            } else if (isTxtFile) {
                service.create(sessionBean.getLocaleBundle().getString("general.message.reading_text_file"));
                dataInSheets.addAll(readTextFile(file));
            } else if (isCsvFile) {
                service.create(sessionBean.getLocaleBundle().getString("general.message.reading_csv_file"));
                dataInSheets.addAll(readCsvFile(file));
            } else if (isPdfFile) {
                service.create(sessionBean.getLocaleBundle().getString("general.message.reading_pdf_file"));
                dataInSheets.addAll(readPdfFile(file));
            }
        } else if (files != null) {
            for (UploadedFile f : files.getFiles()) {
                isExcelFile = false;
                isTxtFile = false;
                isCsvFile = false;
                isPdfFile = false;
                isGSheet = false;

                if (f != null && f.getFileName().endsWith("xlsx")) {
                    isExcelFile = true;
                } else if (f != null && f.getFileName().endsWith("txt")) {
                    isTxtFile = true;
                } else if (f != null && f.getFileName().endsWith("csv")) {
                    isCsvFile = true;
                } else if (f != null && f.getFileName().endsWith("pdf")) {
                    isPdfFile = true;
                }
                service.create(sessionBean.getLocaleBundle().getString("general.message.reading_file") + f.getFileName());

                if (isExcelFile) {
                    try {
                        dataInSheets.addAll(readExcelFile(f));
                    } catch (IOException ex) {
                        Logger.getLogger(DataImportBean.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else if (isTxtFile) {
                    dataInSheets.addAll(readTextFile(f));
                } else if (isCsvFile) {
                    dataInSheets.addAll(readCsvFile(f));
                } else if (isPdfFile) {
                    dataInSheets.addAll(readPdfFile(f));
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

    public String searchTweets() {
        this.progress = 3;
        service.create(sessionBean.getLocaleBundle().getString("back.import.searching_tweets"));

        Runnable incrementProgress = () -> {
            progress = progress + 1;
        };
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(incrementProgress, 0, 250, TimeUnit.MILLISECONDS);

        dataInSheets = twitterBean.searchTweets();
        executor.shutdown();
        progress = 100;
        service.create(sessionBean.getLocaleBundle().getString("back.import.finished_searching_tweets") + dataInSheets.get(0).getCellRecords().size() + sessionBean.getLocaleBundle().getString("back.import.number_tweets_found"));
        return "";
    }

    public String handleFileUpload(FileUploadEvent event) {
        progress = 0;
        file = event.getFile();
        service.create(sessionBean.getLocaleBundle().getString("back.import.file_successful_upload.opening") + file.getFileName() + sessionBean.getLocaleBundle().getString("back.import.file_successful_upload.closing"));
        isExcelFile = false;
        isTxtFile = false;
        isCsvFile = false;
        isPdfFile = false;
        isGSheet = false;
        if (file != null && file.getFileName().endsWith("xlsx")) {
            isExcelFile = true;
            setSource(Source.XLSX);
        } else if (file != null && file.getFileName().endsWith("txt")) {
            isTxtFile = true;
            setSource(Source.TXT);
        } else if (file != null && file.getFileName().endsWith("csv")) {
            isCsvFile = true;
            setSource(Source.CSV);
        } else if (file != null && file.getFileName().endsWith("pdf")) {
            isPdfFile = true;
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
        isExcelFile = false;
        isTxtFile = false;
        isCsvFile = false;
        isTwitter = false;
        isGSheet = true;
        file = null;
        dataInSheets = new ArrayList();
        renderProgressBar = true;
        setSource(Source.GS);
    }

    public void chooseTwitter(ActionEvent event) {
        isExcelFile = false;
        isTxtFile = false;
        isCsvFile = false;
        isTwitter = true;
        isGSheet = false;
        file = null;
        dataInSheets = new ArrayList();
        renderProgressBar = true;
        setSource(Source.TWITTER);
    }

    private List<SheetModel> readTextFile(UploadedFile file) {
        Map<Integer, String> lines = new TreeMap();
        List<SheetModel> sheets = new ArrayList();
        try {
            Runnable incrementProgress = () -> {
                progress = progress + 1;
            };
            ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
            executor.scheduleAtFixedRate(incrementProgress, 0, 250, TimeUnit.MILLISECONDS);

            // if we are computing cooccurrences, we need the lines of text to be decomposed as a csv file.
            if (sessionBean.getFunction().equals("gaze") && gazeBean != null && gazeBean.getOption().equals("1")) {
                CsvParserSettings settings = new CsvParserSettings();
                settings.detectFormatAutomatically();
                settings.setMaxCharsPerColumn(-1);
                CsvParser parser = new CsvParser(settings);
                InputStreamReader reader;
                reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
                List<String[]> rows = parser.parseAll(reader);
                SheetModel sheetModel = new SheetModel();
                sheetModel.setName(file.getFileName());
                ColumnModel cm;
                List<ColumnModel> headerNames = new ArrayList();
                String[] firstLine = rows.get(0);
                int h = 0;
                for (String header : firstLine) {
                    header = Jsoup.clean(header, Safelist.basicWithImages().addAttributes("span", "style"));
                    cm = new ColumnModel(String.valueOf(h++), header.trim());
                    headerNames.add(cm);
                }

                sheetModel.setTableHeaderNames(headerNames);
                int j = 0;
                for (String[] row : rows) {
                    int i = 0;
                    for (String field : row) {
                        field = Jsoup.clean(field, Safelist.basicWithImages().addAttributes("span", "style"));
                        CellRecord cellRecord = new CellRecord(j, i++, field.trim());
                        sheetModel.addCellRecord(cellRecord);
                    }
                    j++;
                }
                sheets.add(sheetModel);
                setSelectedColumnIndex("0");
                setSelectedSheetName(file.getFileName());

            } // normal case of importing text as flat lines without caring for cooccurrences
            else {
                List<String> txtLines;
                BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
                txtLines = br.lines().collect(toList());
                br.close();
                int i = 0;
                for (String line : txtLines) {
                    line = Jsoup.clean(line, Safelist.basicWithImages().addAttributes("span", "style"));
                    lines.put(i++, line);
                }
                SheetModel sheetModel = new SheetModel();
                sheetModel.setName(file.getFileName());
                ColumnModel cm;
                cm = new ColumnModel("0", lines.get(0));
                List<ColumnModel> headerNames = new ArrayList();
                headerNames.add(cm);
                sheetModel.setTableHeaderNames(headerNames);
                for (Map.Entry<Integer, String> line : lines.entrySet()) {
                    CellRecord cellRecord = new CellRecord(line.getKey(), 0, line.getValue());
                    sheetModel.addCellRecord(cellRecord);
                }
                sheets.add(sheetModel);
            }
            executor.shutdown();
            this.progress = 100;
        } catch (IOException ex) {
            Logger.getLogger(DataImportBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        return sheets;
    }

    private List<SheetModel> readPdfFile(UploadedFile file) {
        Map<Integer, String> lines = new TreeMap();
        List<SheetModel> sheets = new ArrayList();
        try {
            Runnable incrementProgress = () -> {
                progress = progress + 1;
            };
            ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
            executor.scheduleAtFixedRate(incrementProgress, 0, 250, TimeUnit.MILLISECONDS);

            SheetModel sheetModel = new SheetModel();
            sheetModel.setName(file.getFileName());

            PdfDocument myDocument = new PdfDocument(new PdfReader(file.getInputStream()));
            int numberOfPages = myDocument.getNumberOfPages();
            int pageNumber;
            int i = 0;
            for (pageNumber = 1; pageNumber <= numberOfPages; pageNumber++) {
                sheetModel.getPageAndStartingLine().put(pageNumber,i);
                String textInDoc = PdfTextExtractor.getTextFromPage(myDocument.getPage(pageNumber), new SimpleTextExtractionStrategy());
                String linesArray[] = textInDoc.split("\\r?\\n");
                for (String line : linesArray) {
                    line = Jsoup.clean(line, Safelist.basicWithImages().addAttributes("span", "style"));
                    if (!line.isBlank()) {
                        lines.put(i++, line.toLowerCase());
                    }
                }
            }
            lines = StatusCleaner.doAllCleaningOps(lines);
            ColumnModel cm;
            cm = new ColumnModel("0", lines.get(0));
            List<ColumnModel> headerNames = new ArrayList();
            headerNames.add(cm);
            sheetModel.setTableHeaderNames(headerNames);
            for (Map.Entry<Integer, String> line : lines.entrySet()) {
                CellRecord cellRecord = new CellRecord(line.getKey(), 0, line.getValue());
                sheetModel.addCellRecord(cellRecord);
            }
            sheets.add(sheetModel);

            executor.shutdown();
            this.progress = 100;
        } catch (IOException ex) {
            Logger.getLogger(DataImportBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        return sheets;
    }

    private List<SheetModel> readCsvFile(UploadedFile file) {
        InputStreamReader reader;
        List<SheetModel> sheets = new ArrayList();
        try {
            Runnable incrementProgress = () -> {
                progress = progress + 1;
            };
            ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
            executor.scheduleAtFixedRate(incrementProgress, 0, 250, TimeUnit.MILLISECONDS);
            CsvParserSettings settings = new CsvParserSettings();
            settings.detectFormatAutomatically();
            settings.setMaxCharsPerColumn(-1);
            CsvParser parser = new CsvParser(settings);
            reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
            List<String[]> rows = parser.parseAll(reader);
            // if you want to see what delimiter it detected
//            CsvFormat format = parser.getDetectedFormat();
            SheetModel sheetModel = new SheetModel();
            sheetModel.setName(file.getFileName());
            ColumnModel cm;
            List<ColumnModel> headerNames = new ArrayList();
            String[] firstLine = rows.get(0);
            int h = 0;
            for (String header : firstLine) {
                header = Jsoup.clean(header, Safelist.basicWithImages().addAttributes("span", "style"));
                cm = new ColumnModel(String.valueOf(h++), header);
                headerNames.add(cm);
            }

            // since we DONT do a bulk import in a CSV import,
            // we have no concept of "selected sheet" among several sheets.
            // we need to set the file name of the unique file for a sheet name
            setSelectedSheetName(file.getFileName());

            // for co-occurrences, we consider all columns, starting from column zero.
            if (sessionBean.getFunction().equals("gaze") && gazeBean != null && gazeBean.getOption().equals("1")) {
                setSelectedColumnIndex("0");
            }
            sheetModel.setTableHeaderNames(headerNames);
            int j = 0;
            for (String[] row : rows) {
                int i = 0;
                for (String field : row) {
                    field = Jsoup.clean(field, Safelist.basicWithImages().addAttributes("span", "style"));
                    CellRecord cellRecord = new CellRecord(j, i++, field);
                    sheetModel.addCellRecord(cellRecord);
                }
                j++;
            }
            sheets.add(sheetModel);
            executor.shutdown();
            this.progress = 100;
        } catch (IOException ex) {
            Logger.getLogger(DataImportBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        return sheets;
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
                    setSelectedSheetName(file.getFileName()+"_"+ sheet.getSheetName());
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

    public void setIsExcelFile(Boolean isExcelFile) {
        this.isExcelFile = isExcelFile;
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

    public Boolean getIsGSheet() {
        System.out.println("is gsheet true?" + isGSheet);
        return isGSheet;
    }

    public void setIsGSheet(Boolean isGSheet) {
        this.isGSheet = isGSheet;
    }

    public Boolean getIsTwitter() {
        return isTwitter;
    }

    public void setIsTwitter(Boolean isTwitter) {
        this.isTwitter = isTwitter;
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
