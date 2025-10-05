package net.clementlevallois.nocodeapp.web.front.flows.spatialize;

import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;

public sealed interface SpatializeState extends FlowState  {

    record AwaitingParameters(
            String jobId,
            int durationInSecond
            ) implements SpatializeState {

        public AwaitingParameters withDurationInSecond(int newDuration) {
            return new AwaitingParameters(jobId, newDuration);
        }

        public AwaitingParameters withJobId(String newJobId) {
            return new AwaitingParameters(newJobId, durationInSecond);
        }
    }

    /**
     * State representing ongoing processing.
     */
    record Processing(
            String jobId,
            AwaitingParameters parameters,
            int progress
            ) implements SpatializeState {

        public Processing withProgress(int newProgress) {
            return new Processing(jobId, parameters, newProgress);
        }
    }

    /**
     * State when results are ready.
     */
    record ResultsReady(
            String jobId,
            String gexf
            ) implements SpatializeState {

    }
}
