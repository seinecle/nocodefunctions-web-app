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
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.functions.model.WorkflowCoocProps;
import net.clementlevallois.importers.model.CellRecord;
import net.clementlevallois.importers.model.SheetModel;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.exceptions.NocodeApplicationException;
import net.clementlevallois.nocodeapp.web.front.io.ImportersService;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
import net.clementlevallois.utils.Multiset;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.file.UploadedFile;

@Named
@ViewScoped
public class CoocDataInputBean implements Serializable {

    private List<SheetModel> dataInSheets;
    private boolean hasHeaders;
    private Integer activeSheetIndex = 0;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    private ImportersService importersService;

    @Inject
    private SessionBean sessionBean;

    @PostConstruct
    public void init() {
        sessionBean.setFlowState(new CoocState.AwaitingData(null // jobId to be set later
        ));
    }

    public void handleFileUpload(FileUploadEvent event) {
        if (event.getFile() == null) {
            sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "File Upload Error", "No file was uploaded.");
            return;
        }
        CoocDataSource dataSource = new CoocDataSource.FileUpload(List.of(event.getFile()));
        processCoocDataSource(dataSource);
    }

    private boolean processCoocDataSource(CoocDataSource dataSource) {
        if (!(sessionBean.getFlowState() instanceof CoocState.AwaitingData)) {
            throw new IllegalStateException("wrong state " + sessionBean.getFlowState().getClass().getSimpleName());
        }

        String jobId = UUID.randomUUID().toString().substring(0, 10);
        Path jobDirectory = applicationProperties.getTempFolderFullPath().resolve(jobId);
        try {
            Files.createDirectories(jobDirectory);
        } catch (IOException ex) {
            throw new NocodeApplicationException("Error in processSpatializationResults method: Failed to read GEXF results for jobId: " + jobId, ex);
        }

        sessionBean.sendFunctionPageReport(Globals.Names.COOC.name());

        ImportersService.PreparationResult result;
        if (dataSource instanceof CoocDataSource.FileUpload fileUpload) {
            result = importersService.handleFileUpload(fileUpload.files(), jobId, Globals.Names.COOC);
        } else {
            throw new IllegalArgumentException("Unsupported data source type");
        }

        if (result instanceof ImportersService.PreparationResult.Failure(String error)) {
            throw new IllegalStateException("Cannot proceed because data preparation failed: "
                    + sessionBean.getFlowState().getClass().getSimpleName());
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
        String jobId = sessionBean.getFlowState().jobId();

        Globals globals = new Globals(applicationProperties.getTempFolderFullPath());
        Path sheetsModelFile = globals.getDataSheetPath(jobId);
        if (Files.exists(sheetsModelFile)) {
            try {
                byte[] fileBytes = Files.readAllBytes(sheetsModelFile);

                try (ByteArrayInputStream bis = new ByteArrayInputStream(fileBytes); ObjectInputStream ois = new ObjectInputStream(bis)) {
                    return (List<SheetModel>) ois.readObject();
                } catch (IOException | ClassNotFoundException ex) {
                    throw new NocodeApplicationException("An IO error occurred", ex);
                }
            } catch (IOException ex) {
                throw new NocodeApplicationException("An IO error occurred", ex);
            }
        } else {
            throw new IllegalStateException("Cannot proceed because sheetsModelFile does not exist: "
                    + sessionBean.getFlowState().getClass().getSimpleName());
        }
    }

    private void sheetModelToCooccurrences() {
        try {
            String jobId = sessionBean.getFlowState().jobId();
            Globals globals = new Globals(applicationProperties.getTempFolderFullPath());
            Path tempDataPathToSheetModel = globals.getDataSheetPath(jobId);
            byte[] byteArray = Files.readAllBytes(tempDataPathToSheetModel);
            List<SheetModel> sheets = null;
            try (ByteArrayInputStream bis = new ByteArrayInputStream(byteArray); ObjectInputStream ois = new ObjectInputStream(bis)) {
                Object obj = ois.readObject();
                sheets = (List<SheetModel>) obj;
            } catch (IOException | ClassNotFoundException ex) {
                throw new NocodeApplicationException("An error occurred", ex);
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
            throw new NocodeApplicationException("An IO error occurred", ex);
        }
    }

    public boolean isDataReady() {
        return sessionBean.getFlowState() instanceof CoocState.DataImported;
    }

    public String proceedToParameters() {
        if (sessionBean.getFlowState() instanceof CoocState.DataImported importedState) {
            sheetModelToCooccurrences();
            sessionBean.setFlowState(new CoocState.AwaitingParameters(importedState.jobId(), 1));
            return "/cooc/cooc-parameters.xhtml?faces-redirect=true";
        } else {
            throw new IllegalStateException("Cannot proceed because the current state is "
                    + sessionBean.getFlowState().getClass().getSimpleName()
                    + " instead of DataImported.");
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
