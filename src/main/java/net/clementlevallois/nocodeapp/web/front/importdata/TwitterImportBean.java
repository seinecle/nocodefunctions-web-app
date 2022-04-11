/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.importdata;

import io.github.redouane59.twitter.TwitterClient;
import io.github.redouane59.twitter.signature.TwitterCredentials;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URLEncoder;
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
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import net.clementlevallois.nocodeapp.web.front.logview.NotificationService;
import org.openide.util.Exceptions;
import twitter4j.OEmbed;
import twitter4j.OEmbedRequest;
import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.RateLimitStatusEvent;
import twitter4j.RateLimitStatusListener;
import twitter4j.Status;
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
    private String queryString;
    private String languageString;
    private String sinceString;
    private String untilString;

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
        List<SheetModel> sheets = new ArrayList();
        SheetModel sheetModel = new SheetModel();
        List<ColumnModel> headerNames = new ArrayList();
        ColumnModel cm;
        cm = new ColumnModel("0", "tweets");
        headerNames.add(cm);
        int[] limitReached = {0};

        try {
            twitter.addRateLimitStatusListener(new RateLimitStatusListener() {
                @Override
                public void onRateLimitStatus(RateLimitStatusEvent event) {
//                    System.out.println("Limit[" + event.getRateLimitStatus().getLimit() + "], Remaining[" + event.getRateLimitStatus().getRemaining() + "]");
                }

                @Override
                public void onRateLimitReached(RateLimitStatusEvent event) {
//                    System.out.println("Limit[" + event.getRateLimitStatus().getLimit() + "], Remaining[" + event.getRateLimitStatus().getRemaining() + "]");
                    sheetModel.setName("Limit reached");
                    sheetModel.setTableHeaderNames(headerNames);
                    CellRecord cellRecord = new CellRecord(0, 0, "Limit[" + event.getRateLimitStatus().getLimit() + "], Remaining[" + event.getRateLimitStatus().getRemaining() + "]");
                    sheetModel.addCellRecord(cellRecord);

                    sheets.add(sheetModel);
                    limitReached[0]++;
                }
            });
            if (limitReached[0] > 0) {
                return sheets;
            }
            if (queryString.isBlank()) {
                queryString = "data science";
            }
            String encoded = URLEncoder.encode(queryString, StandardCharsets.UTF_8);
            Query query = new Query(encoded);
            query.setCount(100);
            if (sinceString != null && sinceString.matches("\\d{4}-\\d{2}-\\d{2}")) {
                query.setSince(sinceString);
            }
            if (untilString != null && untilString.matches("\\d{4}-\\d{2}-\\d{2}")) {
                query.setUntil(untilString);
            }
            if (languageString != null && languageString.toLowerCase().equals("en") || languageString.toLowerCase().equals("fr")) {
                query.setLang(languageString);
            }
            QueryResult result;
            result = twitter.search(query);
            List<Status> tweets = result.getTweets();
            int i = 0;
            for (Status tweet : tweets) {
                String url = "";
                OEmbedRequest oEmbedRequest = new OEmbedRequest(tweet.getId(), url);
                oEmbedRequest.setMaxWidth(550);
                oEmbedRequest.setOmitScript(true);
                if (limitReached[0] > 0) {
                    return sheets;
                }
                OEmbed embed = twitter.getOEmbed(oEmbedRequest);
//                System.out.println("url: " + embed.getURL());
                CellRecord cellRecord = new CellRecord(i++, 0, tweet.getText(), embed.getHtml());
                sheetModel.addCellRecord(cellRecord);
            }

        } catch (TwitterException ex) {
            Logger.getLogger(TwitterImportBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        sheetModel.setName("tweets found");
        sheetModel.setTableHeaderNames(headerNames);
        sheets.add(sheetModel);

        return sheets;
    }

    public String getQueryString() {
        return queryString;
    }

    public void setQueryString(String queryString) {
        this.queryString = queryString;
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

}
