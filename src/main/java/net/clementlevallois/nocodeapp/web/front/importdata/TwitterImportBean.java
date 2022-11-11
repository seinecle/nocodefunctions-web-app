/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.importdata;

import com.github.scribejava.core.model.OAuth2AccessToken;
import com.twitter.clientlib.model.Tweet;
import com.twitter.clientlib.model.TweetSearchResponse;
import io.mikael.urlbuilder.UrlBuilder;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.stream.JsonParser;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SessionBean;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonBean;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.importdata.DataImportBean.Source;
import net.clementlevallois.nocodeapp.web.front.logview.NotificationService;

/**
 *
 * @author LEVALLOIS
 */
@Named(value = "twitterImportBean")
@SessionScoped
public class TwitterImportBean implements Serializable {

    private String code = "";
    private String query;
    private String languageString;
    private String sinceString;
    private String untilString;
    private List<SheetModel> sheets;
    private String searchType; // RECENT or FULL
    private Boolean searchButtonDisabled = false;
    private String dummyToResetSearch;

    private Integer progress;

    @Inject
    NotificationService service;

    @Inject
    SingletonBean singletonBean;

    @Inject
    DataImportBean dataInputBean;

    @Inject
    SessionBean sessionBean;

    public TwitterImportBean() {

    }

    public void onloadingRedirectPage() {
        try {

            //this is because on the server (*not locally*), the code url param GETS JOINED WITH THE NEXT ONE!!
            code = code.split("\\?")[0];
            OAuth2AccessToken accessToken = singletonBean.getOAuth2AccessToken(code);
            sessionBean.setTwitterOAuth2AccessToken(accessToken);
            searchButtonDisabled = false;
            ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
            externalContext.redirect(RemoteLocal.getDomain() + "import_your_data_twitter.html");
        } catch (IOException ex) {
                                System.out.println("ex:"+ ex.getMessage());
        }
    }

    public String navigateBackToSearch() {
        sheets = new ArrayList();
        dataInputBean.setDataInSheets(new ArrayList());

        return "/import_your_data_twitter.html?faces-redirect=true";
    }

    public String goToConnectURL() throws IOException {
        ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
        externalContext.redirect(singletonBean.getTwitterAuthorizationUrl());
        return "error.xhtml";
    }

