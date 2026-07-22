package com.in10s.logutility.service.search;

import com.in10s.logutility.request.search.SearchRequest;
import com.in10s.logutility.response.search.SearchResult;

/** Entry point to the search engine: one request in, one aggregated result out. */
public interface SearchService {

    SearchResult search(SearchRequest request);
}
