/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import net.clementlevallois.functions.model.Occurrence;
import net.clementlevallois.umigon.model.Document;
import net.clementlevallois.utils.Multiset;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

/**
 *
 * @author LEVALLOIS
 */
public class ExcelSaver {

    public static StreamedContent exportUmigon(List<Document> results, ResourceBundle localeBundle) {
        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet sheet = wb.createSheet("results");
        int rowNumber = 0;
        // creating the header
        Row rowHeader = sheet.createRow(rowNumber++);
        Cell cell0Header = rowHeader.createCell(0, CellType.STRING);
        cell0Header.setCellValue(localeBundle.getString("general.nouns.line_number"));
        Cell cell1Header = rowHeader.createCell(1, CellType.STRING);
        cell1Header.setCellValue(localeBundle.getString("general.message.text_provided_as_input"));
        Cell cell2Header = rowHeader.createCell(2, CellType.STRING);
        cell2Header.setCellValue(localeBundle.getString("general.nouns.sentiment"));
        Cell cell3Header = rowHeader.createCell(3, CellType.STRING);
        cell3Header.setCellValue("language");
        Cell cell4Header = rowHeader.createCell(4, CellType.STRING);
        cell4Header.setCellValue(localeBundle.getString("general.nouns.explanations"));

        for (Document doc : results) {
            if (doc == null) {
                continue;
            }
            Row row = sheet.createRow(rowNumber++);
            Cell cell0 = row.createCell(0, CellType.STRING);
            cell0.setCellValue(String.valueOf(rowNumber));
            Cell cell1 = row.createCell(1, CellType.STRING);
            if (doc.getText() != null) {
                cell1.setCellValue(doc.getText());
            }
            Cell cell2 = row.createCell(2, CellType.STRING);
            String sentiment;
            if (doc.getCategoryCode() == null) {
                System.out.println("no category code for this doc");
                continue;
            }
            switch (doc.getCategoryCode()) {
                case "_12":
                    sentiment = "😔 " + doc.getCategoryLocalizedPlainText();
                    break;
                case "_11":
                    sentiment = "🤗 " + doc.getCategoryLocalizedPlainText();
                    break;
                default:
                    sentiment = "😐 " + doc.getCategoryLocalizedPlainText();
                    break;
            }

            cell2.setCellValue(sentiment);
            Cell cell3 = row.createCell(3, CellType.STRING);
            if (doc.getLanguage() != null) {
                cell3.setCellValue(doc.getLanguage());
            }
            Cell cell4 = row.createCell(4, CellType.STRING);
            cell4.setCellValue(doc.getExplanationPlainText());
        }
        StreamedContent file = null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            byte[] barray = bos.toByteArray();
            InputStream is = new ByteArrayInputStream(barray);
            file = DefaultStreamedContent.builder()
                    .name("results.xlsx")
                    .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .stream(() -> is)
                    .build();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }

    public static StreamedContent exportTopics(Map<Integer, Multiset<String>> communitiesResult, int termsPerCommunity) {
        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet sheet = wb.createSheet("results");
        int rowNumber = 0;
        // creating the rows
        for (int r = 0; r <= termsPerCommunity; r++) {
            sheet.createRow(r);
        }

        for (Map.Entry<Integer, Multiset<String>> entry : communitiesResult.entrySet()) {
            Integer communityNumber = entry.getKey();
            Multiset<String> termsInOneCommunity = entry.getValue();
            Cell cellHeader = sheet.getRow(0).createCell(communityNumber, CellType.STRING);
            cellHeader.setCellValue("Topic " + communityNumber);
            List<Map.Entry<String, Integer>> sortDesckeepMostfrequent = termsInOneCommunity.sortDesckeepMostfrequent(termsInOneCommunity, termsPerCommunity);
            int rowValue = 1;
            for (Map.Entry<String, Integer> entry2 : sortDesckeepMostfrequent) {
                Cell cellValue = sheet.getRow(rowValue).createCell(communityNumber, CellType.STRING);
                cellValue.setCellValue(entry2.getKey() + " x " + entry2.getValue());
                rowValue++;
            }
        }
        StreamedContent file = null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            byte[] barray = bos.toByteArray();
            InputStream is = new ByteArrayInputStream(barray);
            file = DefaultStreamedContent.builder()
                    .name("results.xlsx")
                    .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .stream(() -> is)
                    .build();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }

