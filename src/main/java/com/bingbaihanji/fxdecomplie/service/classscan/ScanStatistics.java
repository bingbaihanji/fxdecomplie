package com.bingbaihanji.fxdecomplie.service.classscan;

public record ScanStatistics(
    int totalClasses,
    int scannedClasses,
    int externalClasses,
    int skippedFiles,
    long elapsedMs
) {}
