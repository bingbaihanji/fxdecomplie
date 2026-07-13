package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info;

import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Objects;

/**
 * 类信息类，用于表示 DEX 文件中类的元数据信息
 * 包含类的类型 名称 包名 父类信息以及别名等信息
 * 支持内部类的解析和别名管理功能
 */
public final class ClassInfo implements Comparable<ClassInfo> {
    /** 类在 DEX 中的类型表示 */
    private final ArgType type;
    /** 类的短名称 (不含包名) */
    private String name;
    /** 类的包名，对于内部类此字段为 null */
    @Nullable("for inner classes")
    private String pkg;
    /** 类的完整名称 (包名.短名称) */
    private String fullName;
    /** 父类信息，仅在内部类时有值 */
    @Nullable
    private ClassInfo parentClass;
    /** 类的别名信息 (包名和短名称别名) */
    @Nullable
    private ClassAliasInfo alias;

    /**
     * 私有构造方法，通过 {@link #fromType(RootNode, ArgType)} 等静态工厂方法创建实例
     *
     * @param root       根节点
     * @param type       类类型
     * @param canBeInner 是否可能是内部类
     */
    private ClassInfo(RootNode root, ArgType type, boolean canBeInner) {
        this.type = type;
        splitAndApplyNames(root, type, canBeInner);
    }

    /**
     * 从类型参数创建或获取 ClassInfo 实例
     * 首先检查缓存中是否存在，如果不存在则创建新的实例并存入缓存
     *
     * @param root 根节点
     * @param type 类类型
     * @return ClassInfo 实例
     */
    public static ClassInfo fromType(RootNode root, ArgType type) {
        ArgType clsType = checkClassType(type);
        ClassInfo cls = root.getInfoStorage().getCls(clsType);
        if (cls != null) {
            return cls;
        }
        boolean canBeInner = root.getArgs().isMoveInnerClasses();
        ClassInfo newClsInfo = new ClassInfo(root, clsType, canBeInner);
        return root.getInfoStorage().putCls(newClsInfo);
    }

    /**
     * 从类名创建或获取 ClassInfo 实例
     *
     * @param root   根节点
     * @param clsName 类名
     * @return ClassInfo 实例
     */
    public static ClassInfo fromName(RootNode root, String clsName) {
        return fromType(root, ArgType.object(clsName));
    }

    /**
     * 从类名创建 ClassInfo 实例，不使用缓存
     *
     * @param root        根节点
     * @param fullClsName 完整类名
     * @param canBeInner  是否可能是内部类
     * @return ClassInfo 实例
     */
    public static ClassInfo fromNameWithoutCache(RootNode root, String fullClsName, boolean canBeInner) {
        return new ClassInfo(root, ArgType.object(fullClsName), canBeInner);
    }

    /**
     * 检查并验证类类型
     * 确保类型非空 非数组 非泛型类型，并返回标准化的类类型
     *
     * @param type 待检查的类型
     * @return 标准化后的类类型
     * @throws JadxRuntimeException 如果类型为空或不是有效的类类型
     */
    private static ArgType checkClassType(ArgType type) {
        if (type == null) {
            throw new JadxRuntimeException("Null class type");
        }
        if (type.isArray()) {
            // TODO: 检查数组类中声明方法的情况 (如 int[] 中的 clone 方法)
            return ArgType.OBJECT;
        }
        if (!type.isObject() || type.isGenericType()) {
            throw new JadxRuntimeException("Not class type: " + type);
        }
        if (type.isGeneric()) {
            return ArgType.object(type.getObject());
        }
        return type;
    }

