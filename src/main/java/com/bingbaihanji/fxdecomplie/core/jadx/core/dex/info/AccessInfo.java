package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.AccessFlags;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import org.intellij.lang.annotations.MagicConstant;

/**
 * 访问修饰符信息封装类
 * <p>
 * 用于封装类、字段、方法的访问标志 (access flags)，
 * 提供标志位的判断、修改、可见性比较及字符串表示等功能
 * </p>
 */
public class AccessInfo {

    /** 可见性标志掩码，包含 public、protected、private 三个标志位 */
    public static final int VISIBILITY_FLAGS = AccessFlags.PUBLIC | AccessFlags.PROTECTED | AccessFlags.PRIVATE;
    private final int accFlags;
    private final AFType type;

    /**
     * 构造访问信息对象
     *
     * @param accessFlags 访问标志位值
     * @param type        所属类型 (类、字段、方法)
     */
    public AccessInfo(int accessFlags, AFType type) {
        this.accFlags = accessFlags;
        this.type = type;
    }

    /**
     * 将可见性标志位转换为排序值，用于可见性强弱比较
     * <p>
     * 排序规则：private=1, package-private=2, protected=3, public=4
     * </p>
     *
     * @param flag 可见性标志位
     * @return 对应的排序值
     * @throws JadxRuntimeException 如果传入非预期的可见性标志位
     */
    private static int orderedVisibility(int flag) {
        switch (flag) {
            case AccessFlags.PRIVATE:
                return 1;
            case 0: // 包私有 (package-private)
                return 2;
            case AccessFlags.PROTECTED:
                return 3;
            case AccessFlags.PUBLIC:
                return 4;
            default:
                throw new JadxRuntimeException("Unexpected visibility flag: " + flag);
        }
    }

    /**
     * 判断是否包含指定的访问标志位
     *
     * @param flag 要检查的标志位
     * @return 如果包含该标志位则返回 {@code true}
     */
    @MagicConstant(valuesFromClass = AccessFlags.class)
    public boolean containsFlag(int flag) {
        return (accFlags & flag) != 0;
    }

