package com.bingbaihanji.fxdecomplie.controller;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.ui.settings.SettingsDialog;
import com.bingbaihanji.fxdecomplie.ui.theme.AppTheme;
import com.bingbaihanji.fxdecomplie.ui.theme.VsCodeThemeLoader;

/**
 * 设置控制器：设置对话框的打开与配置应用 (引擎切换、行号、字体、主题)
 * <p>
 * 从 MainWindow 拆分而来，通过 owner 访问共享状态与协作者 (Mediator 模式)
 *
 * @author bingbaihanji
 */
public final class SettingsController {

    private final MainWindow owner;

    public SettingsController(MainWindow owner) {
        this.owner = owner;
    }

    /** 打开设置对话框 */
    public void openSettings() {
        String oldEditorTheme = owner.config().theme().editorTheme();
        SettingsDialog.show(owner.stage(), owner.config(), updated -> {
            boolean engineSwitched = applySettings(updated);

            // 检测编辑器主题变更 — 必须在 refreshCurrentTab 之前更新 ClassTabOpener,
            // 否则异步反编译任务会用旧主题创建新标签页覆盖掉 reapplyTheme 的结果
            String newEditorTheme = owner.config().theme().editorTheme();
            if (!newEditorTheme.equals(oldEditorTheme)) {
                VsCodeThemeLoader.ThemeData theme = AppTheme.loadEditorTheme(owner.config());
                owner.setEditorTheme(theme);
                owner.classTabOpener().setEditorTheme(theme);
                owner.tabManager().getWorkspaceViews().values().forEach(view ->
                        view.splitEditorPane().forEachTab(tab ->
                                tab.reapplyTheme(theme))
                );
            }

            DecompilerTypeEnum activeTabEngine = owner.engineController().activeCodeTabEngine();
            if (!engineSwitched && owner.tabManager() != null
                    && owner.tabManager().currentCodeTab() != null) {
                owner.engineController().refreshCurrentTab(activeTabEngine);
            }
        });
    }

    /** 应用设置对话框确认后的配置变更：切换引擎、更新行号显示和字体设置,返回是否切换了引擎 */
    private boolean applySettings(AppConfig updated) {
        DecompilerTypeEnum configuredEngine = updated.decompiler().defaultEngine();
        boolean engineSwitched = false;
        if (configuredEngine != owner.currentEngine()) {
            owner.engineController().selectEngine(configuredEngine);
            engineSwitched = true;
        }
        boolean lineNumbersEnabled = updated.decompiler().lineNumbersEnabled();
        owner.setLineNumbersEnabled(lineNumbersEnabled);
        int newFontSize = updated.theme().fontSize();
        String newFontFamily = updated.theme().fontFamily();
        owner.tabManager().getWorkspaceViews().values().forEach(view ->
                view.splitEditorPane().forEachTab(tab -> {
                    tab.setLineNumbersEnabled(lineNumbersEnabled);
                    tab.applyFontSettings(newFontSize, newFontFamily);
                })
        );

        return engineSwitched;
    }
}
