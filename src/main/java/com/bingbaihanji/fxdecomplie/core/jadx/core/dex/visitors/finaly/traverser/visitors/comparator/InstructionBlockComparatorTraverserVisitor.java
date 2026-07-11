package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.visitors.comparator;

import java.util.ArrayList;
import java.util.List;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.CentralityState;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.SameInstructionsStrategy;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.SameInstructionsStrategyImpl;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.factory.DuplicatedTraverserStateFactory;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.factory.TraverserStateFactory;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.state.NoBlockTraverserState;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.state.TerminalTraverserState;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.state.TraverserActivePathState;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.state.TraverserBlockInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.state.TraverserState;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Pair;

/**
 * 指令块比较遍历访问者。
 * <p>
 * 用于比较两个遍历路径（finally 路径和候选路径）中对应指令块的指令序列，
 * 判断它们是否匹配，并根据匹配结果生成相应的下一遍历状态。
 * 支持完全匹配、不均匀匹配、块跳过和终止等场景。
 * </p>
 */
public final class InstructionBlockComparatorTraverserVisitor extends AbstractTraverserComparatorVisitor {

	/**
	 * 为完全匹配场景创建新的活跃路径状态。
	 * <p>
	 * 当两个块中的所有指令都匹配且没有剩余指令需要比较时调用，
	 * 两个状态都禁止中心节点和非起始节点，继续进入下一组块。
	 * </p>
	 *
	 * @param previousState 前一个活跃路径状态
	 * @param finallyBlock  finally 块节点
	 * @param candidateBlock 候选块节点
	 * @return 新的活跃路径状态
	 */
	private static TraverserActivePathState createStateForPerfectMatch(TraverserActivePathState previousState,
			BlockNode finallyBlock,
			BlockNode candidateBlock) {
		CentralityState finallyCentralityState = previousState.getFinallyState().getCentralityState().duplicate();
		CentralityState candidateCentralityState = previousState.getCandidateState().getCentralityState().duplicate();

		finallyCentralityState.setAllowsCentral(false);
		candidateCentralityState.setAllowsCentral(false);
		finallyCentralityState.setAllowsNonStartingNode(false);
		candidateCentralityState.setAllowsNonStartingNode(false);

		TraverserStateFactory<NoBlockTraverserState> finallyStateProducer =
				NoBlockTraverserState.getFactory(finallyCentralityState, finallyBlock);
		TraverserStateFactory<NoBlockTraverserState> candidateStateProducer =
				NoBlockTraverserState.getFactory(candidateCentralityState, candidateBlock);

		return TraverserActivePathState.produceFromFactories(previousState, finallyStateProducer, candidateStateProducer);
	}

	/**
	 * 为不均匀匹配场景创建新的活跃路径状态。
	 * <p>
	 * 当所有可比较的指令都匹配，但其中一个块的指令数量多于另一个时调用。
	 * 指令更多的路径使用 DuplicatedTraverserStateFactory 复制状态，
	 * 指令更少的路径（已被完全搜索完毕）使用 NoBlockTraverserState 进入下一组块。
	 * </p>
	 *
	 * @param previousState      前一个活跃路径状态
	 * @param finallyState       finally 遍历状态
	 * @param candidateState     候选遍历状态
	 * @param finallyBlock       finally 块节点
	 * @param candidateBlock     候选块节点
	 * @param finallyInsnsSize   finally 指令数量
	 * @param candidateInsnsSize 候选指令数量
	 * @return 新的活跃路径状态
	 */
	private static TraverserActivePathState createStateForUnevenMatch(TraverserActivePathState previousState,
			TraverserState finallyState,
			TraverserState candidateState, BlockNode finallyBlock, BlockNode candidateBlock, int finallyInsnsSize,
			int candidateInsnsSize) {
		int maxIterateCount = Math.max(finallyInsnsSize, candidateInsnsSize);
		boolean finallyOverruns = finallyInsnsSize > candidateInsnsSize;

		int insnsDelta;
		TraverserStateFactory<?> newFinallyStateProducer;
		TraverserStateFactory<?> newCandidateStateProducer;
		TraverserBlockInfo adjustedBlockInfo;
		if (finallyOverruns) {
			// finally 指令多于候选指令
			CentralityState candidateCentralityState = candidateState.getCentralityState().duplicate();
			candidateCentralityState.setAllowsCentral(false);
			candidateCentralityState.setAllowsNonStartingNode(false);
			CentralityState finallyCentralityState = finallyState.getCentralityState();
			finallyCentralityState.setAllowsCentral(false);
			finallyCentralityState.setAllowsNonStartingNode(false);

			insnsDelta = finallyInsnsSize - maxIterateCount;
			newFinallyStateProducer = new DuplicatedTraverserStateFactory<>(finallyState);
			adjustedBlockInfo = finallyState.getBlockInsnInfo();
			newCandidateStateProducer = NoBlockTraverserState.getFactory(candidateCentralityState, candidateBlock);
		} else {
			// 候选指令多于 finally 指令
			CentralityState finallyCentralityState = finallyState.getCentralityState().duplicate();
			finallyCentralityState.setAllowsCentral(false);
			finallyCentralityState.setAllowsNonStartingNode(false);
			CentralityState candidateCentralityState = candidateState.getCentralityState();
			candidateCentralityState.setAllowsCentral(false);
			candidateCentralityState.setAllowsNonStartingNode(false);

			insnsDelta = candidateInsnsSize - maxIterateCount;
			candidateState.getCentralityState().setAllowsCentral(false);
			newCandidateStateProducer = new DuplicatedTraverserStateFactory<>(candidateState);
			adjustedBlockInfo = candidateState.getBlockInsnInfo();
			newFinallyStateProducer = NoBlockTraverserState.getFactory(finallyCentralityState, finallyBlock);
		}
		adjustedBlockInfo.setBottomOffset(adjustedBlockInfo.getBottomOffset() + insnsDelta);

		return TraverserActivePathState.produceFromFactories(previousState, newFinallyStateProducer, newCandidateStateProducer);
	}

