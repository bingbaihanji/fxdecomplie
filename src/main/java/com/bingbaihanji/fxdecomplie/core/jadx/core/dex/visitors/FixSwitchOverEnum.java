package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import org.jetbrains.annotations.Nullable;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.EnumClassAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.EnumMapAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.RegionRefAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.AccessInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.FieldInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.MethodInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.IndexInsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.InsnType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.InvokeNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.SwitchInsn;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.InsnArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.InsnWrapArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.LiteralArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.FieldNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.regions.SwitchRegion;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.shrink.CodeShrinkVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;

@JadxVisitor(
		name = "FixSwitchOverEnum",
		desc = "Simplify synthetic code in switch over enum",
		runAfter = {
				CodeShrinkVisitor.class,
				EnumVisitor.class
		}
)
public class FixSwitchOverEnum extends AbstractVisitor {

	@Override
	public boolean visit(ClassNode cls) throws JadxException {
		initClsEnumMap(cls);
		return true;
	}

	@Override
	public void visit(MethodNode mth) throws JadxException {
		if (mth.isNoCode()) {
			return;
		}
		boolean changed = false;
		for (BlockNode block : mth.getBasicBlocks()) {
			for (InsnNode insn : block.getInstructions()) {
				if (insn.getType() == InsnType.SWITCH && !insn.contains(AFlag.REMOVE)) {
					changed |= processEnumSwitch(mth, (SwitchInsn) insn);
				}
			}
		}
		if (changed) {
			CodeShrinkVisitor.shrinkMethod(mth);
		}
	}

	private static boolean processEnumSwitch(MethodNode mth, SwitchInsn insn) {
		InsnArg arg = insn.getArg(0);
		if (!arg.isInsnWrap()) {
			return false;
		}
		InsnNode wrapInsn = ((InsnWrapArg) arg).getWrapInsn();
		switch (wrapInsn.getType()) {
			case AGET:
				return processRemappedEnumSwitch(mth, insn, wrapInsn, arg);
			case INVOKE:
				return processDirectEnumSwitch(mth, insn, (InvokeNode) wrapInsn, arg);
		}
		return false;
	}

	private static boolean executeReplace(SwitchInsn swInsn, InsnArg arg, InsnArg invVar, IntFunction<Object> caseReplace) {
		RegionRefAttr regionRefAttr = swInsn.get(AType.REGION_REF);
		if (regionRefAttr == null) {
			return false;
		}
		if (!swInsn.replaceArg(arg, invVar)) {
			return false;
		}
		Map<Object, Object> replaceMap = new HashMap<>();
		int caseCount = swInsn.getKeys().length;
		for (int i = 0; i < caseCount; i++) {
			Object key = swInsn.getKey(i);
			Object replaceObj = caseReplace.apply(i);
			swInsn.modifyKey(i, replaceObj);
			replaceMap.put(key, replaceObj);
		}
		SwitchRegion region = (SwitchRegion) regionRefAttr.getRegion();
		for (SwitchRegion.CaseInfo caseInfo : region.getCases()) {
			caseInfo.getKeys().replaceAll(key -> Utils.getOrElse(replaceMap.get(key), key));
		}
		return true;
	}

	private static boolean processDirectEnumSwitch(MethodNode mth, SwitchInsn swInsn, InvokeNode invInsn, InsnArg arg) {
		MethodInfo callMth = invInsn.getCallMth();
		if (!"ordinal()I".equals(callMth.getShortId())) {
			return false;
		}
		InsnArg invVar = invInsn.getArg(0);
		ClassNode enumCls = mth.root().resolveClass(invVar.getType());
		if (enumCls == null) {
			return false;
		}
		EnumClassAttr enumClassAttr = enumCls.get(AType.ENUM_CLASS);
		if (enumClassAttr == null) {
			return false;
		}
		FieldNode[] casesReplaceArr = mapToCases(swInsn, enumClassAttr.getFields());
		if (casesReplaceArr == null) {
			return false;
		}
		return executeReplace(swInsn, arg, invVar, i -> casesReplaceArr[i]);
	}

	private static @Nullable FieldNode[] mapToCases(SwitchInsn swInsn, List<EnumClassAttr.EnumField> fields) {
		int caseCount = swInsn.getKeys().length;
		if (fields.size() < caseCount) {
			return null;
		}
		FieldNode[] casesMap = new FieldNode[caseCount];
		for (int i = 0; i < caseCount; i++) {
			Object key = swInsn.getKey(i);
			if (key instanceof Integer) {
				int ordinal = (Integer) key;
				try {
					casesMap[ordinal] = fields.get(ordinal).getField();
				} catch (Exception e) {
					return null;
				}
			} else {
				return null;
			}
		}
		return casesMap;
	}

