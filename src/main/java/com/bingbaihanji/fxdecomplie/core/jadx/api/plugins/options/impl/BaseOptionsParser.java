package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.options.impl;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.options.JadxPluginOptions;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Prefer {@link BasePluginOptionsBuilder} as a better way to init and parse options
 */
@Deprecated
public abstract class BaseOptionsParser implements JadxPluginOptions {

    protected Map<String, String> options;

    @Override
    public void setOptions(Map<String, String> options) {
        this.options = options;
        parseOptions();
    }

    public abstract void parseOptions();

    public boolean getBooleanOption(String key, boolean defValue) {
        String val = options.get(key);
        if (val == null) {
            return defValue;
        }
        String valLower = val.toLowerCase(Locale.ROOT);
        if ("yes".equals(valLower) || "true".equals(valLower)) {
            return true;
        }
        if ("no".equals(valLower) || "false".equals(valLower)) {
            return false;
        }
        throw new IllegalArgumentException("Unknown value '" + val + "' for option '" + key + "'"
                + ", expect: 'yes' or 'no'");
    }

    public <T> T getOption(String key, Function<String, T> parse, T defValue) {
        String val = options.get(key);
        if (val == null) {
            return defValue;
        }
        return parse.apply(val);
    }
}
