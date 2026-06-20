package com.spaceagle17.irissearch.fabric;

/**
 * Duck interface letting YarnShaderPackOptionListHeaderEntryMixin access the search/clear
 * toggle button built by ShaderPackOptionListHeaderEntryMixin. Both target the same class
 * and are the same object after weaving; this is how one mixin's source calls the other's members.
 */
public interface ISearchableHeaderEntry {
    /** The search/clear toggle button element, or null if this entry is a plain back button. */
    Object irisSearch$getSearchToggleButton();

    /** Tooltip text for the toggle button, or null if there's nothing to show. */
    String irisSearch$getSearchToggleTooltipText();
}