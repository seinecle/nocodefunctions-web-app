package net.clementlevallois.nocodeapp.web.front.http;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.json.JsonWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.functions.model.Globals;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;

@ApplicationScoped
public class MicroserviceHttpClient {

    private static final Logger LOG = Logger.getLogger(MicroserviceHttpClient.class.getName());

    @Inject
    private ApplicationPropertiesBean applicationProperties;

    private HttpClient httpClient;
    private Properties privateProperties;
    private static final String MICROSERVICE_ENDPOINT = Globals.API_ENDPOINT_ROOT;

    @PostConstruct
    public void init() {
        this.privateProperties = applicationProperties.getPrivateProperties();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        LOG.info("MicroserviceHttpClient initialized.");
    }

    public ServiceClientBuilder api() {
        String apiPort = privateProperties.getProperty("nocode_api_port");
        Objects.requireNonNull(apiPort, "nocode_api_port property is not set!");
        URI baseUri = buildBaseUri("localhost", apiPort);
        return new ServiceClientBuilder(httpClient, baseUri);
    }

    public ServiceClientBuilder importService() {
        String importPort = privateProperties.getProperty("nocode_import_port");
        Objects.requireNonNull(importPort, "nocode_import_port property is not set!");
        URI baseUri = buildBaseUri("localhost", importPort);
        return new ServiceClientBuilder(httpClient, baseUri);
    }

    private URI buildBaseUri(String host, String port) {
        try {
            return new URI("http", null, host, Integer.parseInt(port), MICROSERVICE_ENDPOINT, null, null);
        } catch (NumberFormatException | URISyntaxException e) {
            LOG.log(Level.SEVERE, "Invalid port number: " + port, e);
            throw new IllegalStateException("Invalid port configuration", e);
        }
    }

    public static class ServiceClientBuilder {

        private final HttpClient httpClient;
        private final URI baseUri;

        ServiceClientBuilder(HttpClient httpClient, URI baseUri) {
            this.httpClient = httpClient;
            this.baseUri = baseUri;
        }

        public GetRequestBuilder get(String path) {
            Objects.requireNonNull(path, "Request path cannot be null");
            return new GetRequestBuilder(httpClient, baseUri.resolve(path));
        }

        public PostRequestBuilder post(String path) {
            Objects.requireNonNull(path, "Request path cannot be null");
            return new PostRequestBuilder(httpClient, baseUri.resolve(path));
        }
    }

    private static abstract class AbstractRequestBuilder<B extends AbstractRequestBuilder<B>> {

        protected final HttpClient httpClient;
        protected final HttpRequest.Builder requestBuilder;
        protected URI uri;
        protected Map<String, String> queryParams = new LinkedHashMap<>();

        protected AbstractRequestBuilder(HttpClient httpClient, URI baseUri) {
            this.httpClient = httpClient;
            this.uri = baseUri;
            this.requestBuilder = HttpRequest.newBuilder();
        }

        public B addQueryParameter(String name, String value) {
            queryParams.put(name, value);
            return self();
        }

        public B addHeader(String name, String value) {
            requestBuilder.header(name, value);
            return self();
        }

        protected URI buildFinalUri() {
            try {
                if (queryParams.isEmpty()) {
                    return uri;
                }

                StringBuilder query = new StringBuilder();
                queryParams.forEach((key, value) -> {
                    if (!query.isEmpty()) {
                        query.append("&");
                    }
                    query.append(URLEncoder.encode(key, StandardCharsets.UTF_8));
                    query.append("=");
                    query.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
                });

                return URI.create(
                        new URI(
                                uri.getScheme(),
                                uri.getUserInfo(),
                                uri.getHost(),
                                uri.getPort(),
                                uri.getPath(),
                                query.toString(),
                                uri.getFragment()
                        ).toString()
                );
            } catch (URISyntaxException ex) {
                Logger.getLogger(MicroserviceHttpClient.class.getName()).log(Level.SEVERE, null, ex);
            }
            return uri;
        }

        protected abstract HttpRequest buildRequest();

        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpResponse.BodyHandler<T> bodyHandler) {
            return httpClient.sendAsync(buildRequest(), bodyHandler);
        }

        public <T> CompletableFuture<T> sendAsyncAndGetBody(HttpResponse.BodyHandler<T> bodyHandler) {
            return httpClient.sendAsync(buildRequest(), bodyHandler)
                    .thenApply(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            return response.body();
                        } else {
                            String errorBody = "Could not read error response body.";
                            try {
                                switch (response.body()) {
                                    case String string ->
                                        errorBody = string;
                                    case byte[] bs ->
                                        errorBody = new String(bs, StandardCharsets.UTF_8);
                                    default -> {
                                    }
                                }
                            } catch (Exception e) {
                            }
                            LOG.log(Level.WARNING, "Microservice call failed. Status: {0}, URI: {1}, Body: {2}",
                                    new Object[]{response.statusCode(), response.uri(), errorBody});
                            throw new MicroserviceCallException("Microservice call failed with status code " + response.statusCode(), response.statusCode(), errorBody, response.uri().toString());
                        }
                    });
        }

        @SuppressWarnings("unchecked")
        protected final B self() {
            return (B) this;
        }
    }

    public static class GetRequestBuilder extends AbstractRequestBuilder<GetRequestBuilder> {

        private GetRequestBuilder(HttpClient httpClient, URI uri) {
            super(httpClient, uri);
        }

        @Override
        protected HttpRequest buildRequest() {
            URI finalUri = buildFinalUri();
            requestBuilder.uri(finalUri).GET();
            return requestBuilder.build();
        }
    }

    public static class PostRequestBuilder extends AbstractRequestBuilder<PostRequestBuilder> {

        private HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.noBody();

        private PostRequestBuilder(HttpClient httpClient, URI uri) {
            super(httpClient, uri);
        }

        public PostRequestBuilder withJsonPayload(JsonObject json) {
            Objects.requireNonNull(json, "JSON payload cannot be null");
            StringWriter sw = new StringWriter(128);
            try (JsonWriter jw = jakarta.json.Json.createWriter(sw)) {
                jw.write(json);
            }
            String jsonString = sw.toString();
            this.bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(jsonString.getBytes(StandardCharsets.UTF_8));
            requestBuilder.header("Content-Type", "application/json");
            return self();
        }

        public PostRequestBuilder withByteArrayPayload(byte[] bytes) {
            Objects.requireNonNull(bytes, "Byte array payload cannot be null");
            this.bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(bytes);
            requestBuilder.header("Content-Type", "application/octet-stream");
            return self();
        }

        public PostRequestBuilder withNoPayload() {
            this.bodyPublisher = HttpRequest.BodyPublishers.noBody();
            requestBuilder.headers("Content-Type");
            return self();
        }

        @Override
        protected HttpRequest buildRequest() {
            URI finalUri = buildFinalUri();
            requestBuilder.uri(finalUri).POST(bodyPublisher);
            return requestBuilder.build();
        }
    }

    public static class MicroserviceCallException extends RuntimeException {

        private final int statusCode;
        private final String errorBody;
        private final String uri;

        public MicroserviceCallException(String message, int statusCode, String errorBody, String uri) {
            super(message);
            this.statusCode = statusCode;
            this.errorBody = errorBody;
            this.uri = uri;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getErrorBody() {
            return errorBody;
        }

        public String getUri() {
            return uri;
        }
    }
}
