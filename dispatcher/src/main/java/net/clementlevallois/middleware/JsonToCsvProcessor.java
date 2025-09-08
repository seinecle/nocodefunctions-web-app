/*
 * Copyright Clement Levallois 2021-2023. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.middleware;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;


/**
 *
 * @author LEVALLOIS
 */
public class JsonToCsvProcessor {
    
    public static void main (String [] args) throws IOException{
        String folder = "C:\\Users\\levallois\\open\\nocode-app-other-repos\\analytics";
        String airtable = "airtable_data.json";
        String csv = "current_year_log_2.txt";
        
        StringBuilder sb = new StringBuilder();
        
        
        JsonReader jsonReader = Json.createReader(new StringReader(Files.readString(Path.of(folder, airtable))));
        JsonArray array = jsonReader.readArray();
        for (JsonValue value: array){
            JsonObject oneRecord = value.asJsonObject();
            JsonObject fieldsOfOneRecord = oneRecord.getJsonObject("fields");
            int timeStamp = fieldsOfOneRecord.getInt("timestamp");
            String browser = fieldsOfOneRecord.getString("browser");
            String function = fieldsOfOneRecord.getString("event").split("\\?")[0].split(":")[1].trim();
            String device = fieldsOfOneRecord.getString("device");
            String os = fieldsOfOneRecord.getString("os");
            sb.append(timeStamp).append(",").append(function).append(",").append(device).append(",").append(os).append(",").append(browser).append("\n");            
        }
        Files.writeString(Path.of(folder,csv), sb.toString(), StandardCharsets.UTF_8);
    }
    
}
