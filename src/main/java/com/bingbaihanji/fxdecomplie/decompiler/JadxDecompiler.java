package com.bingbaihanji.fxdecomplie.decompiler;

import com.bingbaihanji.fxdecomplie.decompiler.jadx.JadxDecompilerFacade;
import com.bingbaihanji.fxdecomplie.decompiler.jadx.JadxDecompilerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * jadx 反编译引擎适配器
 * <p>
 * 该类只保留 {@link Decompiler} 接口适配,实际项目状态 输入构建和移植内核调用由
 * {@link JadxDecompilerFacade} 管理 
 *
 * @author bingbaihanji
 * @date 2026-07-11
 */
public class JadxDecompiler implements Decompiler {

    private static final Logger log = LoggerFactory.getLogger(JadxDecompiler.class);

    /** JADX 内核使用进程级共享临时目录,串行化以避免并发清理竞争 */
    private static final Semaphore JADX_CONCURRENCY = new Semaphore(1, true);

    private static final AtomicReference<Thread> JADX_OWNER = new AtomicReference<>();

    private static final AtomicLong JADX_LEASE_START_NANOS = new AtomicLong();

    /** 等待许可的最长时间,避免把整个 30s 请求预算耗在排队上 */
    private static final long ACQUIRE_TIMEOUT_SECONDS = 5;

    /** 许可租约;超过该时间视为持有者卡死,强制释放以自愈 */
    private static final long LEASE_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(120);

    private final JadxDecompilerFacade facade = JadxDecompilerFacade.getInstance();

    @Override
    public String decompile(String classFilePath, byte[] classBytes) {
        return decompileType(DecompilerContext.normalizeInternalName(classFilePath),
                classBytes, DecompilerContext.EMPTY);
    }

    @Override
    public String decompileType(String typeName, byte[] classBytes) {
        return decompileType(typeName, classBytes, DecompilerContext.EMPTY);
    }

    @Override
    public String decompile(String classFilePath, byte[] classBytes, DecompilerContext context) {
        return decompileType(DecompilerContext.normalizeInternalName(classFilePath),
                classBytes, context);
    }

    /**
     * 使用 jadx 引擎反编译指定类
     *
     * <p>核心流程:创建 jadx JadxDecompiler 实例,加载目标类字节码,
     * 调用 load() 初始化类层次图,然后通过 getCode() 获取反编译结果</p>
     *
     * @param typeName   类的内部名称(如 {@code com/example/MyClass})
     * @param classBytes 类的原始字节码
     * @param context    反编译上下文(可为 null,用于解析依赖类字节码)
     * @return 反编译后的 Java 源码字符串 异常时返回错误信息注释
     */
    @Override
    public String decompileType(String typeName, byte[] classBytes, DecompilerContext context) {
        if (!acquireJadxPermit()) {
            return "// jadx decompile failed: busy\n// Class: " + typeName;
        }
        try {
            return facade.decompile(new JadxDecompilerRequest(
                    DecompilerContext.normalizeInternalName(typeName),
                    typeName,
                    classBytes,
                    context));
        } finally {
            releaseJadxPermit();
        }
    }

    private static boolean acquireJadxPermit() {
        try {
            if (JADX_CONCURRENCY.tryAcquire(ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                JADX_OWNER.set(Thread.currentThread());
                JADX_LEASE_START_NANOS.set(System.nanoTime());
                return true;
            }
            Thread holder = JADX_OWNER.get();
            long leaseStart = JADX_LEASE_START_NANOS.get();
            if (holder != null && leaseStart > 0
                    && System.nanoTime() - leaseStart > LEASE_TIMEOUT_NANOS
                    && JADX_OWNER.compareAndSet(holder, null)) {
                log.warn("JADX 单许可持有超过 {}s,强制释放",
                        TimeUnit.NANOSECONDS.toSeconds(LEASE_TIMEOUT_NANOS));
                JADX_LEASE_START_NANOS.set(0);
                JADX_CONCURRENCY.release();
                if (JADX_CONCURRENCY.tryAcquire(ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    JADX_OWNER.set(Thread.currentThread());
                    JADX_LEASE_START_NANOS.set(System.nanoTime());
                    return true;
                }
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void releaseJadxPermit() {
        if (JADX_OWNER.compareAndSet(Thread.currentThread(), null)) {
            JADX_LEASE_START_NANOS.set(0);
            JADX_CONCURRENCY.release();
        }
    }

    @Override
    public DecompilerTypeEnum getType() {
        return DecompilerTypeEnum.JADX;
    }

    @Override
    public String getName() {
        return "jadx";
    }
}
