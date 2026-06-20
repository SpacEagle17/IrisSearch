package com.spaceagle17.irissearch.fabric;

public interface ISearchableOptionContainer {
    /**
     * Filters/re-orders the container's main-screen options by the given query, or restores
     * the original (unfiltered) layout when the query is null/blank.
     */
    void irisSearch$setSearchQuery(String query);
}