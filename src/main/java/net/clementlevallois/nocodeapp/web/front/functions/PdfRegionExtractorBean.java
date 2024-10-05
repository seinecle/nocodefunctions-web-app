package net.clementlevallois.nocodeapp.web.front.functions;

import io.mikael.urlbuilder.UrlBuilder;
import java.io.Serializable;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonWriter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import net.clementlevallois.importers.model.SheetModel;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;
import org.primefaces.model.CroppedImage;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped

public class PdfRegionExtractorBean implements Serializable {

    private Integer progress;
    private Boolean renderProgressBar = false;
    private Boolean runButtonDisabled = true;
    private StreamedContent fileToSave;
    private Boolean renderSeeResultsButton = false;
    private CroppedImage[] selectedRegions;
    private boolean allPages;
    private boolean selectPage;
    private int selectedPage;
    private float proportionTopLeftX;
    private float proportionTopLeftY;
    private float proportionWidth;
    private float proportionHeight;
    private ConcurrentHashMap<String, SheetModel> results = new ConcurrentHashMap();
    private Properties privateProperties;

    @Inject
    BackToFrontMessengerBean logBean;

    @Inject
    SessionBean sessionBean;

    @Inject
    DataImportBean inputData;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    public PdfRegionExtractorBean() {
    }

