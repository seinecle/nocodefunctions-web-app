/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows.sim;

import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;

public sealed interface SimState extends FlowState  {

    record AwaitingParameters(
            String jobId,
            int minSharedTargets,
            String sheetName,
            String sourceColIndex
            ) implements SimState {

        public AwaitingParameters withJobId(String newJobId) {
            return new AwaitingParameters(newJobId, minSharedTargets, sheetName, sourceColIndex);
        }

        public AwaitingParameters withMinSharedTargets(int newValue) {
            return new AwaitingParameters(jobId, newValue, sheetName, sourceColIndex);
        }

        public AwaitingParameters withSheetName(String newValue) {
            return new AwaitingParameters(jobId, minSharedTargets, newValue, sourceColIndex);
        }

        public AwaitingParameters withSourceColIndex(String newValue) {
            return new AwaitingParameters(jobId, minSharedTargets, sheetName, newValue);
        }
    }

    record Processing(
            String jobId,
            AwaitingParameters parameters,
            int progress
            ) implements SimState {

        public Processing withProgress(int newProgress) {
            return new Processing(jobId, parameters, newProgress);
        }
    }

    record ResultsReady(
            String jobId,
            String gexf,
            String nodesAsJson,
            String edgesAsJson,
            boolean shareVVPublicly,
            boolean shareGephiLitePublicly
            ) implements SimState {

        public ResultsReady withShareVVPublicly(boolean newFlag) {
            return new ResultsReady(jobId, gexf, nodesAsJson, edgesAsJson, newFlag, shareGephiLitePublicly);
        }

        public ResultsReady withShareGephiLitePublicly(boolean newFlag) {
            return new ResultsReady(jobId, gexf, nodesAsJson, edgesAsJson, shareVVPublicly, newFlag);
        }
    }
}