	/**
	 * 为块跳过场景创建新的活跃路径状态。
	 * <p>
	 * 当没有指令匹配但其中一个状态允许跳过非起始节点时调用。
	 * 优先尝试修复 finally 路径（禁止其 non-starting node），复制候选状态继续比较；
	 * 否则禁用候选路径的 non-starting node，复制 finally 状态继续后续迭代。
	 * </p>
	 *
	 * @param previousState  前一个活跃路径状态
	 * @param finallyState   finally 遍历状态
	 * @param candidateState 候选遍历状态
	 * @param finallyBlock   finally 块节点
	 * @param candidateBlock 候选块节点
	 * @return 新的活跃路径状态
	 */
	private static TraverserActivePathState createStateForBlockSkip(TraverserActivePathState previousState,
			TraverserState finallyState,
			TraverserState candidateState, BlockNode finallyBlock, BlockNode candidateBlock) {
		CentralityState finallyCentralityState = finallyState.getCentralityState();
		CentralityState candidateCentralityState = candidateState.getCentralityState();

		// TODO: 也许可以用控制器逻辑替代此处的判断，以确定是否需要将这些作为路径终点，
		// 然后再合并上方的路径？

		// 优先修复 finally 路径。如果此路径仍然失败，则在后续迭代中检查候选路径是否可修复。
		if (finallyCentralityState.getAllowsNonStartingNode()) {
			finallyCentralityState.setAllowsNonStartingNode(false);
			TraverserStateFactory<NoBlockTraverserState> newFinallyStateProducer =
					NoBlockTraverserState.getFactory(finallyCentralityState, finallyBlock);
			TraverserStateFactory<?> newCandidateStateProducer = new DuplicatedTraverserStateFactory<>(candidateState);
			return TraverserActivePathState.produceFromFactories(previousState, newFinallyStateProducer, newCandidateStateProducer);
		} else {
			candidateCentralityState.setAllowsNonStartingNode(false);
			TraverserStateFactory<NoBlockTraverserState> newCandidateStateProducer =
					NoBlockTraverserState.getFactory(candidateCentralityState, candidateBlock);
			TraverserStateFactory<?> newFinallyStateProducer = new DuplicatedTraverserStateFactory<>(finallyState);
			return TraverserActivePathState.produceFromFactories(previousState, newFinallyStateProducer, newCandidateStateProducer);
		}
	}

	/**
	 * 为终止场景创建新的活跃路径状态。
	 * <p>
	 * 当两个块的指令不匹配且无法跳过时调用，
	 * 为 finally 和候选路径各生成一个终止状态（终止原因为 NON_MATCHING_INSTRUCTIONS），
	 * 以停止当前搜索路径。
	 * </p>
	 *
	 * @param previousState 前一个活跃路径状态
	 * @return 新的活跃路径状态（包含终止状态）
	 */
	private static TraverserActivePathState createStateForTerminatorState(TraverserActivePathState previousState) {
		TraverserStateFactory<TerminalTraverserState> finallyStateProducer =
				TerminalTraverserState.getFactory(TerminalTraverserState.TerminationReason.NON_MATCHING_INSTRUCTIONS);
		TraverserStateFactory<TerminalTraverserState> candidateStateProducer =
				TerminalTraverserState.getFactory(TerminalTraverserState.TerminationReason.NON_MATCHING_INSTRUCTIONS);

		return TraverserActivePathState.produceFromFactories(previousState, finallyStateProducer, candidateStateProducer);
	}

