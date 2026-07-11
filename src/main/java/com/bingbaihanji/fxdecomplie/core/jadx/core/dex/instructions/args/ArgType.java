package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import com.google.errorprone.annotations.Immutable;

import com.bingbaihanji.fxdecomplie.util.JadxConsts;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.ClassInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference.TypeCompareEnum;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.ListUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;

/**
 * 抽象参数类型表示类，用于描述 Dalvik 字节码中寄存器、字段、方法参数和返回值的类型。
 * <p>
 * 采用不可变设计（{@code @Immutable}），通过静态工厂方法创建各类类型的实例。
 * 支持基本类型、对象类型、数组类型、泛型类型、通配符类型和未知类型等多种类型表示。
 * <p>
 * 类型层次结构：
 * <ul>
 *   <li>{@link PrimitiveArg} - 基本类型（int、boolean、long 等）</li>
 *   <li>{@link ObjectType} - 对象类型</li>
 *   <li>{@link GenericType} - 泛型类型参数（如 {@code T}、{@code E extends Comparable}）</li>
 *   <li>{@link GenericObject} - 参数化泛型对象（如 {@code List<String>}）</li>
 *   <li>{@link OuterGenericObject} - 外部泛型对象（如 {@code Outer<T>.Inner}）</li>
 *   <li>{@link WildcardType} - 通配符类型（如 {@code ? extends Number}）</li>
 *   <li>{@link ArrayArg} - 数组类型</li>
 *   <li>{@link UnknownArg} - 未知类型（待类型推断确定）</li>
 * </ul>
 */
@Immutable
public abstract class ArgType {

	// ==================== 基本类型常量 ====================

	/** int 类型 */
	public static final ArgType INT = primitive(PrimitiveType.INT);
	/** boolean 类型 */
	public static final ArgType BOOLEAN = primitive(PrimitiveType.BOOLEAN);
	/** byte 类型 */
	public static final ArgType BYTE = primitive(PrimitiveType.BYTE);
	/** short 类型 */
	public static final ArgType SHORT = primitive(PrimitiveType.SHORT);
	/** char 类型 */
	public static final ArgType CHAR = primitive(PrimitiveType.CHAR);
	/** float 类型 */
	public static final ArgType FLOAT = primitive(PrimitiveType.FLOAT);
	/** double 类型 */
	public static final ArgType DOUBLE = primitive(PrimitiveType.DOUBLE);
	/** long 类型 */
	public static final ArgType LONG = primitive(PrimitiveType.LONG);
	/** void 类型 */
	public static final ArgType VOID = primitive(PrimitiveType.VOID);

	// ==================== 常用对象类型常量 ====================

	/** java.lang.Object 类型 */
	public static final ArgType OBJECT = objectNoCache(JadxConsts.CLASS_OBJECT);
	/** java.lang.Class 类型 */
	public static final ArgType CLASS = objectNoCache(JadxConsts.CLASS_CLASS);
	/** java.lang.String 类型 */
	public static final ArgType STRING = objectNoCache(JadxConsts.CLASS_STRING);
	/** java.lang.Enum 类型 */
	public static final ArgType ENUM = objectNoCache(JadxConsts.CLASS_ENUM);
	/** java.lang.Throwable 类型 */
	public static final ArgType THROWABLE = objectNoCache(JadxConsts.CLASS_THROWABLE);
	/** java.lang.Error 类型 */
	public static final ArgType ERROR = objectNoCache(JadxConsts.CLASS_ERROR);
	/** java.lang.Exception 类型 */
	public static final ArgType EXCEPTION = objectNoCache(JadxConsts.CLASS_EXCEPTION);
	/** java.lang.RuntimeException 类型 */
	public static final ArgType RUNTIME_EXCEPTION = objectNoCache(JadxConsts.CLASS_RUNTIME_EXCEPTION);
	/** Object[] 数组类型 */
	public static final ArgType OBJECT_ARRAY = array(OBJECT);
	/** 无界通配符类型（?） */
	public static final ArgType WILDCARD = wildcard();

	// ==================== 未知类型常量（用于类型推断） ====================

	/** 完全未知类型，可能为任何类型 */
	public static final ArgType UNKNOWN = unknown(PrimitiveType.values());
	/** 未知对象类型（可能是对象或数组） */
	public static final ArgType UNKNOWN_OBJECT = unknown(PrimitiveType.OBJECT, PrimitiveType.ARRAY);
	/** 未知对象类型（不含数组） */
	public static final ArgType UNKNOWN_OBJECT_NO_ARRAY = unknown(PrimitiveType.OBJECT);
	/** 未知数组类型 */
	public static final ArgType UNKNOWN_ARRAY = array(UNKNOWN);

