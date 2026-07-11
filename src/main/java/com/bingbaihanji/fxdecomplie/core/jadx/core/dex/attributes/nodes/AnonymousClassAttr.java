package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.PinnedAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;

public class AnonymousClassAttr extends PinnedAttribute {

	public enum InlineType {
		CONSTRUCTOR,
		INSTANCE_FIELD,
	}

	private final ClassNode outerCls;
	private final ArgType baseType;
	private final InlineType inlineType;

	public AnonymousClassAttr(ClassNode outerCls, ArgType baseType, InlineType inlineType) {
		this.outerCls = outerCls;
		this.baseType = baseType;
		this.inlineType = inlineType;
	}

	public ClassNode getOuterCls() {
		return outerCls;
	}

	public ArgType getBaseType() {
		return baseType;
	}

	public InlineType getInlineType() {
		return inlineType;
	}

	@Override
	public AType<AnonymousClassAttr> getAttrType() {
		return AType.ANONYMOUS_CLASS;
	}

	@Override
	public String toString() {
		return "AnonymousClass{" + outerCls + ", base: " + baseType + ", inline type: " + inlineType + '}';
	}
}
