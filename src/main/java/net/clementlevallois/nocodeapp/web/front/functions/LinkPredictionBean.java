/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.functions;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.List;
import java.util.UUID;
import javax.annotation.PostConstruct;
import javax.enterprise.context.SessionScoped;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.annotation.MultipartConfig;
import net.clementlevallois.linkprediction.controller.LinkPredictionController;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.io.GEXFSaver;
import net.clementlevallois.nocodeapp.web.front.logview.NotificationService;
import org.openide.util.Exceptions;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.file.UploadedFile;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped
@MultipartConfig
public class LinkPredictionBean implements Serializable {

    private UploadedFile uploadedFile;
    private InputStream is;
    private String uploadButtonMessage;
    private boolean renderGephiWarning = true;
    private int nbPredictions = 1;
    LinkPredictionController predictor;
    List<LinkPredictionController.LinkPredictionProbability> topPredictions;
    private LinkPredictionController.LinkPredictionProbability selectedLink;

    private StreamedContent fileToSave;

    private boolean success = false;

    @Inject
    NotificationService service;

    @Inject
    SessionBean sessionBean;

    public LinkPredictionBean() {
        if (sessionBean == null) {
            sessionBean = new SessionBean();
        }
        sessionBean.setFunction("linkprediction");
        sessionBean.sendFunctionPageReport();
    }

    @PostConstruct
    void init() {
        uploadButtonMessage = sessionBean.getLocaleBundle().getString("general.message.choose_gexf_file");
    }

    public String logout() {
        FacesContext.getCurrentInstance().getExternalContext().invalidateSession();
        return "/index?faces-redirect=true";
    }

    public UploadedFile getUploadedFile() {
        return uploadedFile;
    }

    public void setUploadedFile(UploadedFile uploadedFile) {
        this.uploadedFile = uploadedFile;
    }

    public void upload() {
        if (uploadedFile != null) {
            String success = sessionBean.getLocaleBundle().getString("general.nouns.success");
            String is_uploaded = sessionBean.getLocaleBundle().getString("general.verb.is_uploaded");
            FacesMessage message = new FacesMessage(success, uploadedFile.getFileName() + " " + is_uploaded + ".");
            FacesContext.getCurrentInstance().addMessage(null, message);
        }
    }

    public String handleFileUpload(FileUploadEvent event) throws IOException {
        success = false;
        System.out.println("we are in handleFileUpload");
        String successMsg = sessionBean.getLocaleBundle().getString("general.nouns.success");
        String is_uploaded = sessionBean.getLocaleBundle().getString("general.verb.is_uploaded");
        FacesMessage message = new FacesMessage(successMsg, event.getFile().getFileName() + " " + is_uploaded + ".");
        FacesContext.getCurrentInstance().addMessage(null, message);
        uploadedFile = event.getFile();
        System.out.println("file: " + uploadedFile.getFileName());
        try {
            is = uploadedFile.getInputStream();
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        showPredictions();
        return "";
    }

    public void showPredictions() throws IOException {
        if (uploadedFile == null) {
            System.out.println("no file found for link prediction");
            return;
        }
        String uniqueId = UUID.randomUUID().toString().substring(0, 5);

        System.out.println("file: " + uploadedFile.getFileName());
        predictor = new LinkPredictionController();
        predictor.runPrediction(is, nbPredictions, uniqueId);
        topPredictions = predictor.getTopPredictions();
    }

    public int getNbPredictions() {
        return nbPredictions;
    }

    public void setNbPredictions(int nbPredictions) {
        this.nbPredictions = nbPredictions;
    }

    public String getUploadButtonMessage() {
        return uploadButtonMessage;
    }

    public void setUploadButtonMessage(String uploadButtonMessage) {
        this.uploadButtonMessage = uploadButtonMessage;
    }

    public boolean isRenderGephiWarning() {
        return renderGephiWarning;
    }

    public void setRenderGephiWarning(boolean renderGephiWarning) {
        this.renderGephiWarning = renderGephiWarning;
    }

    public StreamedContent getFileToSave() throws FileNotFoundException {
        success = true;
        if (is == null) {
            System.out.println("no file found for link prediction");
            return null;
        }
        return GEXFSaver.exportGexfAsStreamedFile(predictor.getWorkspace(), "initial_graph_augmented_with_predicted_links");
    }

    public void setFileToSave(StreamedContent fileToSave) {
        this.fileToSave = fileToSave;
    }

    public List<LinkPredictionController.LinkPredictionProbability> getTopPredictions() {
        return topPredictions;
    }

    public void setTopPredictions(List<LinkPredictionController.LinkPredictionProbability> topPredictions) {
        this.topPredictions = topPredictions;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public LinkPredictionController.LinkPredictionProbability getSelectedLink() {
        return selectedLink;
    }

    public void setSelectedLink(LinkPredictionController.LinkPredictionProbability selectedLink) {
        this.selectedLink = selectedLink;
    }

}
