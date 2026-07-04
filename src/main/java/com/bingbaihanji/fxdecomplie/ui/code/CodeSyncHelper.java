package com.bingbaihanji.fxdecomplie.ui.code;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Split View 同步工具，在源码和字节码文本之间做方法级光标同步
 *
 * <p>同步策略分层降级：
 * 1. ASM LineNumberTable → 精确行映射
 * 2. 方法签名文本搜索 → 模糊匹配
 * 3. 失败时静默忽略（记录 debug 日志，不弹窗）</p>
 *
 * @author bingbaihanji
 * @date 2026-06-21
 */
public final class CodeSyncHelper {

    private static final Logger log = LoggerFactory.getLogger(CodeSyncHelper.class);

    /** 匹配方法声明行的模式：可见性 [static] 返回类型 方法名( */
    private static final Pattern METHOD_DECL_PATTERN =
            Pattern.compile("^\\s*(?:public|protected|private|static|final|abstract|synchronized|native|strictfp|\\s)+"
                    + "(?:<[\\w\\s.,?]+>\\s*)?"  // 泛型返回值
                    + "(\\w+(?:\\.\\w+)*)\\s+"    // 返回类型
                    + "(\\w+)\\s*\\(");            // 方法名(

    private CodeSyncHelper() {
        throw new AssertionError("utility class");
    }

    /**
     * 从 classBytes 构建 方法描述符 → 起始行号 映射
     *
     * @param classBytes 类文件字节码
     * @return 方法描述符到起始行号的映射，失败时返回空 Map
     */
    public static Map<String, Integer> buildMethodLineMap(byte[] classBytes) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (classBytes == null || classBytes.length == 0) {
            return map;
        }
        try {
            ClassReader reader = new ClassReader(classBytes);
            reader.accept(new ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc,
                                                 String signature, String[] exceptions) {
                    String key = name + desc;
                    return new MethodVisitor(org.objectweb.asm.Opcodes.ASM9) {
                        @Override
                        public void visitLineNumber(int line, Label start) {
                            if (!map.containsKey(key)) {
                                map.put(key, line);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_FRAMES);
        } catch (Exception e) {
            log.debug("构建方法行号映射失败", e);
        }
        return map;
    }

    /**
     * 从字节码文本中查找目标方法的起始行号
     *
     * @param bytecodeText     ASM Textifier 输出的字节码文本
     * @param methodDescriptor 方法描述符（name + descriptor）
     * @return 找到的行号（1-based），未找到返回 -1
     */
    public static int findMethodLineInBytecode(String bytecodeText, String methodDescriptor) {
        if (bytecodeText == null || methodDescriptor == null) {
            return -1;
        }
        String[] lines = bytecodeText.split("\n", -1);
        // 从方法描述符提取方法名（name部分）
        int parenIdx = methodDescriptor.indexOf('(');
        String methodName = parenIdx > 0 ? methodDescriptor.substring(0, parenIdx) : methodDescriptor;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.contains(methodName)) {
                return i + 1;
            }
        }
        return -1;
    }

    /**
     * 从源码文本中定位光标所在行所属的方法签名
     *
     * @param sourceCode 反编译源码
     * @param lineNumber 光标所在行号（1-based）
     * @return 方法声明摘要，未找到返回 null
     */
    public static String findMethodAtLine(String sourceCode, int lineNumber) {
        if (sourceCode == null || lineNumber <= 0) {
            return null;
        }
        String[] lines = sourceCode.split("\n", -1);
        if (lineNumber > lines.length) {
            return null;
        }

        // 向上搜索最近的方法声明
        for (int i = lineNumber - 1; i >= 0; i--) {
            String line = lines[i].trim();
            Matcher m = METHOD_DECL_PATTERN.matcher(line);
            if (m.find()) {
                return m.group(2); // 方法名
            }
        }
        return null;
    }
}
