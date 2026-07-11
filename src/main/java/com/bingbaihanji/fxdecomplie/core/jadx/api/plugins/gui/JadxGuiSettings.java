package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.gui;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.options.OptionDescription;

import java.util.List;

public interface JadxGuiSettings {

    /**
     * Set plugin custom settings page
     */
    void setCustomSettingsGroup(ISettingsGroup group);

    /**
     * Helper method to build options group only for provided option list
     */
    ISettingsGroup buildSettingsGroupForOptions(String title, List<OptionDescription> options);
}
