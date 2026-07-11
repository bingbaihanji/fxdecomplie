package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.state;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.CentralityState;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.factory.TraverserStateFactory;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.handlers.AbstractBlockTraverserHandler;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.handlers.MergePathActivePathTraverserHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 已识别作用域（含终止节点）的遍历状态
 * <p>
 * 该状态表示遍历器已确定了一个明确的作用域，包含若干根节点（{@code roots}）和一个作用域终止节点
 * （{@code scopeTerminator}）处于此状态时，遍历器已准备好进行比较，
 * {@link #getCompareState()} 返回 {@link ComparisonState#READY_TO_COMPARE}，
 * 并通过 {@link MergePathActivePathTraverserHandler} 合并路径继续处理
 * </p>
 */
public final class IdentifiedScopeWithTerminatorTraverserState extends TraverserState {

    /** 中心性状态 */
    private final CentralityState centralityState;
    /** 作用域内的根基本块列表 */
    private final List<BlockNode> roots;
    /** 作用域的终止基本块 */
    private final BlockNode scopeTerminator;

    /**
     * 构造已识别作用域（含终止节点）的遍历状态
     *
     * @param state           底层的活动路径状态
     * @param centralityState 中心性状态
     * @param roots           作用域内的根基本块列表
     * @param scopeTerminator 作用域的终止基本块
     */
    public IdentifiedScopeWithTerminatorTraverserState(TraverserActivePathState state, CentralityState centralityState,
                                                       List<BlockNode> roots, BlockNode scopeTerminator) {
        super(state);
        this.roots = roots;
        this.scopeTerminator = scopeTerminator;
        this.centralityState = centralityState;
    }

    /**
     * 创建该状态对应的状态工厂
     *
     * @param centralityState 中心性状态
     * @param roots           作用域内的根基本块列表
     * @param scopeTerminator 作用域的终止基本块
     * @return 状态工厂实例
     */
    public static TraverserStateFactory<IdentifiedScopeWithTerminatorTraverserState> getFactory(CentralityState centralityState,
                                                                                                List<BlockNode> roots, BlockNode scopeTerminator) {
        return new IdentifiedScopeWithTerminatorStateFactory(centralityState, roots, scopeTerminator);
    }

    /**
     * 获取下一个待执行的处理器
     *
     * @return 用于合并路径的 {@link MergePathActivePathTraverserHandler} 处理器
     */
    @Override
    public @Nullable AbstractBlockTraverserHandler getNextHandler() {
        return new MergePathActivePathTraverserHandler(getComparatorState());
    }

    /**
     * 获取比较状态
     *
     * @return 始终返回 {@link ComparisonState#READY_TO_COMPARE}，表示已准备好比较
     */
    @Override
    public ComparisonState getCompareState() {
        return ComparisonState.READY_TO_COMPARE;
    }

    /**
     * 判断当前状态是否为终端状态
     *
     * @return 始终返回 {@code false}，该状态非终端状态
     */
    @Override
    public boolean isTerminal() {
        return false;
    }

    @Override
    protected @Nullable CentralityState getUnderlyingCentralityState() {
        return centralityState;
    }

    @Override
    protected @Nullable TraverserBlockInfo getUnderlyingBlockInsnInfo() {
        return null;
    }

    @Override
    protected TraverserState duplicateInternalState(TraverserActivePathState comparatorState) {
        return new IdentifiedScopeWithTerminatorTraverserState(comparatorState, centralityState, roots, scopeTerminator);
    }

    /**
     * 获取作用域的终止基本块
     *
     * @return 作用域终止节点
     */
    public BlockNode getTerminus() {
        return scopeTerminator;
    }

    /**
     * 获取作用域内的根基本块列表
     *
     * @return 根基本块列表
     */
    public List<BlockNode> getRoots() {
        return roots;
    }

    private static final class IdentifiedScopeWithTerminatorStateFactory
            extends TraverserStateFactory<IdentifiedScopeWithTerminatorTraverserState> {

        private final CentralityState centralityState;
        private final List<BlockNode> roots;
        private final BlockNode scopeTerminator;

        public IdentifiedScopeWithTerminatorStateFactory(CentralityState centralityState, List<BlockNode> roots,
                                                         BlockNode scopeTerminator) {
            this.centralityState = centralityState;
            this.roots = roots;
            this.scopeTerminator = scopeTerminator;
        }

        @Override
        public IdentifiedScopeWithTerminatorTraverserState generateInternalState(TraverserActivePathState state) {
            return new IdentifiedScopeWithTerminatorTraverserState(state, centralityState, roots, scopeTerminator);
        }
    }
}
