package net.clementlevallois.nocodeapp.web.front.flows.vv2gexf;

import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;

public sealed interface Vv2gexfState extends FlowState {

    record AwaitingFile(String jobId, String uploadedFileName) implements Vv2gexfState {}

    record Processing(String jobId, AwaitingFile parameters, int progress) implements Vv2gexfState {
        public Processing withProgress(int newProgress) {
            return new Processing(jobId, parameters, newProgress);
        }
    }

    record ResultsReady(String jobId, byte[] gexfBytes) implements Vv2gexfState {}
}
