/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.importdata;

import com.twitter.clientlib.model.Tweet;
import com.twitter.clientlib.model.TweetSearchResponse;
import io.github.redouane59.twitter.TwitterClient;
import io.github.redouane59.twitter.signature.TwitterCredentials;
import io.mikael.urlbuilder.UrlBuilder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.enterprise.context.SessionScoped;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import net.clementlevallois.nocodeapp.web.front.backingbeans.SingletonBean;
import net.clementlevallois.nocodeapp.web.front.functions.UmigonBean;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.logview.NotificationService;
import org.openide.util.Exceptions;
import twitter4j.OEmbed;
import twitter4j.OEmbedRequest;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.auth.RequestToken;

/**
 *
 * @author LEVALLOIS
 */
@Named(value = "twitterImportBean")
@SessionScoped
public class TwitterImportBean implements Serializable {

    private String oauth_token;
    private String oauth_verifier;
    private static final String CREDENTIALS_FILE_PATH = "twitter.json";
    private RequestToken requestToken;
    io.github.redouane59.twitter.dto.others.RequestToken requestTokenRedouane;
    private String tokenUser;
    private String secretUser;
    private Twitter twitter;
    private TwitterClient twitterClient;
    private String query;
    private String languageString;
    private String sinceString;
    private String untilString;
    private List<SheetModel> sheets;
    private String searchType; // RECENT or FULL

    @Inject
    NotificationService notifService;

    public TwitterImportBean() {
    }

    public void onloadingRedirectPage() {
        try {
            //        try {

            //this is because on the server (*not locally*), the oauth_verifier query parameter GETS JOINED WITH THE NEXT ONE!!
//            oauth_verifier = oauth_verifier.split("\\?")[0];
//            twitter.getOAuthAccessToken(requestToken, oauth_verifier);
//            System.out.println("logged in user with : " + twitter.getOAuthAccessToken().getScreenName());
            twitterClient.getOAuth1AccessToken(requestTokenRedouane, oauth_verifier);
            twitterClient.getUserFromUserName("seinecle");
//            System.out.println(userFromUserName.getDescription());
            ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
            externalContext.redirect(RemoteLocal.getDomain() + "import_your_data_bulk_text.html");

//        } catch (IOException | TwitterException ex) {
//            Logger.getLogger(TwitterImportBean.class.getName()).log(Level.SEVERE, null, ex);
//        }
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }

    }

    public String goToConnectURL() throws IOException {
        try {
            twitter = SingletonBean.getTf().getInstance();
            twitter.setOAuthAccessToken(null);
            String callbackURL = RemoteLocal.getDomain() + "twitter_auth.html";
            requestToken = twitter.getOAuthRequestToken(callbackURL);

            InputStream in = TwitterImportBean.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
            TwitterCredentials tc = TwitterClient.OBJECT_MAPPER.readValue(in, TwitterCredentials.class);
            twitterClient = new TwitterClient(tc);
            String callBackURL = URLEncoder.encode(RemoteLocal.getDomain() + "twitter_auth.html", StandardCharsets.UTF_8);
            requestTokenRedouane = twitterClient.getOauth1Token(callBackURL);
            ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
            String authenticationURL = requestToken.getAuthorizationURL();
            System.out.println(authenticationURL);
            externalContext.redirect(authenticationURL);

        } catch (TwitterException ex) {
            Logger.getLogger(TwitterImportBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "error.xhtml";
    }

    public String getOauth_token() {
        return oauth_token;
    }

    public void setOauth_token(String oauth_token) {
        this.oauth_token = oauth_token;
    }

    public String getOauth_verifier() {
        return oauth_verifier;
    }

    public void setOauth_verifier(String oauth_verifier) {
        this.oauth_verifier = oauth_verifier;
    }

    public List<SheetModel> searchTweets() {
        try {
            URI uri = UrlBuilder.empty().withScheme("http").withPort(7002).withHost("localhost").withPath("api/tweets/bytes").addParameter("query", query).addParameter("days_start", "7").addParameter("days_end", "0").toUri();
            System.out.println("uri: " + uri);
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            TweetSearchResponse tweetSearchResponse = TweetSearchResponse.fromJson(body);

            sheets = new ArrayList();
            SheetModel sheetModel = new SheetModel();
            List<ColumnModel> headerNames = new ArrayList();
            ColumnModel cm;
            cm = new ColumnModel("0", "tweets");
            headerNames.add(cm);
            int i = 0;

            for (Tweet tweet : tweetSearchResponse.getData()) {
                String url = "";
                OEmbedRequest oEmbedRequest = new OEmbedRequest(Long.valueOf(tweet.getId()), url);
                oEmbedRequest.setMaxWidth(550);
                oEmbedRequest.setOmitScript(true);
                OEmbed embed = twitter.getOEmbed(oEmbedRequest);
                CellRecord cellRecord = new CellRecord(i++, 0, tweet.getText(), embed.getHtml());
                sheetModel.addCellRecord(cellRecord);
            }

            sheetModel.setName("tweets found");
            sheetModel.setTableHeaderNames(headerNames);
            sheets.add(sheetModel);

        } catch (IOException | InterruptedException ex) {
            Exceptions.printStackTrace(ex);
        } catch (TwitterException ex) {
            Exceptions.printStackTrace(ex);
        }
        return sheets;
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

}
