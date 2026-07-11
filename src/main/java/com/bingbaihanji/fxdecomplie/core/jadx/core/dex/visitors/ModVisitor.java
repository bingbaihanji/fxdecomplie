package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.annotations.AnnotationVisibility;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.annotations.EncodedType;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.annotations.EncodedValue;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.annotations.IAnnotation;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.JadxAttrType;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.types.AnnotationMethodParamsAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.types.AnnotationsAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AttrNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.SkipMethodArgsAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.AccessInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.FieldInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.ArithNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.ConstClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.ConstStringNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.FillArrayInsn;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.FilledNewArrayNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.IfNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.IfOp;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.IndexInsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.InsnType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.NewArrayNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.SwitchInsn;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.InsnArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.InsnWrapArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.LiteralArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.NamedArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.RegisterArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.SSAVar;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.mods.ConstructorInsn;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.mods.TernaryInsn;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.FieldNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IFieldInfoRef;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IMethodDetails;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.regions.conditions.IfCondition;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.trycatch.ExcHandlerAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.trycatch.ExceptionHandler;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions.variables.ProcessVariables;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.shrink.CodeShrinkVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference.TypeCompareEnum;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.InsnRemover;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.InsnUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;

import static com.bingbaihanji.fxdecomplie.core.jadx.core.utils.BlockUtils.replaceInsn;
import static com.bingbaihanji.fxdecomplie.core.jadx.core.utils.ListUtils.allMatch;

/**
 * 方法指令修改访问器。
 * <p>
 * 负责对方法内的指令进行修改（删除、替换、处理异常处理器等），
 * 例如：将常量替换为常量字段引用、内联 CMP 指令、移除多余的类型转换、
 * 将 new-array + fill-array 合并为填充数组指令、处理 move-exception 等。
 * 该访问器在 {@link CodeShrinkVisitor} 与 {@link ProcessVariables} 之前运行。
 */
@JadxVisitor(
		name = "ModVisitor",
		desc = "Modify method instructions",
		runBefore = {
				CodeShrinkVisitor.class,
				ProcessVariables.class
		}
)
public class ModVisitor extends AbstractVisitor {
	private static final Logger LOG = LoggerFactory.getLogger(ModVisitor.class);

	private static final long DOUBLE_TO_BITS = Double.doubleToLongBits(1);
	private static final long FLOAT_TO_BITS = Float.floatToIntBits(1);

	@Override
	public boolean visit(ClassNode cls) throws JadxException {
		replaceConstInAnnotations(cls);
		return true;
	}

	@Override
	public void visit(MethodNode mth) {
		if (mth.isNoCode()) {
			return;
		}
		InsnRemover remover = new InsnRemover(mth);
		replaceStep(mth, remover);
		removeStep(mth, remover);
		iterativeRemoveStep(mth);
	}

	private static void replaceStep(MethodNode mth, InsnRemover remover) {
		ClassNode parentClass = mth.getParentClass();
		for (BlockNode block : mth.getBasicBlocks()) {
			remover.setBlock(block);
			List<InsnNode> insnsList = block.getInstructions();
			int size = insnsList.size();
			for (int i = 0; i < size; i++) {
				InsnNode insn = insnsList.get(i);
				switch (insn.getType()) {
					case CONSTRUCTOR:
						processAnonymousConstructor(mth, ((ConstructorInsn) insn));
						break;

					case CONST:
					case CONST_STR:
					case CONST_CLASS:
						replaceConst(mth, parentClass, block, i, insn);
						break;

					case SWITCH:
						replaceConstKeys(mth, parentClass, (SwitchInsn) insn);
						break;

					case NEW_ARRAY:
						// 如果下一条指令是 'fill-array'，则替换为已填充的数组
						NewArrayNode newArrInsn = (NewArrayNode) insn;
						InsnNode nextInsn = getFirstUseSkipMove(insn.getResult());
						if (nextInsn != null && nextInsn.getType() == InsnType.FILL_ARRAY) {
							FillArrayInsn fillArrInsn = (FillArrayInsn) nextInsn;
							if (checkArrSizes(mth, newArrInsn, fillArrInsn)) {
								InsnNode filledArr = makeFilledArrayInsn(mth, newArrInsn, fillArrInsn);
								replaceInsn(mth, block, i, filledArr);
								remover.addAndUnbind(nextInsn);
							}
						}
						break;

					case MOVE_EXCEPTION:
						processMoveException(mth, block, insn, remover);
						break;

					case ARITH:
						processArith(mth, parentClass, (ArithNode) insn);
						break;

					case CMP_L:
					case CMP_G:
						inlineCMPInsns(mth, block, i, insn, remover);
						break;

					case CHECK_CAST:
						removeCheckCast(mth, block, i, (IndexInsnNode) insn);
						break;

					case CAST:
						fixPrimitiveCast(mth, block, i, insn);
						break;

					case IPUT:
					case IGET:
						fixFieldUsage(mth, (IndexInsnNode) insn);
						break;

					default:
						break;
				}
			}
			remover.perform();
		}
	}

