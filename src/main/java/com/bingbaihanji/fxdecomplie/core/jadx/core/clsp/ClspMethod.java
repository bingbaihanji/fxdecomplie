package com.bingbaihanji.fxdecomplie.core.jadx.core.clsp;

import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.AccessFlags;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.MethodInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IMethodDetails;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;

/**
 * 类路径图中的方法节点。
 * <p>
 * 表示在类路径图上解析到的一个方法，包含方法信息、参数类型、返回类型、
 * 泛型类型参数、异常抛出列表以及访问标志，实现 {@link IMethodDetails}
 * 以提供方法细节，并支持按方法信息排序。
 */
public class ClspMethod implements IMethodDetails, Comparable<ClspMethod> {

	/** 方法信息（名称、所属类、参数描述符等） */
	private final MethodInfo methodInfo;
	/** 方法参数类型列表（可能包含泛型信息） */
	private final List<ArgType> argTypes;
	/** 方法返回类型 */
	private final ArgType returnType;
	/** 方法的泛型类型参数列表 */
	private final List<ArgType> typeParameters;
	/** 方法声明抛出的异常类型列表 */
	private final List<ArgType> throwList;
	/** 方法的访问标志 */
	private final int accFlags;

	/**
	 * 构造一个类路径方法节点。
	 *
	 * @param methodInfo     方法信息
	 * @param argTypes       参数类型列表
	 * @param returnType     返回类型
	 * @param typeParameters 泛型类型参数列表
	 * @param throwList      异常抛出列表
	 * @param accFlags       访问标志
	 */
	public ClspMethod(MethodInfo methodInfo,
			List<ArgType> argTypes, ArgType returnType,
			List<ArgType> typeParameters, List<ArgType> throwList, int accFlags) {
		this.methodInfo = methodInfo;
		this.argTypes = argTypes;
		this.returnType = returnType;
		this.typeParameters = typeParameters;
		this.throwList = throwList;
		this.accFlags = accFlags;
	}

	/** 获取方法信息 */
	@Override
	public MethodInfo getMethodInfo() {
		return methodInfo;
	}

	/** 获取方法返回类型 */
	@Override
	public ArgType getReturnType() {
		return returnType;
	}

	/** 获取方法参数类型列表 */
	@Override
	public List<ArgType> getArgTypes() {
		return argTypes;
	}

	/** 判断参数类型是否包含泛型信息（与原始参数类型不同则为 true） */
	public boolean containsGenericArgs() {
		return !Objects.equals(argTypes, methodInfo.getArgumentsTypes());
	}

	/** 获取参数个数 */
	public int getArgsCount() {
		return argTypes.size();
	}

	/** 获取方法的泛型类型参数列表 */
	@Override
	public List<ArgType> getTypeParameters() {
		return typeParameters;
	}

	/** 获取方法声明抛出的异常类型列表 */
	@Override
	public List<ArgType> getThrows() {
		return throwList;
	}

	/** 判断该方法是否为可变参数方法 */
	@Override
	public boolean isVarArg() {
		return (accFlags & AccessFlags.VARARGS) != 0;
	}

	/** 获取原始访问标志 */
	@Override
	public int getRawAccessFlags() {
		return accFlags;
	}

	/** 基于方法信息判断两个方法节点是否相等 */
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ClspMethod)) {
			return false;
		}
		ClspMethod other = (ClspMethod) o;
		return methodInfo.equals(other.methodInfo);
	}

	/** 基于方法信息计算哈希值 */
	@Override
	public int hashCode() {
		return methodInfo.hashCode();
	}

	/** 按方法信息进行比较排序 */
	@Override
	public int compareTo(@NotNull ClspMethod other) {
		return this.methodInfo.compareTo(other.methodInfo);
	}

	@Override
	public String toAttrString() {
		return IMethodDetails.super.toAttrString() + " (c)";
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("ClspMth{");
		if (Utils.notEmpty(getTypeParameters())) {
			sb.append('<');
			sb.append(Utils.listToString(getTypeParameters()));
			sb.append("> ");
		}
		sb.append(getMethodInfo().getFullName());
		sb.append('(');
		sb.append(Utils.listToString(getArgTypes()));
		sb.append("):");
		sb.append(getReturnType());
		if (isVarArg()) {
			sb.append(" VARARG");
		}
		List<ArgType> throwsList = getThrows();
		if (Utils.notEmpty(throwsList)) {
			sb.append(" throws ").append(Utils.listToString(throwsList));
		}
		sb.append('}');
		return sb.toString();
	}
}
