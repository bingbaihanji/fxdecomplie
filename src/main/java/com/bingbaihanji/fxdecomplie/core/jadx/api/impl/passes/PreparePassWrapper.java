package com.bingbaihanji.fxdecomplie.core.jadx.api.impl.passes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass.JadxPass;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass.types.JadxPreparePass;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.AbstractVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PreparePassWrapper extends AbstractVisitor implements IPassWrapperVisitor {
    private static final Logger LOG = LoggerFactory.getLogger(PreparePassWrapper.class);

    private final JadxPreparePass preparePass;

    public PreparePassWrapper(JadxPreparePass preparePass) {
        this.preparePass = preparePass;
    }

    @Override
    public JadxPass getPass() {
        return preparePass;
    }

    @Override
    public void init(RootNode root) throws JadxException {
        try {
            preparePass.init(root);
        } catch (Exception e) {
            LOG.error("Error in prepare pass init: {}", this, e);
        }
    }

    @Override
    public String getName() {
        return preparePass.getInfo().getName();
    }
}
