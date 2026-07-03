package com.bingbaihanji.fxdecomplie.ui.menu;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;

/**
 * 引擎相关菜单动作
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public interface EngineActions {
    /** 切换反编译引擎 */
    void selectEngine(DecompilerTypeEnum engine);

    /** 用全部引擎反编译当前类并排打开 */
    void compareEngines();

    /** 自动反混淆 */
    void deobfuscate();
}
