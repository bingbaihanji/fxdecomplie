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

public class FieldNode extends NotificationAttrNode implements ICodeNode, IFieldInfoRef {

	private final ClassNode parentClass;
	private final FieldInfo fieldInfo;
	private AccessInfo accFlags;

	private ArgType type;

	private List<MethodNode> useIn = Collections.emptyList();

	private JavaField javaNode;

	public static FieldNode build(ClassNode cls, IFieldData fieldData) {
		FieldInfo fieldInfo = FieldInfo.fromRef(cls.root(), fieldData);
		FieldNode fieldNode = new FieldNode(cls, fieldInfo, fieldData.getAccessFlags());
		fieldNode.addAttrs(fieldData.getAttributes());
		return fieldNode;
	}

	public FieldNode(ClassNode cls, FieldInfo fieldInfo, int accessFlags) {
		this.parentClass = cls;
		this.fieldInfo = fieldInfo;
		this.type = fieldInfo.getType();
		this.accFlags = new AccessInfo(accessFlags, AFType.FIELD);
	}

	public void unload() {
		unloadAttributes();
	}

	public void updateType(ArgType type) {
		this.type = type;
	}

	@Override
	public FieldInfo getFieldInfo() {
		return fieldInfo;
	}

	@Override
	public AccessInfo getAccessFlags() {
		return accFlags;
	}

	@Override
	public void setAccessFlags(AccessInfo accFlags) {
		this.accFlags = accFlags;
	}

	public boolean isStatic() {
		return accFlags.isStatic();
	}

	public boolean isInstance() {
		return !accFlags.isStatic();
	}

	public String getName() {
		return fieldInfo.getName();
	}

	public String getAlias() {
		return fieldInfo.getAlias();
	}

	@Override
	public void rename(String alias) {
		fieldInfo.setAlias(alias);
	}

	public ArgType getType() {
		return type;
	}

	@Override
	public ClassNode getDeclaringClass() {
		return parentClass;
	}

	public ClassNode getParentClass() {
		return parentClass;
	}

	public ClassNode getTopParentClass() {
		return parentClass.getTopParentClass();
	}

	@Override
    public List<MethodNode> getUseIn() {
		return useIn;
	}

	public void setUseIn(List<MethodNode> useIn) {
		this.useIn = useIn;
	}

	public synchronized void addUseIn(MethodNode mth) {
		useIn = ListUtils.safeAdd(useIn, mth);
	}

	@Override
	public String typeName() {
		return "field";
	}

	@Override
	public String getInputFileName() {
		return parentClass.getInputFileName();
	}

	@Override
	public RootNode root() {
		return parentClass.root();
	}

	public JavaField getJavaNode() {
		return javaNode;
	}

	public void setJavaNode(JavaField javaNode) {
		this.javaNode = javaNode;
	}

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
