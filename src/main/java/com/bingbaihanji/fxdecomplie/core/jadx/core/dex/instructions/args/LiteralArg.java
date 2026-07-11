package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args;

import org.jetbrains.annotations.Nullable;

import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.TypeGen;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.StringUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;

/**
 * 字面量指令参数。
 * 以 long 存储原始位值，配合类型（如整型、浮点型、布尔型等）解释其含义。
 */
public final class LiteralArg extends InsnArg {

	/** 创建字面量参数（不修正类型） */
	public static LiteralArg make(long value, ArgType type) {
		return new LiteralArg(value, type);
	}

	/** 创建字面量参数，并根据值修正为合适的类型 */
	public static LiteralArg makeWithFixedType(long value, ArgType type) {
		return new LiteralArg(value, fixLiteralType(value, type));
	}

	/** 根据字面量值推断并修正其类型（用于类型未知的窄类型场景） */
	private static ArgType fixLiteralType(long value, ArgType type) {
		if (value == 0 || type.isTypeKnown() || type.contains(PrimitiveType.LONG) || type.contains(PrimitiveType.DOUBLE)) {
			return type;
		}
		if (value == 1) {
			return ArgType.NARROW_NUMBERS;
		}
		if (value < 0) {
			return ArgType.NARROW_NEG_NUMBERS;
		}
		return ArgType.NARROW_NUMBERS_NO_BOOL;
	}

	/** 创建布尔字面量 false */
	public static LiteralArg litFalse() {
		return new LiteralArg(0, ArgType.BOOLEAN);
	}

	/** 创建布尔字面量 true */
	public static LiteralArg litTrue() {
		return new LiteralArg(1, ArgType.BOOLEAN);
	}

	/** 字面量的原始位值 */
	private final long literal;

	private LiteralArg(long value, ArgType type) {
		if (value != 0 && type.isObject()) {
			throw new JadxRuntimeException("Wrong literal type: " + type + " for value: " + value);
		}
		this.literal = value;
		this.type = type;
	}

	/** 获取字面量的原始位值 */
	public long getLiteral() {
		return literal;
	}

	@Override
	public void setType(ArgType type) {
		super.setType(type);
	}

	@Override
	public boolean isLiteral() {
		return true;
	}

	@Override
	public boolean isZeroLiteral() {
		return literal == 0;
	}

	/** 是否为整型字面量（int/byte/char/short/long） */
	public boolean isInteger() {
		switch (type.getPrimitiveType()) {
			case INT:
			case BYTE:
			case CHAR:
			case SHORT:
			case LONG:
				return true;
			default:
				return false;
		}
	}

	/** 是否为负数（整型、浮点型或双精度型的负值） */
	public boolean isNegative() {
		if (isInteger()) {
			return literal < 0;
		}
		if (type == ArgType.FLOAT) {
			float val = Float.intBitsToFloat(((int) literal));
			return val < 0 && Float.isFinite(val);
		}
		if (type == ArgType.DOUBLE) {
			double val = Double.longBitsToDouble(literal);
			return val < 0 && Double.isFinite(val);
		}
		return false;
	}

	/** 返回取负后的字面量；若类型不支持取负则返回 null */
	@Nullable
	public LiteralArg negate() {
		long neg;
		if (isInteger()) {
			neg = -literal;
		} else if (type == ArgType.FLOAT) {
			float val = Float.intBitsToFloat(((int) literal));
			neg = Float.floatToIntBits(-val);
		} else if (type == ArgType.DOUBLE) {
			double val = Double.longBitsToDouble(literal);
			neg = Double.doubleToLongBits(-val);
		} else {
			return null;
		}
		return new LiteralArg(neg, type);
	}

	@Override
	public InsnArg duplicate() {
		return copyCommonParams(new LiteralArg(literal, type));
	}

	@Override
	public int hashCode() {
		return (int) (literal ^ literal >>> 32) + 31 * getType().hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		LiteralArg that = (LiteralArg) o;
		return literal == that.literal && getType().equals(that.getType());
	}

	@Override
	public String toShortString() {
		return Long.toString(literal);
	}

	@Override
	public String toString() {
		try {
			String value = TypeGen.literalToString(literal, getType(), StringUtils.getInstance(), true, false);
			if (getType().equals(ArgType.BOOLEAN) && ("true".equals(value) || "false".equals(value))) {
				return value;
			}
			return '(' + value + ' ' + type + ')';
		} catch (JadxRuntimeException ex) {
			// 无法将字面量转换为字符串
			return "(" + literal + ' ' + type + ')';
		}
	}
}
