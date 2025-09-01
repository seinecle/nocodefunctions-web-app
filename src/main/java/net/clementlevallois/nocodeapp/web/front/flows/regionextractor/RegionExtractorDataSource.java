/*
 * Copyright Clement Levallois 2021-2024. License Attribution 4.0 International (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.flows.regionextractor;

import java.util.List;
import org.primefaces.model.file.UploadedFile;

/**
 * This sealed interface represents the possible sources of data for the Region
 * Extractor workflow. In this flow, the primary data source is a collection of
 * PDF files uploaded by the user.
 */
public sealed interface RegionExtractorDataSource {

    /**
     * Represents a data source consisting of one or more PDF files uploaded by
     * the user. This record is used for both the initial exemplar PDF upload
     * and the subsequent target PDF uploads. The distinction between exemplar
     * and target is handled by the application's state, not by the data source
     * type itself.
     *
     * @param files A list of {@link UploadedFile} objects provided by the user.
     */
    record FileUpload(List<UploadedFile> files) implements RegionExtractorDataSource {}

}
