package com.bingbaihanji.fxdecomplie.smoke;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.constants.AppPaths;
import com.bingbaihanji.fxdecomplie.context.AppContext;
import com.bingbaihanji.fxdecomplie.decompiler.Decompiler;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerFactory;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import com.bingbaihanji.fxdecomplie.service.DecompileCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 核心路径冒烟测试 — 不启动 JavaFX,只验证关键对象的创建,使用和销毁.
 *
 * <p>这些测试是 CI 的第一道防线 — 如果某个冒烟测试失败,说明基础设施出了问题.</p>
 */
@DisplayName("应用冒烟测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
final class SmokeTest {

    // ─── 1. 后台任务 (必须在 shutdown 之前) ───

    @Test
    @Order(1)
    @DisplayName("后台任务池可执行任务")
    void shouldExecuteBackgroundTask() throws Exception {
        boolean[] executed = {false};
        Future<?> future = BackgroundTasks.run("smoke-test-bg",
                () -> { /* no-op */ executed[0] = true;});

        future.get(5, TimeUnit.SECONDS);
        assertTrue(future.isDone(), "任务应已完成");
        assertTrue(executed[0], "任务应已执行");
    }

    // ─── 2. 应用上下文 ───

    @Test
    @Order(2)
    @DisplayName("应用上下文启动")
    void shouldStartAppContext() {
        AppContext ctx = AppContext.getInstance();
        AppConfig config = ctx.start();
        assertNotNull(config, "配置不能为空");
        assertTrue(config.schemaVersion() >= 0, "schema version 应 >= 0");
    }

    // ─── 3. 引擎工厂 ───

    @Test
    @Order(3)
    @DisplayName("所有反编译引擎可创建")
    void shouldCreateAllDecompilerEngines() {
        for (DecompilerTypeEnum type : DecompilerTypeEnum.values()) {
            Decompiler engine = DecompilerFactory.getDecompiler(type);
            assertNotNull(engine, type + " 引擎不应为空");
            assertNotNull(engine.getName(), type + " 引擎名称不应为空");
        }
    }

    // ─── 3. 后台任务 ───

    // ─── 4. 应用路径 ───

    @Test
    @Order(4)
    @DisplayName("应用路径目录可解析")
    void shouldResolveAppPaths() {
        Path appDir = AppPaths.appDir();
        assertNotNull(appDir, "应用目录不能为空");
        assertTrue(Files.exists(appDir), "应用目录应存在: " + appDir);

        // 这些目录在首次启动时可能还没创建,只验证路径不为 null
        assertNotNull(AppPaths.cacheDir(), "缓存目录不能为空");
        assertNotNull(AppPaths.logsDir(), "日志目录不能为空");
    }

    // ─── 5. L2 内存缓存 ───

    @Test
    @Order(5)
    @DisplayName("L2 反编译缓存可读写和失效")
    void shouldReadWriteAndInvalidateCache() {
        DecompileCache cache = new DecompileCache();
        String wsKey = "smoke-ws";
        String internalName = "com/example/SmokeTest";
        DecompilerTypeEnum engine = DecompilerTypeEnum.VINEFLOWER;
        String optionsHash = "default";
        String source = "public class SmokeTest {}";

        // 写入 → 读取
        cache.put(wsKey, internalName, engine, optionsHash, source);
        String result = cache.get(wsKey, internalName, engine, optionsHash);
        assertEquals(source, result, "反编译缓存应能读取写入的内容");

        // 失效 → 读取为空
        cache.invalidate(wsKey, internalName);
        String afterInvalidate = cache.get(wsKey, internalName, engine, optionsHash);
        assertNull(afterInvalidate, "失效后缓存应返回 null");

        // 清空
        cache.clear();
        assertDoesNotThrow(cache::clear, "清空不应抛异常");
    }

    // ─── 6. AppConfig – 配置读写 ───

    @Test
    @Order(6)
    @DisplayName("配置可加载和验证")
    void shouldLoadConfig() {
        AppConfig config = AppContext.getInstance().config();
        assertNotNull(config, "配置不能为空");

        // 验证关键字段存在
        assertTrue(config.schemaVersion() >= 1, "schema version 应 >= 1");
        assertTrue(config.window().width() >= 400, "窗口宽度应 >= 400");
        assertTrue(config.window().height() >= 300, "窗口高度应 >= 300");
        assertNotNull(config.decompiler().defaultEngine(), "默认引擎不能为空");
    }

    // ─── 7. 应用上下文关闭 (必须最后执行) ───

    @Test
    @Order(7)
    @DisplayName("应用上下文关闭")
    void shouldShutdownAppContext() {
        AppContext ctx = AppContext.getInstance();
        ctx.shutdown();
        assertDoesNotThrow(ctx::shutdown, "重复关闭不应抛异常");
    }
}
