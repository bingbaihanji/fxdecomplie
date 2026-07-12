package com.bingbaihanji.fxdecomplie.util.math;


import org.objectweb.asm.Type;

/**
 * 通用数字解析及其他数值运算工具
 *
 * @author Matt Coley
 */
public class NumberUtil {
    /**
     * @param number
     * 		要转换为字符串的数值
     *
     * @return 该数值的字符串表示
     */

    public static String toString(Number number) {
        if (number == null) {
            return "0";
        }

        if (number instanceof Integer || number instanceof Byte || number instanceof Short) {
            return Integer.toString(number.intValue());
        } else if (number instanceof Double) {
            return Double.toString(number.doubleValue());
        } else if (number instanceof Long) {
            return number.longValue() + "L";
        } else if (number instanceof Float) {
            return number.floatValue() + "F";
        }

        throw new IllegalArgumentException("Unsupported number type: " + number.getClass().getName());
    }

    /**
     * @param input
     * 		表示一个数字的文本输入
     * 		<ul>
     * 		<li>以 {@code F} 结尾的数字解析为 {@link Float}</li>
     * 		<li>以 {@code D} 结尾的数字解析为 {@link Double}</li>
     * 		<li>以 {@code L} 结尾的数字解析为 {@link Long}</li>
     * 		</ul>
     *
     * @return 从文本解析出的数字
     * 可以是 {@link Integer}、{@link Long}、{@link Float} 或 {@link Double}
     *
     * @throws NumberFormatException
     * 		当输入无法被解析时抛出
     */
    @SuppressWarnings("")

    public static Number parse(String input) {
        String text = input.trim().toUpperCase();
        Number value;
        if (text.indexOf('.') > 0) {
            value = parseDecimal(text);
        } else {
            if (text.endsWith("L") && text.startsWith("0X")) {
                String substring = text.substring(2, text.indexOf("L"));
                if (substring.isEmpty()) {
                    return 0L;
                }
                value = Long.parseLong(substring, 16);
            } else if (text.endsWith("L")) {
                value = Long.parseLong(text.substring(0, text.indexOf("L")));
            } else if (text.startsWith("0X")) {
                String substring = text.substring(2);
                if (substring.isEmpty()) {
                    return 0;
                }
                value = Integer.parseInt(substring, 16);
            } else if (text.endsWith("F")) {
                value = Float.parseFloat(text.substring(0, text.indexOf("F")));
            } else if (text.endsWith("D") || text.contains(".")) {
                value = Double.parseDouble(text.substring(0, text.indexOf("D")));
            } else {
                value = Integer.parseInt(text);
            }
        }
        return value;
    }

    /**
     * @param text
     * 		表示一个小数的文本输入
     *
     * @return 小数,类型为 {@link Float} 或 {@link Double}
     */

    private static Number parseDecimal(String text) {
        if (text.endsWith("F")) {
            int end = text.indexOf("F");
            if (end > 0) {
                text = text.substring(0, end);
            }
            return Float.parseFloat(text);
        } else if (text.endsWith("D") || text.contains(".")) {
            int end = text.indexOf("D");
            if (end > 0) {
                text = text.substring(0, end);
            }
            return Double.parseDouble(text);
        } else {
            return Double.parseDouble(text);
        }
    }

    /**
     * @param type1
     * 		数值类型,必须是基本类型
     * @param type2
     * 		数值类型,必须是基本类型
     *
     * @return 范围更宽的基本类型
     */

    public static Type getWidestType(Type type1, Type type2) {
        if (type1.getSort() > type2.getSort()) {
            return type1;
        }
        return type2;
    }

    /**
     * 比较两个数值,不受其类型限制
     *
     * @param right
     * 		第一个值
     * @param left
     * 		第二个值
     *
     * @return {@code X.compare(left, right)} 的比较结果
     */
    public static int cmp(Number left, Number right) {
        // 先检查范围最宽的类型,沿类型列表向下依次收窄直到 int
        if (right instanceof Double || left instanceof Double) {
            return Double.compare(left.doubleValue(), right.doubleValue());
        } else if (right instanceof Float || left instanceof Float) {
            return Float.compare(left.floatValue(), right.floatValue());
        } else if (right instanceof Long || left instanceof Long) {
            return Long.compare(left.longValue(), right.longValue());
        } else {
            return Integer.compare(left.intValue(), right.intValue());
        }
    }

