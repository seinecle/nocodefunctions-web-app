package net.clementlevallois.nocodeapp.web.front.importdata;

import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.*;
import net.clementlevallois.importers.model.UrlLink;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.http.MicroserviceHttpClient;
import net.clementlevallois.nocodeapp.web.front.logview.BackToFrontMessengerBean;
import net.clementlevallois.nocodeapp.web.front.stripe.StripeBean;

import java.io.Serializable;
import java.io.StringReader;
import java.net.http.HttpResponse;
import java.util.*;

@Named
@SessionScoped
public class HtmlTextImportToSimpleLines implements Serializable {

    @Inject
    BackToFrontMessengerBean logBean;

    @Inject
    SessionBean sessionBean;

    @Inject
    StripeBean stripeBean;

    @Inject
    ImportSimpleLinesBean simpleLineImportBean;

    @Inject
    MicroserviceHttpClient microserviceHttpClient;

    private String jobId;

    private String urlWebPage;
    private String urlWebSite;

    private List<UrlLink> linksToHarvest = new ArrayList<>();
    private List<UrlLink> selectedLinks = new ArrayList<>();

    private Integer urlsToCrawl = 10;
    private final Integer MAX_URL_FREE = 10;
    private final Integer MAX_URL_PRO = 100;

    private Integer maxUrlsToCrawl;

    private String commaSeparatedValuesExclusionTerms = "";

    private Boolean includeDepthOne = false;

    public void getRawTextFromUrls() {
        try {
            String currentFunction = null;
            if (currentFunction == null) {
                logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.error_function_not_set"));
                return;
            }

            UrlLink linkOriginal = new UrlLink();
            linkOriginal.setLink((urlWebPage == null || urlWebPage.isBlank()) ? urlWebSite : urlWebPage);
            linkOriginal.setLinkText(sessionBean.getLocaleBundle().getString("import_data.web_link_user_provided"));
            selectedLinks.add(0, linkOriginal);

            for (UrlLink link : selectedLinks) {
                JsonObject jsonPayload = Json.createObjectBuilder()
                        .add(link.getLink(), link.getLinkText())
                        .build();
                JsonArray jsonArray = Json.createArrayBuilder().add(jsonPayload).build();

                microserviceHttpClient.importService()
                        .post("/import/html/getRawTextFromLinks")
                        .addQueryParameter("jobId", jobId)
                        .withJsonPayload(Json.createObjectBuilder().add("links", jsonArray).build())
                        .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofString())
                        .join();

                logBean.addOneNotificationFromString("✅ " + sessionBean.getLocaleBundle().getString("general.message.content_successful_read") + ": " + link.getLink());
            }
        } catch (Exception e) {
            logBean.addOneNotificationFromString("💔 " + e.getMessage());
        }
    }

    public void retrieveUrlsContainedOnAPage() {
        jobId = simpleLineImportBean.getJobId();
        selectedLinks = new ArrayList<>();
        linksToHarvest = new ArrayList<>();

        try {
            String currentFunction = null;
            if (currentFunction == null) {
                logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.error_function_not_set"));
                return;
            }

            var response = microserviceHttpClient.importService()
                    .get("/import/html/getLinksContainedInPage")
                    .addQueryParameter("jobId", jobId)
                    .addQueryParameter("url", urlWebPage)
                    .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofString())
                    .join();

            try (JsonReader reader = Json.createReader(new StringReader(response))) {
                reader.readArray().forEach(jsonValue -> {
                    JsonObject jo = jsonValue.asJsonObject();
                    UrlLink urlOnPage = new UrlLink();
                    urlOnPage.setLink(jo.getString("linkHref"));
                    urlOnPage.setLinkText(jo.getString("linkText"));
                    linksToHarvest.add(urlOnPage);
                });
            }

        } catch (Exception e) {
            logBean.addOneNotificationFromString("💔 " + e.getMessage());
        }
    }

    public void crawlPagesOfAWebsite() {
        jobId = simpleLineImportBean.getJobId();
        selectedLinks = new ArrayList<>();
        linksToHarvest = new ArrayList<>();

        try {
            String currentFunction = null;
            if (currentFunction == null) {
                logBean.addOneNotificationFromString(sessionBean.getLocaleBundle().getString("general.message.error_function_not_set"));
                return;
            }

            var response = microserviceHttpClient.importService()
                    .get("/import/html/getPagesContainedInWebsite")
                    .addQueryParameter("jobId", jobId)
                    .addQueryParameter("url", urlWebSite)
                    .addQueryParameter("maxUrls", String.valueOf(urlsToCrawl))
                    .addQueryParameter("exclusionTerms", commaSeparatedValuesExclusionTerms)
                    .sendAsyncAndGetBody(HttpResponse.BodyHandlers.ofString())
                    .join();

            try (JsonReader reader = Json.createReader(new StringReader(response))) {
                reader.readArray().forEach(jsonValue -> {
                    JsonObject jo = jsonValue.asJsonObject();
                    UrlLink urlOnPage = new UrlLink();
                    urlOnPage.setLink(jo.getString("linkHref"));
                    urlOnPage.setLinkText(jo.getString("linkText"));
                    selectedLinks.add(urlOnPage);
                });
            }

            if (urlsToCrawl > MAX_URL_FREE) {
                stripeBean.manageCredits();
            }

            logBean.addOneNotificationFromString("📚 " + sessionBean.getLocaleBundle().getString("general.message.nb_pages_to_crawl") + " " + selectedLinks.size());

        } catch (Exception e) {
            logBean.addOneNotificationFromString("💔 " + e.getMessage());
        }
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
        return ".xhtml?faces-redirect=true";
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
        return urlsToCrawl;
    }

    public void setUrlsToCrawl(Integer urlsToCrawl) {
        this.urlsToCrawl = urlsToCrawl;
    }

    public String getCommaSeparatedValuesExclusionTerms() {
        return commaSeparatedValuesExclusionTerms;
    }

    public void setCommaSeparatedValuesExclusionTerms(String commaSeparatedValuesExclusionTerms) {
        this.commaSeparatedValuesExclusionTerms = commaSeparatedValuesExclusionTerms;
    }

    public Integer getMaxUrlsToCrawl() {
        if (sessionBean.isHashPresent() && stripeBean.getRemainingCredits() > 0) {
            return MAX_URL_PRO;
        } else {
            return MAX_URL_FREE;
        }
    }

    public void setMaxUrlsToCrawl(Integer maxUrlsToCrawl) {
        this.maxUrlsToCrawl = maxUrlsToCrawl;
    }

}
