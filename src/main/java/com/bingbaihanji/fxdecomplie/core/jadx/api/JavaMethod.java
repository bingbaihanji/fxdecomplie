package com.bingbaihanji.fxdecomplie.core.jadx.api;

import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeAnnotation;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeNodeRef;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.MethodOverrideAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.AccessInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.MethodInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对方法节点（{@link MethodNode}）的对外封装，向 API 使用者提供方法的名称、
 * 参数、返回类型、引用关系等信息。
 */
public final class JavaMethod implements JavaNode {
    /** 被封装的底层方法节点 */
    private final MethodNode mth;
    /** 声明该方法的所属类 */
    private final JavaClass parent;

    JavaMethod(JavaClass cls, MethodNode m) {
        this.parent = cls;
        this.mth = m;
    }

    /** 获取方法名（使用别名，可能经过反混淆重命名） */
    @Override
    public String getName() {
        return mth.getAlias();
    }

    /** 获取方法的完整名称 */
    @Override
    public String getFullName() {
        return mth.getMethodInfo().getFullName();
    }

    /** 获取声明该方法的类 */
    @Override
    public JavaClass getDeclaringClass() {
        return parent;
    }

    /** 获取最顶层的外部类（内部类会向上追溯到顶层类） */
    @Override
    public JavaClass getTopParentClass() {
        return parent.getTopParentClass();
    }

    /** 获取方法的访问标志（public/private/static 等） */
    public AccessInfo getAccessFlags() {
        return mth.getAccessFlags();
    }

    /** 获取方法的参数类型列表，并尝试解析类别名 */
    public List<ArgType> getArguments() {
        List<ArgType> infoArgTypes = mth.getMethodInfo().getArgumentsTypes();
        if (infoArgTypes.isEmpty()) {
            return Collections.emptyList();
        }
        List<ArgType> arguments = mth.getArgTypes();
        return Utils.collectionMap(arguments,
                type -> ArgType.tryToResolveClassAlias(mth.root(), type));
    }

    /** 获取方法的返回类型，并尝试解析类别名 */
    public ArgType getReturnType() {
        ArgType retType = mth.getReturnType();
        return ArgType.tryToResolveClassAlias(mth.root(), retType);
    }

    /** 获取引用了该方法的节点列表（即哪些地方使用了本方法） */
    @Override
    public List<JavaNode> getUseIn() {
        return getDeclaringClass().getRootDecompiler().convertNodes(mth.getUseIn());
    }

    /** 获取本方法内部所使用到的节点列表 */
    public List<JavaNode> getUsed() {
        return getDeclaringClass().getRootDecompiler().convertNodes(mth.getUsed());
    }

    /** 获取本方法使用到的、但未能解析的方法信息列表 */
    public List<MethodInfo> getUnresolvedUsed() {
        return mth.getUnresolvedUsed();
    }

    /** 判断该方法是否递归调用自身 */
    public boolean callsSelf() {
        return mth.callsSelf();
    }

    /** 获取与该方法存在重写关系（覆盖/被覆盖）的相关方法列表 */
    public List<JavaMethod> getOverrideRelatedMethods() {
        MethodOverrideAttr ovrdAttr = mth.get(AType.METHOD_OVERRIDE);
        if (ovrdAttr == null) {
            return Collections.emptyList();
        }
        JadxDecompiler decompiler = getDeclaringClass().getRootDecompiler();
        return ovrdAttr.getRelatedMthNodes()
                .stream()
                .map(decompiler::convertMethodNode)
                .collect(Collectors.toList());
    }

    /** 判断该方法是否为构造方法 */
    public boolean isConstructor() {
        return mth.getMethodInfo().isConstructor();
    }

    /** 判断该方法是否为类初始化方法（静态初始化块 &lt;clinit&gt;） */
    public boolean isClassInit() {
        return mth.getMethodInfo().isClassInit();
    }

    /** 获取该方法定义在源码中的位置（偏移量） */
    @Override
    public int getDefPos() {
        return mth.getDefPosition();
    }

    /** 获取该方法的源码字符串 */
    public String getCodeStr() {
        return mth.getCodeStr();
    }

    /** 移除方法的别名，恢复为原始名称 */
    @Override
    public void removeAlias() {
        this.mth.getMethodInfo().removeAlias();
    }

    /** 判断给定的代码注解是否指向该方法自身 */
    @Override
    public boolean isOwnCodeAnnotation(ICodeAnnotation ann) {
        if (ann.getAnnType() == ICodeAnnotation.AnnType.METHOD) {
            return ann.equals(mth);
        }
        return false;
    }

    /** 获取该方法对应的代码节点引用 */
    @Override
    public ICodeNodeRef getCodeNodeRef() {
        return mth;
    }

    /**
     * 内部 API，不稳定！
     */
    @ApiStatus.Internal
    public MethodNode getMethodNode() {
        return mth;
    }

    @Override
    public int hashCode() {
        return mth.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof JavaMethod && mth.equals(((JavaMethod) o).mth);
    }

    @Override
    public String toString() {
        return mth.toString();
    }
}
