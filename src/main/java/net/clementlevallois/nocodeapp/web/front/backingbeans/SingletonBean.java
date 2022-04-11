/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.backingbeans;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.util.Properties;
import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import org.omnifaces.cdi.Startup;
import org.openide.util.Exceptions;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import twitter4j.TwitterFactory;
import twitter4j.conf.ConfigurationBuilder;

/**
 *
 * @author LEVALLOIS
 */
@Startup
@ApplicationScoped
public class SingletonBean {

    static TwitterFactory tf;
    static ObjectMapper mapper;
    static JedisPool jedisPool;
    public static String PATHLOCALE = "net.clementlevallois.nocodeapp.web.front.resources.i18n.text";
    public static Properties privateProperties;

    @PostConstruct
    public void config() {
        try {
            InputStream is = new FileInputStream("private/private.properties");
            privateProperties = new Properties();
            privateProperties.load(is);
            ConfigurationBuilder cb = new ConfigurationBuilder();
            // app is by @seinecle_fr: https://developer.twitter.com/en/portal/apps/21043553/settings
            cb.setDebugEnabled(true)
                    .setOAuthConsumerKey(privateProperties.getProperty("twitter_consumer_key"))
                    .setOAuthConsumerSecret(privateProperties.getProperty("twitter_consumer_secret"));
//                .setOAuthAccessToken(privateProperties.getProperty("twitter_access_token"))
//                .setOAuthAccessTokenSecret(privateProperties.getProperty("twitter_access_token_secret"));
            tf = new TwitterFactory(cb.build());

            String redisPort;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                redisPort = privateProperties.getProperty("redis_port_local");
            } else {
                redisPort = System.getProperty("redis.port");
            }
            System.out.println("redis port is: " + redisPort);

            initRedis(redisPort);
        } catch (UnknownHostException ex) {
            Exceptions.printStackTrace(ex);
        } catch (FileNotFoundException ex) {
            Exceptions.printStackTrace(ex);
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
        }

    }

    private static void initRedis(String redisPort) throws UnknownHostException {

        // instance a json mapper
        mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); // create once, reuse

        //jedis
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(128);
        jedisPool = new JedisPool(poolConfig, "localhost", Integer.valueOf(redisPort), 2000);
    }

    public static TwitterFactory getTf() {
        return tf;
    }

    public static JedisPool getJedisPool() {
        return jedisPool;
    }

    

}
