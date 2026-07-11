package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.mods;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.InsnType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.InsnArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.RegisterArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.regions.conditions.IfCondition;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.InsnUtils;

import java.util.Collection;
import java.util.function.Consumer;

public final class TernaryInsn extends InsnNode {

    private IfCondition condition;

    public TernaryInsn(IfCondition condition, RegisterArg result, InsnArg th, InsnArg els) {
        this();
        setResult(result);

        if (th.isFalse() && els.isTrue()) {
            // inverted
            this.condition = IfCondition.invert(condition);
            addArg(els);
            addArg(th);
        } else {
            this.condition = condition;
            addArg(th);
            addArg(els);
        }
        visitInsns(this::inheritMetadata);
    }

    private TernaryInsn() {
        super(InsnType.TERNARY, 2);
    }

    public IfCondition getCondition() {
        return condition;
    }

    public void simplifyCondition() {
        condition = IfCondition.simplify(condition);
        if (condition.getMode() == IfCondition.Mode.NOT) {
            invert();
        }
    }

    private void invert() {
        condition = IfCondition.invert(condition);
        InsnArg tmp = getArg(0);
        setArg(0, getArg(1));
        setArg(1, tmp);
    }

    @Override
    public void getRegisterArgs(Collection<RegisterArg> list) {
        super.getRegisterArgs(list);
        list.addAll(condition.getRegisterArgs());
    }

    @Override
    public boolean replaceArg(InsnArg from, InsnArg to) {
        if (super.replaceArg(from, to)) {
            return true;
        }
        return condition.replaceArg(from, to);
    }

    @Override
    public void visitInsns(Consumer<InsnNode> visitor) {
        super.visitInsns(visitor);
        condition.visitInsns(visitor);
    }

    @Override
    public boolean isSame(InsnNode obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof TernaryInsn) || !super.isSame(obj)) {
            return false;
        }
        TernaryInsn that = (TernaryInsn) obj;
        return condition.equals(that.condition);
    }

    @Override
    public InsnNode copy() {
        TernaryInsn copy = new TernaryInsn();
        copy.condition = condition;
        return copyCommonParams(copy);
    }

    @Override
    public void rebindArgs() {
        super.rebindArgs();
        for (RegisterArg reg : condition.getRegisterArgs()) {
            InsnNode parentInsn = reg.getParentInsn();
            if (parentInsn != null) {
                parentInsn.rebindArgs();
            }
        }
    }

    @Override
    public String toString() {
        return InsnUtils.formatOffset(offset) + ": TERNARY "
                + getResult() + " = (" + condition + ") ? " + getArg(0) + " : " + getArg(1);
    }
}
