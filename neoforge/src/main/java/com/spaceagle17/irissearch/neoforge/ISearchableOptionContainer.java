package com.spaceagle17.irissearch.neoforge;

public interface ISearchableOptionContainer {
    /**
     * Filters/re-orders the container's main-screen options by the given query, or restores
     * the original (unfiltered) layout when the query is null/blank.
     */
    void irisSearch$setSearchQuery(String query);

    /**
     * Returns the cached GUI path for the given option ID (e.g. "root", "root/LIGHTING",
     * "root/LIGHTING/SHADOWS"). Built once after the container is constructed.
     */
    String irisSearch$getOptionPath(String optionId);
}
