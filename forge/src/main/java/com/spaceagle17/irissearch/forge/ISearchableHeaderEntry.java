package com.spaceagle17.irissearch.forge;

/**
 * Duck interface letting the header-entry mixin expose its search/clear toggle button
 * to other mixins on the same instance. Both mixin targets are the same class after
 * weaving; this is how one mixin's source calls the other's members.
 */
public interface ISearchableHeaderEntry {
    /** The search/clear toggle button element, or null if this entry is a plain back button. */
    Object irisSearch$getSearchToggleButton();

    /** Tooltip text for the toggle button, or null if there's nothing to show. */
    String irisSearch$getSearchToggleTooltipText();
}
