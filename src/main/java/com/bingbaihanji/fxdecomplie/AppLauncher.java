package com.bingbaihanji.fxdecomplie;

import com.bingbaihanji.fxdecomplie.platform.FxTools;
import com.sun.jna.Platform;

/**
 * 应用启动器，委托到 FxDecompilerApp.main()。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class AppLauncher {

    private AppLauncher() {
    }

    public static void main(String[] args) {


        FxDecompilerApp.main(args);
    }


}
