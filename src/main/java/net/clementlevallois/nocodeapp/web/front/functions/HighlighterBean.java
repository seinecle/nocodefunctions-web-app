/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.functions;

import io.mikael.urlbuilder.UrlBuilder;
import java.io.FileNotFoundException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.importers.model.CellRecord;
import net.clementlevallois.importers.model.SheetModel;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;

import net.clementlevallois.nocodeapp.web.front.logview.NotificationService;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped

public class HighlighterBean implements Serializable {

    @Inject
    NotificationService service;

    @Inject
    DataImportBean inputData;

    @Inject
    SessionBean sessionBean;

    private String colorText = "ffffff";
    private String colorBackground = "e81e5b";

    private StreamedContent fileToSave;

    public HighlighterBean() {
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

    public void doTheHighlight() throws FileNotFoundException {
        try {
            String sheetName = inputData.getSelectedSheetName();
            String termCol = inputData.getTwoColumnsIndexForColOne();
            String contextCol = inputData.getTwoColumnsIndexForColTwo();
            service.create(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
            List<SheetModel> dataInSheets = inputData.getDataInSheets();
            SheetModel sheetWithData = null;
            for (SheetModel sm : dataInSheets) {
                if (sm.getName().equals(sheetName)) {
                    sheetWithData = sm;
                    break;
                }
            }
            if (sheetWithData == null) {
                service.create(sessionBean.getLocaleBundle().getString("general.message.data_not_found") + " (1)");
                return;
            }
            Map<Integer, List<CellRecord>> mapOfCellRecordsPerRow = sheetWithData.getRowIndexToCellRecords();
            if (inputData.getHasHeaders()) {
                mapOfCellRecordsPerRow.remove(0);
            }
            Iterator<Map.Entry<Integer, List<CellRecord>>> iterator = mapOfCellRecordsPerRow.entrySet().iterator();

            String term = "";
            String context = "";
            int termIndexInt = Integer.parseInt(termCol);
            int contextIndexInt = Integer.parseInt(contextCol);

            List<String[]> results = new ArrayList();
            String[] result;

            while (iterator.hasNext()) {
                Map.Entry<Integer, List<CellRecord>> entryCellRecordsInRow = iterator.next();
                for (CellRecord cr : entryCellRecordsInRow.getValue()) {
                    if (cr.getColIndex() == termIndexInt) {
                        term = cr.getRawValue();
                    }
                    if (cr.getColIndex() == contextIndexInt) {
                        context = cr.getRawValue();
                    }
                }
                if (context != null && term != null && context.toLowerCase().contains(term.toLowerCase())) {
                    String formattedTerm = "<span style=\"background-color: #" + colorBackground + "; color: #" + colorText + ";\">" + term + "</span>";
                    context = context.toLowerCase().replace(term.toLowerCase(), formattedTerm);
                    result = new String[]{term, context};
                    results.add(result);
                }
            }

            HttpRequest request;
            HttpClient client = HttpClient.newHttpClient();
            Set<CompletableFuture> futures = new HashSet();
            byte[] documentsAsByteArray = Converters.byteArraySerializerForAnyObject(results);
            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(documentsAsByteArray);

            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(7003)
                    .withHost("localhost")
                    .withPath("api/export/xlsx/highlighter")
                    .toUri();

            request = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .uri(uri)
                    .build();

            CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(resp -> {
                byte[] body = resp.body();
                InputStream is = new ByteArrayInputStream(body);
                fileToSave = DefaultStreamedContent.builder()
                        .name("results.xlsx")
                        .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .stream(() -> is)
                        .build();
            }
            );
            futures.add(future);

            CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
            combinedFuture.join();
            service.create(sessionBean.getLocaleBundle().getString("general.message.analysis_complete"));

        } catch (IOException ex) {
            Logger.getLogger(HighlighterBean.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
