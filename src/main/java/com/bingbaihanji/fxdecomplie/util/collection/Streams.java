package com.bingbaihanji.fxdecomplie.util.collection;


import com.bingbaihanji.fxdecomplie.util.concurrent.CountDown;
import com.bingbaihanji.fxdecomplie.util.reflect.ReflectUtils;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Stream 工具类
 *
 * @author xDark
 */
public final class Streams {
    private Streams() {
    }

    /**
     * 合并给定的多个 Stream
     *
     * @param streams
     * 		待合并的 Stream
     * @param <T>
     * 		Stream 元素类型
     *
     * @return 合并后的 Stream
     */
    public static <T> Stream<? extends T> of(Stream<? extends T>... streams) {
        Stream<? extends T> merged = null;
        for (Stream<? extends T> stream : streams) {
            if (merged == null) {
                merged = stream;
            } else {
                merged = Stream.concat(merged, stream);
            }
        }
        return merged == null ? Stream.empty() : merged;
    }

    /**
     * 使 Stream 支持中断
     *
     * @param stream
     * 		要使其可中断的 Stream
     * @param <T>
     * 		Stream 元素类型
     *
     * @return 可中断的 Stream
     */
    public static <T> Stream<T> interruptable(Stream<? extends T> stream) {
        Spliterator<? extends T> spliterator = stream.spliterator();
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<>(spliterator.estimateSize(), spliterator.characteristics()) {
            @Override
            public boolean tryAdvance(Consumer<? super T> action) {
                if (Thread.interrupted()) {
                    return false;
                }
                return spliterator.tryAdvance(action);
            }

            @Override
            public void forEachRemaining(Consumer<? super T> action) {
                if (Thread.interrupted()) {
                    return;
                }
                spliterator.forEachRemaining(action);
            }
        }, stream.isParallel());
    }

    /**
     * 在给定的执行器中消费 Stream 中的所有元素
     *
     * @param stream
     * 		要消费的 Stream
     * @param consumer
     * 		元素消费者
     * @param executor
     * 		使用的执行器
     * @param <T>
     * 		元素类型
     */
    public static <T> void forEachOn(Stream<T> stream, Consumer<? super T> consumer, Executor executor) {
        AtomicReference<Throwable> throwable = new AtomicReference<>();
        CountDown countDown = new CountDown();
        stream.forEach(x -> {
            Throwable thrown = throwable.get();
            if (thrown != null) {
                ReflectUtils.propagate(thrown);
            }
            countDown.register();
            executor.execute(() -> {
                try {
                    if (throwable.get() == null) {
                        try {
                            consumer.accept(x);
                        } catch (Throwable t) {
                            throwable.compareAndSet(null, t);
                        }
                    }
                } finally {
                    countDown.release();
                }
            });
        });
        try {
            countDown.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Throwable thrown = throwable.get();
        if (thrown != null) {
            ReflectUtils.propagate(thrown);
        }
    }

    /**
     * 从 {@code seed} 开始递归遍历
     *
     * @param seed
     * 		初始种子
     * @param fn
     * 		变换函数
     * @param <T>
     * 		元素类型
     *
     * @return 包含所有遍历元素的 Stream
     */
    public static <T> Stream<T> recurse(T seed, Function<? super T, Stream<? extends T>> fn) {
        return Stream.concat(
                Stream.of(seed),
                Stream.of(seed)
                        .flatMap(fn)
                        .flatMap(x -> recurse(x, fn)));
    }

    /**
     * 从 {@code seed} 开始递归遍历
     *
     * @param seed
     * 		初始 Stream
     * @param fn
     * 		变换函数
     * @param <T>
     * 		元素类型
     *
     * @return 包含所有遍历元素的 Stream
     */
    public static <T> Stream<T> recurse(Stream<? extends T> seed, Function<? super T, Stream<? extends T>> fn) {
        return seed.flatMap(x -> recurse(x, fn));
    }

    /**
     * @param flatMap
     * 		变换函数
     * @param <T>
     * 		元素类型
     *
     * @return 包含所有遍历元素的 Stream
     */

    public static <T> Stream<T> recurseWithoutCycles(T seed, Function<T, Set<T>> flatMap) {
        Deque<Iterator<T>> vertices = new ArrayDeque<>();
        Set<T> visited = new HashSet<>();
        return recurseWithoutCycles0(seed, flatMap, vertices, visited);
    }

    /**
     * @param flatMap
     * 		变换函数
     * @param <T>
     * 		元素类型
     *
     * @return 包含所有遍历元素的 Stream
     */

    public static <T> Stream<T> recurseIdentityWithoutCycles(T seed, Function<T, Set<T>> flatMap) {
        Deque<Iterator<T>> vertices = new ArrayDeque<>();
        Set<T> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        return recurseWithoutCycles0(seed, flatMap, vertices, visited);
    }

    private static <T> Stream<T> recurseWithoutCycles0(T seed,
                                                       Function<T, Set<T>> flatMap,
                                                       Deque<Iterator<T>> vertices, Set<T> visited) {
        vertices.push(Collections.singletonList(seed).iterator());
        return StreamSupport.stream(new Spliterators.AbstractSpliterator<>(Long.MAX_VALUE, Spliterator.IMMUTABLE | Spliterator.NONNULL) {
            @Override
            public boolean tryAdvance(Consumer<? super T> action) {
                while (true) {
                    Iterator<T> iterator = vertices.peek();
                    if (iterator == null) {
                        return false;
                    }
                    if (!iterator.hasNext()) {
                        vertices.poll();
                        continue;
                    }
                    T vertex = iterator.next();
                    if (visited.add(vertex)) {
                        action.accept(vertex);
                        vertices.push(flatMap.apply(vertex).iterator());
                        return true;
                    }
                }
            }
        }, false);
    }
}
