/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows.cooc;

import java.io.Serializable;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;

/**
 * This sealed interface represents the complete set of possible states for the
 * Co-occurrence (cooc) workflow.
 */
public sealed interface CoocState extends FlowState {
    
    /**
     * The initial state of the workflow. No data has been uploaded yet.
     */
    record AwaitingData(String jobId) implements CoocState, Serializable {
    }

    /**
     * The state where data is ready and the analysis parameters are being set.
     */
    record AwaitingParameters(
            String jobId,String fileName,
            int minSharedTargets, boolean hasHeaders) implements CoocState, Serializable {
        
        public AwaitingParameters withJobId(String jobId) {
            return new AwaitingParameters(jobId, fileName, minSharedTargets, hasHeaders);
        }
        public AwaitingParameters withFileName(String newFileName) {
            return new AwaitingParameters(jobId, newFileName, minSharedTargets, hasHeaders);
        }
        public AwaitingParameters withMinSharedTargets(int newMinSharedTargets) {
            return new AwaitingParameters(jobId, fileName, newMinSharedTargets, hasHeaders);
        }
        public AwaitingParameters withHasHeaders(boolean newHasHeaders) {
            return new AwaitingParameters(jobId, fileName, minSharedTargets, newHasHeaders);
        }
    }

    /**
     * Represents the state where the analysis has been submitted to the
     * backend microservice and is currently processing.
     */
    record Processing(
            String jobId,
            AwaitingParameters parameters,
            int progress) implements CoocState, Serializable {

        public Processing withProgress(int newProgress) {
            return new Processing(jobId, parameters, newProgress);
        }
    }

    /**
     * The terminal state representing a successfully completed analysis.
     */
    record ResultsReady(
            String jobId,
            String gexf,
            String nodesAsJson,
            String edgesAsJson,
            boolean shareVVPublicly,
            boolean shareGephiLitePublicly) implements CoocState, Serializable {

        public ResultsReady withShareVVPublicly(boolean newFlag) {
            return new ResultsReady(jobId, gexf, nodesAsJson, edgesAsJson, newFlag, shareGephiLitePublicly);
        }

        public ResultsReady withShareGephiLitePublicly(boolean newFlag) {
            return new ResultsReady(jobId, gexf, nodesAsJson, edgesAsJson, shareVVPublicly, newFlag);
        }
    }
}