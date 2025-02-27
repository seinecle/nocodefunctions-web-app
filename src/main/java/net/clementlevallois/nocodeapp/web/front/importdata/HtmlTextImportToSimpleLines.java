package net.clementlevallois.nocodeapp.web.front.importdata;

import io.mikael.urlbuilder.UrlBuilder;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.clementlevallois.importers.model.UrlLink;
import net.clementlevallois.nocodeapp.web.front.backingbeans.ApplicationPropertiesBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;

/**
 *
 * @author LEVALLOIS
 */
@Named
@SessionScoped
public class HtmlTextImportToSimpleLines implements Serializable {

    @Inject
    BackToFrontMessengerBean logBean;

    @Inject
    SessionBean sessionBean;

    @Inject
    ApplicationPropertiesBean applicationProperties;

    @Inject
    ImportSimpleLinesBean simpleLineImportBean;

    private String dataPersistenceUniqueId;

    private String urlWebPage;
    private String urlWebSite;

    private List<UrlLink> linksToHarvest = new ArrayList();
    private List<UrlLink> selectedLinks = new ArrayList();

    private Integer urlsToCrawl = 10;
    private Integer maxUrlsToCrawl;
    private Integer MAX_URL_FREE = 10;
    private Integer MAX_URL_PRO = 100;
    private String commaSeparatedValuesExclusionTerms = "";

    private Boolean includeDepthOne = false;

