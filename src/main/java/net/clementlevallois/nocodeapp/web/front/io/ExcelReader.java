/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
 /*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.io;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.nocodeapp.web.front.importdata.SheetModel;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.primefaces.model.file.UploadedFile;

/**
 *
 * @author LEVALLOIS
 */
public class ExcelReader {

    public static List<String> getListOfSheets(UploadedFile file) throws FileNotFoundException, IOException {
        List<String> sheetNames = new ArrayList();
        try {
            if (file == null) {
                return sheetNames;
            }
            InputStream inputStream = file.getInputStream();
            try (OPCPackage pkg = OPCPackage.open(inputStream)) {
                XSSFReader r = new XSSFReader(pkg);
                XSSFReader.SheetIterator iter = (XSSFReader.SheetIterator) r.getSheetsData();
                while (iter.hasNext()) {
                    InputStream stream = iter.next();
                    String sheetName = iter.getSheetName();
                    sheetNames.add(sheetName);
                    stream.close();
                }
            }
            inputStream.close();
//            try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
//                Iterator<Sheet> sheetIterator = wb.sheetIterator();
//                while (sheetIterator.hasNext()) {
//                    Sheet next = sheetIterator.next();
//                    sheetNames.add(next.getSheetName());
//                }
//            }

            return sheetNames;
        } catch (InvalidFormatException ex) {
            Logger.getLogger(ExcelReader.class.getName()).log(Level.SEVERE, null, ex);
            return sheetNames;
        } catch (OpenXML4JException ex) {
            Logger.getLogger(ExcelReader.class.getName()).log(Level.SEVERE, null, ex);
            return sheetNames;
        }
    }

    public static List<SheetModel> getDataInSheets(UploadedFile file) throws FileNotFoundException, IOException {
        List<SheetModel> sheetsData = new ArrayList();
        return sheetsData;
    }

//    public static List<SheetModel> readAll(UploadedFile file) throws FileNotFoundException, IOException {
//
//        List<SheetModel> sheets = new ArrayList();
//
//        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
//            int numberOfSheets = wb.getNumberOfSheets();
//            for (int i = 0; i < numberOfSheets; i++) {
//                List< Map<String, ColumnModel>> sheetData = new ArrayList();
//                List<ColumnModel> headerNames = new ArrayList();
//                SheetModel sheetModel = new SheetModel();
//                Sheet sheet = wb.getSheetAt(i);
//                sheetModel.setName(sheet.getSheetName());
//                int firstRowNum = sheet.getFirstRowNum();
//                int lastRowNum = sheet.getLastRowNum();
//                Row headerRow = sheet.getRow(firstRowNum);
//                short minColIx = headerRow.getFirstCellNum();
//                short maxColIx = headerRow.getLastCellNum();
//                for (short colIx = minColIx; colIx < maxColIx; colIx++) {
//                    Cell cell = headerRow.getCell(colIx);
//                    if (cell == null) {
//                        continue;
//                    }
//
//                    ColumnModel cm;
//                    cm = new ColumnModel(cell.getStringCellValue(), String.valueOf(colIx));
//                    headerNames.add(cm);
//                }
//                Row currentRow;
//                int startRow = 0;
//                for (int j = startRow; j < lastRowNum; j++) {
//                    currentRow = sheet.getRow(j);
//                    Map<String, ColumnModel> row = new HashMap();
//                    for (short colIx = minColIx; colIx < maxColIx; colIx++) {
//                        Cell cell = currentRow.getCell(colIx);
//                        if (cell == null) {
//                            continue;
//                        }
//                        row.put(headerNames.get(colIx).getHeader(), new ColumnModel(headerNames.get(colIx).getHeader(), returnStringValue(cell)));
//                    }
//                    sheetData.add(row);
//                }
//
//                sheetModel.setSheetData(sheetData);
//                sheetModel.setTableHeaderNames(headerNames);
//                sheets.add(sheetModel);
//            }
//        }
//
//        return sheets;
//    }

    public static String returnStringValue(Cell cell) {
        CellType cellType = cell.getCellType();

        switch (cellType) {
            case NUMERIC:
                double doubleVal = cell.getNumericCellValue();
                return String.valueOf(doubleVal);
            case STRING:
                return cell.getStringCellValue();
            case ERROR:
                return String.valueOf(cell.getErrorCellValue());
            case BLANK:
                return "";
            case FORMULA:
                return cell.getCellFormula();
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
        }
        return "error decoding string value of the cell";
    }

}
