package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.api.JavaPackage;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeNodeRef;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.LineAttrNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.PackageInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils.containsChar;

/**
 * 包节点，表示 DEX 文件中的包结构。
 * 维护包的层级关系（父包、子包）、包含的类列表，以及包别名（用于重命名）。
 */
public class PackageNode extends LineAttrNode
        implements IPackageUpdate, IDexNode, ICodeNodeRef, Comparable<PackageNode> {

    /** 根节点引用 */
    private final RootNode root;
    /** 原始包信息（不含别名） */
    private final PackageInfo pkgInfo;
    /** 父包节点，顶层包时为 null */
    private final @Nullable PackageNode parentPkg;
    /** 子包列表 */
    private final List<PackageNode> subPackages = new ArrayList<>();
    /** 该包直接包含的类列表 */
    private final List<ClassNode> classes = new ArrayList<>();

    /** 别名包信息，未设置别名时等于原始包信息 */
    private PackageInfo aliasPkgInfo;

    /** 对应的 Java API 层包节点 */
    private JavaPackage javaNode;

    /**
     * 私有构造函数，通过 {@link #getOrBuild} 等静态方法创建实例。
     *
     * @param root      根节点
     * @param parentPkg 父包节点，顶层包时为 null
     * @param pkgInfo   包信息
     */
    private PackageNode(RootNode root, @Nullable PackageNode parentPkg, PackageInfo pkgInfo) {
        this.root = root;
        this.parentPkg = parentPkg;
        this.pkgInfo = pkgInfo;
        this.aliasPkgInfo = pkgInfo;
    }

    /**
     * 获取或构建指定全限定包名对应的包节点，并将给定类添加到该包中。
     *
     * @param root    根节点
     * @param fullPkg 全限定包名（以 '.' 分隔）
     * @param cls     要添加到包中的类节点
     * @return 对应的包节点
     */
    public static PackageNode getForClass(RootNode root, String fullPkg, ClassNode cls) {
        PackageNode pkg = getOrBuild(root, fullPkg);
        pkg.getClasses().add(cls);
        return pkg;
    }

    /**
     * 获取已存在的包节点，如果不存在则递归构建完整的包层级结构。
     * 会自动创建缺失的父包节点。
     *
     * @param root    根节点
     * @param fullPkg 全限定包名（以 '.' 分隔）
     * @return 对应的包节点
     */
    public static PackageNode getOrBuild(RootNode root, String fullPkg) {
        PackageNode existPkg = root.resolvePackage(fullPkg);
        if (existPkg != null) {
            return existPkg;
        }
        PackageInfo pgkInfo = PackageInfo.fromFullPkg(root, fullPkg);
        PackageNode parentPkg = getParentPkg(root, pgkInfo);
        PackageNode pkgNode = new PackageNode(root, parentPkg, pgkInfo);
        if (parentPkg != null) {
            parentPkg.getSubPackages().add(pkgNode);
        }
        root.addPackage(pkgNode);
        return pkgNode;
    }

    /**
     * 获取指定包信息对应的父包节点。如果父包不存在则递归构建。
     *
     * @param root    根节点
     * @param pgkInfo 包信息
     * @return 父包节点，如果为顶层包则返回 null
     */
    private static @Nullable PackageNode getParentPkg(RootNode root, PackageInfo pgkInfo) {
        PackageInfo parentPkg = pgkInfo.getParentPkg();
        if (parentPkg == null) {
            return null;
        }
        return getOrBuild(root, parentPkg.getFullName());
    }

    @Override
    public void rename(String newName) {
        rename(newName, true);
    }

    /**
     * 重命名包。支持三种格式：
     * <ul>
     *   <li>包含 '/' 的名称：按 '/' 转换为 '.' 后作为全限定别名</li>
     *   <li>以 '.' 开头的名称：去掉开头的点作为全限定别名</li>
     *   <li>包含 '.' 的名称：作为全限定别名</li>
     *   <li>其他：作为叶子别名（仅重命名当前包的最后一段）</li>
     * </ul>
     *
     * @param newName     新名称
     * @param runUpdates  是否立即通知子包和类更新
     */
    public void rename(String newName, boolean runUpdates) {
        String alias;
        boolean isFullAlias;
        if (containsChar(newName, '/')) {
            alias = newName.replace('/', '.');
            isFullAlias = true;
        } else if (newName.startsWith(".")) {
            // 视为完整包名，去掉开头的点号
            alias = newName.substring(1);
            isFullAlias = true;
        } else {
            alias = newName;
            isFullAlias = containsChar(newName, '.');
        }
        if (isFullAlias) {
            setFullAlias(alias, runUpdates);
        } else {
            setLeafAlias(alias, runUpdates);
        }
    }

    /**
     * 设置叶子包别名（仅影响当前包的最后一段名称）。
     * 父包路径保持不变。
     *
     * @param alias      新的叶子名称
     * @param runUpdates 是否立即通知子包和类更新
     */
    public void setLeafAlias(String alias, boolean runUpdates) {
        if (pkgInfo.getName().equals(alias)) {
            aliasPkgInfo = pkgInfo;
        } else {
            aliasPkgInfo = PackageInfo.fromShortName(root, getParentAliasPkgInfo(), alias);
        }
        if (runUpdates) {
            updatePackages(this);
        }
    }

    /**
     * 设置全限定包别名（替换整个包路径）。
     *
     * @param fullAlias  新的全限定包名
     * @param runUpdates 是否立即通知子包和类更新
     */
    public void setFullAlias(String fullAlias, boolean runUpdates) {
        if (pkgInfo.getFullName().equals(fullAlias)) {
            aliasPkgInfo = pkgInfo;
        } else {
            aliasPkgInfo = PackageInfo.fromFullPkg(root, fullAlias);
        }
        if (runUpdates) {
            updatePackages(this);
        }
    }

    /**
     * 当父包更新时回调，重新计算当前包的别名信息并传播更新到子包和类。
     */
    @Override
    public void onParentPackageUpdate(PackageNode updatedPkg) {
        aliasPkgInfo = PackageInfo.fromShortName(root, getParentAliasPkgInfo(), aliasPkgInfo.getName());
        updatePackages(updatedPkg);
    }

    /**
     * 触发包更新，将变更传播到所有子包和类。
     */
    public void updatePackages() {
        updatePackages(this);
    }

    private void updatePackages(PackageNode updatedPkg) {
        for (PackageNode subPackage : subPackages) {
            subPackage.onParentPackageUpdate(updatedPkg);
        }
        for (ClassNode cls : classes) {
            cls.onParentPackageUpdate(updatedPkg);
        }
    }

    /**
     * 获取包的短名称（最后一段）。
     */
    public String getName() {
        return pkgInfo.getName();
    }

    /**
     * 获取包的全限定名称（以 '.' 分隔的完整路径）。
     */
    public String getFullName() {
        return pkgInfo.getFullName();
    }

    /**
     * 获取原始包信息（不含别名）。
     */
    public PackageInfo getPkgInfo() {
        return pkgInfo;
    }

    /**
     * 获取别名包信息。如果未设置别名，则返回原始包信息。
     */
    public PackageInfo getAliasPkgInfo() {
        return aliasPkgInfo;
    }

    /**
     * 判断当前包是否设置了别名（名称与原始包不同）。
     */
    public boolean hasAlias() {
        if (pkgInfo == aliasPkgInfo) {
            return false;
        }
        return !pkgInfo.getName().equals(aliasPkgInfo.getName());
    }

    /**
     * 判断父包是否设置了别名（当前包的别名父包与原始父包不同）。
     *
     * @return 如果父包设置了别名返回 true，否则返回 false
     */
    public boolean hasParentAlias() {
        if (pkgInfo == aliasPkgInfo) {
            return false;
        }
        return !Objects.equals(pkgInfo.getParentPkg(), aliasPkgInfo.getParentPkg());
    }

    /** 移除当前包的别名，恢复原始包名 */
    public void removeAlias() {
        aliasPkgInfo = pkgInfo;
    }

    /**
     * 获取父包节点。
     *
     * @return 父包节点，顶层包时返回 null
     */
    public @Nullable PackageNode getParentPkg() {
        return parentPkg;
    }

    /**
     * 获取父包的别名包信息。
     *
     * @return 父包的别名包信息，当前为顶层包时返回 null
     */
    public @Nullable PackageInfo getParentAliasPkgInfo() {
        return parentPkg == null ? null : parentPkg.aliasPkgInfo;
    }

    /**
     * 判断当前包是否为根包（无父包）。
     *
     * @return 如果是根包返回 true，否则返回 false
     */
    public boolean isRoot() {
        return parentPkg == null;
    }

    /**
     * 判断当前包是否为叶子包（无子包）。
     *
     * @return 如果是叶子包返回 true，否则返回 false
     */
    public boolean isLeaf() {
        return subPackages.isEmpty();
    }

    /**
     * 获取当前包的所有子包列表。
     *
     * @return 子包列表
     */
    public List<PackageNode> getSubPackages() {
        return subPackages;
    }

    /**
     * 获取当前包直接包含的类列表。
     *
     * @return 类节点列表
     */
    public List<ClassNode> getClasses() {
        return classes;
    }

    /**
     * 获取当前包包含的去重类列表。
     * 通过 ClassInfo 去重，避免同一个类被多次列出。
     *
     * @return 去重后的类节点列表
     */
    public List<ClassNode> getClassesNoDup() {
        return classes.stream()
                .map(ClassNode::getClassInfo)
                .collect(Collectors.toSet())
                .stream()
                .map(e -> root.resolveClass(e)).collect(Collectors.toList());
    }

    /**
     * 获取对应的 Java API 层包节点。
     *
     * @return Java API 层包节点
     */
    public JavaPackage getJavaNode() {
        return javaNode;
    }

    /**
     * 设置对应的 Java API 层包节点。
     *
     * @param javaNode Java API 层包节点
     */
    public void setJavaNode(JavaPackage javaNode) {
        this.javaNode = javaNode;
    }

    /**
     * 判断当前包是否为空（既没有类也没有子包）。
     *
     * @return 如果包为空返回 true，否则返回 false
     */
    public boolean isEmpty() {
        return classes.isEmpty() && subPackages.isEmpty();
    }

    @Override
    public String typeName() {
        return "package";
    }

    @Override
    public AnnType getAnnType() {
        return AnnType.PKG;
    }

    @Override
    public RootNode root() {
        return root;
    }

    @Override
    public String getInputFileName() {
        return "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PackageNode)) {
            return false;
        }
        return pkgInfo.equals(((PackageNode) o).pkgInfo);
    }

    @Override
    public int hashCode() {
        return pkgInfo.hashCode();
    }

    @Override
    public int compareTo(@NotNull PackageNode other) {
        return getPkgInfo().getFullName().compareTo(other.getPkgInfo().getFullName());
    }

    @Override
    public String toString() {
        return getPkgInfo().getFullName();
    }
}
