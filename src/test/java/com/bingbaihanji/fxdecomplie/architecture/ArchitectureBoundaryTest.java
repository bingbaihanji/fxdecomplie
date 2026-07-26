package com.bingbaihanji.fxdecomplie.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 扫描所有 main 源码的 import 语句,验证包依赖边界不违反架构规则。
 *
 * <p>不依赖 ArchUnit 等第三方库 — 纯 JDK 正则扫描，保持零额外依赖。</p>
 */
@DisplayName("架构边界验证")
final class ArchitectureBoundaryTest {

    private static final Path MAIN_SRC = Paths.get("src/main/java");
    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("^import\\s+(com\\.bingbaihanji\\.[\\w.*]+);", Pattern.MULTILINE);
    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("^package\\s+([\\w.]+);", Pattern.MULTILINE);

    private static final String BASE = "com.bingbaihanji.fxdecomplie";
    private static final String CG = "com.bingbaihanji.classgraph";
    private static final String WINDOWS = "com.bingbaihanji.windows";

    /**
     * 每条禁止规则: {源包前缀, 禁止import的包前缀, 规则描述}
     */
    record ForbiddenRule(String srcPkg, String forbiddenImport, String description) {
    }

    private static final List<ForbiddenRule> RULES = List.of(
            new ForbiddenRule(BASE + ".service", BASE + ".controller",
                    "R1: service 不能依赖 controller"),
            new ForbiddenRule(BASE + ".service", BASE + ".ui",
                    "R2: service 不能依赖 ui"),
            new ForbiddenRule(BASE + ".core.jadx", BASE + ".controller",
                    "R3: core/jadx 不能依赖 controller"),
            new ForbiddenRule(BASE + ".core.jadx", BASE + ".ui",
                    "R4: core/jadx 不能依赖 ui"),
            new ForbiddenRule(BASE + ".core.jadx", BASE + ".service",
                    "R5: core/jadx 不能依赖 service"),
            new ForbiddenRule(BASE + ".model", BASE + ".controller",
                    "R9: model 不能依赖 controller"),
            new ForbiddenRule(BASE + ".model", BASE + ".ui",
                    "R9: model 不能依赖 ui"),
            new ForbiddenRule(BASE + ".model", BASE + ".service",
                    "R9: model 不能依赖 service")
    );

    /**
     * 白名单: 已知违规,后续阶段逐步消除。
     * 格式: "源文件相对路径::imported包"
     */
    private static final Set<String> WHITELIST = Set.of(
            // 阶段1-2会重构这些,先列入白名单
            // 添加新条目格式: "service/SomeFile.java::com.bingbaihanji.fxdecomplie.ui.SomeUI"
    );

    // ─── 测试 1: 禁止的包依赖方向 ───

    @Test
    @DisplayName("禁止的包依赖方向 (R1-R5)")
    void shouldNotViolateDependencyBoundaries() throws IOException {
        Map<String, String> sourceFiles = readAllJavaSources(MAIN_SRC);
        List<String> violations = new ArrayList<>();
        int totalImports = 0;

        for (Map.Entry<String, String> entry : sourceFiles.entrySet()) {
            String relPath = entry.getKey();
            String content = entry.getValue();
            String pkg = extractPackage(content);
            if (pkg == null) {
                continue;
            }

            Matcher m = IMPORT_PATTERN.matcher(content);
            while (m.find()) {
                totalImports++;
                String imported = m.group(1);

                for (ForbiddenRule rule : RULES) {
                    if (pkg.startsWith(rule.srcPkg)
                            && imported.startsWith(rule.forbiddenImport)
                            && !isSamePackage(pkg, imported)) {
                        String violationKey = relPath + "::" + imported;
                        if (!WHITELIST.contains(violationKey)) {
                            violations.add(relPath + " imports " + imported
                                    + " — " + rule.description);
                        }
                    }
                }
            }
        }

        System.out.println("[架构测试] 扫描 " + sourceFiles.size() + " 个文件, "
                + totalImports + " 条 import, 违规 " + violations.size() + " 条");

        if (!violations.isEmpty()) {
            System.out.println("[架构测试] 违规列表:");
            violations.forEach(v -> System.out.println("  " + v));
        }

        assertTrue(violations.isEmpty(),
                "发现 " + violations.size() + " 条架构违规:\n"
                        + String.join("\n", violations));
    }

    // ─── 测试 2: ClassGraph 隔离 ───

