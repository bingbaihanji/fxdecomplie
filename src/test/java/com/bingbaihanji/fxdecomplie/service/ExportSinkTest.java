package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.ExportConfig;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportSinkTest {

    @TempDir
    Path tempDir;

    private static ExportConfig config(Path output, ExportConfig.Format format) {
        return new ExportConfig(output, DecompilerTypeEnum.VINEFLOWER, format,
                ExportConfig.ConflictPolicy.OVERWRITE, true);
    }

    private static FileTreeNode node(String path) {
        return new FileTreeNode(path.substring(path.lastIndexOf('/') + 1), path,
                FileTreeNode.NodeTypeEnum.RESOURCE);
    }

    @Test
    void dirSinkWritesImmediately() throws Exception {
        Path out = tempDir.resolve("dir");
        try (ExportSink sink = ExportWriter.openSink(config(out, ExportConfig.Format.DIR))) {
            assertTrue(sink.write(node("a/b.txt"), new ExportService.ExportContent(
                    "a/b.txt", "hello".getBytes(StandardCharsets.UTF_8))));
        }
        assertEquals("hello", Files.readString(out.resolve("a/b.txt")));
    }

    @Test
    void zipSinkWritesEntriesInOrder() throws Exception {
        Path zip = tempDir.resolve("out.zip");
        try (ExportSink sink = ExportWriter.openSink(config(zip, ExportConfig.Format.ZIP))) {
            assertTrue(sink.write(node("z.txt"),
                    new ExportService.ExportContent("z.txt", new byte[]{1})));
            assertTrue(sink.write(node("a.txt"),
                    new ExportService.ExportContent("a.txt", new byte[]{2})));
        }
        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            var entries = zipFile.entries();
            assertEquals("z.txt", entries.nextElement().getName());
            assertEquals("a.txt", entries.nextElement().getName());
        }
    }
}
