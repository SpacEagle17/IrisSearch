package com.spaceagle17.irissearch.util;

import com.spaceagle17.irissearch.engine.IrisShaderPackTranslations;

import java.util.Objects;

public class PreservedSearchState {
    // Cross-instance search state: saved by onClose() so a freshly created ShaderPackScreen
    // can restore the search bar the user had open when they clicked Done/Cancel.
    public static final PreservedSearchState pending = new PreservedSearchState();

    public boolean active = false;
    public String query = "";
    public int cursor = 0;
    public String packName = null;

    public void captureFrom(String query, boolean searchModeActive, int cursor, String packName) {
        this.active = searchModeActive && !query.isEmpty();
        this.query = query;
        this.cursor = cursor;
        this.packName = packName;
    }

    public void copyFrom(PreservedSearchState other) {
        this.active = other.active;
        this.query = other.query;
        this.cursor = other.cursor;
        this.packName = other.packName;
    }

    public void clear() {
        active = false;
        query = "";
        cursor = 0;
        packName = null;
    }

    /** Whether {@link #packName} still matches the currently loaded shader pack. */
    public boolean matchesCurrentPack() {
        return Objects.equals(packName, IrisShaderPackTranslations.getCurrentPackName());
    }
}
