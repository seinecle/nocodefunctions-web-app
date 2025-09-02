/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows.cowo;

import org.primefaces.model.file.UploadedFile;
import java.util.List;


public sealed interface CowoDataSource {

    record FileUpload(List<UploadedFile> files) implements CowoDataSource {}

    /**
     * Represents a data source from a single web page URL provided by the user.
     *
     * @param url The URL of the web page to be scraped.
     */
    record WebPage(String url) implements CowoDataSource {}

    /**
     * Represents a data source from crawling an entire website.
     *
     * @param rootUrl The starting URL for the crawl.
     * @param maxUrls The maximum number of URLs to crawl.
     * @param exclusionTerms A list of terms to exclude URLs containing them.
     */
    record WebSite(String rootUrl, int maxUrls, String exclusionTerms) implements CowoDataSource {}
}