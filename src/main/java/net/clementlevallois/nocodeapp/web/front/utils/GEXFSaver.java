/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.utils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

/**
 *
 * @author LEVALLOIS
 */
public class GEXFSaver {

    public static StreamedContent exportGexfAsStreamedFile(String gexf, String resultFileNameWithoutExtension) {

        byte[] readAllBytes = gexf.getBytes();
        InputStream is = new ByteArrayInputStream(readAllBytes);
        StreamedContent file = DefaultStreamedContent.builder()
                .name(resultFileNameWithoutExtension + ".gexf")
                .contentType("application/gexf+xml")
                .stream(() -> is)
                .build();

        return file;
    }
}
