package com.bingbaihanji.fxdecomplie.ui.settings;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.ExportConfig;
import com.bingbaihanji.fxdecomplie.service.DiskCodeCache;
import com.bingbaihanji.fxdecomplie.utils.I18nUtil;
import com.bingbaihanji.windows.jfx.DefaultWindowTheme;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 设置对话框 — 分类标签页式配置界面
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class SettingsDialog {

    private static final Logger logger = LoggerFactory.getLogger(SettingsDialog.class);

    private SettingsDialog() {
        throw new AssertionError("utility class");
    }

    public static void show(Stage owner, AppConfig config, Consumer<AppConfig> onApply) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(I18nUtil.getString("menu.edit.settings"));
        dialog.setHeaderText(null);
        dialog.setOnShown(e -> {
            var window = dialog.getDialogPane().getScene().getWindow();
            DefaultWindowTheme.applyWindowDarkMode(window);
            if (window instanceof Stage s) {
                setDialogIcon(s);
            }
        });

        TabPane tabPane = new TabPane();

        // 反编译器标签页
        Tab decompilerTab = new Tab(I18nUtil.getString("settings.decompiler"));
        decompilerTab.setClosable(false);
        ComboBox<String> engineCombo = new ComboBox<>();
        engineCombo.getItems().addAll("PROCYON", "CFR", "VINEFLOWER", "JD");
        engineCombo.setValue(config.decompiler().defaultEngine().name());

        // 引擎选项 JSON 编辑器
        Label engineOptionsLabel = new Label(I18nUtil.getString("settings.decompiler.engineOptions"));
        engineOptionsLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");

        TextArea engineOptionsArea = new TextArea();
        engineOptionsArea.setPromptText(I18nUtil.getString("settings.decompiler.engineOptionsHint"));
        engineOptionsArea.setPrefRowCount(6);
        engineOptionsArea.setStyle("-fx-control-inner-background: #3c3c3c; -fx-text-fill: #cccccc; "
                + "-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px;");

        // 将当前选项加载为 JSON
        try {
            String json = new GsonBuilder().setPrettyPrinting().create()
                    .toJson(config.decompiler().engineOptions());
            engineOptionsArea.setText(("{}".equals(json) || "null".equals(json)) ? "" : json);
        } catch (Exception ignored) {
            logger.debug("序列化引擎选项JSON失败", ignored);
            engineOptionsArea.setText("");
        }

        decompilerTab.setContent(new VBox(10,
                new Label(I18nUtil.getString("settings.defaultEngine")), engineCombo,
                engineOptionsLabel, engineOptionsArea));

        // 界面标签页
        Tab uiTab = new Tab(I18nUtil.getString("settings.ui"));
        uiTab.setClosable(false);
        CheckBox lineNumCheck = new CheckBox(I18nUtil.getString("settings.lineNumbers"));
        lineNumCheck.setSelected(config.decompiler().lineNumbersEnabled());
        CheckBox wrapCheck = new CheckBox(I18nUtil.getString("settings.wordWrap"));
        wrapCheck.setSelected(config.decompiler().wrapText());

        Spinner<Integer> fontSizeSpinner = new Spinner<>(8, 48,
                Math.clamp(config.theme().fontSize(), 8, 48), 1);
        fontSizeSpinner.setEditable(true);
        fontSizeSpinner.getEditor().setTextFormatter(
                new javafx.scene.control.TextFormatter<>(change -> {
                    String newText = change.getControlNewText();
                    if (newText.matches("\\d{1,2}")) return change;
                    return null;
                }));
        fontSizeSpinner.setPrefWidth(80);

        ComboBox<String> fontFamilyCombo = new ComboBox<>();
        fontFamilyCombo.getItems().addAll(
                "Consolas", "Fira Code", "JetBrains Mono", "Cascadia Code",
                "Source Code Pro", "Courier New", "Monaco", "Menlo",
                "DejaVu Sans Mono", "Microsoft YaHei Mono"
        );
        fontFamilyCombo.setValue(config.theme().fontFamily() != null
                && !config.theme().fontFamily().isBlank()
                ? config.theme().fontFamily() : "Consolas");
        fontFamilyCombo.setEditable(true);

        uiTab.setContent(new VBox(10,
                new Label(I18nUtil.getString("settings.fontSize")), fontSizeSpinner,
                new Label(I18nUtil.getString("settings.fontFamily")), fontFamilyCombo,
                lineNumCheck, wrapCheck));

        // 搜索标签页
        Tab searchTab = new Tab(I18nUtil.getString("settings.search"));
        searchTab.setClosable(false);
        CheckBox fullSourceSearchCheck = new CheckBox(I18nUtil.getString("settings.search.fullSource"));
        fullSourceSearchCheck.setSelected(config.search().fullSourceSearch());
        Spinner<Integer> resultLimitSpinner = new Spinner<>(50, 2000,
                Math.clamp(config.search().resultLimit(), 50, 2000), 50);
        resultLimitSpinner.setEditable(true);
        // 验证数字输入,防止 NumberFormatException
        resultLimitSpinner.getEditor().setTextFormatter(
                new javafx.scene.control.TextFormatter<>(change -> {
                    String newText = change.getControlNewText();
                    if (newText.isEmpty() || newText.matches("\\d{1,4}")) {
                        return change;
                    }
                    return null;
                }));
        searchTab.setContent(new VBox(10,
                fullSourceSearchCheck,
                new Label(I18nUtil.getString("settings.search.resultLimit")),
                resultLimitSpinner));

        // 导出标签页
        Tab exportTab = new Tab(I18nUtil.getString("settings.export"));
        exportTab.setClosable(false);
        ComboBox<String> exportEngineCombo = new ComboBox<>();
        exportEngineCombo.getItems().addAll(exportEngineOptions());
        exportEngineCombo.setValue(exportEngineValue(config.export().defaultEngine()));
        ComboBox<String> exportFormatCombo = new ComboBox<>();
        exportFormatCombo.getItems().addAll("DIR", "ZIP");
        exportFormatCombo.setValue(config.export().defaultFormat());
        ComboBox<String> conflictCombo = new ComboBox<>();
        conflictCombo.getItems().addAll("SKIP", "OVERWRITE", "RENAME");
        conflictCombo.setValue(config.export().conflictPolicy());
        CheckBox exportResourcesCheck = new CheckBox(I18nUtil.getString("settings.export.resources"));
        exportResourcesCheck.setSelected(config.export().exportResources());
        TextField exportPathField = new TextField(config.export().lastPath());
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
            alert.setOnShown(ev -> DefaultWindowTheme
                    .applyWindowDarkMode(alert.getDialogPane().getScene().getWindow()));
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

        Button restoreDefaultsBtn = new Button(I18nUtil.getString("settings.restoreDefaults"));
        restoreDefaultsBtn.setStyle("-fx-font-size: 12px; -fx-text-fill: #f44747;");
        restoreDefaultsBtn.setOnAction(e -> {
            engineCombo.setValue("VINEFLOWER");
            lineNumCheck.setSelected(true);
            wrapCheck.setSelected(true);
            fontSizeSpinner.getValueFactory().setValue(14);
            fontFamilyCombo.setValue("Consolas");
            fullSourceSearchCheck.setSelected(false);
            resultLimitSpinner.getValueFactory().setValue(200);
            exportEngineCombo.setValue("FOLLOW_CURRENT");
            exportFormatCombo.setValue("DIR");
            conflictCombo.setValue("OVERWRITE");
            exportResourcesCheck.setSelected(true);
            exportPathField.setText("");
            engineOptionsArea.setText("");
            langCombo.setValue("简体中文");
        });

        VBox content = new VBox(10, tabPane, restoreDefaultsBtn);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);

        var result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.APPLY) {
            config.decompiler().defaultEngine(DecompilerTypeEnum.valueOf(engineCombo.getValue()));
            config.decompiler().lineNumbersEnabled(lineNumCheck.isSelected());
            config.decompiler().wrapText(wrapCheck.isSelected());
            config.theme().fontSize(fontSizeSpinner.getValue());
            config.theme().fontFamily(fontFamilyCombo.getValue());
            config.search().fullSourceSearch(fullSourceSearchCheck.isSelected());
            config.search().resultLimit(resultLimitSpinner.getValue());
            config.export().defaultEngine("FOLLOW_CURRENT".equals(exportEngineCombo.getValue())
                    ? "" : exportEngineCombo.getValue());
            config.export().defaultFormat(safeEnum(exportFormatCombo.getValue(),
                    ExportConfig.Format.DIR).name());
            config.export().conflictPolicy(safeEnum(conflictCombo.getValue(),
                    ExportConfig.ConflictPolicy.OVERWRITE).name());
            config.export().exportResources(exportResourcesCheck.isSelected());
            config.export().lastPath(exportPathField.getText() == null
                    ? "" : exportPathField.getText());

            // 保存引擎选项
            try {
                String json = engineOptionsArea.getText();
                if (json != null && !json.isBlank()) {
                    Map<String, Map<String, String>> opts =
                            new Gson().fromJson(json,
                                    new TypeToken<Map<String, Map<String, String>>>() {
                                    }.getType());
                    config.decompiler().engineOptions().clear();
                    config.decompiler().engineOptions().putAll(opts);
                } else {
                    config.decompiler().engineOptions().clear();
                }
            } catch (Exception ignored) {
                logger.debug("解析引擎选项JSON失败，保留现有选项", ignored);
                // JSON 无效 — 保留现有选项
            }

            // 应用语言变更
            int langIdx = langCombo.getSelectionModel().getSelectedIndex();
            Locale newLocale = (langIdx == 1) ? Locale.ENGLISH : Locale.SIMPLIFIED_CHINESE;
            config.language(langIdx == 1 ? "en" : "zh-CN");
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

    private static void setDialogIcon(javafx.stage.Stage stage) {
        try (var stream = SettingsDialog.class.getResourceAsStream("/icon/logo.png")) {
            if (stream != null) {
                stage.getIcons().add(new javafx.scene.image.Image(stream));
            }
        } catch (Exception ignored) {
            logger.debug("设置对话框图标失败", ignored);
        }
    }
}
