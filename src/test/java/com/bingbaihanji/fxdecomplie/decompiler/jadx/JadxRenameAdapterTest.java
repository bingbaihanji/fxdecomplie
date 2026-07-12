package com.bingbaihanji.fxdecomplie.decompiler.jadx;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JadxRenameAdapterTest {

    @Test
    void emptyAdapterIsNoOp() {
        String source = "package com.example;\n\npublic class Foo {\n}\n";
        assertEquals(source, JadxRenameAdapter.EMPTY.applyRenames(source));
    }

    @Test
    void applyRenamesReplacesInternalNameWithProjectName() {
        JadxRenameAdapter adapter = JadxRenameAdapter.of(
                Map.of("com/example/OldName", "com.example.NewName"));

        // Simulate decompiled source referencing the old class
        String source = "import com.example.OldName;\n\n"
                + "public class Client {\n"
                + "    private com.example.OldName field;\n"
                + "    public com.example.OldName get() { return new OldName(); }\n"
                + "}\n";
        String result = adapter.applyRenames(source);

        assertTrue(result.contains("com.example.NewName"),
                "qualified references should be renamed:\n" + result);
        assertFalse(result.contains("com.example.OldName"),
                "old qualified name should not remain:\n" + result);
        // Simple name in constructor call should survive unchanged
        assertTrue(result.contains("new OldName()"),
                "simple name in constructor should not be touched:\n" + result);
    }

    @Test
    void applyRenamesHandlesNullSource() {
        JadxRenameAdapter adapter = JadxRenameAdapter.of(
                Map.of("a/b/C", "x.y.Z"));
        assertNull(adapter.applyRenames(null));
    }

    @Test
    void snapshotHashIsStable() {
        JadxRenameAdapter a = JadxRenameAdapter.of(Map.of("a/b/C", "x.y.Z"));
        JadxRenameAdapter b = JadxRenameAdapter.of(Map.of("a/b/C", "x.y.Z"));

        assertEquals(a.snapshotHash(), b.snapshotHash());
    }

    @Test
    void snapshotHashDiffersForDifferentMaps() {
        JadxRenameAdapter a = JadxRenameAdapter.of(Map.of("a/b/C", "x.y.Z"));
        JadxRenameAdapter b = JadxRenameAdapter.of(Map.of("d/e/F", "p.q.R"));

        assertNotEquals(a.snapshotHash(), b.snapshotHash());
    }

    @Test
    void emptyAdapterHashIsConstant() {
        assertEquals("no-rename", JadxRenameAdapter.EMPTY.snapshotHash());
    }
}