	/** 窄类型：可能为任意窄类型、对象或数组 */
	public static final ArgType NARROW = unknown(
			PrimitiveType.INT, PrimitiveType.FLOAT,
			PrimitiveType.BOOLEAN, PrimitiveType.SHORT, PrimitiveType.BYTE, PrimitiveType.CHAR,
			PrimitiveType.OBJECT, PrimitiveType.ARRAY);

	/** 窄数值类型：可能为 boolean、int、float、short、byte、char */
	public static final ArgType NARROW_NUMBERS = unknown(
			PrimitiveType.BOOLEAN, PrimitiveType.INT, PrimitiveType.FLOAT,
			PrimitiveType.SHORT, PrimitiveType.BYTE, PrimitiveType.CHAR);

	/** 窄整数类型：可能为 int、short、byte、char */
	public static final ArgType NARROW_INTEGRAL = unknown(
			PrimitiveType.INT, PrimitiveType.SHORT, PrimitiveType.BYTE, PrimitiveType.CHAR);

	/** 窄数值类型（不含 boolean）：可能为 int、float、short、byte、char */
	public static final ArgType NARROW_NUMBERS_NO_BOOL = unknown(
			PrimitiveType.INT, PrimitiveType.FLOAT,
			PrimitiveType.SHORT, PrimitiveType.BYTE, PrimitiveType.CHAR);

	/** 窄负数值类型：可能为 int、short、byte、float */
	public static final ArgType NARROW_NEG_NUMBERS = unknown(
			PrimitiveType.INT, PrimitiveType.SHORT, PrimitiveType.BYTE, PrimitiveType.FLOAT);

	/** 窄数值类型（不含 float）：可能为 int、boolean、short、byte、char */
	public static final ArgType NARROW_NUMBERS_NO_FLOAT = unknown(
			PrimitiveType.INT, PrimitiveType.BOOLEAN,
			PrimitiveType.SHORT, PrimitiveType.BYTE, PrimitiveType.CHAR);

	/** 宽类型：可能为 long 或 double */
	public static final ArgType WIDE = unknown(PrimitiveType.LONG, PrimitiveType.DOUBLE);

	/** 可能为 int 或 float */
	public static final ArgType INT_FLOAT = unknown(PrimitiveType.INT, PrimitiveType.FLOAT);
	/** 可能为 int 或 boolean */
	public static final ArgType INT_BOOLEAN = unknown(PrimitiveType.INT, PrimitiveType.BOOLEAN);
	/** 可能为 byte 或 boolean */
	public static final ArgType BYTE_BOOLEAN = unknown(PrimitiveType.BYTE, PrimitiveType.BOOLEAN);

	/** 已确定为 int 类型的未知类型 */
	public static final ArgType UNKNOWN_INT = unknown(PrimitiveType.INT);

	/** 缓存的哈希值，在子类构造时计算，用于加速 equals 与集合查找 */
	protected int hash;

	/**
	 * 创建基本类型实例
	 */
	private static ArgType primitive(PrimitiveType stype) {
		return new PrimitiveArg(stype);
	}

	/**
	 * 创建不走缓存的对象类型实例（用于内部常量初始化，避免循环依赖）
	 */
	private static ArgType objectNoCache(String obj) {
		return new ObjectType(obj);
	}

	/**
	 * 根据类名创建对象类型。常用类型（Object、String、Class、Throwable、Exception）
	 * 会返回预定义的常量实例。
	 *
	 * @param obj 类的内部名称（如 "java/lang/String"）
	 * @return 对象类型实例
	 */
	public static ArgType object(String obj) {
		// 待办：添加缓存
		String cleanObjectName = Utils.cleanObjectName(obj);
		switch (cleanObjectName) {
			case JadxConsts.CLASS_OBJECT:
				return OBJECT;
			case JadxConsts.CLASS_STRING:
				return STRING;
			case JadxConsts.CLASS_CLASS:
				return CLASS;
			case JadxConsts.CLASS_THROWABLE:
				return THROWABLE;
			case JadxConsts.CLASS_EXCEPTION:
				return EXCEPTION;
			default:
				return new ObjectType(cleanObjectName);
		}
	}

	/**
	 * 创建无边界约束的泛型类型参数（如 {@code T}）
	 *
	 * @param type 泛型参数名称
	 * @return 泛型类型实例
	 */
	public static ArgType genericType(String type) {
		return new GenericType(type);
	}

