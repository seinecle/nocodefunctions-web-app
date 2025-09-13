package net.clementlevallois.nocodeapp.web.front.flows.gexf2vv;

import org.primefaces.model.file.UploadedFile;

public sealed interface Gexf2VvDataSource {

    record FileUpload(UploadedFile file) implements Gexf2VvDataSource {

    }
}
