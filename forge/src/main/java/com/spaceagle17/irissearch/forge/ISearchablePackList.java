package com.spaceagle17.irissearch.forge;

public interface ISearchablePackList {
    String irisSearch$getTypedSearchQuery();
    void irisSearch$setTypedSearchQuery(String query);
    int irisSearch$getSavedCursorPosition();
    void irisSearch$setSavedCursorPosition(int pos);
    // Apply query and refresh (unlike setTypedSearchQuery, which only stores).
    void irisSearch$updateSearchQuery(String query);

    /** Vertical space (px) the search box lives in, above the list's own rows. */
    int irisSearch$getReservedHeaderHeight();

    // Live bounds reflecting the list's current position (via reflection on inherited vanilla fields).
    int irisSearch$getListLeft();
    int irisSearch$getListTop();
    int irisSearch$getListWidth();

    // Shift y0 down to reserve header space above the list's rows.
    void irisSearch$setListTop(int top);

    // Computed locally (not by calling the live override)—published Oculus jar has overrides SRG-named.
    int irisSearch$getRowWidth();
    boolean irisSearch$shouldShowSearchBar();
}
