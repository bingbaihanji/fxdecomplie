package com.bingbaihanji.fxdecomplie;

/**
 * 应用启动器,委托到 FxDecompilerApp.main()
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class AppLauncher {

    /** 工具类私有构造,防止实例化 */
    private AppLauncher() {
    }

    /** 程序入口,直接委托到 FxDecompilerApp.main() */
    public static void main(String[] args) {
        FxDecompilerApp.main(args);
    }
}
