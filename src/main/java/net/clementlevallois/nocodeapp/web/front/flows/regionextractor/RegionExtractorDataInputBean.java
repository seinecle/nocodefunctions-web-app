/*
 * Copyright Clement Levallois 2021-2024. License Attribution 4.0 International (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.flows.regionextractor;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.PhaseId;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.importers.model.ImagesPerFile;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.exceptions.NocodeApplicationException;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;
import net.clementlevallois.nocodeapp.web.front.flows.regionextractor.RegionExtractorState.AwaitingTargetPdfs;
import net.clementlevallois.nocodeapp.web.front.flows.regionextractor.RegionExtractorState.ExemplarPdfUploaded;
import net.clementlevallois.nocodeapp.web.front.flows.regionextractor.RegionExtractorState.RegionDefinition;
import net.clementlevallois.nocodeapp.web.front.flows.regionextractor.RegionExtractorState.RegionParameters;
import net.clementlevallois.nocodeapp.web.front.flows.regionextractor.RegionExtractorState.TargetPdfsUploaded;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import org.primefaces.PrimeFaces;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.CroppedImage;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.file.UploadedFile;

@Named
@RequestScoped
public class RegionExtractorDataInputBean implements Serializable {

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    private SessionBean sessionBean;

    @Inject
    private MicroserviceHttpClient microserviceClient;

    @Inject
    RegionExtractorService helperMethodsService;

    private Globals globals;

    @PostConstruct
    public void init() {
        globals = new Globals(applicationProperties.getTempFolderFullPath());
    }

    public void handleExamplarUpload(FileUploadEvent event) {
        String jobId = UUID.randomUUID().toString().substring(0, 10);
        sessionBean.setFlowState(new RegionExtractorState.AwaitingExemplarPdf(jobId));
        if (event.getFile() == null) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "File Upload Error", "No file was uploaded.");
            return;
        }
        RegionExtractorDataSource dataSource = new RegionExtractorDataSource.FileUpload(List.of(event.getFile()));
        processRegionExtractorDataSource(jobId, dataSource);
        RegionParameters regionParameters = new RegionParameters(false, 0, null, 0, 0, 0, 0, 0, 0);
        ExemplarPdfUploaded exemplarPdfUploadedState = new RegionExtractorState.ExemplarPdfUploaded(jobId, regionParameters);
        sessionBean.setFlowState(exemplarPdfUploadedState);
        extractPngsFromPdfPages(jobId);
        ImagesPerFile imagesOfExemplar = getImagesOfExemplar();
        sessionBean.setFlowState(new RegionExtractorState.RegionDefinition(jobId, imagesOfExemplar, regionParameters));
    }

    public void handleTargetFilesUpload(FileUploadEvent event) {
        if (event.getFile() == null) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "File Upload Error", "No file was uploaded.");
            return;
        }
        FlowState regionExtractorState = sessionBean.getFlowState();
        if (regionExtractorState instanceof RegionExtractorState.AwaitingTargetPdfs state) {
            try {
                String fileNameForPersistence = Globals.UPLOADED_FILE_PREFIX + event.getFile().getFileName();

                Path pathToFile = applicationProperties.getTempFolderFullPath().resolve(state.jobId()).resolve(fileNameForPersistence);
                Files.write(pathToFile, event.getFile().getContent());
            } catch (IOException ex) {
                throw new NocodeApplicationException("An IO error occurred", ex);
            }
        }else {
            throw new IllegalStateException("wrong state " + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

    public void onAllTargetFilesUploadsComplete() {
        PrimeFaces.current().ajax().update("notifications", "targetFilesUploadingForm:whenFileUploaded");
        FlowState regionExtractorState = sessionBean.getFlowState();
        if (regionExtractorState instanceof RegionExtractorState.AwaitingTargetPdfs state) {
            sessionBean.setFlowState(new RegionExtractorState.TargetPdfsUploaded(state.jobId(), state.regionParameters()));
        } else {
            throw new IllegalStateException("wrong state " + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

    private void processRegionExtractorDataSource(String jobId, RegionExtractorDataSource dataSource) {
        if (!(sessionBean.getFlowState() instanceof RegionExtractorState.AwaitingExemplarPdf)) {
            throw new IllegalStateException("wrong state " + sessionBean.getFlowState().getClass().getSimpleName());
        }

        Path jobDirectory = applicationProperties.getTempFolderFullPath().resolve(jobId);
        try {
            Files.createDirectories(jobDirectory);
            Files.copy(((RegionExtractorDataSource.FileUpload) dataSource).files().get(0).getInputStream(), jobDirectory.resolve("template.pdf"), REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new NocodeApplicationException("An IO error occurred", ex);
        }
        sessionBean.sendFunctionPageReport(Globals.Names.REGION_EXTRACTOR.name());
    }

    private void extractPngsFromPdfPages(String jobId) {
        microserviceClient.importService().get("pdf/pages-to-png")
                .addQueryParameter("jobId", jobId)
                .addQueryParameter("fileName", "template.pdf")
                .sendAsync(HttpResponse.BodyHandlers.ofString())
                .join();
    }

    public List<UploadedFile> handleTargetPdfs(RegionExtractorDataSource dataSource) {
        if (!(dataSource instanceof RegionExtractorDataSource.FileUpload pdfDataSource)) {
            throw new IllegalStateException("wrong state " + sessionBean.getFlowState().getClass().getSimpleName());
        } else {
            return pdfDataSource.files();
        }
    }

    public ImagesPerFile getImagesOfExemplar() {
        FlowState regionExtractorState = sessionBean.getFlowState();
        if (regionExtractorState instanceof ExemplarPdfUploaded rd && rd.jobId() != null) {
            Path pngsForOneFile = globals.getAllPngPath(rd.jobId());
            try (InputStream is = Files.newInputStream(pngsForOneFile); ObjectInputStream ois = new ObjectInputStream(is)) {
                ImagesPerFile imagesPerFile = (ImagesPerFile) ois.readObject();
                return imagesPerFile;
            } catch (IOException | ClassNotFoundException ex) {
                throw new NocodeApplicationException("Error in getImagesOfExemplar method: Failed to read images for jobId: " + rd.jobId(), ex);
            }
        } else {
            throw new IllegalStateException("wrong state " + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

    public List<Integer> getPageIndices() {
        if (sessionBean.getFlowState() instanceof RegionDefinition(String jobId, ImagesPerFile images, RegionParameters regionParameters)) {
            if (getExtractSameRegionOnAllPages()) {
                return List.of(0); // Just the first page
            } else {
                return IntStream.range(0, images.getImages().length).boxed().collect(Collectors.toList());
            }
        } else {
            throw new IllegalStateException("wrong state " + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

    public StreamedContent getImageByIndex() {
        var fc = FacesContext.getCurrentInstance();
        // During initial HTML render, just hand back an empty placeholder so PF can write the URL.
        if (fc.getCurrentPhaseId() == PhaseId.RENDER_RESPONSE) {
            return new DefaultStreamedContent();
        }
        var params = fc.getExternalContext().getRequestParameterMap();
        String pageParam = params.get("pageIndex");
        if (pageParam == null) {
            throw new IllegalStateException("param should not be null " + sessionBean.getFlowState().getClass().getSimpleName());
        }

        int pageIndex = Integer.parseInt(pageParam);
        if (sessionBean.getFlowState() instanceof RegionDefinition(String jobId, ImagesPerFile images, RegionParameters regionParameters)) {
            String random = UUID.randomUUID().toString() + String.valueOf(pageIndex);
            return DefaultStreamedContent.builder().name(random).contentType("image/png").stream(() -> new ByteArrayInputStream(images.getImage(pageIndex))).build();
        } else {
            throw new IllegalStateException("state should be RegionDefinition " + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

    public boolean getThisPageSelected() {
        if (sessionBean.getFlowState() instanceof RegionDefinition(String jobId, ImagesPerFile images, RegionParameters regionParameters)) {
            FacesContext context = FacesContext.getCurrentInstance();
            String indexRow = context.getExternalContext().getRequestParameterMap().get("pageIndex");
            if (indexRow == null) {
                return false;
            } else {
                Integer rowIndexAsInt = Integer.valueOf(indexRow);
                return regionParameters.selectedPage().equals(rowIndexAsInt);
            }
        } else {
            throw new IllegalStateException("state should be RegionDefinition " + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

    public void setThisPageSelected(boolean selectPage) {
        FacesContext context = FacesContext.getCurrentInstance();
        String index = context.getExternalContext().getRequestParameterMap().get("pageIndex");
        if (index != null) {
            try {
                if (sessionBean.getFlowState() instanceof RegionDefinition(String jobId, ImagesPerFile images, RegionParameters regionParameters)) {
                    regionParameters = regionParameters.withSelectedPage(Integer.parseInt(index));
                    sessionBean.setFlowState(new RegionExtractorState.RegionDefinition(jobId, images, regionParameters));
                } else {
                    throw new IllegalStateException("state should be RegionDefinition " + sessionBean.getFlowState().getClass().getSimpleName());
                }
            } catch (NumberFormatException e) {
                throw new IllegalStateException("invalid page number " + sessionBean.getFlowState().getClass().getSimpleName());
            }
        } else {
            throw new IllegalStateException("param should not be null " + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

    public boolean getExtractSameRegionOnAllPages() {
        if (sessionBean.getFlowState() instanceof RegionDefinition(String jobId, ImagesPerFile images, RegionParameters regionParameters)) {
            return regionParameters.allPages();
        } else {
            throw new IllegalStateException("state should be RegionDefinition " + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

    public void setExtractSameRegionOnAllPages(boolean allPagesParam) {
        if (sessionBean.getFlowState() instanceof RegionDefinition(String jobId, ImagesPerFile images, RegionParameters regionParameters)) {
            regionParameters = regionParameters.withAllPages(allPagesParam);
            sessionBean.setFlowState(new RegionExtractorState.RegionDefinition(jobId, images, regionParameters));
        } else {
            throw new IllegalStateException("state should be RegionDefinition " + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

    public CroppedImage getSelectedRegion() {
        if (sessionBean.getFlowState() instanceof RegionDefinition(String jobId, ImagesPerFile images, RegionParameters regionParameters)) {
            return regionParameters.selectedRegion();
        } else {
            throw new IllegalStateException("state should be RegionDefinition " + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

    public void setSelectedRegion(CroppedImage selectedRegion) {
        if (sessionBean.getFlowState() instanceof RegionDefinition(String jobId, ImagesPerFile images, RegionParameters regionParameters)) {
            RegionParameters withSelectedRegion = regionParameters.withSelectedRegion(selectedRegion);
            RegionDefinition updatedRegionDefinition = new RegionDefinition(jobId, images, withSelectedRegion);
            sessionBean.setFlowState(updatedRegionDefinition);
        } else {
            throw new IllegalStateException("state should be RegionDefinition " + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

    public String goToPdfUpload() {
        if (sessionBean.getFlowState() instanceof RegionDefinition rd) {
            var fc = FacesContext.getCurrentInstance();
            var params = fc.getExternalContext().getRequestParameterMap();
            String pageParam = params.get("pageIndex");
            if (pageParam == null) {
                throw new IllegalStateException("param should not be null " + sessionBean.getFlowState().getClass().getSimpleName());
            }

            RegionParameters withSelectedPage = rd.regionParameters().withSelectedPage(Integer.parseInt(pageParam));
            rd = new RegionDefinition(rd.jobId(), rd.imagesPerFile(), withSelectedPage);

            RegionDefinition updatedRegionDefinition = helperMethodsService.getDocumentDimensions(rd);
            updatedRegionDefinition = helperMethodsService.defineRegion(updatedRegionDefinition);
            updatedRegionDefinition = helperMethodsService.getDocumentDimensions(updatedRegionDefinition);

            AwaitingTargetPdfs awaitingTargetPdfs = new AwaitingTargetPdfs(updatedRegionDefinition.jobId(), updatedRegionDefinition.regionParameters());
            sessionBean.setFlowState(awaitingTargetPdfs);
            return "/regionextractor/import-all-documents.xhtml?function=pdf_region_extractor&amp;faces-redirect=true";
        } else {
            throw new IllegalStateException("state should be RegionDefinition " + sessionBean.getFlowState().getClass().getSimpleName());
        }

    }

    public boolean isStateRegionDefinition() {
        return sessionBean.getFlowState() instanceof RegionExtractorState.RegionDefinition;
    }

    public boolean areTargetPdfsUploaded() {
        return sessionBean.getFlowState() instanceof TargetPdfsUploaded;
    }
}
