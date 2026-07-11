package com.bingbaihanji.fxdecomplie.core.jadx.core.utils;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.AbstractVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;

public class DebugChecksPass extends AbstractVisitor {

	private final String visitorName;

	public DebugChecksPass(String visitorName) {
		this.visitorName = visitorName;
	}

	@Override
	public String getName() {
		return "Checks-for-" + visitorName;
	}

	@Override
	public void visit(MethodNode mth) throws JadxException {
		if (!mth.contains(AType.JADX_ERROR)) {
			try {
				DebugChecks.runChecksAfterVisitor(mth, visitorName);
			} catch (Exception e) {
				mth.addError("Check error", e);
			}
		}
	}
}
