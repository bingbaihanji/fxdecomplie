package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IRegion;

/**
 * Region created based on parent instruction
 */
public class RegionRefAttr implements IJadxAttribute {
	private final IRegion region;

	public RegionRefAttr(IRegion region) {
		this.region = region;
	}

	public IRegion getRegion() {
		return region;
	}

	@Override
	public AType<RegionRefAttr> getAttrType() {
		return AType.REGION_REF;
	}

	@Override
	public String toString() {
		return "RegionRef:" + region.baseString();
	}
}
