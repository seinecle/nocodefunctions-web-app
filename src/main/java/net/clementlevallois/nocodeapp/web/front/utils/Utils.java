/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package net.clementlevallois.nocodeapp.web.front.utils;

import java.io.IOException;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.json.Json;
import jakarta.json.JsonObject;

/**
 *
 * @author LEVALLOIS
 */
public class Utils {

    public static String turnJsonObjectToString(JsonObject jsonObject) {
        String output = "{}";
        try ( java.io.StringWriter stringWriter = new StringWriter()) {
            var jsonWriter = Json.createWriter(stringWriter);
            jsonWriter.writeObject(jsonObject);
            output = stringWriter.toString();
        } catch (IOException ex) {
            Logger.getLogger(Utils.class.getName()).log(Level.SEVERE, null, ex);
        }
        return output;
    }

}
