/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows;


import java.util.List;
import org.primefaces.model.file.UploadedFile;

/**
 * This sealed interface represents the complete set of possible states for the
 * Semantic Network (cowo) workflow. Using a sealed interface ensures that any
 * instance of CowoFlowState must be one of the permitted record types, allowing
 * for exhaustive, compile-time-checked pattern matching.
 */
public sealed interface CowoFlowState {

    String jobId();

    /**
     * The initial state of the workflow, where the application is awaiting user
     * configuration. It holds all the parameters that can be set by the user.
     * Each 'with...' method returns a new, immutable instance of this state
     * with the updated parameter, which is ideal for use with JSF backing beans.
     */
    record AwaitingParameters(
            String jobId,
            List<String> selectedLanguages,
            int minTermFreq,
            int maxNGram,
            boolean removeNonAsciiCharacters,
            boolean scientificCorpus,
            boolean firstNames,
            boolean lemmatize,
            boolean replaceStopwords,
            boolean usePMI,
            UploadedFile fileUserStopwords,
            int minCharNumber) implements CowoFlowState {

        public AwaitingParameters withSelectedLanguages(List<String> newLanguages) {
            return new AwaitingParameters(jobId, newLanguages, minTermFreq, maxNGram, removeNonAsciiCharacters, scientificCorpus, firstNames, lemmatize, replaceStopwords, usePMI, fileUserStopwords, minCharNumber);
        }

        public AwaitingParameters withMinTermFreq(int newMinTermFreq) {
            return new AwaitingParameters(jobId, selectedLanguages, newMinTermFreq, maxNGram, removeNonAsciiCharacters, scientificCorpus, firstNames, lemmatize, replaceStopwords, usePMI, fileUserStopwords, minCharNumber);
        }
        
        public AwaitingParameters withMaxNGram(int newMaxNGram) {
            return new AwaitingParameters(jobId, selectedLanguages, minTermFreq, newMaxNGram, removeNonAsciiCharacters, scientificCorpus, firstNames, lemmatize, replaceStopwords, usePMI, fileUserStopwords, minCharNumber);
        }

        public AwaitingParameters withRemoveNonAsciiCharacters(boolean newFlag) {
            return new AwaitingParameters(jobId, selectedLanguages, minTermFreq, maxNGram, newFlag, scientificCorpus, firstNames, lemmatize, replaceStopwords, usePMI, fileUserStopwords, minCharNumber);
        }
        
        public AwaitingParameters withScientificCorpus(boolean newFlag) {
            return new AwaitingParameters(jobId, selectedLanguages, minTermFreq, maxNGram, removeNonAsciiCharacters, newFlag, firstNames, lemmatize, replaceStopwords, usePMI, fileUserStopwords, minCharNumber);
        }

        public AwaitingParameters withFirstNames(boolean newFlag) {
            return new AwaitingParameters(jobId, selectedLanguages, minTermFreq, maxNGram, removeNonAsciiCharacters, scientificCorpus, newFlag, lemmatize, replaceStopwords, usePMI, fileUserStopwords, minCharNumber);
        }

        public AwaitingParameters withLemmatize(boolean newFlag) {
            return new AwaitingParameters(jobId, selectedLanguages, minTermFreq, maxNGram, removeNonAsciiCharacters, scientificCorpus, firstNames, newFlag, replaceStopwords, usePMI, fileUserStopwords, minCharNumber);
        }

        public AwaitingParameters withReplaceStopwords(boolean newFlag) {
            return new AwaitingParameters(jobId, selectedLanguages, minTermFreq, maxNGram, removeNonAsciiCharacters, scientificCorpus, firstNames, lemmatize, newFlag, usePMI, fileUserStopwords, minCharNumber);
        }

        public AwaitingParameters withUsePMI(boolean newFlag) {
            return new AwaitingParameters(jobId, selectedLanguages, minTermFreq, maxNGram, removeNonAsciiCharacters, scientificCorpus, firstNames, lemmatize, replaceStopwords, newFlag, fileUserStopwords, minCharNumber);
        }

        public AwaitingParameters withFileUserStopwords(UploadedFile newFile) {
            return new AwaitingParameters(jobId, selectedLanguages, minTermFreq, maxNGram, removeNonAsciiCharacters, scientificCorpus, firstNames, lemmatize, replaceStopwords, usePMI, newFile, minCharNumber);
        }
        
        public AwaitingParameters withMinCharNumber(int newMinChar) {
            return new AwaitingParameters(jobId, selectedLanguages, minTermFreq, maxNGram, removeNonAsciiCharacters, scientificCorpus, firstNames, lemmatize, replaceStopwords, usePMI, fileUserStopwords, newMinChar);
        }
    }

    /**
     * Represents the state where the analysis has been submitted to the
     * backend microservice and is currently processing. It carries forward all
     * the parameters from the previous state and includes the current progress.
     */
    record Processing(
            String jobId,
            AwaitingParameters parameters,
            int progress) implements CowoFlowState {

        public Processing withProgress(int newProgress) {
            return new Processing(jobId, parameters, newProgress);
        }
    }

    /**
     * The terminal state representing a successfully completed analysis. It
     * holds all the necessary information to display and export the results.
     */
    record ResultsReady(
            String jobId,
            String gexf,
            String nodesAsJson,
            String edgesAsJson,
            int minFreqNode,
            int maxFreqNode,
            boolean shareVVPublicly,
            boolean shareGephiLitePublicly) implements CowoFlowState {
        
        public ResultsReady withShareVVPublicly(boolean newFlag){
            return new ResultsReady(jobId, gexf, nodesAsJson, edgesAsJson, minFreqNode, maxFreqNode, newFlag, shareGephiLitePublicly);
        }

        public ResultsReady withShareGephiLitePublicly(boolean newFlag){
            return new ResultsReady(jobId, gexf, nodesAsJson, edgesAsJson, minFreqNode, maxFreqNode, shareVVPublicly, newFlag);
        }
    }

    /**
     * A terminal state representing a failure in the workflow. It captures the
     * original parameters and an error message for diagnosis.
     */
    record FlowFailed(
            String jobId,
            AwaitingParameters parameters,
            String errorMessage) implements CowoFlowState {
    }
}