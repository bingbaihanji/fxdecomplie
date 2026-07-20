package com.bingbaihanji.fxdecomplie.core.classgraph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Simplified ClassGraph-compatible scan entry point for fxdecomplie.
 *
 * <p>Based on ClassGraph 4.8.184</p>
 * <p>Copyright Luke Hutchison</p>
 * <p>MIT License</p>
 */
public final class ClassGraph {
    private final List<String> acceptPackages = new ArrayList<>();
    private final List<String> rejectPackages = new ArrayList<>();

    public ClassGraph acceptPackages(String... packages) {
        acceptPackages.addAll(Arrays.asList(packages));
        return this;
    }

    public ClassGraph rejectPackages(String... packages) {
        rejectPackages.addAll(Arrays.asList(packages));
        return this;
    }

    public ScanResult scan() {
        return new ScanResult(java.util.Map.of());
    }

    List<String> getAcceptPackages() {
        return List.copyOf(acceptPackages);
    }

    List<String> getRejectPackages() {
        return List.copyOf(rejectPackages);
    }
}
