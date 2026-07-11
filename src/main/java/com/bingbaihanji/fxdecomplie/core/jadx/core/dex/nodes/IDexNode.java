package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.data.IRenameNode;

public interface IDexNode extends IRenameNode {

    String typeName();

    RootNode root();

    String getInputFileName();
}
