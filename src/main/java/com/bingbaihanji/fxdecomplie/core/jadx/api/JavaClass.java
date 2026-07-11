package com.bingbaihanji.fxdecomplie.core.jadx.api;

import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeAnnotation;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeNodeRef;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.AnonymousClassAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.InlinedAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.AccessInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.FieldNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


/**
 * Java 类的表示，封装了反编译后的类信息
 * <p>
 * 提供对类的源码、字段、方法、内部类、依赖关系等的访问，
 * 是 jadx 反编译引擎 API 的核心类之一
 * </p>
 */
public final class JavaClass implements JavaNode {
    private final @Nullable JadxDecompiler decompiler;
    private final ClassNode cls;
    private final @Nullable JavaClass parent;

    private List<JavaClass> innerClasses = Collections.emptyList();
    private List<JavaClass> inlinedClasses = Collections.emptyList();
    private List<JavaField> fields = Collections.emptyList();
    private List<JavaMethod> methods = Collections.emptyList();
    private boolean listsLoaded;

    JavaClass(ClassNode classNode, @NotNull JadxDecompiler decompiler) {
        this.decompiler = decompiler;
        this.cls = classNode;
        this.parent = null;
    }

    /**
     * 内部类构造函数
     */
    JavaClass(ClassNode classNode, @NotNull JavaClass parent) {
        this.decompiler = null;
        this.cls = classNode;
        this.parent = parent;
    }

    /**
     * 获取该类反编译后的 Java 源代码字符串
     *
     * @return 反编译后的源代码字符串
     */
    public String getCode() {
        return getCodeInfo().getCodeStr();
    }

    /**
     * 获取该类的代码信息对象，包含反编译后的源码及元数据
     *
     * @return 代码信息对象，不会返回 null
     */
    public @NotNull ICodeInfo getCodeInfo() {
        ICodeInfo code = load();
        if (code != null) {
            return code;
        }
        return cls.decompile();
    }

    /**
     * 触发该类的反编译操作并加载内部列表 (字段、方法等)
     * 如果已经加载过，则不会重复执行
     */
    public void decompile() {
        load();
    }

    /**
     * 检测调用 load() 是否会触发耗时的反编译操作
     *
     * @return 如果需要执行反编译则返回 true，否则返回 false
     */
    public boolean loadingWouldRequireDecompilation() {
        if (listsLoaded) {
            // 列表已加载完毕，无论类本身状态如何都无需反编译
            return false;
        }
        if (cls.getState().isProcessComplete()) {
            // 反编译已完成
            return false;
        }
        return true;
    }

    /**
     * 重新加载该类，清除缓存并重新反编译
     *
     * @return 重新反编译后的代码信息
     */
    public synchronized ICodeInfo reload() {
        listsLoaded = false;
        return cls.reloadCode();
    }

    /**
     * 卸载该类，清除已加载的列表和反编译代码，释放内存
     */
    public void unload() {
        listsLoaded = false;
        cls.unloadCode();
    }

    /**
     * 判断该类是否不包含可生成的代码 (例如被标记为不生成)
     *
     * @return 如果该类不生成代码则返回 true
     */
    public boolean isNoCode() {
        return cls.contains(AFlag.DONT_GENERATE);
    }

    /**
     * 判断该类是否为内部类
     *
     * @return 如果是内部类则返回 true
     */
    public boolean isInner() {
        return cls.isInner();
    }

    /**
     * 获取该类反汇编后的 smali 代码
     *
     * @return smali 反汇编代码字符串
     */
    public synchronized String getSmali() {
        return cls.getDisassembledCode();
    }

    @Override
    public boolean isOwnCodeAnnotation(ICodeAnnotation ann) {
        if (ann.getAnnType() == ICodeAnnotation.AnnType.CLASS) {
            return ann.equals(cls);
        }
        return false;
    }

    @Override
    public ICodeNodeRef getCodeNodeRef() {
        return cls;
    }

    /**
     * 内部 API，不稳定！
     * <p>
     * 返回底层的 {@link ClassNode} 节点，供引擎内部使用，外部调用者不应依赖此方法
     * </p>
     *
     * @return 底层类节点
     */
    @ApiStatus.Internal
    public ClassNode getClassNode() {
        return cls;
    }

    /**
     * 反编译该类并加载其内部的字段、方法等列表
     * 如果已经加载过，则不执行任何操作
     *
     * @return 如果执行了反编译则返回代码信息，否则返回 null
     */
    private synchronized @Nullable ICodeInfo load() {
        if (listsLoaded) {
            return null;
        }
        ICodeInfo code;
        if (cls.getState().isProcessComplete()) {
            // 已反编译 -> 类的内部结构已加载
            code = null;
        } else {
            code = cls.decompile();
        }
        loadLists();
        return code;
    }

