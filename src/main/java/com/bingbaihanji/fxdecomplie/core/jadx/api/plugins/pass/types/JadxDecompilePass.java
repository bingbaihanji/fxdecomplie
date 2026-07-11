package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass.types;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass.JadxPass;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;

/**
 * 反编译处理通道接口。
 * <p>
 * 实现此接口可在反编译过程中遍历所有类和方法，
 * 对中间表示（IR）进行修改或信息提取。
 */
public interface JadxDecompilePass extends JadxPass {
	JadxPassType TYPE = new JadxPassType("DecompilePass");

	/**
	 * 初始化通道，传入根节点以获取全局上下文。
	 *
	 * @param root 根节点
	 */
	void init(RootNode root);

	/**
	 * 访问类。
	 *
	 * @param cls 当前类节点
	 * @return false 表示禁用子方法和内部类遍历
	 */
	boolean visit(ClassNode cls);

	/**
	 * 访问方法。
	 *
	 * @param mth 当前方法节点
	 */
	void visit(MethodNode mth);

	@Override
	default JadxPassType getPassType() {
		return TYPE;
	}
}
