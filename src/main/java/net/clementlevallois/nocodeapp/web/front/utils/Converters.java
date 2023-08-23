package net.clementlevallois.nocodeapp.web.front.utils;

import java.io.IOException;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;

/**
 *
 * @author LEVALLOIS
 */
public class Converters {

    public static String turnJsonObjectToString(JsonObject jsonObject) {
        String output = "{}";
        try ( java.io.StringWriter stringWriter = new StringWriter()) {
            var jsonWriter = Json.createWriter(stringWriter);
            jsonWriter.writeObject(jsonObject);
            output = stringWriter.toString();
        } catch (IOException ex) {
            Logger.getLogger(Converters.class.getName()).log(Level.SEVERE, null, ex);
        }
        return output;
    }

    public static byte[] byteArraySerializerForAnyObject(Object o) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(o);
        oos.flush();
        byte[] data = bos.toByteArray();
        return data;
    }

}