    private void loadLists() {
        listsLoaded = true;
        JadxDecompiler rootDecompiler = getRootDecompiler();
        int inClsCount = cls.getInnerClasses().size();
        if (inClsCount != 0) {
            List<JavaClass> list = new ArrayList<>(inClsCount);
            for (ClassNode inner : cls.getInnerClasses()) {
                if (!inner.contains(AFlag.DONT_GENERATE)) {
                    JavaClass javaClass = rootDecompiler.convertClassNode(inner);
                    javaClass.loadLists();
                    list.add(javaClass);
                }
            }
            this.innerClasses = Collections.unmodifiableList(list);
        }
        int inlinedClsCount = cls.getInlinedClasses().size();
        if (inlinedClsCount != 0) {
            List<JavaClass> list = new ArrayList<>(inlinedClsCount);
            for (ClassNode inner : cls.getInlinedClasses()) {
                JavaClass javaClass = rootDecompiler.convertClassNode(inner);
                javaClass.loadLists();
                list.add(javaClass);
            }
            this.inlinedClasses = Collections.unmodifiableList(list);
        }

        int fieldsCount = cls.getFields().size();
        if (fieldsCount != 0) {
            List<JavaField> flds = new ArrayList<>(fieldsCount);
            for (FieldNode f : cls.getFields()) {
                if (!f.contains(AFlag.DONT_GENERATE)) {
                    flds.add(rootDecompiler.convertFieldNode(f));
                }
            }
            this.fields = Collections.unmodifiableList(flds);
        }

        int methodsCount = cls.getMethods().size();
        if (methodsCount != 0) {
            List<JavaMethod> mths = new ArrayList<>(methodsCount);
            for (MethodNode m : cls.getMethods()) {
                if (!m.contains(AFlag.DONT_GENERATE)) {
                    mths.add(rootDecompiler.convertMethodNode(m));
                }
            }
            mths.sort(Comparator.comparing(JavaMethod::getName));
            this.methods = Collections.unmodifiableList(mths);
        }
    }

    JadxDecompiler getRootDecompiler() {
        if (parent != null) {
            return parent.getRootDecompiler();
        }
        return Objects.requireNonNull(decompiler);
    }

    /**
     * 获取指定位置处的代码注解
     *
     * @param pos 代码中的位置
     * @return 该位置的代码注解，如果没有则返回 null
     */
    public @Nullable ICodeAnnotation getAnnotationAt(int pos) {
        return getCodeInfo().getCodeMetadata().getAt(pos);
    }

    /**
     * 获取该类代码中位置到引用节点的映射
     * <p>
     * 键为代码位置，值为对应的 {@link JavaNode} 引用，用于代码导航等功能
     * </p>
     *
     * @return 位置到引用节点的映射，若无元数据则返回空映射
     */
    public Map<Integer, JavaNode> getUsageMap() {
        Map<Integer, ICodeAnnotation> map = getCodeInfo().getCodeMetadata().getAsMap();
        if (map.isEmpty() || decompiler == null) {
            return Collections.emptyMap();
        }
        Map<Integer, JavaNode> resultMap = new HashMap<>(map.size());
        for (Map.Entry<Integer, ICodeAnnotation> entry : map.entrySet()) {
            int codePosition = entry.getKey();
            ICodeAnnotation obj = entry.getValue();
            if (obj instanceof ICodeNodeRef) {
                JavaNode node = getRootDecompiler().getJavaNodeByRef((ICodeNodeRef) obj);
                if (node != null) {
                    resultMap.put(codePosition, node);
                }
            }
        }
        return resultMap;
    }

    /**
     * 在给定代码信息中查找指定节点被使用的所有位置
     *
     * @param codeInfo 待搜索的代码信息
     * @param javaNode 目标节点
     * @return 该节点被引用的所有代码位置列表，若无元数据则返回空列表
     */
    public List<Integer> getUsePlacesFor(ICodeInfo codeInfo, JavaNode javaNode) {
        if (!codeInfo.hasMetadata()) {
            return Collections.emptyList();
        }
        List<Integer> result = new ArrayList<>();
        codeInfo.getCodeMetadata().searchDown(0, (pos, ann) -> {
            if (javaNode.isOwnCodeAnnotation(ann)) {
                result.add(pos);
            }
            return null;
        });
        return result;
    }

    /**
     * 获取引用了该类的所有节点
     *
     * @return 使用该类的节点列表
     */
    @Override
    public List<JavaNode> getUseIn() {
        return getRootDecompiler().convertNodes(cls.getUseIn());
    }

    /**
     * 根据反编译后的行号获取对应的源码行号
     *
     * @param decompiledLine 反编译后代码的行号
     * @return 对应的源码行号，若无映射则返回 null
     */
    public Integer getSourceLine(int decompiledLine) {
        return getCodeInfo().getCodeMetadata().getLineMapping().get(decompiledLine);
    }

    /**
     * 获取该类的名称 (可能是别名)
     *
     * @return 类名
     */
    @Override
    public String getName() {
        return cls.getAlias();
    }

