/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.io;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.exporter.spi.CharacterExporter;
import org.gephi.io.exporter.spi.Exporter;
import org.gephi.project.api.Workspace;
import org.openide.util.Lookup;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

/**
 *
 * @author LEVALLOIS
 */
public class GEXFSaver {

    public static StreamedContent exportGexfAsStreamedFile(Workspace workspace, String resultFileNameWithoutExtension) {

        ExportController ec = Lookup.getDefault().lookup(ExportController.class);
        Exporter exporterGexf = ec.getExporter("gexf");
        exporterGexf.setWorkspace(workspace);
        StringWriter stringWriter = new StringWriter();
        ec.exportWriter(stringWriter, (CharacterExporter) exporterGexf);
        byte[] readAllBytes = stringWriter.toString().getBytes();

        InputStream is = new ByteArrayInputStream(readAllBytes);
        StreamedContent file = DefaultStreamedContent.builder()
                .name(resultFileNameWithoutExtension + ".gexf")
                .contentType("application/gexf+xml")
                .stream(() -> is)
                .build();

        return file;
    }
}
