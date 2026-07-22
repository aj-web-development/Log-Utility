package com.app.logutility.service.search;

import com.app.logutility.request.search.SearchRequest;
import com.app.logutility.response.search.SearchResult;

/** Entry point to the search engine: one request in, one aggregated result out. */
public interface SearchService {

    SearchResult search(SearchRequest request);
}
