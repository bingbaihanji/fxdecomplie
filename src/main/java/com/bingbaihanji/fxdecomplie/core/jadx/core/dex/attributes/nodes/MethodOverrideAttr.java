package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.PinnedAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IMethodDetails;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;

public class MethodOverrideAttr extends PinnedAttribute {

    /**
     * All methods overridden by current method. Current method excluded, empty for base method.
     */
    private final List<IMethodDetails> overrideList;
    private final Set<IMethodDetails> baseMethods;
    /**
     * All method nodes from override hierarchy. Current method included.
     */
    private SortedSet<MethodNode> relatedMthNodes;

    public MethodOverrideAttr(List<IMethodDetails> overrideList, SortedSet<MethodNode> relatedMthNodes, Set<IMethodDetails> baseMethods) {
        this.overrideList = overrideList;
        this.relatedMthNodes = relatedMthNodes;
        this.baseMethods = baseMethods;
    }

    public List<IMethodDetails> getOverrideList() {
        return overrideList;
    }

    public SortedSet<MethodNode> getRelatedMthNodes() {
        return relatedMthNodes;
    }

    public void setRelatedMthNodes(SortedSet<MethodNode> relatedMthNodes) {
        this.relatedMthNodes = relatedMthNodes;
    }

    public Set<IMethodDetails> getBaseMethods() {
        return baseMethods;
    }

    @Override
    public AType<MethodOverrideAttr> getAttrType() {
        return AType.METHOD_OVERRIDE;
    }

    @Override
    public String toString() {
        return "METHOD_OVERRIDE: " + getBaseMethods();
    }
}
