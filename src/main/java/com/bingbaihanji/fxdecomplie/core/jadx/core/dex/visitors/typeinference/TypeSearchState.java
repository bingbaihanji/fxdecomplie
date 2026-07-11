package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.InsnArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.RegisterArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.SSAVar;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;

/**
 * 类型搜索状态。
 * <p>
 * 在类型推断的搜索过程中，维护方法内每个 SSA 变量（{@link SSAVar}）
 * 到其类型搜索信息（{@link TypeSearchVarInfo}）的映射，
 * 并提供按解析状态查询变量的能力。
 */
public class TypeSearchState {

	/** SSA 变量到其类型搜索信息的映射 */
	private final Map<SSAVar, TypeSearchVarInfo> varInfoMap;

	/**
	 * 构造类型搜索状态，为方法内所有 SSA 变量初始化类型搜索信息。
	 *
	 * @param mth 目标方法节点
	 */
	public TypeSearchState(MethodNode mth) {
		List<SSAVar> vars = mth.getSVars();
		this.varInfoMap = new LinkedHashMap<>(vars.size());
		for (SSAVar var : vars) {
			varInfoMap.put(var, new TypeSearchVarInfo(var));
		}
	}

	/**
	 * 获取指定 SSA 变量的类型搜索信息。
	 *
	 * @param var SSA 变量
	 * @return 对应的类型搜索信息
	 * @throws JadxRuntimeException 当映射中不存在该变量时抛出
	 */
	@NotNull
	public TypeSearchVarInfo getVarInfo(SSAVar var) {
		TypeSearchVarInfo varInfo = this.varInfoMap.get(var);
		if (varInfo == null) {
			throw new JadxRuntimeException("TypeSearchVarInfo not found in map for var: " + var);
		}
		return varInfo;
	}

	/**
	 * 获取指令参数当前的类型。
	 * <p>
	 * 如果参数是寄存器，则返回其 SSA 变量当前的类型；否则返回参数自身的类型。
	 *
	 * @param arg 指令参数
	 * @return 参数当前的类型
	 */
	public ArgType getArgType(InsnArg arg) {
		if (arg.isRegister()) {
			RegisterArg reg = (RegisterArg) arg;
			return getVarInfo(reg.getSVar()).getCurrentType();
		}
		return arg.getType();
	}

	/**
	 * 获取所有变量的类型搜索信息。
	 *
	 * @return 所有变量信息的列表
	 */
	public List<TypeSearchVarInfo> getAllVars() {
		return new ArrayList<>(varInfoMap.values());
	}

	/**
	 * 获取所有类型尚未解析的变量。
	 *
	 * @return 未解析类型的变量信息列表
	 */
	public List<TypeSearchVarInfo> getUnresolvedVars() {
		return varInfoMap.values().stream()
				.filter(varInfo -> !varInfo.isTypeResolved())
				.collect(Collectors.toList());
	}

	/**
	 * 获取所有类型已解析的变量。
	 *
	 * @return 已解析类型的变量信息列表
	 */
	public List<TypeSearchVarInfo> getResolvedVars() {
		return varInfoMap.values().stream()
				.filter(TypeSearchVarInfo::isTypeResolved)
				.collect(Collectors.toList());
	}
}
