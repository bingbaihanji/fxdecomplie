package com.bingbaihanji.fxdecomplie.decompiler;

import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.util.jvm.ClassNameUtil;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 每次反编译的上下文,通过 WorkspaceIndex 按工作区隔离地解析依赖类字节码
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class DecompilerContext implements AutoCloseable {

    /** 空上下文实例,不提供任何依赖类字节码解析能力 */
    public static final DecompilerContext EMPTY = new DecompilerContext(
            internalName -> null, Map.of());

    /** 字节码提供器,用于按内部名称解析依赖类字节码 */
    private final ClassBytecodeProvider bytecodeProvider;
    /** 反编译选项(不可变) */
    private final Map<String, String> options;
    /** 可关闭的资源(如 ZipFile),反编译完成后可能需要关闭 */
    private final AutoCloseable closeable;
    /** 是否在使用后自动关闭 closeable */
    private final boolean closeAfterUse;
    /** CAS 关闭标记,确保 closeable 只被关闭一次 */
    private final AtomicBoolean closed = new AtomicBoolean();

    private DecompilerContext(ClassBytecodeProvider bytecodeProvider,
                              Map<String, String> options) {
        this(bytecodeProvider, options, null, false);
    }

    private DecompilerContext(ClassBytecodeProvider bytecodeProvider,
                              Map<String, String> options,
                              AutoCloseable closeable,
                              boolean closeAfterUse) {
        this.bytecodeProvider = Objects.requireNonNull(bytecodeProvider, "bytecodeProvider");
        this.options = options == null ? Map.of() : Map.copyOf(options);
        this.closeable = closeable;
        this.closeAfterUse = closeAfterUse;
    }

    /**
     * 使用指定字节码提供器创建上下文(无额外选项)
     *
     * @param bytecodeProvider 字节码提供器
     * @return 反编译上下文
     */
    public static DecompilerContext of(ClassBytecodeProvider bytecodeProvider) {
        return of(bytecodeProvider, Map.of());
    }

    /**
     * 使用指定字节码提供器和选项创建上下文
     *
     * @param bytecodeProvider 字节码提供器
     * @param options          反编译选项(不可变拷贝)
     * @return 反编译上下文
     */
    public static DecompilerContext of(ClassBytecodeProvider bytecodeProvider,
                                       Map<String, String> options) {
        return of(bytecodeProvider, options, null);
    }

    /**
     * 使用指定字节码提供器、选项和可关闭资源创建上下文(默认 closeAfterUse=true)
     *
     * @param bytecodeProvider 字节码提供器
     * @param options          反编译选项(不可变拷贝)
     * @param closeable        反编译完成后需要关闭的资源
     * @return 反编译上下文
     */
    public static DecompilerContext of(ClassBytecodeProvider bytecodeProvider,
                                       Map<String, String> options,
                                       AutoCloseable closeable) {
        return of(bytecodeProvider, options, closeable, true);
    }

    /**
     * 创建一次性使用的上下文(语义等同 {@link #of(ClassBytecodeProvider, Map, AutoCloseable)},
     * closeAfterUse 固定为 true)
     *
     * @param bytecodeProvider 字节码提供器
     * @param options          反编译选项
     * @param closeable        反编译完成后需要关闭的资源
     * @return 反编译上下文
     */
    public static DecompilerContext singleUse(ClassBytecodeProvider bytecodeProvider,
                                              Map<String, String> options,
                                              AutoCloseable closeable) {
        return of(bytecodeProvider, options, closeable, true);
    }

    /**
     * 核心工厂方法：校验参数并按规则创建上下文
     * 若 bytecodeProvider 为 null 且传入了 closeable,则立即关闭 closeable 并抛出异常(参数无效) 
     * 若仅 bytecodeProvider 为 null,则回退为仅带选项的上下文
     */
    private static DecompilerContext of(ClassBytecodeProvider bytecodeProvider,
                                        Map<String, String> options,
                                        AutoCloseable closeable,
                                        boolean closeAfterUse) {
        if (bytecodeProvider == null) {
            if (closeable != null) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            "bytecodeProvider 为 null 时无法持有 closeable", e);
                }
            }
            return withOptions(options);
        }
        return new DecompilerContext(bytecodeProvider, options, closeable, closeAfterUse);
    }

    /**
     * 从工作区索引创建上下文(无额外选项)
     * 通过 WorkspaceIndex 的 {@code getClassBytes} 方法解析依赖类字节码
     *
     * @param index 工作区索引
     * @return 反编译上下文(若 index 为 null 则返回仅携带选项的空上下文)
     */
    public static DecompilerContext fromWorkspaceIndex(WorkspaceIndex index) {
        return fromWorkspaceIndex(index, Map.of());
    }

    /**
     * 从工作区索引创建上下文(带选项)
     *
     * @param index   工作区索引
     * @param options 反编译选项
     * @return 反编译上下文(若 index 为 null 则返回仅携带选项的空上下文)
     */
    public static DecompilerContext fromWorkspaceIndex(WorkspaceIndex index,
                                                       Map<String, String> options) {
        if (index == null) {
            return withOptions(options);
        }
        return of(index::getClassBytes, options);
    }

    /**
     * 创建仅携带选项的上下文(无字节码提供器,不解析依赖类)
     *
     * @param options 反编译选项
     * @return 反编译上下文
     */
    public static DecompilerContext withOptions(Map<String, String> options) {
        return new DecompilerContext(internalName -> null, options);
    }

    /**
     * 标准化内部名称(去掉开头的 L 和结尾的 ; 等描述符前缀/后缀)
     *
     * @param internalName 待标准化的内部名称
     * @return 标准化后的内部名称
     */
    public static String normalizeInternalName(String internalName) {
        return ClassNameUtil.normalizeInternalName(internalName);
    }

    /** 从内部名称中提取简单类名(例如 "com/example/Foo" → "Foo") */
    public static String simpleName(String internalName) {
        return ClassNameUtil.simpleName(internalName);
    }

    /**
     * 获取指定内部名称对应的类字节码
     * 内部名称会先被标准化处理,然后委托给 {@link ClassBytecodeProvider}
     *
     * @param internalName JVM 内部类名
     * @return 类文件字节数组,若找不到则返回 {@code null}
     */
    public byte[] getClassBytes(String internalName) {
        if (internalName == null || internalName.isBlank()) {
            return null;
        }
        String normalized = normalizeInternalName(internalName);
        return bytecodeProvider.getClassBytes(normalized);
    }

    /**
     * 解析依赖类字节码(与 {@link #getClassBytes} 行为一致,为将来扩展预留)
     *
     * @param internalName JVM 内部类名
     * @return 类文件字节数组,若找不到则返回 {@code null}
     */
    public byte[] resolveClassBytes(String internalName) {
        return getClassBytes(internalName);
    }

    /** @return 反编译选项(不可变) */
    public Map<String, String> options() {
        return options;
    }

    /**
     * 获取单个选项值
     *
     * @param key          选项键
     * @param defaultValue 默认值(未找到时返回)
     * @return 选项值
     */
    public String option(String key, String defaultValue) {
        return options.getOrDefault(key, defaultValue);
    }

    /** @return 是否携带选项 */
    public boolean hasOptions() {
        return !options.isEmpty();
    }

    /** @return 是否在使用后自动关闭可关闭资源 */
    public boolean closeAfterUse() {
        return closeAfterUse;
    }

    /**
     * 关闭上下文关联的可关闭资源
     * 仅当 {@link #closeAfterUse()} 为 {@code true} 且资源未被关闭过时才执行关闭
     * 通过 CAS 保证线程安全,资源只被关闭一次
     *
     * @throws Exception 关闭资源时可能抛出的异常
     */
    @Override
    public void close() throws Exception {
        if (closeAfterUse && closeable != null && closed.compareAndSet(false, true)) {
            closeable.close();
        }
    }
}