    /**
     * @param first
     * 		第一个值
     * @param second
     * 		第二个值
     *
     * @return 差值
     */

    public static Number sub(Number first, Number second) {
        // 先检查范围最宽的类型,沿类型列表向下依次收窄直到 int
        if (second instanceof Double || first instanceof Double) {
            return first.doubleValue() - second.doubleValue();
        } else if (second instanceof Float || first instanceof Float) {
            return first.floatValue() - second.floatValue();
        } else if (second instanceof Long || first instanceof Long) {
            return first.longValue() - second.longValue();
        } else {
            return first.intValue() - second.intValue();
        }
    }

    /**
     * @param first
     * 		第一个值
     * @param second
     * 		第二个值
     *
     * @return 和值
     */

    public static Number add(Number first, Number second) {
        // 先检查范围最宽的类型,沿类型列表向下依次收窄直到 int
        if (second instanceof Double || first instanceof Double) {
            return first.doubleValue() + second.doubleValue();
        } else if (second instanceof Float || first instanceof Float) {
            return first.floatValue() + second.floatValue();
        } else if (second instanceof Long || first instanceof Long) {
            return first.longValue() + second.longValue();
        } else {
            return first.intValue() + second.intValue();
        }
    }

    /**
     * @param first
     * 		第一个值
     * @param second
     * 		第二个值
     *
     * @return 积值
     */

    public static Number mul(Number first, Number second) {
        // 先检查范围最宽的类型,沿类型列表向下依次收窄直到 int
        if (second instanceof Double || first instanceof Double) {
            return first.doubleValue() * second.doubleValue();
        } else if (second instanceof Float || first instanceof Float) {
            return first.floatValue() * second.floatValue();
        } else if (second instanceof Long || first instanceof Long) {
            return first.longValue() * second.longValue();
        } else {
            return first.intValue() * second.intValue();
        }
    }

    /**
     * @param first
     * 		第一个值
     * @param second
     * 		第二个值
     *
     * @return 商值
     */

    public static Number div(Number first, Number second) {
        // 先检查范围最宽的类型,沿类型列表向下依次收窄直到 int
        if (second instanceof Double || first instanceof Double) {
            return first.doubleValue() / second.doubleValue();
        } else if (second instanceof Float || first instanceof Float) {
            return first.floatValue() / second.floatValue();
        } else if (second instanceof Long || first instanceof Long) {
            return first.longValue() / second.longValue();
        } else {
            return first.intValue() / second.intValue();
        }
    }

    /**
     * @param first
     * 		第一个值
     * @param second
     * 		第二个值
     *
     * @return 余数值
     */

    public static Number rem(Number first, Number second) {
        // 先检查范围最宽的类型,沿类型列表向下依次收窄直到 int
        if (second instanceof Double || first instanceof Double) {
            return first.doubleValue() % second.doubleValue();
        } else if (second instanceof Float || first instanceof Float) {
            return first.floatValue() % second.floatValue();
        } else if (second instanceof Long || first instanceof Long) {
            return first.longValue() % second.longValue();
        } else {
            return first.intValue() % second.intValue();
        }
    }

    /**
     * @param first
     * 		第一个值
     * @param second
     * 		第二个值
     *
     * @return 保留相同置位位的值(按位与)
     */

    public static Number and(Number first, Number second) {
        // 先检查范围最宽的类型,沿类型列表向下依次收窄直到 int
        if (second instanceof Long || first instanceof Long) {
            return first.longValue() & second.longValue();
        } else {
            return first.intValue() & second.intValue();
        }
    }

    /**
     * @param first
     * 		第一个值
     * @param second
     * 		第二个值
     *
     * @return 保留所有置位位的值(按位或)
     */

    public static Number or(Number first, Number second) {
        // 先检查范围最宽的类型,沿类型列表向下依次收窄直到 int
        if (second instanceof Long || first instanceof Long) {
            return first.longValue() | second.longValue();
        } else {
            return first.intValue() | second.intValue();
        }
    }

    /**
     * @param first
     * 		第一个值
     * @param second
     * 		第二个值
     *
     * @return 保留不相同位的值(按位异或)
     */

