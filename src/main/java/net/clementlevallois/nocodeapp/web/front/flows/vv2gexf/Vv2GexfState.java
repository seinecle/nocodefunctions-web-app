package net.clementlevallois.nocodeapp.web.front.flows.vv2gexf;

import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;
import java.nio.file.Path;

public sealed interface Vv2GexfState extends FlowState {

    record AwaitingFile(String jobId, Path jsonFile) implements Vv2GexfState {
        public AwaitingFile withJsonFile(Path p){ return new AwaitingFile(jobId, p); }
    }
    record Processing(String jobId) implements Vv2GexfState {}
    record ResultsReady(String jobId, Path gexfPath, int nodeCount, int edgeCount) implements Vv2GexfState {}
    record Failed(String jobId, String message) implements Vv2GexfState {}
}