    /**
     * 判断是否同时包含所有指定的访问标志位
     *
     * @param flags 要检查的标志位数组
     * @return 如果包含所有标志位则返回 {@code true}
     */
    @MagicConstant(valuesFromClass = AccessFlags.class)
    public boolean containsFlags(int... flags) {
        for (int flag : flags) {
            if ((accFlags & flag) == 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 移除指定的访问标志位，返回新的 {@link AccessInfo} 实例
     * 如果当前不包含该标志位，则返回自身
     *
     * @param flag 要移除的标志位
     * @return 移除标志位后的新实例，或当前实例
     */
    public AccessInfo remove(int flag) {
        if (containsFlag(flag)) {
            return new AccessInfo(accFlags & ~flag, type);
        }
        return this;
    }

    /**
     * 添加指定的访问标志位，返回新的 {@link AccessInfo} 实例
     * 如果当前已包含该标志位，则返回自身
     *
     * @param flag 要添加的标志位
     * @return 添加标志位后的新实例，或当前实例
     */
    public AccessInfo add(int flag) {
        if (!containsFlag(flag)) {
            return new AccessInfo(accFlags | flag, type);
        }
        return this;
    }

    /**
     * 更改可见性修饰符，返回新的 {@link AccessInfo} 实例
     * 会清除原有的可见性标志位 (public/protected/private)，然后设置新的可见性
     * 如果新可见性与当前相同，则返回自身
     *
     * @param flag 新的可见性标志位
     * @return 更改可见性后的新实例，或当前实例
     */
    public AccessInfo changeVisibility(int flag) {
        int currentVisFlags = accFlags & VISIBILITY_FLAGS;
        if (currentVisFlags == flag) {
            return this;
        }
        int unsetAllVisFlags = accFlags & ~VISIBILITY_FLAGS;
        return new AccessInfo(unsetAllVisFlags | flag, type);
    }

    /**
     * 获取仅包含可见性标志位的 {@link AccessInfo} 实例
     *
     * @return 只保留可见性标志位的新实例
     */
    public AccessInfo getVisibility() {
        return new AccessInfo(accFlags & VISIBILITY_FLAGS, type);
    }

    /**
     * 判断当前可见性是否弱于另一个 {@link AccessInfo}
     * <p>
     * 可见性强弱顺序 (从弱到强)：private &lt; package-private &lt; protected &lt; public
     * </p>
     *
     * @param otherAccInfo 要比较的另一个访问信息
     * @return 如果当前可见性更弱则返回 {@code true}
     */
    public boolean isVisibilityWeakerThan(AccessInfo otherAccInfo) {
        int thisVis = accFlags & VISIBILITY_FLAGS;
        int otherVis = otherAccInfo.accFlags & VISIBILITY_FLAGS;
        if (thisVis == otherVis) {
            return false;
        }
        return orderedVisibility(thisVis) < orderedVisibility(otherVis);
    }

    /**
     * 判断是否为 public 访问级别
     *
     * @return 如果是 public 则返回 {@code true}
     */
    public boolean isPublic() {
        return (accFlags & AccessFlags.PUBLIC) != 0;
    }

    /**
     * 判断是否为 protected 访问级别
     *
     * @return 如果是 protected 则返回 {@code true}
     */
    public boolean isProtected() {
        return (accFlags & AccessFlags.PROTECTED) != 0;
    }

    /**
     * 判断是否为 private 访问级别
     *
     * @return 如果是 private 则返回 {@code true}
     */
    public boolean isPrivate() {
        return (accFlags & AccessFlags.PRIVATE) != 0;
    }

    /**
     * 判断是否为包私有 (package-private)访问级别
     * 即不包含 public、protected、private 任一可见性标志位
     *
     * @return 如果是包私有则返回 {@code true}
     */
    public boolean isPackagePrivate() {
        return (accFlags & VISIBILITY_FLAGS) == 0;
    }

    /**
     * 判断是否为 abstract (抽象)
     *
     * @return 如果是 abstract 则返回 {@code true}
     */
    public boolean isAbstract() {
        return (accFlags & AccessFlags.ABSTRACT) != 0;
    }

    /**
     * 判断是否为 interface (接口)
     *
     * @return 如果是 interface 则返回 {@code true}
     */
    public boolean isInterface() {
        return (accFlags & AccessFlags.INTERFACE) != 0;
    }

    /**
     * 判断是否为 annotation (注解)
     *
     * @return 如果是 annotation 则返回 {@code true}
     */
    public boolean isAnnotation() {
        return (accFlags & AccessFlags.ANNOTATION) != 0;
    }

    /**
     * 判断是否为 native (本地方法)
     *
     * @return 如果是 native 则返回 {@code true}
     */
    public boolean isNative() {
        return (accFlags & AccessFlags.NATIVE) != 0;
    }

    /**
     * 判断是否为 static (静态)
     *
     * @return 如果是 static 则返回 {@code true}
     */
    public boolean isStatic() {
        return (accFlags & AccessFlags.STATIC) != 0;
    }

    /**
     * 判断是否为 final (不可继承/不可修改)
     *
     * @return 如果是 final 则返回 {@code true}
     */
    public boolean isFinal() {
        return (accFlags & AccessFlags.FINAL) != 0;
    }

    /**
     * 判断是否为构造方法
     *
     * @return 如果是构造方法则返回 {@code true}
     */
    public boolean isConstructor() {
        return (accFlags & AccessFlags.CONSTRUCTOR) != 0;
    }

    /**
     * 判断是否为 enum (枚举)
     *
     * @return 如果是 enum 则返回 {@code true}
     */
    public boolean isEnum() {
        return (accFlags & AccessFlags.ENUM) != 0;
    }

    /**
     * 判断是否为 synthetic (编译器合成)
     * 编译器自动生成的成员 (如桥接方法、内部类访问方法等)会被标记为 synthetic
     *
     * @return 如果是 synthetic 则返回 {@code true}
     */
    public boolean isSynthetic() {
        return (accFlags & AccessFlags.SYNTHETIC) != 0;
    }

    /**
     * 判断是否为 bridge (桥接方法)
     * 编译器为泛型类型擦除生成的桥接方法会被标记为此标志
     *
     * @return 如果是桥接方法则返回 {@code true}
     */
    public boolean isBridge() {
        return (accFlags & AccessFlags.BRIDGE) != 0;
    }

    /**
     * 判断是否为可变参数方法
     *
     * @return 如果是可变参数方法则返回 {@code true}
     */
    public boolean isVarArgs() {
        return (accFlags & AccessFlags.VARARGS) != 0;
    }

    /**
     * 判断是否为 synchronized (同步方法)
     * 同时检查 {@code SYNCHRONIZED} 和 {@code DECLARED_SYNCHRONIZED} 标志位
     *
     * @return 如果是同步方法则返回 {@code true}
     */
    public boolean isSynchronized() {
        return (accFlags & (AccessFlags.SYNCHRONIZED | AccessFlags.DECLARED_SYNCHRONIZED)) != 0;
    }

    /**
     * 判断是否为 transient (瞬态字段，不参与序列化)
     *
     * @return 如果是 transient 则返回 {@code true}
     */
    public boolean isTransient() {
        return (accFlags & AccessFlags.TRANSIENT) != 0;
    }

    /**
     * 判断是否为 volatile (易变字段，保证多线程可见性)
     *
     * @return 如果是 volatile 则返回 {@code true}
     */
    public boolean isVolatile() {
        return (accFlags & AccessFlags.VOLATILE) != 0;
    }

    /**
     * 判断是否为 module-info 模块描述符
     *
     * @return 如果是模块描述符则返回 {@code true}
     */
    public boolean isModuleInfo() {
        return (accFlags & AccessFlags.MODULE) != 0;
    }

    /**
     * 判断是否为 data (Kotlin data class 标记)
     *
     * @return 如果是 data class 则返回 {@code true}
     */
    public boolean isData() {
        return (accFlags & AccessFlags.DATA) != 0;
    }

    /**
     * 获取访问标志所属类型
     *
     * @return 所属类型 (类、字段或方法)
     */
    public AFType getType() {
        return type;
    }

    /**
     * 根据访问标志生成 Java 源码风格的修饰符字符串 (以空格分隔并带尾随空格)
     * <p>
     * 会按照类型 (方法、字段、类)追加相应的修饰符，例如 synchronized、volatile、strict 等
     * 当 {@code showHidden} 为 {@code true} 时，还会以注释形式追加隐藏标志，
     * 如 {@code /* bridge *}{@code /}、{@code /* synthetic *}{@code /} 等
     * </p>
     *
     * @param showHidden 是否输出隐藏的合成、桥接、数据类等标注
     * @return 拼接后的修饰符字符串
     */
    public String makeString(boolean showHidden) {
        StringBuilder code = new StringBuilder();
        if (isPublic()) {
            code.append("public ");
        }
        if (isPrivate()) {
            code.append("private ");
        }
        if (isProtected()) {
            code.append("protected ");
        }
        if (isStatic()) {
            code.append("static ");
        }
        if (isFinal()) {
            code.append("final ");
        }
        if (isAbstract()) {
            code.append("abstract ");
        }
        if (isNative()) {
            code.append("native ");
        }
        switch (type) {
            case METHOD:
                if (isSynchronized()) {
                    code.append("synchronized ");
                }
                if (showHidden) {
                    if (isBridge()) {
                        code.append("/* bridge */ ");
                    }
                    if (false && isVarArgs()) {
                        code.append("/* varargs */ ");
                    }
                }
                break;

            case FIELD:
                if (isVolatile()) {
                    code.append("volatile ");
                }
                if (isTransient()) {
                    code.append("transient ");
                }
                break;

            case CLASS:
                if ((accFlags & AccessFlags.STRICT) != 0) {
                    code.append("strict ");
                }
                if (showHidden) {
                    if (isData()) {
                        code.append("/* data */ ");
                    }
                    if (isModuleInfo()) {
                        code.append("/* module-info */ ");
                    }
                    if (false) {
                        if ((accFlags & AccessFlags.SUPER) != 0) {
                            code.append("/* super */ ");
                        }
                        if ((accFlags & AccessFlags.ENUM) != 0) {
                            code.append("/* enum */ ");
                        }
                    }
                }
                break;
        }
        if (isSynthetic() && showHidden) {
            code.append("/* synthetic */ ");
        }
        return code.toString();
    }

    /**
     * 获取可见性级别的名称
     *
     * @return 可见性名称，取值为 {@code "package-private"}、{@code "public"}、
     *         {@code "private"} 或 {@code "protected"}
     * @throws JadxRuntimeException 如果可见性标志位无法识别
     */
    public String visibilityName() {
        if (isPackagePrivate()) {
            return "package-private";
        }
        if (isPublic()) {
            return "public";
        }
        if (isPrivate()) {
            return "private";
        }
        if (isProtected()) {
            return "protected";
        }
        throw new JadxRuntimeException("Unknown visibility flags: " + getVisibility());
    }

    /**
     * 获取原始的访问标志整型值
     *
     * @return 原始访问标志位
     */
    public int rawValue() {
        return accFlags;
    }

    /**
     * 返回访问信息的调试字符串表示，包含所属类型、十六进制标志值及修饰符字符串
     *
     * @return 调试用字符串
     */
    @Override
    public String toString() {
        return "AccessInfo: " + type + " 0x" + Integer.toHexString(accFlags) + " (" + makeString(true) + ')';
    }

    /**
     * 访问标志所属类型枚举
     */
    public enum AFType {
        /** 类 */
        CLASS,
        /** 字段 */
        FIELD,
        /** 方法 */
        METHOD
    }
}
