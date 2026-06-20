package com.bingbaihanji.windows.platform;

/**
 * 原生窗口外观请求
 *
 * <p>所有字段均为可选{@code null} 字段表示"保持现有平台值不变"
 * 这使得调用方可以只请求实际需要的属性</p>
 */
public record WindowAppearance(
        Boolean darkMode,
        Boolean shadow,
        WindowBackdropType backdropType,
        WindowCornerPreference cornerPreference,
        Integer borderColor,
        FrameMargins frameMargins
) {

    /**
     * 创建 Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 创建适用于深色对话框的原生外观配置
     *
     * @param borderColor      边框颜色(COLORREF 格式)
     * @param cornerPreference 圆角偏好
     * @return 预设的深色对话框外观
     */
    public static WindowAppearance darkDialog(int borderColor, WindowCornerPreference cornerPreference) {
        return builder()
                .darkMode(true)
                .shadow(true)
                .backdropType(WindowBackdropType.TRANSIENT_WINDOW)
                .cornerPreference(cornerPreference)
                .borderColor(borderColor)
                .extendFrameIntoClientArea()
                .build();
    }

    /**
     * 检查是否设置了任何原生外观属性
     *
     * @return 如果至少有一个属性非 null 则返回 true
     */
    public boolean hasNativeAttributes() {
        return darkMode != null
                || shadow != null
                || backdropType != null
                || cornerPreference != null
                || borderColor != null
                || frameMargins != null;
    }

    /**
     * Win32 风格的窗口边框外边距各边设为 -1 时，DWM 帧将扩展到整个客户区
     */
    public record FrameMargins(int left, int right, int top, int bottom) {
        /**
         * 创建全客户区扩展的边距(四边均为 -1)
         */
        public static FrameMargins fullClientArea() {
            return new FrameMargins(-1, -1, -1, -1);
        }
    }

    /**
     * WindowAppearance 的 Builder
     */
    public static final class Builder {
        private Boolean darkMode;
        private Boolean shadow;
        private WindowBackdropType backdropType;
        private WindowCornerPreference cornerPreference;
        private Integer borderColor;
        private FrameMargins frameMargins;

        private Builder() {
        }

        public Builder darkMode(boolean darkMode) {
            this.darkMode = darkMode;
            return this;
        }

        public Builder shadow(boolean shadow) {
            this.shadow = shadow;
            return this;
        }

        public Builder backdropType(WindowBackdropType backdropType) {
            this.backdropType = backdropType;
            return this;
        }

        public Builder cornerPreference(WindowCornerPreference cornerPreference) {
            this.cornerPreference = cornerPreference;
            return this;
        }

        public Builder borderColor(int borderColor) {
            this.borderColor = borderColor;
            return this;
        }

        public Builder frameMargins(FrameMargins frameMargins) {
            this.frameMargins = frameMargins;
            return this;
        }

        /**
         * 将 DWM 帧扩展到整个客户区
         */
        public Builder extendFrameIntoClientArea() {
            this.frameMargins = FrameMargins.fullClientArea();
            return this;
        }

        public WindowAppearance build() {
            return new WindowAppearance(
                    darkMode,
                    shadow,
                    backdropType,
                    cornerPreference,
                    borderColor,
                    frameMargins);
        }
    }
}
