package net.clementlevallois.nocodeapp.web.front.importdata;

import java.io.IOException;
import java.io.InputStream;

/**
 *
 * @author LEVALLOIS
 */
public class FileUploaded {

    private byte[] bytes;
    private String fileName;

    public FileUploaded(InputStream is, String fileName) throws IOException {
        this.bytes = is.readAllBytes();
        this.fileName = fileName;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

}
