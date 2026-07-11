package com.bingbaihanji.fxdecomplie.util.cache;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 函数记忆化工具
 *
 * <p>返回的函数使用线程安全缓存被包装函数不应依赖副作用；同一个 key 在并发首次访问时
 * 仍可能因 {@link ConcurrentHashMap#computeIfAbsent(Object, Function)} 的语义被调用多次</p>
 *
 * @author Amejonah
 */
public final class MemoizedFunctions {

    private MemoizedFunctions() {
        throw new AssertionError("utility class");
    }

    public static <Key, Value> Function<Key, Value> memoize(Function<Key, Value> function) {
        Objects.requireNonNull(function, "function");
        return new MemoizedFunction<>(function);
    }

    public static <KeyA, KeyB, Value> BiFunction<KeyA, KeyB, Value> memoize(BiFunction<KeyA, KeyB, Value> function) {
        Objects.requireNonNull(function, "function");
        return new BiMemoizedFunction<>(function);
    }

    private static final class MemoizedFunction<Key, Value> implements Function<Key, Value> {
        private final Map<Key, Value> cache = new ConcurrentHashMap<>();
        private final Function<Key, Value> function;

        private MemoizedFunction(Function<Key, Value> function) {
            this.function = function;
        }

        @Override
        public Value apply(Key key) {
            return cache.computeIfAbsent(key, function);
        }
    }

    private static final class BiMemoizedFunction<KeyA, KeyB, Value> implements BiFunction<KeyA, KeyB, Value> {
        private final Map<KeyA, Map<KeyB, Value>> cache = new ConcurrentHashMap<>();
        private final BiFunction<KeyA, KeyB, Value> function;

        private BiMemoizedFunction(BiFunction<KeyA, KeyB, Value> function) {
            this.function = function;
        }


        @Override
        public Value apply(KeyA keyA, KeyB keyB) {
            return cache.computeIfAbsent(keyA, _ -> new ConcurrentHashMap<>())
                    .computeIfAbsent(keyB, k -> function.apply(keyA, keyB));
        }
    }
}
