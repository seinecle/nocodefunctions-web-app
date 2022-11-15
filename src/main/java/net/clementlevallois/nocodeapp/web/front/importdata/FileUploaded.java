/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.clementlevallois.nocodeapp.web.front.importdata;

import java.io.InputStream;

/**
 *
 * @author LEVALLOIS
 */
public class FileUploaded {
    
    private InputStream inputStream;
    private String fileName;

    public FileUploaded(InputStream is, String fileName) {
        this.inputStream = is;
        this.fileName = fileName;
    }
    
    public InputStream getInputStream() {
        return inputStream;
    }

    public void setInputStream(InputStream is) {
        this.inputStream = is;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    
    
}
