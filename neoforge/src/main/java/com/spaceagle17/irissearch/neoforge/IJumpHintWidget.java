package com.spaceagle17.irissearch.neoforge;

public interface IJumpHintWidget {
    /**
     * Queues "Ctrl+Click to open" popup when Ctrl is held over a search result.
     * @return true if a popup was queued
     */
    boolean irisSearch$tryQueueJumpHint(Object guiGraphics, int mouseX, int mouseY, boolean hovered);
}
