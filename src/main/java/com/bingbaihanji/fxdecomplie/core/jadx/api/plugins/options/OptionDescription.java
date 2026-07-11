package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.options;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public interface OptionDescription {

    String name();

    String description();

    /**
     * Possible values.
     * Empty if not a limited set
     */
    List<String> values();

    /**
     * Default value.
     * Null if required
     */
    @Nullable
    String defaultValue();

    default OptionType getType() {
        return OptionType.STRING;
    }

    default Set<OptionFlag> getFlags() {
        return Collections.emptySet();
    }
}
