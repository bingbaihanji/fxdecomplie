package com.bingbaihanji.fxdecomplie.core.jadx.core.export;

/**
 * Gradle 信息存储器，保存从 APK/资源中提取的、用于生成 Gradle 构建脚本的配置标志。
 */
public class GradleInfoStorage {

    /** 是否使用矢量图 pathData（vector drawable 路径数据） */
    private boolean vectorPathData;

    /** 是否使用矢量图 fillType（vector drawable 填充类型） */
    private boolean vectorFillType;

    /** 是否使用 Apache HTTP legacy 库 */
    private boolean useApacheHttpLegacy;

    /** 资源 ID 是否为非 final（Android Gradle 插件的 nonFinalResIds 选项） */
    private boolean nonFinalResIds;

    /**
     * 是否使用矢量图 pathData。
     */
    public boolean isVectorPathData() {
        return vectorPathData;
    }

    /**
     * 设置是否使用矢量图 pathData。
     */
    public void setVectorPathData(boolean vectorPathData) {
        this.vectorPathData = vectorPathData;
    }

    /**
     * 是否使用矢量图 fillType。
     */
    public boolean isVectorFillType() {
        return vectorFillType;
    }

    /**
     * 设置是否使用矢量图 fillType。
     */
    public void setVectorFillType(boolean vectorFillType) {
        this.vectorFillType = vectorFillType;
    }

    /**
     * 是否使用 Apache HTTP legacy 库。
     */
    public boolean isUseApacheHttpLegacy() {
        return useApacheHttpLegacy;
    }

    /**
     * 设置是否使用 Apache HTTP legacy 库。
     */
    public void setUseApacheHttpLegacy(boolean useApacheHttpLegacy) {
        this.useApacheHttpLegacy = useApacheHttpLegacy;
    }

    /**
     * 资源 ID 是否为非 final。
     */
    public boolean isNonFinalResIds() {
        return nonFinalResIds;
    }

    /**
     * 设置资源 ID 是否为非 final。
     */
    public void setNonFinalResIds(boolean nonFinalResIds) {
        this.nonFinalResIds = nonFinalResIds;
    }
}