	/**
	 * 创建带单个上界的泛型类型参数（如 {@code T extends Comparable}）
	 */
	public static ArgType genericType(String type, ArgType extendType) {
		return new GenericType(type, extendType);
	}

	/**
	 * 创建带多个上界的泛型类型参数（如 {@code T extends Comparable & Serializable}）
	 */
	public static ArgType genericType(String type, List<ArgType> extendTypes) {
		return new GenericType(type, extendTypes);
	}

	/**
	 * 创建无界通配符类型（{@code ?}）
	 */
	public static ArgType wildcard() {
		return new WildcardType(OBJECT, WildcardBound.UNBOUND);
	}

	/**
	 * 创建带边界的通配符类型（如 {@code ? extends Number} 或 {@code ? super String}）
	 */
	public static ArgType wildcard(ArgType obj, WildcardBound bound) {
		return new WildcardType(obj, bound);
	}

	/**
	 * 创建参数化泛型对象类型（如 {@code List<String>}、{@code Map<K, V>}）
	 *
	 * @param obj      基础对象类型（必须为对象类型）
	 * @param generics 泛型参数列表
	 * @return 泛型对象类型实例
	 */
	public static ArgType generic(ArgType obj, List<ArgType> generics) {
		if (!obj.isObject()) {
			throw new IllegalArgumentException("Expected Object as ArgType, got: " + obj);
		}
		return new GenericObject(obj.getObject(), generics);
	}

	/** 创建参数化泛型对象类型（可变参数形式的泛型参数） */
	public static ArgType generic(ArgType obj, ArgType... generics) {
		return generic(obj, Arrays.asList(generics));
	}

	/** 根据类名创建参数化泛型对象类型 */
	public static ArgType generic(String obj, List<ArgType> generics) {
		return new GenericObject(Utils.cleanObjectName(obj), generics);
	}

	/** 根据类名创建带单个泛型参数的参数化泛型对象类型 */
	public static ArgType generic(String obj, ArgType generic) {
		return generic(obj, Collections.singletonList(generic));
	}

	/** 根据类名创建参数化泛型对象类型（仅用于测试） */
	@TestOnly
	public static ArgType generic(String obj, ArgType... generics) {
		return generic(obj, Arrays.asList(generics));
	}

	/**
	 * 创建外部泛型对象类型（如 {@code Outer<T>.Inner}）
	 */
	public static ArgType outerGeneric(ArgType genericOuterType, ArgType innerType) {
		return new OuterGenericObject((ObjectType) genericOuterType, (ObjectType) innerType);
	}

	/**
	 * 创建一维数组类型（如 {@code int[]}、{@code String[]}）
	 *
	 * @param vtype 数组元素类型
	 * @return 数组类型实例
	 */
	public static ArgType array(@NotNull ArgType vtype) {
		return new ArrayArg(vtype);
	}

	/**
	 * 创建指定维度的数组类型（如 {@code int[][]} 对应 dimension=2）
	 *
	 * @param type      数组元素类型
	 * @param dimension 数组维度
	 * @return 数组类型实例
	 */
	public static ArgType array(@NotNull ArgType type, int dimension) {
		if (dimension == 1) {
			return new ArrayArg(type);
		}
		ArgType arrType = type;
		for (int i = 0; i < dimension; i++) {
			arrType = new ArrayArg(arrType);
		}
		return arrType;
	}

	/**
	 * 创建未知类型，指定可能的类型集合（用于类型推断）
	 *
	 * @param types 可能的类型数组
	 * @return 未知类型实例
	 */
	public static ArgType unknown(PrimitiveType... types) {
		return new UnknownArg(types);
	}

	/**
	 * 已知类型基类。所有类型确定（非未知）的子类继承此类。
	 */
	private abstract static class KnownType extends ArgType {

		private static final PrimitiveType[] EMPTY_POSSIBLES = new PrimitiveType[0];

		@Override
		public boolean isTypeKnown() {
			return true;
		}

		@Override
		public boolean contains(PrimitiveType type) {
			return getPrimitiveType() == type;
		}

		@Override
		public ArgType selectFirst() {
			return null;
		}

		@Override
		public PrimitiveType[] getPossibleTypes() {
			return EMPTY_POSSIBLES;
		}
	}

	/**
	 * 基本类型实现，表示 Java 基本数据类型（int、boolean、long 等）
	 */
	private static final class PrimitiveArg extends KnownType {
		private final PrimitiveType type;