    @PostConstruct
    public void init() {
        sessionBean.setFunction("pdf_region_extractor");
        privateProperties = applicationProperties.getPrivateProperties();
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public String goToPdfUpload() {
        return "/import/import_your_pdf_choose_region.xhtml?function=pdf_region_extractor&amp;faces-redirect=true";
    }

    public void fillInCoordinates() {
        try {
            byte[] firstImage = inputData.getImagesPerFiles().get(0).getImages()[0];
            try (InputStream is = new ByteArrayInputStream(firstImage)) {
                BufferedImage buf = ImageIO.read(is);
                int heightImage = buf.getHeight();
                int widthImage = buf.getWidth();
                CroppedImage selectedRegion;
                if (allPages) {
                    selectedRegion = selectedRegions[0];
                } else {
                    selectedRegion = selectedRegions[selectedPage];
                }
                if (selectedRegion == null) {
                    System.out.println("selected region is null");
                    return;
                }
                int leftCornerX = selectedRegion.getLeft();
                int leftCornerY = selectedRegion.getTop();
                int height = selectedRegion.getHeight();
                int width = selectedRegion.getWidth();

                proportionTopLeftX = (float) leftCornerX / (float) widthImage;
                proportionTopLeftY = (float) leftCornerY / (float) heightImage;
                proportionWidth = (float) width / (float) widthImage;
                proportionHeight = (float) height / (float) heightImage;
            }
        } catch (IOException ex) {
            Logger.getLogger(PdfRegionExtractorBean.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public CroppedImage getSelectedRegion() {
        if (selectedRegions == null) {
            selectedRegions = new CroppedImage[inputData.getImagesPerFiles().get(0).getImages().length];
            for (int i = 0; i < selectedRegions.length; i++) {
                selectedRegions[i] = new CroppedImage();
            }
        }
        FacesContext context = FacesContext.getCurrentInstance();
        String index = context.getExternalContext().getRequestParameterMap().get("rowIndex2");
        if (index == null) {
            return null;
        }
        return selectedRegions[Integer.parseInt(index)];
    }

    public void setSelectedRegion(CroppedImage selectedRegion) {
        if (selectedRegions == null) {
            selectedRegions = new CroppedImage[inputData.getImagesPerFiles().get(0).getImages().length];
        }
        FacesContext context = FacesContext.getCurrentInstance();
        String index = context.getExternalContext().getRequestParameterMap().get("rowIndex2");
        if (index == null) {
            return;
        }
        if (Integer.parseInt(index) < selectedRegions.length) {
            selectedRegions[Integer.parseInt(index)] = selectedRegion;
        } else {
            logBean.addOneNotificationFromString("😭 error with the selected region");
        }
    }

    public String extract() throws FileNotFoundException, IOException {
        fillInCoordinates();

        sessionBean.sendFunctionPageReport();
        logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.starting_analysis"));
        Map<String, String> pdfs = inputData.getPdfsToBeExtracted();
        HttpRequest request;
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMinutes(10)).build();
        Set<CompletableFuture> futures = new HashSet();
        results = new ConcurrentHashMap();
        HttpRequest.BodyPublisher bodyPublisher;

        URI uri = UrlBuilder
                .empty()
                .withScheme("http")
                .withPort(Integer.valueOf(privateProperties.getProperty("nocode_import_port")))
                .withHost("localhost")
                .addParameter("owner", privateProperties.getProperty("pwdOwner"))
                .withPath("api/import/pdf/extract-region")
                .toUri();

        for (Map.Entry<String, String> pdf : pdfs.entrySet()) {

            JsonObjectBuilder overallObject = Json.createObjectBuilder();

            overallObject.add("pdfBytes", pdf.getValue());
            overallObject.add("fileName", pdf.getKey());
            overallObject.add("allPages", allPages);
            if (allPages == false) {
                overallObject.add("selectedPage", selectedPage);
            }
            overallObject.add("leftCornerX", proportionTopLeftX);
            overallObject.add("leftCornerY", proportionTopLeftY);
            overallObject.add("width", proportionWidth);
            overallObject.add("height", proportionHeight);

            JsonObject build = overallObject.build();
            StringWriter sw = new StringWriter(128);
            try (JsonWriter jw = Json.createWriter(sw)) {
                jw.write(build);
            }
            String jsonString = sw.toString();

            bodyPublisher = HttpRequest.BodyPublishers.ofString(jsonString);

            request = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .uri(uri)
                    .build();

            CompletableFuture<Void> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenAccept(resp -> {
                byte[] body = resp.body();
                if (resp.statusCode() == 200) {
                    try (
                            ByteArrayInputStream bis = new ByteArrayInputStream(body); ObjectInputStream ois = new ObjectInputStream(bis)) {
                        SheetModel resultsAsSheet = (SheetModel) ois.readObject();
                        results.put(resultsAsSheet.getName(), resultsAsSheet);
                    } catch (IOException | ClassNotFoundException ex) {
                        Logger.getLogger(PdfMatcherBean.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } else {
                    System.out.println("call to extract pdf region returned an error");
                    System.out.println("status code is: " + resp.statusCode());
                    System.out.println("body is: " + new String(resp.body()));
                }
            }
            );
            futures.add(future);
        }

        this.progress = 40;
        logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.almost_done"));

        CompletableFuture<Void> combinedFuture = CompletableFuture.allOf(futures.toArray((new CompletableFuture[0])));
        combinedFuture.join();

        return "/" + sessionBean.getFunction() + "/results.xhtml?faces-redirect=true";

    }

    public StreamedContent getFileToSave() {
        try {
            HttpRequest request;
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMinutes(10)).build();
            byte[] documentsAsByteArray = Converters.byteArraySerializerForAnyObject(results);
            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(documentsAsByteArray);

            String lang = FacesContext.getCurrentInstance().getViewRoot().getLocale().toLanguageTag();

            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(Integer.valueOf(privateProperties.getProperty("nocode_import_port")))
                    .withHost("localhost")
                    .withPath("api/export/xlsx/pdf_region_extractor")
                    .addParameter("file name", lang)
                    .addParameter("text extracted", lang)
                    .toUri();

            request = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .uri(uri)
                    .build();

            HttpResponse<byte[]> resp = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            try {
                byte[] body = resp.body();
                try (InputStream is = new ByteArrayInputStream(body)) {
                    fileToSave = DefaultStreamedContent.builder()
                            .name("results.xlsx")
                            .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                            .stream(() -> is)
                            .build();
                }
            } catch (IOException ex) {
                Logger.getLogger(PdfRegionExtractorBean.class.getName()).log(Level.SEVERE, null, ex);
            }
        } catch (IOException ex) {
            Logger.getLogger(UmigonBean.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            Logger.getLogger(PdfRegionExtractorBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        return fileToSave;
    }

    public void setFileToSave(StreamedContent fileToSave) {
        this.fileToSave = fileToSave;
    }

    public void onComplete() {
    }

    public void cancel() {
        progress = null;
    }

    public Boolean getRenderSeeResultsButton() {
        return renderSeeResultsButton;
    }

    public void setRenderSeeResultsButton(Boolean renderSeeResultsButton) {
        this.renderSeeResultsButton = renderSeeResultsButton;
    }

    public Boolean getRunButtonDisabled() {
        return runButtonDisabled;
    }

    public void setRunButtonDisabled(Boolean runButtonDisabled) {
        this.runButtonDisabled = runButtonDisabled;
    }

    public void dummy() {
    }

    public boolean isAllPages() {
        return allPages;
    }

    public void setAllPages(boolean allPages) {
        this.allPages = allPages;
    }

    public boolean isSelectPage() {
        return selectPage;
    }

    public void setSelectPage(boolean selectPage) {
        FacesContext context = FacesContext.getCurrentInstance();
        String index = context.getExternalContext().getRequestParameterMap().get("rowIndex3");
        if (index != null) {
            selectedPage = Integer.parseInt(index);
            this.selectPage = selectPage;
        }
    }

    public Boolean getRenderProgressBar() {
        return renderProgressBar;
    }

    public void setRenderProgressBar(Boolean renderProgressBar) {
        this.renderProgressBar = renderProgressBar;
    }
}
