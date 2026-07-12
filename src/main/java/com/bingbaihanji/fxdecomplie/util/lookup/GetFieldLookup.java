package com.bingbaihanji.fxdecomplie.util.lookup;

import com.bingbaihanji.fxdecomplie.util.value.ReValue;

import org.objectweb.asm.tree.FieldInsnNode;

/**
 * 用于查找绑定上下文的字段值
 *
 * @author Matt Coley
 */
public interface GetFieldLookup {
    /**
     * @param field
     * 		字段引用
     * @param context
     * 		字段所在的类上下文
     * 		具有{@link ReValue#hasKnownValue() 已知值}
     *
     * @return 表示该字段的值
     */

    ReValue get(FieldInsnNode field, ReValue context);

    /**
     * @param field
     * 		字段引用
     *
     * @return 当此查找能够通过 {@link #get(FieldInsnNode, ReValue)} 提供值时返回 {@code true}
     */
    boolean hasLookup(FieldInsnNode field);
}
