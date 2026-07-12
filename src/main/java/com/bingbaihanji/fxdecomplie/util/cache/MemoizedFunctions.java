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

    /**
     * 对单参数函数进行记忆化,返回带线程安全缓存的包装函数
     *
     * @param function 被包装的函数,不能为 null
     * @param <Key>    键类型
     * @param <Value>  值类型
     * @return 带缓存的记忆化函数
     */
    public static <Key, Value> Function<Key, Value> memoize(Function<Key, Value> function) {
        Objects.requireNonNull(function, "function");
        return new MemoizedFunction<>(function);
    }

    /**
     * 对双参数函数进行记忆化,返回带线程安全缓存的包装函数
     *
     * @param function 被包装的双参数函数,不能为 null
     * @param <KeyA>   第一个键类型
     * @param <KeyB>   第二个键类型
     * @param <Value>  值类型
     * @return 带缓存的记忆化双参数函数
     */
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