    @Test
    @DisplayName("ClassGraph 内核隔离 (R6-R8)")
    void shouldIsolateClassGraph() throws IOException {
        Map<String, String> sourceFiles = readAllJavaSources(MAIN_SRC);
        List<String> violations = new ArrayList<>();

        for (Map.Entry<String, String> entry : sourceFiles.entrySet()) {
            String relPath = entry.getKey();
            String content = entry.getValue();
            String pkg = extractPackage(content);

            // R6: classgraph 内核不能 import 主应用
            if (pkg != null && pkg.startsWith(CG)) {
                Matcher m = IMPORT_PATTERN.matcher(content);
                while (m.find()) {
                    String imported = m.group(1);
                    if (imported.startsWith(BASE) || imported.startsWith(WINDOWS)) {
                        violations.add(relPath + " (classgraph) imports " + imported
                                + " — R6: classgraph 不能依赖主应用");
                    }
                }
            }

            // R7-R8: 主应用 (非适配层) 不能直接 import classgraph
            if (pkg != null && pkg.startsWith(BASE)
                    && !relPath.contains("service/classscan/")
                    && !relPath.contains("service/reference/")) {
                Matcher m = IMPORT_PATTERN.matcher(content);
                while (m.find()) {
                    String imported = m.group(1);
                    if (imported.startsWith(CG)) {
                        violations.add(relPath + " imports " + imported
                                + " — R7/R8: UI/controller 禁止直接引用 ClassGraph");
                    }
                }
            }
        }

        System.out.println("[架构测试] ClassGraph 隔离检查: " + violations.size() + " 条违规");

        if (!violations.isEmpty()) {
            System.out.println("[架构测试] 违规列表:");
            violations.forEach(v -> System.out.println("  " + v));
        }

        assertTrue(violations.isEmpty(),
                "ClassGraph 隔离违规 (" + violations.size() + " 条):\n"
                        + String.join("\n", violations));
    }

    // ─── 测试 3: Model 包不依赖重量级包 ───

    @Test
    @DisplayName("Model 包不依赖 controller/ui/service (R9)")
    void shouldNotDependOnHeavyweightPackagesInModel() throws IOException {
        Map<String, String> sourceFiles = readAllJavaSources(MAIN_SRC);
        List<String> violations = new ArrayList<>();

        for (Map.Entry<String, String> entry : sourceFiles.entrySet()) {
            String relPath = entry.getKey();
            String content = entry.getValue();
            String pkg = extractPackage(content);

            if (pkg != null && pkg.startsWith(BASE + ".model")) {
                Matcher m = IMPORT_PATTERN.matcher(content);
                while (m.find()) {
                    String imported = m.group(1);
                    // model 允许依赖 util、decompiler（枚举）、bytecode（元数据）
                    // 禁止依赖 controller、ui、service、classgraph、windows
                    if ((imported.startsWith(BASE + ".controller")
                            || imported.startsWith(BASE + ".ui")
                            || imported.startsWith(BASE + ".service"))
                            && !imported.startsWith(BASE + ".model")) {
                        violations.add(relPath + " imports " + imported
                                + " — R9: model 不能依赖 controller/ui/service");
                    }
                    if (imported.startsWith(CG) || imported.startsWith(WINDOWS)) {
                        violations.add(relPath + " imports " + imported
                                + " — R9: model 不能依赖 classgraph/windows");
                    }
                }
            }
        }

        System.out.println("[架构测试] Model 依赖检查: " + violations.size() + " 条违规");

        if (!violations.isEmpty()) {
            System.out.println("[架构测试] 违规列表:");
            violations.forEach(v -> System.out.println("  " + v));
        }

        assertTrue(violations.isEmpty(),
                "Model 包依赖违规 (" + violations.size() + " 条):\n"
                        + String.join("\n", violations));
    }

    // ─── helper methods ───

    private static Map<String, String> readAllJavaSources(Path root) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                    .forEach(p -> {
                        try {
                            String content = Files.readString(p);
                            String rel = root.relativize(p).toString().replace('\\', '/');
                            result.put(rel, content);
                        } catch (IOException ignored) {
                            System.err.println("读取失败: " + p);
                        }
                    });
        }
        return result;
    }

    private static String extractPackage(String source) {
        Matcher m = PACKAGE_PATTERN.matcher(source);
        return m.find() ? m.group(1) : null;
    }

    private static boolean isSamePackage(String pkg, String imported) {
        // 同包或子包不算违规
        return imported.startsWith(pkg + ".");
    }
}
