package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.ExportConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportSinkTest {

    @TempDir
    Path tempDir;

    private static ExportConfig config(Path output, ExportConfig.Format format) {
        return new ExportConfig(output, DecompilerTypeEnum.VINEFLOWER, format,
                ExportConfig.ConflictPolicy.OVERWRITE, true);
    }

    private static ExportService.ExportContent content(String path, String text) {
        return new ExportService.ExportContent(path,
                text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void dirSinkWritesImmediately() throws Exception {
        Path out = tempDir.resolve("dir");
        try (ExportSink sink = ExportWriter.openSink(config(out, ExportConfig.Format.DIR))) {
            assertTrue(sink.write(content("a/b.txt", "hello")));
            assertTrue(Files.exists(out.resolve("a/b.txt")), "file should exist before close");
            assertEquals("hello", Files.readString(out.resolve("a/b.txt")));

            assertTrue(sink.write(content("c/d.txt", "world")));
            assertEquals("world", Files.readString(out.resolve("c/d.txt")));
        }
    }

    @Test
    void zipSinkWritesEntriesInOrder() throws Exception {
        Path zip = tempDir.resolve("out.zip");
        try (ExportSink sink = ExportWriter.openSink(config(zip, ExportConfig.Format.ZIP))) {
            assertTrue(sink.write(new ExportService.ExportContent("z.txt", new byte[]{1})));
            assertTrue(sink.write(new ExportService.ExportContent("a.txt", new byte[]{2})));
        }
        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            var entries = zipFile.entries();
            var first = entries.nextElement();
            assertEquals("z.txt", first.getName());
            assertArrayEquals(new byte[]{1}, zipFile.getInputStream(first).readAllBytes());
            var second = entries.nextElement();
            assertEquals("a.txt", second.getName());
            assertArrayEquals(new byte[]{2}, zipFile.getInputStream(second).readAllBytes());
        }
    }

    @Test
    void unsafePathsBecomeIoExceptionAndDirSinkStaysUsable() throws Exception {
        Path out = tempDir.resolve("unsafe-dir");
        try (ExportSink sink = ExportWriter.openSink(config(out, ExportConfig.Format.DIR))) {
            assertThrows(IOException.class,
                    () -> sink.write(content("../evil.properties", "bad")));
            assertTrue(sink.write(content("ok.txt", "ok")));
        }
        assertTrue(Files.exists(out.resolve("ok.txt")));

        Path zip = tempDir.resolve("unsafe.zip");
        try (ExportSink sink = ExportWriter.openSink(config(zip, ExportConfig.Format.ZIP))) {
            assertThrows(IOException.class,
                    () -> sink.write(content("../evil.properties", "bad")));
        }
    }
}
