package net.clementlevallois.nocodeapp.web.front.utils;

import java.io.IOException;
import java.io.StringWriter;
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
            System.out.println("exception when turning json to string: "+ ex.getMessage());
        }
        return output;
    }

    public static byte[] byteArraySerializerForAnyObject(Object o) throws IOException {
        ObjectOutputStream oos;
        byte[] data;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            oos = new ObjectOutputStream(bos);
            oos.writeObject(o);
            oos.flush();
            data = bos.toByteArray();
        }
        oos.close();
        return data;
    }
}
