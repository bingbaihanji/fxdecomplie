package com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen;

import com.bingbaihanji.fxdecomplie.core.jadx.api.security.IJadxSecurity;
import com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen.entry.ResourceEntry;

import java.util.*;

/**
 * 资源存储类，用于管理和存储Android资源条目
 * 提供资源条目的添加、替换、重命名和查询功能
 */
public class ResourceStorage {
    /**
     * 资源条目名称比较器，按配置、类型名、键名依次排序
     */
    private static final Comparator<ResourceEntry> RES_ENTRY_NAME_COMPARATOR = Comparator
            .comparing(ResourceEntry::getConfig)
            .thenComparing(ResourceEntry::getTypeName)
            .thenComparing(ResourceEntry::getKeyName);

    /** 资源条目列表 */
    private final List<ResourceEntry> list = new ArrayList<>();
    /** 安全接口实例 */
    private final IJadxSecurity security;
    /**
     * 同一配置和类型下的名称必须唯一
     * 用于检测和处理资源名称冲突
     */
    private final Map<ResourceEntry, ResourceEntry> uniqNameEntries = new TreeMap<>(RES_ENTRY_NAME_COMPARATOR);
    /**
     * 重命名映射表，确保同一资源ID在不同配置下保持相同的名称
     */
    private final Map<Integer, String> renames = new HashMap<>();
    /** 应用包名 */
    private String appPackage;

    /**
     * 构造资源存储实例
     *
     * @param security 安全接口，用于校验应用包名等
     */
    public ResourceStorage(IJadxSecurity security) {
        this.security = security;
    }

    /**
     * 添加一个资源条目，同时登记到唯一名称集合中
     *
     * @param resEntry 资源条目
     */
    public void add(ResourceEntry resEntry) {
        list.add(resEntry);
        uniqNameEntries.put(resEntry, resEntry);
    }

    /**
     * 用新的资源条目替换已存在的旧资源条目
     *
     * @param prevResEntry 待替换的旧资源条目
     * @param newResEntry  新的资源条目
     */
    public void replace(ResourceEntry prevResEntry, ResourceEntry newResEntry) {
        int idx = list.indexOf(prevResEntry);
        if (idx != -1) {
            list.set(idx, newResEntry);
        }
        // 不从唯一名称集合中移除，使旧名称保持占用状态
    }

    /**
     * 为指定资源条目登记重命名 (使用其 ID 与键名)
     *
     * @param entry 资源条目
     */
    public void addRename(ResourceEntry entry) {
        addRename(entry.getId(), entry.getKeyName());
    }

    /**
     * 登记资源 ID 到键名的重命名映射
     *
     * @param id      资源 ID
     * @param keyName 键名
     */
    public void addRename(int id, String keyName) {
        renames.put(id, keyName);
    }

    /**
     * 获取指定资源 ID 对应的重命名后的键名
     *
     * @param id 资源 ID
     * @return 重命名后的键名，若不存在则返回 {@code null}
     */
    public String getRename(int id) {
        return renames.get(id);
    }

    /**
     * 在唯一名称集合中查找与给定资源条目同名 (配置 + 类型 + 键名)的条目
     *
     * @param resourceEntry 待查询的资源条目
     * @return 同名的已存在资源条目，若不存在则返回 {@code null}
     */
    public ResourceEntry searchEntryWithSameName(ResourceEntry resourceEntry) {
        return uniqNameEntries.get(resourceEntry);
    }

    /**
     * 结束资源收集：按资源 ID 升序排序，并清理临时用的唯一名称集合与重命名映射
     */
    public void finish() {
        list.sort(Comparator.comparingInt(ResourceEntry::getId));
        uniqNameEntries.clear();
        renames.clear();
    }

    /**
     * 返回已存储的资源条目数量
     *
     * @return 资源条目数量
     */
    public int size() {
        return list.size();
    }

    /**
     * 返回所有资源条目的可迭代视图
     *
     * @return 资源条目集合
     */
    public Iterable<ResourceEntry> getResources() {
        return list;
    }

    /**
     * 获取应用包名
     *
     * @return 应用包名
     */
    public String getAppPackage() {
        return appPackage;
    }

    /**
     * 设置应用包名 (经安全接口校验后存储)
     *
     * @param appPackage 应用包名
     */
    public void setAppPackage(String appPackage) {
        this.appPackage = security.verifyAppPackage(appPackage);
    }

    /**
     * 构建资源 ID 到「类型名/键名」的名称映射表
     *
     * @return 资源 ID 到资源名称的映射
     */
    public Map<Integer, String> getResourcesNames() {
        Map<Integer, String> map = new HashMap<>();
        for (ResourceEntry entry : list) {
            map.put(entry.getId(), entry.getTypeName() + '/' + entry.getKeyName());
        }
        return map;
    }
}
