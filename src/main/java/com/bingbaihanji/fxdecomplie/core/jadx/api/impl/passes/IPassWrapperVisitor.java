package com.bingbaihanji.fxdecomplie.core.jadx.api.impl.passes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass.JadxPass;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.IDexTreeVisitor;

public interface IPassWrapperVisitor extends IDexTreeVisitor {

	JadxPass getPass();
}
