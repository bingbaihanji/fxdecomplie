package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass.impl;

import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxDecompiler;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass.JadxPassInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass.types.JadxAfterLoadPass;

import java.util.function.Consumer;

public class SimpleAfterLoadPass implements JadxAfterLoadPass {

    private final JadxPassInfo info;
    private final Consumer<JadxDecompiler> init;

    public SimpleAfterLoadPass(String name, Consumer<JadxDecompiler> init) {
        this.info = new SimpleJadxPassInfo(name);
        this.init = init;
    }

    @Override
    public JadxPassInfo getInfo() {
        return info;
    }

    @Override
    public void init(JadxDecompiler decompiler) {
        init.accept(decompiler);
    }
}
