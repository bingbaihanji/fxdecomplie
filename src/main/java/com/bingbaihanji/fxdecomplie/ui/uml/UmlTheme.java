package com.bingbaihanji.fxdecomplie.ui.uml;

/**
 * UML 类图亮/暗色板。
 * 颜色取自参考项目 class-diagram-gen 的 PlantUML skinparam。
 */
public record UmlTheme(
        boolean dark,
        String background,
        String nodeBackground,
        String nodeBorder,
        String titleText,
        String packageText,
        String memberText,
        String classHeader,
        String abstractClassHeader,
        String interfaceHeader,
        String enumHeader,
        String annotationHeader,
        String recordHeader,
        String markerBackground,
        String generalizationColor,
        String realizationColor,
        String associationColor,
        String dependencyColor
) {

    public static final UmlTheme LIGHT = new UmlTheme(
            false,
            "#FFFFFF",
            "#FFFFFF",
            "#A0A7B3",
            "#1F2430",
            "#8C9BAC",
            "#2E3440",
            "#EDF2FA",
            "#EAF0FF",
            "#E9F4EC",
            "#FDF3E0",
            "#F3E9FA",
            "#E7F7F4",
            "#EDEDED",
            "#000082",
            "#008200",
            "#595959",
            "#595959");

    public static final UmlTheme DARK = new UmlTheme(
            true,
            "#1E1E1E",
            "#3C3F41",
            "#6A6F73",
            "#E6E6E6",
            "#8C8C8C",
            "#BBBBBB",
            "#4B5053",
            "#364A63",
            "#3D5A46",
            "#5A4A30",
            "#4A3A52",
            "#2F5B56",
            "#3A3D40",
            "#6C8CFF",
            "#4CAF50",
            "#9E9E9E",
            "#9E9E9E");
}
