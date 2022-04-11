/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.clementlevallois.nocodeapp.web.front.labelling;

import java.util.List;
import javax.json.Json;
import javax.json.JsonObjectBuilder;
import net.clementlevallois.testbibd.Block;

/**
 *
 * @author LEVALLOIS
 * @param <T>
 */
public class DataBlocks<T> {

    List<List<Block>> data;

    public List<List<Block>> getData() {
        return data;
    }

    public void setData(List<List<Block>> data) {
        this.data = data;
    }

    public String toJson() {
        JsonObjectBuilder job = Json.createObjectBuilder();
        int i = 0;
        for (List<Block> oneSeries : data) {
            JsonObjectBuilder seriesBuilder = Json.createObjectBuilder();
            int k = 0;
            for (Block block : oneSeries) {
                JsonObjectBuilder blockBuilder = Json.createObjectBuilder();
                int j = 0;
                for (Object item : block.getItems()) {
                    blockBuilder.add(String.valueOf(j++), (String) item);
                }
                seriesBuilder.add(String.valueOf(k++), blockBuilder);
            }
            job.add(String.valueOf(i++), seriesBuilder);
        }
        return job.build().toString();

    }

}
