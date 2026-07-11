package com.bingbaihanji.fxdecomplie.core.jadx.api.deobf.impl;

import com.bingbaihanji.fxdecomplie.core.jadx.api.deobf.IRenameCondition;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.*;

/**
 * 始终重命名条件的实现
 * <p>
 * {@link IRenameCondition} 接口的实现，对任意包、类、字段和方法均返回需要重命名，
 * 即无条件地对所有节点执行重命名采用单例模式，通过 {@link #INSTANCE} 获取实例
 */
public class AlwaysRename implements IRenameCondition {

    /** 全局共享的单例实例 */
    public static final IRenameCondition INSTANCE = new AlwaysRename();

    private AlwaysRename() {
    }

    @Override
    public void init(RootNode root) {
    }

    @Override
    public boolean shouldRename(PackageNode pkg) {
        return true;
    }

    @Override
    public boolean shouldRename(ClassNode cls) {
        return true;
    }

    @Override
    public boolean shouldRename(FieldNode fld) {
        return true;
    }

    @Override
    public boolean shouldRename(MethodNode mth) {
        return true;
    }
}
