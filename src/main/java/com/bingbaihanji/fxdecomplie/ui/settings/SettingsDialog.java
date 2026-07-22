package com.bingbaihanji.fxdecomplie.ui.settings;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.decompiler.EngineParameters;
import com.bingbaihanji.fxdecomplie.decompiler.jadx.JadxAdapterOptions;
import com.bingbaihanji.fxdecomplie.model.DecompilerParameter;
import com.bingbaihanji.fxdecomplie.model.ExportConfig;
import com.bingbaihanji.fxdecomplie.service.DiskCodeCache;
import com.bingbaihanji.fxdecomplie.ui.DialogHelper;
import com.bingbaihanji.fxdecomplie.ui.theme.ThemeManager;
import com.bingbaihanji.fxdecomplie.util.i18n.I18nUtil;
import com.bingbaihanji.utils.json.JSONUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;

/**
 * 设置对话框 — 分类标签页式配置界面
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class SettingsDialog {

    private static final Logger log = LoggerFactory.getLogger(SettingsDialog.class);
    private static final String JSON_AREA_STYLE = "-fx-control-inner-background: #3c3c3c; -fx-text-fill: #cccccc; "
            + "-fx-font-family: 'Consolas', 'JetBrains Mono', monospace; -fx-font-size: 12px;";
    private static final String JSON_AREA_ERROR_STYLE = JSON_AREA_STYLE
            + " -fx-border-color: #f44747; -fx-border-width: 1px;";

    private static final List<String> JADX_CODE_OPTIONS = List.of(
            "showInconsistentCode",
            "useImports",
            "debugInfo",
            "extractFinally",
            "inlineAnonymousClasses",
            "inlineMethods",
            "moveInnerClasses",
            "allowInlineKotlinLambda",
            "restoreSwitchOverString",
            "replaceConsts",
            "escapeUnicode",
            "insertDebugLines",
            "respectBytecodeAccModifiers");

    private static final List<String> JADX_DEOBFUSCATION_OPTIONS = List.of(
            "deobfuscationOn",
            "deobfuscationMinLength",
            "deobfuscationMaxLength",
            "sourceNameRepeatLimit");

    private static final List<String> JADX_RESOURCE_OPTIONS = List.of(
            "skipResources",
            "skipSources",
            "skipXmlPrettyPrint",
            JadxAdapterOptions.LOAD_WORKSPACE_DEPENDENCIES,
            JadxAdapterOptions.WORKSPACE_DEPENDENCY_LIMIT,
            JadxAdapterOptions.WORKSPACE_DEPENDENCY_DEPTH);

    private static final List<String> JADX_PERFORMANCE_OPTIONS = List.of(
            "threadsCount",
            "typeUpdatesLimitCount");

    private SettingsDialog() {
        throw new AssertionError("utility class");
    }

    public static void show(Stage owner, AppConfig config, Consumer<AppConfig> onApply) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(I18nUtil.getString("menu.edit.settings"));
        dialog.setHeaderText(null);
        dialog.setResizable(true);
        DialogHelper.applyNativeStyle(dialog);

        AppConfig draft = config.copy();
        TabPane tabPane = new TabPane();

        // ── 反编译器标签页 ──
        Tab decompilerTab = new Tab(I18nUtil.getString("settings.decompiler"));
        decompilerTab.setClosable(false);

        // 默认引擎选择(现有控件,保持不变)
        ComboBox<String> engineCombo = new ComboBox<>();
        engineCombo.getItems().addAll("PROCYON", "CFR", "VINEFLOWER", "JD", "JADX");
        engineCombo.setValue(draft.decompiler().defaultEngine().name());

        // 引擎选项 JSON 编辑器(移到独立面板中,给足可读空间)
        Label engineOptionsLabel = new Label(I18nUtil.getString("settings.decompiler.engineOptions"));
        engineOptionsLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");

        TextArea engineOptionsArea = new TextArea();
        engineOptionsArea.setPromptText(I18nUtil.getString("settings.decompiler.engineOptionsHint"));
        engineOptionsArea.setPrefRowCount(14);
        engineOptionsArea.setMinHeight(130);
        engineOptionsArea.setPrefHeight(260);
        engineOptionsArea.setWrapText(false);
        engineOptionsArea.setStyle(JSON_AREA_STYLE);
        VBox.setVgrow(engineOptionsArea, Priority.ALWAYS);

        // 初始化 JSON
        refreshEngineOptionsJson(draft, engineOptionsArea);
        boolean[] jsonDirty = {false};
        engineOptionsArea.textProperty().addListener((obs, old, value) -> {
            if (engineOptionsArea.isFocused()) {
                jsonDirty[0] = true;
            }
        });

        // 跟踪每个引擎的参数控件 (key → control),用于 JSON → 面板回填
        Map<String, Map<String, javafx.scene.Node>> engineControlMaps = new LinkedHashMap<>();
        engineControlMaps.put("CFR", new LinkedHashMap<>());
        engineControlMaps.put("PROCYON", new LinkedHashMap<>());
        engineControlMaps.put("VINEFLOWER", new LinkedHashMap<>());
        engineControlMaps.put("JADX", new LinkedHashMap<>());

        // ── 引擎参数子标签页 ──
        TabPane engineTabPane = new TabPane();
        engineTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(engineTabPane, Priority.ALWAYS);

        // CFR 面板
        Tab cfrTab = new Tab("CFR");
        cfrTab.setClosable(false);
        cfrTab.setContent(buildEngineParameterPanel(draft, "CFR", EngineParameters.forType(DecompilerTypeEnum.CFR),
                engineOptionsArea, engineControlMaps.get("CFR")));

        // Procyon 面板
        Tab procyonTab = new Tab("Procyon");
        procyonTab.setClosable(false);
        procyonTab.setContent(buildEngineParameterPanel(draft, "PROCYON", EngineParameters.forType(DecompilerTypeEnum.PROCYON),
                engineOptionsArea, engineControlMaps.get("PROCYON")));

        // Vineflower 面板
        Tab vfTab = new Tab("Vineflower");
        vfTab.setClosable(false);
        vfTab.setContent(buildEngineParameterPanel(draft, "VINEFLOWER", EngineParameters.forType(DecompilerTypeEnum.VINEFLOWER),
                engineOptionsArea, engineControlMaps.get("VINEFLOWER")));

        // jadx 面板
        Tab jadxTab = new Tab("jadx");
        jadxTab.setClosable(false);
        jadxTab.setContent(buildEngineParameterPanel(draft, "JADX", EngineParameters.forType(DecompilerTypeEnum.JADX),
                engineOptionsArea, engineControlMaps.get("JADX")));

        engineTabPane.getTabs().addAll(cfrTab, procyonTab, vfTab, jadxTab);

        VBox decompilerContent = new VBox(10,
                new Label(I18nUtil.getString("settings.defaultEngine")), engineCombo,
                engineTabPane);
        VBox.setVgrow(decompilerContent, Priority.ALWAYS);
        decompilerTab.setContent(decompilerContent);

        Tab engineOptionsTab = new Tab(I18nUtil.getStringOrDefault(
                "settings.decompiler.engineOptionsJson", "Engine Options JSON"));
        engineOptionsTab.setClosable(false);
        engineOptionsTab.setContent(buildEngineOptionsJsonContent(draft, engineOptionsArea,
                engineOptionsLabel, engineControlMaps, jsonDirty));

        // JSON 失焦 → 解析 → 回填面板控件
        engineOptionsArea.focusedProperty().addListener((obs, old, focused) -> {
            if (!focused) {
                String jsonText = engineOptionsArea.getText();
                if (jsonText == null || jsonText.isBlank()) {
                    return;
                }
                try {
                    Map<String, Map<String, String>> allOpts = JSONUtils.fromJson(jsonText,
                            new TypeReference<Map<String, Map<String, String>>>() {
                            });
                    applyJsonToControls(allOpts, engineControlMaps);
                    jsonDirty[0] = false;
                    engineOptionsArea.setStyle(JSON_AREA_STYLE);
                } catch (Exception ex) {
                    engineOptionsArea.setStyle(JSON_AREA_ERROR_STYLE);
                }
            }
        });

        // 界面标签页
        Tab uiTab = new Tab(I18nUtil.getString("settings.ui"));
        uiTab.setClosable(false);
        CheckBox lineNumCheck = new CheckBox(I18nUtil.getString("settings.lineNumbers"));
        lineNumCheck.setSelected(draft.decompiler().lineNumbersEnabled());
        CheckBox wrapCheck = new CheckBox(I18nUtil.getString("settings.wordWrap"));
        wrapCheck.setSelected(draft.decompiler().wrapText());

        Spinner<Integer> fontSizeSpinner = new Spinner<>(8, 48,
                Math.clamp(draft.theme().fontSize(), 8, 48), 1);
        fontSizeSpinner.setEditable(true);
        fontSizeSpinner.getEditor().setTextFormatter(
                new javafx.scene.control.TextFormatter<>(change -> {
                    String newText = change.getControlNewText();
                    if (newText.isEmpty() || newText.matches("\\d{1,2}")) {
                        return change;
                    }
                    return null;
                }));
        fontSizeSpinner.setPrefWidth(80);

        ComboBox<String> fontFamilyCombo = new ComboBox<>();
        fontFamilyCombo.getItems().addAll(
                "Consolas", "Fira Code", "JetBrains Mono", "Cascadia Code",
                "Source Code Pro", "Courier New", "Monaco", "Menlo",
                "DejaVu Sans Mono", "Microsoft YaHei Mono"
        );
        fontFamilyCombo.setValue(draft.theme().fontFamily() != null
                && !draft.theme().fontFamily().isBlank()
                ? draft.theme().fontFamily() : "Consolas");
        fontFamilyCombo.setEditable(true);

        // 编辑器配色下拉框
        ComboBox<String> editorThemeCombo = new ComboBox<>();
        editorThemeCombo.getItems().addAll(ThemeManager.getAllThemes());
        String currentEditorTheme = draft.theme().editorTheme();
        if (currentEditorTheme == null || currentEditorTheme.isBlank()) {
            currentEditorTheme = "Dark+";
        }
        editorThemeCombo.setValue(currentEditorTheme);

        // 导入/导出/删除按钮
        Button importThemeBtn = new Button(I18nUtil.getString("settings.editorTheme.import"));
        Button exportThemeBtn = new Button(I18nUtil.getString("settings.editorTheme.export"));
        Button deleteThemeBtn = new Button(I18nUtil.getString("settings.editorTheme.delete"));
        deleteThemeBtn.setDisable("Dark+".equals(currentEditorTheme));

        HBox themeBtnBar = new HBox(8, importThemeBtn, exportThemeBtn, deleteThemeBtn);
        VBox themeSection = new VBox(6,
                new Label(I18nUtil.getString("settings.editorTheme")),
                editorThemeCombo,
                themeBtnBar);

        uiTab.setContent(new VBox(10,
                new Label(I18nUtil.getString("settings.fontSize")), fontSizeSpinner,
                new Label(I18nUtil.getString("settings.fontFamily")), fontFamilyCombo,
                lineNumCheck, wrapCheck,
                new Separator(),
                themeSection));

        // ---- 编辑器配色事件处理 ----
        editorThemeCombo.valueProperty().addListener((obs, old, val) -> {
            if (val != null) {
                deleteThemeBtn.setDisable("Dark+".equals(val)
                        || ThemeManager.getBuiltinThemes().contains(val));
            }
        });

        importThemeBtn.setOnAction(e -> {
            javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
            fileChooser.setTitle(I18nUtil.getString("settings.editorTheme.importTitle"));
            fileChooser.getExtensionFilters().add(
                    new javafx.stage.FileChooser.ExtensionFilter("JSON Files", "*.json"));
            var selected = fileChooser.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (selected != null) {
                try {
                    String importedName = ThemeManager.importTheme(selected.toPath());
                    refreshThemeCombo(editorThemeCombo, importedName);
                } catch (Exception ex) {
                    log.warn("导入主题失败", ex);
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle(I18nUtil.getString("dialog.error.title"));
                    alert.setHeaderText(null);
                    alert.setContentText(I18nUtil.getString("settings.editorTheme.invalidJson",
                            ex.getMessage()));
                    alert.initOwner(dialog.getDialogPane().getScene().getWindow());
                    DialogHelper.applyNativeStyle(alert);
                    alert.showAndWait();
                }
            }
        });

        exportThemeBtn.setOnAction(e -> {
            String selectedTheme = editorThemeCombo.getValue();
            if (selectedTheme == null || selectedTheme.isBlank()) {
                return;
            }
            javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
            fileChooser.setTitle(I18nUtil.getString("settings.editorTheme.exportTitle"));
            fileChooser.setInitialFileName(selectedTheme + ".json");
            fileChooser.getExtensionFilters().add(
                    new javafx.stage.FileChooser.ExtensionFilter("JSON Files", "*.json"));
            var target = fileChooser.showSaveDialog(dialog.getDialogPane().getScene().getWindow());
            if (target != null) {
                try {
                    ThemeManager.exportTheme(selectedTheme, target.toPath());
                } catch (Exception ex) {
                    log.warn("导出主题失败", ex);
                }
            }
        });

        deleteThemeBtn.setOnAction(e -> {
            String selectedTheme = editorThemeCombo.getValue();
            if (selectedTheme == null || "Dark+".equals(selectedTheme)
                    || ThemeManager.getBuiltinThemes().contains(selectedTheme)) {
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle(I18nUtil.getString("dialog.confirm.title"));
            confirm.setHeaderText(null);
            confirm.setContentText(I18nUtil.getString("settings.editorTheme.deleteConfirm",
                    selectedTheme));
            confirm.initOwner(dialog.getDialogPane().getScene().getWindow());
            DialogHelper.applyNativeStyle(confirm);
            confirm.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    ThemeManager.deleteExternalTheme(selectedTheme);
                    refreshThemeCombo(editorThemeCombo, "Dark+");
                }
            });
        });

        // 搜索标签页
        Tab searchTab = new Tab(I18nUtil.getString("settings.search"));
        searchTab.setClosable(false);
        CheckBox fullSourceSearchCheck = new CheckBox(I18nUtil.getString("settings.search.fullSource"));
        fullSourceSearchCheck.setSelected(draft.search().fullSourceSearch());
        Spinner<Integer> resultLimitSpinner = new Spinner<>(50, 2000,
                Math.clamp(draft.search().resultLimit(), 50, 2000), 50);
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
        exportEngineCombo.setValue(exportEngineValue(draft.export().defaultEngine()));
        ComboBox<String> exportFormatCombo = new ComboBox<>();
        exportFormatCombo.getItems().addAll("DIR", "ZIP");
        exportFormatCombo.setValue(draft.export().defaultFormat());
        ComboBox<String> conflictCombo = new ComboBox<>();
        conflictCombo.getItems().addAll("SKIP", "OVERWRITE", "RENAME");
        conflictCombo.setValue(draft.export().conflictPolicy());
        CheckBox exportResourcesCheck = new CheckBox(I18nUtil.getString("settings.export.resources"));
        exportResourcesCheck.setSelected(draft.export().exportResources());
        TextField exportPathField = new TextField(draft.export().lastPath());
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

        tabPane.getTabs().addAll(decompilerTab, engineOptionsTab, uiTab, searchTab, exportTab, cacheTab, langTab);

        Button restoreDefaultsBtn = new Button(I18nUtil.getString("settings.restoreDefaults"));
        restoreDefaultsBtn.setStyle("-fx-font-size: 12px; -fx-text-fill: #f44747;");
        restoreDefaultsBtn.setOnAction(e -> {
            engineCombo.setValue("JADX");
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
            editorThemeCombo.setValue("Dark+");
        });

        VBox content = new VBox(10, tabPane, restoreDefaultsBtn);
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setMinSize(760, 560);
        dialog.getDialogPane().setPrefSize(980, 720);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);

        var result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.APPLY) {
            draft.decompiler().defaultEngine(DecompilerTypeEnum.valueOf(engineCombo.getValue()));
            draft.decompiler().lineNumbersEnabled(lineNumCheck.isSelected());
            draft.decompiler().wrapText(wrapCheck.isSelected());
            draft.theme().fontSize(fontSizeSpinner.getValue());
            draft.theme().fontFamily(fontFamilyCombo.getValue());
            draft.search().fullSourceSearch(fullSourceSearchCheck.isSelected());
            draft.search().resultLimit(resultLimitSpinner.getValue());
            draft.export().defaultEngine("FOLLOW_CURRENT".equals(exportEngineCombo.getValue())
                    ? "" : exportEngineCombo.getValue());
            draft.export().defaultFormat(safeEnum(exportFormatCombo.getValue(),
                    ExportConfig.Format.DIR).name());
            draft.export().conflictPolicy(safeEnum(conflictCombo.getValue(),
                    ExportConfig.ConflictPolicy.OVERWRITE).name());
            draft.export().exportResources(exportResourcesCheck.isSelected());
            draft.export().lastPath(exportPathField.getText() == null
                    ? "" : exportPathField.getText());

            // 保存引擎选项(已通过面板控件实时更新到 engineControlMaps + engineOptions Map)
            try {
                Map<String, Map<String, String>> jsonOpts =
                        parseEngineOptionsJson(engineOptionsArea.getText());
                if (jsonDirty[0] || engineOptionsArea.isFocused()) {
                    applyJsonToControls(jsonOpts, engineControlMaps);
                    jsonDirty[0] = false;
                }

                for (var engineEntry : engineControlMaps.entrySet()) {
                    String engName = engineEntry.getKey();
                    Map<String, String> engOpts = draft.decompiler().engineOptions()
                            .computeIfAbsent(engName, k -> new LinkedHashMap<>());
                    engOpts.clear();
                    if (jsonOpts != null && jsonOpts.get(engName) != null) {
                        engOpts.putAll(jsonOpts.get(engName));
                    }
                    for (var ctrlEntry : engineEntry.getValue().entrySet()) {
                        String key = ctrlEntry.getKey();
                        String val = readControlValue(ctrlEntry.getValue());
                        if (val != null && !val.isEmpty()) {
                            engOpts.put(key, val);
                        }
                    }
                    log.info("引擎选项已保存 [{}]: {} 项", engName, engOpts.size());
                }
            } catch (Exception ex) {
                log.warn("保存引擎选项失败", ex);
            }

            // 保存编辑器配色
            if (editorThemeCombo.getValue() != null) {
                draft.theme().editorTheme(editorThemeCombo.getValue());
            }

            // 应用语言变更
            int langIdx = langCombo.getSelectionModel().getSelectedIndex();
            Locale newLocale = (langIdx == 1) ? Locale.ENGLISH : Locale.SIMPLIFIED_CHINESE;
            draft.language(langIdx == 1 ? "en" : "zh-CN");
            I18nUtil.switchLocale(newLocale);

            config.copyFrom(draft);
            config.save();
            onApply.accept(config);
        }
    }

    /** 获取导出引擎下拉框选项列表 */
    private static List<String> exportEngineOptions() {
        List<String> options = new ArrayList<>();
        options.add("FOLLOW_CURRENT");
        options.add("PROCYON");
        options.add("CFR");
        options.add("VINEFLOWER");
        options.add("JD");
        return options;
    }

    /** 将配置中的导出引擎值规范化,空值时回退到 FOLLOW_CURRENT */
    private static String exportEngineValue(String value) {
        return value == null || value.isBlank() ? "FOLLOW_CURRENT" : value;
    }

    /** 安全地将字符串转换为枚举值,解析失败时返回默认值 */
    private static <E extends Enum<E>> E safeEnum(String value, E fallback) {
        try {
            return Enum.valueOf(fallback.getDeclaringClass(), value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return fallback;
        }
    }

    // ==================== 引擎参数面板构建方法 ====================

    /**
     * 构建单个引擎的参数面板,包含通用和高级两个折叠区域
     *
     * @param config     应用配置
     * @param engineName 引擎名称(如 CFR PROCYON)
     * @param params     该引擎的参数定义列表
     * @param jsonArea   JSON 编辑器文本区域,用于双向同步
     * @param controlMap 用于存储参数 key 到控件的映射
     */
    private static VBox buildEngineParameterPanel(AppConfig config, String engineName,
                                                  List<DecompilerParameter> params,
                                                  TextArea jsonArea,
                                                  Map<String, javafx.scene.Node> controlMap) {
        Map<String, String> engineOpts = config.decompiler().engineOptions()
                .computeIfAbsent(engineName, ignored -> new LinkedHashMap<>());
        if ("JADX".equals(engineName)) {
            return buildJadxParameterPanel(config, params, engineOpts, jsonArea, controlMap);
        }

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
        VBox.setVgrow(scroll, Priority.ALWAYS);

        return new VBox(scroll);
    }

    /** JADX 选项较多,按实际作用域分组,避免全部挤在“通用/高级”里 */
    private static VBox buildJadxParameterPanel(AppConfig config,
                                                List<DecompilerParameter> params,
                                                Map<String, String> engineOpts,
                                                TextArea jsonArea,
                                                Map<String, javafx.scene.Node> controlMap) {
        Map<String, DecompilerParameter> byKey = new LinkedHashMap<>();
        for (DecompilerParameter param : params) {
            byKey.put(param.key(), param);
        }

        VBox sections = new VBox(6,
                buildTitledParameterPane("settings.engine.jadx.code", selectParameters(byKey, JADX_CODE_OPTIONS),
                        engineOpts, jsonArea, controlMap, config),
                buildTitledParameterPane("settings.engine.jadx.deobfuscation",
                        selectParameters(byKey, JADX_DEOBFUSCATION_OPTIONS),
                        engineOpts, jsonArea, controlMap, config),
                buildTitledParameterPane("settings.engine.jadx.resources",
                        selectParameters(byKey, JADX_RESOURCE_OPTIONS),
                        engineOpts, jsonArea, controlMap, config),
                buildTitledParameterPane("settings.engine.jadx.performance",
                        selectParameters(byKey, JADX_PERFORMANCE_OPTIONS),
                        engineOpts, jsonArea, controlMap, config));

        List<DecompilerParameter> other = new ArrayList<>(byKey.values());
        if (!other.isEmpty()) {
            TitledPane otherPane = buildTitledParameterPane("settings.engine.advanced", other,
                    engineOpts, jsonArea, controlMap, config);
            otherPane.setExpanded(false);
            sections.getChildren().add(otherPane);
        }

        ScrollPane scroll = new ScrollPane(sections);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #2b2b2b; -fx-background-color: #2b2b2b;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return new VBox(scroll);
    }

    private static List<DecompilerParameter> selectParameters(Map<String, DecompilerParameter> source,
                                                              List<String> keys) {
        List<DecompilerParameter> result = new ArrayList<>();
        for (String key : keys) {
            DecompilerParameter param = source.remove(key);
            if (param != null) {
                result.add(param);
            }
        }
        return result;
    }

    /**
     * 构建带标题的折叠参数面板,使用 GridPane 布局标签和控件
     * 每个参数控件变更时自动同步到 engineOpts 并刷新 JSON 显示
     */
    private static TitledPane buildTitledParameterPane(String titleKey,
                                                       List<DecompilerParameter> params,
                                                       Map<String, String> engineOpts,
                                                       TextArea jsonArea,
                                                       Map<String, javafx.scene.Node> controlMap,
                                                       AppConfig config) {
        if (params.isEmpty()) {
            TitledPane emptyPane = new TitledPane();
            emptyPane.setText(I18nUtil.getStringOrDefault(titleKey, titleKey));
            emptyPane.setContent(new Label(I18nUtil.getStringOrDefault(
                    "settings.engine.noOptions", "No options")));
            emptyPane.setExpanded(false);
            return emptyPane;
        }

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(8);
        grid.setStyle("-fx-padding: 8;");
        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(190);
        labelColumn.setPrefWidth(260);
        labelColumn.setHgrow(Priority.ALWAYS);
        ColumnConstraints controlColumn = new ColumnConstraints();
        controlColumn.setMinWidth(120);
        controlColumn.setPrefWidth(180);
        grid.getColumnConstraints().addAll(labelColumn, controlColumn);

        for (int i = 0; i < params.size(); i++) {
            DecompilerParameter param = params.get(i);
            String labelText = I18nUtil.getStringOrDefault(param.i18nKey(), param.fallbackLabel());
            String helpText = I18nUtil.getStringOrDefault(param.helpKey(), "");

            Label lbl = new Label(labelText);
            lbl.setWrapText(true);
            lbl.setMaxWidth(Double.MAX_VALUE);
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
            grid.add(lbl, 0, i);
            grid.add(controlHolder[0], 1, i);
            GridPane.setHgrow(lbl, Priority.ALWAYS);
        }

        TitledPane pane = new TitledPane();
        pane.setText(I18nUtil.getStringOrDefault(titleKey, titleKey));
        pane.setContent(grid);
        return pane;
    }

    private static VBox buildEngineOptionsJsonContent(AppConfig config,
                                                      TextArea jsonArea,
                                                      Label engineOptionsLabel,
                                                      Map<String, Map<String, javafx.scene.Node>> engineControlMaps,
                                                      boolean[] jsonDirty) {
        Button formatButton = new Button(I18nUtil.getStringOrDefault(
                "settings.decompiler.formatJson", "Format"));
        formatButton.setMinWidth(Region.USE_PREF_SIZE);
        formatButton.setOnAction(e -> formatJsonArea(jsonArea));

        Button applyJsonButton = new Button(I18nUtil.getStringOrDefault(
                "settings.decompiler.applyJson", "Apply JSON to controls"));
        applyJsonButton.setMinWidth(Region.USE_PREF_SIZE);
        applyJsonButton.setOnAction(e -> {
            Map<String, Map<String, String>> parsed = parseEngineOptionsJson(jsonArea.getText());
            if (parsed == null) {
                jsonArea.setStyle(JSON_AREA_ERROR_STYLE);
                return;
            }
            applyJsonToControls(parsed, engineControlMaps);
            jsonDirty[0] = false;
            jsonArea.setStyle(JSON_AREA_STYLE);
        });

        Button refreshButton = new Button(I18nUtil.getStringOrDefault(
                "settings.decompiler.refreshJson", "Refresh from controls"));
        refreshButton.setMinWidth(Region.USE_PREF_SIZE);
        refreshButton.setOnAction(e -> {
            refreshEngineOptionsJson(config, jsonArea);
            jsonDirty[0] = false;
            jsonArea.setStyle(JSON_AREA_STYLE);
        });

        engineOptionsLabel.setWrapText(true);
        engineOptionsLabel.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(engineOptionsLabel, Priority.NEVER);

        FlowPane actions = new FlowPane(8, 6, formatButton, applyJsonButton, refreshButton);
        actions.setPrefWrapLength(360);
        actions.setMinHeight(Region.USE_PREF_SIZE);
        actions.setStyle("-fx-padding: 0;");

        VBox toolbar = new VBox(6, engineOptionsLabel, actions);
        toolbar.setFillWidth(true);
        toolbar.setStyle("-fx-padding: 0 0 6 0;");

        VBox content = new VBox(10, toolbar, jsonArea);
        content.setFillWidth(true);
        content.setStyle("-fx-padding: 10;");
        VBox.setVgrow(jsonArea, Priority.ALWAYS);
        return content;
    }

    private static void formatJsonArea(TextArea jsonArea) {
        String text = jsonArea.getText();
        if (text == null || text.isBlank()) {
            jsonArea.setStyle(JSON_AREA_STYLE);
            return;
        }
        try {
            Object tree = JSONUtils.fromJson(text, Object.class);
            jsonArea.setText(JSONUtils.toPrettyJson(tree));
            jsonArea.setStyle(JSON_AREA_STYLE);
        } catch (Exception ex) {
            jsonArea.setStyle(JSON_AREA_ERROR_STYLE);
        }
    }

    /** 根据参数类型创建对应的 JavaFX 控件(CheckBox/Spinner/TextField/ComboBox) */
    private static javafx.scene.Node createParameterControl(DecompilerParameter param,
                                                            String currentValue,
                                                            Runnable onChange) {
        return switch (param.type()) {
            case BOOLEAN -> {
                CheckBox cb = new CheckBox();
                cb.setSelected("true".equalsIgnoreCase(currentValue) || "1".equals(currentValue));
                boolean numericBool = "1".equals(param.defaultValue())
                        || "0".equals(param.defaultValue());
                cb.setUserData(numericBool);
                cb.selectedProperty().addListener((obs, old, val) -> onChange.run());
                yield cb;
            }
            case INTEGER -> {
                Spinner<Integer> spinner = new Spinner<>(0, Integer.MAX_VALUE,
                        parseOrDefault(currentValue, 0), 1);
                spinner.setEditable(true);
                spinner.setPrefWidth(150);
                spinner.getEditor().setTextFormatter(new javafx.scene.control.TextFormatter<>(
                        change -> change.getControlNewText().matches("\\d*") ? change : null));
                spinner.valueProperty().addListener((obs, old, val) -> onChange.run());
                yield spinner;
            }
            case STRING -> {
                TextField tf = new TextField(currentValue != null ? currentValue : "");
                tf.setPrefWidth(180);
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

    /** 从控件读取当前值并更新到引擎选项 Map 中 */
    private static void updateEngineOptionFromControl(String key, javafx.scene.Node control,
                                                      Map<String, String> engineOpts) {
        String value = switch (control) {
            case CheckBox cb -> {
                boolean numeric = cb.getUserData() instanceof Boolean b && b;
                yield numeric
                        ? (cb.isSelected() ? "1" : "0")
                        : (cb.isSelected() ? "true" : "false");
            }
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

    /** 将当前配置中的引擎选项序列化为 JSON 显示在文本区域(JSON 区域聚焦时不更新,避免覆盖用户编辑) */
    private static void syncJsonFromConfig(AppConfig config, TextArea jsonArea) {
        if (jsonArea.isFocused()) {
            return;
        }
        try {
            String json = JSONUtils.toPrettyJson(config.decompiler().engineOptions());
            jsonArea.setText(("{}".equals(json) || "null".equals(json)) ? "" : json);
        } catch (Exception ignored) {
            log.debug("syncJsonFromConfig 序列化失败", ignored);
        }
    }

    /** 将 JSON 文本解析为引擎选项 Map 结构,解析失败返回 null */
    private static Map<String, Map<String, String>> parseEngineOptionsJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSONUtils.fromJson(json,
                    new TypeReference<Map<String, Map<String, String>>>() {
                    });
        } catch (Exception ex) {
            log.warn("解析引擎选项 JSON 失败", ex);
            return null;
        }
    }

    /** 将解析出的 JSON 选项值回填到引擎参数面板的控件上 */
    private static void applyJsonToControls(Map<String, Map<String, String>> jsonOpts,
                                            Map<String, Map<String, javafx.scene.Node>> engineControlMaps) {
        if (jsonOpts == null || engineControlMaps == null) {
            return;
        }
        for (var engineEntry : jsonOpts.entrySet()) {
            Map<String, javafx.scene.Node> controls = engineControlMaps.get(engineEntry.getKey());
            if (controls == null || engineEntry.getValue() == null) {
                continue;
            }
            for (var optionEntry : engineEntry.getValue().entrySet()) {
                javafx.scene.Node ctrl = controls.get(optionEntry.getKey());
                if (ctrl != null) {
                    setControlValue(ctrl, optionEntry.getValue());
                }
            }
        }
    }

    /** 从配置中读取引擎选项并格式化为 JSON 显示在文本区域 */
    private static void refreshEngineOptionsJson(AppConfig config, TextArea jsonArea) {
        try {
            String json = JSONUtils.toPrettyJson(config.decompiler().engineOptions());
            jsonArea.setText(("{}".equals(json) || "null".equals(json)) ? "" : json);
        } catch (Exception ex) {
            log.warn("序列化引擎选项 JSON 失败", ex);
            jsonArea.setText("");
        }
    }

    /** 获取引擎子标签页当前选中的引擎名称 */
    private static String getActiveEngineName(TabPane engineTabPane) {
        Tab selected = engineTabPane.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return "VINEFLOWER";
        }
        return switch (selected.getText()) {
            case "CFR" -> "CFR";
            case "Procyon" -> "PROCYON";
            case "Vineflower" -> "VINEFLOWER";
            case "jadx" -> "JADX";
            default -> "VINEFLOWER";
        };
    }

    /** 根据类型将字符串值设置到对应的 JavaFX 控件上 */
    private static void setControlValue(javafx.scene.Node control, String value) {
        switch (control) {
            case CheckBox cb -> cb.setSelected("true".equalsIgnoreCase(value) || "1".equals(value));
            case Spinner<?> sp -> {
                try {
                    ((Spinner<Integer>) sp).getValueFactory().setValue(Integer.parseInt(value));
                } catch (NumberFormatException ignored) {
                }
            }
            case TextField tf -> tf.setText(value != null ? value : "");
            case ComboBox<?> combo -> {
                if (value != null && combo.getItems().contains(value)) {
                    ((ComboBox<String>) combo).setValue(value);
                }
            }
            default -> {
            }
        }
    }

    /** 从 JavaFX 控件读取当前值并以字符串形式返回 */
    private static String readControlValue(javafx.scene.Node control) {
        return switch (control) {
            case CheckBox cb -> {
                boolean numeric = cb.getUserData() instanceof Boolean b && b;
                yield numeric
                        ? (cb.isSelected() ? "1" : "0")
                        : String.valueOf(cb.isSelected());
            }
            case Spinner<?> sp -> sp.getValue().toString();
            case TextField tf -> tf.getText();
            case ComboBox<?> combo -> {
                Object val = combo.getValue();
                yield val != null ? val.toString() : "";
            }
            default -> "";
        };
    }

    /** 安全地将字符串解析为 int,失败时返回默认值 */
    private static int parseOrDefault(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** 刷新编辑器配色下拉框：重新扫描主题列表并选中指定主题 */
    private static void refreshThemeCombo(ComboBox<String> combo, String selectName) {
        String currentValue = combo.getValue();
        combo.getItems().setAll(ThemeManager.getAllThemes());
        if (selectName != null && combo.getItems().contains(selectName)) {
            combo.setValue(selectName);
        } else if (currentValue != null && combo.getItems().contains(currentValue)) {
            combo.setValue(currentValue);
        } else {
            combo.setValue("Dark+");
        }
    }
}
