package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.state;

import org.jetbrains.annotations.Nullable;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.CentralityState;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.factory.TraverserStateFactory;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.handlers.AbstractBlockPathTraverserHandler;

/**
 * 终端遍历状态，表示遍历器已到达终止状态，不再继续处理。
 * <p>
 * 该状态由 {@link TerminationReason} 枚举标记具体的终止原因。当遍历器进入此状态后，
 * {@link #isTerminal()} 返回 {@code true}，且 {@link #getNextHandler()} 返回 {@code null}，
 * 表示没有后续处理器需要执行。
 * </p>
 */
public final class TerminalTraverserState extends TraverserState {

	/**
	 * 创建终端状态对应的状态工厂。
	 *
	 * @param terminationReason 终止原因
	 * @return 终端状态工厂实例
	 */
	public static TraverserStateFactory<TerminalTraverserState> getFactory(TerminationReason terminationReason) {
		return new TerminalStateFactory(terminationReason);
	}

	/**
	 * 遍历终止原因枚举。
	 */
	public enum TerminationReason {
		/**
		 * 在比较 finally 块与候选块中的指令时，发现不匹配的指令，
		 * 导致遍历器终止。
		 */
		NON_MATCHING_INSTRUCTIONS,

		/**
		 * 在比较 finally 块与候选块时，发现路径不匹配，
		 * 导致遍历器终止。
		 */
		NON_MATCHING_PATHS,

		/**
		 * 当某个处理器被请求查找基本块的前驱节点时，
		 * 在作用域内未找到任何前驱节点，表示已到达路径终点。
		 */
		END_OF_PATH,
		/**
		 * 当某个处理器被请求处理基本块时，
		 * 发现该处理器的缓存结果已存在，直接使用缓存而终止进一步处理。
		 */
		USING_CACHED_RESULTS,

		/**
		 * 多个处理器产生的状态无法合并为一个一致的状态，
		 * 导致遍历器终止。
		 */
		UNMERGEABLE_STATE,

		/**
		 * 遍历过程中出现了无法解析的状态冲突，
		 * 导致遍历器终止。
		 */
		UNRESOLVABLE_STATES,
	}

	private static class TerminalStateFactory extends TraverserStateFactory<TerminalTraverserState> {
		private final TerminationReason terminationReason;

		public TerminalStateFactory(TerminationReason terminationReason) {
			this.terminationReason = terminationReason;
		}

		@Override
		public TerminalTraverserState generateInternalState(TraverserActivePathState state) {
			return new TerminalTraverserState(state, terminationReason);
		}
	}

	/** 该终端状态对应的终止原因 */
	private final TerminationReason terminationReason;

	/**
	 * 构造终端遍历状态。
	 *
	 * @param state             底层的活动路径状态
	 * @param terminationReason 终止原因
	 */
	public TerminalTraverserState(TraverserActivePathState state, TerminationReason terminationReason) {
		super(state);
		this.terminationReason = terminationReason;
	}

	/**
	 * 判断当前状态是否为终端状态。
	 *
	 * @return 始终返回 {@code true}，因为这是终端状态
	 */
	@Override
	public boolean isTerminal() {
		return true;
	}

	/**
	 * 获取下一个待执行的处理器。
	 *
	 * @return 始终返回 {@code null}，终端状态没有后续处理器
	 */
	@Override
	public @Nullable AbstractBlockPathTraverserHandler getNextHandler() {
		return null;
	}

	/**
	 * 获取该终端状态的终止原因。
	 *
	 * @return 终止原因
	 */
	public TerminationReason getTerminationReason() {
		return terminationReason;
	}

	/**
	 * 获取比较状态。
	 *
	 * @return 始终返回 {@link ComparisonState#NOT_READY}，终端状态不参与比较
	 */
	@Override
	public ComparisonState getCompareState() {
		return ComparisonState.NOT_READY;
	}

	@Override
	protected @Nullable CentralityState getUnderlyingCentralityState() {
		return null;
	}

	@Override
	protected @Nullable TraverserBlockInfo getUnderlyingBlockInsnInfo() {
		return null;
	}

	@Override
	protected TraverserState duplicateInternalState(TraverserActivePathState comparatorState) {
		return new TerminalTraverserState(comparatorState, terminationReason);
	}
}
