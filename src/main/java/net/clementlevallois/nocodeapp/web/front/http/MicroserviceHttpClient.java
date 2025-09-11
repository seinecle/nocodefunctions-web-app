package net.clementlevallois.nocodeapp.web.front.http;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.json.JsonWriter;
import java.io.IOException;
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

/**
 * An injectable, application-scoped HTTP client for communicating with internal
 * microservices. It provides a fluent builder API for constructing and sending
 * requests with consistent configuration, error handling, and timeout settings.
 */
@ApplicationScoped
public class MicroserviceHttpClient {

    @Inject
    private ApplicationPropertiesBean applicationProperties;

    private HttpClient httpClient;
    private Properties privateProperties;
    private static final String API_ENDPOINT_PATH = Globals.API_ENDPOINT_ROOT;
    private static final String IMPORT_ENDPOINT_PATH = Globals.IMPORT_ENDPOINT_ROOT;
    private static final String EXPORT_ENDPOINT_PATH = Globals.EXPORT_ENDPOINT_ROOT;

    @PostConstruct
    public void init() {
        this.privateProperties = applicationProperties.getPrivateProperties();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Starts building a request to a specific target service.
     *
     * @param scheme The URI scheme (e.g., "http").
     * @param host The host (e.g., "localhost").
     * @param port The port number.
     * @return A ServiceClientBuilder to continue defining the request.
     */
    public ServiceClientBuilder target(String scheme, String host, int port) {
        try {
            URI baseUri = new URI(scheme, null, host, port, null, null, null);
            return new ServiceClientBuilder(httpClient, baseUri);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI components", e);
        }
    }

    /**
     * Creates a client builder for the pre-configured 'API' microservice.
     *
     * @return A ServiceClientBuilder configured for the API service.
     */
    public ServiceClientBuilder api() {
        String apiPort = privateProperties.getProperty("nocode_api_port");
        Objects.requireNonNull(apiPort, "nocode_api_port property is not set!");
        URI baseUri = buildBaseUriWithBasePath("localhost", apiPort, API_ENDPOINT_PATH);
        return new ServiceClientBuilder(httpClient, baseUri);
    }

    /**
     * Creates a client builder for the pre-configured 'import' microservice.
     *
     * @return A ServiceClientBuilder configured for the import service.
     */
    public ServiceClientBuilder importService() {
        String importPort = privateProperties.getProperty("nocode_import_port");
        Objects.requireNonNull(importPort, "nocode_import_port property is not set!");
        URI baseUri = buildBaseUriWithBasePath("localhost", importPort, IMPORT_ENDPOINT_PATH);
        return new ServiceClientBuilder(httpClient, baseUri);
    }

    public ServiceClientBuilder exportService() {
        String importPort = privateProperties.getProperty("nocode_import_port");
        Objects.requireNonNull(importPort, "nocode_import_port property is not set!");
        URI baseUri = buildBaseUriWithBasePath("localhost", importPort, EXPORT_ENDPOINT_PATH);
        return new ServiceClientBuilder(httpClient, baseUri);
    }

    private URI buildBaseUriWithBasePath(String host, String port, String endpointRootPath) {
        try {
            return new URI("http", null, host, Integer.parseInt(port), endpointRootPath, null, null);
        } catch (NumberFormatException | URISyntaxException e) {
            throw new IllegalStateException("Invalid port configuration", e);
        }
    }

    /**
     * Validates the HttpResponse, throwing a MicroserviceCallException for
     * non-2xx status codes.
     *
     * @param <T>
     * @param response
     */
    public static <T> void validateResponse(HttpResponse<T> response) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }
        String errorBody = "Could not read error response body.";
        switch (response.body()) {
            case String s ->
                errorBody = s;
            case byte[] bs ->
                errorBody = new String(bs, StandardCharsets.UTF_8);
            default -> {
            }
        }
        throw new MicroserviceCallException("Microservice call failed with status code " + response.statusCode(), response.statusCode(), errorBody, response.uri().toString());
    }

    /**
     * Builder for specifying the request path (e.g., GET, POST) after a target
     * is set.
     */
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

    /**
     * Abstract base for all request builders, providing common functionality
     * for adding headers, query parameters, and sending the request.
     */
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
            if (queryParams.isEmpty()) {
                return uri;
            }
            try {
                StringBuilder query = new StringBuilder();
                for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                    if (!query.isEmpty()) {
                        query.append("&");
                    }
                    query.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
                    query.append("=");
                    query.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
                }
                return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(), query.toString(), uri.getFragment());
            } catch (URISyntaxException ex) {
                throw new IllegalStateException("Failed to build final URI with query parameters", ex);
            }
        }

        protected abstract HttpRequest buildRequest();

        /**
         * Sends the request synchronously.
         */
        public <T> HttpResponse<T> send(HttpResponse.BodyHandler<T> bodyHandler) throws IOException, InterruptedException {
            HttpResponse<T> response = httpClient.send(buildRequest(), bodyHandler);
            MicroserviceHttpClient.validateResponse(response);
            return response;
        }

        /**
         * Sends the request asynchronously.
         */
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpResponse.BodyHandler<T> bodyHandler) {
            return httpClient.sendAsync(buildRequest(), bodyHandler)
                    .thenApply(response -> {
                        MicroserviceHttpClient.validateResponse(response);
                        return response;
                    });
        }

        /**
         * Sends the request asynchronously and returns the body upon successful
         * completion.
         */
        public <T> CompletableFuture<T> sendAsyncAndGetBody(HttpResponse.BodyHandler<T> bodyHandler) {
            return sendAsync(bodyHandler).thenApply(HttpResponse::body);
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
            requestBuilder.uri(buildFinalUri()).GET();
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
            this.bodyPublisher = HttpRequest.BodyPublishers.ofString(sw.toString());
            requestBuilder.header("Content-Type", "application/json");
            return self();
        }

        public PostRequestBuilder withByteArrayPayload(byte[] bytes) {
            Objects.requireNonNull(bytes, "Byte array payload cannot be null");
            this.bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(bytes);
            requestBuilder.header("Content-Type", "application/octet-stream");
            return self();
        }

        @Override
        protected HttpRequest buildRequest() {
            requestBuilder.uri(buildFinalUri()).POST(bodyPublisher);
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
