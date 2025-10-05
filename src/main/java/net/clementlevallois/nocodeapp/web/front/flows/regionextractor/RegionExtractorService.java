package net.clementlevallois.nocodeapp.web.front.flows.regionextractor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import net.clementlevallois.functions.model.FunctionRegionExtract;
import static net.clementlevallois.functions.model.FunctionRegionExtract.QueryParams.ALL_PAGES;
import static net.clementlevallois.functions.model.FunctionRegionExtract.QueryParams.HEIGHT;
import static net.clementlevallois.functions.model.FunctionRegionExtract.QueryParams.LEFT_CORNER_X;
import static net.clementlevallois.functions.model.FunctionRegionExtract.QueryParams.LEFT_CORNER_Y;
import static net.clementlevallois.functions.model.FunctionRegionExtract.QueryParams.SELECTED_PAGES;
import static net.clementlevallois.functions.model.FunctionRegionExtract.QueryParams.WIDTH;
import net.clementlevallois.functions.model.Globals;
import static net.clementlevallois.functions.model.Globals.GlobalQueryParams.CALLBACK_URL;
import static net.clementlevallois.functions.model.Globals.GlobalQueryParams.JOB_ID;
import net.clementlevallois.importers.model.ImagesPerFile;
import net.clementlevallois.nocodeapp.web.front.MessageFromApi;
import static net.clementlevallois.nocodeapp.web.front.MessageFromApi.Information.ERROR;
import static net.clementlevallois.nocodeapp.web.front.MessageFromApi.Information.PROGRESS;
import net.clementlevallois.nocodeapp.web.front.WatchTower;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.exceptions.NocodeApplicationException;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowFailed;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;
import net.clementlevallois.nocodeapp.web.front.flows.regionextractor.RegionExtractorState.RegionDefinition;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import org.primefaces.model.CroppedImage;

/**
 *
 * @author clevallois
 */
@ApplicationScoped
public class RegionExtractorService {

    @Inject
    private MicroserviceHttpClient microserviceClient;

    @Inject
    private ApplicationPropertiesBean applicationProperties;

    @Inject
    private SessionBean sessionBean;

    public RegionExtractorState.RegionDefinition defineRegion(RegionExtractorState.RegionDefinition currentState) {
        RegionExtractorState.RegionParameters regionParameters = currentState.regionParameters();
        if (regionParameters.selectedRegion() == null) {
            return currentState;
        }
        CroppedImage croppedImage = regionParameters.selectedRegion();

        if (croppedImage == null) {
            return currentState;
        }

        float proportionTopLeftX = (float) croppedImage.getLeft() / regionParameters.pageWidth();
        float proportionTopLeftY = (float) croppedImage.getTop() / regionParameters.pageHeight();
        float proportionWidth = (float) croppedImage.getWidth() / regionParameters.pageWidth();
        float proportionHeight = (float) croppedImage.getHeight() / regionParameters.pageHeight();

        RegionExtractorState.RegionParameters updatedRegionParameters = regionParameters.withProportionTopLeftX(proportionTopLeftX).withProportionTopLeftY(proportionTopLeftY).withProportionWidth(proportionWidth).withProportionHeight(proportionHeight);
        RegionExtractorState.RegionDefinition newState = new RegionExtractorState.RegionDefinition(currentState.jobId(), currentState.imagesPerFile(), updatedRegionParameters);
        sessionBean.setFlowState(newState);
        return newState;
    }

