package com.bingbaihanji.fxdecomplie.decompiler.jadx;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JadxInputBuilderTest {

    @Test
    void includesWorkspaceDependenciesFromContext(@TempDir Path tmp) throws Exception {
        CompiledSample sample = compileSample(tmp);
        DecompilerContext context = DecompilerContext.of(name ->
                "com/example/Dependency".equals(name) ? sample.dependencyBytes() : null);

        JadxInputPlan plan = new JadxInputBuilder().build(new JadxDecompilerRequest(
                "com/example/UsesDependency",
                "com/example/UsesDependency.class",
                sample.targetBytes(),
                context));

        assertEquals("com/example/UsesDependency", plan.targetType());
        assertEquals(2, plan.totalClasses());
        assertEquals(1, plan.dependencyClasses());
    }

    @Test
    void canDisableWorkspaceDependencyLoading(@TempDir Path tmp) throws Exception {
        CompiledSample sample = compileSample(tmp);
        DecompilerContext context = DecompilerContext.of(name ->
                        "com/example/Dependency".equals(name) ? sample.dependencyBytes() : null,
                Map.of(JadxAdapterOptions.LOAD_WORKSPACE_DEPENDENCIES, "false"));

        JadxInputPlan plan = new JadxInputBuilder().build(new JadxDecompilerRequest(
                "com/example/UsesDependency",
                "com/example/UsesDependency.class",
                sample.targetBytes(),
                context));

        assertEquals(1, plan.totalClasses());
        assertEquals(0, plan.dependencyClasses());
    }

    private static CompiledSample compileSample(Path tmp) throws Exception {
        Path srcRoot = tmp.resolve("src");
        Path outRoot = tmp.resolve("classes");
        Path packageDir = srcRoot.resolve("com").resolve("example");
        Files.createDirectories(packageDir);
        Files.createDirectories(outRoot);
        Path source = packageDir.resolve("UsesDependency.java");
        Files.writeString(source, """
                package com.example;

                public class UsesDependency {
                    private final Dependency dependency = new Dependency();

                    public String name() {
                        return dependency.name();
                    }
                }

                class Dependency {
                    String name() {
                        return "ok";
                    }
                }
                """);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "tests require a JDK compiler");
        int exit = compiler.run(null, null, null,
                "-d", outRoot.toString(), source.toString());
        assertEquals(0, exit, "test class should compile");

        return new CompiledSample(
                Files.readAllBytes(outRoot.resolve("com/example/UsesDependency.class")),
                Files.readAllBytes(outRoot.resolve("com/example/Dependency.class")));
    }

    private record CompiledSample(byte[] targetBytes, byte[] dependencyBytes) {
    }
}