	/**
	 * 如果字段在使用处不可见，则将实例转换（cast）为其声明所在的原始类。
	 */
	private static void fixFieldUsage(MethodNode mth, IndexInsnNode insn) {
		InsnArg instanceArg = insn.getArg(insn.getType() == InsnType.IGET ? 0 : 1);
		if (instanceArg.contains(AFlag.SUPER)) {
			return;
		}
		if (instanceArg.isInsnWrap() && ((InsnWrapArg) instanceArg).getWrapInsn().getType() == InsnType.CAST) {
			return;
		}
		FieldInfo fieldInfo = (FieldInfo) insn.getIndex();
		ArgType clsType = fieldInfo.getDeclClass().getType();
		ArgType instanceType = instanceArg.getType();
		if (Objects.equals(clsType, instanceType)) {
			// 无需转换
			return;
		}

		FieldNode fieldNode = mth.root().resolveField(fieldInfo);
		if (fieldNode == null) {
			// 未知字段
			TypeCompareEnum result = mth.root().getTypeCompare().compareTypes(instanceType, clsType);
			if (result.isEqual() || (result == TypeCompareEnum.NARROW_BY_GENERIC && !instanceType.isGenericType())) {
				return;
			}
		} else if (isFieldVisibleInMethod(fieldNode, mth)) {
			return;
		}
		// 插入类型转换
		IndexInsnNode castInsn = new IndexInsnNode(InsnType.CAST, clsType, 1);
		castInsn.addArg(instanceArg.duplicate());
		castInsn.add(AFlag.SYNTHETIC);
		castInsn.add(AFlag.EXPLICIT_CAST);

		InsnArg castArg = InsnArg.wrapInsnIntoArg(castInsn);
		castArg.setType(clsType);
		insn.replaceArg(instanceArg, castArg);
		InsnRemover.unbindArgUsage(mth, instanceArg);
	}

	private static boolean isFieldVisibleInMethod(FieldNode field, MethodNode mth) {
		AccessInfo accessFlags = field.getAccessFlags();
		if (accessFlags.isPublic()) {
			return true;
		}
		ClassNode useCls = mth.getParentClass();
		ClassNode fieldCls = field.getParentClass();
		boolean sameScope = Objects.equals(useCls, fieldCls) && !mth.getAccessFlags().isStatic();
		if (sameScope) {
			return true;
		}
		if (accessFlags.isPrivate()) {
			return false;
		}
		// 包级私有或 protected
		if (Objects.equals(useCls.getClassInfo().getPackage(), fieldCls.getClassInfo().getPackage())) {
			// 同一个包
			return true;
		}
		if (accessFlags.isPackagePrivate()) {
			return false;
		}
		// protected
		TypeCompareEnum result = mth.root().getTypeCompare().compareTypes(useCls, fieldCls);
		return result == TypeCompareEnum.NARROW; // 若使用类是字段所在类的子类则返回 true
	}

	private static void replaceConstKeys(MethodNode mth, ClassNode parentClass, SwitchInsn insn) {
		int[] keys = insn.getKeys();
		int len = keys.length;
		for (int k = 0; k < len; k++) {
			IFieldInfoRef f = parentClass.getConstField(keys[k]);
			if (f != null) {
				insn.modifyKey(k, f);
				addFieldUsage(f, mth);
			}
		}
	}

