package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info;

import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.IMethodProto;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.IMethodRef;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.TypeGen;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;

/**
 * 方法信息类，表示 DEX 字节码中的一个方法。
 * 包含方法名称、返回类型、参数类型、所属类等元数据信息。
 * 支持方法别名机制和方法签名生成。
 */
public final class MethodInfo implements Comparable<MethodInfo> {

	/** 方法原始名称 */
	private final String name;
	/** 返回类型 */
	private final ArgType retType;
	/** 参数类型列表 */
	private final List<ArgType> argTypes;
	/** 声明该方法的类信息 */
	private final ClassInfo declClass;
	/** 方法短标识，格式为：方法名(参数签名)返回值签名 */
	private final String shortId;
	/** 原始完整标识，包含类的原始全限定名 */
	private final String rawFullId;
	/** 预计算的哈希值 */
	private final int hash;

	/** 方法别名，用于反混淆后显示的名称 */
	private String alias;

	/**
	 * 构造方法信息对象。
	 *
	 * @param declClass 声明该方法的类信息
	 * @param name      方法名称
	 * @param args      参数类型列表
	 * @param retType   返回类型
	 */
	private MethodInfo(ClassInfo declClass, String name, List<ArgType> args, ArgType retType) {
		this.name = name;
		this.alias = name;
		this.declClass = declClass;
		this.argTypes = args;
		this.retType = retType;
		this.shortId = makeShortId(name, argTypes, retType);
		this.rawFullId = declClass.makeRawFullName() + '.' + shortId;
		this.hash = calcHashCode();
	}

	/**
	 * 从方法引用创建或获取已缓存的方法信息。
	 * 使用唯一ID进行缓存查找，避免重复解析相同方法。
	 *
	 * @param root      根节点
	 * @param methodRef 方法引用
	 * @return 方法信息实例
	 */
	public static MethodInfo fromRef(RootNode root, IMethodRef methodRef) {
		InfoStorage infoStorage = root.getInfoStorage();
		int uniqId = methodRef.getUniqId();
		if (uniqId != 0) {
			MethodInfo prevMth = infoStorage.getByUniqId(uniqId);
			if (prevMth != null) {
				return prevMth;
			}
		}
		methodRef.load();
		ArgType parentClsType = ArgType.parse(methodRef.getParentClassType());
		ClassInfo parentClass = ClassInfo.fromType(root, parentClsType);
		ArgType returnType = ArgType.parse(methodRef.getReturnType());
		List<ArgType> args = Utils.collectionMap(methodRef.getArgTypes(), ArgType::parse);
		MethodInfo newMth = new MethodInfo(parentClass, methodRef.getName(), args, returnType);
		MethodInfo uniqMth = infoStorage.putMethod(newMth);
		if (uniqId != 0) {
			infoStorage.putByUniqId(uniqId, uniqMth);
		}
		return uniqMth;
	}

	/**
	 * 从详细信息创建方法信息。
	 * 用于已知方法各组成部分的场景。
	 *
	 * @param root      根节点
	 * @param declClass 声明类信息
	 * @param name      方法名称
	 * @param args      参数类型列表
	 * @param retType   返回类型
	 * @return 方法信息实例
	 */
	public static MethodInfo fromDetails(RootNode root, ClassInfo declClass, String name, List<ArgType> args, ArgType retType) {
		MethodInfo newMth = new MethodInfo(declClass, name, args, retType);
		return root.getInfoStorage().putMethod(newMth);
	}

	/**
	 * 从方法原型接口创建方法信息。
	 * 解析原型中的参数和返回类型后委托给 {@link #fromDetails}。
	 *
	 * @param root      根节点
	 * @param declClass 声明类信息
	 * @param name      方法名称
	 * @param proto     方法原型接口
	 * @return 方法信息实例
	 */
	public static MethodInfo fromMethodProto(RootNode root, ClassInfo declClass, String name, IMethodProto proto) {
		List<ArgType> args = Utils.collectionMap(proto.getArgTypes(), ArgType::parse);
		ArgType returnType = ArgType.parse(proto.getReturnType());
		return fromDetails(root, declClass, name, args, returnType);
	}

	/**
	 * 生成方法签名字符串（使用原始方法名）。
	 *
	 * @param includeRetType 是否包含返回类型
	 * @return 方法签名字符串
	 */
	public String makeSignature(boolean includeRetType) {
		return makeSignature(false, includeRetType);
	}

	/**
	 * 生成方法签名字符串。
	 *
	 * @param useAlias       是否使用别名替代原始方法名
	 * @param includeRetType 是否包含返回类型
	 * @return 方法签名字符串
	 */
	public String makeSignature(boolean useAlias, boolean includeRetType) {
		return makeShortId(useAlias ? alias : name,
				argTypes,
				includeRetType ? retType : null);
	}

