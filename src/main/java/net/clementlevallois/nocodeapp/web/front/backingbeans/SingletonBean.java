/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.backingbeans;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.scribejava.core.model.OAuth2AccessToken;
import com.github.scribejava.core.pkce.PKCE;
import com.github.scribejava.core.pkce.PKCECodeChallengeMethod;
import com.twitter.clientlib.TwitterCredentialsOAuth2;
import com.twitter.clientlib.auth.TwitterOAuth20Service;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import jakarta.enterprise.context.ApplicationScoped;
import net.clementlevallois.nocodeapp.web.front.http.RemoteLocal;
import org.omnifaces.cdi.Startup;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 *
 * @author LEVALLOIS
 */
@Startup
@ApplicationScoped
public class SingletonBean {

    static ObjectMapper mapper;
    static JedisPool jedisPool;
    private final static String PATHLOCALE = "net.clementlevallois.nocodeapp.web.front.resources.i18n.text";
    private static Properties privateProperties;
    private final static String PATHLOCALDEV = "C:\\Users\\levallois\\open\\no code app\\webapp\\jsf-app\\";
    private final static String PATHREMOTEDEV = "/home/waouh/nocodeapp-web/";
    private static String rootProps;
    private TwitterOAuth20Service twitterOAuthService;
    private String twitterAuthorizationUrl;

    public SingletonBean() {
        try {
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                rootProps = PATHLOCALDEV;
            } else {
                rootProps = PATHREMOTEDEV;
            }
            InputStream is = new FileInputStream(rootProps + "private/private.properties");
            privateProperties = new Properties();
            privateProperties.load(is);

            // This is the new Twitter client for java by Twitter.
            String twitterClientId = privateProperties.getProperty("twitter_client_id");
            String twitterClientSecret = privateProperties.getProperty("twitter_client_secret");

            TwitterCredentialsOAuth2 credentials = new TwitterCredentialsOAuth2(twitterClientId,
                    twitterClientSecret,
                    "",
                    "");

            twitterOAuthService = new TwitterOAuth20Service(
                    credentials.getTwitterOauth2ClientId(),
                    credentials.getTwitterOAuth2ClientSecret(),
                    RemoteLocal.getDomain() + "twitter_auth.html",
                    "offline.access tweet.read users.read");

            final String secretState = "state";
            PKCE pkce = new PKCE();
            pkce.setCodeChallenge("challenge");
            pkce.setCodeChallengeMethod(PKCECodeChallengeMethod.PLAIN);
            pkce.setCodeVerifier("challenge");
            twitterAuthorizationUrl = twitterOAuthService.getAuthorizationUrl(pkce, secretState);

            String redisPort;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                redisPort = privateProperties.getProperty("redis_port_local");
            } else {
                redisPort = System.getProperty("redis.port");
            }
//            System.out.println("redis port is: " + redisPort);

            initRedis(redisPort);
        } catch (UnknownHostException ex) {
            System.out.println("ex:" + ex.getMessage());
        } catch (FileNotFoundException ex) {
            System.out.println("ex:" + ex.getMessage());
        } catch (IOException ex) {
            System.out.println("ex:" + ex.getMessage());
        }

    }

    private static void initRedis(String redisPort) throws UnknownHostException {

        // instance a json mapper
        mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); // create once, reuse

        //jedis
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(128);
        jedisPool = new JedisPool(poolConfig, "localhost", Integer.parseInt(redisPort), 2000);
    }

    public static JedisPool getJedisPool() {
        return jedisPool;
    }

    public static Properties getPrivateProperties() {
        return privateProperties;
    }

    public static String getPATHLOCALE() {
        return PATHLOCALE;
    }
//
//    public static String getPATHREMOTEDEV() {
//        return PATHREMOTEDEV;
//    }
//
//    public static String getRootProps() {
//        return rootProps;
//    }

    public TwitterOAuth20Service getTwitterOAuthService() {
        return twitterOAuthService;
    }

    public String getTwitterAuthorizationUrl() {
        return twitterAuthorizationUrl;
    }

    public OAuth2AccessToken getOAuth2AccessToken(String code) {
        try {
            PKCE pkce = new PKCE();
            pkce.setCodeChallenge("challenge");
            pkce.setCodeChallengeMethod(PKCECodeChallengeMethod.PLAIN);
            pkce.setCodeVerifier("challenge");
            return twitterOAuthService.getAccessToken(pkce, code);
        } catch (IOException | ExecutionException | InterruptedException ex) {
            System.out.println("ex:" + ex.getMessage());
            return null;
        }

    }

}
