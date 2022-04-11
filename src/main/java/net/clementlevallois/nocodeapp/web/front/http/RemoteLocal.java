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
        try {
            HttpServletRequest request = (HttpServletRequest) FacesContext.getCurrentInstance().getExternalContext().getRequest();
            String string = request.getRequestURL().toString();
            URL url = new URL(string);
            String interm;
            String post;
            if (url.getAuthority().contains("localhost")) {
                interm = "://";
                post = "jsf-app/";
            } else {
                interm = "s://";
                post = "";
            }
            String result = url.getProtocol() + interm + url.getAuthority() + "/" + post;
//            System.out.println("return url is: " + result);
            return result;
        } catch (MalformedURLException ex) {
            Logger.getLogger(GoogleSheetsImportBean.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "error in url retrieval";
    }

    public static boolean isLocal() {
        try {
            HttpServletRequest request = (HttpServletRequest) FacesContext.getCurrentInstance().getExternalContext().getRequest();
            String string = request.getRequestURL().toString();
            URL url = new URL(string);
            return url.getAuthority().contains("localhost");
        } catch (MalformedURLException ex) {
            Exceptions.printStackTrace(ex);
            return false;
        }
    }
    public static boolean isTest() {
        try {
            HttpServletRequest request = (HttpServletRequest) FacesContext.getCurrentInstance().getExternalContext().getRequest();
            String string = request.getRequestURL().toString();
            URL url = new URL(string);
            return url.getAuthority().contains("test");
        } catch (MalformedURLException ex) {
            Exceptions.printStackTrace(ex);
            return false;
        }
    }
}
