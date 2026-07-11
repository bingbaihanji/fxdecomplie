package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes;

import java.util.Collections;
import java.util.List;

import com.bingbaihanji.fxdecomplie.core.jadx.api.JavaField;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.IFieldData;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.NotificationAttrNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.AccessInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.AccessInfo.AFType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.FieldInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.ListUtils;

/**
 * 字段节点，表示 DEX 中的一个字段定义。
 * <p>
 * 继承自 {@link NotificationAttrNode}，实现了 {@link ICodeNode} 和 {@link IFieldInfoRef} 接口，
 * 提供字段的访问标志、类型、使用位置等信息的管理能力。
 */
public class FieldNode extends NotificationAttrNode implements ICodeNode, IFieldInfoRef {

	/** 字段所属的父类节点 */
	private final ClassNode parentClass;
	/** 字段的基本信息（名称、类型描述符、所属类） */
	private final FieldInfo fieldInfo;
	/** 字段的访问标志（public/private/protected/static/final 等） */
	private AccessInfo accFlags;

	/** 字段的类型，可能在分析过程中被更新（例如泛型擦除后的类型推断） */
	private ArgType type;

	/** 使用了该字段的方法列表 */
	private List<MethodNode> useIn = Collections.emptyList();

	/** 对应的 Java 层字段 API 对象 */
	private JavaField javaNode;

	/**
	 * 从原始字段数据构建字段节点。
	 *
	 * @param cls       字段所属的类节点
	 * @param fieldData 原始字段数据
	 * @return 新创建的字段节点
	 */
	public static FieldNode build(ClassNode cls, IFieldData fieldData) {
		FieldInfo fieldInfo = FieldInfo.fromRef(cls.root(), fieldData);
		FieldNode fieldNode = new FieldNode(cls, fieldInfo, fieldData.getAccessFlags());
		fieldNode.addAttrs(fieldData.getAttributes());
		return fieldNode;
	}

	/**
	 * 构造字段节点。
	 *
	 * @param cls         所属类节点
	 * @param fieldInfo   字段信息
	 * @param accessFlags 访问标志原始值
	 */
	public FieldNode(ClassNode cls, FieldInfo fieldInfo, int accessFlags) {
		this.parentClass = cls;
		this.fieldInfo = fieldInfo;
		this.type = fieldInfo.getType();
		this.accFlags = new AccessInfo(accessFlags, AFType.FIELD);
	}

	/** 卸载字段节点的属性，释放内存。 */
	public void unload() {
		unloadAttributes();
	}

	/**
	 * 更新字段的类型（例如在类型推断过程中）。
	 *
	 * @param type 新的类型
	 */
	public void updateType(ArgType type) {
		this.type = type;
	}

	/** 获取字段信息。 */
	@Override
	public FieldInfo getFieldInfo() {
		return fieldInfo;
	}

	/** 获取访问标志。 */
	@Override
	public AccessInfo getAccessFlags() {
		return accFlags;
	}

	/** 设置访问标志。 */
	@Override
	public void setAccessFlags(AccessInfo accFlags) {
		this.accFlags = accFlags;
	}

	/** 判断字段是否为静态字段。 */
	public boolean isStatic() {
		return accFlags.isStatic();
	}

	/** 判断字段是否为实例字段。 */
	public boolean isInstance() {
		return !accFlags.isStatic();
	}

	/** 获取字段名称。 */
	public String getName() {
		return fieldInfo.getName();
	}

	/** 获取字段别名（重命名后的名称），若未重命名则返回原始名称。 */
	public String getAlias() {
		return fieldInfo.getAlias();
	}

	/** 设置字段别名，用于重命名显示。 */
	@Override
	public void rename(String alias) {
		fieldInfo.setAlias(alias);
	}

	/** 获取字段的类型。 */
	public ArgType getType() {
		return type;
	}

	/** 获取声明该字段的类节点。 */
	@Override
	public ClassNode getDeclaringClass() {
		return parentClass;
	}

	/** 获取字段所属的父类节点。 */
	public ClassNode getParentClass() {
		return parentClass;
	}

	/** 获取字段所属的顶层父类节点（对于内部类则返回最外层类）。 */
	public ClassNode getTopParentClass() {
		return parentClass.getTopParentClass();
	}

	/** 获取使用了该字段的方法列表。 */
	@Override
    public List<MethodNode> getUseIn() {
		return useIn;
	}

	/** 设置使用了该字段的方法列表。 */
	public void setUseIn(List<MethodNode> useIn) {
		this.useIn = useIn;
	}

	/**
	 * 线程安全地向使用列表中添加一个方法引用。
	 *
	 * @param mth 使用了该字段的方法
	 */
	public synchronized void addUseIn(MethodNode mth) {
		useIn = ListUtils.safeAdd(useIn, mth);
	}

	/** 返回节点类型名称，固定为 "field"。 */
	@Override
	public String typeName() {
		return "field";
	}

	/** 获取字段所属类对应的输入文件名。 */
	@Override
	public String getInputFileName() {
		return parentClass.getInputFileName();
	}

	/** 获取根节点。 */
	@Override
	public RootNode root() {
		return parentClass.root();
	}

	/** 获取对应的 Java 层字段 API 对象。 */
	public JavaField getJavaNode() {
		return javaNode;
	}

	/** 设置对应的 Java 层字段 API 对象。 */
	public void setJavaNode(JavaField javaNode) {
		this.javaNode = javaNode;
	}

	/** 返回注解类型，固定为 FIELD。 */
	@Override
	public AnnType getAnnType() {
		return AnnType.FIELD;
	}

	@Override
	public int hashCode() {
		return fieldInfo.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		FieldNode other = (FieldNode) obj;
		return fieldInfo.equals(other.fieldInfo);
	}

	@Override
	public String toString() {
		return fieldInfo.getDeclClass() + "." + fieldInfo.getName() + " :" + type;
	}
}
