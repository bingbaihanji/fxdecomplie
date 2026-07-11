package com.bingbaihanji.fxdecomplie.core.jadx.api.args;

/**
 * 用户重命名映射的读写模式枚举
 * <p>
 * 控制反编译器在加载和保存用户自定义重命名映射 (如类名、方法名、字段名的手动重命名)时的行为策略
 */
public enum UserRenamesMappingsMode {

    /**
     * 仅读取映射文件，用户可手动保存 (默认模式)
     */
    READ,

    /**
     * 读取映射文件，并在每次变更后自动保存
     */
    READ_AND_AUTOSAVE_EVERY_CHANGE,

    /**
     * 读取映射文件，并在退出应用或关闭项目前自动保存
     */
    READ_AND_AUTOSAVE_BEFORE_CLOSING,

    /**
     * 不加载也不保存映射文件
     */
    IGNORE;

    /**
     * 获取默认的映射读写模式
     *
     * @return 默认返回 {@link #READ}，即仅读取、用户手动保存
     */
    public static UserRenamesMappingsMode getDefault() {
        return READ;
    }

    /**
     * 判断当前模式是否应读取映射文件
     *
     * @return 当模式不是 {@link #IGNORE} 时返回 {@code true}
     */
    public boolean shouldRead() {
        return this != IGNORE;
    }

    /**
     * 判断当前模式是否应自动写入映射文件
     *
     * @return 当模式为 {@link #READ_AND_AUTOSAVE_EVERY_CHANGE} 或
     *         {@link #READ_AND_AUTOSAVE_BEFORE_CLOSING} 时返回 {@code true}
     */
    public boolean shouldWrite() {
        return this == READ_AND_AUTOSAVE_EVERY_CHANGE || this == READ_AND_AUTOSAVE_BEFORE_CLOSING;
    }
}