    public RegionExtractorState.RegionDefinition getDocumentDimensions(RegionExtractorState.RegionDefinition state) {
        ImagesPerFile imagesPerFile = state.imagesPerFile();
        if (imagesPerFile == null) {
            throw new IllegalStateException("imagesPerFile was null " + sessionBean.getFlowState().getClass().getSimpleName());
        }
        byte[] imageBytes = imagesPerFile.getImage(state.regionParameters().selectedPage());
        if (imageBytes == null) {
            throw new IllegalStateException("image bytes was null " + sessionBean.getFlowState().getClass().getSimpleName());
        }

        try (InputStream is = new ByteArrayInputStream(imageBytes)) {
            BufferedImage buf = ImageIO.read(is);
            if (buf == null) {
                throw new IllegalStateException("BufferImage was null " + sessionBean.getFlowState().getClass().getSimpleName());
            }
            int widthImage = buf.getWidth();
            int heightImage = buf.getHeight();
            RegionExtractorState.RegionParameters regionParameters = state.regionParameters();
            regionParameters = regionParameters.withPageHeight(heightImage);
            regionParameters = regionParameters.withPageWidth(widthImage);
            RegionDefinition updatedRegionDefinition = new RegionDefinition(state.jobId(), imagesPerFile, regionParameters);
            sessionBean.setFlowState(updatedRegionDefinition);
            return updatedRegionDefinition;
        } catch (IOException ex) {
            throw new NocodeApplicationException("Error in getDocumentDimensions method: Failed to read image dimensions for jobId: " + state.jobId(), ex);
        }
    }

    public FlowState callMicroService(RegionExtractorState.TargetPdfsUploaded current) {
        String jobId = current.jobId();
        RegionExtractorState.RegionParameters params = current.regionParameters();
        AtomicReference<FlowFailed> errorFlow = new AtomicReference<>();
        AtomicReference<Boolean> isOk = new AtomicReference<>(true);

        var requestBuilder = microserviceClient.importService().get(FunctionRegionExtract.ENDPOINT);

        for (FunctionRegionExtract.QueryParams param : FunctionRegionExtract.QueryParams.values()) {
            String value = switch (param) {
                case FILE_NAME_PREFIX ->
                    Globals.UPLOADED_FILE_PREFIX;
                case ALL_PAGES ->
                    String.valueOf(params.allPages());
                case SELECTED_PAGES ->
                    String.valueOf(params.selectedPage());
                case LEFT_CORNER_X ->
                    String.valueOf(params.proportionTopLeftX());
                case LEFT_CORNER_Y ->
                    String.valueOf(params.proportionTopLeftY());
                case WIDTH ->
                    String.valueOf(params.proportionWidth());
                case HEIGHT ->
                    String.valueOf(params.proportionHeight());
            };
            requestBuilder.addQueryParameter(param.name(), value);
        }

        String callbackURL = RemoteLocal.getDomain() + RemoteLocal.getInternalMessageApiEndpoint() + FunctionRegionExtract.NAME;

        for (Globals.GlobalQueryParams param : Globals.GlobalQueryParams.values()) {
            String paramValue = switch (param) {
                case JOB_ID ->
                    jobId;
                case CALLBACK_URL ->
                    callbackURL;
            };
            requestBuilder.addQueryParameter(param.name(), paramValue);
        }

        requestBuilder.sendAsync(HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                    if (resp.statusCode() != 200) {
                        errorFlow.set(new FlowFailed(jobId, current, "region extractor call failed with non-200 status"));
                        isOk.set(Boolean.FALSE);
                    }
                })
                .exceptionally(ex -> {
                    errorFlow.set(new FlowFailed(jobId, current, "region extractor call threw exception"));
                    isOk.set(Boolean.FALSE);
                    return null;
                });
        return isOk.get() ? new RegionExtractorState.Processing(jobId, 0) : errorFlow.get();
    }

    public FlowState checkCompletion(RegionExtractorState.Processing currentState) {
        String jobId = currentState.jobId();
        Globals globals = new Globals(applicationProperties.getTempFolderFullPath());
        Path completeSignal = globals.getResultInBinaryFormat(jobId);

        if (Files.exists(completeSignal)) {
            RegionExtractorState updatedState = new RegionExtractorState.ResultsReady(jobId);
            sessionBean.setFlowState(updatedState);
            return updatedState;
        } else {
            var messages = WatchTower.getDequeAPIMessages().get(jobId);
            if (messages != null) {
                for (MessageFromApi msg : messages) {
                    if (jobId.equals(msg.getjobId())) {
                        switch (msg.getInfo()) {
                            case ERROR -> {
                                messages.remove(msg);
                                return new FlowFailed(jobId, currentState, msg.getMessage());
                            }
                            case PROGRESS -> {
                                if (msg.getProgress() != null) {
                                    messages.remove(msg);
                                    return currentState.withProgress(msg.getProgress());
                                }
                            }
                        }
                    }
                }
            }
        }
        return currentState;
    }
}
