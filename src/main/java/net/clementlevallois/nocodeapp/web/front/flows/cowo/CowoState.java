package net.clementlevallois.nocodeapp.web.front.flows.cowo;

import java.util.List;
import net.clementlevallois.nocodeapp.web.front.flows.base.FlowState;
import org.primefaces.model.file.UploadedFile;


public sealed interface CowoState extends FlowState  {

    record AwaitingDataSource(String jobId)implements CowoState{
    }
    
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
            int minCharNumber) implements CowoState {

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
        
        public AwaitingParameters withJobId(String newJobId) {
            return new AwaitingParameters(newJobId, selectedLanguages, minTermFreq, maxNGram, removeNonAsciiCharacters, scientificCorpus, firstNames, lemmatize, replaceStopwords, usePMI, fileUserStopwords, minCharNumber);
        }
    }

    record Processing(
            String jobId,
            AwaitingParameters parameters,
            int progress) implements CowoState {

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
            boolean shareGephiLitePublicly) implements CowoState {
        
        public ResultsReady withShareVVPublicly(boolean newFlag){
            return new ResultsReady(jobId, gexf, nodesAsJson, edgesAsJson, newFlag, shareGephiLitePublicly);
        }

        public ResultsReady withShareGephiLitePublicly(boolean newFlag){
            return new ResultsReady(jobId, gexf, nodesAsJson, edgesAsJson, shareVVPublicly, newFlag);
        }
    }
    
}