package net.clementlevallois.nocodeapp.web.front.flows.umigon;

import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;
import net.clementlevallois.umigon.model.classification.Document;
import java.util.List;

public sealed interface UmigonState extends FlowState {

    record AwaitingParameters(
            String jobId,
            String selectedLanguage,
            int maxCapacity
    ) implements UmigonState {

        public AwaitingParameters withJobId(String newJobId) {
            return new AwaitingParameters(newJobId, selectedLanguage, maxCapacity);
        }

        public AwaitingParameters withSelectedLanguage(String newLanguage) {
            return new AwaitingParameters(jobId, newLanguage, maxCapacity);
        }

        public AwaitingParameters withMaxCapacity(int newMaxCapacity) {
            return new AwaitingParameters(jobId, selectedLanguage, newMaxCapacity);
        }
    }

    record Processing(
            String jobId,
            AwaitingParameters parameters,
            int progress
    ) implements UmigonState {

        public Processing withProgress(int newProgress) {
            return new Processing(jobId, parameters, newProgress);
        }
    }

    record ResultsReady(
            String jobId,
            List<Document> results
    ) implements UmigonState {
    }
}
