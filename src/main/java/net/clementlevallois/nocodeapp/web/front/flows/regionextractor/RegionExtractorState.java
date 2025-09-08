/*
 * Copyright Clement Levallois 2021-2024. License Attribution 4.0 International (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.flows.regionextractor;

import net.clementlevallois.importers.model.ImagesPerFile;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;
import org.primefaces.model.CroppedImage;

public sealed interface RegionExtractorState extends FlowState  {

    // Stable discriminator if you like string checks in EL
    default String typeName() {
        return this.getClass().getSimpleName();
    }

    // -------- Typed accessors usable from EL (return null when not matching) ------
    default AwaitingExemplarPdf asAwaitingExemplarPdf() { return null; }
    default ExemplarPdfUploaded asExemplarPdfUploaded() { return null; }
    default RegionDefinition asRegionDefinition() { return null; }
    default AwaitingTargetPdfs asAwaitingTargetPdfs() { return null; }
    default TargetPdfsUploaded asTargetPdfsUploaded() { return null; }
    default Processing asProcessing() { return null; }
    default ResultsReady asResultsReady() { return null; }

    // -------- Boolean helpers for rendered=... etc. --------------------------------
    default boolean isAwaitingExemplarPdf() { return false; }
    default boolean isExemplarPdfUploaded() { return false; }
    default boolean isRegionDefinition() { return false; }
    default boolean isAwaitingTargetPdfs() { return false; }
    default boolean isTargetPdfsUploaded() { return false; }
    default boolean isProcessing() { return false; }
    default boolean isResultsReady() { return false; }

    /**
     * A record holding the proportional coordinates of the selected region and
     * the page selection strategy.
     */
    
    record ImageDimensions(int width, int height) {}
    
    record RegionParameters(
            boolean allPages,
            Integer selectedPage,
            CroppedImage selectedRegion,
            int pageWidth,
            int pageHeight,
            float proportionTopLeftX,
            float proportionTopLeftY,
            float proportionWidth,
            float proportionHeight
    ) {
        public RegionParameters withAllPages(boolean allPages) {
            return new RegionParameters(allPages, selectedPage, selectedRegion, pageWidth, pageHeight, proportionTopLeftX, proportionTopLeftY, proportionWidth, proportionHeight);
        }
        public RegionParameters withSelectedPage(int selectedPage) {
            return new RegionParameters(allPages, selectedPage, selectedRegion, pageWidth, pageHeight, proportionTopLeftX, proportionTopLeftY, proportionWidth, proportionHeight);
        }
        public RegionParameters withSelectedRegion(CroppedImage selectedRegion) {
            return new RegionParameters(allPages, selectedPage, selectedRegion, pageWidth, pageHeight,  proportionTopLeftX, proportionTopLeftY, proportionWidth, proportionHeight);
        }
        public RegionParameters withPageWidth(int pageWidth) {
            return new RegionParameters(allPages, selectedPage, selectedRegion, pageWidth, pageHeight, proportionTopLeftX, proportionTopLeftY, proportionWidth, proportionHeight);
        }
        public RegionParameters withPageHeight(int pageHeight) {
            return new RegionParameters(allPages, selectedPage, selectedRegion, pageWidth, pageHeight, proportionTopLeftX, proportionTopLeftY, proportionWidth, proportionHeight);
        }
        public RegionParameters withProportionTopLeftX(float proportionTopLeftX) {
            return new RegionParameters(allPages, selectedPage, selectedRegion, pageWidth, pageHeight, proportionTopLeftX, proportionTopLeftY, proportionWidth, proportionHeight);
        }
        public RegionParameters withProportionTopLeftY(float proportionTopLeftY) {
            return new RegionParameters(allPages, selectedPage, selectedRegion, pageWidth, pageHeight, proportionTopLeftX, proportionTopLeftY, proportionWidth, proportionHeight);
        }
        public RegionParameters withProportionWidth(float proportionWidth) {
            return new RegionParameters(allPages, selectedPage, selectedRegion, pageWidth, pageHeight, proportionTopLeftX, proportionTopLeftY, proportionWidth, proportionHeight);
        }
        public RegionParameters withProportionHeight(float proportionHeight) {
            return new RegionParameters(allPages, selectedPage, selectedRegion, pageWidth, pageHeight, proportionTopLeftX, proportionTopLeftY, proportionWidth, proportionHeight);
        }
    }

    /** The initial state. */
    record AwaitingExemplarPdf(String jobId) implements RegionExtractorState {
        @Override public AwaitingExemplarPdf asAwaitingExemplarPdf() { return this; }
        @Override public boolean isAwaitingExemplarPdf() { return true; }
    }

    /** Exemplar PDF uploaded, conversion started. */
    record ExemplarPdfUploaded(String jobId, RegionParameters regionParameters) implements RegionExtractorState {
        @Override public ExemplarPdfUploaded asExemplarPdfUploaded() { return this; }
        @Override public boolean isExemplarPdfUploaded() { return true; }
    }

    /** Exemplar converted to images; user selects a region. */
    record RegionDefinition(String jobId, ImagesPerFile imagesPerFile, RegionParameters regionParameters)
            implements RegionExtractorState {
        @Override public RegionDefinition asRegionDefinition() { return this; }
        @Override public boolean isRegionDefinition() { return true; }
    }

    /** User defined region; ready to upload target PDFs. */
    record AwaitingTargetPdfs(String jobId, RegionParameters regionParameters)
            implements RegionExtractorState {
        @Override public AwaitingTargetPdfs asAwaitingTargetPdfs() { return this; }
        @Override public boolean isAwaitingTargetPdfs() { return true; }
    }

    /** target PDFs uploaded */
    record TargetPdfsUploaded(String jobId, RegionParameters regionParameters)
            implements RegionExtractorState {
        @Override public TargetPdfsUploaded asTargetPdfsUploaded() { return this; }
        @Override public boolean isTargetPdfsUploaded() { return true; }
    }

    /** Back-end processing targets. */
    record Processing(String jobId, int progress) implements RegionExtractorState {
        @Override public Processing asProcessing() { return this; }
        @Override public boolean isProcessing() { return true; }

        public Processing withProgress(int progress) {
            return new Processing(jobId, progress);
        }

    }

    /** Terminal success. */
    record ResultsReady(String jobId)
            implements RegionExtractorState {
        @Override public ResultsReady asResultsReady() { return this; }
        @Override public boolean isResultsReady() { return true; }
    }
}
