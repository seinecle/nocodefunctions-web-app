/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows.cooc;

import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;

/**
 * This sealed interface represents the complete set of possible states for the
 * Co-occurrence (cooc) workflow.
 */
public sealed interface CoocState extends FlowState {
    
    @Override
    String jobId();
    
    /**
     * The initial state of the workflow. No data has been uploaded yet.
     */
    record AwaitingData() implements CoocState {
        @Override
        public String jobId() {
            return null;
        }
    }

    /**
     * The state where data has been successfully imported.
     */
    record DataImported(String jobId) implements CoocState {
        public DataImported withJobId(String newJobId) {
            return new DataImported(newJobId);
        }
    }

    /**
     * The state where data is ready and the analysis parameters are being set.
     */
    record AwaitingParameters(
            String jobId,
            int minSharedTargets) implements CoocState {
        
        public AwaitingParameters withMinSharedTargets(int newMinSharedTargets) {
            return new AwaitingParameters(jobId, newMinSharedTargets);
        }
        public AwaitingParameters withHasHeaders(boolean newHasHeaders) {
            return new AwaitingParameters(jobId, minSharedTargets);
        }
        public AwaitingParameters withSheetName(String newSheetName) {
            return new AwaitingParameters(jobId, minSharedTargets);
        }
    }

    /**
     * Represents the state where the analysis has been submitted to the
     * backend microservice and is currently processing.
     */
    record Processing(
            String jobId,
            AwaitingParameters parameters,
            int progress) implements CoocState {

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
            boolean shareGephiLitePublicly) implements CoocState {

        public ResultsReady withShareVVPublicly(boolean newFlag) {
            return new ResultsReady(jobId, gexf, nodesAsJson, edgesAsJson, newFlag, shareGephiLitePublicly);
        }

        public ResultsReady withShareGephiLitePublicly(boolean newFlag) {
            return new ResultsReady(jobId, gexf, nodesAsJson, edgesAsJson, shareVVPublicly, newFlag);
        }
    }
}