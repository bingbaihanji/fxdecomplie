package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.trycatch;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.ClassInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.InsnArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IRegion;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.InsnUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;
import com.bingbaihanji.fxdecomplie.util.JadxConsts;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 异常处理器类，表示 try-catch 语句中的 catch 处理块。
 * 管理异常捕获类型、处理代码块以及与 try 块的关联关系。
 */
public class ExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ExceptionHandler.class);

    /** 捕获的异常类型列表 */
    private final List<ClassInfo> catchTypes = new ArrayList<>(1);
    /** 处理器代码的偏移地址 */
    private final int handlerOffset;
    /** 处理器包含的所有基本块 */
    private final List<BlockNode> blocks = new ArrayList<>();
    /** 处理器的入口基本块 */
    private BlockNode handlerBlock;
    /** 处理器所在的区域 */
    private IRegion handlerRegion;
    /** 异常参数 */
    private InsnArg arg;

    /** 关联的 try 块属性 */
    private TryCatchBlockAttr tryBlock;
    /** 是否为 finally 块 */
    private boolean isFinally;

    /** 是否已标记为待删除 */
    private boolean removed = false;

    /**
     * 私有构造函数，通过 {@link #build} 工厂方法创建实例
     *
     * @param addr 处理器的偏移地址
     */
    private ExceptionHandler(int addr) {
        this.handlerOffset = addr;
    }

    /**
     * 构建异常处理器实例的工厂方法
     *
     * @param mth  所属方法节点
     * @param addr 处理器的偏移地址
     * @param type 捕获的异常类型，null 表示捕获所有异常
     * @return 新创建的异常处理器实例
     */
    public static ExceptionHandler build(MethodNode mth, int addr, @Nullable ClassInfo type) {
        ExceptionHandler eh = new ExceptionHandler(addr);
        eh.addCatchType(mth, type);
        return eh;
    }

    /**
     * 向 catch 块添加异常类型
     *
     * @param mth  所属方法节点
     * @param type 异常类型，null 表示捕获所有异常（Throwable）
     * @return 如果添加成功返回 true，否则返回 false
     */
    public boolean addCatchType(MethodNode mth, @Nullable ClassInfo type) {
        if (type != null) {
            if (catchTypes.contains(type)) {
                return false;
            }
            return catchTypes.add(type);
        }
        if (!this.catchTypes.isEmpty()) {
            mth.addDebugComment("Throwable added to exception handler: '" + catchTypeStr() + "', keep only Throwable");
            catchTypes.clear();
            return true;
        }
        return false;
    }

    /**
     * 批量添加异常捕获类型
     *
     * @param mth   所属方法节点
     * @param types 要添加的异常类型集合
     */
    public void addCatchTypes(MethodNode mth, Collection<ClassInfo> types) {
        for (ClassInfo type : types) {
            addCatchType(mth, type);
        }
    }

    /**
     * 获取所有捕获的异常类型列表
     *
     * @return 捕获的异常类型列表
     */
    public List<ClassInfo> getCatchTypes() {
        return catchTypes;
    }

    /**
     * 获取异常处理器的参数类型
     * 如果是捕获所有异常或有多个异常类型，返回 Throwable 类型
     *
     * @return 异常参数类型
     */
    public ArgType getArgType() {
        if (isCatchAll()) {
            return ArgType.THROWABLE;
        }
        List<ClassInfo> types = getCatchTypes();
        if (types.size() == 1) {
            return types.iterator().next().getType();
        } else {
            return ArgType.THROWABLE;
        }
    }

    /**
     * 判断是否捕获所有异常（catch 块为空或包含 Throwable 类型）
     *
     * @return 如果捕获所有异常返回 true，否则返回 false
     */
    public boolean isCatchAll() {
        if (catchTypes.isEmpty()) {
            return true;
        }
        for (ClassInfo classInfo : catchTypes) {
            if (classInfo.getFullName().equals(JadxConsts.CLASS_THROWABLE)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取处理器代码的偏移地址
     *
     * @return 偏移地址
     */
    public int getHandlerOffset() {
        return handlerOffset;
    }

    /** 获取处理器的入口基本块 */
    public BlockNode getHandlerBlock() {
        return handlerBlock;
    }

    /** 设置处理器的入口基本块 */
    public void setHandlerBlock(BlockNode handlerBlock) {
        this.handlerBlock = handlerBlock;
    }

    /** 获取处理器包含的所有基本块 */
    public List<BlockNode> getBlocks() {
        return blocks;
    }

    /** 向处理器添加一个基本块 */
    public void addBlock(BlockNode node) {
        blocks.add(node);
    }

    /** 获取处理器所在的区域 */
    public IRegion getHandlerRegion() {
        return handlerRegion;
    }

    /** 设置处理器所在的区域 */
    public void setHandlerRegion(IRegion handlerRegion) {
        this.handlerRegion = handlerRegion;
    }

    /** 获取异常参数 */
    public InsnArg getArg() {
        return arg;
    }

    /** 设置异常参数 */
    public void setArg(InsnArg arg) {
        this.arg = arg;
    }

    /** 获取关联的 try 块属性 */
    public TryCatchBlockAttr getTryBlock() {
        return tryBlock;
    }

    /** 设置关联的 try 块属性 */
    public void setTryBlock(TryCatchBlockAttr tryBlock) {
        this.tryBlock = tryBlock;
    }

    /** 判断是否为 finally 块 */
    public boolean isFinally() {
        return isFinally;
    }

    /** 设置是否为 finally 块 */
    public void setFinally(boolean isFinally) {
        this.isFinally = isFinally;
    }

    /** 判断该异常处理器是否已被标记为待删除 */
    public boolean isRemoved() {
        return removed;
    }

    /**
     * 获取异常处理器的底部分割块
     * 用于确定 try-catch 结构中 catch 块的结束位置
     *
     * @return 底部分割块，如果没有找到则返回 null
     */
    @Nullable
    public BlockNode getBottomSplitter() {
        TryCatchBlockAttr handlerTryBlock = getTryBlock();
        // TODO: 实现对包含内部 try 块的 catch 底部分割块的查找支持
        if (handlerTryBlock.getInnerTryBlocks().size() > 1) {
            LOG.warn("No support yet for finding bottom block of try body with multipe inner trys");
            return null;
        }
        TryCatchBlockAttr searchForTryBody;
        if (handlerTryBlock.getInnerTryBlocks().isEmpty()) {
            searchForTryBody = handlerTryBlock;
        } else {
            searchForTryBody = Utils.getOne(handlerTryBlock.getInnerTryBlocks());
        }

        BlockNode splitter = null;
        for (BlockNode handlerPredecessor : getHandlerBlock().getPredecessors()) {
            if (!handlerPredecessor.contains(AFlag.EXC_BOTTOM_SPLITTER)) {
                continue;
            }

            for (BlockNode splitterPredecessor : handlerPredecessor.getPredecessors()) {
                TryCatchBlockAttr tryBody = splitterPredecessor.get(AType.TRY_BLOCK);
                if (tryBody == searchForTryBody) {
                    splitter = handlerPredecessor;
                    break;
                }
            }

            if (splitter != null) {
                break;
            }
        }
        return splitter;
    }

    /**
     * 标记该异常处理器及其关联的代码块为待删除状态
     */
    public void markForRemove() {
        this.removed = true;
        this.blocks.forEach(b -> b.add(AFlag.REMOVE));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ExceptionHandler that = (ExceptionHandler) o;
        return handlerOffset == that.handlerOffset
                && catchTypes.equals(that.catchTypes)
                && Objects.equals(tryBlock, that.tryBlock);
    }

    @Override
    public int hashCode() {
        return Objects.hash(catchTypes, handlerOffset /* , tryBlock */);
    }

    /**
     * 获取捕获异常类型的字符串表示
     *
     * @return 异常类型字符串，多个类型用 " | " 分隔，无类型时返回 "all"
     */
    public String catchTypeStr() {
        return catchTypes.isEmpty() ? "all" : Utils.listToString(catchTypes, " | ", ClassInfo::getShortName);
    }

    @Override
    public String toString() {
        return catchTypeStr() + " -> " + InsnUtils.formatOffset(handlerOffset);
    }
}
