package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.prepare;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.annotations.EncodedValue;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.JadxAttrType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.AccessInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.ConstStorage;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.FieldNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.AbstractVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.JadxVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.usage.UsageInfoVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;
import org.jetbrains.annotations.Nullable;

@JadxVisitor(
        name = "CollectConstValues",
        desc = "Collect and store values from static final fields",
        runAfter = {
                UsageInfoVisitor.class // check field usage (do not restore if used somewhere)
        }
)
public class CollectConstValues extends AbstractVisitor {

    public static @Nullable Object getFieldConstValue(FieldNode fld) {
        AccessInfo accFlags = fld.getAccessFlags();
        if (!accFlags.isStatic() || !accFlags.isFinal()) {
            return null;
        }
        EncodedValue constVal = fld.get(JadxAttrType.CONSTANT_VALUE);
        if (constVal == null || constVal == EncodedValue.NULL) {
            return null;
        }
        if (!fld.getUseIn().isEmpty()) {
            // field still used somewhere and not inlined by compiler, so we don't need to restore it
            return null;
        }
        return constVal.getValue();
    }

    @Override
    public boolean visit(ClassNode cls) throws JadxException {
        RootNode root = cls.root();
        if (!root.getArgs().isReplaceConsts()) {
            return true;
        }
        if (cls.getFields().isEmpty()) {
            return true;
        }
        ConstStorage constStorage = root.getConstValues();
        for (FieldNode fld : cls.getFields()) {
            try {
                Object value = getFieldConstValue(fld);
                if (value != null) {
                    constStorage.addConstField(fld, value, fld.getAccessFlags().isPublic());
                }
            } catch (Exception e) {
                cls.addWarnComment("Failed to process value of field: " + fld, e);
            }
        }
        return true;
    }
}
