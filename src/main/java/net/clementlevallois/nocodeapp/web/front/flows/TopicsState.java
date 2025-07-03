package net.clementlevallois.nocodeapp.web.front.flows;

import java.util.Map;
import net.clementlevallois.utils.Multiset;
import org.primefaces.model.file.UploadedFile;

/**
 * This sealed interface represents the complete set of possible states for the
 * Topic Modeling (topics) workflow. Using a sealed interface ensures that any
 * instance of TopicsState must be one of the permitted record types, allowing
 * for exhaustive, compile-time-checked pattern matching.
 */
public sealed interface TopicsState {

    String jobId();

    /**
     * The initial state of the workflow, where the application is awaiting user
     * configuration. It holds all the parameters that can be set by the user.
     * Each 'with...' method returns a new, immutable instance of this state
     * with the updated parameter.
     */
    record AwaitingParameters(
            String jobId,
            String selectedLanguage,
            int precision,
            int minCharNumber,
            int minTermFreq,
            boolean scientificCorpus,
            boolean replaceStopwords,
            boolean lemmatize,
            boolean removeNonAsciiCharacters,
            UploadedFile fileUserStopwords) implements TopicsState {

        public AwaitingParameters withJobId(String newJobId) {
            return new AwaitingParameters(newJobId, selectedLanguage, precision, minCharNumber, minTermFreq, scientificCorpus, replaceStopwords, lemmatize, removeNonAsciiCharacters, fileUserStopwords);
        }
        
        public AwaitingParameters withSelectedLanguage(String newLanguage) {
            return new AwaitingParameters(jobId, newLanguage, precision, minCharNumber, minTermFreq, scientificCorpus, replaceStopwords, lemmatize, removeNonAsciiCharacters, fileUserStopwords);
        }
        
        public AwaitingParameters withPrecision(int newPrecision) {
            return new AwaitingParameters(jobId, selectedLanguage, newPrecision, minCharNumber, minTermFreq, scientificCorpus, replaceStopwords, lemmatize, removeNonAsciiCharacters, fileUserStopwords);
        }

        public AwaitingParameters withMinCharNumber(int newMinChar) {
            return new AwaitingParameters(jobId, selectedLanguage, precision, newMinChar, minTermFreq, scientificCorpus, replaceStopwords, lemmatize, removeNonAsciiCharacters, fileUserStopwords);
        }
        
        public AwaitingParameters withMinTermFreq(int newMinFreq) {
            return new AwaitingParameters(jobId, selectedLanguage, precision, minCharNumber, newMinFreq, scientificCorpus, replaceStopwords, lemmatize, removeNonAsciiCharacters, fileUserStopwords);
        }
        
        public AwaitingParameters withScientificCorpus(boolean flag) {
            return new AwaitingParameters(jobId, selectedLanguage, precision, minCharNumber, minTermFreq, flag, replaceStopwords, lemmatize, removeNonAsciiCharacters, fileUserStopwords);
        }

        public AwaitingParameters withReplaceStopwords(boolean flag) {
            return new AwaitingParameters(jobId, selectedLanguage, precision, minCharNumber, minTermFreq, scientificCorpus, flag, lemmatize, removeNonAsciiCharacters, fileUserStopwords);
        }

        public AwaitingParameters withLemmatize(boolean flag) {
            return new AwaitingParameters(jobId, selectedLanguage, precision, minCharNumber, minTermFreq, scientificCorpus, replaceStopwords, flag, removeNonAsciiCharacters, fileUserStopwords);
        }
        
        public AwaitingParameters withRemoveNonAsciiCharacters(boolean flag) {
            return new AwaitingParameters(jobId, selectedLanguage, precision, minCharNumber, minTermFreq, scientificCorpus, replaceStopwords, lemmatize, flag, fileUserStopwords);
        }
        
        public AwaitingParameters withFileUserStopwords(UploadedFile newFile) {
            return new AwaitingParameters(jobId, selectedLanguage, precision, minCharNumber, minTermFreq, scientificCorpus, replaceStopwords, lemmatize, removeNonAsciiCharacters, newFile);
        }
    }

    /**
     * Represents the state where the analysis has been submitted to the
     * backend microservice and is currently processing. It includes the current progress.
     */
    record Processing(
            String jobId,
            AwaitingParameters parameters,
            int progress) implements TopicsState {

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
            Map<Integer, Multiset<String>> keywordsPerTopic,
            boolean shareGephiLitePublicly) implements TopicsState {
        
        public ResultsReady withShareGephiLitePublicly(boolean newFlag){
            return new ResultsReady(jobId, gexf, keywordsPerTopic, newFlag);
        }
    }

    /**
     * A terminal state representing a failure in the workflow. It captures the
     * original parameters and an error message for diagnosis.
     */
    record FlowFailed(
            String jobId,
            AwaitingParameters parameters,
            String errorMessage) implements TopicsState {
    }
}
