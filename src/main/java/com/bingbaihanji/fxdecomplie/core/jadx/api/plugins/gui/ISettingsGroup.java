package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.gui;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * Settings page customization
 */
public interface ISettingsGroup {

    /**
     * Node name
     */
    String getTitle();

    /**
     * Custom page component
     */
    JComponent buildComponent();

    /**
     * Optional child nodes list
     */
    default List<ISettingsGroup> getSubGroups() {
        return Collections.emptyList();
    }

    /**
     * Settings close handler.
     * Apply settings if 'save' param set to true.
     * It can be used to clean up resources.
     */
    default void close(boolean save) {
        // optional method
    }
}