	private static void fixPrimitiveCast(MethodNode mth, BlockNode block, int i, InsnNode insn) {
		// 将 boolean 到 (byte/char/short/long/double/float) 的转换替换为三元表达式
		InsnArg castArg = insn.getArg(0);
		if (castArg.getType() == ArgType.BOOLEAN) {
			ArgType type = insn.getResult().getType();
			if (type.isPrimitive()) {
				TernaryInsn ternary = makeBooleanConvertInsn(insn.getResult(), castArg, type);
				replaceInsn(mth, block, i, ternary);
			}
		}
	}

	public static TernaryInsn makeBooleanConvertInsn(RegisterArg result, InsnArg castArg, ArgType type) {
		InsnArg zero = LiteralArg.make(0, type);
		long litVal = 1;
		if (type == ArgType.DOUBLE) {
			litVal = DOUBLE_TO_BITS;
		} else if (type == ArgType.FLOAT) {
			litVal = FLOAT_TO_BITS;
		}
		InsnArg one = LiteralArg.make(litVal, type);

		IfNode ifNode = new IfNode(IfOp.EQ, -1, castArg, LiteralArg.litTrue());
		IfCondition condition = IfCondition.fromIfNode(ifNode);
		return new TernaryInsn(condition, result, one, zero);
	}

	private void replaceConstInAnnotations(ClassNode cls) {
		if (cls.root().getArgs().isReplaceConsts()) {
			replaceConstsInAnnotationForAttrNode(cls, cls);
			cls.getFields().forEach(f -> replaceConstsInAnnotationForAttrNode(cls, f));
			cls.getMethods().forEach((m) -> {
				replaceConstsInAnnotationForAttrNode(cls, m);
				replaceConstsInAnnotationForMethodParamsAttr(cls, m);
			});
		}
	}

	private void replaceConstsInAnnotationForMethodParamsAttr(ClassNode cls, MethodNode m) {
		AnnotationMethodParamsAttr paramsAnnotation = m.get(JadxAttrType.ANNOTATION_MTH_PARAMETERS);
		if (paramsAnnotation == null) {
			return;
		}
		paramsAnnotation.getParamList().forEach(annotationsList -> replaceConstsInAnnotationsAttr(cls, annotationsList));
	}

	private void replaceConstsInAnnotationForAttrNode(ClassNode parentCls, AttrNode attrNode) {
		AnnotationsAttr annotationsList = attrNode.get(JadxAttrType.ANNOTATION_LIST);
		replaceConstsInAnnotationsAttr(parentCls, annotationsList);
	}

	private void replaceConstsInAnnotationsAttr(ClassNode parentCls, AnnotationsAttr annotationsList) {
		if (annotationsList == null) {
			return;
		}
		for (IAnnotation annotation : annotationsList.getAll()) {
			if (annotation.getVisibility() == AnnotationVisibility.SYSTEM) {
				continue;
			}
			for (Map.Entry<String, EncodedValue> entry : annotation.getValues().entrySet()) {
				entry.setValue(replaceConstValue(parentCls, entry.getValue()));
			}
		}
	}

	@SuppressWarnings("unchecked")
	private EncodedValue replaceConstValue(ClassNode parentCls, EncodedValue encodedValue) {
		if (encodedValue.getType() == EncodedType.ENCODED_ANNOTATION) {
			IAnnotation annotation = (IAnnotation) encodedValue.getValue();
			for (Map.Entry<String, EncodedValue> entry : annotation.getValues().entrySet()) {
				entry.setValue(replaceConstValue(parentCls, entry.getValue()));
			}
			return encodedValue;
		}
		if (encodedValue.getType() == EncodedType.ENCODED_ARRAY) {
			List<EncodedValue> listVal = (List<EncodedValue>) encodedValue.getValue();
			if (!listVal.isEmpty()) {
				listVal.replaceAll(v -> replaceConstValue(parentCls, v));
			}
			return new EncodedValue(EncodedType.ENCODED_ARRAY, listVal);
		}
		IFieldInfoRef constField = parentCls.getConstField(encodedValue.getValue());
		if (constField != null) {
			return new EncodedValue(EncodedType.ENCODED_FIELD, constField.getFieldInfo());
		}
		return encodedValue;
	}

