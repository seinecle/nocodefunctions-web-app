/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.functions;

import it.uniroma1.dis.wsngroup.gexf4j.core.Gexf;
import it.uniroma1.dis.wsngroup.gexf4j.core.impl.StaxGraphWriter;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.enterprise.context.SessionScoped;
import javax.faces.application.FacesMessage;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.annotation.MultipartConfig;
import net.clementlevallois.gexfvosviewerjson.GexfToVOSViewerJson;
import net.clementlevallois.gexfvosviewerjson.Metadata;
import net.clementlevallois.gexfvosviewerjson.Terminology;
import net.clementlevallois.gexfvosviewerjson.VOSViewerJsonToGexf;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.io.GEXFSaver;
import net.clementlevallois.nocodeapp.web.front.logview.NotificationService;
import org.omnifaces.util.Faces;
import org.openide.util.Exceptions;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;
import org.primefaces.model.file.UploadedFile;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped
@MultipartConfig
public class ConverterBean implements Serializable {

    private UploadedFile uploadedFile;
    private String option = "sourceGexf";
    private String item = "item_name";
    private String link = "link_name";
    private String linkStrength = "link strength name";
    private InputStream is;
    private String uploadButtonMessage;
    private boolean renderGephiWarning = true;

    private boolean shareVVPublicly;

    private StreamedContent fileToSave;

    @Inject
    NotificationService service;

    @Inject
    SessionBean sessionBean;

    public ConverterBean() {
        if (sessionBean == null) {
            sessionBean = new SessionBean();
        }
        sessionBean.setFunction("networkconverter");
        sessionBean.sendFunctionPageReport();
    }
    
    @PostConstruct
        void init(){
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

    public String handleFileUpload(FileUploadEvent event) {
        System.out.println("we are in handleFileUpload");
        String success = sessionBean.getLocaleBundle().getString("general.nouns.success");
        String is_uploaded = sessionBean.getLocaleBundle().getString("general.verb.is_uploaded");
        FacesMessage message = new FacesMessage(success, event.getFile().getFileName() + " " + is_uploaded + ".");
        FacesContext.getCurrentInstance().addMessage(null, message);
        uploadedFile = event.getFile();
//        System.out.println("file: " + uploadedFile.getFileName());
        try {
            is = uploadedFile.getInputStream();
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }
        return "";
    }

    public void gotoVV() throws IOException {
        if (uploadedFile == null) {
            System.out.println("no file found for conversion to vv");
            return;
        }
//        System.out.println("file: " + uploadedFile.getFileName());
        GexfToVOSViewerJson converter = new GexfToVOSViewerJson(is);
        converter.setMaxNumberNodes(500);
        converter.setTerminologyData(new Terminology());
        converter.getTerminologyData().setItem(item);
        converter.getTerminologyData().setItems("");
        converter.getTerminologyData().setLink(link);
        converter.getTerminologyData().setLinks("");
        converter.getTerminologyData().setLink_strength(linkStrength);
        converter.getTerminologyData().setTotal_link_strength("");
        converter.setMetadataData(new Metadata());
        converter.getMetadataData().setAuthorCanBePlural("");
        converter.getMetadataData().setDescriptionOfData("Made with nocodefunctions.com");

        String convertToJson = converter.convertToJson();
        String path = RemoteLocal.isLocal() ? "C:\\Users\\levallois\\Google Drive\\open\\GexfVosViewerJson\\testswebapp\\" : "html/vosviewer/data/";
        String subfolder;
        String vosviewerJsonFileName = "vosviewer_" + Faces.getSessionId().substring(0, 20) + ".json";
        if (shareVVPublicly) {
            subfolder = "public/";
        } else {
            subfolder = "private/";
        }

        path = path + subfolder;

        BufferedWriter bw = Files.newBufferedWriter(Path.of(path + vosviewerJsonFileName), StandardCharsets.UTF_8);
        bw.write(convertToJson);
        bw.close();

        ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
        String urlVV;
        if (RemoteLocal.isTest()) {
            urlVV = "https://test.nocodefunctions.com/html/vosviewer/index.html?json=data/" + subfolder + vosviewerJsonFileName;
        } else {
            urlVV = "https://nocodefunctions.com/html/vosviewer/index.html?json=data/" + subfolder + vosviewerJsonFileName;
        }
        externalContext.redirect(urlVV);
    }

    public String getOption() {
        return option;
    }

    public void setOption(String option) {
        this.option = option;
        if (option.equals("sourceGexf")) {
            setUploadButtonMessage(sessionBean.getLocaleBundle().getString("general.message.choose_gexf_file"));
            renderGephiWarning = true;
        }
        if (option.equals("sourceVV")) {
            setUploadButtonMessage(sessionBean.getLocaleBundle().getString("general.message.choose_vosviewer_file"));
            renderGephiWarning = false;
        }
    }

    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getLinkStrength() {
        return linkStrength;
    }

    public void setLinkStrength(String linkStrength) {
        this.linkStrength = linkStrength;
    }

    public String getUploadButtonMessage() {
        return uploadButtonMessage;
    }

    public void setUploadButtonMessage(String uploadButtonMessage) {
        this.uploadButtonMessage = uploadButtonMessage;
    }

    public boolean isShareVVPublicly() {
        return shareVVPublicly;
    }

    public void setShareVVPublicly(boolean shareVVPublicly) {
        this.shareVVPublicly = shareVVPublicly;
    }

    public boolean isRenderGephiWarning() {
        return renderGephiWarning;
    }

    public void setRenderGephiWarning(boolean renderGephiWarning) {
        this.renderGephiWarning = renderGephiWarning;
    }

    public StreamedContent getFileToSave() throws FileNotFoundException {
        if (is == null) {
            System.out.println("no file found for conversion to gephi");
            return null;
        }

        VOSViewerJsonToGexf converter = new VOSViewerJsonToGexf(is);
        Gexf gexf = converter.convertToGexf();

        String uniqueId = UUID.randomUUID().toString().substring(0, 5);
        String graphFileName = "graph_" + uniqueId + ".gexf";
        File tempFileForDownload = new File(graphFileName);

        StaxGraphWriter graphWriter = new StaxGraphWriter();

        Writer out;
        try {
            out = new OutputStreamWriter(new FileOutputStream(graphFileName), StandardCharsets.UTF_8.name());
            graphWriter.writeToStream(gexf, out, tempFileForDownload.getAbsolutePath(), StandardCharsets.UTF_8.name());
//            System.out.println(tempFileForDownload.getAbsolutePath());
        } catch (IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        StreamedContent fileStream = null;
        try {
            byte[] readAllBytes = Files.readAllBytes(tempFileForDownload.toPath());
            InputStream inputStreamToSave = new ByteArrayInputStream(readAllBytes);
            fileStream = DefaultStreamedContent.builder()
                    .name("results.gexf")
                    .contentType("application/gexf+xml")
                    .stream(() -> inputStreamToSave)
                    .build();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            // clean the temp file created
            Files.delete(tempFileForDownload.toPath());
        } catch (IOException ex) {
            System.out.println("could not delete temp graph file");
            Logger.getLogger(GEXFSaver.class.getName()).log(Level.SEVERE, null, ex);
        }

        return fileStream;
    }

    public void setFileToSave(StreamedContent fileToSave) {
        this.fileToSave = fileToSave;
    }

}
