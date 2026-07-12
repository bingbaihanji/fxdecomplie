package com.bingbaihanji.fxdecomplie.decompiler.jadx;

import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxArgs;
import com.bingbaihanji.fxdecomplie.core.jadx.api.args.GeneratedRenamesMappingFileMode;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerContext;
import com.bingbaihanji.fxdecomplie.decompiler.JadxDecompiler;
import com.bingbaihanji.fxdecomplie.rename.RenameService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JadxDecompilerFacadeTest {

    @Test
    void deobfuscationUsesIsolatedRuntimeOutput() {
        JadxArgs args = JadxArgsFactory.create(Map.of("deobfuscationOn", "true"));

        assertTrue(args.isDeobfuscationOn());
        assertFalse(args.getRenameCondition().shouldRename((com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.PackageNode) null));
        assertEquals(GeneratedRenamesMappingFileMode.IGNORE,
                args.getGeneratedRenamesMappingFileMode());
        assertNotNull(args.getOutDir());
        assertTrue(args.getOutDir().toPath().toAbsolutePath().normalize()
                .endsWith(Path.of("cache", "jadx", "runtime")));
    }

    @Test
    void deobfuscationAliasesAreSyncedToProjectRenameState(@TempDir Path tmp) throws Exception {
        RenameService.setRootDir(tmp.resolve("renames"));
        CompiledSample sample = compileSample(tmp);
        String workspaceHash = "jadx-sync-test";
        DecompilerContext context = DecompilerContext.of(
                name -> "z/a".equals(name) ? sample.dependencyBytes() : null,
                Map.of(
                        "deobfuscationOn", "true",
                        "deobfuscationMinLength", "3"),
                null,
                workspaceHash);

        String source = new JadxDecompiler().decompile(
                "z/b.class", sample.targetBytes(), context);

        assertFalse(source.startsWith("// jadx decompile failed"), source);
        assertFalse(RenameService.loadAll(workspaceHash).isEmpty());
        String displayName = RenameService.displayClassName("z/b.class", workspaceHash);
        assertFalse("b".equals(displayName), displayName);
        assertTrue(displayName.startsWith("C"), displayName);
        assertTrue(displayName.endsWith("b"), displayName);
    }

    @Test
    void decompilesWhenDeobfuscationIsEnabled(@TempDir Path tmp) throws Exception {
        CompiledSample sample = compileSample(tmp);
        DecompilerContext context = DecompilerContext.of(
                name -> "z/a".equals(name) ? sample.dependencyBytes() : null,
                Map.of(
                        "deobfuscationOn", "true",
                        "deobfuscationMinLength", "3"));

        String source = new JadxDecompiler().decompile(
                "z/b.class", sample.targetBytes(), context);

        assertFalse(source.startsWith("// jadx Error:"), source);
        assertFalse(source.startsWith("// jadx decompile failed"), source);
        assertTrue(source.contains("targetValue"), source);
        assertFalse(source.contains("dependencyValue() {\n        return 41;"), source);
    }

    @Test
    void decompileResultReturnsOkForValidClass(@TempDir Path tmp) throws Exception {
        CompiledSample sample = compileSample(tmp);
        DecompilerContext context = DecompilerContext.of(
                name -> "z/a".equals(name) ? sample.dependencyBytes() : null);
        JadxDecompilerResult result = JadxDecompilerFacade.getInstance()
                .decompileResult(new JadxDecompilerRequest(
                        "z/b", "z/b.class", sample.targetBytes(), context));

        assertEquals(JadxResultStatus.OK, result.status());
        assertTrue(result.isSuccess());
        assertNotNull(result.source());
        assertTrue(result.source().contains("targetValue"));
    }

    @Test
    void decompileResultReturnsExceptionForEmptyBytes() {
        JadxDecompilerResult result = JadxDecompilerFacade.getInstance()
                .decompileResult(new JadxDecompilerRequest(
                        "test/Empty", "", new byte[0], DecompilerContext.EMPTY));

        assertEquals(JadxResultStatus.EXCEPTION, result.status());
        assertFalse(result.isSuccess());
        assertNotNull(result.diagnostic());
    }

    private static CompiledSample compileSample(Path tmp) throws Exception {
        Path srcRoot = tmp.resolve("src");
        Path outRoot = tmp.resolve("classes");
        Path packageDir = srcRoot.resolve("z");
        Files.createDirectories(packageDir);
        Files.createDirectories(outRoot);

        Path source = packageDir.resolve("b.java");
        Files.writeString(source, """
                package z;

                public class b {
                    private final a dep = new a();

                    public int targetValue() {
                        return dep.dependencyValue() + 1;
                    }
                }

                class a {
                    int dependencyValue() {
                        return 41;
                    }
                }
                """);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "tests require a JDK compiler");
        int exit = compiler.run(null, null, null,
                "-d", outRoot.toString(), source.toString());
        assertEquals(0, exit, "test class should compile");

        return new CompiledSample(
                Files.readAllBytes(outRoot.resolve("z/b.class")),
                Files.readAllBytes(outRoot.resolve("z/a.class")));
    }

    private record CompiledSample(byte[] targetBytes, byte[] dependencyBytes) {
    }
}
