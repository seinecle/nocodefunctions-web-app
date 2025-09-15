package net.clementlevallois.nocodeapp.web.front.io;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

/**
 *
 * @author LEVALLOIS
 */
public class ExportToGexf {

    public static StreamedContent exportGexfAsStreamedFile(String gexf, String resultFileNameWithoutExtension) {
        StreamedContent file = null;
        try {
            byte[] readAllBytes = gexf.getBytes(StandardCharsets.UTF_8);
            try (InputStream is = new ByteArrayInputStream(readAllBytes)) {
                file = DefaultStreamedContent.builder()
                        .name(resultFileNameWithoutExtension + ".gexf")
                        .contentType("application/gexf+xml")
                        .stream(() -> is)
                        .build();
            }
        } catch (IOException ex) {
            System.out.println("exception in exportGexfAsStreamedFile:" + ex.toString());
        }
        return file;
    }
}
