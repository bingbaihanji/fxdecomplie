package com.bingbaihanji.fxdecomplie.ui.hex.model;

import javafx.beans.property.LongProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.SimpleLongProperty;

public class SelectionModel {
    private final LongProperty selectionStart = new SimpleLongProperty(-1);
    private final LongProperty selectionEnd = new SimpleLongProperty(-1);
    private final LongProperty cursorPosition = new SimpleLongProperty(-1);
    private long maxAddress;

    public SelectionModel(long maxAddress) { this.maxAddress = maxAddress; }

    public void clear() {
        selectionStart.set(-1); selectionEnd.set(-1); cursorPosition.set(-1);
    }

    public void startSelection(long address) {
        if (address < 0 || address > maxAddress) return;
        selectionStart.set(address); selectionEnd.set(address); cursorPosition.set(address);
    }

    public void extendSelection(long address) {
        if (address < 0 || address > maxAddress) return;
        if (!hasSelection()) { startSelection(address); return; }
        selectionEnd.set(address); cursorPosition.set(address);
    }

    public void select(long start, long end) {
        if (start < 0 || end > maxAddress || start > maxAddress) return;
        selectionStart.set(Math.min(start, end));
        selectionEnd.set(Math.max(start, end));
        cursorPosition.set(selectionStart.get());
    }

    public boolean hasSelection() { return selectionStart.get() >= 0 && selectionEnd.get() >= 0; }
    public long getMinAddress() { return Math.min(selectionStart.get(), selectionEnd.get()); }
    public long getMaxAddress() { return Math.max(selectionStart.get(), selectionEnd.get()); }

    public void setMaxAddress(long max) {
        this.maxAddress = max;
        if (selectionStart.get() > max) selectionStart.set(-1);
        if (selectionEnd.get() > max) selectionEnd.set(-1);
        if (cursorPosition.get() > max) cursorPosition.set(-1);
    }

    public long getSelectionSize() { return hasSelection() ? getMaxAddress() - getMinAddress() + 1 : 0; }
    public boolean contains(long address) { return hasSelection() && address >= getMinAddress() && address <= getMaxAddress(); }
    public long getSelectionStart() { return selectionStart.get(); }
    public LongProperty selectionStartProperty() { return selectionStart; }
    public long getSelectionEnd() { return selectionEnd.get(); }
    public LongProperty selectionEndProperty() { return selectionEnd; }
    public long getCursorPosition() { return cursorPosition.get(); }
    public ReadOnlyLongProperty cursorPositionProperty() { return cursorPosition; }
}
