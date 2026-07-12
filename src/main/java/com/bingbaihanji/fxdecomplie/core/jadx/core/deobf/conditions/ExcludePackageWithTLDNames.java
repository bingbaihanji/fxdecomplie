package com.bingbaihanji.fxdecomplie.core.jadx.core.deobf.conditions;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.PackageNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provides a list of all top level domains, so we can exclude them from deobfuscation.
 */
public class ExcludePackageWithTLDNames extends AbstractDeobfCondition {

    private static final String RESOURCE_NAME = "tlds.txt";
    private static final String LEGACY_RESOURCE_PATH = "/jadx/core/deobf/conditions/tlds.txt";

    private static Set<String> loadTldSet() {
        InputStream inputStream = ExcludePackageWithTLDNames.class.getResourceAsStream(RESOURCE_NAME);
        if (inputStream == null) {
            inputStream = ExcludePackageWithTLDNames.class.getResourceAsStream(LEGACY_RESOURCE_PATH);
        }
        if (inputStream == null) {
            throw new JadxRuntimeException("Failed to load top level domain list file: " + RESOURCE_NAME);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .filter(line -> !line.startsWith("#") && !line.isEmpty())
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            throw new JadxRuntimeException("Failed to load top level domain list file: " + RESOURCE_NAME, e);
        }
    }

    @Override
    public Action check(PackageNode pkg) {
        if (pkg.isRoot() && TldHolder.TLD_SET.contains(pkg.getName())) {
            return Action.FORBID_RENAME;
        }
        return Action.NO_ACTION;
    }

    /**
     * Lazy load TLD set
     */
    private static class TldHolder {
        private static final Set<String> TLD_SET = loadTldSet();
    }
}
