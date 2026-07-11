package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeNodeRef;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.IAttributeNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.AccessInfo;

public interface ICodeNode extends IDexNode, IAttributeNode, IUsageInfoNode, ICodeNodeRef {

	ClassNode getDeclaringClass();

	AccessInfo getAccessFlags();

	void setAccessFlags(AccessInfo newAccessFlags);
}