	private static boolean processRemappedEnumSwitch(MethodNode mth, SwitchInsn insn, InsnNode wrapInsn, InsnArg arg) {
		EnumMapInfo enumMapInfo = checkEnumMapAccess(mth.root(), wrapInsn);
		if (enumMapInfo == null) {
			return false;
		}
		FieldNode enumMapField = enumMapInfo.getMapField();
		InsnArg invArg = enumMapInfo.getArg();

		EnumMapAttr.KeyValueMap valueMap = getEnumMap(enumMapField);
		if (valueMap == null) {
			return false;
		}
		int caseCount = insn.getKeys().length;
		for (int i = 0; i < caseCount; i++) {
			Object key = insn.getKey(i);
			Object newKey = valueMap.get(key);
			if (newKey == null) {
				return false;
			}
		}
		if (executeReplace(insn, arg, invArg, i -> valueMap.get(insn.getKey(i)))) {
			enumMapField.add(AFlag.DONT_GENERATE);
			checkAndHideClass(enumMapField.getParentClass());
			return true;
		}
		return false;
	}

	private static void initClsEnumMap(ClassNode enumCls) {
		MethodNode clsInitMth = enumCls.getClassInitMth();
		if (clsInitMth == null || clsInitMth.isNoCode() || clsInitMth.getBasicBlocks() == null) {
			return;
		}
		EnumMapAttr mapAttr = new EnumMapAttr();
		for (BlockNode block : clsInitMth.getBasicBlocks()) {
			for (InsnNode insn : block.getInstructions()) {
				if (insn.getType() == InsnType.APUT) {
					addToEnumMap(enumCls.root(), mapAttr, insn);
				}
			}
		}
		if (!mapAttr.isEmpty()) {
			enumCls.addAttr(mapAttr);
		}
	}

	private static @Nullable EnumMapAttr.KeyValueMap getEnumMap(FieldNode field) {
		ClassNode syntheticClass = field.getParentClass();
		EnumMapAttr mapAttr = syntheticClass.get(AType.ENUM_MAP);
		if (mapAttr == null) {
			return null;
		}
		return mapAttr.getMap(field);
	}

	private static void addToEnumMap(RootNode root, EnumMapAttr mapAttr, InsnNode aputInsn) {
		InsnArg litArg = aputInsn.getArg(2);
		if (!litArg.isLiteral()) {
			return;
		}
		EnumMapInfo mapInfo = checkEnumMapAccess(root, aputInsn);
		if (mapInfo == null) {
			return;
		}
		InsnArg enumArg = mapInfo.getArg();
		FieldNode field = mapInfo.getMapField();
		if (field == null || !enumArg.isInsnWrap()) {
			return;
		}
		InsnNode sget = ((InsnWrapArg) enumArg).getWrapInsn();
		if (!(sget instanceof IndexInsnNode)) {
			return;
		}
		Object index = ((IndexInsnNode) sget).getIndex();
		if (!(index instanceof FieldInfo)) {
			return;
		}
		FieldNode fieldNode = root.resolveField((FieldInfo) index);
		if (fieldNode == null) {
			return;
		}
		int literal = (int) ((LiteralArg) litArg).getLiteral();
		mapAttr.add(field, literal, fieldNode);
	}

	private static @Nullable EnumMapInfo checkEnumMapAccess(RootNode root, InsnNode checkInsn) {
		InsnArg sgetArg = checkInsn.getArg(0);
		InsnArg invArg = checkInsn.getArg(1);
		if (!sgetArg.isInsnWrap() || !invArg.isInsnWrap()) {
			return null;
		}
		InsnNode invInsn = ((InsnWrapArg) invArg).getWrapInsn();
		InsnNode sgetInsn = ((InsnWrapArg) sgetArg).getWrapInsn();
		if (invInsn.getType() != InsnType.INVOKE || sgetInsn.getType() != InsnType.SGET) {
			return null;
		}
		InvokeNode inv = (InvokeNode) invInsn;
		if (!"ordinal()I".equals(inv.getCallMth().getShortId())) {
			return null;
		}
		ClassNode enumCls = root.resolveClass(inv.getCallMth().getDeclClass());
		if (enumCls == null || !enumCls.isEnum()) {
			return null;
		}
		Object index = ((IndexInsnNode) sgetInsn).getIndex();
		if (!(index instanceof FieldInfo)) {
			return null;
		}
		FieldNode enumMapField = root.resolveField((FieldInfo) index);
		if (enumMapField == null || !enumMapField.getAccessFlags().isSynthetic()) {
			return null;
		}
		return new EnumMapInfo(inv.getArg(0), enumMapField);
	}

	/**
	 * If all static final synthetic fields have DONT_GENERATE => hide whole class
	 */
	private static void checkAndHideClass(ClassNode cls) {
		for (FieldNode field : cls.getFields()) {
			AccessInfo af = field.getAccessFlags();
			if (af.isSynthetic() && af.isStatic() && af.isFinal()
					&& !field.contains(AFlag.DONT_GENERATE)) {
				return;
			}
		}
		cls.add(AFlag.DONT_GENERATE);
	}

	private static class EnumMapInfo {
		private final InsnArg arg;
		private final FieldNode mapField;

		public EnumMapInfo(InsnArg arg, FieldNode mapField) {
			this.arg = arg;
			this.mapField = mapField;
		}

		public InsnArg getArg() {
			return arg;
		}

		public FieldNode getMapField() {
			return mapField;
		}
	}
}
