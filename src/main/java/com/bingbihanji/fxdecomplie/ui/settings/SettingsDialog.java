package com.bingbihanji.fxdecomplie.ui.settings;

import com.bingbihanji.fxdecomplie.config.AppConfig;
import com.bingbihanji.fxdecomplie.utils.I18nUtil;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * 设置对话框 — 分类标签页式配置界面。
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class SettingsDialog {

    private SettingsDialog() { throw new AssertionError("utility class"); }

    public static void show(Stage owner, AppConfig config, Consumer<AppConfig> onApply) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(I18nUtil.getString("menu.edit.settings"));
        dialog.setHeaderText(null);

        TabPane tabPane = new TabPane();

        // 反编译器标签页
        Tab decompilerTab = new Tab(I18nUtil.getString("settings.decompiler"));
        decompilerTab.setClosable(false);
        ComboBox<String> engineCombo = new ComboBox<>();
        engineCombo.getItems().addAll("PROCYON", "CFR", "VINEFLOWER", "JD");
        engineCombo.setValue(config.decompiler.defaultEngine);
        decompilerTab.setContent(new VBox(10,
                new Label(I18nUtil.getString("settings.defaultEngine")), engineCombo));

        // 界面标签页
        Tab uiTab = new Tab(I18nUtil.getString("settings.ui"));
        uiTab.setClosable(false);
        CheckBox lineNumCheck = new CheckBox(I18nUtil.getString("settings.lineNumbers"));
        lineNumCheck.setSelected(config.decompiler.lineNumbersEnabled);
        CheckBox wrapCheck = new CheckBox(I18nUtil.getString("settings.wordWrap"));
        wrapCheck.setSelected(config.decompiler.wrapText);
        uiTab.setContent(new VBox(10, lineNumCheck, wrapCheck));

        // 语言标签页
        Tab langTab = new Tab(I18nUtil.getString("settings.language"));
        langTab.setClosable(false);
        ComboBox<String> langCombo = new ComboBox<>();
        langCombo.getItems().addAll("简体中文", "English");
        langCombo.setValue("简体中文");
        langTab.setContent(new VBox(10, new Label(I18nUtil.getString("settings.uiLang")), langCombo));

        tabPane.getTabs().addAll(decompilerTab, uiTab, langTab);
        dialog.getDialogPane().setContent(tabPane);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);

        var result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.APPLY) {
            config.decompiler.defaultEngine = engineCombo.getValue();
            config.decompiler.lineNumbersEnabled = lineNumCheck.isSelected();
            config.decompiler.wrapText = wrapCheck.isSelected();

            // Apply language change
            String selectedLang = langCombo.getValue();
            Locale newLocale = "English".equals(selectedLang)
                    ? Locale.ENGLISH
                    : Locale.SIMPLIFIED_CHINESE;
            I18nUtil.switchLocale(newLocale);

            config.save();
            onApply.accept(config);
        }
    }
}
