package com.bingbaihanji.fxdecomplie.util;

/**
 * 提供常见数学运算所需功能的实用类
 *
 * @author bingbaihanji
 * @date 2026-07-10 14:40:06
 * @description 提供常见数学运算所需功能的实用类
 */
public class MathUtils {

    private MathUtils() {
        throw new AssertionError("工具类不允许实例化");
    }

    /**
     * 返回两个参数的和,如果结果溢出 {@code int} 范围则抛出异常
     *
     * @param x 第一个值
     * @param y 第二个值
     * @return 两数之和
     * @throws ArithmeticException 如果结果溢出 int 范围
     */
    public static int addExact(int x, int y) {
        int sum = x + y;
        // 如果 x 和 y 同号,则它们的和也应与它们同号
        if ((x >= 0 == y >= 0) && (x >= 0 != sum >= 0)) {
            throw new ArithmeticException("integer overflow");
        } else {
            return sum;
        }
    }

    /**
     * 返回两个参数的和,如果结果溢出 {@code long} 范围则抛出异常
     *
     * @param x 第一个值
     * @param y 第二个值
     * @return 两数之和
     * @throws ArithmeticException 如果结果溢出 long 范围
     */
    public static long addExact(long x, long y) {
        long sum = x + y;
        // 如果 x 和 y 同号,则它们的和也应与它们同号
        if ((x >= 0 == y >= 0) && (x >= 0 != sum >= 0)) {
            throw new ArithmeticException("integer overflow");
        } else {
            return sum;
        }
    }

    /**
     * 返回两个参数的差,如果结果溢出 {@code int} 范围则抛出异常
     *
     * @param x 第一个值(被减数)
     * @param y 第二个值(减数)
     * @return 两数之差
     * @throws ArithmeticException 如果结果溢出 int 范围
     */
    public static int subtractExact(int x, int y) {
        int difference = x - y;
        // 如果 x 和 y 中只有一个为负,则差值应与 x 同号
        if ((x < 0 != y < 0) && (x < 0 != difference < 0)) {
            throw new ArithmeticException("integer overflow");
        }
        return difference;
    }

    /**
     * 返回两个参数的差,如果结果溢出 {@code long} 范围则抛出异常
     *
     * @param x 第一个值(被减数)
     * @param y 第二个值(减数)
     * @return 两数之差
     * @throws ArithmeticException 如果结果溢出 long 范围
     */
    public static long subtractExact(long x, long y) {
        long difference = x - y;
        // 如果 x 和 y 中只有一个为负,则差值应与 x 同号
        if ((x < 0 != y < 0) && (x < 0 != difference < 0)) {
            throw new ArithmeticException("integer overflow");
        }
        return difference;
    }

    /**
     * 返回两个参数的乘积,如果结果溢出 {@code int} 范围则抛出异常
     *
     * @param x 第一个值
     * @param y 第二个值
     * @return 两数之积
     * @throws ArithmeticException 如果结果溢出 int 范围
     */
    public static int multiplyExact(int x, int y) {
        int product = x * y;
        // 用其中一个因子除回,应得到另一个因子,否则发生了溢出
        if (x != 0 && y != 0 && (product / x != y || product / y != x)) {
            throw new ArithmeticException("integer overflow");
        }
        return product;
    }

    /**
     * 返回两个参数的乘积,如果结果溢出 {@code long} 范围则抛出异常
     *
     * @param x 第一个值
     * @param y 第二个值
     * @return 两数之积
     * @throws ArithmeticException 如果结果溢出 long 范围
     */
    public static long multiplyExact(long x, long y) {
        long product = x * y;
        // 用其中一个因子除回,应得到另一个因子,否则发生了溢出
        if (x != 0 && y != 0 && (product / x != y || product / y != x)) {
            throw new ArithmeticException("integer overflow");
        }
        return product;
    }

    /**
     * 返回参数自增 1 后的值,如果结果溢出 {@code int} 范围则抛出异常
     * 仅在参数为 {@linkplain Integer#MAX_VALUE 最大值} 时发生溢出
     *
     * @param a 要自增的值
     * @return 自增后的结果
     * @throws ArithmeticException 如果结果溢出 int 范围
     */
    public static int incrementExact(int a) {
        if (a == Integer.MAX_VALUE) {
            throw new ArithmeticException("integer overflow");
        } else {
            return a + 1;
        }
    }

