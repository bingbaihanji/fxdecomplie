package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.ICatch;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.ITry;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.utils.Utils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.ClassInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.InsnType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.trycatch.CatchAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.trycatch.ExcHandlerAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.trycatch.ExceptionHandler;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.ProcessInstructionsVisitor.getNextInsnOffset;

/**
 * Try/Catch 附加访问器
 * <p>
 * 将方法中的 try/catch 信息 (异常处理块 try 块边界)附加到对应的指令上，
 * 为后续的异常处理分析和代码生成做准备该访问器在
 * {@link ProcessInstructionsVisitor} 之前运行
 */
@JadxVisitor(
        name = "Attach Try/Catch Visitor",
        desc = "Attach try/catch info to instructions",
        runBefore = {
                ProcessInstructionsVisitor.class
        }
)
public class AttachTryCatchVisitor extends AbstractVisitor {
    private static final Logger LOG = LoggerFactory.getLogger(AttachTryCatchVisitor.class);

    /**
     * 初始化方法中的所有 try/catch 块，将异常处理器转换并标记 try 块边界
     *
     * @param mth          目标方法节点
     * @param insnByOffset 以偏移量为索引的指令数组
     * @param tries        原始的 try 块数据列表
     */
    private static void initTryCatches(MethodNode mth, InsnNode[] insnByOffset, List<ITry> tries) {
        if (tries.isEmpty()) {
            return;
        }
        if (false) {
            LOG.debug("Raw try blocks in {}", mth);
            tries.forEach(tryData -> LOG.debug(" - {}", tryData));
        }
        for (ITry tryData : tries) {
            List<ExceptionHandler> handlers = convertToHandlers(mth, tryData.getCatch(), insnByOffset);
            if (handlers.isEmpty()) {
                continue;
            }
            markTryBounds(insnByOffset, tryData, CatchAttr.build(handlers));
        }
    }

    /**
     * 标记 try 块的起止边界，为范围内的指令附加 catch 属性，
     * 并在首条和末条指令上分别添加 TRY_ENTER / TRY_LEAVE 标志
     */
    private static void markTryBounds(InsnNode[] insnByOffset, ITry aTry, CatchAttr catchAttr) {
        int offset = aTry.getStartOffset();
        int end = aTry.getEndOffset();

        boolean tryBlockStarted = false;
        InsnNode insn = null;
        while (offset <= end) {
            InsnNode insnAtOffset = insnByOffset[offset];
            if (insnAtOffset != null) {
                insn = insnAtOffset;
                attachCatchAttr(catchAttr, insn);
                if (!tryBlockStarted) {
                    insn.add(AFlag.TRY_ENTER);
                    tryBlockStarted = true;
                }
            }
            offset = getNextInsnOffset(insnByOffset, offset);
            if (offset == -1) {
                break;
            }
        }
        if (tryBlockStarted) {
            insn.add(AFlag.TRY_LEAVE);
        } else {
            // 范围内未找到任何指令 -> 在起始偏移处插入一个 nop 指令
            InsnNode nop = insertNOP(insnByOffset, aTry.getStartOffset());
            nop.add(AFlag.TRY_ENTER);
            nop.add(AFlag.TRY_LEAVE);
            nop.addAttr(catchAttr);
        }
    }

    /**
     * 为指令附加 catch 属性 若指令上已存在 catch 属性，则合并两者的异常处理器
     */
    private static void attachCatchAttr(CatchAttr catchAttr, InsnNode insn) {
        CatchAttr existAttr = insn.get(AType.EXC_CATCH);
        if (existAttr != null) {
            // 合并异常处理器
            List<ExceptionHandler> handlers = Utils.concat(existAttr.getHandlers(), catchAttr.getHandlers());
            insn.addAttr(CatchAttr.build(handlers));
        } else {
            insn.addAttr(catchAttr);
        }
    }

    /**
     * 将 catch 块转换为异常处理器列表，包含具体类型的处理器以及可能存在的
     * catch-all (捕获所有异常)处理器
     */
    private static List<ExceptionHandler> convertToHandlers(MethodNode mth, ICatch catchBlock, InsnNode[] insnByOffset) {
        int[] handlerOffsetArr = catchBlock.getHandlers();
        String[] handlerTypes = catchBlock.getTypes();

        int handlersCount = handlerOffsetArr.length;
        List<ExceptionHandler> list = new ArrayList<>(handlersCount);
        for (int i = 0; i < handlersCount; i++) {
            int handlerOffset = handlerOffsetArr[i];
            ClassInfo type = ClassInfo.fromName(mth.root(), handlerTypes[i]);
            Utils.addToList(list, createHandler(mth, insnByOffset, handlerOffset, type));
        }
        int allHandlerOffset = catchBlock.getCatchAllHandler();
        if (allHandlerOffset >= 0) {
            Utils.addToList(list, createHandler(mth, insnByOffset, allHandlerOffset, null));
        }
        return list;
    }

    /**
     * 在指定偏移处创建异常处理器若该处已存在处理器则复用 (并可能追加捕获类型)，
     * 否则新建处理器并将其注册到方法上
     *
     * @param type 捕获的异常类型，null 表示 catch-all 处理器
     * @return 创建或复用的异常处理器 若仅更新了已有处理器则返回 null
     */
    @Nullable
    private static ExceptionHandler createHandler(MethodNode mth, InsnNode[] insnByOffset, int handlerOffset, @Nullable ClassInfo type) {
        InsnNode insn = insnByOffset[handlerOffset];
        if (insn != null) {
            ExcHandlerAttr excHandlerAttr = insn.get(AType.EXC_HANDLER);
            if (excHandlerAttr != null) {
                ExceptionHandler handler = excHandlerAttr.getHandler();
                if (handler.addCatchType(mth, type)) {
                    // 已有处理器被更新 (假定来自同一 try 块)——不再重复添加
                    return null;
                }
                // 相同的处理器 (可能被不同的 try 块共用)
                return handler;
            }
        } else {
            insn = insertNOP(insnByOffset, handlerOffset);
        }
        ExceptionHandler handler = ExceptionHandler.build(mth, handlerOffset, type);
        mth.addExceptionHandler(handler);
        insn.addAttr(new ExcHandlerAttr(handler));
        return handler;
    }

    /**
     * 在指定偏移处插入一条合成的 NOP 指令，并将其登记到指令数组中
     */
    private static InsnNode insertNOP(InsnNode[] insnByOffset, int offset) {
        InsnNode nop = new InsnNode(InsnType.NOP, 0);
        nop.setOffset(offset);
        nop.add(AFlag.SYNTHETIC);
        insnByOffset[offset] = nop;
        return nop;
    }

    @Override
    public void visit(MethodNode mth) throws JadxException {
        if (mth.isNoCode()) {
            return;
        }
        initTryCatches(mth, mth.getInstructions(), mth.getCodeReader().getTries());
    }
}
