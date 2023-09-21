package net.clementlevallois.nocodeapp.web.front.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

/**
 *
 * @author LEVALLOIS
 */
public class GEXFSaver {

    public static StreamedContent exportGexfAsStreamedFile(String gexf, String resultFileNameWithoutExtension) {
        try {
            byte[] readAllBytes = gexf.getBytes(StandardCharsets.UTF_8);
            StreamedContent file;
            try (InputStream is = new ByteArrayInputStream(readAllBytes)) {
                file = DefaultStreamedContent.builder()
                        .name(resultFileNameWithoutExtension + ".gexf")
                        .contentType("application/gexf+xml")
                        .stream(() -> is)
                        .build();
            }
            return file;
        } catch (IOException ex) {
            System.out.println("exception in exportGexfAsStreamedFile:" + ex.toString());
            return null;
        }
    }
}
