package com.bingbaihanji.fxdecomplie.decompiler.jadx;

import com.bingbaihanji.fxdecomplie.core.jadx.api.deobf.IRenameCondition;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.FieldNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.PackageNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;

/**
 * 项目适配层的 jadx 去混淆条件。
 *
 * <p>fxdecomplie 当前持久化状态只支持类/字段/方法重命名,不支持包级 rename。
 * 因此禁用 jadx package alias,避免源码 package/import 与项目树路径产生两套状态。</p>
 */
final class JadxProjectRenameCondition implements IRenameCondition {
    private final IRenameCondition delegate;

    JadxProjectRenameCondition(IRenameCondition delegate) {
        this.delegate = delegate;
    }

    @Override
    public void init(RootNode root) {
        if (delegate != null) {
            delegate.init(root);
        }
    }

    @Override
    public boolean shouldRename(PackageNode pkg) {
        return false;
    }

    @Override
    public boolean shouldRename(ClassNode cls) {
        return delegate != null && delegate.shouldRename(cls);
    }

    @Override
    public boolean shouldRename(FieldNode fld) {
        return delegate != null && delegate.shouldRename(fld);
    }

    @Override
    public boolean shouldRename(MethodNode mth) {
        return delegate != null && delegate.shouldRename(mth);
    }
}
