/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.importdata;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author LEVALLOIS
 */


public class DataFormatConverter {
    
    public Map<Integer, String> convertToMapOfLines(boolean isBulkData, List<SheetModel> dataInSheets, String selectedSheetName, String selectedColumnIndex, boolean hasHeaders){
        Map<Integer, String> lines = new HashMap();

        if (isBulkData) {
            int i = 0;
            for (SheetModel sm : dataInSheets) {
                List<CellRecord> cellRecords = sm.getColumnIndexToCellRecords().get(0);
                for (CellRecord cr : cellRecords) {
                    lines.put(i++, cr.getRawValue());
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
            if (sheetWithData == null){
                return lines;
            }
            List<CellRecord> cellRecords = sheetWithData.getCellRecords();
            int i = 0;
            int selectedColAsInt = Integer.valueOf(selectedColumnIndex);
            for (CellRecord cr : cellRecords) {
                if (cr.getColIndex() == selectedColAsInt) {
                    lines.put(i++, cr.getRawValue());
                }
            }
        }

        if (hasHeaders) {
            lines.remove(0);
        }
        return lines;
    }
    
}
