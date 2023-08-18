/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.http;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonBean;

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
    String url = "";
    String locale = "";
    boolean testLocalOnWindows = true;
    boolean middleWareRunningOnWindows = false;

    public SendReport() {
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
        this.url = url;
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
        String baseURL;
        String endPoint = null;
        Map<String, String> params = new HashMap();
        if (testLocalOnWindows && System.getProperty("os.name").toLowerCase().contains("wind")) {
            baseURL = SingletonBean.getPrivateProperties().getProperty("middleware_local_baseurl");
        } else {
            baseURL = SingletonBean.getPrivateProperties().getProperty("middleware_remote_baseurl");
        }
        if (TYPE.equals("error")) {
            endPoint = "sendUmigonReport";
            params.put("key", errorReport);
        }
        if (TYPE.equals("sendFeedback")) {
            endPoint = "sendUmigonReport";
            StringBuilder sb = new StringBuilder();
            sb.append("url: ").append(url);
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

        URL url;
        try {
            String paramsString = ParameterStringBuilder.getParamsString(params);
            url = new URL(baseURL + endPoint + "?" + paramsString);
            boolean hasInternetAccess = java.net.InetAddress.getByName("middleware.clementlevallois.net").isReachable(1000);
            if (!hasInternetAccess | (testLocalOnWindows & !middleWareRunningOnWindows)) {
                System.out.println("report not sent on function counter because we are local and middleware not deployed locally");
                return;
            }

            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("Accept-Charset", "UTF-8");
            con.getResponseCode();
        } catch (UnsupportedEncodingException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

}