		PrimitiveArg(PrimitiveType type) {
			this.type = type;
			this.hash = type.hashCode();
		}

		@Override
		public PrimitiveType getPrimitiveType() {
			return type;
		}

		@Override
		public boolean isPrimitive() {
			return true;
		}

		@Override
		boolean internalEquals(Object obj) {
			return type == ((PrimitiveArg) obj).type;
		}

		@Override
		public String toString() {
			return type.toString();
		}
	}

	/**
	 * 对象类型实现，表示 Java 引用类型（类、接口等）
	 */
	private static class ObjectType extends KnownType {
		protected final String objName;

		ObjectType(String obj) {
			this.objName = obj;
			this.hash = objName.hashCode();
		}

		@Override
		public String getObject() {
			return objName;
		}

		@Override
		public boolean isObject() {
			return true;
		}

		@Override
		public PrimitiveType getPrimitiveType() {
			return PrimitiveType.OBJECT;
		}

		@Override
		boolean internalEquals(Object obj) {
			return objName.equals(((ObjectType) obj).objName);
		}

		@Override
		public String toString() {
			return objName;
		}
	}

	/**
	 * 泛型类型参数实现，表示未实例化的类型变量（如 {@code T}、{@code E extends Comparable}）
	 */
	private static final class GenericType extends ObjectType {
		private List<ArgType> extendTypes;

		GenericType(String obj) {
			this(obj, Collections.emptyList());
		}

		GenericType(String obj, ArgType extendType) {
			this(obj, Collections.singletonList(extendType));
		}

		GenericType(String obj, List<ArgType> extendTypes) {
			super(obj);
			this.extendTypes = extendTypes;
		}

		@Override
		public boolean isGenericType() {
			return true;
		}

		@Override
		public List<ArgType> getExtendTypes() {
			return extendTypes;
		}

		@Override
		public void setExtendTypes(List<ArgType> extendTypes) {
			this.extendTypes = extendTypes;
		}

		@Override
		boolean internalEquals(Object obj) {
			return super.internalEquals(obj)
					&& extendTypes.equals(((GenericType) obj).extendTypes);
		}

		@Override
		public String toString() {
			List<ArgType> extTypes = this.extendTypes;
			if (extTypes.isEmpty()) {
				return objName;
			}
			return objName + " extends " + Utils.listToString(extTypes, " & ");
		}
	}

	/**
	 * 通配符边界类型枚举
	 */
	public enum WildcardBound {
		EXTENDS(1, "? extends "), // 上界 (? extends A)
		UNBOUND(0, "?"), // 无边界限制 (?)
		SUPER(-1, "? super "); // 下界 (? super A)

		private final int num;
		private final String str;

		WildcardBound(int val, String str) {
			this.num = val;
			this.str = str;
		}

		public int getNum() {
			return num;
		}

		public String getStr() {
			return str;
		}

		public static WildcardBound getByNum(int num) {
			return num == 0 ? UNBOUND : (num == 1 ? EXTENDS : SUPER);
		}
	}

	/**
	 * 通配符类型实现（如 {@code ? extends Number}、{@code ? super String}、{@code ?}）
	 */
	private static final class WildcardType extends ObjectType {
		private final ArgType type;
		private final WildcardBound bound;

		WildcardType(ArgType obj, WildcardBound bound) {
			super(OBJECT.getObject());
			this.type = Objects.requireNonNull(obj);
			this.bound = Objects.requireNonNull(bound);
		}

		@Override
		public boolean isWildcard() {
			return true;
		}

		@Override
		public boolean isGeneric() {
			return true;
		}

		@Override
		public ArgType getWildcardType() {
			return type;
		}

		@Override
		public WildcardBound getWildcardBound() {
			return bound;
		}

		@Override
		boolean internalEquals(Object obj) {
			return super.internalEquals(obj)
					&& bound == ((WildcardType) obj).bound
					&& type.equals(((WildcardType) obj).type);
		}

		@Override
		public String toString() {
			if (bound == WildcardBound.UNBOUND) {
				return bound.getStr();
			}
			return bound.getStr() + type;
		}
	}

	/**
	 * 参数化泛型对象类型实现（如 {@code List<String>}、{@code Map<String, Integer>}）
	 */
	private static class GenericObject extends ObjectType {
		private final List<ArgType> generics;

		GenericObject(String obj, List<ArgType> generics) {
			super(obj);
			this.generics = Objects.requireNonNull(generics);
			this.hash = calcHash();
		}

		private int calcHash() {
			return objName.hashCode() + 31 * generics.hashCode();
		}

