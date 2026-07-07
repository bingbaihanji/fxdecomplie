package com.bingbaihanji.fxdecomplie.rename;

/**
 * 单个重命名条目
 *
 * @param type      类型（"class" / "method" / "field"）
 * @param className 所属类内部名（如 com/example/Foo）
 * @param oldName   原始名称
 * @param newName   新名称
 * @param desc      方法/字段描述符（类级为空）
 * @author bingbaihanji
 * @date 2026-07-03
 */
public record RenameEntry(
        String type,
        String className,
        String oldName,
        String newName,
        String desc
) {
    public RenameEntry {
        type = type == null ? "" : type;
        className = className == null ? "" : className;
        oldName = oldName == null ? "" : oldName;
        newName = newName == null ? "" : newName;
        desc = desc == null ? "" : desc;
    }
}
