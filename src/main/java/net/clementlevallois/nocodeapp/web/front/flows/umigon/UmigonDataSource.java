package net.clementlevallois.nocodeapp.web.front.flows.umigon;

import org.primefaces.model.file.UploadedFile;

public sealed interface UmigonDataSource {

    /**
     * @param file Un objet UploadedFile de PrimeFaces.
     */
    record FileUpload(UploadedFile file) implements UmigonDataSource {}
}
