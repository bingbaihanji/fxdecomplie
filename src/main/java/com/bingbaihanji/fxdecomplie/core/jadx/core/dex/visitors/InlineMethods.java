package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.MethodInlineAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.FieldInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.MethodInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.BaseInvokeNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.IndexInsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.InsnType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.InvokeNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.InsnArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.RegisterArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.*;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference.TypeInferenceVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.BlockUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.InsnRemover;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import com.bingbaihanji.fxdecomplie.util.collection.ListUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@JadxVisitor(
        name = "InlineMethods",
        desc = "Inline methods (previously marked in MarkMethodsForInline)",
        runAfter = TypeInferenceVisitor.class,
        runBefore = ModVisitor.class
)
/**
 * 方法内联访问器
 * <p>
 * 将此前在 {@link MarkMethodsForInline} 中标记为可内联的方法调用替换为方法体本身，
 * 从而在反编译结果中消除多余的间接调用 (如简单的 getter/setter 访问器)
 * 内联过程中会完成寄存器重映射、结果值处理，并同步更新方法/字段/类的使用 (usage)信息
 */
public class InlineMethods extends AbstractVisitor {
    private static final Logger LOG = LoggerFactory.getLogger(InlineMethods.class);

    /**
     * 遍历方法的所有基本块，处理其中的 INVOKE 指令，尝试对其进行内联
     *
     * @param mth 待处理的方法节点
     */
    @Override
    public void visit(MethodNode mth) throws JadxException {
        if (mth.isNoCode()) {
            return;
        }
        for (BlockNode block : mth.getBasicBlocks()) {
            for (InsnNode insn : block.getInstructions()) {
                if (insn.getType() == InsnType.INVOKE) {
                    processInvokeInsn(mth, block, (InvokeNode) insn);
                }
            }
        }
    }

    private void processInvokeInsn(MethodNode mth, BlockNode block, InvokeNode insn) {
        IMethodDetails callMthDetails = insn.get(AType.METHOD_DETAILS);
        if (!(callMthDetails instanceof MethodNode)) {
            return;
        }
        MethodNode callMth = (MethodNode) callMthDetails;
        try {
            MethodInlineAttr mia = MarkMethodsForInline.process(callMth);
            if (mia == null) {
                // 方法尚未加载 => 强制处理其所在类以完成加载
                mth.addDebugComment("Class process forced to load method for inline: " + callMth);
                mth.root().getProcessClasses().forceProcess(callMth.getParentClass());
                // 重新检查一次
                mia = MarkMethodsForInline.process(callMth);
                if (mia == null) {
                    mth.addWarnComment("Failed to check method for inline after forced process" + callMth);
                    return;
                }
            }
            if (mia.notNeeded()) {
                return;
            }
            inlineMethod(mth, callMth, mia, block, insn);
        } catch (Exception e) {
            throw new JadxRuntimeException("Failed to process method for inline: " + callMth, e);
        }
    }

    private void inlineMethod(MethodNode mth, MethodNode callMth, MethodInlineAttr mia, BlockNode block, InvokeNode insn) {
        InsnNode inlCopy = mia.getInsn().copyWithoutResult();
        if (replaceRegs(mth, callMth, mia, insn, inlCopy)) {
            IMethodDetails methodDetailsAttr = inlCopy.get(AType.METHOD_DETAILS);
            // replaceInsn 会一并替换属性，因此需要保留 METHOD_DETAILS
            if (BlockUtils.replaceInsn(mth, block, insn, inlCopy)) {
                if (methodDetailsAttr != null) {
                    inlCopy.addAttr(methodDetailsAttr);
                }
                updateUsageInfo(mth, callMth, mia.getInsn());
                return;
            }
        }
        mth.addWarnComment("Failed to inline method: " + callMth);
        // 撤销对指令所做的改动
        InsnRemover.unbindInsn(mth, inlCopy);
        insn.rebindArgs();
    }

