package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.regions;

import java.util.ArrayList;
import java.util.List;

import com.bingbaihanji.fxdecomplie.core.jadx.api.ICodeWriter;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.RegionGen;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IContainer;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IRegion;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.CodegenException;

/**
 * 通用区域节点，用于表示反编译过程中的代码块序列。
 * <p>
 * Region 是最基础的区域容器，内部维护一个有序的子容器列表（blocks），
 * 按顺序存放多个 {@link IContainer} 子块，最终通过 {@link #generate} 方法
 * 依次生成对应的反编译代码。
 * </p>
 */
public final class Region extends AbstractRegion {

	/** 子容器列表，按顺序存储该区域包含的各个代码块 */
	private final List<IContainer> blocks;

	/**
	 * 构造一个新的 Region 实例。
	 *
	 * @param parent 父级区域节点
	 */
	public Region(IRegion parent) {
		super(parent);
		this.blocks = new ArrayList<>(1);
	}

	/**
	 * 获取该区域包含的所有子容器块列表。
	 *
	 * @return 子容器的不可变引用列表
	 */
	@Override
	public List<IContainer> getSubBlocks() {
		return blocks;
	}

	/**
	 * 向该区域末尾添加一个子容器块，并将其父级设置为当前 Region。
	 *
	 * @param region 要添加的子容器
	 */
	public void add(IContainer region) {
		updateParent(region, this);
		blocks.add(region);
	}

	/**
	 * 依次为该区域中的每个子容器块生成反编译代码。
	 * <p>
	 * 遍历所有子容器，委托 {@link RegionGen#makeRegion} 逐个生成代码输出。
	 * </p>
	 *
	 * @param regionGen 区域代码生成器
	 * @param code      代码输出写入器
	 * @throws CodegenException 代码生成过程中发生错误时抛出
	 */
	@Override
	public void generate(RegionGen regionGen, ICodeWriter code) throws CodegenException {
		for (IContainer c : blocks) {
			regionGen.makeRegion(code, c);
		}
	}

	/**
	 * 将该区域中的指定子容器替换为新的容器。
	 * <p>
	 * 查找 oldBlock 在子容器列表中的位置，若存在则替换为 newBlock
	 * 并更新新容器的父级引用。
	 * </p>
	 *
	 * @param oldBlock 要被替换的旧子容器
	 * @param newBlock 替换后的新子容器
	 * @return 如果成功替换返回 {@code true}，未找到旧子容器则返回 {@code false}
	 */
	@Override
	public boolean replaceSubBlock(IContainer oldBlock, IContainer newBlock) {
		int i = blocks.indexOf(oldBlock);
		if (i != -1) {
			blocks.set(i, newBlock);
			updateParent(newBlock, this);
			return true;
		}
		return false;
	}

	/**
	 * 生成该区域的唯一标识字符串，格式为 {@code (块数量:子块1|子块2|...)}。
	 * <p>
	 * 用于调试和 toString 实现中的唯一性标识。
	 * </p>
	 *
	 * @return 区域的唯一标识字符串
	 */
	@Override
	public String baseString() {
		StringBuilder sb = new StringBuilder();
		int size = blocks.size();
		sb.append('(');
		sb.append(size);
		if (size > 0) {
			sb.append(':');
			Utils.listToString(sb, blocks, "|", IContainer::baseString);
		}
		sb.append(')');
		return sb.toString();
	}

	/**
	 * 返回该 Region 的字符串表示，格式为 {@code R(块数量:子块1|子块2|...)}。
	 *
	 * @return 以 'R' 为前缀的区域字符串表示
	 */
	@Override
	public String toString() {
		return 'R' + baseString();
	}
}
