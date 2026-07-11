package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AttrList;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.Edge;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;

import java.util.Objects;

/**
 * 边指令属性：表示需要附加在控制流图某条边（从起始块到结束块）上的指令
 * <p>
 * 该属性会同时添加到边的起始块和结束块上，用于在代码生成阶段
 * 在两个基本块之间的跳转边上插入指令
 * </p>
 */
public class EdgeInsnAttr implements IJadxAttribute {

    /** 边的起始块 */
    private final BlockNode start;
    /** 边的结束块 */
    private final BlockNode end;
    /** 附加在该边上的指令 */
    private final InsnNode insn;

    private EdgeInsnAttr(BlockNode start, BlockNode end, InsnNode insn) {
        this.start = start;
        this.end = end;
        this.insn = insn;
    }

    /**
     * 为指定的边添加边指令属性
     *
     * @param edge 目标边
     * @param insn 要附加的指令
     */
    public static void addEdgeInsn(Edge edge, InsnNode insn) {
        addEdgeInsn(edge.getSource(), edge.getTarget(), insn);
    }

    /**
     * 为从 start 到 end 的边添加边指令属性，并同时挂载到两个块上（避免重复添加）
     *
     * @param start 边的起始块
     * @param end   边的结束块
     * @param insn  要附加的指令
     */
    public static void addEdgeInsn(BlockNode start, BlockNode end, InsnNode insn) {
        EdgeInsnAttr edgeInsnAttr = new EdgeInsnAttr(start, end, insn);
        if (!start.getAll(AType.EDGE_INSN).contains(edgeInsnAttr)) {
            start.addAttr(AType.EDGE_INSN, edgeInsnAttr);
        }
        if (!end.getAll(AType.EDGE_INSN).contains(edgeInsnAttr)) {
            end.addAttr(AType.EDGE_INSN, edgeInsnAttr);
        }
    }

    @Override
    public AType<AttrList<EdgeInsnAttr>> getAttrType() {
        return AType.EDGE_INSN;
    }

    /**
     * 获取边的起始块
     *
     * @return 起始块
     */
    public BlockNode getStart() {
        return start;
    }

    /**
     * 获取边的结束块
     *
     * @return 结束块
     */
    public BlockNode getEnd() {
        return end;
    }

    /**
     * 获取附加在该边上的指令
     *
     * @return 指令节点
     */
    public InsnNode getInsn() {
        return insn;
    }

    /**
     * 基于起始块、结束块与指令内容判断两个属性是否相等
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EdgeInsnAttr that = (EdgeInsnAttr) o;
        return start.equals(that.start)
                && end.equals(that.end)
                && insn.isDeepEquals(that.insn);
    }

    /**
     * 基于起始块、结束块与指令计算哈希码
     */
    @Override
    public int hashCode() {
        return Objects.hash(start, end, insn);
    }

    /**
     * 返回边指令属性的字符串表示
     */
    @Override
    public String toString() {
        return "EDGE_INSN: " + start + "->" + end + ' ' + insn;
    }
}
