package net.clementlevallois.nocodeapp.web.front.functions;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.CompletionException;
import jakarta.faces.application.FacesMessage;
import java.util.concurrent.CompletableFuture;
import net.clementlevallois.functions.model.FunctionHighlighter;
import net.clementlevallois.importers.model.CellRecord;
import net.clementlevallois.importers.model.SheetModel;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient.MicroserviceCallException;


import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

@Named
@SessionScoped
public class HighlighterBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(HighlighterBean.class.getName());

    @Inject
    BackToFrontMessengerBean logBean;

    @Inject
    DataImportBean inputData;

    @Inject
    SessionBean sessionBean;

    @Inject
    private MicroserviceHttpClient microserviceClient;

    private String colorText = "#ffffff";
    private String colorBackground = "#e81e5b";

    private StreamedContent fileToSave;

    public HighlighterBean() {
    }

    @PostConstruct
    public void init() {
        sessionBean.setFunctionName(FunctionHighlighter.NAME);
    }

    public void onload() {
    }

    public String getColorText() {
        return colorText;
    }

    public void setColorText(String colorText) {
        this.colorText = colorText;
    }

    public String getColorBackground() {
        return colorBackground;
    }

    public void setColorBackground(String colorBackground) {
        this.colorBackground = colorBackground;
    }

    public StreamedContent getFileToSave() {
        return fileToSave;
    }

    public void setFileToSave(StreamedContent fileToSave) {
        this.fileToSave = fileToSave;
    }

    public void doTheHighlight() {
        logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));

        try {
            String sheetName = inputData.getSelectedSheetName();
            String termCol = inputData.getTwoColumnsIndexForColOne();
            String contextCol = inputData.getTwoColumnsIndexForColTwo();

            if (sheetName == null || termCol == null || contextCol == null) {
                 sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Input Error", "Please select a sheet and two columns.");
                 logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.data_not_found"));
                 return;
            }

            List<SheetModel> dataInSheets = inputData.getDataInSheets();
            SheetModel sheetWithData = null;
            for (SheetModel sm : dataInSheets) {
                if (sm.getName().equals(sheetName)) {
                    sheetWithData = sm;
                    break;
                }
            }
            if (sheetWithData == null) {
                sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "Input Error", "Selected sheet not found.");
                logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.data_not_found") + " (1)");
                return;
            }

            Map<Integer, List<CellRecord>> mapOfCellRecordsPerRow = sheetWithData.getRowIndexToCellRecords();
            if (inputData.getHasHeaders()) {
                mapOfCellRecordsPerRow.remove(0);
            }

            int termIndexInt = Integer.parseInt(termCol);
            int contextIndexInt = Integer.parseInt(contextCol);

            List<String[]> results = new ArrayList();
            Iterator<Map.Entry<Integer, List<CellRecord>>> iterator = mapOfCellRecordsPerRow.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<Integer, List<CellRecord>> entryCellRecordsInRow = iterator.next();
                String term = null;
                String context = null;

                for (CellRecord cr : entryCellRecordsInRow.getValue()) {
                    if (cr.getColIndex() == termIndexInt) {
                        term = cr.getRawValue();
                    }
                    if (cr.getColIndex() == contextIndexInt) {
                        context = cr.getRawValue();
                    }
                }

                if (context != null && term != null && context.toLowerCase().contains(term.toLowerCase())) {
                    String formattedTerm = "<span style=\"background-color: " + colorBackground + "; color: " + colorText + ";\">" + term + "</span>";
                    context = context.toLowerCase().replace(term.toLowerCase(), formattedTerm);
                    results.add(new String[]{term, context});
                }
            }

            if (results.isEmpty()) {
                 sessionBean.addMessage(FacesMessage.SEVERITY_INFO, "No Matches", "No terms found in context for highlighting.");
                 logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.analysis_complete") + " (no matches)");
                 fileToSave = new DefaultStreamedContent(); // Provide empty content
                 return;
            }

            byte[] documentsAsByteArray = Converters.byteArraySerializerForAnyObject(results);

            // Use MicroserviceHttpClient to call the export service
            CompletableFuture<byte[]> futureBytes = microserviceClient.importService().post("/api/export/xlsx/highlighter")
                 .withByteArrayPayload(documentsAsByteArray)
                 .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofByteArray());

            // Block to get the result for StreamedContent
            byte[] body = futureBytes.join();

            try (InputStream is = new ByteArrayInputStream(body)) {
                fileToSave = DefaultStreamedContent.builder()
                        .name("results.xlsx")
                        .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .stream(() -> is)
                        .build();

                logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.analysis_complete"));
                sessionBean.addMessage(FacesMessage.SEVERITY_INFO, "Success", "Analysis complete. Download ready.");

            } catch (IOException e) {
                 LOG.log(Level.SEVERE, "Error creating StreamedContent from response body", e);
                 sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Download Error", "Could not prepare download file.");
            }

        } catch (NumberFormatException ex) {
            LOG.log(Level.SEVERE, "Error parsing column index", ex);
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Input Error", "Invalid column index format.");
        } catch (CompletionException cex) {
            Throwable cause = cex.getCause();
            LOG.log(Level.SEVERE, "Error during asynchronous export service call (CompletionException)", cause);
            String errorMessage = "Error exporting data: " + cause.getMessage();
            if (cause instanceof MicroserviceCallException msce) {
                 errorMessage = "Error exporting data: Status " + msce.getStatusCode() + ", " + msce.getErrorBody();
            }
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Export Failed", errorMessage);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Error during data processing or export call", ex);
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Analysis Error", "An error occurred during processing: " + ex.getMessage());
        } catch (Exception ex) {
             LOG.log(Level.SEVERE, "Unexpected error in doTheHighlight", ex);
             sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Analysis Error", "An unexpected error occurred: " + ex.getMessage());
        }
    }
}
