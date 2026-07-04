package com.bingbaihanji.fxdecomplie.ui;

import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 应用图标工具类
 *
 * @author bingbaihanji
 * @date 2026-07-02
 */
public final class IconHelper {

    private static final Logger log = LoggerFactory.getLogger(IconHelper.class);
    private static final String LOGO_PATH = "/icon/logo.png";
    private static Image cachedLogo;

    private IconHelper() {
        throw new AssertionError("utility class");
    }

    /** 为 Stage 设置应用 Logo 图标 */
    public static void setStageIcon(Stage stage) {
        if (stage == null) {
            return;
        }
        Image logo = getLogoImage();
        if (logo != null && !stage.getIcons().contains(logo)) {
            stage.getIcons().add(logo);
        }
    }

    /** @return 缓存的 Logo 图像，可用于 ImageView 等组件 */
    public static Image getLogoImage() {
        if (cachedLogo == null) {
            try (var stream = IconHelper.class.getResourceAsStream(LOGO_PATH)) {
                if (stream != null) {
                    cachedLogo = new Image(stream);
                }
            } catch (Exception ignored) {
                log.debug("加载 Logo 图标失败", ignored);
            }
        }
        return cachedLogo;
    }
}
