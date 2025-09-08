/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows.cooc;

import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.WorkflowCoocProps;
import net.clementlevallois.importers.model.CellRecord;
import net.clementlevallois.importers.model.SheetModel;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowFailed;
import net.clementlevallois.nocodeapp.web.front.io.ImportersService;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
import net.clementlevallois.utils.Multiset;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;

@Named
@ViewScoped
public class CoocDataInputBean implements Serializable {

    private static final Logger LOG = Logger.getLogger(CoocDataInputBean.class.getName());

    private String jobId;
    private List<SheetModel> dataInSheets;
    private boolean hasHeaders;
    private Integer activeSheetIndex = 0;
    
    private CoocState.AwaitingParameters awaitingParameters;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    private ImportersService importersService;

    @Inject
    private SessionBean sessionBean;

    @PostConstruct
    public void init() {
        sessionBean.setFlowState(new CoocState.AwaitingData());
    }

    public void handleFileUpload(FileUploadEvent event) {
        if (event.getFile() == null) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "File Upload Error", "No file was uploaded.");
            return;
        }
        CoocDataSource dataSource = new CoocDataSource.FileUpload(List.of(event.getFile()));
        boolean done = processCoocDataSource(dataSource);
        if (done) {
            System.out.println("file uploaded: " + event.getFile().getFileName());
        }
    }

    private boolean processCoocDataSource(CoocDataSource dataSource) {
        if (!(sessionBean.getFlowState() instanceof CoocState.AwaitingData)) {
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Invalid State", "The application is in an invalid state.");
            sessionBean.setFlowState(new FlowFailed(null, sessionBean.getFlowState(), "invalid state"));
            return false;
        }

        this.jobId = UUID.randomUUID().toString().substring(0, 10);
        Path jobDirectory = applicationProperties.getTempFolderFullPath().resolve(jobId);
        try {
            Files.createDirectories(jobDirectory);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "unable to create directories for job " + jobId, ex);
            sessionBean.setFlowState(new FlowFailed(jobId, sessionBean.getFlowState(), "unable to create directories"));
            return false;
        }

        sessionBean.sendFunctionPageReport(Globals.Names.COOC.name());

        ImportersService.PreparationResult result = switch (dataSource) {
            case CoocDataSource.FileUpload(List<UploadedFile> files) ->
                importersService.handleFileUpload(files, jobId, Globals.Names.COOC);
        };

        if (result instanceof ImportersService.PreparationResult.Failure(String error)) {
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Data Preparation Failed", error);
            sessionBean.setFlowState(new FlowFailed(jobId, new CoocState.DataImported(jobId), "data prep failed"));
        } else {
            switch (dataSource) {
                case CoocDataSource.FileUpload(List<UploadedFile> files) -> {
                    for (var file : files) {
                        sessionBean.addMessage(FacesMessage.SEVERITY_INFO, "Success", file.getFileName() + " has been processed.");
                    }
                }
            }
            dataInSheets = populateDataInSheetsVariable();
            sessionBean.setFlowState(new CoocState.DataImported(jobId));
        }
        return true;
    }

    private List<SheetModel> populateDataInSheetsVariable() {

        if (jobId == null) {
            return Collections.EMPTY_LIST;
        }

        Globals globals = new Globals(applicationProperties.getTempFolderFullPath());
        Path sheetsModelFile = globals.getDataSheetPath(jobId);
        if (Files.exists(sheetsModelFile)) {
            try {
                byte[] fileBytes = Files.readAllBytes(sheetsModelFile);

                try (ByteArrayInputStream bis = new ByteArrayInputStream(fileBytes); ObjectInputStream ois = new ObjectInputStream(bis)) {
                    return (List<SheetModel>) ois.readObject();
                } catch (IOException | ClassNotFoundException ex) {
                    System.getLogger(CoocDataInputBean.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                    sessionBean.setFlowState(new FlowFailed(jobId, awaitingParameters, "could not populate data in sheets field"));
                    return Collections.EMPTY_LIST;
                }
            } catch (IOException ex) {
                System.getLogger(CoocDataInputBean.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                sessionBean.setFlowState(new FlowFailed(jobId, awaitingParameters, "could not populate data in sheets field"));
                return Collections.EMPTY_LIST;
            }
        } else {
            return Collections.EMPTY_LIST;
        }
    }

    private void sheetModelToCooccurrences() {
           try {
            Globals globals = new Globals(applicationProperties.getTempFolderFullPath());
            Path tempDataPathToSheetModel = globals.getDataSheetPath(jobId);
            byte[] byteArray = Files.readAllBytes(tempDataPathToSheetModel);
            List<SheetModel> sheets = null;
            try (ByteArrayInputStream bis = new ByteArrayInputStream(byteArray); ObjectInputStream ois = new ObjectInputStream(bis)) {
                Object obj = ois.readObject();
                sheets = (List<SheetModel>) obj;
            } catch (IOException | ClassNotFoundException ex) {
                LOG.log(Level.SEVERE, "error in deserializing sheets to cooccurrences in job " + jobId, ex);
            }
            SheetModel sheetWithData = sheets.get(activeSheetIndex);
            Map<Integer, List<CellRecord>> mapOfCellRecordsPerRow = sheetWithData.getRowIndexToCellRecords();
            Map<Integer, Multiset<String>> lines = new HashMap();

            Iterator<Map.Entry<Integer, List<CellRecord>>> iterator = mapOfCellRecordsPerRow.entrySet().iterator();
            int i = 0;
            Multiset<String> multiset;
            while (iterator.hasNext()) {
                Map.Entry<Integer, List<CellRecord>> next = iterator.next();
                multiset = new Multiset();
                for (CellRecord cr : next.getValue()) {
                    multiset.addOne(cr.getRawValue());
                }
                lines.put(i++, multiset);
            }
            if (hasHeaders && !lines.isEmpty()) {
                lines.remove(0);
            }
            byte[] coocsAsByteArray = Converters.byteArraySerializerForAnyObject(lines);

            WorkflowCoocProps coocProps = new WorkflowCoocProps(applicationProperties.getTempFolderFullPath());
            Files.write(coocProps.getPathForCooccurrencesFormattedAsMap(jobId), coocsAsByteArray);
            Files.deleteIfExists(tempDataPathToSheetModel);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "error in processing sheets to cooccurrences in job " + jobId, ex);
        }
    }

    public boolean isDataReady() {
        return sessionBean.getFlowState() instanceof CoocState.DataImported;
    }

    public String proceedToParameters() {
        if (sessionBean.getFlowState() instanceof CoocState.DataImported importedState) {
            sheetModelToCooccurrences();
            // 1 is the default value for min targets, this param can be adjusted at the parameters page
            sessionBean.setFlowState(new CoocState.AwaitingParameters(importedState.jobId(), 1));
            return "/cooc/cooc-parameters.xhtml?faces-redirect=true";
        } else {
            sessionBean.addMessage(FacesMessage.SEVERITY_ERROR, "Error", "You must first upload a file.");
            return null;
        }
    }

    public List<SheetModel> getDataInSheets() {
        if (dataInSheets == null || dataInSheets.isEmpty()) {
            dataInSheets = populateDataInSheetsVariable();
        }
        for (SheetModel sheet : dataInSheets) {
            sheet.setHasHeaders(hasHeaders);
        }
        return dataInSheets;
    }

    public void setDataInSheets(List<SheetModel> dataInSheets) {
        this.dataInSheets = dataInSheets;
    }

    public Boolean getHasHeaders() {
        return hasHeaders;
    }

    public void setHasHeaders(Boolean hasHeaders) {
        this.hasHeaders = hasHeaders;
    }

    public Integer getActiveSheetIndex() {
        return activeSheetIndex;
    }

    public void setActiveSheetIndex(Integer activeSheetIndex) {
        this.activeSheetIndex = activeSheetIndex;
    }
    
}
