package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info;

import java.util.Objects;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.IFieldRef;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.TypeGen;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IFieldInfoRef;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;

/**
 * 字段信息类，表示 DEX 字节码中一个字段的完整描述信息。
 * 包含字段所属类、字段名、字段类型以及可选的别名。
 * 实现了 {@link IFieldInfoRef} 接口，作为字段信息的统一引用。
 */
public final class FieldInfo implements IFieldInfoRef {

	/** 字段所属的类信息 */
	private final ClassInfo declClass;
	/** 字段名称 */
	private final String name;
	/** 字段类型 */
	private final ArgType type;
	/** 字段别名（用于重命名后的显示，默认与字段名相同） */
	private String alias;

	/**
	 * 私有构造方法，通过所属类、字段名和类型创建字段信息实例。
	 *
	 * @param declClass 字段所属的类信息
	 * @param name      字段名称
	 * @param type      字段类型
	 */
	private FieldInfo(ClassInfo declClass, String name, ArgType type) {
		this.declClass = declClass;
		this.name = name;
		this.type = type;
		this.alias = name;
	}

	/**
	 * 根据类信息、字段名和类型创建或获取已有的字段信息实例。
	 * 通过根节点的信息存储进行去重，确保相同字段只存在一个实例。
	 *
	 * @param root      根节点，用于获取信息存储
	 * @param declClass 字段所属的类信息
	 * @param name      字段名称
	 * @param type      字段类型
	 * @return 字段信息实例
	 */
	public static FieldInfo from(RootNode root, ClassInfo declClass, String name, ArgType type) {
		FieldInfo field = new FieldInfo(declClass, name, type);
		return root.getInfoStorage().getField(field);
	}

	/**
	 * 根据字段引用创建或获取已有的字段信息实例。
	 * 从 {@link IFieldRef} 中解析出类信息、字段名和类型。
	 *
	 * @param root     根节点，用于获取信息存储
	 * @param fieldRef 字段引用接口
	 * @return 字段信息实例
	 */
	public static FieldInfo fromRef(RootNode root, IFieldRef fieldRef) {
		ClassInfo declClass = ClassInfo.fromName(root, fieldRef.getParentClassType());
		FieldInfo field = new FieldInfo(declClass, fieldRef.getName(), ArgType.parse(fieldRef.getType()));
		return root.getInfoStorage().getField(field);
	}

	/** 获取字段名称 */
	public String getName() {
		return name;
	}

	/** 获取字段类型 */
	public ArgType getType() {
		return type;
	}

	/** 获取字段所属的类信息 */
	public ClassInfo getDeclClass() {
		return declClass;
	}

	/** 获取字段别名 */
	public String getAlias() {
		return alias;
	}

	/** 设置字段别名 */
	public void setAlias(String alias) {
		this.alias = alias;
	}

	/** 移除字段别名，恢复为原始字段名 */
	public void removeAlias() {
		this.alias = name;
	}

	/** 判断字段是否存在别名（即别名与原始名称不同） */
	public boolean hasAlias() {
		return !Objects.equals(name, alias);
	}

	/**
	 * 获取字段的完整标识符，格式为：全限定类名.字段名:类型签名
	 *
	 * @return 字段完整标识符字符串
	 */
	public String getFullId() {
		return declClass.getFullName() + '.' + name + ':' + TypeGen.signature(type);
	}

	/**
	 * 获取字段的短标识符，格式为：字段名:类型签名（不含类名）
	 *
	 * @return 字段短标识符字符串
	 */
	public String getShortId() {
		return name + ':' + TypeGen.signature(type);
	}

	/**
	 * 获取字段的原始完整标识符（使用原始类型名称，未经过泛型擦除），
	 * 格式为：原始全限定类名.字段名:类型签名
	 *
	 * @return 字段原始完整标识符字符串
	 */
	public String getRawFullId() {
		return declClass.makeRawFullName() + '.' + name + ':' + TypeGen.signature(type);
	}

	/**
	 * 判断另一个字段是否与当前字段具有相同的名称和类型。
	 *
	 * @param other 待比较的字段信息
	 * @return 如果名称和类型都相同则返回 {@code true}
	 */
	public boolean equalsNameAndType(FieldInfo other) {
		return name.equals(other.name) && type.equals(other.type);
	}

	/** 实现 {@link IFieldInfoRef} 接口，返回自身实例 */
	@Override
	public FieldInfo getFieldInfo() {
		return this;
	}

	/**
	 * 判断两个字段信息是否相等。
	 * 当所属类、字段名和字段类型都相同时，认为两个字段相等。
	 */
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		FieldInfo fieldInfo = (FieldInfo) o;
		return name.equals(fieldInfo.name)
				&& type.equals(fieldInfo.type)
				&& declClass.equals(fieldInfo.declClass);
	}

	/** 基于字段名、字段类型和所属类计算哈希码 */
	@Override
	public int hashCode() {
		int result = name.hashCode();
		result = 31 * result + type.hashCode();
		result = 31 * result + declClass.hashCode();
		return result;
	}

	/** 返回字段的可读字符串表示，格式为：所属类.字段名 字段类型 */
	@Override
	public String toString() {
		return declClass + "." + name + ' ' + type;
	}
}
