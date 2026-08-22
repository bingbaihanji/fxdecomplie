package com.bingbaihanji.fxdecomplie.config;

import com.bingbaihanji.fxdecomplie.context.AppContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigValidator")
class ConfigValidatorTest {

    private AppConfig config;

    @BeforeEach
    void setUp() {
        AppContext.getInstance().start();
        config = AppContext.getInstance().config().copy();
    }

    @Test
    @DisplayName("默认配置应为合法")
    void shouldValidateDefaultConfig() {
        List<String> errors = ConfigValidator.validate(config);
        assertTrue(errors.isEmpty(), "默认配置应合法,但发现: " + errors);
    }

    @Test
    @DisplayName("窗口宽度过小应报错")
    void shouldRejectTooSmallWindowWidth() {
        config.window().width(200);
        List<String> errors = ConfigValidator.validate(config);
        assertFalse(errors.isEmpty(), "宽度 200 应报错");
        assertTrue(errors.stream().anyMatch(e -> e.contains("宽度")),
                "错误信息应包含 '宽度'");
    }

    @Test
    @DisplayName("窗口高度过小应报错")
    void shouldRejectTooSmallWindowHeight() {
        config.window().height(100);
        List<String> errors = ConfigValidator.validate(config);
        assertFalse(errors.isEmpty(), "高度 100 应报错");
        assertTrue(errors.stream().anyMatch(e -> e.contains("高度")),
                "错误信息应包含 '高度'");
    }

    @Test
    @DisplayName("字体超出范围应报错")
    void shouldRejectInvalidFontSize() {
        config.theme().fontSize(100);
        List<String> errors = ConfigValidator.validate(config);
        assertFalse(errors.isEmpty(), "字体 100 应报错");
    }

    @Test
    @DisplayName("isValid 在合法配置时返回 true")
    void shouldReturnTrueForValidConfig() {
        assertTrue(ConfigValidator.isValid(config));
    }

    @Test
    @DisplayName("firstError 在无错误时返回 null")
    void shouldReturnNullForNoErrors() {
        assertNull(ConfigValidator.firstError(config));
    }
}
