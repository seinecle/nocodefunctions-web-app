/*
 * Copyright Clement Levallois 2021-2024. License Attribution 4.0 International (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.flows.regionextractor;

import java.util.List;
import org.primefaces.model.file.UploadedFile;


public sealed interface RegionExtractorDataSource {

    /**
     * @param files A list of {@link UploadedFile} objects provided by the user.
     */
    record FileUpload(List<UploadedFile> files) implements RegionExtractorDataSource {}

}
