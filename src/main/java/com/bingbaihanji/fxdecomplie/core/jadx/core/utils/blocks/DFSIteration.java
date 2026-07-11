package com.bingbaihanji.fxdecomplie.core.jadx.core.utils.blocks;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;

/**
 * 基于双端队列实现的深度优先（DFS）块迭代器。
 * <p>
 * 从给定的起始基本块开始，按照 {@link #nextFunc} 定义的后继块关系进行深度优先遍历。
 * 使用 {@link BlockSet} 记录已访问块，避免重复遍历。
 */
public class DFSIteration {
	/** 后继块计算函数，用于确定每个块的下一步遍历目标 */
	private final Function<BlockNode, List<BlockNode>> nextFunc;
	/** 双端队列，作为 DFS 遍历的工作队列（从尾部取，实现栈行为） */
	private final Deque<BlockNode> queue;
	/** 已访问块的集合，基于 BitSet 实现 */
	private final BlockSet visited;

	/**
	 * 构造深度优先遍历迭代器。
	 *
	 * @param mth        所属方法节点
	 * @param startBlock 遍历起始块
	 * @param next       后继块计算函数，接收当前块返回下一个要遍历的块列表
	 */
	public DFSIteration(MethodNode mth, BlockNode startBlock, Function<BlockNode, List<BlockNode>> next) {
		nextFunc = next;
		queue = new ArrayDeque<>();
		visited = new BlockSet(mth);
		queue.addLast(startBlock);
		visited.add(startBlock);
	}

	/**
	 * 获取深度优先遍历的下一个块节点。
	 *
	 * @return 下一个未访问的基本块；如果没有更多块则返回 null
	 */
	public @Nullable BlockNode next() {
		BlockNode current = queue.pollLast();
		if (current == null) {
			return null;
		}
		List<BlockNode> nextBlocks = nextFunc.apply(current);
		int count = nextBlocks.size();
		for (int i = count - 1; i >= 0; i--) { // 逆序遍历以保持队列中的原有顺序
			BlockNode next = nextBlocks.get(i);
			if (!visited.addChecked(next)) {
				queue.addLast(next);
			}
		}
		return current;
	}
}
