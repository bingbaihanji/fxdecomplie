package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.annotations.IAnnotation;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttrType;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.JadxAttrType;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.types.AnnotationsAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import com.bingbaihanji.fxdecomplie.util.collection.ListUtils;

import java.util.*;
import java.util.function.Consumer;

/**
 * 属性存储容器，用于存放不同类型的属性：<br>
 * 1. 标志（Flags）—— 布尔型属性（已设置或未设置）<br>
 * 2. 属性（Attributes）—— 与属性类型（{@link IJadxAttrType}）关联的类实例（{@link IJadxAttribute}）<br>
 */
public class AttributeStorage {

    private static final Map<IJadxAttrType<?>, IJadxAttribute> EMPTY_ATTRIBUTES = Collections.emptyMap();

    static {
        int flagsCount = AFlag.values().length;
        if (flagsCount >= 64) {
            throw new JadxRuntimeException("Try to reduce flags count to 64 for use one long in EnumSet, now " + flagsCount);
        }
    }

    private final Set<AFlag> flags;
    private Map<IJadxAttrType<?>, IJadxAttribute> attributes;
    public AttributeStorage() {
        flags = EnumSet.noneOf(AFlag.class);
        attributes = EMPTY_ATTRIBUTES;
    }

    /**
     * 从属性列表创建一个属性存储容器。
     *
     * @param list 属性列表
     * @return 包含给定属性的存储容器
     */
    public static AttributeStorage fromList(List<IJadxAttribute> list) {
        AttributeStorage storage = new AttributeStorage();
        storage.add(list);
        return storage;
    }

    /** 添加一个标志 */
    public void add(AFlag flag) {
        flags.add(flag);
    }

    /** 添加一个属性 */
    public void add(IJadxAttribute attr) {
        writeAttributes(map -> map.put(attr.getAttrType(), attr));
    }

    /** 批量添加属性 */
    public void add(List<IJadxAttribute> list) {
        writeAttributes(map -> list.forEach(attr -> map.put(attr.getAttrType(), attr)));
    }

    /** 向指定类型的属性列表中追加单个元素，若列表不存在则新建 */
    public <T> void add(IJadxAttrType<AttrList<T>> type, T obj) {
        AttrList<T> list = get(type);
        if (list != null) {
            list.getList().add(obj);
        } else {
            add(new AttrList<>(type, ListUtils.mutableListOf(obj)));
        }
    }

    /** 向指定类型的属性列表中追加多个元素，若列表不存在则新建 */
    public <T> void addAttrList(IJadxAttrType<AttrList<T>> type, List<T> attrList) {
        AttrList<T> list = get(type);
        if (list != null) {
            list.getList().addAll(attrList);
        } else {
            add(new AttrList<>(type, attrList));
        }
    }

    /** 合并另一个存储容器中的所有标志和属性 */
    public void addAll(AttributeStorage otherList) {
        flags.addAll(otherList.flags);
        if (!otherList.attributes.isEmpty()) {
            writeAttributes(m -> m.putAll(otherList.attributes));
        }
    }

    /** 判断是否包含指定标志 */
    public boolean contains(AFlag flag) {
        return flags.contains(flag);
    }

    /** 判断是否包含指定类型的属性 */
    public <T extends IJadxAttribute> boolean contains(IJadxAttrType<T> type) {
        return attributes.containsKey(type);
    }

    /** 获取指定类型的属性，不存在时返回 null */
    @SuppressWarnings("unchecked")
    public <T extends IJadxAttribute> T get(IJadxAttrType<T> type) {
        return (T) attributes.get(type);
    }

    /** 根据类名获取注解，不存在时返回 null */
    public IAnnotation getAnnotation(String cls) {
        AnnotationsAttr aList = get(JadxAttrType.ANNOTATION_LIST);
        return aList == null ? null : aList.get(cls);
    }

    /** 获取指定类型属性列表中的全部元素，返回不可修改的列表 */
    public <T> List<T> getAll(IJadxAttrType<AttrList<T>> type) {
        AttrList<T> attrList = get(type);
        if (attrList == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(attrList.getList());
    }

    /** 移除指定标志 */
    public void remove(AFlag flag) {
        flags.remove(flag);
    }

    /** 清除所有标志 */
    public void clearFlags() {
        flags.clear();
    }

    /** 移除指定类型的属性 */
    public <T extends IJadxAttribute> void remove(IJadxAttrType<T> type) {
        if (!attributes.isEmpty()) {
            writeAttributes(map -> map.remove(type));
        }
    }

    /** 移除指定的属性实例（仅当存储中的实例与其为同一对象时才移除） */
    public void remove(IJadxAttribute attr) {
        if (!attributes.isEmpty()) {
            writeAttributes(map -> {
                IJadxAttrType<? extends IJadxAttribute> type = attr.getAttrType();
                IJadxAttribute a = map.get(type);
                if (a == attr) {
                    map.remove(type);
                }
            });
        }
    }

    private void writeAttributes(Consumer<Map<IJadxAttrType<?>, IJadxAttribute>> mapConsumer) {
        synchronized (this) {
            if (attributes == EMPTY_ATTRIBUTES) {
                attributes = new IdentityHashMap<>(2); // 大多数情况下只会添加 1 或 2 个属性
            }
            mapConsumer.accept(attributes);
            if (attributes.isEmpty()) {
                attributes = EMPTY_ATTRIBUTES;
            }
        }
    }

    /** 卸载不需要保持加载的属性，用于释放内存 */
    public void unloadAttributes() {
        if (attributes.isEmpty()) {
            return;
        }
        writeAttributes(map -> map.entrySet().removeIf(entry -> !entry.getValue().keepLoaded()));
    }

    /** 获取所有标志与属性的字符串表示列表 */
    public List<String> getAttributeStrings() {
        int size = flags.size() + attributes.size();
        if (size == 0) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>(size);
        for (AFlag a : flags) {
            list.add(a.toString());
        }
        for (IJadxAttribute a : attributes.values()) {
            list.add(a.toAttrString());
        }
        return list;
    }

    /** 判断存储容器是否为空（无标志且无属性） */
    public boolean isEmpty() {
        return flags.isEmpty() && attributes.isEmpty();
    }

    @Override
    public String toString() {
        List<String> list = getAttributeStrings();
        if (list.isEmpty()) {
            return "";
        }
        list.sort(String::compareTo);
        return "A[" + Utils.listToString(list) + ']';
    }
}
