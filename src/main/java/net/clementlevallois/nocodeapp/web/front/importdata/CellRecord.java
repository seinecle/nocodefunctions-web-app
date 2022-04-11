/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.importdata;

/**
 *
 * @author LEVALLOIS
 */
public class CellRecord {

    private int rowIndex;
    private int colIndex;
    private String rawValue;
    private String valueWithExtraFormatting;

    public CellRecord(int rowIndex, int colIndex, String value) {
        this.rowIndex = rowIndex;
        this.colIndex = colIndex;
        this.rawValue = value;
    }
    
    public CellRecord(int rowIndex, int colIndex, String value, String value2) {
        this.rowIndex = rowIndex;
        this.colIndex = colIndex;
        this.rawValue = value;
        this.valueWithExtraFormatting = value2;
    }
    
    

    public int getRowIndex() {
        return rowIndex;
    }

    public void setRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
    }

    public int getColIndex() {
        return colIndex;
    }

    public void setColIndex(int colIndex) {
        this.colIndex = colIndex;
    }

    public String getRawValue() {
        return rawValue;
    }

    public void setRawValue(String rawValue) {
        this.rawValue = rawValue;
    }

    public String getValueWithExtraFormatting() {
        return valueWithExtraFormatting;
    }

    public void setValueWithExtraFormatting(String valueWithExtraFormatting) {
        this.valueWithExtraFormatting = valueWithExtraFormatting;
    }
    
    

}
