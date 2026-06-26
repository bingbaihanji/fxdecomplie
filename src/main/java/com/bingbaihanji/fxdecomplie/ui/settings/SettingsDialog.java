package com.bingbaihanji.fxdecomplie.ui.settings;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.decompiler.CfrParameters;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.decompiler.ProcyonParameters;
import com.bingbaihanji.fxdecomplie.decompiler.VineflowerParameters;
import com.bingbaihanji.fxdecomplie.model.DecompilerParameter;
import com.bingbaihanji.fxdecomplie.model.DecompilerParameter.ParamType;
import com.bingbaihanji.fxdecomplie.model.ExportConfig;
import com.bingbaihanji.fxdecomplie.service.DiskCodeCache;
import com.bingbaihanji.fxdecomplie.utils.I18nUtil;
import com.bingbaihanji.windows.jfx.DefaultWindowTheme;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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

        // ── 反编译器标签页 ──
        Tab decompilerTab = new Tab(I18nUtil.getString("settings.decompiler"));
        decompilerTab.setClosable(false);

        // 默认引擎选择（现有控件，保持不变）
        ComboBox<String> engineCombo = new ComboBox<>();
        engineCombo.getItems().addAll("PROCYON", "CFR", "VINEFLOWER", "JD");
        engineCombo.setValue(config.decompiler().defaultEngine().name());

        // 引擎选项 JSON 编辑器（移到独立 TitledPane 中，默认折叠）
        Label engineOptionsLabel = new Label(I18nUtil.getString("settings.decompiler.engineOptions"));
        engineOptionsLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");

        TextArea engineOptionsArea = new TextArea();
        engineOptionsArea.setPromptText(I18nUtil.getString("settings.decompiler.engineOptionsHint"));
        engineOptionsArea.setPrefRowCount(6);
        engineOptionsArea.setStyle("-fx-control-inner-background: #3c3c3c; -fx-text-fill: #cccccc; "
                + "-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px;");

        // 初始化 JSON
        refreshEngineOptionsJson(config, engineOptionsArea);

        // 跟踪每个引擎的参数控件 (key → control)，用于 JSON → 面板回填
        Map<String, Map<String, javafx.scene.Node>> engineControlMaps = new LinkedHashMap<>();
        engineControlMaps.put("CFR", new LinkedHashMap<>());
        engineControlMaps.put("PROCYON", new LinkedHashMap<>());
        engineControlMaps.put("VINEFLOWER", new LinkedHashMap<>());

        // ── 引擎参数子标签页 ──
        TabPane engineTabPane = new TabPane();

        // CFR 面板
        Tab cfrTab = new Tab("CFR");
        cfrTab.setClosable(false);
        cfrTab.setContent(buildEngineParameterPanel(config, "CFR", CfrParameters.PARAMETERS,
                engineOptionsArea, engineControlMaps.get("CFR")));

        // Procyon 面板
        Tab procyonTab = new Tab("Procyon");
        procyonTab.setClosable(false);
        procyonTab.setContent(buildEngineParameterPanel(config, "PROCYON", ProcyonParameters.PARAMETERS,
                engineOptionsArea, engineControlMaps.get("PROCYON")));

        // Vineflower 面板
        Tab vfTab = new Tab("Vineflower");
        vfTab.setClosable(false);
        vfTab.setContent(buildEngineParameterPanel(config, "VINEFLOWER", VineflowerParameters.PARAMETERS,
                engineOptionsArea, engineControlMaps.get("VINEFLOWER")));

        engineTabPane.getTabs().addAll(cfrTab, procyonTab, vfTab);

        // JSON 编辑器折叠面板
        TitledPane jsonPane = new TitledPane();
        jsonPane.setText(I18nUtil.getStringOrDefault("settings.decompiler.engineOptionsJson", "Engine Options JSON (Advanced)"));
        jsonPane.setExpanded(false);
        jsonPane.setContent(new VBox(5, engineOptionsLabel, engineOptionsArea));

        VBox decompilerContent = new VBox(10,
                new Label(I18nUtil.getString("settings.defaultEngine")), engineCombo,
                engineTabPane,
                jsonPane);
        decompilerTab.setContent(decompilerContent);

        // JSON 失焦 → 解析 → 回填面板控件
        engineOptionsArea.focusedProperty().addListener((obs, old, focused) -> {
            if (!focused) {
                String jsonText = engineOptionsArea.getText();
                if (jsonText == null || jsonText.isBlank()) return;
                try {
                    Map<String, Map<String, String>> allOpts = new Gson().fromJson(jsonText,
                            new TypeToken<Map<String, Map<String, String>>>(){}.getType());
                    String activeEngine = getActiveEngineName(engineTabPane);
                    Map<String, String> engineOpts = allOpts.getOrDefault(activeEngine, Map.of());
                    Map<String, javafx.scene.Node> controls = engineControlMaps.get(activeEngine);
                    if (controls != null) {
                        for (var entry : engineOpts.entrySet()) {
                            javafx.scene.Node ctrl = controls.get(entry.getKey());
                            if (ctrl != null) {
                                setControlValue(ctrl, entry.getValue());
                            }
                        }
                    }
                    engineOptionsArea.setStyle("-fx-control-inner-background: #3c3c3c; -fx-text-fill: #cccccc; "
                            + "-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px;");
                } catch (Exception ex) {
                    engineOptionsArea.setStyle("-fx-control-inner-background: #3c3c3c; -fx-text-fill: #cccccc; "
                            + "-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px; "
                            + "-fx-border-color: #f44747; -fx-border-width: 1px;");
                }
            }
        });

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
            com.bingbaihanji.fxdecomplie.ui.DialogHelper.showInfo(
                    owner,
                    I18nUtil.getString("settings.cache"),
                    I18nUtil.getString("settings.cache.cleaned"));
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

            // 保存引擎选项（已通过面板控件实时更新到 engineControlMaps + engineOptions Map）
            try {
                for (var engineEntry : engineControlMaps.entrySet()) {
                    String engName = engineEntry.getKey();
                    Map<String, String> engOpts = config.decompiler().engineOptions()
                            .computeIfAbsent(engName, k -> new LinkedHashMap<>());
                    for (var ctrlEntry : engineEntry.getValue().entrySet()) {
                        String key = ctrlEntry.getKey();
                        String val = readControlValue(ctrlEntry.getValue());
                        if (val != null && !val.isEmpty()) {
                            engOpts.put(key, val);
                        }
                    }
                }
                // Also merge JSON area if user manually edited it
                String json = engineOptionsArea.getText();
                if (json != null && !json.isBlank()) {
                    try {
                        Map<String, Map<String, String>> jsonOpts = new Gson().fromJson(json,
                                new TypeToken<Map<String, Map<String, String>>>(){}.getType());
                        for (var entry : jsonOpts.entrySet()) {
                            config.decompiler().engineOptions()
                                    .computeIfAbsent(entry.getKey(), k -> new LinkedHashMap<>())
                                    .putAll(entry.getValue());
                        }
                    } catch (Exception ignored) {}
                }
            } catch (Exception ex) {
                logger.warn("保存引擎选项失败", ex);
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

    // ==================== 引擎参数面板构建方法 ====================

    private static VBox buildEngineParameterPanel(AppConfig config, String engineName,
                                                   List<DecompilerParameter> params,
                                                   TextArea jsonArea,
                                                   Map<String, javafx.scene.Node> controlMap) {
        Map<String, String> engineOpts = config.decompiler().engineOptions()
                .getOrDefault(engineName, new LinkedHashMap<>());

        List<DecompilerParameter> common = new ArrayList<>();
        List<DecompilerParameter> advanced = new ArrayList<>();
        for (DecompilerParameter p : params) {
            if (p.category() == DecompilerParameter.Category.COMMON) {
                common.add(p);
            } else {
                advanced.add(p);
            }
        }

        var commonPane = buildTitledParameterPane("settings.engine.common", common, engineOpts,
                jsonArea, controlMap, config);
        var advancedPane = buildTitledParameterPane("settings.engine.advanced", advanced, engineOpts,
                jsonArea, controlMap, config);
        advancedPane.setExpanded(false);

        ScrollPane scroll = new ScrollPane(new VBox(5, commonPane, advancedPane));
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #2b2b2b; -fx-background-color: #2b2b2b;");

        return new VBox(scroll);
    }

    private static TitledPane buildTitledParameterPane(String titleKey,
                                                         List<DecompilerParameter> params,
                                                         Map<String, String> engineOpts,
                                                         TextArea jsonArea,
                                                         Map<String, javafx.scene.Node> controlMap,
                                                         AppConfig config) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(5);
        grid.setStyle("-fx-padding: 8;");

        for (int i = 0; i < params.size(); i++) {
            DecompilerParameter param = params.get(i);
            String labelText = I18nUtil.getStringOrDefault(param.i18nKey(), param.fallbackLabel());
            String helpText = I18nUtil.getStringOrDefault(param.helpKey(), "");

            Label lbl = new Label(labelText);
            lbl.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");
            if (!helpText.isEmpty()) {
                lbl.setTooltip(new Tooltip(helpText));
            }

            String currentValue = engineOpts.getOrDefault(param.key(), param.defaultValue());
            javafx.scene.Node[] controlHolder = new javafx.scene.Node[1];
            controlHolder[0] = createParameterControl(param, currentValue, () -> {
                updateEngineOptionFromControl(param.key(), controlHolder[0], engineOpts);
                syncJsonFromConfig(config, jsonArea);
            });

            controlMap.put(param.key(), controlHolder[0]);
            grid.add(controlHolder[0], 0, i);
            grid.add(lbl, 1, i);
        }

        TitledPane pane = new TitledPane();
        pane.setText(I18nUtil.getStringOrDefault(titleKey, titleKey));
        pane.setContent(grid);
        return pane;
    }

    private static javafx.scene.Node createParameterControl(DecompilerParameter param,
                                                              String currentValue,
                                                              Runnable onChange) {
        return switch (param.type()) {
            case BOOLEAN -> {
                CheckBox cb = new CheckBox();
                cb.setSelected("true".equalsIgnoreCase(currentValue) || "1".equals(currentValue));
                cb.selectedProperty().addListener((obs, old, val) -> onChange.run());
                yield cb;
            }
            case INTEGER -> {
                Spinner<Integer> spinner = new Spinner<>(0, Integer.MAX_VALUE,
                        parseOrDefault(currentValue, 0), 1);
                spinner.setEditable(true);
                spinner.setPrefWidth(100);
                spinner.getEditor().setTextFormatter(new javafx.scene.control.TextFormatter<>(
                        change -> change.getControlNewText().matches("\\d*") ? change : null));
                spinner.valueProperty().addListener((obs, old, val) -> onChange.run());
                yield spinner;
            }
            case STRING -> {
                TextField tf = new TextField(currentValue != null ? currentValue : "");
                tf.setPrefWidth(120);
                tf.textProperty().addListener((obs, old, val) -> onChange.run());
                yield tf;
            }
            case ENUM -> {
                ComboBox<String> combo = new ComboBox<>();
                if (param.enumValues() != null) {
                    combo.getItems().addAll(param.enumValues());
                }
                if (currentValue != null && !currentValue.isEmpty()) {
                    combo.setValue(currentValue);
                } else if (param.enumValues() != null && param.enumValues().length > 0) {
                    combo.setValue(param.enumValues()[0]);
                }
                combo.valueProperty().addListener((obs, old, val) -> onChange.run());
                yield combo;
            }
        };
    }

    private static void updateEngineOptionFromControl(String key, javafx.scene.Node control,
                                                        Map<String, String> engineOpts) {
        String value = switch (control) {
            case CheckBox cb -> cb.isSelected() ? "true" : "false";
            case Spinner<?> sp -> sp.getValue().toString();
            case TextField tf -> tf.getText();
            case ComboBox<?> combo -> {
                Object val = combo.getValue();
                yield val != null ? val.toString() : "";
            }
            default -> "";
        };
        engineOpts.put(key, value);
    }

    private static void syncJsonFromConfig(AppConfig config, TextArea jsonArea) {
        if (jsonArea.isFocused()) {
            return;
        }
        try {
            String json = new GsonBuilder().setPrettyPrinting().create()
                    .toJson(config.decompiler().engineOptions());
            jsonArea.setText(("{}".equals(json) || "null".equals(json)) ? "" : json);
        } catch (Exception ignored) {
            logger.debug("syncJsonFromConfig 序列化失败", ignored);
        }
    }

    private static void refreshEngineOptionsJson(AppConfig config, TextArea jsonArea) {
        try {
            String json = new GsonBuilder().setPrettyPrinting().create()
                    .toJson(config.decompiler().engineOptions());
            jsonArea.setText(("{}".equals(json) || "null".equals(json)) ? "" : json);
        } catch (Exception ignored) {
            jsonArea.setText("");
        }
    }

    private static String getActiveEngineName(TabPane engineTabPane) {
        Tab selected = engineTabPane.getSelectionModel().getSelectedItem();
        if (selected == null) return "VINEFLOWER";
        return switch (selected.getText()) {
            case "CFR" -> "CFR";
            case "Procyon" -> "PROCYON";
            case "Vineflower" -> "VINEFLOWER";
            default -> "VINEFLOWER";
        };
    }

    private static void setControlValue(javafx.scene.Node control, String value) {
        switch (control) {
            case CheckBox cb -> cb.setSelected("true".equalsIgnoreCase(value) || "1".equals(value));
            case Spinner<?> sp -> {
                try { ((Spinner<Integer>) sp).getValueFactory().setValue(Integer.parseInt(value)); }
                catch (NumberFormatException ignored) {}
            }
            case TextField tf -> tf.setText(value != null ? value : "");
            case ComboBox<?> combo -> {
                if (value != null && combo.getItems().contains(value)) {
                    ((ComboBox<String>) combo).setValue(value);
                }
            }
            default -> {}
        }
    }

    private static String readControlValue(javafx.scene.Node control) {
        return switch (control) {
            case CheckBox cb -> String.valueOf(cb.isSelected());
            case Spinner<?> sp -> sp.getValue().toString();
            case TextField tf -> tf.getText();
            case ComboBox<?> combo -> {
                Object val = combo.getValue();
                yield val != null ? val.toString() : "";
            }
            default -> "";
        };
    }

    private static int parseOrDefault(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }
}
