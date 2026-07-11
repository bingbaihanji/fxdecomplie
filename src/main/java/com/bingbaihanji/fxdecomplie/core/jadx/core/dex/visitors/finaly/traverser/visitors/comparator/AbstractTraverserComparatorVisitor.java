package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.visitors.comparator;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.traverser.state.TraverserActivePathState;

/**
 * 遍历器比较访问者的抽象基类。
 * <p>
 * 定义了在遍历活跃路径状态时执行比较操作的标准访问者接口，
 * 子类通过实现 visit 方法提供具体的比较逻辑。
 * </p>
 */
public abstract class AbstractTraverserComparatorVisitor {

	/**
	 * 访问给定的遍历活跃路径状态并执行比较处理。
	 *
	 * @param state 当前的遍历活跃路径状态
	 * @return 处理后的遍历活跃路径状态
	 */
	public abstract TraverserActivePathState visit(TraverserActivePathState state);
}
