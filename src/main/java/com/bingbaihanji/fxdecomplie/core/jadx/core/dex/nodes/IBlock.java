package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.ICodeWriter;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.RegionGen;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.CodegenException;

import java.util.List;

public interface IBlock extends IContainer {

    List<InsnNode> getInstructions();

    @Override
    default void generate(RegionGen regionGen, ICodeWriter code) throws CodegenException {
        regionGen.makeSimpleBlock(this, code);
    }
}
