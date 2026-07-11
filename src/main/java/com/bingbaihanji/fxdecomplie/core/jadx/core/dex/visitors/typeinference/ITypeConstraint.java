package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.SSAVar;

import java.util.List;

/**
 * 类型约束接口
 * <p>
 * 表示类型推断过程中的一条类型约束，用于校验相关变量的当前类型是否满足该约束
 */
public interface ITypeConstraint {

    /**
     * 获取与该约束相关的所有 SSA 变量
     *
     * @return 相关变量列表
     */
    List<SSAVar> getRelatedVars();

    /**
     * 在给定的类型搜索状态下校验该约束是否成立
     *
     * @param state 当前类型搜索状态
     * @return 如果约束满足返回 true，否则返回 false
     */
    boolean check(TypeSearchState state);
}
