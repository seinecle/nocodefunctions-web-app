/*
 * Copyright Clement Levallois 2021-2024. License Attribution 4.0 Intertnational (CC BY 4.0)
 */
package net.clementlevallois.nocodeapp.web.front.exportdata;

import jakarta.json.JsonObject;
import net.clementlevallois.nocodeapp.web.front.utils.Converters;

/**
 *
 * @author LEVALLOIS
 */
public class TopNodes {

    String nodesAsJson;
    String edgesAsJson;

    public TopNodes() {
    }

    public void getJsonAndConvertToString(JsonObject jsonObject) {
        nodesAsJson = Converters.turnJsonObjectToString(jsonObject.getJsonObject("nodes"));
        edgesAsJson = Converters.turnJsonObjectToString(jsonObject.getJsonObject("edges"));
    }

    public String getNodesAsJson() {
        return nodesAsJson;
    }

    public String getEdgesAsJson() {
        return edgesAsJson;
    }

}
