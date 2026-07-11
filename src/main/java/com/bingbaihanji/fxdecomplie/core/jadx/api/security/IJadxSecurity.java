package com.bingbaihanji.fxdecomplie.core.jadx.api.security;

import com.bingbaihanji.fxdecomplie.core.jadx.zip.security.IJadxZipSecurity;
import org.w3c.dom.Document;

import java.io.InputStream;

public interface IJadxSecurity extends IJadxZipSecurity {

    /**
     * Check if application package is safe
     *
     * @return normalized/sanitized string or same string if safe
     */
    String verifyAppPackage(String appPackage);

    /**
     * XML document parser
     */
    Document parseXml(InputStream in);

    /**
     * Sanitize/escape string to make it safe for use in place described by SanitizeType
     */
    String sanitizeString(String str, SanitizeType type);
}