    private boolean replaceRegs(MethodNode mth, MethodNode callMth, MethodInlineAttr mia, InvokeNode insn, InsnNode inlCopy) {
        try {
            if (!callMth.methodInfo().getArgumentsTypes().isEmpty()) {
                // 重映射参数寄存器
                InsnArg[] regs = new InsnArg[callMth.getRegsCount()];
                int[] regNums = mia.getArgsRegNums();
                for (int i = 0; i < regNums.length; i++) {
                    InsnArg arg = insn.getArg(i);
                    regs[regNums[i]] = arg;
                }
                // 替换参数
                List<RegisterArg> inlArgs = new ArrayList<>();
                inlCopy.getRegisterArgs(inlArgs);
                for (RegisterArg r : inlArgs) {
                    int regNum = r.getRegNum();
                    if (regNum >= regs.length) {
                        mth.addWarnComment("Unknown register number '" + r + "' in method call: " + callMth);
                        return false;
                    }
                    InsnArg repl = regs[regNum];
                    if (repl == null) {
                        mth.addWarnComment("Not passed register '" + r + "' in method call: " + callMth);
                        return false;
                    }
                    if (!inlCopy.replaceArg(r, repl.duplicate())) {
                        mth.addWarnComment("Failed to replace arg " + r + " for method inline: " + callMth);
                        return false;
                    }
                }
            }
            RegisterArg resultArg = insn.getResult();
            if (resultArg != null) {
                inlCopy.setResult(resultArg.duplicate());
            } else if (isAssignNeeded(mia.getInsn(), insn, callMth)) {
                // 添加一个假的结果值以生成正确的 Java 表达式 (参见测试 TestGetterInlineNegative)
                inlCopy.setResult(mth.makeSyntheticRegArg(callMth.getReturnType(), "unused"));
            }
            return true;
        } catch (Exception e) {
            mth.addWarnComment("Method inline failed with exception", e);
            return false;
        }
    }

    private boolean isAssignNeeded(InsnNode inlineInsn, InvokeNode parentInsn, MethodNode callMthNode) {
        if (parentInsn.getResult() != null) {
            return false;
        }
        if (parentInsn.contains(AFlag.WRAPPED)) {
            return false;
        }
        if (inlineInsn.getType() == InsnType.IPUT) {
            return false;
        }
        return !callMthNode.isVoidReturn();
    }

    private void updateUsageInfo(MethodNode mth, MethodNode inlinedMth, InsnNode insn) {
        List<MethodNode> newUseIn = new ArrayList<>(inlinedMth.getUseIn());
        newUseIn.remove(mth);
        inlinedMth.setUseIn(newUseIn);
        insn.visitInsns(innerInsn -> {
            // TODO: 与 UsageInfoVisitor 共用代码
            switch (innerInsn.getType()) {
                case INVOKE:
                case CONSTRUCTOR:
                    MethodInfo callMth = ((BaseInvokeNode) innerInsn).getCallMth();
                    MethodNode callMthNode = mth.root().resolveMethod(callMth);
                    if (callMthNode != null) {
                        callMthNode.setUseIn(ListUtils.safeReplace(new ArrayList<>(callMthNode.getUseIn()), inlinedMth, mth));
                        replaceClsUsage(mth, inlinedMth, callMthNode.getParentClass());
                    }
                    break;

                case IGET:
                case IPUT:
                case SPUT:
                case SGET:
                    FieldInfo fieldInfo = (FieldInfo) ((IndexInsnNode) innerInsn).getIndex();
                    FieldNode fieldNode = mth.root().resolveField(fieldInfo);
                    if (fieldNode != null) {
                        fieldNode.setUseIn(ListUtils.safeReplace(new ArrayList<>(fieldNode.getUseIn()), inlinedMth, mth));
                        replaceClsUsage(mth, inlinedMth, fieldNode.getParentClass());
                    }
                    break;
            }
        });
    }

    private void replaceClsUsage(MethodNode mth, MethodNode inlinedMth, ClassNode parentClass) {
        parentClass.setUseInMth(ListUtils.safeReplace(parentClass.getUseInMth(), inlinedMth, mth));
        parentClass.setUseIn(ListUtils.safeReplace(parentClass.getUseIn(), inlinedMth.getParentClass(), mth.getParentClass()));
    }
}