    /**
     * 返回参数自增 1 后的值,如果结果溢出 {@code long} 范围则抛出异常
     * 仅在参数为 {@linkplain Long#MAX_VALUE 最大值} 时发生溢出
     *
     * @param a 要自增的值
     * @return 自增后的结果
     * @throws ArithmeticException 如果结果溢出 long 范围
     */
    public static long incrementExact(long a) {
        if (a == Long.MAX_VALUE) {
            throw new ArithmeticException("integer overflow");
        } else {
            return a + 1;
        }
    }

    /**
     * 返回参数自减 1 后的值,如果结果溢出 {@code int} 范围则抛出异常
     * 仅在参数为 {@linkplain Integer#MIN_VALUE 最小值} 时发生溢出
     *
     * @param a 要自减的值
     * @return 自减后的结果
     * @throws ArithmeticException 如果结果溢出 int 范围
     */
    public static int decrementExact(int a) {
        if (a == Integer.MIN_VALUE) {
            throw new ArithmeticException("integer overflow");
        } else {
            return a - 1;
        }
    }

    /**
     * 返回参数自减 1 后的值,如果结果溢出 {@code long} 范围则抛出异常
     * 仅在参数为 {@linkplain Long#MIN_VALUE 最小值} 时发生溢出
     *
     * @param a 要自减的值
     * @return 自减后的结果
     * @throws ArithmeticException 如果结果溢出 long 范围
     */
    public static long decrementExact(long a) {
        if (a == Long.MIN_VALUE) {
            throw new ArithmeticException("integer overflow");
        } else {
            return a - 1;
        }
    }

    /**
     * 返回参数的相反数,如果结果溢出 {@code int} 范围则抛出异常
     * 仅在参数为 {@linkplain Integer#MIN_VALUE 最小值} 时发生溢出
     *
     * @param a 要求相反数的值
     * @return 相反数
     * @throws ArithmeticException 如果结果溢出 int 范围
     */
    public static int negateExact(int a) {
        if (a == Integer.MIN_VALUE) {
            throw new ArithmeticException("integer overflow");
        } else {
            return -a;
        }
    }

    /**
     * 返回参数的相反数,如果结果溢出 {@code long} 范围则抛出异常
     * 仅在参数为 {@linkplain Long#MIN_VALUE 最小值} 时发生溢出
     *
     * @param a 要求相反数的值
     * @return 相反数
     * @throws ArithmeticException 如果结果溢出 long 范围
     */
    public static long negateExact(long a) {
        if (a == Long.MIN_VALUE) {
            throw new ArithmeticException("integer overflow");
        } else {
            return -a;
        }
    }

    /**
     * 将 {@code long} 值转换为 {@code int},如果该值溢出 {@code int} 范围则抛出异常
     *
     * @param value 要转换的 long 值
     * @return 转换为 int 的结果
     * @throws ArithmeticException 如果该值溢出 int 范围
     */
    public static int toIntExact(long value) {
        if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
            throw new ArithmeticException("integer overflow");
        } else {
            return (int) value;
        }
    }

    /**
     * 将数值限制在给定的数值范围内如果数值小于范围最小值,则返回最小值
     * 如果数值大于范围最大值,则返回最大值
     *
     * @param value 要钳制的值
     * @param min   结果的最小值
     * @param max   结果的最大值
     * @return 钳制后的值
     */
    public static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        } else if (value > max) {
            return max;
        }
        return value;
    }

    /**
     * 将数值限制在给定的数值范围内如果数值小于范围最小值,则返回最小值
     * 如果数值大于范围最大值,则返回最大值
     *
     * @param value 要钳制的值
     * @param min   结果的最小值
     * @param max   结果的最大值
     * @return 钳制后的值
     */
    public static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        } else if (value > max) {
            return max;
        }
        return value;
    }

    /**
     * 将数值限制在给定的数值范围内如果数值小于范围最小值,则返回最小值
     * 如果数值大于范围最大值,则返回最大值
     *
     * @param value 要钳制的值
     * @param min   结果的最小值
     * @param max   结果的最大值
     * @return 钳制后的值
     */
    public static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        } else if (value > max) {
            return max;
        }
        return value;
    }

    /**
     * 将数值限制在给定的数值范围内如果数值小于范围最小值,则返回最小值
     * 如果数值大于范围最大值,则返回最大值
     *
     * @param value 要钳制的值
     * @param min   结果的最小值
     * @param max   结果的最大值
     * @return 钳制后的值
     */
    public static long clamp(long value, long min, long max) {
        if (value < min) {
            return min;
        } else if (value > max) {
            return max;
        }
        return value;
    }
}