	private final SameInstructionsStrategy sameInstructionsStrategy = new SameInstructionsStrategyImpl();

	/**
	 * 访问遍历活跃路径状态，比较 finally 路径和候选路径中当前块的指令序列。
	 * <p>
	 * 核心逻辑：从每个块的指令列表中从后往前逐条比较指令是否相同，
	 * 根据匹配结果（完全匹配、不完全匹配、无匹配）决定下一遍历状态。
	 * </p>
	 *
	 * @param state 当前的遍历活跃路径状态
	 * @return 处理后的遍历活跃路径状态
	 */
	@Override
	public TraverserActivePathState visit(TraverserActivePathState state) {
		TraverserState finallyState = state.getFinallyState();
		TraverserState candidateState = state.getCandidateState();

		TraverserBlockInfo finallyBlockInfo = finallyState.getBlockInsnInfo();
		TraverserBlockInfo candidateBlockInfo = candidateState.getBlockInsnInfo();

		if (finallyBlockInfo == null || candidateBlockInfo == null) {
			throw new UnsupportedOperationException(
					"The instruction comparator handler has received a state which does not support block insn info");
		}

		BlockNode finallyBlock = finallyBlockInfo.getBlock();
		BlockNode candidateBlock = candidateBlockInfo.getBlock();

		List<InsnNode> finallyInsns = finallyBlockInfo.getInsnsSlice();
		List<InsnNode> candidateInsns = candidateBlockInfo.getInsnsSlice();
		int finallyInsnsSize = finallyInsns.size();
		int candidateInsnsSize = candidateInsns.size();

		int maxIterateCount = Math.min(finallyInsnsSize, candidateInsnsSize);

		List<Pair<InsnNode>> matchingInsns = new ArrayList<>(maxIterateCount);

		// 从后往前逐条比较指令，统计匹配的指令数量
		for (int i = 0; i < maxIterateCount; i++) {
			InsnNode candidateInsn = candidateInsns.get(candidateInsnsSize - i - 1);
			InsnNode finallyInsn = finallyInsns.get(finallyInsnsSize - i - 1);

			if (!sameInstructionsStrategy.sameInsns(candidateInsn, finallyInsn)) {
				break;
			}

			Pair<InsnNode> match = new Pair<>(finallyInsn, candidateInsn);
			matchingInsns.add(match);
		}

		int matchedInsnsCount = matchingInsns.size();

		state.registerWithBlockInfo(finallyBlockInfo, matchedInsnsCount);
		state.registerWithBlockInfo(candidateBlockInfo, matchedInsnsCount);

		boolean finallyOverruns = finallyInsnsSize > candidateInsnsSize;
		boolean candidateOverruns = finallyInsnsSize < candidateInsnsSize;
		boolean sameSizedSlices = !finallyOverruns && !candidateOverruns;
		boolean allMatched = matchedInsnsCount == maxIterateCount;
		boolean noneMatched = matchedInsnsCount == 0;

		state.getMatchedInsns().addAll(matchingInsns);

		TraverserActivePathState newState;
		if (allMatched) {
			if (sameSizedSlices) {
				// 所有指令都匹配，且两个块中都没有剩余指令需要继续比较。
				// 继续进入下一组块。
				newState = createStateForPerfectMatch(state, finallyBlock, candidateBlock);
			} else {
				// 所有可比较的指令都匹配，但其中一个块的指令数量多于另一个。
				// 为指令列表已被完全搜索完毕的处理器继续进入下一组块。
				newState = createStateForUnevenMatch(state, finallyState, candidateState, finallyBlock, candidateBlock, finallyInsnsSize,
						candidateInsnsSize);
			}
		} else if (noneMatched && eitherStateAllowsBlockSkip(finallyState, candidateState)) {
			newState = createStateForBlockSkip(state, finallyState, candidateState, finallyBlock, candidateBlock);
		} else {
			// 如果有任意指令不匹配，说明块的起始指令就不一致。
			// 这意味着后续的块也不应被标记为重复指令，
			// 因此返回终止状态以停止搜索。
			newState = createStateForTerminatorState(state);
		}

		return newState;
	}

	/**
	 * 判断 finally 状态或候选状态是否允许跳过非起始节点（即允许块跳过）。
	 *
	 * @param finallyState  finally 遍历状态
	 * @param candidateState 候选遍历状态
	 * @return 如果任一状态允许跳过非起始节点则返回 true
	 */
	private boolean eitherStateAllowsBlockSkip(TraverserState finallyState, TraverserState candidateState) {
		CentralityState finallyCentralityState = finallyState.getCentralityState();
		CentralityState candidateCentralityState = candidateState.getCentralityState();

		return finallyCentralityState.getAllowsNonStartingNode() || candidateCentralityState.getAllowsNonStartingNode();
	}
}
