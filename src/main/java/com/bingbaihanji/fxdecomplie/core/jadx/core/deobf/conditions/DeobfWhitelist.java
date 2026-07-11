package com.bingbaihanji.fxdecomplie.core.jadx.core.deobf.conditions;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.PackageNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 反混淆白名单条件。
 * <p>
 * 对处于白名单中的包或类禁止重命名（反混淆），常用于保留 Android 支持库、
 * AndroidX 等已知库的原始名称。白名单项支持以 {@code .*} 结尾表示整个包。
 */
public class DeobfWhitelist extends AbstractDeobfCondition {

    /** 默认白名单列表，包含常见的需保留原名的包和类 */
    public static final List<String> DEFAULT_LIST = Arrays.asList(
            "android.support.v4.*",
            "android.support.v7.*",
            "android.support.v4.os.*",
            "android.support.annotation.Px",
            "androidx.core.os.*",
            "androidx.annotation.Px");

    /** 默认白名单的字符串形式（以空格分隔各项） */
    public static final String DEFAULT_STR = Utils.listToString(DEFAULT_LIST, " ");

    /** 白名单中的包名集合（去除末尾的 {@code .*} 后） */
    private final Set<String> packages = new HashSet<>();
    /** 白名单中的完整类名集合 */
    private final Set<String> classes = new HashSet<>();

    /**
     * 初始化白名单条件，从根节点参数中读取反混淆白名单配置，
     * 并将各项分类到包集合或类集合中。
     *
     * @param root 根节点
     */
    @Override
    public void init(RootNode root) {
        packages.clear();
        classes.clear();
        for (String whitelistItem : root.getArgs().getDeobfuscationWhitelist()) {
            if (!whitelistItem.isEmpty()) {
                if (whitelistItem.endsWith(".*")) {
                    packages.add(whitelistItem.substring(0, whitelistItem.length() - 2));
                } else {
                    classes.add(whitelistItem);
                }
            }
        }
    }

    /**
     * 检查给定包是否处于白名单中。
     *
     * @param pkg 待检查的包节点
     * @return 若在白名单中则返回禁止重命名，否则返回无操作
     */
    @Override
    public Action check(PackageNode pkg) {
        if (packages.contains(pkg.getPkgInfo().getFullName())) {
            return Action.FORBID_RENAME;
        }
        return Action.NO_ACTION;
    }

    /**
     * 检查给定类是否处于白名单中。
     *
     * @param cls 待检查的类节点
     * @return 若在白名单中则返回禁止重命名，否则返回无操作
     */
    @Override
    public Action check(ClassNode cls) {
        if (classes.contains(cls.getClassInfo().getFullName())) {
            return Action.FORBID_RENAME;
        }
        return Action.NO_ACTION;
    }
}
