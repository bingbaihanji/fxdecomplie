package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors;

import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.json.JsonMappingGen;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.trycatch.CatchAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;

public class FallbackModeVisitor extends AbstractVisitor {

	@Override
	public void init(RootNode root) {
		if (root.getArgs().isJsonOutput()) {
			JsonMappingGen.dump(root);
		}
	}

	@Override
	public void visit(MethodNode mth) throws JadxException {
		if (mth.isNoCode()) {
			return;
		}
		for (InsnNode insn : mth.getInstructions()) {
			if (insn == null) {
				continue;
			}
			// remove 'exception catch' for instruction which don't throw any exceptions
			CatchAttr catchAttr = insn.get(AType.EXC_CATCH);
			if (catchAttr != null) {
				switch (insn.getType()) {
					case RETURN:
					case IF:
					case GOTO:
					case JAVA_JSR:
					case MOVE:
					case MOVE_EXCEPTION:
					case ARITH: // ??
					case NEG:
					case CONST:
					case CONST_STR:
					case CONST_CLASS:
					case CMP_L:
					case CMP_G:
						insn.remove(AType.EXC_CATCH);
						break;

					default:
						break;
				}
			}
		}
	}
}