		@Override
		public boolean isGeneric() {
			return true;
		}

		@Override
		public List<ArgType> getGenericTypes() {
			return generics;
		}

		@Override
		boolean internalEquals(Object obj) {
			return super.internalEquals(obj)
					&& Objects.equals(generics, ((GenericObject) obj).generics);
		}

		@Override
		public String toString() {
			return super.toString() + '<' + Utils.listToString(generics) + '>';
		}
	}

	/**
	 * 外部泛型对象类型实现，表示内部类持有外部类的泛型参数（如 {@code Outer<T>.Inner}）
	 */
	private static class OuterGenericObject extends ObjectType {
		private final ObjectType outerType;
		private final ObjectType innerType;

		OuterGenericObject(ObjectType outerType, ObjectType innerType) {
			super(outerType.getObject() + '$' + innerType.getObject());
			this.outerType = outerType;
			this.innerType = innerType;
			this.hash = calcHash();
		}

		private int calcHash() {
			return objName.hashCode() + 31 * (outerType.hashCode() + 31 * innerType.hashCode());
		}

		@Override
		public boolean isGeneric() {
			return true;
		}

		@Override
		public List<ArgType> getGenericTypes() {
			return innerType.getGenericTypes();
		}

		@Override
		public ArgType getOuterType() {
			return outerType;
		}

		@Override
		public ArgType getInnerType() {
			return innerType;
		}

		@Override
		boolean internalEquals(Object obj) {
			return super.internalEquals(obj)
					&& Objects.equals(outerType, ((OuterGenericObject) obj).outerType)
					&& Objects.equals(innerType, ((OuterGenericObject) obj).innerType);
		}

		@Override
		public String toString() {
			return outerType.toString() + '$' + innerType.toString();
		}
	}

	/**
	 * 数组类型实现，通过递归嵌套支持多维数组（如 {@code int[][]} 由两层 ArrayArg 嵌套表示）
	 */
	private static final class ArrayArg extends KnownType {
		private static final PrimitiveType[] ARRAY_POSSIBLES = new PrimitiveType[] { PrimitiveType.ARRAY };
		private final ArgType arrayElement;

		ArrayArg(ArgType arrayElement) {
			this.arrayElement = arrayElement;
			this.hash = arrayElement.hashCode();
		}

		@Override
		public ArgType getArrayElement() {
			return arrayElement;
		}

		@Override
		public boolean isArray() {
			return true;
		}

		@Override
		public PrimitiveType getPrimitiveType() {
			return PrimitiveType.ARRAY;
		}

		@Override
		public boolean isTypeKnown() {
			return arrayElement.isTypeKnown();
		}

		@Override
		public ArgType selectFirst() {
			return array(arrayElement.selectFirst());
		}

		@Override
		public PrimitiveType[] getPossibleTypes() {
			return ARRAY_POSSIBLES;
		}

		@Override
		public int getArrayDimension() {
			return 1 + arrayElement.getArrayDimension();
		}

		@Override
		public ArgType getArrayRootElement() {
			return arrayElement.getArrayRootElement();
		}

		@Override
		boolean internalEquals(Object other) {
			ArrayArg otherArr = (ArrayArg) other;
			return this.arrayElement.equals(otherArr.getArrayElement());
		}

		@Override
		public String toString() {
			return arrayElement + "[]";
		}
	}

	/**
	 * 未知类型实现，包含一组可能的类型，用于类型推断阶段。
	 * 当类型推断完成后，应被替换为确定的类型。
	 */
	private static final class UnknownArg extends ArgType {
		private final PrimitiveType[] possibleTypes;

		UnknownArg(PrimitiveType[] types) {
			this.possibleTypes = types;
			this.hash = Arrays.hashCode(possibleTypes);
		}

		@Override
		public PrimitiveType[] getPossibleTypes() {
			return possibleTypes;
		}

		@Override
		public boolean isTypeKnown() {
			return false;
		}

		@Override
		public boolean contains(PrimitiveType type) {
			for (PrimitiveType t : possibleTypes) {
				if (t == type) {
					return true;
				}
			}
			return false;
		}

		@Override
		public ArgType selectFirst() {
			if (contains(PrimitiveType.OBJECT)) {
				return OBJECT;
			}
			if (contains(PrimitiveType.ARRAY)) {
				return array(OBJECT);
			}
			return primitive(possibleTypes[0]);
		}

		@Override
		boolean internalEquals(Object obj) {
			return Arrays.equals(possibleTypes, ((UnknownArg) obj).possibleTypes);
		}

