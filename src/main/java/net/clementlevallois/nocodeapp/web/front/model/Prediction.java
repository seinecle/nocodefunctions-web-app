package net.clementlevallois.nocodeapp.web.front.model;

/**
 *
 * @author LEVALLOIS
 */
public record Prediction(
        String sourceId,
        String sourceLabel,
        int sourceDegree,
        String targetId,
        String targetLabel,
        int targetDegree,
        int predictionValue) {

}