    public void getRawTextFromUrls() {

        try {
            String currentFunction = sessionBean.getFunction();

            Properties privateProperties = applicationProperties.getPrivateProperties();

            if (currentFunction == null) {
                logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.error_function_not_set"));
                return;
            }

            UrlLink linkOriginal = new UrlLink();
            if (urlWebPage == null || urlWebPage.isBlank()) {
                linkOriginal.setLink(urlWebSite);
            } else {
                linkOriginal.setLink(urlWebPage);
            }
            linkOriginal.setLinkText(sessionBean.getLocaleBundle().getString("import_data.web_link_user_provided"));

            selectedLinks.add(0, linkOriginal);

            for (UrlLink link : selectedLinks) {
                JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder();
                JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
                jsonObjectBuilder.add(link.getLink(), link.getLinkText());
                jsonArrayBuilder.add(jsonObjectBuilder);

                HttpClient client = HttpClient.newHttpClient();

                URI uri = UrlBuilder
                        .empty()
                        .withScheme("http")
                        .withPort(Integer.valueOf(privateProperties.getProperty("nocode_import_port")))
                        .withHost("localhost")
                        .withPath("api/import/html/getRawTextFromLinks")
                        .addParameter("dataPersistenceId", dataPersistenceUniqueId)
                        .toUri();

                HttpRequest request = HttpRequest.newBuilder()
                        .POST(BodyPublishers.ofString(jsonArrayBuilder.build().toString()))
                        .timeout(Duration.ofSeconds(10))
                        .uri(uri)
                        .build();
                try {
                    HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
                    String body = resp.body();
                    if (resp.statusCode() != 200) {
                        System.out.println("return of html text reader by the API was not a 200 code");
                        String errorMessage = body;
                        System.out.println(errorMessage);
                        logBean.addOneNotificationFromString(errorMessage);
                        sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "💔", errorMessage);
                    } else {
                        logBean.addOneNotificationFromString("✅ " + sessionBean.getLocaleBundle().getString("general.message.content_successful_read") + ": " + link.getLink());
                    }

                } catch (HttpTimeoutException e) {
                    logBean.addOneNotificationFromString("💔 " + sessionBean.getLocaleBundle().getString("general.message.error_url_timed_out") + ": " + link.getLink());
                } catch (ConnectException e) {
                    logBean.addOneNotificationFromString("💔 " + sessionBean.getLocaleBundle().getString("general.message.error_no_connection") + ": " + link.getLink());
                }
            }
        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(HtmlTextImportToSimpleLines.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void retrieveUrlsContainedOnAPage() {
        dataPersistenceUniqueId = simpleLineImportBean.getDataPersistenceUniqueId();
        selectedLinks = new ArrayList();
        linksToHarvest = new ArrayList();

        try {
            String currentFunction = sessionBean.getFunction();

            Properties privateProperties = applicationProperties.getPrivateProperties();

            if (currentFunction == null) {
                logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.error_function_not_set"));
                return;
            }

            HttpClient client = HttpClient.newHttpClient();

            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(Integer.valueOf(privateProperties.getProperty("nocode_import_port")))
                    .withHost("localhost")
                    .withPath("api/import/html/getLinksContainedInPage")
                    .addParameter("dataPersistenceId", dataPersistenceUniqueId)
                    .addParameter("url", urlWebPage)
                    .toUri();

            HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .uri(uri)
                    .build();

            try {
                HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
                String body = resp.body();
                if (resp.statusCode() != 200) {
                    System.out.println("return of html text reader by the API was not a 200 code");
                    String errorMessage = body;
                    System.out.println(errorMessage);
                    logBean.addOneNotificationFromString(errorMessage);
                    sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "💔", errorMessage);
                } else {
                    JsonReader reader = Json.createReader(new StringReader(body));
                    JsonArray jsonArray = reader.readArray();
                    for (JsonValue jsonValue : jsonArray) {
                        JsonObject jo = jsonValue.asJsonObject();
                        String linkHref = jo.getString("linkHref");
                        String linkText = jo.getString("linkText");
                        UrlLink urlOnPage = new UrlLink();
                        urlOnPage.setLink(linkHref);
                        urlOnPage.setLinkText(linkText);
                        linksToHarvest.add(urlOnPage);
                    }
                }
            } catch (HttpTimeoutException e) {
                logBean.addOneNotificationFromString("💔 " + sessionBean.getLocaleBundle().getString("general.message.error_url_timed_out") + ": " + urlWebPage);
            } catch (ConnectException e) {
                logBean.addOneNotificationFromString("💔 " + sessionBean.getLocaleBundle().getString("general.message.error_no_connection") + ": " + urlWebPage);
            }

        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(HtmlTextImportToSimpleLines.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void crawlPagesOfAWebsite() {
        dataPersistenceUniqueId = simpleLineImportBean.getDataPersistenceUniqueId();
        selectedLinks = new ArrayList();
        linksToHarvest = new ArrayList();

        try {
            String currentFunction = sessionBean.getFunction();

            Properties privateProperties = applicationProperties.getPrivateProperties();

            if (currentFunction == null) {
                logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.error_function_not_set"));
                return;
            }

            HttpClient client = HttpClient.newHttpClient();

            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(Integer.valueOf(privateProperties.getProperty("nocode_import_port")))
                    .withHost("localhost")
                    .withPath("api/import/html/getPagesContainedInWebsite")
                    .addParameter("dataPersistenceId", dataPersistenceUniqueId)
                    .addParameter("url", urlWebSite)
                    .addParameter("maxUrls", String.valueOf(urlsToCrawl))
                    .addParameter("exclusionTerms", commaSeparatedValuesExclusionTerms)
                    .toUri();

            HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .uri(uri)
                    .build();

            try {
                HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
                String body = resp.body();
                if (resp.statusCode() != 200) {
                    System.out.println("return of html text reader by the API was not a 200 code");
                    String errorMessage = body;
                    System.out.println(errorMessage);
                    logBean.addOneNotificationFromString("💔 " + errorMessage);
                    sessionBean.addMessage(FacesMessage.SEVERITY_WARN, "💔", errorMessage);
                } else {
                    JsonReader reader = Json.createReader(new StringReader(body));
                    JsonArray jsonArray = reader.readArray();
                    for (JsonValue jsonValue : jsonArray) {
                        JsonObject jo = jsonValue.asJsonObject();
                        String linkHref = jo.getString("linkHref");
                        String linkText = jo.getString("linkText");
                        UrlLink urlOnPage = new UrlLink();
                        urlOnPage.setLink(linkHref);
                        urlOnPage.setLinkText(linkText);
                        selectedLinks.add(urlOnPage);
                    }
                }
            } catch (HttpTimeoutException e) {
                logBean.addOneNotificationFromString("💔 " + sessionBean.getLocaleBundle().getString("general.message.error_url_timed_out") + ": " + urlWebPage);
            } catch (ConnectException e) {
                logBean.addOneNotificationFromString("💔 " + sessionBean.getLocaleBundle().getString("general.message.error_no_connection") + ": " + urlWebPage);
            }

        } catch (IOException | InterruptedException ex) {
            Logger.getLogger(HtmlTextImportToSimpleLines.class.getName()).log(Level.SEVERE, null, ex);
        }

        String nbOfPagesToCrawl = "📚 " + sessionBean.getLocaleBundle().getString("general.message.nb_pages_to_crawl") + " " + selectedLinks.size();
        logBean.addOneNotificationFromString(nbOfPagesToCrawl);

    }

    public List<UrlLink> getLinksToHarvest() {
        return linksToHarvest;
    }

    public void setLinksToHarvest(List<UrlLink> urls) {
        this.linksToHarvest = urls;
    }

    public List<UrlLink> getSelectedLinks() {
        return selectedLinks;
    }

    public void setSelectedLinks(List<UrlLink> selectedLinks) {
        this.selectedLinks = selectedLinks;
    }

    public String gotToFunctionWithDataInBulk() {
        getRawTextFromUrls();
        urlWebPage = null;
        urlWebSite = null;
        return "/" + sessionBean.getFunction() + "/" + sessionBean.getFunction() + ".xhtml?faces-redirect=true";
    }

    public String getUrlWebPage() {
        return urlWebPage;
    }

    public void setUrlWebPage(String urlWebPage) {
        this.urlWebPage = urlWebPage;
    }

    public String getUrlWebSite() {
        return urlWebSite;
    }

    public void setUrlWebSite(String urlWebSite) {
        this.urlWebSite = urlWebSite;
    }

    public Boolean getIncludeDepthOne() {
        return includeDepthOne;
    }

    public void setIncludeDepthOne(Boolean includeDepthOne) {
        this.includeDepthOne = includeDepthOne;
    }

    public Integer getUrlsToCrawl() {
        return this.urlsToCrawl;
    }

    public void setUrlsToCrawl(Integer urlsToCrawl) {
        this.urlsToCrawl = urlsToCrawl;
    }

    public Integer getMaxUrlsToCrawl() {
        if (sessionBean.getHash()!= null && !sessionBean.getHash().isBlank()){
            return MAX_URL_PRO;
        }else{
            return MAX_URL_FREE;            
        }
    }

    public void setMaxUrlsToCrawl(Integer maxUrlsToCrawl) {
        this.maxUrlsToCrawl = maxUrlsToCrawl;
    }
    
    

    public String getCommaSeparatedValuesExclusionTerms() {
        return commaSeparatedValuesExclusionTerms;
    }

    public void setCommaSeparatedValuesExclusionTerms(String commaSeparatedValuesExclusionTerms) {
        this.commaSeparatedValuesExclusionTerms = commaSeparatedValuesExclusionTerms;
    }
    
    

}
