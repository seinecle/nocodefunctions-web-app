package net.clementlevallois.nocodeapp.web.front.http;

import io.mikael.urlbuilder.UrlBuilder;
import io.mikael.urlbuilder.util.UrlParameterMultimap;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author LEVALLOIS
 */
public class SendReport extends Thread {

    String event = "";
    String errorReport = "";
    String userAgent = "";
    String stopwords = "";
    String TYPE;
    String email = "";
    String emailDesigner = "";
    String pass = "";
    String keycode = "";
    String description = "";
    String annotationTypeOfTask;
    String sourceLang = "";
    String suggestion = "";
    String freeCommentError = "";
    String freeCommentSuggestion = "";
    String locale = "";
    String feedbackURL = "";
    boolean testLocalOnWindows = false;
    boolean middleWareRunningOnWindows = false;
    String middlewarePort;
    String middlewareHost;

    public SendReport(String middleWareHost, String middlewarePort) {
        this.middlewareHost = middleWareHost;
        this.middlewarePort = middlewarePort;
    }

    public void initAnalytics(String event, String userAgent) {
        this.event = event;
        this.TYPE = "analytics";
        this.userAgent = userAgent;
    }

    public void initSendDataKeycode(String email, String keycode, String description) {
        this.email = email;
        this.keycode = keycode;
        this.description = description;
        this.TYPE = "sendDataKeycode";
    }

    public void initSendAnnotatorCredentials(String email, String pass, String keycode, String description, String emailDesigner, String annotationTask) {
        this.email = email;
        this.emailDesigner = emailDesigner;
        this.pass = pass;
        this.keycode = keycode;
        this.description = description;
        this.TYPE = "sendAnnotatorCredentials";
        this.annotationTypeOfTask = annotationTask;
    }

    public void initSendTaskDesignerCredentials(String email, String pass) {
        this.email = email;
        this.pass = pass;
        this.TYPE = "sendTaskDesignerCredentials";
    }

    public void initFeedback(String url, String locale, String sourceLang, String suggestion, String freeCommentError, String freeCommentSuggestion, String email) {
        this.sourceLang = sourceLang;
        this.suggestion = suggestion;
        this.freeCommentError = freeCommentError;
        this.freeCommentSuggestion = freeCommentSuggestion;
        this.feedbackURL = url;
        this.email = email;
        this.locale = locale;
        this.TYPE = "sendFeedback";
    }

    public void initShareStopwords(String event, String stopwords) {
        this.event = event;
        this.stopwords = stopwords;
        this.TYPE = "stopwords";
    }

    public void initErrorReport(String errorReport) {
        this.errorReport = errorReport;
        this.TYPE = "error";
    }

    @Override
    public void run() {
        URI uri = null;
        try {
            System.out.println("function launched: " + event);
            String scheme;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                scheme = "http";
                testLocalOnWindows = true;
            } else {
                scheme = "https";
                testLocalOnWindows = false;
            }
            if (testLocalOnWindows && !middleWareRunningOnWindows) {
                System.out.println("report not sent on function counter because we are local on Windows but middleware not deployed locally");
                return;
            }
            String endPoint = null;

            Map<String, String> params = new HashMap();
            if (TYPE.equals("error")) {
                endPoint = "sendUmigonReport";
                params.put("key", errorReport);
            }
            if (TYPE.equals("sendFeedback")) {
                endPoint = "sendUmigonReport";
                StringBuilder sb = new StringBuilder();
                sb.append(" | ").append("url: ").append(feedbackURL);
                sb.append(" | ").append("locale: ").append(locale);
                if (!sourceLang.isBlank()) {
                    sb.append(" | ").append("faulty phrase: ").append(sourceLang);
                }
                if (!suggestion.isBlank()) {
                    sb.append(" | ").append("suggestion for better translation: ").append(suggestion);
                }
                if (!freeCommentError.isBlank()) {
                    sb.append(" | ").append("free comment on error detected: ").append(freeCommentError);
                }
                if (!email.isBlank()) {
                    sb.append(" | ").append("email: ").append(email);
                }
                if (!freeCommentSuggestion.isBlank()) {
                    sb.append(" | ").append("free comment on new feature suggestion: ").append(freeCommentSuggestion);
                }
                params.put("key", sb.toString());
            }
            if (TYPE.equals("analytics")) {
                endPoint = "sendEvent";
                params.put("event", event);
                params.put("useragent", userAgent);
            }

            if (TYPE.equals("stopwords")) {
                endPoint = "sendUmigonReport";
                params.put("key", stopwords);
            }

            if (TYPE.equals("sendDataKeycode")) {
                endPoint = "sendDataKeycode";
                params.put("email", email);
                params.put("keycode", keycode);
                params.put("description", description);
            }

            if (TYPE.equals("sendAnnotatorCredentials")) {
                endPoint = "sendAnnotatorCredentials";
                params.put("emailAnnotator", email);
                params.put("pass", pass);

                // this is to obfuscate the correct keycode.
                // When the keycode is parsed back in the app, the 3 last characters are removed
                params.put("keycode", keycode + "2ie");
                params.put("description", description);
                params.put("emailDesigner", emailDesigner);
                params.put("annotationTypeOfTask", annotationTypeOfTask);
            }

            if (TYPE.equals("sendTaskDesignerCredentials")) {
                endPoint = "sendTaskDesignerCredentials";
                params.put("pass", pass);
                params.put("email", email);
            }

            UrlBuilder urlBuilder = UrlBuilder.empty()
                    .withScheme(scheme)
                    .withHost(middlewareHost)
                    .withPort(Integer.valueOf(middlewarePort))
                    .withPath(endPoint);
            UrlParameterMultimap paramsList = UrlParameterMultimap.newMultimap();

            for (Map.Entry<String, String> entry : params.entrySet()) {
                paramsList.add(entry.getKey(), entry.getValue());
            }

            uri = urlBuilder.withParameters(paramsList).toUri();
            HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException ex) {
            System.out.println("error sending report to this url:");
            if (uri != null) {
                System.out.println(uri.toString());
            }
        }
    }
}
