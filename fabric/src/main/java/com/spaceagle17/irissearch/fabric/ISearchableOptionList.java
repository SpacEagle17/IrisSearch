package com.spaceagle17.irissearch.fabric;

public interface ISearchableOptionList {
    boolean irisSearch$isSearchModeActive();
    String irisSearch$getTypedSearchQuery();
    void irisSearch$setTypedSearchQuery(String query);
    int irisSearch$getSavedCursorPosition();
    void irisSearch$setSavedCursorPosition(int pos);
    /** Applies the query to the option container and triggers a list rebuild. Distinct from setTypedSearchQuery, which only stores the value. */
    void irisSearch$updateSearchQuery(String query);
    void irisSearch$disableSearchMode();
    void irisSearch$disableSearchModeAndRebuild();
    void irisSearch$enableSearchModeAndRebuild();
    /** Horizontal space (px) held on the left side of the header row by the search/clear toggle button. The search box starts after this margin. */
    int irisSearch$getReservedLeftWidth();
    void irisSearch$setReservedLeftWidth(int width);
    void irisSearch$setListBounds(int left, int top, int width);
    int irisSearch$getListLeft();
    int irisSearch$getListTop();
    int irisSearch$getListWidth();

    /**
     * The HeaderEntry row's actual render-time bounds, published by the header-row mixins
     * every frame. Reading these directly instead of re-deriving position from the list
     * bounds prevents Y-alignment from breaking when Iris changes internal padding constants.
     */
    void irisSearch$publishHeaderRowBounds(int x, int y, int width, int height, boolean usesGetterShape);
    boolean irisSearch$hasHeaderRowBounds();
    int irisSearch$getHeaderRowX();
    int irisSearch$getHeaderRowY();
    int irisSearch$getHeaderRowWidth();
    int irisSearch$getHeaderRowHeight();
    /** True if the header entry publishes position via getters (newer Iris); false if x/y are passed as render params. Affects search-box vertical offset. */
    boolean irisSearch$headerRowUsesGetterShape();
    /** True when the option list is currently showing a sub-screen. Ctrl+F search activation is blocked while navigated into a sub-screen. */
    boolean irisSearch$isOnSubScreen();
}