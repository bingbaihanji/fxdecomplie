package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.*;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.trycatch.ExceptionHandler;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import com.bingbaihanji.fxdecomplie.util.collection.ListUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 区域的深度优先遍历工具类
 * <p>
 * 提供对方法区域树（{@link IRegion} / {@link IContainer} / {@link IBlock}）的多种遍历方式：
 * 普通遍历、部分遍历（可提前返回结果）以及迭代式遍历（可重复直到访问者不再要求重复）
 */
public class DepthRegionTraversal {

    /** 迭代式遍历次数上限的倍率因子（上限 = 该值 * 基本块数量） */
    private static final int ITERATIVE_LIMIT_MULTIPLIER = 5;
    /** 用于标记离开某个区域的哨兵容器 */
    private static final IContainer LEAVE_REGION_MARK = new InsnContainer(Collections.emptyList());

    private DepthRegionTraversal() {
    }

    /** 从方法的根区域开始遍历 */
    public static void traverse(MethodNode mth, IRegionVisitor visitor) {
        traverseInternal(mth, visitor, mth.getRegion());
    }

    /** 从指定容器开始遍历 */
    public static void traverse(MethodNode mth, IContainer container, IRegionVisitor visitor) {
        traverseInternal(mth, visitor, container);
    }

    /** 从方法根区域开始部分遍历，访问者返回非空结果时立即终止并返回该结果 */
    public static <R> @Nullable R traversePartial(MethodNode mth, IRegionPartialVisitor<R> visitor) {
        return traversePartialInternal(mth, visitor, mth.getRegion());
    }

    /** 从指定容器开始部分遍历，访问者返回非空结果时立即终止并返回该结果 */
    public static <R> @Nullable R traversePartial(MethodNode mth, IContainer container, IRegionPartialVisitor<R> visitor) {
        return traversePartialInternal(mth, visitor, container);
    }

    /**
     * 迭代式遍历：反复遍历方法区域，直到访问者不再要求重复 
     * 超过次数上限时抛出 {@link JadxRuntimeException}
     */
    public static void traverseIterative(MethodNode mth, IRegionIterativeVisitor visitor) {
        boolean repeat;
        int k = 0;
        int limit = ITERATIVE_LIMIT_MULTIPLIER * mth.getBasicBlocks().size();
        do {
            repeat = traverseIterativeStepInternal(mth, visitor, mth.getRegion());
            if (k++ > limit) {
                throw new JadxRuntimeException("Iterative traversal limit reached: "
                        + "limit: " + limit + ", visitor: " + visitor.getClass().getName()
                        + ", blocks count: " + mth.getBasicBlocks().size());
            }
        } while (repeat);
    }

    /**
     * 迭代式遍历（包含异常处理器区域）：先遍历方法区域，若无需重复再依次遍历各异常处理器区域，
     * 直到访问者不再要求重复 超过次数上限时抛出 {@link JadxRuntimeException}
     */
    public static void traverseIncludingExcHandlers(MethodNode mth, IRegionIterativeVisitor visitor) {
        boolean repeat;
        int k = 0;
        int limit = ITERATIVE_LIMIT_MULTIPLIER * mth.getBasicBlocks().size();
        do {
            repeat = traverseIterativeStepInternal(mth, visitor, mth.getRegion());
            if (!repeat) {
                for (ExceptionHandler h : mth.getExceptionHandlers()) {
                    repeat = traverseIterativeStepInternal(mth, visitor, h.getHandlerRegion());
                    if (repeat) {
                        break;
                    }
                }
            }
            if (k++ > limit) {
                throw new JadxRuntimeException("Iterative traversal limit reached: "
                        + "limit: " + limit + ", visitor: " + visitor.getClass().getName()
                        + ", blocks count: " + mth.getBasicBlocks().size());
            }
        } while (repeat);
    }

    private static void traverseInternal(MethodNode mth, IRegionVisitor visitor, IContainer startContainer) {
        List<IContainer> stack = new ArrayList<>();
        List<IRegion> regionLeaveStack = new ArrayList<>();
        stack.add(startContainer);
        while (true) {
            IContainer current = ListUtils.removeLast(stack);
            if (current == null) {
                return;
            }
            if (current == LEAVE_REGION_MARK) {
                IRegion region = ListUtils.removeLast(regionLeaveStack);
                visitor.leaveRegion(mth, Objects.requireNonNull(region));
            } else if (current instanceof IBlock) {
                visitor.processBlock(mth, (IBlock) current);
            } else if (current instanceof IRegion) {
                IRegion region = (IRegion) current;
                boolean visitRegion = visitor.enterRegion(mth, region);
                stack.add(LEAVE_REGION_MARK);
                regionLeaveStack.add(region);
                if (visitRegion) {
                    addSubBlocksToStack(stack, region);
                }
            }
        }
    }

    private static <R> @Nullable R traversePartialInternal(MethodNode mth, IRegionPartialVisitor<R> visitor, IContainer startContainer) {
        List<IContainer> stack = new ArrayList<>();
        stack.add(startContainer);
        while (true) {
            IContainer current = ListUtils.removeLast(stack);
            if (current == null) {
                return null;
            }
            R result = visitor.visit(mth, current);
            if (result != null) {
                return result;
            }
            if (current instanceof IRegion) {
                addSubBlocksToStack(stack, (IRegion) current);
            }
        }
    }

    private static void addSubBlocksToStack(List<IContainer> stack, IRegion region) {
        List<IContainer> subBlocks = region.getSubBlocks();
        // 以逆序压栈，保证访问时保持原有顺序
        for (int i = subBlocks.size() - 1; i >= 0; i--) {
            stack.add(subBlocks.get(i));
        }
    }

    private static boolean traverseIterativeStepInternal(MethodNode mth, IRegionIterativeVisitor visitor, IRegion startRegion) {
        List<IRegion> stack = new ArrayList<>();
        stack.add(startRegion);
        while (true) {
            IRegion region = ListUtils.removeLast(stack);
            if (region == null) {
                return false;
            }
            if (visitor.visitRegion(mth, region)) {
                return true;
            }
            List<IContainer> subBlocks = region.getSubBlocks();
            // 以逆序压栈，保证访问时保持原有顺序
            for (int i = subBlocks.size() - 1; i >= 0; i--) {
                IContainer subBlock = subBlocks.get(i);
                if (subBlock instanceof IRegion) {
                    stack.add((IRegion) subBlock);
                }
            }
        }
    }
}