	private static void replaceConst(MethodNode mth, ClassNode parentClass, BlockNode block, int i, InsnNode insn) {
		IFieldInfoRef f;
		if (insn.getType() == InsnType.CONST_STR) {
			String s = ((ConstStringNode) insn).getString();
			f = parentClass.getConstField(s);
		} else if (insn.getType() == InsnType.CONST_CLASS) {
			ArgType t = ((ConstClassNode) insn).getClsType();
			f = parentClass.getConstField(t);
		} else {
			f = parentClass.getConstFieldByLiteralArg((LiteralArg) insn.getArg(0));
		}
		if (f != null) {
			InsnNode inode = new IndexInsnNode(InsnType.SGET, f.getFieldInfo(), 0);
			inode.setResult(insn.getResult());
			replaceInsn(mth, block, i, inode);
			addFieldUsage(f, mth);
		}
	}

	private static void processArith(MethodNode mth, ClassNode parentClass, ArithNode arithNode) {
		if (arithNode.getArgsCount() != 2) {
			throw new JadxRuntimeException("Invalid args count in insn: " + arithNode);
		}
		InsnArg litArg = arithNode.getArg(1);
		if (litArg.isLiteral()) {
			IFieldInfoRef f = parentClass.getConstFieldByLiteralArg((LiteralArg) litArg);
			if (f != null) {
				InsnNode fGet = new IndexInsnNode(InsnType.SGET, f.getFieldInfo(), 0);
				if (arithNode.replaceArg(litArg, InsnArg.wrapArg(fGet))) {
					addFieldUsage(f, mth);
				}
			}
		}
	}

	/**
	 * 将 CMP 指令内联到 'if' 指令中，以便于条件合并。
	 */
	private static void inlineCMPInsns(MethodNode mth, BlockNode block, int i, InsnNode insn, InsnRemover remover) {
		RegisterArg resArg = insn.getResult();
		List<RegisterArg> useList = resArg.getSVar().getUseList();
		if (allMatch(useList, use -> InsnUtils.isInsnType(use.getParentInsn(), InsnType.IF))) {
			for (RegisterArg useArg : new ArrayList<>(useList)) {
				InsnNode useInsn = useArg.getParentInsn();
				if (useInsn != null) {
					InsnArg wrapArg = InsnArg.wrapInsnIntoArg(insn.copyWithoutResult());
					if (!useInsn.replaceArg(useArg, wrapArg)) {
						mth.addWarnComment("Failed to inline CMP insn: " + insn + " into " + useInsn);
						return;
					}
				}
			}
			remover.addAndUnbind(insn);
		}
	}

	private static boolean checkArrSizes(MethodNode mth, NewArrayNode newArrInsn, FillArrayInsn fillArrInsn) {
		int dataSize = fillArrInsn.getSize();
		InsnArg arrSizeArg = newArrInsn.getArg(0);
		Object value = InsnUtils.getConstValueByArg(mth.root(), arrSizeArg);
		if (value instanceof LiteralArg) {
			long literal = ((LiteralArg) value).getLiteral();
			return dataSize == (int) literal;
		}
		return false;
	}

	private static void removeCheckCast(MethodNode mth, BlockNode block, int i, IndexInsnNode insn) {
		InsnArg castArg = insn.getArg(0);
		if (castArg.isZeroLiteral()) {
			// 对 'null' 始终保留类型转换
			insn.add(AFlag.EXPLICIT_CAST);
			return;
		}
		ArgType castType = (ArgType) insn.getIndex();
		if (!ArgType.isCastNeeded(mth.root(), castArg.getType(), castType)) {
			RegisterArg result = insn.getResult();
			result.setType(castArg.getType());

			InsnNode move = new InsnNode(InsnType.MOVE, 1);
			move.setResult(result);
			move.addArg(castArg);
			replaceInsn(mth, block, i, move);
			return;
		}
		InsnNode prevCast = isCastDuplicate(insn);
		if (prevCast != null) {
			// 将前一个类型转换替换为 move 指令
			InsnNode move = new InsnNode(InsnType.MOVE, 1);
			move.setResult(prevCast.getResult());
			move.addArg(prevCast.getArg(0));
			replaceInsn(mth, block, prevCast, move);
		}
	}

