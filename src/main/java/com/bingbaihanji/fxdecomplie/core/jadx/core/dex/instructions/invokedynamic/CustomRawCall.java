package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.invokedynamic;

import java.util.ArrayList;
import java.util.List;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.IMethodHandle;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.IMethodProto;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.annotations.EncodedValue;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.insns.InsnData;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.ClassInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.MethodInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.ConstStringNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.InvokeCustomRawNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.InvokeNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.InvokeType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.InsnArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;

import static com.bingbaihanji.fxdecomplie.core.jadx.core.utils.EncodedValueUtils.buildLookupArg;
import static com.bingbaihanji.fxdecomplie.core.jadx.core.utils.EncodedValueUtils.convertToInsnArg;

/**
 * Show `invoke-custom` similar to polymorphic call
 */
public class CustomRawCall {

	public static InsnNode build(MethodNode mth, InsnData insn, boolean isRange, List<EncodedValue> values) {
		IMethodHandle resolveHandle = (IMethodHandle) values.get(0).getValue();
		String invokeName = (String) values.get(1).getValue();
		IMethodProto invokeProto = (IMethodProto) values.get(2).getValue();
		List<InsnArg> resolveArgs = buildArgs(mth, values);

		if (resolveHandle.getType().isField()) {
			throw new JadxRuntimeException("Field handle not yet supported");
		}

		RootNode root = mth.root();
		MethodInfo resolveMth = MethodInfo.fromRef(root, resolveHandle.getMethodRef());
		InvokeType resolveInvokeType = InvokeCustomUtils.convertInvokeType(resolveHandle.getType());
		InvokeNode resolve = new InvokeNode(resolveMth, resolveInvokeType, resolveArgs.size());
		resolveArgs.forEach(resolve::addArg);

		ClassInfo invokeCls = ClassInfo.fromType(root, ArgType.OBJECT); // type will be known at runtime
		MethodInfo invokeMth = MethodInfo.fromMethodProto(root, invokeCls, invokeName, invokeProto);
		InvokeCustomRawNode customRawNode = new InvokeCustomRawNode(resolve, invokeMth, insn, isRange);
		customRawNode.setCallSiteValues(values);
		return customRawNode;
	}

	private static List<InsnArg> buildArgs(MethodNode mth, List<EncodedValue> values) {
		int valuesCount = values.size();
		List<InsnArg> list = new ArrayList<>(valuesCount);
		RootNode root = mth.root();
		list.add(buildLookupArg(root)); // use `java.lang.invoke.MethodHandles.lookup()` as first arg
		for (int i = 1; i < valuesCount; i++) {
			EncodedValue value = values.get(i);
			try {
				list.add(convertToInsnArg(root, value));
			} catch (Exception e) {
				mth.addWarnComment("Failed to build arg in invoke-custom insn: " + value, e);
				list.add(InsnArg.wrapArg(new ConstStringNode(value.toString())));
			}
		}
		return list;
	}
}