		@Override
		public String toString() {
			if (possibleTypes.length == PrimitiveType.values().length) {
				return "??";
			} else {
				return "??[" + Utils.arrayToStr(possibleTypes) + ']';
			}
		}
	}

	// ==================== 类型查询与访问方法（默认实现，由子类按需重写） ====================

	/** 类型是否已确定（非未知类型） */
	public boolean isTypeKnown() {
		return false;
	}

	/** 获取基本类型标识，非基本类型返回 {@code null} */
	public PrimitiveType getPrimitiveType() {
		return null;
	}

	/** 是否为基本类型 */
	public boolean isPrimitive() {
		return false;
	}

	/** 获取对象类型的内部类名，非对象类型抛出 {@link UnsupportedOperationException} */
	public String getObject() {
		throw new UnsupportedOperationException("ArgType.getObject(), call class: " + this.getClass());
	}

	/** 是否为对象类型 */
	public boolean isObject() {
		return false;
	}

	/** 是否为带泛型信息的类型（参数化泛型对象或通配符） */
	public boolean isGeneric() {
		return false;
	}

	/** 是否为泛型类型参数（类型变量，如 {@code T}） */
	public boolean isGenericType() {
		return false;
	}

	/** 获取泛型参数列表，无泛型时返回 {@code null} */
	public List<ArgType> getGenericTypes() {
		return null;
	}

	/** 获取泛型类型参数的上界列表，无上界时返回空列表 */
	public List<ArgType> getExtendTypes() {
		return Collections.emptyList();
	}

	/** 设置泛型类型参数的上界列表（默认空实现，仅泛型类型参数支持） */
	public void setExtendTypes(List<ArgType> extendTypes) {
	}

	/** 获取通配符所约束的类型，非通配符返回 {@code null} */
	public ArgType getWildcardType() {
		return null;
	}

	/** 获取通配符的边界（上界/下界/无界），非通配符返回 {@code null} */
	public WildcardBound getWildcardBound() {
		return null;
	}

	/** 是否为通配符类型 */
	public boolean isWildcard() {
		return false;
	}

	/** 获取外部泛型对象的外部类类型，不适用时返回 {@code null} */
	public ArgType getOuterType() {
		return null;
	}

	/** 获取外部泛型对象的内部类类型，不适用时返回 {@code null} */
	public ArgType getInnerType() {
		return null;
	}

	/** 是否为数组类型 */
	public boolean isArray() {
		return false;
	}

	/** 获取数组维度，非数组返回 0 */
	public int getArrayDimension() {
		return 0;
	}

	/** 获取数组的元素类型（降低一维），非数组返回 {@code null} */
	public ArgType getArrayElement() {
		return null;
	}

	/** 获取多维数组最底层的根元素类型，非数组返回自身 */
	public ArgType getArrayRootElement() {
		return this;
	}

	/** 判断该类型的可能类型集合是否包含指定的基本类型 */
	public abstract boolean contains(PrimitiveType type);

	/** 从可能的类型集合中选取首选的确定类型 */
	public abstract ArgType selectFirst();

	/** 获取该类型所有可能的基本类型集合（用于类型推断） */
	public abstract PrimitiveType[] getPossibleTypes();

	/**
	 * 判断从 {@code from} 类型转换为 {@code to} 类型时是否需要显式强制类型转换。
	 *
	 * @param root 根节点，提供类型比较能力
	 * @param from 源类型
	 * @param to   目标类型
	 * @return 需要强制转换返回 {@code true}
	 */
	public static boolean isCastNeeded(RootNode root, ArgType from, ArgType to) {
		if (from.equals(to)) {
			return false;
		}
		TypeCompareEnum result = root.getTypeCompare().compareTypes(from, to);
		return !result.isNarrow();
	}

	/**
	 * 判断 {@code type} 是否为 {@code of} 的实例（子类型关系）。
	 *
	 * @param root 根节点，提供类继承关系查询
	 * @param type 待判断类型
	 * @param of   目标类型
	 * @return 是子类型返回 {@code true}
	 */
	public static boolean isInstanceOf(RootNode root, ArgType type, ArgType of) {
		if (type.equals(of)) {
			return true;
		}
		if (!type.isObject() || !of.isObject()) {
			return false;
		}
		return root.getClsp().isImplements(type.getObject(), of.getObject());
	}

	/** 判断给定对象类型对应的类是否在类路径中已知 */
	public static boolean isClsKnown(RootNode root, ArgType cls) {
		if (cls.isObject()) {
			return root.getClsp().isClsKnown(cls.getObject());
		}
		return false;
	}

