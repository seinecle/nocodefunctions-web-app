package net.clementlevallois.nocodeapp.web.front.flows.vv2gexf;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;

// import your actual microservice client type:
import net.clementlevallois.nocodeapp.web.front.microservices.MicroserviceHttpClient;

@ApplicationScoped
public class Vv2GexfService {

    @Inject
    MicroserviceHttpClient micro;

    // Align this path with your converter microservice (same style as your other flows)
    private static final String ENDPOINT = "/converter/v1/vv-json-to-gexf";

    /**
     * Sends the VV JSON to the microservice and writes the returned GEXF to disk.
     * Returns the path to the written .gexf (in the same job folder).
     */
    public Path convert(String jobId, Path vvJson) {
        try {
            Objects.requireNonNull(vvJson, "vvJson");
            Path jobDir = vvJson.getParent();
            if (jobDir == null) jobDir = Files.createTempDirectory("vv2gexf-" + jobId + "-");
            Path out = jobDir.resolve(jobId + "-from-vv.json.gexf");
            var response = micro.api()
                    .get(jobId)query("jobId", jobId)                 // optional, if your MS expects it
                    .multipart("file", vvJson)             // attach the JSON file (param name "file" or whatever your MS expects)
                    .send();                                // executes the HTTP request
            
            int status = response.statusCode();
            if (status != 200) {
                throw new IOException("vv2gexf microservice error: HTTP " + status + " — " + response.bodyAsString());
            }
            
            byte[] gexfBytes = response.bodyAsBytes();
            Files.write(out, gexfBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            
            return out;
        } catch (IOException ex) {
            System.getLogger(Vv2GexfService.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
    }
}
