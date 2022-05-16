/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.http;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.faces.context.FacesContext;
import javax.servlet.http.HttpServletRequest;
import net.clementlevallois.nocodeapp.web.front.importdata.GoogleSheetsImportBean;
import org.openide.util.Exceptions;

/**
 *
 * @author LEVALLOIS
 */
public class RemoteLocal {

    public static String getDomain() {
        String post;
        String protocol;
        String domain;
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            post = "jsf-app/";
            protocol = "http://";
            domain = "localhost:8080";
        } else {
            post = "";
            protocol = "https://";
            if (System.getProperty("test") != null && System.getProperty("test").equals("yes")) {
                domain = "test.nocodefunctions.com";
            } else {
                domain = "nocodefunctions.com";
            }
        }
        String result = protocol + domain + "/" + post;
        return result;
    }

    public static boolean isLocal() {
        return System.getProperty("test") == null || System.getProperty("test").isBlank();
    }

    public static boolean isTest() {
        return System.getProperty("test") != null && System.getProperty("test").equals("yes");
    }
}
