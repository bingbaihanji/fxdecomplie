package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;

/**
 * DEX 树遍历访问者接口
 * <p>
 * 定义了对 DEX 文件结构 (根节点、类节点、方法节点)进行遍历时的访问回调方法
 * 实现此接口可对 DEX 树的各个层级执行自定义处理逻辑
 * </p>
 */
public interface IDexTreeVisitor {

    /**
     * 获取访问者的简短标识名称
     *
     * @return 访问者的名称标识
     */
    String getName();

    /**
     * 初始化回调，在 DEX 树加载完成后、访问者遍历开始前调用
     * <p>
     * 可用于执行前置准备工作，例如初始化数据结构或解析依赖信息
     * </p>
     *
     * @param root DEX 树的根节点
     * @throws JadxException 初始化过程中发生异常时抛出
     */
    void init(RootNode root) throws JadxException;

    /**
     * 访问类节点
     * <p>
     * 遍历过程中遇到类节点时调用可通过返回值控制是否继续遍历该类的子方法和内部类
     * </p>
     *
     * @param cls 待访问的类节点
     * @return {@code true} 继续遍历子方法和内部类 {@code false} 跳过该类的子节点遍历
     * @throws JadxException 访问过程中发生异常时抛出
     */
    boolean visit(ClassNode cls) throws JadxException;

    /**
     * 访问方法节点
     * <p>
     * 遍历过程中遇到方法节点时调用，可对该方法执行自定义处理逻辑
     * </p>
     *
     * @param mth 待访问的方法节点
     * @throws JadxException 访问过程中发生异常时抛出
     */
    void visit(MethodNode mth) throws JadxException;
}