    /**
     * 构建完整的类名
     * 根据包名 短名称 父类信息以及是否使用别名和原始格式来生成完整类名
     * 内部类使用 '.' 或 '$' 作为分隔符
     *
     * @param pkg         包名
     * @param shortName   短名称
     * @param parentClass 父类信息，如果是内部类则不为 null
     * @param alias       是否使用别名
     * @param raw         是否使用原始格式 (使用 '$' 而非 '.' 作为内部类分隔符)
     * @return 完整的类名字符串
     */
    private static String makeFullClsName(String pkg, String shortName, ClassInfo parentClass, boolean alias, boolean raw) {
        if (parentClass != null) {
            String parentFullName;
            char innerSep = raw ? '$' : '.';
            if (alias) {
                parentFullName = raw ? parentClass.makeAliasRawFullName() : parentClass.getAliasFullName();
            } else {
                parentFullName = raw ? parentClass.makeRawFullName() : parentClass.getFullName();
            }
            return parentFullName + innerSep + shortName;
        }
        return pkg.isEmpty() ? shortName : pkg + '.' + shortName;
    }

    /**
     * 修改类的短名称别名
     * 如果新名称与原名称相同或为空，则只处理包名别名 (如果有)
     *
     * @param aliasName 新的别名短名称
     */
    public void changeShortName(String aliasName) {
        ClassAliasInfo newAlias;
        String aliasPkg = getAliasPkg();
        if (Objects.equals(name, aliasName) || CodegenEscapeUtils.isEmpty(aliasName)) {
            if (Objects.equals(getPackage(), aliasPkg)) {
                newAlias = null;
            } else {
                newAlias = new ClassAliasInfo(aliasPkg, name);
            }
        } else {
            newAlias = new ClassAliasInfo(aliasPkg, aliasName);
        }
        if (newAlias != null) {
            fillAliasFullName(newAlias);
        }
        this.alias = newAlias;
    }

    /**
     * 修改类的包名别名
     * 不能对内部类修改包名
     *
     * @param aliasPkg 新的别名包名
     * @throws JadxRuntimeException 如果尝试修改内部类的包名
     */
    public void changePkg(String aliasPkg) {
        if (isInner()) {
            throw new JadxRuntimeException("Can't change package for inner class: " + this);
        }
        if (!Objects.equals(getAliasPkg(), aliasPkg)) {
            ClassAliasInfo newAlias = new ClassAliasInfo(aliasPkg, getAliasShortName());
            fillAliasFullName(newAlias);
            this.alias = newAlias;
        }
    }

    /**
     * 同时修改类的包名和短名称别名
     * 不能对内部类修改包名
     *
     * @param aliasPkg       新的别名包名
     * @param aliasShortName 新的别名短名称
     * @throws JadxRuntimeException 如果尝试修改内部类的包名
     */
    public void changePkgAndName(String aliasPkg, String aliasShortName) {
        if (isInner()) {
            throw new JadxRuntimeException("Can't change package for inner class");
        }
        ClassAliasInfo newAlias = new ClassAliasInfo(aliasPkg, aliasShortName);
        fillAliasFullName(newAlias);
        this.alias = newAlias;
    }

    /**
     * 为别名信息填充完整名称
     * 仅对非内部类 (无父类)设置别名的完整类名
     *
     * @param alias 待填充完整名称的别名信息
     */
    private void fillAliasFullName(ClassAliasInfo alias) {
        if (parentClass == null) {
            alias.setFullName(makeFullClsName(alias.getPkg(), alias.getShortName(), null, true, false));
        }
    }

    /**
     * 获取类的别名包名
     * 对于内部类，返回父类的别名包名
     *
     * @return 别名包名，如果没有别名则返回原包名
     */
    public String getAliasPkg() {
        if (isInner()) {
            return parentClass.getAliasPkg();
        }
        return alias == null ? getPackage() : alias.getPkg();
    }

    /**
     * 获取类的别名短名称
     *
     * @return 别名短名称，如果没有别名则返回原短名称
     */
    public String getAliasShortName() {
        return alias == null ? getShortName() : alias.getShortName();
    }

