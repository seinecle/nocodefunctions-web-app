package net.clementlevallois.nocodeapp.web.front.flows.topics;

import org.primefaces.model.file.UploadedFile;
import java.util.List;

public sealed interface TopicsDataSource {

    /**
     * @param files A list of UploadedFile objects from PrimeFaces.
     */
    record FileUpload(List<UploadedFile> files) implements TopicsDataSource {}

    /**
     * @param url The URL of the web page to be scraped.
     */
    record WebPage(String url) implements TopicsDataSource {}

    /**
     * @param rootUrl The starting URL for the crawl.
     * @param maxUrls The maximum number of URLs to crawl.
     * @param exclusionTerms A list of terms to exclude URLs containing them.
     */
    record WebSite(String rootUrl, int maxUrls, String exclusionTerms) implements TopicsDataSource {}
}
