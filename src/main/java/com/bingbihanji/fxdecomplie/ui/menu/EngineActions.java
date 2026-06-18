package com.bingbihanji.fxdecomplie.ui.menu;

import com.bingbihanji.fxdecomplie.decompiler.DecompilerTypeEnum;

/**
 * Engine-related menu actions.
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public interface EngineActions {
    /** 切换反编译引擎 */
    void selectEngine(DecompilerTypeEnum engine);

    /** 用全部引擎反编译当前类并排打开 */
    void compareEngines();
}
