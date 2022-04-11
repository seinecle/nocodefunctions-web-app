/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.importdata;

import java.io.Serializable;

/**
 *
 * @author LEVALLOIS
 */
public class ColumnModel implements Serializable {

    private String colIndex;
    private String cellValue;

    public ColumnModel(String header, String property) {
        this.colIndex = header;
        this.cellValue = property;
    }

    public String getColIndex() {
        return colIndex;
    }

    public void setColIndex(String colIndex) {
        this.colIndex = colIndex;
    }

    public String getCellValue() {
        return cellValue;
    }

    public void setCellValue(String cellValue) {
        this.cellValue = cellValue;
    }


}