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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.importers.model.ImagesPerFile;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.flows.regionextractor.RegionExtractorState.AwaitingTargetPdfs;
import net.clementlevallois.nocodeapp.web.front.flows.regionextractor.RegionExtractorState.ExemplarPdfUploaded;
import net.clementlevallois.nocodeapp.web.front.flows.regionextractor.RegionExtractorState.RegionDefinition;
import net.clementlevallois.nocodeapp.web.front.flows.regionextractor.RegionExtractorState.RegionParameters;
import net.clementlevallois.nocodeapp.web.front.flows.regionextractor.RegionExtractorState.TargetPdfsUploaded;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import org.primefaces.PrimeFaces;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.CroppedImage;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.file.UploadedFile;


@Named
@RequestScoped
public class RegionExtractorDataInputBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(RegionExtractorDataInputBean.class.getName());

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    private SessionBean sessionBean;

    @Inject
    private BackToFrontMessengerBean logBean;

    @Inject
    private MicroserviceHttpClient microserviceClient;

    @Inject
    RegionExtractorService helperMethodsService;

    private Globals globals;

    @PostConstruct
    public void init() {
        globals = new Globals(applicationProperties.getTempFolderFullPath());
    }

    /**
     * @param event
     */
    public void handleExamplarUpload(FileUploadEvent event) {
        sessionBean.setRegionExtractorState(new RegionExtractorState.AwaitingExemplarPdf());
        if (event.getFile() == null) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "File Upload Error", "No file was uploaded.");
            return;
        }
        String jobId = UUID.randomUUID().toString().substring(0, 10);
        RegionExtractorDataSource dataSource = new RegionExtractorDataSource.FileUpload(List.of(event.getFile()));
        processRegionExtractorDataSource(jobId, dataSource);
        RegionParameters regionParameters = new RegionParameters(false, 0, null, 0, 0, 0, 0, 0, 0);
        ExemplarPdfUploaded exemplarPdfUploadedState = new RegionExtractorState.ExemplarPdfUploaded(jobId, regionParameters);
        sessionBean.setRegionExtractorState(exemplarPdfUploadedState);
        extractPngsFromPdfPages(jobId);
        ImagesPerFile imagesOfExemplar = getImagesOfExemplar();
        sessionBean.setRegionExtractorState(new RegionExtractorState.RegionDefinition(jobId, imagesOfExemplar, regionParameters));
    }

    public void handleTargetFilesUpload(FileUploadEvent event) {
        if (event.getFile() == null) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "File Upload Error", "No file was uploaded.");
            return;
        }
        RegionExtractorState regionExtractorState = sessionBean.getRegionExtractorState();
        if (regionExtractorState instanceof RegionExtractorState.AwaitingTargetPdfs state) {
            try {
                String fileNameForPersistence = Globals.UPLOADED_FILE_PREFIX + event.getFile().getFileName();

                Path pathToFile = applicationProperties.getTempFolderFullPath().resolve(state.jobId()).resolve(fileNameForPersistence);
                Files.write(pathToFile, event.getFile().getContent());
            } catch (IOException ex) {
                System.getLogger(RegionExtractorDataInputBean.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
            }
        }
    }

    public void onAllTargetFilesUploadsComplete() {
        PrimeFaces.current().ajax().update("notifications", "targetFilesUploadingForm:whenFileUploaded");
        RegionExtractorState regionExtractorState = sessionBean.getRegionExtractorState();
        if (regionExtractorState instanceof RegionExtractorState.AwaitingTargetPdfs state) {
            sessionBean.setRegionExtractorState(new RegionExtractorState.TargetPdfsUploaded(state.jobId(), state.regionParameters()));
        } else {
            System.out.println("incorrect state in onAllTargetFilesUploadsComplete: " + regionExtractorState.typeName());
        }
    }

    private void processRegionExtractorDataSource(String jobId, RegionExtractorDataSource dataSource) {
        if (!(sessionBean.getRegionExtractorState() instanceof RegionExtractorState.AwaitingExemplarPdf)) {
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Invalid State", "The application is in an invalid state.");
            sessionBean.setRegionExtractorState(new RegionExtractorState.FlowFailed(null, sessionBean.getRegionExtractorState(), "invalid state"));
            return;
        }

        Path jobDirectory = applicationProperties.getTempFolderFullPath().resolve(jobId);
        try {
            Files.createDirectories(jobDirectory);
            Files.copy(((RegionExtractorDataSource.FileUpload) dataSource).files().get(0).getInputStream(), jobDirectory.resolve("template.pdf"), REPLACE_EXISTING);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "unable to create directories for job " + jobId, ex);
            sessionBean.setRegionExtractorState(new RegionExtractorState.FlowFailed(jobId, sessionBean.getRegionExtractorState(), "unable to create directories"));
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
            logBean.addOneNotificationFromString("Error: incorrect data source type for target PDFs.");
            return new ArrayList<>();
        } else {
            return pdfDataSource.files();
        }
    }

    public ImagesPerFile getImagesOfExemplar() {
        RegionExtractorState regionExtractorState = sessionBean.getRegionExtractorState();
        if (regionExtractorState instanceof ExemplarPdfUploaded rd && rd.jobId() != null) {
            Path pngsForOneFile = globals.getAllPngPath(rd.jobId());
            try (InputStream is = Files.newInputStream(pngsForOneFile); ObjectInputStream ois = new ObjectInputStream(is)) {
                ImagesPerFile imagesPerFile = (ImagesPerFile) ois.readObject();
                return imagesPerFile;
            } catch (IOException | ClassNotFoundException ex) {
                Logger.getLogger(RegionExtractorDataInputBean.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else {
            //error
        }
        return null;
    }

    public List<Integer> getPageIndices() {
        if (sessionBean.getRegionExtractorState() instanceof RegionDefinition(String jobId, ImagesPerFile images, RegionParameters regionParameters)) {
            if (getAllPages()) {
                return IntStream.range(0, images.getImages().length).boxed().collect(Collectors.toList());
            } else {
                return List.of(0); // Just the first page
            }
        }
        return Collections.emptyList();
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
            return new DefaultStreamedContent(); // defensive
        }

        int pageIndex = Integer.parseInt(pageParam);
        if (sessionBean.getRegionExtractorState() instanceof RegionDefinition(String jobId, ImagesPerFile images, RegionParameters regionParameters)) {
            String random = UUID.randomUUID().toString() + String.valueOf(pageIndex);
            return DefaultStreamedContent.builder().name(random).contentType("image/png").stream(() -> new ByteArrayInputStream(images.getImage(pageIndex))).build();

        } else {
            return new DefaultStreamedContent();
        }
    }

    public boolean getThisPageSelected() {
        if (sessionBean.getRegionExtractorState() instanceof RegionDefinition(String jobId, ImagesPerFile images, RegionParameters regionParameters)) {
            FacesContext context = FacesContext.getCurrentInstance();
            String indexRow = context.getExternalContext().getRequestParameterMap().get("rowIndex3");
            if (indexRow == null) {
                return false;
            } else {
                Integer rowIndexAsInt = Integer.valueOf(indexRow);
                return regionParameters.selectedPage().equals(rowIndexAsInt);
            }
        } else {
            return false;
        }
    }

    public void setThisPageSelected(boolean selectPage) {
        FacesContext context = FacesContext.getCurrentInstance();
        String index = context.getExternalContext().getRequestParameterMap().get("rowIndex3");
        if (index != null) {
            try {
                if (sessionBean.getRegionExtractorState() instanceof RegionDefinition(String jobId, ImagesPerFile images, RegionParameters regionParameters)) {
                    regionParameters = regionParameters.withSelectedPage(Integer.parseInt(index));
                    sessionBean.setRegionExtractorState(new RegionExtractorState.RegionDefinition(jobId, images, regionParameters));
                    LOG.log(Level.INFO, "Selected page set to: {0}", regionParameters.selectedPage());
                } else {
                    //error
                }
            } catch (NumberFormatException e) {
                LOG.log(Level.WARNING, "Invalid rowIndex3 parameter for setSelectPage: " + index, e);
                sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Input Error", "Invalid page number selected.");
            }
        } else {
            LOG.warning("Missing rowIndex3 parameter for setSelectPage.");
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Input Error", "Missing page number for selection.");
        }
    }

    public boolean getAllPages() {
        if (sessionBean.getRegionExtractorState() instanceof RegionDefinition(String jobId, ImagesPerFile images, RegionParameters regionParameters)) {
            return regionParameters.allPages();
        } else {
            return false;
        }
    }

    public void setAllPages(boolean allPagesParam) {
        if (sessionBean.getRegionExtractorState() instanceof RegionDefinition(String jobId, ImagesPerFile images, RegionParameters regionParameters)) {
            regionParameters = regionParameters.withAllPages(allPagesParam);
            sessionBean.setRegionExtractorState(new RegionExtractorState.RegionDefinition(jobId, images, regionParameters));
            LOG.log(Level.INFO, "Selected page set to: {0}", regionParameters.selectedPage());
        } else {
            //error
        }
    }

    public CroppedImage getSelectedRegion() {
        if (sessionBean.getRegionExtractorState() instanceof RegionDefinition(String jobId, ImagesPerFile images, RegionParameters regionParameters)) {
            return regionParameters.selectedRegion();
        } else {
            return new CroppedImage();
        }
    }

    public void setSelectedRegion(CroppedImage selectedRegion) {
        if (sessionBean.getRegionExtractorState() instanceof RegionDefinition(String jobId, ImagesPerFile images, RegionParameters regionParameters)) {
            RegionParameters withSelectedRegion = regionParameters.withSelectedRegion(selectedRegion);
            RegionDefinition updatedRegionDefinition = new RegionDefinition(jobId, images, withSelectedRegion);
            sessionBean.setRegionExtractorState(updatedRegionDefinition);
        }
    }

    public String goToPdfUpload() {
        if (sessionBean.getRegionExtractorState() instanceof RegionDefinition rd) {
            RegionDefinition updatedRegionDefinition = helperMethodsService.getDocumentDimensions(rd);
            updatedRegionDefinition = helperMethodsService.defineRegion(updatedRegionDefinition);
            updatedRegionDefinition = helperMethodsService.getDocumentDimensions(updatedRegionDefinition);

            AwaitingTargetPdfs awaitingTargetPdfs = new AwaitingTargetPdfs(updatedRegionDefinition.jobId(), updatedRegionDefinition.regionParameters());
            sessionBean.setRegionExtractorState(awaitingTargetPdfs);
            return "/regionextractor/import-all-documents.xhtml?function=pdf_region_extractor&amp;faces-redirect=true";
        } else {
            // error
            return null;
        }

    }

    public boolean isStateRegionDefinition() {
        return sessionBean.getRegionExtractorState() instanceof RegionExtractorState.RegionDefinition;
    }

    public boolean areTargetPdfsUploaded() {
        return sessionBean.getRegionExtractorState() instanceof TargetPdfsUploaded;
    }

}