	/**
	 * 生成方法短标识。
	 * 格式为：方法名(参数1签名参数2签名...)返回值签名
	 *
	 * @param name     方法名称
	 * @param argTypes 参数类型列表
	 * @param retType  返回类型，为 null 时省略返回类型签名
	 * @return 方法短标识字符串
	 */
	public static String makeShortId(String name, List<ArgType> argTypes, @Nullable ArgType retType) {
		StringBuilder sb = new StringBuilder();
		sb.append(name);
		sb.append('(');
		for (ArgType arg : argTypes) {
			sb.append(TypeGen.signature(arg));
		}
		sb.append(')');
		if (retType != null) {
			sb.append(TypeGen.signature(retType));
		}
		return sb.toString();
	}

	/**
	 * 判断指定方法是否为当前方法的重载方法。
	 * 重载方法具有相同名称和参数数量，但参数类型不同。
	 *
	 * @param otherMthInfo 待比较的方法信息
	 * @return 如果是重载方法返回 true
	 */
	public boolean isOverloadedBy(MethodInfo otherMthInfo) {
		return argTypes.size() == otherMthInfo.argTypes.size()
				&& name.equals(otherMthInfo.name)
				&& !Objects.equals(this.shortId, otherMthInfo.shortId);
	}

	/** 获取方法原始名称 */
	public String getName() {
		return name;
	}

	/** 获取方法的完整名称，格式为：类全限定名.方法名 */
	public String getFullName() {
		return declClass.getFullName() + '.' + name;
	}

	/** 获取方法的别名完整名称，格式为：类别名全限定名.方法别名 */
	public String getAliasFullName() {
		return declClass.getAliasFullName() + '.' + alias;
	}

	/** 获取方法的完整标识，格式为：类全限定名.方法短标识 */
	public String getFullId() {
		return declClass.getFullName() + '.' + shortId;
	}

	/** 获取原始完整标识，包含类的原始全限定名 */
	public String getRawFullId() {
		return rawFullId;
	}

	/**
	 * 获取方法短标识，包含方法名和签名。
	 * 格式为：方法名(参数签名)返回值签名
	 */
	public String getShortId() {
		return shortId;
	}

	/** 获取声明该方法的类信息 */
	public ClassInfo getDeclClass() {
		return declClass;
	}

	/** 获取方法返回类型 */
	public ArgType getReturnType() {
		return retType;
	}

	/** 获取方法参数类型列表 */
	public List<ArgType> getArgumentsTypes() {
		return argTypes;
	}

	/** 获取方法参数数量 */
	public int getArgsCount() {
		return argTypes.size();
	}

	/** 判断该方法是否为构造方法（方法名为 {@code <init>}） */
	public boolean isConstructor() {
		return "<init>".equals(name);
	}

	/** 判断该方法是否为静态初始化方法（方法名为 {@code <clinit>}） */
	public boolean isClassInit() {
		return "<clinit>".equals(name);
	}

	/** 获取方法别名 */
	public String getAlias() {
		return alias;
	}

	/**
	 * 设置方法别名。
	 *
	 * @param alias 新的别名
	 */
	public void setAlias(String alias) {
		this.alias = alias;
	}

	/** 移除别名，将别名恢复为原始方法名 */
	public void removeAlias() {
		this.alias = name;
	}

	/** 判断当前方法是否设置了不同于原始名称的别名 */
	public boolean hasAlias() {
		return !name.equals(alias);
	}

	/**
	 * 计算方法信息的哈希值。
	 * 基于方法短标识和声明类的哈希值组合计算。
	 *
	 * @return 计算得到的哈希值
	 */
	public int calcHashCode() {
		return shortId.hashCode() + 31 * declClass.hashCode();
	}

	/** 返回预计算的哈希值 */
	@Override
	public int hashCode() {
		return hash;
	}

	/** 判断两个方法信息是否相等（方法短标识与声明类均相同即视为相等） */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof MethodInfo)) {
			return false;
		}
		MethodInfo other = (MethodInfo) obj;
		return shortId.equals(other.shortId)
				&& declClass.equals(other.declClass);
	}

	/**
	 * 比较两个方法信息的顺序。
	 * 先按声明类比较，声明类相同时再按方法短标识比较。
	 */
	@Override
	public int compareTo(MethodInfo other) {
		int clsCmp = declClass.compareTo(other.declClass);
		if (clsCmp != 0) {
			return clsCmp;
		}
		return shortId.compareTo(other.shortId);
	}

	/** 返回方法的可读字符串表示，格式为：类全限定名.方法名(参数类型列表):返回类型 */
	@Override
	public String toString() {
		return declClass.getFullName() + '.' + name
				+ '(' + Utils.listToString(argTypes) + "):" + retType;
	}
}
