package com.bingbaihanji.windows.jfx.win32;

import com.bingbaihanji.windows.platform.win32.Win32Api;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 通过 JavaFX Stage 查找原生 Win32 窗口句柄的工具类
 *
 * <p>先在 JavaFX 线程通过反射获取句柄；若失败则通过枚举当前进程所有窗口的标题来匹配
 * 仅在 Windows 平台有效,其他平台直接返回 {@code Optional.empty()}</p>
 */
final class Win32WindowHandles {

    private static final long DEFAULT_POLL_DELAY_MS = 20L;

    private Win32WindowHandles() {
    }

    /**
     * 查找指定 Stage 对应的原生窗口句柄
     *
     * @param stage   JavaFX Stage
     * @param timeout 等待窗口创建的最大时长
     * @return 窗口句柄,若平台不支持或超时则返回 {@code Optional.empty()}
     */
    static Optional<WinDef.HWND> find(Stage stage, Duration timeout) {
        if (stage == null || !com.sun.jna.Platform.isWindows()) {
            return Optional.empty();
        }
        StageSnapshot snapshot = snapshot(stage, timeout);
        if (snapshot == null) {
            return Optional.empty();
        }
        WinDef.HWND reflected = snapshot.hwnd();
        if (isValid(reflected)) {
            return Optional.of(reflected);
        }
        // 反射获取失败,回退到按标题枚举当前进程窗口
        if (Platform.isFxApplicationThread()) {
            return Optional.ofNullable(findByCurrentProcessTitle(snapshot.title()))
                    .filter(Win32WindowHandles::isValid);
        }
        long deadline = System.nanoTime() + Math.max(0L, timeout.toNanos());
        do {
            WinDef.HWND hwnd = findByCurrentProcessTitle(snapshot.title());
            if (isValid(hwnd)) {
                return Optional.of(hwnd);
            }
            if (timeout.isZero()) {
                break;
            }
            try {
                Thread.sleep(DEFAULT_POLL_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        } while (System.nanoTime() < deadline);
        return Optional.empty();
    }

    /**
     * 获取窗口句柄的指针值(long)
     */
    static long pointerValue(WinDef.HWND hwnd) {
        return hwnd == null ? 0L : Pointer.nativeValue(hwnd.getPointer());
    }

    /**
     * 从指针值构造窗口句柄
     */
    static WinDef.HWND fromPointerValue(long value) {
        return value == 0L ? null : new WinDef.HWND(Pointer.createConstant(value));
    }

    /**
     * 获取 Stage 的快照(标题 + 句柄),必要时调度到 JavaFX 线程执行
     */
    private static StageSnapshot snapshot(Stage stage, Duration timeout) {
        if (Platform.isFxApplicationThread()) {
            return new StageSnapshot(stage.getTitle(), findByReflection(stage));
        }
        CompletableFuture<StageSnapshot> future = new CompletableFuture<>();
        try {
            Platform.runLater(() -> {
                try {
                    future.complete(new StageSnapshot(stage.getTitle(), findByReflection(stage)));
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
        } catch (IllegalStateException e) {
            // JavaFX 工具包未初始化
            return null;
        }
        try {
            long waitMillis = Math.max(50L, timeout.toMillis());
            return future.get(waitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException | TimeoutException e) {
            return null;
        }
    }

    /**
     * 通过反射从 JavaFX Stage 内部获取原生句柄
     * 不同 JavaFX 版本内部实现可能不同,标题枚举为备用方案
     */
    public static WinDef.HWND findByReflection(Window window) {
        try {
            Class<?> stageHelperClass = Class.forName("com.sun.javafx.stage.StageHelper");
            Method getPeer = stageHelperClass.getMethod("getPeer", Window.class);
            Object peer = getPeer.invoke(null, window);
            if (peer == null) {
                return null;
            }
            Method getRawHandle = peer.getClass().getMethod("getRawHandle");
            Object raw = getRawHandle.invoke(peer);
            if (raw instanceof Long value) {
                return fromPointerValue(value);
            }
            if (raw instanceof Integer value) {
                return fromPointerValue(Integer.toUnsignedLong(value));
            }
        } catch (Throwable ignored) {
            // JavaFX 内部实现因版本而异,标题枚举为备用方案
        }
        return null;
    }


    /**
     * 通过枚举当前进程所有窗口标题来查找匹配的窗口句柄
     *
     * <p>当标题为空时返回当前进程的第一个可见顶级窗口,适用于未设置标题的 Stage</p>
     */
    private static WinDef.HWND findByCurrentProcessTitle(String title) {
        boolean matchAny = title == null || title.isBlank();
        int currentPid = Win32Api.Kernel32Api.INSTANCE.GetCurrentProcessId();
        AtomicReference<WinDef.HWND> match = new AtomicReference<>();
        User32.INSTANCE.EnumWindows((hwnd, data) -> {
            if (!isValid(hwnd) || !User32.INSTANCE.IsWindowVisible(hwnd)) {
                return true;
            }
            IntByReference windowPid = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(hwnd, windowPid);
            if (windowPid.getValue() != currentPid) {
                return true;
            }
            if (matchAny) {
                // 标题为空时返回第一个可见的当前进程窗口
                match.set(hwnd);
                return false;
            }
            char[] buffer = new char[512];
            User32.INSTANCE.GetWindowText(hwnd, buffer, buffer.length);
            if (title.equals(trimNullTerminated(buffer))) {
                match.set(hwnd);
                return false;
            }
            return true;
        }, null);
        return match.get();
    }

    private static boolean isValid(WinDef.HWND hwnd) {
        return hwnd != null && pointerValue(hwnd) != 0L;
    }

    private static String trimNullTerminated(char[] chars) {
        int length = 0;
        while (length < chars.length && chars[length] != '\0') {
            length++;
        }
        return new String(chars, 0, length).trim();
    }

    /**
     * Stage 快照,包含标题和窗口句柄
     */
    private record StageSnapshot(String title, WinDef.HWND hwnd) {
    }
}