	private static @Nullable InsnNode isCastDuplicate(IndexInsnNode castInsn) {
		InsnArg arg = castInsn.getArg(0);
		if (arg.isRegister()) {
			SSAVar sVar = ((RegisterArg) arg).getSVar();
			if (sVar != null && sVar.getUseCount() == 1 && !sVar.isUsedInPhi()) {
				InsnNode assignInsn = sVar.getAssign().getParentInsn();
				if (assignInsn != null && assignInsn.getType() == InsnType.CHECK_CAST) {
					ArgType assignCastType = (ArgType) ((IndexInsnNode) assignInsn).getIndex();
					if (assignCastType.equals(castInsn.getIndex())) {
						return assignInsn;
					}
				}
			}
		}
		return null;
	}

	/**
	 * 移除不必要的指令。
	 */
	private static void removeStep(MethodNode mth, InsnRemover remover) {
		for (BlockNode block : mth.getBasicBlocks()) {
			remover.setBlock(block);
			for (InsnNode insn : block.getInstructions()) {
				switch (insn.getType()) {
					case NOP:
					case GOTO:
					case NEW_INSTANCE:
						remover.addAndUnbind(insn);
						break;

					default:
						if (insn.contains(AFlag.REMOVE)) {
							remover.addAndUnbind(insn);
						}
						break;
				}
			}
			remover.perform();
		}
	}

	private static void iterativeRemoveStep(MethodNode mth) {
		boolean changed;
		do {
			changed = false;
			for (BlockNode block : mth.getBasicBlocks()) {
				for (InsnNode insn : block.getInstructions()) {
					if (insn.getType() == InsnType.MOVE
							&& insn.isAttrStorageEmpty()
							&& isResultArgNotUsed(insn)) {
						InsnRemover.remove(mth, block, insn);
						changed = true;
						break;
					}
				}
			}
		} while (changed);
	}

	private static boolean isResultArgNotUsed(InsnNode insn) {
		RegisterArg result = insn.getResult();
		if (result != null) {
			SSAVar ssaVar = result.getSVar();
			return ssaVar.getUseCount() == 0;
		}
		return false;
	}

	/**
	 * 对匿名类构造函数调用中的参数应用以下处理：
	 * <ul>
	 *     <li>禁止将其内联到构造函数调用中</li>
	 *     <li>将变量声明为 final（编译器隐式要求）</li>
	 * </ul>
	 */
	private static void processAnonymousConstructor(MethodNode mth, ConstructorInsn co) {
		IMethodDetails callMthDetails = mth.root().getMethodUtils().getMethodDetails(co);
		if (!(callMthDetails instanceof MethodNode)) {
			return;
		}
		MethodNode callMth = (MethodNode) callMthDetails;
		if (!callMth.contains(AFlag.ANONYMOUS_CONSTRUCTOR) || callMth.contains(AFlag.NO_SKIP_ARGS)) {
			return;
		}
		SkipMethodArgsAttr attr = callMth.get(AType.SKIP_MTH_ARGS);
		if (attr != null) {
			int argsCount = Math.min(callMth.getMethodInfo().getArgsCount(), co.getArgsCount());
			for (int i = 0; i < argsCount; i++) {
				if (attr.isSkip(i)) {
					anonymousCallArgMod(co.getArg(i));
				}
			}
		} else {
			// 无法获取额外信息，则对所有参数应用修改（最安全的方案）
			co.getArguments().forEach(ModVisitor::anonymousCallArgMod);
		}
	}

	private static void anonymousCallArgMod(InsnArg arg) {
		arg.add(AFlag.DONT_INLINE);
		if (arg.isRegister()) {
			((RegisterArg) arg).getSVar().getCodeVar().setFinal(true);
		}
	}

