package com.bingbaihanji.fxdecomplie.ui.hex.model;

import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * 模式模型,用于存储和管理十六进制视图中结构化区域(Region)的层级信息 
 * <p>
 * 区域代表数据文件中的逻辑结构片段(如 Java 类文件的常量池、字段、方法等),
 * 每个区域包含地址范围、名称、描述、颜色和父区域名称 
 * 支持按地址快速查询所有包含该地址的区域,并提供颜色和层级路径获取功能 
 * 通常由 {@link com.bingbaihanji.fxdecomplie.ui.hex.highlight.BuiltinHighlighter}
 * 填充,用于在 HexView 中展示结构化的颜色标记和工具提示 
 * </p>
 *
 * @author BingBaiHanJi
 * @see Region
 * @see com.bingbaihanji.fxdecomplie.ui.hex.highlight.BuiltinHighlighter
 */
public class PatternModel {

    /** 按起始地址索引的区域映射(支持快速范围查询) */
    private final NavigableMap<Long, List<Region>> regionsByAddress = new TreeMap<>();

    /** 所有区域的列表(保持添加顺序) */
    private final List<Region> allRegions = new ArrayList<>();

    /**
     * 添加一个区域到模型中 
     * <p>
     * 区域会被同时添加到全局列表和按地址索引的映射中 
     * 多个区域可共享同一起始地址 
     * </p>
     *
     * @param region 要添加的区域对象
     */
    public void addRegion(Region region) {
        allRegions.add(region);
        regionsByAddress.computeIfAbsent(region.address, k -> new ArrayList<>()).add(region);
    }

    /**
     * 清除所有已添加的区域 
     */
    public void clear() {
        allRegions.clear();
        regionsByAddress.clear();
    }

    /**
     * 获取包含指定地址的所有区域 
     * <p>
     * 返回的区域列表按区域大小升序排列(即最内层/最具体的区域排在最前) 
     * 若同一地址被多个区域覆盖,该方法会全部返回 
     * </p>
     *
     * @param address 要查询的地址
     * @return 包含该地址的区域列表(若不存在则返回空列表)
     */
    public List<Region> getRegionsAt(long address) {
        List<Region> result = new ArrayList<>();
        // 获取起始地址小于等于 address 的所有区域条目(按起始地址降序遍历)
        var headMap = regionsByAddress.headMap(address + 1, true);
        for (var entry : headMap.descendingMap().entrySet()) {
            for (Region r : entry.getValue()) {
                if (r.contains(address)) {
                    result.add(r);
                }
            }
        }
        // 按区域大小升序排序(最小的区域即最内层)
        result.sort((a, b) -> Long.compare(a.size, b.size));
        return result;
    }

    /**
     * 获取指定地址处的颜色 
     * <p>
     * 本质上是 {@link #getRegionsAt(long)} 的第一个结果的颜色(最内层区域),
     * 若不存在任何区域则返回 {@code null} 
     * </p>
     *
     * @param address 要查询的地址
     * @return 最内层区域的颜色,若无则返回 {@code null}
     */
    public Color getColorAt(long address) {
        List<Region> regions = getRegionsAt(address);
        return regions.isEmpty() ? null : regions.get(0).color;
    }

    /**
     * 获取指定地址的层级路径字符串 
     * <p>
     * 路径由所有包含该地址的区域名称按从外层到内层的顺序拼接而成,
     * 用 " > " 分隔 若该地址无任何区域,则返回 {@code null} 
     * </p>
     * <p>
     * 例如：对于 Java 类文件中的字段区域,路径可能为
     * {@code "constant_pool > cp[5] > field[0] > attribute[2]"} 
     * </p>
     *
     * @param address 要查询的地址
     * @return 层级路径字符串,若无则返回 {@code null}
     */
    public String getPathAt(long address) {
        List<Region> regions = getRegionsAt(address);
        if (regions.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        // regions 已按 size 升序,即最内层在前；反向遍历可得到从外到内
        for (int i = regions.size() - 1; i >= 0; i--) {
            if (!sb.isEmpty()) {
                sb.append(" > ");
            }
            sb.append(regions.get(i).name);
        }
        return sb.toString();
    }

    /**
     * 获取所有已添加的区域(按添加顺序) 
     *
     * @return 包含所有区域的列表
     */
    public List<Region> getAllRegions() {
        return allRegions;
    }

    /**
     * 表示一个结构化的区域,包含地址范围、名称、描述、颜色和父区域名称 
     * <p>
     * 区域用于标记数据文件中的逻辑片段,通常在 {@link com.bingbaihanji.fxdecomplie.ui.hex.highlight.BuiltinHighlighter}
     * 解析数据时创建 
     * </p>
     *
     * @param address      区域的起始地址(包含)
     * @param size         区域长度(字节)
     * @param name         区域名称(用于路径显示)
     * @param description  区域描述(用于工具提示)
     * @param color        区域颜色(用于背景高亮)
     * @param parentName   父区域名称(可为 {@code null},用于表示层级关系)
     */
    public record Region(long address, long size, String name, String description, Color color, String parentName) {
        /**
         * 判断指定地址是否位于该区域内 
         *
         * @param addr 要检查的地址
         * @return 若 {@code address <= addr < address + size} 则返回 {@code true}
         */
        public boolean contains(long addr) {
            return addr >= address && addr < address + size;
        }
    }
}