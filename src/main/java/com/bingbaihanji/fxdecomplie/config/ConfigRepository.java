package com.bingbaihanji.fxdecomplie.config;

import java.nio.file.Path;

/**
 * 配置仓储抽象 — 解耦 {@link AppConfig} 的静态单例加载.
 *
 * <p>实现类负责 JSON 序列化,AtomicFile 写入和备份.</p>
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
public interface ConfigRepository {

    /** 加载配置(首次调用可能从磁盘读取,后续返回缓存) */
    AppConfig load();

    /** 持久化配置到磁盘 */
    void save(AppConfig config);

    /** 配置文件路径 */
    Path configFile();
}