    public String searchTweets() {
        dataInputBean.setSource(Source.TWITTER);
        sheets = new ArrayList();
        dataInputBean.setDataInSheets(new ArrayList());

        if (sessionBean.getTwitterOAuth2AccessToken() == null) {
            service.create(sessionBean.getLocaleBundle().getString("back.import.twitter_credentials_not found"));
            return "";
        }
        try {
            this.progress = 3;
            service.create(sessionBean.getLocaleBundle().getString("back.import.searching_tweets"));
            service.create(sessionBean.getLocaleBundle().getString("general.message.wait_long_operation"));

            Runnable incrementProgress = () -> {
                progress = progress + 1;
            };
            ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
            executor.scheduleAtFixedRate(incrementProgress, 0, 250, TimeUnit.MILLISECONDS);

            URI uri = UrlBuilder
                    .empty()
                    .withScheme("http")
                    .withPort(7003)
                    .withHost("localhost")
                    .withPath("api/tweets/json")
                    .addParameter("accessToken", sessionBean.getTwitterOAuth2AccessToken().getAccessToken())
                    .addParameter("refreshToken", sessionBean.getTwitterOAuth2AccessToken().getRefreshToken())
                    .addParameter("query", query)
                    .addParameter("days_start", "7")
                    .addParameter("days_end", "0")
                    .toUri();
//            System.out.println("uri: " + uri);
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.out.println("error! " + response.body());
                if (response.statusCode() == 429) {
                    service.create(sessionBean.getLocaleBundle().getString("back.import.twitter.too_many_requests"));
                    JsonReader jr = Json.createReader(new StringReader(response.body()));
                    JsonObject read = jr.readObject();
                    service.create("wait " + read.getString("time to wait") + " seconds.");
                    service.create(sessionBean.getLocaleBundle().getString("general.message.see_more_details") + " https://t.co/uWmGyb9rf0");
                } else {
                    service.create(sessionBean.getLocaleBundle().getString("back.import.error_fetching_tweets"));
                    service.create("error fetching tweets: " + response.body());
                }
                return "";
            }
            String body = response.body();
            Jsonb jsonb = JsonbBuilder.create();
            TweetSearchResponse tweetSearchResponse = jsonb.fromJson(body, TweetSearchResponse.class);

            sheets = new ArrayList();
            SheetModel sheetModel = new SheetModel();
            List<ColumnModel> headerNames = new ArrayList();
            ColumnModel cm;
            cm = new ColumnModel("0", "tweets");
            headerNames.add(cm);
            int i = 0;

            for (Tweet tweet : tweetSearchResponse.getData()) {
                String url = "https://publish.twitter.com/oembed?url=https://twitter.com/" + tweet.getAuthorId() + "/status/" + tweet.getId();
                String html = "";
                try ( JsonParser jsonParser = Json.createParser(new URL(url).openStream())) {
                    String keyName = "";
                    boolean found = false;
                    while (jsonParser.hasNext() & !found) {
                        JsonParser.Event event = jsonParser.next();
                        switch (event) {
                            case KEY_NAME:
                                keyName = jsonParser.getString();
                                break;
                            case VALUE_STRING:
                                if (keyName.equals("html")) {
                                    html = jsonParser.getString();
                                    found = true;
                                }
                                break;
                            default:
                            // No need..
                        }
                    }
                }
                CellRecord cellRecord = new CellRecord(i++, 0, tweet.getText(), html);
                sheetModel.addCellRecord(cellRecord);
            }

            sheetModel.setName("tweets found");
            sheetModel.setTableHeaderNames(headerNames);
            sheets.add(sheetModel);
            executor.shutdown();

        } catch (IOException | InterruptedException ex) {
            service.create(sessionBean.getLocaleBundle().getString("back.import.error_fetching_tweets"));
                                System.out.println("ex:"+ ex.getMessage());
            return "";

        }
        progress = 100;
        if (!sheets.isEmpty()) {
            service.create(sessionBean.getLocaleBundle().getString("back.import.finished_searching_tweets") + sheets.get(0).getCellRecords().size() + sessionBean.getLocaleBundle().getString("back.import.number_tweets_found"));
            dataInputBean.setDataInSheets(sheets);
        }

        return "";
    }

    public String gotToFunctionWithDataInBulk() {
        System.out.println("function is: " + sessionBean.getFunction());
        return "/" + sessionBean.getFunction() + "/" + sessionBean.getFunction() + ".xhtml?faces-redirect=true";
    }

    public String getLanguageString() {
        return languageString;
    }

    public void setLanguageString(String languageString) {
        this.languageString = languageString;
    }

    public String getSinceString() {
        return sinceString;
    }

    public void setSinceString(String sinceString) {
        this.sinceString = sinceString;
    }

    public String getUntilString() {
        return untilString;
    }

    public void setUntilString(String untilString) {
        this.untilString = untilString;
    }

    public List<SheetModel> getSheets() {
        return sheets;
    }

    public void setSheets(List<SheetModel> sheets) {
        this.sheets = sheets;
    }

    public String getSearchType() {
        return searchType;
    }

    public void setSearchType(String searchType) {
        this.searchType = searchType;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Boolean getSearchButtonDisabled() {
        return searchButtonDisabled;
    }

    public void setSearchButtonDisabled(Boolean searchButtonDisabled) {
        this.searchButtonDisabled = searchButtonDisabled;
    }

    public String getDummyToResetSearch() {
        return dummyToResetSearch;
    }

    public void setDummyToResetSearch(String dummyToResetSearch) {
        if (dummyToResetSearch.equals("true")) {
            sheets = new ArrayList();
            dataInputBean.setDataInSheets(new ArrayList());
        }
    }

}