    /**
     * 获取类的别名完整名称
     * 如果存在别名则返回别名全名，否则检查父类是否有别名，都没有则返回原全名
     *
     * @return 别名完整名称
     */
    public String getAliasFullName() {
        if (alias != null) {
            String aliasFullName = alias.getFullName();
            if (aliasFullName == null) {
                return makeAliasFullName();
            }
            return aliasFullName;
        }
        if (parentClass != null && parentClass.hasAlias()) {
            return makeAliasFullName();
        }
        return getFullName();
    }

    /**
     * 判断类是否有别名
     * 检查当前类或其父类是否存在别名
     *
     * @return 如果存在别名返回 true，否则返回 false
     */
    public boolean hasAlias() {
        if (alias != null && !alias.getShortName().equals(getShortName())) {
            return true;
        }
        return parentClass != null && parentClass.hasAlias();
    }

    /**
     * 判断类是否有包名别名
     *
     * @return 如果包名存在别名返回 true，否则返回 false
     */
    public boolean hasAliasPkg() {
        return !getPackage().equals(getAliasPkg());
    }

    /**
     * 移除类的别名信息
     */
    public void removeAlias() {
        this.alias = null;
    }

    /**
     * 解析并应用类名信息
     * 将完整类名拆分为包名和短名称，并根据配置判断是否为内部类
     * 内部类通过 '$' 符号识别，并递归解析父类信息
     *
     * @param root       根节点
     * @param type       类类型
     * @param canBeInner 是否可能是内部类
     */
    private void splitAndApplyNames(RootNode root, ArgType type, boolean canBeInner) {
        String fullObjectName = type.getObject();
        String clsPkg;
        String clsName;
        int dot = fullObjectName.lastIndexOf('.');
        if (dot == -1) {
            clsPkg = "";
            clsName = fullObjectName;
        } else {
            clsPkg = fullObjectName.substring(0, dot);
            clsName = fullObjectName.substring(dot + 1);
        }

        boolean innerCls = false;
        if (canBeInner) {
            int sep = clsName.lastIndexOf('$');
            if (sep > 0 && sep != clsName.length() - 1) {
                String parClsName = clsPkg + '.' + clsName.substring(0, sep);
                if (clsPkg.isEmpty()) {
                    parClsName = clsName.substring(0, sep);
                }
                pkg = null;
                parentClass = fromName(root, parClsName);
                clsName = clsName.substring(sep + 1);
                innerCls = true;
            }
        }
        if (!innerCls) {
            pkg = clsPkg;
            parentClass = null;
        }
        this.name = clsName;
        this.fullName = makeFullName();
    }

    /**
     * 构建完整的类名 (非别名，非原始格式)
     */
    private String makeFullName() {
        return makeFullClsName(pkg, name, parentClass, false, false);
    }

    /**
     * 构建原始格式的完整类名 (非别名，使用 '$' 作为内部类分隔符)
     */
    public String makeRawFullName() {
        return makeFullClsName(pkg, name, parentClass, false, true);
    }

    /**
     * 构建别名的完整类名 (使用别名，非原始格式)
     */
    public String makeAliasFullName() {
        return makeFullClsName(getAliasPkg(), getAliasShortName(), parentClass, true, false);
    }

    /**
     * 构建别名的原始格式完整类名 (使用别名，使用 '$' 作为内部类分隔符)
     *
     * @return 别名的原始格式完整类名
     */
    public String makeAliasRawFullName() {
        return makeFullClsName(getAliasPkg(), getAliasShortName(), parentClass, true, true);
    }

    /**
     * 获取类的别名完整路径
     * 将包名转换为文件路径分隔符，短名称中的 '.' 替换为 '_'，用于生成文件路径
     *
     * @return 别名对应的完整文件路径
     */
    public String getAliasFullPath() {
        String fileName = getAliasNameWithoutPackage().replace('.', '_');
        String aliasPkg = getAliasPkg();
        if (aliasPkg.isEmpty()) {
            return fileName;
        }
        return aliasPkg.replace('.', File.separatorChar) + File.separatorChar + fileName;
    }