	/** 该类型是否可能是对象类型（已确定为对象或未知类型中包含对象） */
	public boolean canBeObject() {
		return isObject() || (!isTypeKnown() && contains(PrimitiveType.OBJECT));
	}

	/** 该类型是否可能是数组类型（已确定为数组或未知类型中包含数组） */
	public boolean canBeArray() {
		return isArray() || (!isTypeKnown() && contains(PrimitiveType.ARRAY));
	}

	/** 该类型是否可能是指定的基本类型 */
	public boolean canBePrimitive(PrimitiveType primitiveType) {
		return (isPrimitive() && getPrimitiveType() == primitiveType)
				|| (!isTypeKnown() && contains(primitiveType));
	}

	/** 该类型是否可能是任意数值类型（非对象且非数组的基本类型） */
	public boolean canBeAnyNumber() {
		if (isPrimitive()) {
			return !getPrimitiveType().isObjectOrArray();
		}
		for (PrimitiveType primitiveType : getPossibleTypes()) {
			if (!primitiveType.isObjectOrArray()) {
				return true;
			}
		}
		return false;
	}

	/** 将 {@link PrimitiveType} 枚举转换为对应的 {@link ArgType} 实例 */
	public static ArgType convertFromPrimitiveType(PrimitiveType primitiveType) {
		switch (primitiveType) {
			case BOOLEAN:
				return BOOLEAN;
			case CHAR:
				return CHAR;
			case BYTE:
				return BYTE;
			case SHORT:
				return SHORT;
			case INT:
				return INT;
			case FLOAT:
				return FLOAT;
			case LONG:
				return LONG;
			case DOUBLE:
				return DOUBLE;
			case OBJECT:
				return OBJECT;
			case ARRAY:
				return OBJECT_ARRAY;
			case VOID:
				return ArgType.VOID;
		}
		return OBJECT;
	}

	/**
	 * 解析类型描述符字符串（如 {@code "Ljava/lang/String;"}、{@code "[I"}、{@code "TT;"}）为 {@link ArgType}。
	 *
	 * @param type 类型描述符字符串
	 * @return 解析得到的类型
	 * @throws JadxRuntimeException 当字符串为空或格式无法识别时抛出
	 */
	public static ArgType parse(String type) {
		if (type == null || type.isEmpty()) {
			throw new JadxRuntimeException("Failed to parse type string: " + type);
		}
		char f = type.charAt(0);
		switch (f) {
			case 'L':
				return object(type);
			case 'T':
				return genericType(type.substring(1, type.length() - 1));
			case '[':
				return array(parse(type.substring(1)));
			default:
				if (type.length() != 1) {
					throw new JadxRuntimeException("Unknown type string: \"" + type + '"');
				}
				return parse(f);
		}
	}

	/**
	 * 解析单个基本类型描述符字符（如 {@code 'I'} 对应 int，{@code 'Z'} 对应 boolean）。
	 *
	 * @param f 基本类型描述符字符
	 * @return 对应的基本类型
	 * @throws JadxRuntimeException 当字符无法识别时抛出
	 */
	public static ArgType parse(char f) {
		switch (f) {
			case 'Z':
				return BOOLEAN;
			case 'B':
				return BYTE;
			case 'C':
				return CHAR;
			case 'S':
				return SHORT;
			case 'I':
				return INT;
			case 'J':
				return LONG;
			case 'F':
				return FLOAT;
			case 'D':
				return DOUBLE;
			case 'V':
				return VOID;

			default:
				throw new JadxRuntimeException("Unknown type char: '" + f + "' (0x" + Integer.toHexString(f) + ')');
		}
	}

	/**
	 * 获取该类型占用的寄存器数量：long/double 占 2 个，未知类型占 0 个，其余占 1 个。
	 */
	public int getRegCount() {
		if (isPrimitive()) {
			PrimitiveType type = getPrimitiveType();
			if (type == PrimitiveType.LONG || type == PrimitiveType.DOUBLE) {
				return 2;
			} else {
				return 1;
			}
		}
		if (!isTypeKnown()) {
			return 0;
		}
		return 1;
	}

	/** 该类型或其数组元素是否包含泛型信息 */
	public boolean containsGeneric() {
		if (isGeneric() || isGenericType()) {
			return true;
		}
		if (isArray()) {
			ArgType arrayElement = getArrayElement();
			if (arrayElement != null) {
				return arrayElement.containsGeneric();
			}
		}
		return false;
	}

