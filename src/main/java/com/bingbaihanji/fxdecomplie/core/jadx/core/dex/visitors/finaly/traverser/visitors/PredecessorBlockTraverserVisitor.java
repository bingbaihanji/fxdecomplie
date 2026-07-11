package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.visitors;

import java.util.List;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.CentralityState;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.GlobalTraverserSourceState;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.state.NewBlockTraverserState;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.state.TerminalTraverserState;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.state.TraverserBlockInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.state.TraverserState;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.state.UnknownAdvanceStrategyTraverserState;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.ListUtils;

public final class PredecessorBlockTraverserVisitor extends AbstractBlockTraverserVisitor {

	public PredecessorBlockTraverserVisitor(TraverserState state) {
		super(state);
	}

	@Override
	public TraverserState visit(BlockNode block) {
		TraverserState currentState = getState();
		CentralityState centralityState = currentState.getCentralityState();
		GlobalTraverserSourceState globalState = currentState.getGlobalState();

		List<BlockNode> predecessors = block.getPredecessors();
		List<BlockNode> containedPredecessors = ListUtils.filter(predecessors, globalState::isBlockContained);
		int predecessorsCount = containedPredecessors.size();
		switch (predecessorsCount) {
			case 0:
				return new TerminalTraverserState(getComparator(), TerminalTraverserState.TerminationReason.END_OF_PATH);
			case 1:
				BlockNode nextBlock = containedPredecessors.get(0);
				TraverserBlockInfo blockInfo = new TraverserBlockInfo(nextBlock);
				return new NewBlockTraverserState(getComparator(), centralityState, blockInfo);
			default:
				return new UnknownAdvanceStrategyTraverserState(getComparator(), centralityState, containedPredecessors);
		}
	}
}
