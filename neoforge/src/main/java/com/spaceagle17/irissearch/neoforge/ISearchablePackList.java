package com.spaceagle17.irissearch.neoforge;

public interface ISearchablePackList {
    String irisSearch$getTypedSearchQuery();
    void irisSearch$setTypedSearchQuery(String query);
    int irisSearch$getSavedCursorPosition();
    void irisSearch$setSavedCursorPosition(int pos);
    void irisSearch$updateSearchQuery(String query);
    int irisSearch$getReservedHeaderHeight();
    int irisSearch$getListLeft();
    int irisSearch$getListTop();
    int irisSearch$getListWidth();
    int irisSearch$getListBottom();
    int irisSearch$getRowWidth();
    boolean irisSearch$shouldShowSearchBar();
}