	/** 递归判断该类型是否包含类型变量（泛型参数、通配符、泛型实参、外部类型或数组元素中的类型变量） */
	public boolean containsTypeVariable() {
		if (isGenericType()) {
			return true;
		}
		ArgType wildcardType = getWildcardType();
		if (wildcardType != null) {
			return wildcardType.containsTypeVariable();
		}
		if (isGeneric()) {
			List<ArgType> genericTypes = getGenericTypes();
			if (genericTypes != null) {
				for (ArgType genericType : genericTypes) {
					if (genericType.containsTypeVariable()) {
						return true;
					}
				}
			}
			ArgType outerType = getOuterType();
			if (outerType != null) {
				return outerType.containsTypeVariable();
			}
			return false;
		}
		if (isArray()) {
			ArgType arrayElement = getArrayElement();
			if (arrayElement != null) {
				return arrayElement.containsTypeVariable();
			}
		}
		return false;
	}

	/** 是否为 void 类型 */
	public boolean isVoid() {
		return isPrimitive() && getPrimitiveType() == PrimitiveType.VOID;
	}

	/**
	 * 递归访问此类型的所有子类型（数组元素、通配符类型、泛型参数）。
	 * 若需提前退出遍历，令 visitor 返回非 null 值即可，该值将作为结果返回。
	 *
	 * @param visitor 类型访问器
	 * @param <R>     返回值类型
	 * @return 首个非 null 的访问结果，若全部返回 null 则为 null
	 */
	@Nullable
	public <R> R visitTypes(Function<ArgType, R> visitor) {
		R r = visitor.apply(this);
		if (r != null) {
			return r;
		}
		if (isArray()) {
			ArgType arrayElement = getArrayElement();
			if (arrayElement != null) {
				return arrayElement.visitTypes(visitor);
			}
		}
		ArgType wildcardType = getWildcardType();
		if (wildcardType != null) {
			R res = wildcardType.visitTypes(visitor);
			if (res != null) {
				return res;
			}
		}
		if (isGeneric()) {
			List<ArgType> genericTypes = getGenericTypes();
			if (genericTypes != null) {
				for (ArgType genericType : genericTypes) {
					R res = genericType.visitTypes(visitor);
					if (res != null) {
						return res;
					}
				}
			}
		}
		return null;
	}

	/**
	 * 尝试将类型中的类名解析为其别名（如反混淆后的重命名），递归处理数组元素、通配符与泛型实参。
	 *
	 * @param root 根节点，提供类信息与别名查询
	 * @param type 待解析的类型
	 * @return 解析别名后的类型，若无别名则返回原类型
	 */
	public static ArgType tryToResolveClassAlias(RootNode root, ArgType type) {
		if (type.isGenericType()) {
			return type;
		}
		if (type.isArray()) {
			ArgType rootType = type.getArrayRootElement();
			ArgType aliasType = tryToResolveClassAlias(root, rootType);
			if (aliasType == rootType) {
				return type;
			}
			return ArgType.array(aliasType, type.getArrayDimension());
		}
		if (type.isObject()) {
			ArgType wildcardType = type.getWildcardType();
			if (wildcardType != null) {
				return new WildcardType(tryToResolveClassAlias(root, wildcardType), type.getWildcardBound());
			}
			ClassInfo clsInfo = ClassInfo.fromName(root, type.getObject());
			ArgType baseType = clsInfo.hasAlias() ? ArgType.object(clsInfo.getAliasFullName()) : type;
			if (!type.isGeneric()) {
				return baseType;
			}
			List<ArgType> genericTypes = type.getGenericTypes();
			if (genericTypes != null) {
				return new GenericObject(baseType.getObject(), tryToResolveClassAlias(root, genericTypes));
			}
		}
		return type;
	}

	/**
	 * 批量将类型列表中的类名解析为别名。
	 *
	 * @param root  根节点，提供类信息与别名查询
	 * @param types 待解析的类型列表
	 * @return 解析别名后的类型列表
	 */
	public static List<ArgType> tryToResolveClassAlias(RootNode root, List<ArgType> types) {
		return ListUtils.map(types, t -> tryToResolveClassAlias(root, t));
	}

	@Override
	public String toString() {
		return "ARG_TYPE";
	}

	@Override
	public int hashCode() {
		return hash;
	}

	/** 由各子类实现的类型内容相等比较（在 {@link #equals(Object)} 完成哈希与类型校验后调用） */
	abstract boolean internalEquals(Object obj);

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (hash != obj.hashCode()) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		return internalEquals(obj);
	}
}
