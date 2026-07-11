package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.utils;

import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxDecompiler;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.JadxPluginContext;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;
import com.bingbaihanji.fxdecomplie.core.jadx.zip.IZipEntry;
import com.bingbaihanji.fxdecomplie.core.jadx.zip.ZipReader;
import com.bingbaihanji.fxdecomplie.core.jadx.zip.io.LimitedInputStream;
import com.bingbaihanji.fxdecomplie.core.jadx.zip.security.DisabledZipSecurity;
import com.bingbaihanji.fxdecomplie.core.jadx.zip.security.IJadxZipSecurity;
import com.bingbaihanji.fxdecomplie.core.jadx.zip.security.JadxZipSecurity;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Deprecated, migrate to {@link ZipReader}. <br>
 * Prefer already configured instance from {@link JadxDecompiler#getZipReader()} or
 * {@link JadxPluginContext#getZipReader()}.
 */
@Deprecated
public class ZipSecurity {
    private static final boolean DISABLE_CHECKS = "true".equalsIgnoreCase(System.getenv("JADX_DISABLE_ZIP_SECURITY"));

    private static final int MAX_ENTRIES_COUNT = parseEnvInt("JADX_ZIP_MAX_ENTRIES_COUNT", 100_000);

    private static int parseEnvInt(String name, int def) {
        String v = System.getenv(name);
        return v != null ? Integer.parseInt(v) : def;
    }

    private static final IJadxZipSecurity ZIP_SECURITY = buildZipSecurity();

    private static final ZipReader ZIP_READER = new ZipReader(ZIP_SECURITY);

    private ZipSecurity() {
    }

    private static IJadxZipSecurity buildZipSecurity() {
        if (DISABLE_CHECKS) {
            return DisabledZipSecurity.INSTANCE;
        }
        JadxZipSecurity jadxZipSecurity = new JadxZipSecurity();
        jadxZipSecurity.setMaxEntriesCount(MAX_ENTRIES_COUNT);
        return jadxZipSecurity;
    }

    public static boolean isInSubDirectory(File baseDir, File file) {
        return ZIP_SECURITY.isInSubDirectory(baseDir, file);
    }

    /**
     * Checks that entry name contains no any traversals and prevents cases like "../classes.dex",
     * to limit output only to the specified directory
     */
    public static boolean isValidZipEntryName(String entryName) {
        return ZIP_SECURITY.isValidEntryName(entryName);
    }

    public static boolean isZipBomb(IZipEntry entry) {
        return !ZIP_SECURITY.isValidEntry(entry);
    }

    public static boolean isValidZipEntry(IZipEntry entry) {
        return ZIP_SECURITY.isValidEntry(entry);
    }

    public static InputStream getInputStreamForEntry(ZipFile zipFile, ZipEntry entry) throws IOException {
        if (DISABLE_CHECKS) {
            return new BufferedInputStream(zipFile.getInputStream(entry));
        }
        InputStream in = zipFile.getInputStream(entry);
        LimitedInputStream limited = new LimitedInputStream(in, entry.getSize());
        return new BufferedInputStream(limited);
    }

    /**
     * Visit valid entries in a zip file.
     * Return not null value from visitor to stop iteration.
     */
    @Nullable
    public static <R> R visitZipEntries(File file, Function<IZipEntry, R> visitor) {
        return ZIP_READER.visitEntries(file, visitor);
    }

    public static void readZipEntries(File file, BiConsumer<IZipEntry, InputStream> visitor) {
        ZIP_READER.readEntries(file, visitor);
    }
}
