package net.clementlevallois.nocodeapp.web.front.flows.gexf2vv;

import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;

public sealed interface Gexf2VvState extends FlowState {

    record AwaitingParameters(
            String jobId,
            String item,
            String link,
            String linkStrength,
            boolean shareVVPublicly
            ) implements Gexf2VvState {

        public AwaitingParameters withItem(String v) {
            return new AwaitingParameters(jobId, v, link, linkStrength, shareVVPublicly);
        }

        public AwaitingParameters withLink(String v) {
            return new AwaitingParameters(jobId, item, v, linkStrength, shareVVPublicly);
        }

        public AwaitingParameters withLinkStrength(String v) {
            return new AwaitingParameters(jobId, item, link, v, shareVVPublicly);
        }

        public AwaitingParameters withShareVVPublicly(boolean v) {
            return new AwaitingParameters(jobId, item, link, linkStrength, v);
        }

        public AwaitingParameters withJobId(String v) {
            return new AwaitingParameters(v, item, link, linkStrength, shareVVPublicly);
        }
    }

    record Processing(String jobId, AwaitingParameters parameters, int progress) implements Gexf2VvState {

    }

    record ResultsReady(String jobId, String vosviewerUrl, boolean shareVVPublicly) implements Gexf2VvState {

    }
}
