package com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.code.decoders;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.insns.custom.impl.SwitchPayload;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.DataReader;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.code.CodeDecodeState;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.code.JavaInsnData;

public class LookupSwitchDecoder implements IJavaInsnDecoder {

    private static void read(CodeDecodeState state, boolean skip) {
        DataReader reader = state.reader();
        JavaInsnData insn = state.insn();
        int dataOffset = reader.getOffset();
        int insnOffset = insn.getOffset();
        reader.skip(3 - insnOffset % 4);
        int defTarget = insnOffset + reader.readS4();
        int pairs = reader.readS4();
        if (skip) {
            reader.skip(pairs * 8);
        } else {
            state.pop(0);
            int[] keys = new int[pairs];
            int[] targets = new int[pairs];
            for (int i = 0; i < pairs; i++) {
                keys[i] = reader.readS4();
                int target = insnOffset + reader.readS4();
                targets[i] = target;
                state.registerJump(target);
            }
            insn.setTarget(defTarget);
            state.registerJump(defTarget);
            insn.setPayload(new SwitchPayload(pairs, keys, targets));
        }
        insn.setPayloadSize(reader.getOffset() - dataOffset);
    }

    @Override
    public void decode(CodeDecodeState state) {
        read(state, false);
    }

    @Override
    public void skip(CodeDecodeState state) {
        read(state, true);
    }
}
