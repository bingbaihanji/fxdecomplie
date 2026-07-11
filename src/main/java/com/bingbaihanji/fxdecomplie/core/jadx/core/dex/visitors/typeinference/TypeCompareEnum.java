package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference;

/**
 * 类型比较结果枚举。
 * <p>
 * 用于描述两个类型在类型推断过程中的比较关系。
 * 当类型推断展开泛型类型时，基本类型相同但泛型参数不同会产生对应的比较结果。
 */
public enum TypeCompareEnum {
    /** 类型完全相同 */
    EQUAL,
    /** 当前类型更窄（更具体） */
    NARROW,
    /** 相同的基本类型，但带泛型，因此更窄 */
    NARROW_BY_GENERIC, // 相同的基础类型，带泛型
    /** 当前类型更宽（更通用） */
    WIDER,
    /** 相同的基本类型，但不带泛型，因此更宽 */
    WIDER_BY_GENERIC, // 相同的基础类型，不带泛型
    /** 类型冲突（不兼容） */
    CONFLICT,
    /** 相同的基本类型，但泛型存在冲突 */
    CONFLICT_BY_GENERIC, // 相同的基础类型，泛型冲突
    /** 类型关系未知 */
    UNKNOWN;

    /**
     * 反转比较结果。
     * <p>
     * 如果当前结果是 NARROW，则返回 WIDER；反之亦然。
     * EQUAL、CONFLICT 和 UNKNOWN 保持不变。
     *
     * @return 反转后的比较结果
     */
    public TypeCompareEnum invert() {
        switch (this) {
            case NARROW:
                return WIDER;

            case NARROW_BY_GENERIC:
                return WIDER_BY_GENERIC;

            case WIDER:
                return NARROW;

            case WIDER_BY_GENERIC:
                return NARROW_BY_GENERIC;

            case CONFLICT:
            case CONFLICT_BY_GENERIC:
            case EQUAL:
            case UNKNOWN:
            default:
                return this;
        }
    }

    /**
     * 判断两个类型是否完全相同。
     *
     * @return 如果类型相同返回 true
     */
    public boolean isEqual() {
        return this == EQUAL;
    }

    /**
     * 判断当前类型是否更宽（包括泛型相关的更宽）。
     *
     * @return 如果当前类型更宽返回 true
     */
    public boolean isWider() {
        return this == WIDER || this == WIDER_BY_GENERIC;
    }

    /**
     * 判断当前类型是否相等或更宽。
     *
     * @return 如果类型相等或更宽返回 true
     */
    public boolean isWiderOrEqual() {
        return isEqual() || isWider();
    }

    /**
     * 判断当前类型是否更窄（包括泛型相关的更窄）。
     *
     * @return 如果当前类型更窄返回 true
     */
    public boolean isNarrow() {
        return this == NARROW || this == NARROW_BY_GENERIC;
    }

    /**
     * 判断当前类型是否相等或更窄。
     *
     * @return 如果类型相等或更窄返回 true
     */
    public boolean isNarrowOrEqual() {
        return isEqual() || isNarrow();
    }

    /**
     * 判断两个类型是否冲突（不兼容）。
     *
     * @return 如果类型冲突返回 true
     */
    public boolean isConflict() {
        return this == CONFLICT || this == CONFLICT_BY_GENERIC;
    }
}
