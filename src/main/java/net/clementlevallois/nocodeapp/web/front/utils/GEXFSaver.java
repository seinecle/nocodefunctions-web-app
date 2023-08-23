package net.clementlevallois.nocodeapp.web.front.utils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

/**
 *
 * @author LEVALLOIS
 */
public class GEXFSaver {

    public static StreamedContent exportGexfAsStreamedFile(String gexf, String resultFileNameWithoutExtension) {

        byte[] readAllBytes = gexf.getBytes(StandardCharsets.UTF_8);
        InputStream is = new ByteArrayInputStream(readAllBytes);
        StreamedContent file = DefaultStreamedContent.builder()
                .name(resultFileNameWithoutExtension + ".gexf")
                .contentType("application/gexf+xml")
                .stream(() -> is)
                .build();

        return file;
    }
}