    /**
     * 获取该类的完全限定名
     *
     * @return 完全限定类名
     */
    @Override
    public String getFullName() {
        return cls.getFullName();
    }

    /**
     * 获取该类的原始名称 (未经别名处理)
     *
     * @return 原始类名
     */
    public String getRawName() {
        return cls.getRawName();
    }

    /**
     * 获取该类所属的包名
     *
     * @return 包名
     */
    public String getPackage() {
        return cls.getPackage();
    }

    /**
     * 获取该类所属的 Java 包对象
     *
     * @return Java 包对象
     */
    public JavaPackage getJavaPackage() {
        return cls.getPackageNode().getJavaNode();
    }

    /**
     * 获取声明该类的外层类
     *
     * @return 声明该类的外层类，如果是顶层类则返回 null
     */
    @Override
    public @Nullable JavaClass getDeclaringClass() {
        return parent;
    }

    /**
     * 获取原始的顶层父类 (基于声明结构，不考虑代码移动或内联)
     *
     * @return 原始顶层父类，如果自身已是顶层类则返回自身
     */
    public JavaClass getOriginalTopParentClass() {
        return parent == null ? this : parent.getOriginalTopParentClass();
    }

    /**
     * 返回包含该类代码的顶层父类
     * <p>
     * 由于代码可能被移动 (move)或内联 (inline)，
     * 代码父类可能与原始父类不同
     * </p>
     *
     * @return 如果自身已是顶层类则返回自身，否则返回顶层父类
     */
    @Override
    public JavaClass getTopParentClass() {
        JavaClass codeParent = getCodeParent();
        return codeParent == null ? this : codeParent.getTopParentClass();
    }

    /**
     * 返回包含该类代码的父类
     * <p>
     * 由于代码可能被移动 (move)或内联 (inline)，
     * 代码父类可能与原始父类不同
     * </p>
     *
     * @return 代码父类，如果没有则返回 null
     */
    public @Nullable JavaClass getCodeParent() {
        AnonymousClassAttr anonymousClsAttr = cls.get(AType.ANONYMOUS_CLASS);
        if (anonymousClsAttr != null) {
            // 已移动到使用它的类中
            return getRootDecompiler().convertClassNode(anonymousClsAttr.getOuterCls());
        }
        InlinedAttr inlinedAttr = cls.get(AType.INLINED);
        if (inlinedAttr != null) {
            return getRootDecompiler().convertClassNode(inlinedAttr.getInlineCls());
        }
        return parent;
    }

    /**
     * 获取该类的访问标志信息 (public、abstract 等)
     *
     * @return 访问信息对象
     */
    public AccessInfo getAccessInfo() {
        return cls.getAccessFlags();
    }

    /**
     * 获取该类的内部类列表 (会触发按需加载)
     *
     * @return 内部类列表
     */
    public List<JavaClass> getInnerClasses() {
        load();
        return innerClasses;
    }

    /**
     * 获取被内联到该类中的类列表 (会触发按需加载)
     *
     * @return 内联类列表
     */
    public List<JavaClass> getInlinedClasses() {
        load();
        return inlinedClasses;
    }

    /**
     * 获取该类的字段列表 (会触发按需加载)
     *
     * @return 字段列表
     */
    public List<JavaField> getFields() {
        load();
        return fields;
    }

    /**
     * 获取该类的方法列表 (会触发按需加载)
     *
     * @return 方法列表
     */
    public List<JavaMethod> getMethods() {
        load();
        return methods;
    }

    /**
     * 根据方法的短标识 (shortId)查找方法
     *
     * @param shortId 方法短标识
     * @return 匹配的方法，未找到则返回 null
     */
    @Nullable
    public JavaMethod searchMethodByShortId(String shortId) {
        MethodNode methodNode = cls.searchMethodByShortId(shortId);
        if (methodNode == null) {
            return null;
        }
        return getRootDecompiler().convertMethodNode(methodNode);
    }

    /**
     * 获取该类直接依赖的类列表
     *
     * @return 依赖类列表
     */
    public List<JavaClass> getDependencies() {
        JadxDecompiler d = getRootDecompiler();
        return cls.getDependencies().stream().map(d::convertClassNode).toList();
    }

    /**
     * 获取该类依赖的类总数 (包含传递依赖)
     *
     * @return 依赖总数
     */
    public int getTotalDepsCount() {
        return cls.getTotalDepsCount();
    }

    /**
     * 移除该类的别名，恢复使用原始名称
     */
    @Override
    public void removeAlias() {
        cls.removeAlias();
    }

    /**
     * 获取该类定义在代码中的起始位置
     *
     * @return 定义位置
     */
    @Override
    public int getDefPos() {
        return cls.getDefPosition();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof JavaClass && cls.equals(((JavaClass) o).cls);
    }

    @Override
    public int hashCode() {
        return cls.hashCode();
    }

    @Override
    public String toString() {
        return getFullName();
    }
}
