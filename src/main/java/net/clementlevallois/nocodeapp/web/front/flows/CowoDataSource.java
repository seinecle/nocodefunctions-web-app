/*
 * Licence Apache 2.0
 * https://www.apache.org/licenses/LICENSE-2.0
 */
package net.clementlevallois.nocodeapp.web.front.flows;

import org.primefaces.model.file.UploadedFile;
import java.util.List;

/**
 * Represents a source of data for the cowo workflow. This is a sealed
 * interface, establishing a closed set of all possible data sources. This
 * allows for exhaustive, type-safe pattern matching in the data preparation
 * service.
 */
public sealed interface CowoDataSource {

    /**
     * Represents a data source from one or more files uploaded by the user.
     *
     * @param files A list of UploadedFile objects from PrimeFaces.
     */
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
    record WebSite(String rootUrl, int maxUrls, List<String> exclusionTerms) implements CowoDataSource {}
}