	/**
	 * 返回参数的第一个使用指令。
	 * 如果只被使用一次，则尝试沿 move 链继续追踪。
	 */
	@Nullable
	private static InsnNode getFirstUseSkipMove(RegisterArg arg) {
		SSAVar sVar = arg.getSVar();
		int useCount = sVar.getUseCount();
		if (useCount == 0) {
			return null;
		}
		RegisterArg useArg = sVar.getUseList().get(0);
		InsnNode parentInsn = useArg.getParentInsn();
		if (parentInsn == null) {
			return null;
		}
		if (useCount == 1 && parentInsn.getType() == InsnType.MOVE) {
			return getFirstUseSkipMove(parentInsn.getResult());
		}
		return parentInsn;
	}

	private static InsnNode makeFilledArrayInsn(MethodNode mth, NewArrayNode newArrayNode, FillArrayInsn insn) {
		ArgType insnArrayType = newArrayNode.getArrayType();
		ArgType insnElementType = insnArrayType.getArrayElement();
		ArgType elType = insn.getElementType();
		if (!elType.isTypeKnown()
				&& insnElementType.isPrimitive()
				&& elType.contains(insnElementType.getPrimitiveType())) {
			elType = insnElementType;
		}
		if (!elType.equals(insnElementType) && !insnArrayType.equals(ArgType.OBJECT)) {
			mth.addWarn("Incorrect type for fill-array insn " + InsnUtils.formatOffset(insn.getOffset())
					+ ", element type: " + elType + ", insn element type: " + insnElementType);
		}
		if (!elType.isTypeKnown()) {
			LOG.warn("Unknown array element type: {} in mth: {}", elType, mth);
			elType = insnElementType.isTypeKnown() ? insnElementType : elType.selectFirst();
			if (elType == null) {
				throw new JadxRuntimeException("Null array element type");
			}
		}

		List<LiteralArg> list = insn.getLiteralArgs(elType);
		InsnNode filledArr = new FilledNewArrayNode(elType, list.size());
		filledArr.setResult(newArrayNode.getResult().duplicate());
		for (LiteralArg arg : list) {
			IFieldInfoRef f = mth.getParentClass().getConstFieldByLiteralArg(arg);
			if (f != null) {
				InsnNode fGet = new IndexInsnNode(InsnType.SGET, f.getFieldInfo(), 0);
				filledArr.addArg(InsnArg.wrapArg(fGet));
				addFieldUsage(f, mth);
			} else {
				filledArr.addArg(arg.duplicate());
			}
		}
		return filledArr;
	}

	private static void processMoveException(MethodNode mth, BlockNode block, InsnNode insn, InsnRemover remover) {
		ExcHandlerAttr excHandlerAttr = block.get(AType.EXC_HANDLER);
		if (excHandlerAttr == null) {
			return;
		}
		ExceptionHandler excHandler = excHandlerAttr.getHandler();

		// 结果参数同时被该指令和异常处理器使用，
		RegisterArg resArg = insn.getResult();
		ArgType type = excHandler.getArgType();
		String name = excHandler.isCatchAll() ? "th" : "e";
		if (resArg.getName() == null) {
			resArg.setName(name);
		}
		SSAVar sVar = insn.getResult().getSVar();
		if (sVar.getUseCount() == 0) {
			excHandler.setArg(new NamedArg(name, type));
			remover.addAndUnbind(insn);
		} else if (sVar.isUsedInPhi()) {
			// 异常变量被移动到外部变量 => 替换为 'move' 指令
			InsnNode moveInsn = new InsnNode(InsnType.MOVE, 1);
			moveInsn.setResult(insn.getResult());
			NamedArg namedArg = new NamedArg(name, type);
			moveInsn.addArg(namedArg);
			excHandler.setArg(namedArg);
			replaceInsn(mth, block, 0, moveInsn);
		}
		block.copyAttributeFrom(insn, AType.CODE_COMMENTS); // 保存注释
	}

	public static void addFieldUsage(IFieldInfoRef fieldData, MethodNode mth) {
		if (fieldData instanceof FieldNode) {
			((FieldNode) fieldData).addUseIn(mth);
		}
	}
}
