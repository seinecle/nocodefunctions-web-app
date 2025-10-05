package net.clementlevallois.nocodeapp.web.front.flows.vv2gexf;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.functions.model.FunctionNetworkConverter;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.nocodeapp.web.front.exceptions.NocodeApplicationException;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient.MicroserviceCallException;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;

@ApplicationScoped
public class Vv2gexfService {

    @Inject
    private MicroserviceHttpClient microserviceClient;

    /**
     * Appelle le microservice de conversion VOSviewer JSON -> GEXF. Le fichier
     * JSON a été préalablement écrit dans le dossier temp avec le nom == jobId
     * (convention historique de ConverterBean).
     * @param jobId
     * @return 
     */
    public byte[] convert(String jobId) {
        try {
            var callbackURL = RemoteLocal.getDomain() + RemoteLocal.getInternalMessageApiEndpoint()
                    + FunctionNetworkConverter.ENDPOINT;

            var request = microserviceClient.api()
                    .get(FunctionNetworkConverter.ENDPOINT_VV_TO_GEXF)
                    .addQueryParameter(Globals.GlobalQueryParams.JOB_ID.name(), jobId)
                    .addQueryParameter(Globals.GlobalQueryParams.CALLBACK_URL.name(), callbackURL);

            // Réponse attendue: bytes GEXF (synchrones)
            return request.sendAsync(HttpResponse.BodyHandlers.ofByteArray())
                    .thenApply(HttpResponse::body)
                    .join();

        } catch (CompletionException ce) {
            Throwable cause = ce.getCause() != null ? ce.getCause() : ce;
            if (cause instanceof MicroserviceCallException msce) {
                throw new NocodeApplicationException("Conversion VV->GEXF failed (status " + msce.getStatusCode() + ")", msce);
            } else {
                throw new NocodeApplicationException("Error when calling microservice VV->GEXF", cause);
            }
        } catch (Exception e) {
            throw new NocodeApplicationException("Error while calling microservice VV->GEXF", e);
        }
    }
}
