package com.bingbaihanji.fxdecomplie.core.jadx.core.clsp;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.AccessFlags;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import org.intellij.lang.annotations.MagicConstant;

import java.util.*;

/**
 * 类路径图中的类节点。
 * <p>
 * 表示在类路径图上解析到的一个类，包含类型信息、访问标志、父类/接口、
 * 方法列表、类型参数等元数据，用于支持类型推断和方法重载解析。
 */
public class ClspClass {

    /** 类在类路径图中的类型表示 */
    private final ArgType clsType;
    /** 类在类路径图中的唯一标识 ID */
    private final int id;
    /** 类的访问标志（public、interface、abstract 等） */
    private final int accFlags;
    /** 类来源（标识该类来自哪个类路径源） */
    private final ClspClassSource source;
    /** 父类和实现的接口类型数组 */
    private ArgType[] parents;
    /** 方法映射表，键为方法的短 ID，值为对应的 ClspMethod */
    private Map<String, ClspMethod> methodsMap = Collections.emptyMap();
    /** 泛型类型参数列表 */
    private List<ArgType> typeParameters = Collections.emptyList();

    /**
     * 构造一个类路径类节点。
     *
     * @param clsType  类的类型
     * @param id       唯一标识 ID
     * @param accFlags 访问标志
     * @param source   类来源
     */
    public ClspClass(ArgType clsType, int id, int accFlags, ClspClassSource source) {
        this.clsType = clsType;
        this.id = id;
        this.accFlags = accFlags;
        this.source = source;
    }

    /** 获取类的全限定名 */
    public String getName() {
        return clsType.getObject();
    }

    /** 获取类的类型 */
    public ArgType getClsType() {
        return clsType;
    }

    /** 获取类在类路径图中的唯一标识 ID */
    public int getId() {
        return id;
    }

    /** 获取类的访问标志 */
    public int getAccFlags() {
        return accFlags;
    }

    /** 判断该类是否为接口 */
    public boolean isInterface() {
        return AccessFlags.hasFlag(accFlags, AccessFlags.INTERFACE);
    }

    /** 检查类是否具有指定的访问标志 */
    public boolean hasAccFlag(@MagicConstant(flagsFromClass = AccessFlags.class) int flags) {
        return AccessFlags.hasFlag(accFlags, flags);
    }

    /** 获取父类和实现的接口类型数组 */
    public ArgType[] getParents() {
        return parents;
    }

    /** 设置父类和实现的接口类型数组 */
    public void setParents(ArgType[] parents) {
        this.parents = parents;
    }

    /** 获取方法映射表（键为方法短 ID，值为 ClspMethod） */
    public Map<String, ClspMethod> getMethodsMap() {
        return methodsMap;
    }

    /** 设置方法映射表 */
    public void setMethodsMap(Map<String, ClspMethod> methodsMap) {
        this.methodsMap = Objects.requireNonNull(methodsMap);
    }

    /** 获取排序后的方法列表 */
    public List<ClspMethod> getSortedMethodsList() {
        List<ClspMethod> list = new ArrayList<>(methodsMap.size());
        list.addAll(methodsMap.values());
        Collections.sort(list);
        return list;
    }

    /** 根据方法列表构建方法映射表并设置 */
    public void setMethods(List<ClspMethod> methods) {
        Map<String, ClspMethod> map = new HashMap<>(methods.size());
        for (ClspMethod mth : methods) {
            map.put(mth.methodInfo().getShortId(), mth);
        }
        setMethodsMap(map);
    }

    /** 获取泛型类型参数列表 */
    public List<ArgType> getTypeParameters() {
        return typeParameters;
    }

    /** 设置泛型类型参数列表 */
    public void setTypeParameters(List<ArgType> typeParameters) {
        this.typeParameters = typeParameters;
    }

    /** 获取类的来源（标识该类来自哪个类路径源） */
    public ClspClassSource getSource() {
        return this.source;
    }

    /** 基于类型计算哈希值 */
    @Override
    public int hashCode() {
        return clsType.hashCode();
    }

    /** 基于类型判断两个类节点是否相等 */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ClspClass nClass = (ClspClass) o;
        return clsType.equals(nClass.clsType);
    }

    @Override
    public String toString() {
        return clsType.toString();
    }
}
