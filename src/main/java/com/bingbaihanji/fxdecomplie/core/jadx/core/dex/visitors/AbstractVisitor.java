package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;

/**
 * 访问器抽象基类。
 * <p>
 * 为 {@link IDexTreeVisitor} 提供空实现（no-op），子类只需重写自己关心的方法即可，
 * 无需实现全部接口方法。
 */
public abstract class AbstractVisitor implements IDexTreeVisitor {

	/**
	 * 初始化访问器。默认空实现，子类可按需重写。
	 *
	 * @param root 根节点
	 */
	@Override
	public void init(RootNode root) throws JadxException {
		// 空实现
	}

	/**
	 * 访问类节点。默认空实现并返回 {@code true} 表示继续遍历其方法。
	 *
	 * @param cls 待访问的类节点
	 * @return 是否继续访问该类中的方法
	 */
	@Override
	public boolean visit(ClassNode cls) throws JadxException {
		// 空实现
		return true;
	}

	/**
	 * 访问方法节点。默认空实现，子类可按需重写。
	 *
	 * @param mth 待访问的方法节点
	 */
	@Override
	public void visit(MethodNode mth) throws JadxException {
		// 空实现
	}

	/**
	 * 获取访问器名称，默认返回当前类的简单类名。
	 *
	 * @return 访问器名称
	 */
	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public String toString() {
		return getName();
	}
}
