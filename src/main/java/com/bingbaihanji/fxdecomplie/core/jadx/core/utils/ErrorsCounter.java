package com.bingbaihanji.fxdecomplie.core.jadx.core.utils;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.IAttributeNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.JadxError;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IDexNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxOverflowException;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 错误计数器，用于统计和记录反编译过程中发生的错误和警告
 * 线程安全，支持在多线程环境下累加错误/警告计数
 */
public class ErrorsCounter {
    private static final Logger LOG = LoggerFactory.getLogger(ErrorsCounter.class);
    /** 是否在错误信息中打印方法字节码指令数量（仅在调试模式下开启） */
    private static final boolean PRINT_MTH_SIZE = false;

    /** 发生错误的节点集合 */
    private final Set<IAttributeNode> errorNodes = new HashSet<>();
    /** 发生警告的节点集合 */
    private final Set<IAttributeNode> warnNodes = new HashSet<>();
    /** 累计错误数量 */
    private int errorsCount;
    /** 累计警告数量 */
    private int warnsCount;

    /**
     * 记录一个错误，将错误信息附加到指定节点并返回格式化的错误消息
     *
     * @param node    发生错误的节点
     * @param warnMsg 错误描述信息
     * @param th      异常对象，可为 {@code null}
     * @return 格式化后的错误消息
     */
    public static <N extends IDexNode & IAttributeNode> String error(N node, String warnMsg, Throwable th) {
        return node.root().getErrorsCounter().addError(node, warnMsg, th);
    }

    /**
     * 记录一个警告，将警告信息附加到指定节点
     *
     * @param node    发生警告的节点
     * @param warnMsg 警告描述信息
     */
    public static <N extends IDexNode & IAttributeNode> void warning(N node, String warnMsg) {
        node.root().getErrorsCounter().addWarning(node, warnMsg);
    }

    /**
     * 格式化错误/警告消息，包含节点类型、节点名称和输入文件名
     *
     * @param node 相关的 dex 节点
     * @param msg  原始消息文本
     * @return 格式化后的消息字符串，格式为 "{msg} in {节点类型}: {节点}, file: {文件名}"
     */
    public static String formatMsg(IDexNode node, String msg) {
        return msg + " in " + node.typeName() + ": " + node + ", file: " + node.getInputFileName();
    }

    /**
     * 向错误计数器添加一条错误记录（线程安全）
     * 将错误节点加入集合，累加错误计数，记录日志，并将 {@link JadxError} 属性附加到节点
     *
     * @param node  发生错误的节点
     * @param error 错误描述信息
     * @param e     异常对象，可为 {@code null}
     * @return 格式化后的错误消息
     */
    private synchronized <N extends IDexNode & IAttributeNode> String addError(N node, String error, @Nullable Throwable e) {
        errorNodes.add(node);
        errorsCount++;

        String msg = formatMsg(node, error);
        if (PRINT_MTH_SIZE && node instanceof MethodNode) {
            String mthSize = "[" + ((MethodNode) node).getInsnsCount() + "] ";
            msg = mthSize + msg;
            error = mthSize + error;
        }
        if (e == null) {
            LOG.error(msg);
        } else if (e instanceof StackOverflowError) {
            LOG.error("{}, error: StackOverflowError", msg);
        } else if (e instanceof JadxOverflowException) {
            // 不打印完整的堆栈跟踪
            String details = e.getMessage();
            e = new JadxOverflowException(details);
            if (details == null || details.isEmpty()) {
                LOG.error("{}", msg);
            } else {
                LOG.error("{}, details: {}", msg, details);
            }
        } else {
            LOG.error(msg, e);
        }
        node.addAttr(AType.JADX_ERROR, new JadxError(error, e));
        return msg;
    }

    /**
     * 向警告计数器添加一条警告记录（线程安全）
     * 将警告节点加入集合，累加警告计数并记录日志
     *
     * @param node 发生警告的节点
     * @param warn 警告描述信息
     */
    private synchronized <N extends IDexNode & IAttributeNode> void addWarning(N node, String warn) {
        warnNodes.add(node);
        warnsCount++;
        LOG.warn(formatMsg(node, warn));
    }

    /**
     * 打印错误与警告的汇总报告
     * 输出所有发生错误的节点列表（按名称排序），以及警告的总数与涉及节点数
     */
    public void printReport() {
        if (getErrorCount() > 0) {
            LOG.error("{} errors occurred in following nodes:", getErrorCount());
            List<String> errors = new ArrayList<>(errorNodes.size());
            for (IAttributeNode node : errorNodes) {
                String nodeName = node.getClass().getSimpleName().replace("Node", "");
                errors.add(nodeName + ": " + node);
            }
            Collections.sort(errors);
            for (String err : errors) {
                LOG.error("  {}", err);
            }
        }
        if (getWarnsCount() > 0) {
            LOG.warn("{} warnings in {} nodes", getWarnsCount(), warnNodes.size());
        }
    }

    /**
     * 获取累计错误数量
     *
     * @return 错误数量
     */
    public int getErrorCount() {
        return errorsCount;
    }

    /**
     * 获取累计警告数量
     *
     * @return 警告数量
     */
    public int getWarnsCount() {
        return warnsCount;
    }

    /**
     * 获取发生错误的节点集合
     *
     * @return 错误节点集合
     */
    public Set<IAttributeNode> getErrorNodes() {
        return errorNodes;
    }

    /**
     * 获取发生警告的节点集合
     *
     * @return 警告节点集合
     */
    public Set<IAttributeNode> getWarnNodes() {
        return warnNodes;
    }
}
