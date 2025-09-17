package net.clementlevallois.nocodeapp.web.front.flows.umigon;

import java.util.List;
import org.primefaces.model.file.UploadedFile;

public sealed interface UmigonDataSource {

    /**
     * @param files A list of UploadedFile objects from PrimeFaces.
     */
    record FileUpload(List<UploadedFile> files) implements UmigonDataSource {}

    /**
     * @param url The URL of the web page to be scraped.
     */
    record WebPage(String url) implements UmigonDataSource {}

    /**
     * @param rootUrl The starting URL for the crawl.
     * @param maxUrls The maximum number of URLs to crawl.
     * @param exclusionTerms A list of terms to exclude URLs containing them.
     */
    record WebSite(String rootUrl, int maxUrls, String exclusionTerms) implements UmigonDataSource {}
}
