package com.in10s.logutility.search;

/** Entry point to the search engine: one request in, one aggregated result out. */
public interface SearchService {

    SearchResult search(SearchRequest request);
}
