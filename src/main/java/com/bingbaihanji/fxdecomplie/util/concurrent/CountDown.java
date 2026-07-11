package com.bingbaihanji.fxdecomplie.util.concurrent;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * 可动态注册任务数的倒计时同步器
 *
 * <p>与 {@link java.util.concurrent.CountDownLatch} 不同，本类允许在等待前后继续注册任务
 * 每次 {@link #release()} 会减少一个计数，计数归零时唤醒等待线程</p>
 *
 * @author xDark
 */
public final class CountDown {
    private final Impl impl = new Impl();

    /**
     * 等待计数归零
     *
     * @throws InterruptedException
     * 		当前线程被中断时抛出
     */
    public void await() throws InterruptedException {
        impl.acquireSharedInterruptibly(1);
    }

    /**
     * 在指定时间内等待计数归零
     *
     * @param timeout
     * 		最大等待时间
     * @param unit
     * 		时间单位
     *
     * @throws InterruptedException
     * 		当前线程被中断时抛出
     */
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return impl.tryAcquireSharedNanos(1, unit.toNanos(timeout));
    }

    /**
     * 计数减一
     */
    public void release() {
        impl.releaseShared(1);
    }

    /**
     * 计数加一
     */
    public void register() {
        impl.bulkRegister(1);
    }

    /**
     * 批量增加计数
     *
     * @param count
     * 		要增加的数量,必须大于 0
     */
    public void bulkRegister(int count) {
        impl.bulkRegister(count);
    }

    private static final class Impl extends AbstractQueuedSynchronizer {

        void bulkRegister(int count) {
            if (count <= 0) {
                throw new IllegalArgumentException("count must be positive");
            }
            while (true) {
                int oldCount = getState();
                if (Integer.MAX_VALUE - oldCount < count) {
                    throw new IllegalStateException("count overflow");
                }
                int newCount = oldCount + count;
                if (compareAndSetState(oldCount, newCount)) {
                    return;
                }
            }
        }

        @Override
        protected int tryAcquireShared(int acquires) {
            return (getState() == 0) ? 1 : -1;
        }

        @Override
        protected boolean tryReleaseShared(int releases) {
            while (true) {
                int state = getState();
                if (state == 0) {
                    return false;
                }
                int newState = state - 1;
                if (compareAndSetState(state, newState)) {
                    return newState == 0;
                }
            }
        }
    }
}
