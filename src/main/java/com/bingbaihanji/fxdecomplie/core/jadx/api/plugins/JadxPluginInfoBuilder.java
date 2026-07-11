package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins;

import com.bingbaihanji.fxdecomplie.core.jadx.core.plugins.versions.VerifyRequiredVersion;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class JadxPluginInfoBuilder {
    private String pluginId;
    private String name;
    private String description;
    private String homepage = "";
    private @Nullable String requiredJadxVersion;
    private @Nullable String provides;

    private JadxPluginInfoBuilder() {
    }

    /**
     * Start building method
     */
    public static JadxPluginInfoBuilder pluginId(String pluginId) {
        JadxPluginInfoBuilder builder = new JadxPluginInfoBuilder();
        builder.pluginId = Objects.requireNonNull(pluginId);
        return builder;
    }

    public JadxPluginInfoBuilder name(String name) {
        this.name = Objects.requireNonNull(name);
        return this;
    }

    public JadxPluginInfoBuilder description(String description) {
        this.description = Objects.requireNonNull(description);
        return this;
    }

    public JadxPluginInfoBuilder homepage(String homepage) {
        this.homepage = homepage;
        return this;
    }

    public JadxPluginInfoBuilder provides(String provides) {
        this.provides = provides;
        return this;
    }

    public JadxPluginInfoBuilder requiredJadxVersion(String versions) {
        this.requiredJadxVersion = versions;
        return this;
    }

    public JadxPluginInfo build() {
        Objects.requireNonNull(pluginId, "PluginId is required");
        Objects.requireNonNull(name, "Name is required");
        Objects.requireNonNull(description, "Description is required");
        if (provides == null) {
            provides = pluginId;
        }
        if (requiredJadxVersion != null) {
            VerifyRequiredVersion.verify(requiredJadxVersion);
        }
        JadxPluginInfo pluginInfo = new JadxPluginInfo(pluginId, name, description, homepage, provides);
        pluginInfo.setRequiredJadxVersion(requiredJadxVersion);
        return pluginInfo;
    }
}
