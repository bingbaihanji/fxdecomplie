package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.annotations.EncodedValue;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.insns.InsnData;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.MethodInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.InsnArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.invokedynamic.CustomRawCall;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.InsnUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;

/**
 * Information for raw invoke-custom instruction.<br>
 * Output will be formatted as polymorphic call with equivalent semantic
 * Contains two parts:
 * - resolve: treated as additional invoke insn (uses only constant args)
 * - invoke: call of resolved method (base for this invoke)
 * <br>
 * See {@link CustomRawCall} class for build details
 */
public class InvokeCustomRawNode extends InvokeNode {
	private final InvokeNode resolve;
	private List<EncodedValue> callSiteValues;

	public InvokeCustomRawNode(InvokeNode resolve, MethodInfo mthInfo, InsnData insn, boolean isRange) {
		super(mthInfo, insn, InvokeType.CUSTOM_RAW, false, isRange);
		this.resolve = resolve;
	}

	public InvokeCustomRawNode(InvokeNode resolve, MethodInfo mthInfo, InvokeType invokeType, int argsCount) {
		super(mthInfo, invokeType, argsCount);
		this.resolve = resolve;
	}

	public InvokeNode getResolveInvoke() {
		return resolve;
	}

	public void setCallSiteValues(List<EncodedValue> callSiteValues) {
		this.callSiteValues = callSiteValues;
	}

	public List<EncodedValue> getCallSiteValues() {
		return callSiteValues;
	}

	@Override
	public InsnNode copy() {
		InvokeCustomRawNode copy = new InvokeCustomRawNode(resolve, getCallMth(), getInvokeType(), getArgsCount());
		copyCommonParams(copy);
		copy.setCallSiteValues(callSiteValues);
		return copy;
	}

	@Override
	public boolean isStaticCall() {
		return true;
	}

	@Override
	public int getFirstArgOffset() {
		return 0;
	}

	@Override
	public @Nullable InsnArg getInstanceArg() {
		return null;
	}

	@Override
	public boolean isSame(InsnNode obj) {
		if (this == obj) {
			return true;
		}
		if (obj instanceof InvokeCustomRawNode) {
			return super.isSame(obj) && resolve.isSame(((InvokeCustomRawNode) obj).resolve);
		}
		return false;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(InsnUtils.formatOffset(offset)).append(": INVOKE_CUSTOM ");
		if (getResult() != null) {
			sb.append(getResult()).append(" = ");
		}
		if (!appendArgs(sb)) {
			sb.append('\n');
		}
		appendAttributes(sb);
		sb.append(" call-site: \n  ").append(Utils.listToString(callSiteValues, "\n  ")).append('\n');
		return sb.toString();
	}
}