    public static Number xor(Number first, Number second) {
        // 先检查范围最宽的类型,沿类型列表向下依次收窄直到 int
        if (second instanceof Long || first instanceof Long) {
            return first.longValue() ^ second.longValue();
        } else {
            return first.intValue() ^ second.intValue();
        }
    }

    /**
     * @param value
     * 		数值
     *
     * @return 取负后的值
     */

    public static Number neg(Number value) {
        // 先检查范围最宽的类型,沿类型列表向下依次收窄直到 int
        if (value instanceof Double) {
            return -value.doubleValue();
        } else if (value instanceof Float) {
            return -value.floatValue();
        } else if (value instanceof Long) {
            return -value.longValue();
        } else {
            return -value.intValue();
        }
    }

    /**
     * @param value
     * 		数值
     * @param shift
     * 		移位的位数
     *
     * @return 移位后的值
     */

    public static Number shiftLeft(Number value, Number shift) {
        // 先检查范围最宽的类型,沿类型列表向下依次收窄直到 int
        if (value instanceof Long) {
            return value.longValue() << shift.longValue();
        } else {
            return value.intValue() << shift.intValue();
        }
    }

    /**
     * @param value
     * 		数值
     * @param shift
     * 		移位的位数
     *
     * @return 移位后的值
     */

    public static Number shiftRight(Number value, Number shift) {
        // 先检查范围最宽的类型,沿类型列表向下依次收窄直到 int
        if (value instanceof Long) {
            return value.longValue() >> shift.longValue();
        } else {
            return value.intValue() >> shift.intValue();
        }
    }

    /**
     * @param value
     * 		数值
     * @param shift
     * 		移位的位数
     *
     * @return 移位后的值
     */

    public static Number shiftRightU(Number value, Number shift) {
        // 先检查范围最宽的类型,沿类型列表向下依次收窄直到 int
        if (value instanceof Long) {
            return value.longValue() >>> shift.longValue();
        } else {
            return value.intValue() >>> shift.intValue();
        }
    }

    /**
     * 针对 {@code int} 的快速 {@link Math#pow(double, double)}
     *
     * @param base
     * 		底数
     * @param exp
     * 		指数(幂次)
     *
     * @return {@code base^exp}
     */
    public static int intPow(int base, int exp) {
        if (exp < 0) {
            throw new IllegalArgumentException("Exponent must be positive");
        }
        int result = 1;
        while (true) {
            if ((exp & 1) != 0) {
                result *= base;
            }
            if ((exp >>= 1) == 0) {
                break;
            }
            base *= base;
        }
        return result;
    }

    /**
     * @param value
     * 		基准值
     * @param min
     * 		钳制下限
     * @param max
     * 		钳制上限
     *
     * @return 返回该值;若低于 min 则返回 min,若高于 max 则返回 max
     */
    public static int intClamp(int value, int min, int max) {
        if (value > max) {
            return max;
        }
        if (value < min) {
            return min;
        }
        return value;
    }

    /**
     * @param value
     * 		基准值
     * @param min
     * 		钳制下限
     * @param max
     * 		钳制上限
     *
     * @return 返回该值;若低于 min 则返回 min,若高于 max 则返回 max
     */
    public static double doubleClamp(double value, double min, double max) {
        if (value > max) {
            return max;
        }
        if (value < min) {
            return min;
        }
        return value;
    }

    /**
     * @param i
     * 		某个值
     *
     * @return {@code i != 0}
     */
    public static boolean isNonZero(int i) {
        return i != 0;
    }

    /**
     * @param i
     * 		某个值
     *
     * @return {@code i == 0}
     */
    public static boolean isZero(int i) {
        return i == 0;
    }

    /**
     * @param a
     * 		某个值
     * @param b
     * 		另一个值
     *
     * @return 当两者符号相同时返回 {@code true}
     */
    public static boolean haveSameSign(int a, int b) {
        return (a > 0 && b > 0) || (a < 0 && b < 0);
    }

    /**
     * @param v
     * 		某个值
     *
     * @return 大于或等于 {@code v} 的下一个 2 的幂
     */
    public static int nextPowerOfTwo(int v) {
        // 位运算技巧,向上取整到下一个 2 的幂
        v--;
        v |= v >> 1;
        v |= v >> 2;
        v |= v >> 4;
        v |= v >> 8;
        v |= v >> 16;
        return v + 1;
    }
}
