package net.clementlevallois.nocodeapp.web.front.flows.topics;

import java.util.Map;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;
import net.clementlevallois.utils.Multiset;
import org.primefaces.model.file.UploadedFile;

public sealed interface TopicsState extends FlowState {

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

    record Processing(
            String jobId,
            AwaitingParameters parameters,
            int progress) implements TopicsState {

        public Processing withProgress(int newProgress) {
            return new Processing(jobId, parameters, newProgress);
        }
    }

    record ResultsReady(
            String jobId,
            String gexf,
            Map<Integer, Multiset<String>> keywordsPerTopic,
            boolean shareGephiLitePublicly,
            boolean shareVVPublicly
            ) implements TopicsState {
        
        public ResultsReady withShareGephiLitePublicly(boolean newFlag){
            return new ResultsReady(jobId, gexf, keywordsPerTopic, newFlag, shareVVPublicly);
        }
        public ResultsReady withShareVVPublicly(boolean newFlag){
            return new ResultsReady(jobId, gexf, keywordsPerTopic, shareGephiLitePublicly, newFlag);
        }
    }
}
