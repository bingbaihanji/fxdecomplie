package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttrType;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodeComment;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.AnonymousClassAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.ClassTypeVarsAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.CodeFeaturesAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.DeclareVariablesAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.DecompileModeOverrideAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.EdgeInsnAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.EnumClassAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.EnumMapAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.ExcSplitCrossAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.FieldReplaceAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.ForceReturnAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.GenericInfoAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.InlinedAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.JadxCommentsAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.JadxError;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.JumpInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.LocalVarsDebugInfoAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.LoopInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.LoopLabelAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.MethodBridgeAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.MethodInlineAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.MethodOverrideAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.MethodReplaceAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.MethodThrowsAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.MethodTypeVarsAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.PhiListAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.RegDebugInfoAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.RegionRefAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.RenameReasonAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.SkipMethodArgsAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.SpecialEdgeAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.TmpEdgeAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IMethodDetails;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.trycatch.CatchAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.trycatch.ExcHandlerAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.trycatch.TryCatchBlockAttr;

/**
 * 属性类型枚举。
 * 使用泛型类型，以便在调用 'AttributeStorage.get' 方法后省略强制类型转换。
 *
 * @param <T> 属性类的具体实现类型
 */
public final class AType<T extends IJadxAttribute> implements IJadxAttrType<T> {

	// 类、方法、字段、指令
	public static final AType<AttrList<CodeComment>> CODE_COMMENTS = new AType<>();

	// 类、方法、字段
	public static final AType<RenameReasonAttr> RENAME_REASON = new AType<>();

	// 类、方法
	public static final AType<AttrList<JadxError>> JADX_ERROR = new AType<>(); // 代码反编译失败
	public static final AType<JadxCommentsAttr> JADX_COMMENTS = new AType<>(); // 关于反编译的附加信息

	// 类
	public static final AType<EnumClassAttr> ENUM_CLASS = new AType<>();
	public static final AType<EnumMapAttr> ENUM_MAP = new AType<>();
	public static final AType<ClassTypeVarsAttr> CLASS_TYPE_VARS = new AType<>();
	public static final AType<AnonymousClassAttr> ANONYMOUS_CLASS = new AType<>();
	public static final AType<InlinedAttr> INLINED = new AType<>();
	public static final AType<DecompileModeOverrideAttr> DECOMPILE_MODE_OVERRIDE = new AType<>();

	// 字段
	public static final AType<FieldInitInsnAttr> FIELD_INIT_INSN = new AType<>();
	public static final AType<FieldReplaceAttr> FIELD_REPLACE = new AType<>();

	// 方法
	public static final AType<LocalVarsDebugInfoAttr> LOCAL_VARS_DEBUG_INFO = new AType<>();
	public static final AType<MethodInlineAttr> METHOD_INLINE = new AType<>();
	public static final AType<MethodReplaceAttr> METHOD_REPLACE = new AType<>();
	public static final AType<MethodBridgeAttr> BRIDGED_BY = new AType<>();
	public static final AType<SkipMethodArgsAttr> SKIP_MTH_ARGS = new AType<>();
	public static final AType<MethodOverrideAttr> METHOD_OVERRIDE = new AType<>();
	public static final AType<MethodTypeVarsAttr> METHOD_TYPE_VARS = new AType<>();
	public static final AType<AttrList<TryCatchBlockAttr>> TRY_BLOCKS_LIST = new AType<>();
	public static final AType<CodeFeaturesAttr> METHOD_CODE_FEATURES = new AType<>();
	public static final AType<MethodThrowsAttr> METHOD_THROWS = new AType<>();

	// 区域
	public static final AType<DeclareVariablesAttr> DECLARE_VARIABLES = new AType<>();

	// 基本块
	public static final AType<PhiListAttr> PHI_LIST = new AType<>();
	public static final AType<ForceReturnAttr> FORCE_RETURN = new AType<>();
	public static final AType<AttrList<LoopInfo>> LOOP = new AType<>();
	public static final AType<AttrList<EdgeInsnAttr>> EDGE_INSN = new AType<>();
	public static final AType<AttrList<SpecialEdgeAttr>> SPECIAL_EDGE = new AType<>();
	public static final AType<TmpEdgeAttr> TMP_EDGE = new AType<>();
	public static final AType<TryCatchBlockAttr> TRY_BLOCK = new AType<>();
	public static final AType<ExcSplitCrossAttr> EXC_SPLIT_CROSS = new AType<>();

	// 基本块或指令
	public static final AType<ExcHandlerAttr> EXC_HANDLER = new AType<>();
	public static final AType<CatchAttr> EXC_CATCH = new AType<>();

	// 指令
	public static final AType<LoopLabelAttr> LOOP_LABEL = new AType<>();
	public static final AType<AttrList<JumpInfo>> JUMP = new AType<>();
	public static final AType<IMethodDetails> METHOD_DETAILS = new AType<>();
	public static final AType<GenericInfoAttr> GENERIC_INFO = new AType<>();
	public static final AType<RegionRefAttr> REGION_REF = new AType<>();

	// 寄存器
	public static final AType<RegDebugInfoAttr> REG_DEBUG_INFO = new AType<>();
}