    /**
     * 获取类的完整名称
     *
     * @return 完整类名 (包名.短名称)
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * 获取类的短名称
     *
     * @return 类的短名称 (不含包名)
     */
    public String getShortName() {
        return name;
    }

    /**
     * 获取类的包名
     * 对于内部类，返回其父类的包名
     *
     * @return 包名
     * @throws JadxRuntimeException 如果非内部类的包名为 null
     */
    @NotNull
    public String getPackage() {
        if (parentClass != null) {
            return parentClass.getPackage();
        }
        if (pkg == null) {
            throw new JadxRuntimeException("Package is null for not inner class");
        }
        return pkg;
    }

    /**
     * 判断类是否位于默认包 (无包名)
     *
     * @return 如果位于默认包返回 true，否则返回 false
     */
    public boolean isDefaultPackage() {
        return getPackage().isEmpty();
    }

    /**
     * 获取类的原始名称
     *
     * @return 类型的原始对象名称
     */
    public String getRawName() {
        return type.getObject();
    }

    /**
     * 获取不含包名的别名 (内部类以 '.' 拼接各级外部类别名)
     *
     * @return 不含包名的别名名称
     */
    public String getAliasNameWithoutPackage() {
        if (parentClass == null) {
            return getAliasShortName();
        }
        return parentClass.getAliasNameWithoutPackage() + '.' + getAliasShortName();
    }

    /**
     * 返回当前 ClassInfo 所表示类的外部类
     *
     * @return 外部 (父)类信息，如果不是内部类则返回 null
     */
    public @Nullable ClassInfo getParentClass() {
        return parentClass;
    }

    /**
     * 返回当前 ClassInfo 所表示类的最顶层外部类
     *
     * @return 最顶层的外部类信息，如果不是内部类则返回 null
     */
    public @Nullable ClassInfo getTopParentClass() {
        if (parentClass != null) {
            ClassInfo topCls = parentClass.getTopParentClass();
            return topCls != null ? topCls : parentClass;
        }
        return null;
    }

    /**
     * 判断当前类是否为内部类
     *
     * @return 如果是内部类返回 true，否则返回 false
     */
    public boolean isInner() {
        return parentClass != null;
    }

    /**
     * 将当前类标记为非内部类，重新解析名称并清除父类信息
     *
     * @param root 根节点
     */
    public void notInner(RootNode root) {
        splitAndApplyNames(root, type, false);
        this.parentClass = null;
    }

    /**
     * 将当前类转换为指定父类的内部类，重新解析名称并设置父类信息
     *
     * @param parent 父类节点
     */
    public void convertToInner(ClassNode parent) {
        splitAndApplyNames(parent.root(), type, true);
        this.parentClass = parent.getClassInfo();
    }

    /**
     * 更新类名信息，按当前内部类状态重新解析名称
     *
     * @param root 根节点
     */
    public void updateNames(RootNode root) {
        splitAndApplyNames(root, type, isInner());
    }

    /**
     * 获取类的类型
     *
     * @return 类的 ArgType 类型
     */
    public ArgType getType() {
        return type;
    }

    /**
     * 返回类的字符串表示 (完整类名)
     *
     * @return 完整类名
     */
    @Override
    public String toString() {
        return getFullName();
    }

    /**
     * 基于类型计算哈希码
     *
     * @return 哈希码
     */
    @Override
    public int hashCode() {
        return type.hashCode();
    }

    /**
     * 基于类型比较两个 ClassInfo 是否相等
     *
     * @param obj 待比较的对象
     * @return 如果类型相同返回 true，否则返回 false
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof ClassInfo) {
            return type.equals(((ClassInfo) obj).type);
        }
        return false;
    }

    /**
     * 基于原始类名比较两个 ClassInfo 的顺序
     *
     * @param other 待比较的另一个 ClassInfo
     * @return 比较结果
     */
    @Override
    public int compareTo(@NotNull ClassInfo other) {
        return getRawName().compareTo(other.getRawName());
    }
}
