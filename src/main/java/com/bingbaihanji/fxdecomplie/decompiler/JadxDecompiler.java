package com.bingbaihanji.fxdecomplie.decompiler;

import com.bingbaihanji.fxdecomplie.decompiler.jadx.JadxDecompilerFacade;
import com.bingbaihanji.fxdecomplie.decompiler.jadx.JadxDecompilerRequest;

/**
 * jadx 反编译引擎适配器
 * <p>
 * 该类只保留 {@link Decompiler} 接口适配,实际项目状态、输入构建和移植内核调用由
 * {@link JadxDecompilerFacade} 管理。
 *
 * @author bingbaihanji
 * @date 2026-07-11
 */
public class JadxDecompiler implements Decompiler {

    private final JadxDecompilerFacade facade = JadxDecompilerFacade.getInstance();

    @Override
    public String decompile(String classFilePath, byte[] classBytes) {
        return decompileType(DecompilerContext.normalizeInternalName(classFilePath),
                classBytes, DecompilerContext.EMPTY);
    }

    @Override
    public String decompileType(String typeName, byte[] classBytes) {
        return decompileType(typeName, classBytes, DecompilerContext.EMPTY);
    }

    @Override
    public String decompile(String classFilePath, byte[] classBytes, DecompilerContext context) {
        return decompileType(DecompilerContext.normalizeInternalName(classFilePath),
                classBytes, context);
    }

    /**
     * 使用 jadx 引擎反编译指定类
     *
     * <p>核心流程：创建 jadx JadxDecompiler 实例，加载目标类字节码，
     * 调用 load() 初始化类层次图，然后通过 getCode() 获取反编译结果</p>
     *
     * @param typeName   类的内部名称(如 {@code com/example/MyClass})
     * @param classBytes 类的原始字节码
     * @param context    反编译上下文(可为 null，用于解析依赖类字节码)
     * @return 反编译后的 Java 源码字符串 异常时返回错误信息注释
     */
    @Override
    public String decompileType(String typeName, byte[] classBytes, DecompilerContext context) {
        return facade.decompile(new JadxDecompilerRequest(
                DecompilerContext.normalizeInternalName(typeName),
                typeName,
                classBytes,
                context));
    }

    @Override
    public DecompilerTypeEnum getType() {
        return DecompilerTypeEnum.JADX;
    }

    @Override
    public String getName() {
        return "jadx";
    }
}
