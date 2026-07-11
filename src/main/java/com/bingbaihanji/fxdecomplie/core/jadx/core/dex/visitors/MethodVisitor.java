package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;

import java.util.function.Consumer;

public class MethodVisitor extends AbstractVisitor {

    private final String name;
    private final Consumer<MethodNode> visitor;

    public MethodVisitor(String name, Consumer<MethodNode> visitor) {
        this.name = name;
        this.visitor = visitor;
    }

    @Override
    public void visit(MethodNode mth) throws JadxException {
        visitor.accept(mth);
    }

    @Override
    public String getName() {
        return name;
    }
}
