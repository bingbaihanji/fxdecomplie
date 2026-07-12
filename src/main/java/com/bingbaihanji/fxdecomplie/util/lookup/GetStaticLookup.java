package com.bingbaihanji.fxdecomplie.util.lookup;

import com.bingbaihanji.fxdecomplie.util.value.ReValue;
import org.objectweb.asm.tree.FieldInsnNode;

/**
 * 用于查找静态字段值
 *
 * @author Matt Coley
 */
public interface GetStaticLookup {
    /**
     * @param field
     * 		字段引用
     *
     * @return 表示该字段的值
     */

    ReValue get(FieldInsnNode field);

    /**
     * @param field
     * 		字段引用
     *
     * @return 当此查找能够通过 {@link #get(FieldInsnNode)} 提供值时返回 {@code true}
     */
    boolean hasLookup(FieldInsnNode field);
}
