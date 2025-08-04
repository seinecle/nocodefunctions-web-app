/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows.cooc;

import org.primefaces.model.file.UploadedFile;
import java.util.List;

/**
 * Represents a source of data for the Co-occurrence (cooc) workflow. This is a
 * sealed interface, establishing a closed set of all possible data sources.
 */
public sealed interface CoocDataSource {

    /**
     * Represents a data source from one or more files uploaded by the user.
     *
     * @param files A list of UploadedFile objects from PrimeFaces.
     */
    record FileUpload(List<UploadedFile> files) implements CoocDataSource {}
}