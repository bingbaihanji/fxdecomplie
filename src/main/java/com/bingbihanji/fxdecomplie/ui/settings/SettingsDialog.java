package com.bingbihanji.fxdecomplie.ui.settings;

import com.bingbihanji.fxdecomplie.config.AppConfig;
import com.bingbihanji.fxdecomplie.model.ExportConfig;
import com.bingbihanji.fxdecomplie.service.DiskCodeCache;
import com.bingbihanji.fxdecomplie.utils.I18nUtil;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 设置对话框 — 分类标签页式配置界面。
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class SettingsDialog {

    private SettingsDialog() {
        throw new AssertionError("utility class");
    }

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

        // 搜索标签页
        Tab searchTab = new Tab(I18nUtil.getString("settings.search"));
        searchTab.setClosable(false);
        CheckBox fullSourceSearchCheck = new CheckBox(I18nUtil.getString("settings.search.fullSource"));
        fullSourceSearchCheck.setSelected(config.search.fullSourceSearch);
        Spinner<Integer> resultLimitSpinner = new Spinner<>(50, 2000,
                Math.clamp(config.search.resultLimit, 50, 2000), 50);
        resultLimitSpinner.setEditable(true);
        searchTab.setContent(new VBox(10,
                fullSourceSearchCheck,
                new Label(I18nUtil.getString("settings.search.resultLimit")),
                resultLimitSpinner));

        // 导出标签页
        Tab exportTab = new Tab(I18nUtil.getString("settings.export"));
        exportTab.setClosable(false);
        ComboBox<String> exportEngineCombo = new ComboBox<>();
        exportEngineCombo.getItems().addAll(exportEngineOptions());
        exportEngineCombo.setValue(exportEngineValue(config.export.defaultEngine));
        ComboBox<String> exportFormatCombo = new ComboBox<>();
        exportFormatCombo.getItems().addAll("DIR", "ZIP");
        exportFormatCombo.setValue(config.export.defaultFormat);
        ComboBox<String> conflictCombo = new ComboBox<>();
        conflictCombo.getItems().addAll("SKIP", "OVERWRITE", "RENAME");
        conflictCombo.setValue(config.export.conflictPolicy);
        CheckBox exportResourcesCheck = new CheckBox(I18nUtil.getString("settings.export.resources"));
        exportResourcesCheck.setSelected(config.export.exportResources);
        TextField exportPathField = new TextField(config.export.lastPath);
        exportPathField.setPromptText(I18nUtil.getString("settings.export.path"));
        exportTab.setContent(new VBox(10,
                new Label(I18nUtil.getString("dialog.export.engine")), exportEngineCombo,
                new Label(I18nUtil.getString("dialog.export.format")), exportFormatCombo,
                new Label(I18nUtil.getString("dialog.export.conflict")), conflictCombo,
                exportResourcesCheck,
                new Label(I18nUtil.getString("settings.export.path")), exportPathField));

        // 缓存标签页
        Tab cacheTab = new Tab(I18nUtil.getString("settings.cache"));
        cacheTab.setClosable(false);
        Label cachePath = new Label(DiskCodeCache.cacheRoot().toString());
        cachePath.setWrapText(true);
        Button cleanCacheButton = new Button(I18nUtil.getString("settings.cache.clean"));
        cleanCacheButton.setOnAction(event -> {
            DiskCodeCache.cleanAll();
            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    I18nUtil.getString("settings.cache.cleaned"));
            alert.initOwner(owner);
            alert.setHeaderText(null);
            alert.setTitle(I18nUtil.getString("settings.cache"));
            alert.showAndWait();
        });
        cacheTab.setContent(new VBox(10,
                new Label(I18nUtil.getString("settings.cache.path")),
                cachePath,
                new HBox(cleanCacheButton)));

        // 语言标签页
        Tab langTab = new Tab(I18nUtil.getString("settings.language"));
        langTab.setClosable(false);
        ComboBox<String> langCombo = new ComboBox<>();
        langCombo.getItems().addAll("简体中文", "English");
        langCombo.setValue(Locale.ENGLISH.getLanguage().equals(I18nUtil.getCurrentLocale().getLanguage())
                ? "English" : "简体中文");
        langTab.setContent(new VBox(10, new Label(I18nUtil.getString("settings.uiLang")), langCombo));

        tabPane.getTabs().addAll(decompilerTab, uiTab, searchTab, exportTab, cacheTab, langTab);
        dialog.getDialogPane().setContent(tabPane);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);

        var result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.APPLY) {
            config.decompiler.defaultEngine = engineCombo.getValue();
            config.decompiler.lineNumbersEnabled = lineNumCheck.isSelected();
            config.decompiler.wrapText = wrapCheck.isSelected();
            config.search.fullSourceSearch = fullSourceSearchCheck.isSelected();
            config.search.resultLimit = resultLimitSpinner.getValue();
            config.export.defaultEngine = "FOLLOW_CURRENT".equals(exportEngineCombo.getValue())
                    ? "" : exportEngineCombo.getValue();
            config.export.defaultFormat = safeEnum(exportFormatCombo.getValue(),
                    ExportConfig.Format.DIR).name();
            config.export.conflictPolicy = safeEnum(conflictCombo.getValue(),
                    ExportConfig.ConflictPolicy.OVERWRITE).name();
            config.export.exportResources = exportResourcesCheck.isSelected();
            config.export.lastPath = exportPathField.getText() == null
                    ? "" : exportPathField.getText();

            // Apply language change
            String selectedLang = langCombo.getValue();
            Locale newLocale = "English".equals(selectedLang)
                    ? Locale.ENGLISH
                    : Locale.SIMPLIFIED_CHINESE;
            config.language = "English".equals(selectedLang) ? "en" : "zh-CN";
            I18nUtil.switchLocale(newLocale);

            config.save();
            onApply.accept(config);
        }
    }

    private static List<String> exportEngineOptions() {
        List<String> options = new ArrayList<>();
        options.add("FOLLOW_CURRENT");
        options.add("PROCYON");
        options.add("CFR");
        options.add("VINEFLOWER");
        options.add("JD");
        return options;
    }

    private static String exportEngineValue(String value) {
        return value == null || value.isBlank() ? "FOLLOW_CURRENT" : value;
    }

    private static <E extends Enum<E>> E safeEnum(String value, E fallback) {
        try {
            return Enum.valueOf(fallback.getDeclaringClass(), value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return fallback;
        }
    }
}
