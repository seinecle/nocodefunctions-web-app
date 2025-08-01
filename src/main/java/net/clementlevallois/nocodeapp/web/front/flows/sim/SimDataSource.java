/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows.sim;

import org.primefaces.model.file.UploadedFile;

public sealed interface SimDataSource {

    /**
     * @param file A UploadedFile object from PrimeFaces.
     */
    record FileUpload(UploadedFile file) implements SimDataSource {}

}