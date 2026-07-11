package com.bingbaihanji.fxdecomplie.core.jadx.zip;

import com.bingbaihanji.fxdecomplie.core.jadx.zip.security.IJadxZipSecurity;
import com.bingbaihanji.fxdecomplie.core.jadx.zip.security.JadxZipSecurity;

import java.util.Set;

public class ZipReaderOptions {

    private final IJadxZipSecurity zipSecurity;
    private final Set<ZipReaderFlags> flags;
    public ZipReaderOptions(IJadxZipSecurity zipSecurity, Set<ZipReaderFlags> flags) {
        this.zipSecurity = zipSecurity;
        this.flags = flags;
    }

    public static ZipReaderOptions getDefault() {
        return new ZipReaderOptions(new JadxZipSecurity(), ZipReaderFlags.none());
    }

    public IJadxZipSecurity getZipSecurity() {
        return zipSecurity;
    }

    public Set<ZipReaderFlags> getFlags() {
        return flags;
    }
}
