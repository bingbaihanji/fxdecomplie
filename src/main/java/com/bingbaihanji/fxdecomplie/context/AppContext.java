package com.bingbaihanji.fxdecomplie.context;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.constants.AppPaths;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerFactory;
import com.bingbaihanji.fxdecomplie.rename.RenameService;
import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import com.bingbaihanji.fxdecomplie.service.CommentManager;
import com.bingbaihanji.fxdecomplie.ui.code.ClassTabOpener;
import com.bingbaihanji.windows.jfx.WindowToolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 应用级上下文,集中管理进程内共享状态和生命周期
 * <p>
 * UI 控制器仍保持窗口级实例 配置 缓存目录 后台线程池和反编译引擎属于应用级资源,
 * 由此处统一初始化和关闭
 * </p>
 */
public final class AppContext {

    private static final Logger log = LoggerFactory.getLogger(AppContext.class);
    private static final AppContext INSTANCE = new AppContext();

    private final Object lifecycleLock = new Object();
    private volatile boolean started;
    private volatile boolean shutdown;
    private AppConfig config;

    private AppContext() {
    }

    public static AppContext getInstance() {
        return INSTANCE;
    }

    private static void shutdownQuietly(String name, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("关闭应用资源失败: {}", name, e);
        }
    }

    /** 初始化应用级服务该方法可重复调用,但只会执行一次启动流程 */
    public AppConfig start() {
        synchronized (lifecycleLock) {
            if (started && config != null) {
                return config;
            }
            log.info("初始化应用上下文");
            config = AppConfig.getInstance();
            CommentManager.setRootDir(AppPaths.commentsDir());
            RenameService.setRootDir(AppPaths.renamesDir());
            started = true;
            shutdown = false;
            return config;
        }
    }

    /** @return 应用级共享配置若尚未启动,会先初始化上下文 */
    public AppConfig config() {
        AppConfig current = config;
        return current != null ? current : start();
    }

    /** 统一关闭应用级资源,幂等 */
    public void shutdown() {
        synchronized (lifecycleLock) {
            if (shutdown) {
                return;
            }
            shutdown = true;
        }
        log.info("关闭应用上下文");
        shutdownQuietly("background tasks", BackgroundTasks::shutdown);
        shutdownQuietly("class tab opener", ClassTabOpener::shutdown);
        shutdownQuietly("window toolkit", WindowToolkit::shutdown);
        shutdownQuietly("decompiler factory", DecompilerFactory::cleanup);
    }
}
