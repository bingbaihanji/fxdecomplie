package com.bingbaihanji.fxdecomplie.app;

import com.bingbaihanji.fxdecomplie.constants.AppPaths;
import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import com.bingbaihanji.fxdecomplie.service.DecompileCache;
import com.bingbaihanji.fxdecomplie.service.DiskCodeCache;
import com.bingbaihanji.fxdecomplie.service.cache.CacheManager;
import com.bingbaihanji.fxdecomplie.service.decompile.DecompileCacheService;
import com.bingbaihanji.fxdecomplie.service.task.TaskExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * 应用服务装配和生命周期管理.
 *
 * <p>所有服务的创建,依赖注入,关闭都在此处统一处理.
 * 替代分散在各处的静态单例,提供可测试的服务入口.</p>
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
public final class ApplicationServices implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ApplicationServices.class);

    private static volatile ApplicationServices instance;

    private final Path appDir;

    private final DecompileCache l2Cache;

    private final DiskCodeCache l3Cache;

    private final DecompileCacheService decompileCache;

    private final CacheManager cacheManager;

    private ApplicationServices(Path appDir) {
        this.appDir = appDir;
        this.l2Cache = new DecompileCache();
        this.l3Cache = DiskCodeCache.getDefault();
        this.decompileCache = new DecompileCacheService(l2Cache, l3Cache);
        this.cacheManager = new CacheManager(decompileCache);
        log.info("应用服务已初始化: {}", appDir);
    }

    /** 创建应用服务单例(首次调用初始化,后续返回已有实例) */
    public static ApplicationServices create(Path appDir) {
        if (instance == null) {
            synchronized (ApplicationServices.class) {
                if (instance == null) {
                    instance = new ApplicationServices(appDir);
                }
            }
        }
        return instance;
    }

    /** 创建应用服务单例(使用默认应用目录) */
    public static ApplicationServices create() {
        return create(AppPaths.appDir());
    }

    /** 获取当前实例 */
    public static ApplicationServices getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "ApplicationServices 尚未初始化,请先调用 create()");
        }
        return instance;
    }

    // ─── 服务访问器 ───

    private static void shutdownQuietly(String name, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("关闭应用服务失败: {}", name, e);
        }
    }

    /** 任务执行器(通过 BackgroundTasks 静态兼容层也可访问) */
    public TaskExecutorService taskExecutor() {
        return BackgroundTasks.getExecutor();
    }

    /** 应用缓存统一入口 */
    public CacheManager cacheManager() {
        return cacheManager;
    }

    // ─── 生命周期 ───

    /** 应用根目录 */
    public Path appDir() {
        return appDir;
    }

    @Override
    public void close() {
        log.info("关闭应用服务");
        shutdownQuietly("cache manager", cacheManager::close);
        shutdownQuietly("background tasks", BackgroundTasks::shutdown);
        instance = null;
    }
}