    public static StreamedContent exportPdfMatcher(Map<String, List<Occurrence>> result) {
        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet sheet = wb.createSheet("results");

        Iterator<Map.Entry<String, List<Occurrence>>> iterator = result.entrySet().iterator();
        // creating the rows

        int maxRows = 0;

        while (iterator.hasNext()) {
            Map.Entry<String, List<Occurrence>> next = iterator.next();
            if (next.getValue().size() > maxRows) {
                maxRows = next.getValue().size();
            }
        }

        for (int r = 0; r <= (maxRows + 5); r++) {
            sheet.createRow(r);
        }

        int colNum = 0;
        for (Map.Entry<String, List<Occurrence>> entry : result.entrySet()) {
            colNum++;
            String fileName = entry.getKey();
            List<Occurrence> occInOneFile = entry.getValue();
            Cell cellHeader = sheet.getRow(0).createCell(colNum, CellType.STRING);
            cellHeader.setCellValue("file " + fileName);
            int rowValue = 1;
            for (Occurrence occ : occInOneFile) {
                Cell cellValue = sheet.getRow(rowValue).createCell(colNum, CellType.STRING);
                cellValue.setCellValue("page " + occ.getPage() + ", context: " + occ.getContext());
                rowValue++;
            }
        }
        StreamedContent file = null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            byte[] barray = bos.toByteArray();
            InputStream is = new ByteArrayInputStream(barray);
            file = DefaultStreamedContent.builder()
                    .name("results.xlsx")
                    .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .stream(() -> is)
                    .build();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }

    public static StreamedContent exportHighlighted(List<String[]> results) {
        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet sheet = wb.createSheet("results");
        int rowNumber = 0;
        // creating the header
        Row rowHeader = sheet.createRow(rowNumber++);
        Cell cell0Header = rowHeader.createCell(0, CellType.STRING);
        cell0Header.setCellValue(String.valueOf("term"));
        Cell cell1Header = rowHeader.createCell(1, CellType.STRING);
        cell1Header.setCellValue("term highlighted in context");

        for (String[] entry : results) {
            Row row = sheet.createRow(rowNumber++);
            Cell cell0 = row.createCell(0, CellType.STRING);
            cell0.setCellValue(entry[0]);
            Cell cell1 = row.createCell(1, CellType.STRING);
            cell1.setCellValue(entry[1]);
        }
        StreamedContent file = null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            byte[] barray = bos.toByteArray();
            InputStream is = new ByteArrayInputStream(barray);
            file = DefaultStreamedContent.builder()
                    .name("results.xlsx")
                    .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .stream(() -> is)
                    .build();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }

    public static StreamedContent exportOrganic(List<Document> results, ResourceBundle localeBundle) {
        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet sheet = wb.createSheet("results");
        int rowNumber = 0;
        // creating the header
        Row rowHeader = sheet.createRow(rowNumber++);
        Cell cell0Header = rowHeader.createCell(0, CellType.STRING);
        cell0Header.setCellValue(String.valueOf("line number"));
        Cell cell1Header = rowHeader.createCell(1, CellType.STRING);
        cell1Header.setCellValue("text provided as input");
        Cell cell2Header = rowHeader.createCell(2, CellType.STRING);
        cell2Header.setCellValue("is promoted?");

        for (Document doc : results) {
            if (doc == null) {
                continue;
            }
            Row row = sheet.createRow(rowNumber++);
            Cell cell0 = row.createCell(0, CellType.STRING);
            cell0.setCellValue(String.valueOf(rowNumber));
            Cell cell1 = row.createCell(1, CellType.STRING);
            cell1.setCellValue(doc.getText());
            Cell cell2 = row.createCell(2, CellType.STRING);

            String organic;
            if (doc.getCategorizationResult().toString().startsWith("_061")) {
                organic = "📢 " + localeBundle.getString("organic.general.soundspromoted");
            } else {
                organic = "🌿 " + localeBundle.getString("organic.general.soundsorganic");
            }

            cell2.setCellValue(organic);
        }
        StreamedContent file = null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            byte[] barray = bos.toByteArray();
            InputStream is = new ByteArrayInputStream(barray);
            file = DefaultStreamedContent.builder()
                    .name("graph.gexf")
                    .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    .stream(() -> is)
                    .build();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }

}
