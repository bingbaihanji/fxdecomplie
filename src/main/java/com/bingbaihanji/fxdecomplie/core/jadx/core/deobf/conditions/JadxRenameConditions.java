package com.bingbaihanji.fxdecomplie.core.jadx.core.deobf.conditions;

import com.bingbaihanji.fxdecomplie.core.jadx.api.deobf.IDeobfCondition;
import com.bingbaihanji.fxdecomplie.core.jadx.api.deobf.IRenameCondition;
import com.bingbaihanji.fxdecomplie.core.jadx.api.deobf.impl.CombineDeobfConditions;

import java.util.ArrayList;
import java.util.List;

/**
 * jadx 重命名条件的工厂类。
 * <p>
 * 提供构建默认反混淆条件集合以及组合出最终 {@link IRenameCondition} 的便捷方法。
 */
public class JadxRenameConditions {

    /**
     * 该方法提供 jadx 使用的默认反混淆条件的可变列表。
     * 若需构建 {@link IRenameCondition}，请使用 {@link CombineDeobfConditions#combine(List)} 方法。
     *
     * @return 默认反混淆条件列表
     */
    public static List<IDeobfCondition> buildDefaultDeobfConditions() {
        List<IDeobfCondition> list = new ArrayList<>();
        list.add(new BaseDeobfCondition());
        list.add(new DeobfWhitelist());
        list.add(new ExcludePackageWithTLDNames());
        list.add(new ExcludeAndroidRClass());
        list.add(new AvoidClsAndPkgNamesCollision());
        list.add(new DeobfLengthCondition());
        return list;
    }

    /**
     * 构建由默认反混淆条件组合而成的重命名条件。
     *
     * @return 组合后的重命名条件
     */
    public static IRenameCondition buildDefault() {
        return CombineDeobfConditions.combine(buildDefaultDeobfConditions());
    }
}
