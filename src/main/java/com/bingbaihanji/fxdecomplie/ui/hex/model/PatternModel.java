package com.bingbaihanji.fxdecomplie.ui.hex.model;

import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

public class PatternModel {
    public record Region(long address, long size, String name, String description, Color color, String parentName) {
        public boolean contains(long addr) { return addr >= address && addr < address + size; }
    }

    private final NavigableMap<Long, List<Region>> regionsByAddress = new TreeMap<>();
    private final List<Region> allRegions = new ArrayList<>();

    public void addRegion(Region region) { allRegions.add(region); regionsByAddress.computeIfAbsent(region.address, k -> new ArrayList<>()).add(region); }
    public void clear() { allRegions.clear(); regionsByAddress.clear(); }

    public List<Region> getRegionsAt(long address) {
        List<Region> result = new ArrayList<>();
        var headMap = regionsByAddress.headMap(address + 1, true);
        for (var entry : headMap.descendingMap().entrySet()) { for (Region r : entry.getValue()) { if (r.contains(address)) result.add(r); } }
        result.sort((a, b) -> Long.compare(a.size, b.size));
        return result;
    }

    public Color getColorAt(long address) { List<Region> regions = getRegionsAt(address); return regions.isEmpty() ? null : regions.get(0).color; }

    public String getPathAt(long address) {
        List<Region> regions = getRegionsAt(address);
        if (regions.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = regions.size() - 1; i >= 0; i--) { if (!sb.isEmpty()) sb.append(" > "); sb.append(regions.get(i).name); }
        return sb.toString();
    }

    public List<Region> getAllRegions() { return allRegions; }
}
