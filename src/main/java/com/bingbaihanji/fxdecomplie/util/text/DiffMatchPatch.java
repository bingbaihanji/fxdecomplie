package com.bingbaihanji.fxdecomplie.util.text;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文本差异比对、匹配与补丁应用工具
 * <p>
 * 计算两段文本之间的差异并生成补丁,支持将补丁应用到另一段文本 源自 Google diff-match-patch 算法实现
 * </p>
 *
 * @author fraser@google.com (Neil Fraser)
 * @author bingbaihanji
 * @date 2025-12-08
 */
public final class DiffMatchPatch {

    /** 空白行结束正则 */
    private static final Pattern BLANK_LINE_END = Pattern.compile("\\n\\r?\\n\\Z", Pattern.DOTALL);
    /** 空白行开始正则 */
    private static final Pattern BLANK_LINE_START = Pattern.compile("\\A\\r?\\n\\r?\\n", Pattern.DOTALL);
    private static final Pattern PATCH_HEADER = Pattern.compile("^@@ -(\\d+),?(\\d*) \\+(\\d+),?(\\d*) @@$");
    private final short matchMaxBits = 32;
    /** Diff 操作超时秒数(0 表示无限) */
    public float diffTimeout = 1.0f;
    /** 空编辑操作的成本(以编辑字符数为单位) */
    public short diffEditCost = 4;
    /** 匹配阈值(0.0 = 精确匹配,1.0 = 非常宽松) */
    public float matchThreshold = 0.5f;
    /** 匹配搜索距离(0 = 精确位置,1000+ = 广泛匹配) */
    public int matchDistance = 1000;
    /** 大块删除时的匹配阈值(0.0 = 精确,1.0 = 宽松) */
    public float patchDeleteThreshold = 0.5f;
    /** 补丁上下文字符块大小 */
    public short patchMargin = 4;

    /**
     * 反转义 URL 编码字符(兼容 JavaScript 的 encodeURI),例如 "%3F" → "?"
     */
    private static String unescapeForEncodeUriCompatability(String str) {
        return str.replace("%21", "!")
                .replace("%7E", "~")
                .replace("%27", "'")
                .replace("%28", "(")
                .replace("%29", ")")
                .replace("%3B", ";")
                .replace("%2F", "/")
                .replace("%3F", "?")
                .replace("%3A", ":")
                .replace("%40", "@")
                .replace("%26", "&")
                .replace("%3D", "=")
                .replace("%2B", "+")
                .replace("%24", "$")
                .replace("%2C", ",")
                .replace("%23", "#");
    }

    /**
     * 计算两段文本的差异默认启用行级快速差异检测
     * @param text1 原始文本
     * @param text2 新文本
     * @return Diff 对象链表
     */
    public LinkedList<Diff> diffMain(String text1, String text2) {
        return diffMain(text1, text2, true);
    }

    /**
     * 计算两段文本的差异
     * @param text1 原始文本
     * @param text2 新文本
     * @param checklines 是否启用行级快速差异检测
     * @return Diff 对象链表
     */
    public LinkedList<Diff> diffMain(String text1, String text2, boolean checklines) {
        // 设置差异计算完成的截止时间
        long deadline;
        if (diffTimeout <= 0) {
            deadline = Long.MAX_VALUE;
        } else {
            deadline = System.currentTimeMillis() + (long) (diffTimeout * 1000);
        }
        return diffMain(text1, text2, checklines, deadline);
    }

    /**
     * 计算两段文本的差异(内部实现)通过去除公共前缀/后缀简化问题
     */
    private LinkedList<Diff> diffMain(String text1, String text2, boolean checklines, long deadline) {
        if (text1 == null || text2 == null) {
            throw new IllegalArgumentException("输入参数为 null (diffMain)");
        }
        LinkedList<Diff> diffs;
        if (text1.equals(text2)) {
            diffs = new LinkedList<>();
            if (!text1.isEmpty()) {
                diffs.add(new Diff(Operation.EQUAL, text1));
            }
            return diffs;
        }

        // 去除公共前缀(加速)
        int commonLength = diffCommonPrefix(text1, text2);
        String commonPrefix = text1.substring(0, commonLength);
        text1 = text1.substring(commonLength);
        text2 = text2.substring(commonLength);

        // 去除公共后缀(加速)
        commonLength = diffCommonSuffix(text1, text2);
        String commonSuffix = text1.substring(text1.length() - commonLength);
        text1 = text1.substring(0, text1.length() - commonLength);
        text2 = text2.substring(0, text2.length() - commonLength);

        // 计算中间块的差异
        diffs = diffCompute(text1, text2, checklines, deadline);

        // 恢复前缀和后缀
        if (!commonPrefix.isEmpty()) {
            diffs.addFirst(new Diff(Operation.EQUAL, commonPrefix));
        }
        if (!commonSuffix.isEmpty()) {
            diffs.addLast(new Diff(Operation.EQUAL, commonSuffix));
        }

        diffCleanupMerge(diffs);
        return diffs;
    }

    /**
     * 计算两段文本差异(假设无公共前后缀)
     * @param text1 原始字符串
     * @param text2 新字符串
     * @param checklines 加速标志false 时不预先进行 行级差异检测 true 时运行更快但非最优的差异算法
     * @param deadline 差异计算截止时间
     * @return Diff 对象链表
     */
    private LinkedList<Diff> diffCompute(String text1, String text2, boolean checklines, long deadline) {
        LinkedList<Diff> diffs = new LinkedList<>();

        if (text1.isEmpty()) {
            // 仅添加文本(加速)
            diffs.add(new Diff(Operation.INSERT, text2));
            return diffs;
        }

        if (text2.isEmpty()) {
            // 仅删除文本(加速)
            diffs.add(new Diff(Operation.DELETE, text1));
            return diffs;
        }

        String longText = text1.length() > text2.length() ? text1 : text2;
        String shortText = text1.length() > text2.length() ? text2 : text1;
        int i = longText.indexOf(shortText);
        if (i != -1) {
            // 短文本包含在长文本中(快速路径)
            Operation op = (text1.length() > text2.length()) ? Operation.DELETE : Operation.INSERT;
            diffs.add(new Diff(op, longText.substring(0, i)));
            diffs.add(new Diff(Operation.EQUAL, shortText));
            diffs.add(new Diff(op, longText.substring(i + shortText.length())));
            return diffs;
        }

        if (shortText.length() == 1) {
            // 单字符串
            // 经过快速路径后,字符不可能相等
            diffs.add(new Diff(Operation.DELETE, text1));
            diffs.add(new Diff(Operation.INSERT, text2));
            return diffs;
        }

        // 检查问题是否可以被拆分为两部分
        String[] hm = diffHalfMatch(text1, text2);
        if (hm != null) {
            // 找到半匹配,整理返回数据
            String text1A = hm[0];
            String text1B = hm[1];
            String text2A = hm[2];
            String text2B = hm[3];
            String midCommon = hm[4];
            // 将两组数据分别处理
            LinkedList<Diff> diffsA = diffMain(text1A, text2A, checklines, deadline);
            LinkedList<Diff> diffsB = diffMain(text1B, text2B, checklines, deadline);
            // 合并结果
            diffs = diffsA;
            diffs.add(new Diff(Operation.EQUAL, midCommon));
            diffs.addAll(diffsB);
            return diffs;
        }

        if (checklines && text1.length() > 100 && text2.length() > 100) {
            return diffLineMode(text1, text2, deadline);
        }

        return diffBisect(text1, text2, deadline);
    }

    /**
     * 先在两段字符串上快速执行行级差异,
     *
     * 此加速可能产生非最优差异结果
     * @param text1 原始字符串
     * @param text2 新字符串
     * @param deadline 差异计算截止时间
     * @return Diff 对象链表
     */
    private LinkedList<Diff> diffLineMode(String text1, String text2, long deadline) {
        // 先按行扫描文本
        LinesToCharsResult b = diffLinesToChars(text1, text2);
        text1 = b.chars1;
        text2 = b.chars2;
        List<String> lineArray = b.lineArray;

        LinkedList<Diff> diffs = diffMain(text1, text2, false, deadline);

        // 将差异结果转换回原始文本
        diffCharsToLines(diffs, lineArray);
        // 消除异常匹配(如空白行)
        diffCleanupSemantic(diffs);

        // 逐字符重新计算替换块差异
        // 末尾添加占位条目
        diffs.add(new Diff(Operation.EQUAL, ""));
        int countDelete = 0;
        int countInsert = 0;
        StringBuilder textDelete = new StringBuilder();
        StringBuilder textInsert = new StringBuilder();
        ListIterator<Diff> pointer = diffs.listIterator();
        Diff thisDiff = pointer.next();
        while (thisDiff != null) {
            switch (thisDiff.operation) {
                case INSERT:
                    countInsert++;
                    textInsert.append(thisDiff.text);
                    break;
                case DELETE:
                    countDelete++;
                    textDelete.append(thisDiff.text);
                    break;
                case EQUAL:
                    // 遇到相等时检查之前是否有冗余
                    if (countDelete >= 1 && countInsert >= 1) {
                        // 删除有问题记录并添加合并后的记录
                        pointer.previous();
                        for (int j = 0; j < countDelete + countInsert; j++) {
                            pointer.previous();
                            pointer.remove();
                        }
                        for (Diff newDiff : diffMain(textDelete.toString(), textInsert.toString(), false, deadline)) {
                            pointer.add(newDiff);
                        }
                    }
                    countInsert = 0;
                    countDelete = 0;
                    textDelete = new StringBuilder();
                    textInsert = new StringBuilder();
                    break;
                default:
                    break;
            }
            thisDiff = pointer.hasNext() ? pointer.next() : null;
        }
        diffs.removeLast(); // 移除末尾的占位条目

        return diffs;
    }

    /**
     * 查找差异的"中间蛇",将问题一分为二 并返回递归构造的差异结果 参见 Myers 1986 论文：An O(ND) Difference Algorithm and
     * Its Variations
     * @param text1 原始字符串
     * @param text2 新字符串
     * @param deadline 未完成时的终止时间
     * @return Diff 对象链表
     */
    public LinkedList<Diff> diffBisect(String text1, String text2, long deadline) {
        // 缓存文本长度以避免重复计算
        int text1Length = text1.length();
        int text2Length = text2.length();
        int maxD = (text1Length + text2Length + 1) / 2;
        int vLength = 2 * maxD;
        int[] v1 = new int[vLength];
        int[] v2 = new int[vLength];
        for (int x = 0; x < vLength; x++) {
            v1[x] = -1;
            v2[x] = -1;
        }
        v1[maxD + 1] = 0;
        v2[maxD + 1] = 0;
        int delta = text1Length - text2Length;
        // 如果字符总数为奇数则前路径将与反向路径发生碰撞
        boolean front = (delta % 2 != 0);
        // k 循环的起始和结束偏移
        // 防止映射超出网格空间
        int k1start = 0;
        int k1end = 0;
        int k2start = 0;
        int k2end = 0;
        for (int d = 0; d < maxD; d++) {
            // 如果到达截止时间则退出
            if (System.currentTimeMillis() > deadline) {
                break;
            }

            // 向前路径走一步
            for (int k1 = -d + k1start; k1 <= d - k1end; k1 += 2) {
                int k1Offset = maxD + k1;
                int x1;
                if (k1 == -d || (k1 != d && v1[k1Offset - 1] < v1[k1Offset + 1])) {
                    x1 = v1[k1Offset + 1];
                } else {
                    x1 = v1[k1Offset - 1] + 1;
                }
                int y1 = x1 - k1;
                while (x1 < text1Length && y1 < text2Length && text1.charAt(x1) == text2.charAt(y1)) {
                    x1++;
                    y1++;
                }
                v1[k1Offset] = x1;
                if (x1 > text1Length) {
                    // 超出图表右边界
                    k1end += 2;
                } else if (y1 > text2Length) {
                    // 超出图表下边界
                    k1start += 2;
                } else if (front) {
                    int k2Offset = maxD + delta - k1;
                    if (k2Offset >= 0 && k2Offset < vLength && v2[k2Offset] != -1) {
                        // 镜像 x2 到左上角坐标系
                        int x2 = text1Length - v2[k2Offset];
                        if (x1 >= x2) {
                            // 检测到重叠
                            return diffBisectSplit(text1, text2, x1, y1, deadline);
                        }
                    }
                }
            }

            // 向反向路径走一步
            for (int k2 = -d + k2start; k2 <= d - k2end; k2 += 2) {
                int k2Offset = maxD + k2;
                int x2;
                if (k2 == -d || (k2 != d && v2[k2Offset - 1] < v2[k2Offset + 1])) {
                    x2 = v2[k2Offset + 1];
                } else {
                    x2 = v2[k2Offset - 1] + 1;
                }
                int y2 = x2 - k2;
                while (x2 < text1Length && y2 < text2Length
                        && text1.charAt(text1Length - x2 - 1) == text2.charAt(text2Length - y2 - 1)) {
                    x2++;
                    y2++;
                }
                v2[k2Offset] = x2;
                if (x2 > text1Length) {
                    // 超出图表左边界
                    k2end += 2;
                } else if (y2 > text2Length) {
                    // 超出图表上边界
                    k2start += 2;
                } else if (!front) {
                    int k1Offset = maxD + delta - k2;
                    if (k1Offset >= 0 && k1Offset < vLength && v1[k1Offset] != -1) {
                        int x1 = v1[k1Offset];
                        int y1 = maxD + x1 - k1Offset;
                        // 镜像 x2 到左上角坐标系
                        x2 = text1Length - x2;
                        if (x1 >= x2) {
                            // 检测到重叠
                            return diffBisectSplit(text1, text2, x1, y1, deadline);
                        }
                    }
                }
            }
        }
        // 差异计算超时(已到达截止时间),或差异数量等于字符数量(无公共部分)
        LinkedList<Diff> diffs = new LinkedList<>();
        diffs.add(new Diff(Operation.DELETE, text1));
        diffs.add(new Diff(Operation.INSERT, text2));
        return diffs;
    }

    /**
     * 根据"中间蛇"位置将差异分为两部分,递归计算
     * @param text1 原始字符串
     * @param text2 新字符串
     * @param x text1 中的分割点索引
     * @param y text2 中的分割点索引
     * @param deadline 未完成时的终止时间
     * @return Diff 对象链表
     */
    private LinkedList<Diff> diffBisectSplit(String text1, String text2, int x, int y, long deadline) {
        String text1A = text1.substring(0, x);
        String text2A = text2.substring(0, y);
        String text1B = text1.substring(x);
        String text2B = text2.substring(y);

        // 顺序计算两个差异
        LinkedList<Diff> diffs = diffMain(text1A, text2A, false, deadline);
        LinkedList<Diff> diffsB = diffMain(text1B, text2B, false, deadline);

        diffs.addAll(diffsB);
        return diffs;
    }

    /**
     * 将两段文本拆分为字符串列表,将文本缩减为哈希字符串,每个 Unicode 字符代表一行
     * @param text1 第一段文本
     * @param text2 第二段文本
     * @return 包含编码后的 text1、text2 以及唯一字符串列表的结果对象(列表第 0 个元素故意留空)
     */
    public LinesToCharsResult diffLinesToChars(String text1, String text2) {
        List<String> lineArray = new ArrayList<>();
        Map<String, Integer> lineHash = new HashMap<>();
        // 例如：lineArray[4] == "Hello\n"
        // 例如：lineHash.get("Hello\n") == 4

        // "\x00" 是合法字符,但部分调试器不兼容
        // 插入一个无用条目以避免生成 null 字符
        lineArray.add("");

        String chars1 = diffLinesToCharsMunge(text1, lineArray, lineHash);
        String chars2 = diffLinesToCharsMunge(text2, lineArray, lineHash);
        return new LinesToCharsResult(chars1, chars2, lineArray);
    }

    /**
     * 将文本拆分为字符串列表,并将文本缩减为哈希字符串,每个 Unicode 字符代表一行
     * @param text 待编码的字符串
     * @param lineArray 唯一字符串列表
     * @param lineHash 字符串到索引的映射
     * @return 编码后的字符串
     */
    private String diffLinesToCharsMunge(String text, List<String> lineArray, Map<String, Integer> lineHash) {
        int lineStart = 0;
        int lineEnd = -1;
        String line;
        StringBuilder chars = new StringBuilder();
        // 遍历文本,为每行提取子字符串
        // text.split('\n') 会临时加倍内存占用,修改文本会产生大量待 GC 的大字符串
        while (lineEnd < text.length() - 1) {
            lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd == -1) {
                lineEnd = text.length() - 1;
            }
            line = text.substring(lineStart, lineEnd + 1);
            lineStart = lineEnd + 1;

            if (lineHash.containsKey(line)) {
                chars.append(String.valueOf((char) (int) lineHash.get(line)));
            } else {
                lineArray.add(line);
                lineHash.put(line, lineArray.size() - 1);
                chars.append(String.valueOf((char) (lineArray.size() - 1)));
            }
        }
        return chars.toString();
    }

    /**
     * 将 diff 中的行哈希还原为真实文本行
     * @param diffs Diff 对象链表
     * @param lineArray 唯一字符串列表
     */
    public void diffCharsToLines(LinkedList<Diff> diffs, List<String> lineArray) {
        StringBuilder text;
        for (Diff diff : diffs) {
            text = new StringBuilder();
            for (int y = 0; y < diff.text.length(); y++) {
                text.append(lineArray.get(diff.text.charAt(y)));
            }
            diff.text = text.toString();
        }
    }

    /**
     * 计算两段字符串的公共前缀长度
     * @param text1 第一段字符串
     * @param text2 第二段字符串
     * @return 两段字符串开头公共字符的数量
     */
    public int diffCommonPrefix(String text1, String text2) {
        // 性能分析： http://neil.fraser.name/news/2007/10/09/
        int n = Math.min(text1.length(), text2.length());
        for (int i = 0; i < n; i++) {
            if (text1.charAt(i) != text2.charAt(i)) {
                return i;
            }
        }
        return n;
    }

    /**
     * 计算两段字符串的公共后缀长度
     * @param text1 第一段字符串
     * @param text2 第二段字符串
     * @return 两段字符串末尾公共字符的数量
     */
    public int diffCommonSuffix(String text1, String text2) {
        // 性能分析： http://neil.fraser.name/news/2007/10/09/
        int text1Length = text1.length();
        int text2Length = text2.length();
        int n = Math.min(text1Length, text2Length);
        for (int i = 1; i <= n; i++) {
            if (text1.charAt(text1Length - i) != text2.charAt(text2Length - i)) {
                return i - 1;
            }
        }
        return n;
    }

    /**
     * 判断第一段字符串的后缀与第二段字符串的前缀是否存在重叠
     * @param text1 第一段字符串
     * @param text2 第二段字符串
     * @return 第一段末尾与第二段开头公共字符的数量
     */
    public int diffCommonOverlap(String text1, String text2) {
        // 缓存文本长度以避免重复计算
        int text1Length = text1.length();
        int text2Length = text2.length();
        // 排除空字符串情况
        if (text1Length == 0 || text2Length == 0) {
            return 0;
        }
        // 截断较长字符串
        if (text1Length > text2Length) {
            text1 = text1.substring(text1Length - text2Length);
        } else if (text1Length < text2Length) {
            text2 = text2.substring(0, text1Length);
        }
        int textLength = Math.min(text1Length, text2Length);
        // 快速检查最坏情况：完全相同
        if (text1.equals(text2)) {
            return textLength;
        }

        // 从单字符匹配开始,逐步增加长度直到无法匹配
        // 性能分析：http://neil.fraser.name/news/2010/11/04/
        int best = 0;
        int length = 1;
        while (true) {
            String pattern = text1.substring(textLength - length);
            int found = text2.indexOf(pattern);
            if (found == -1) {
                return best;
            }
            length += found;
            if (found == 0 || text1.substring(textLength - length).equals(text2.substring(0, length))) {
                best = length;
                length++;
            }
        }
    }

    /**
     * 检测两段文本是否共享一个至少为较长文本一半长度的公共子串 此加速可能产生非最优差异结果
     * @param text1 第一段字符串
     * @param text2 第二段字符串
     * @return 五元素字符串数组,包含 text1 的前缀、text1 的后缀、text2 的前缀、text2 的后缀以及公共中间部分；无匹配时返回 null
     */
    public String[] diffHalfMatch(String text1, String text2) {
        if (diffTimeout <= 0) {
            // 无时间限制时不冒险返回非最优差异
            return null;
        }
        String longText = text1.length() > text2.length() ? text1 : text2;
        String shortText = text1.length() > text2.length() ? text2 : text1;
        if (longText.length() < 4 || shortText.length() * 2 < longText.length()) {
            return null;
        }

        // 首先检查第二四分之一段是否是半匹配的种子
        String[] hm1 = diffHalfMatchI(longText, shortText, (longText.length() + 3) / 4);
        // 再基于第三四分之一段检查
        String[] hm2 = diffHalfMatchI(longText, shortText, (longText.length() + 1) / 2);
        String[] hm;
        if (hm1 == null && hm2 == null) {
            return null;
        } else if (hm2 == null) {
            hm = hm1;
        } else if (hm1 == null) {
            hm = hm2;
        } else {
            // 两者都匹配,选择较长的
            hm = hm1[4].length() > hm2[4].length() ? hm1 : hm2;
        }

        // 找到半匹配,整理返回数据
        if (text1.length() > text2.length()) {
            return hm;
        } else {
            return new String[]{hm[2], hm[3], hm[0], hm[1], hm[4]};
        }
    }

    /**
     * 检测 shortText 中是否存在一个至少为 longText 一半长度的子串匹配
     * @param longText 较长字符串
     * @param shortText 较短字符串
     * @param i longText 中四分之一长度子串的起始索引
     * @return 五元素字符串数组,包含 longText 的前缀、longText 的后缀、shortText 的前缀、shortText
     * 的后缀以及公共中间部分；无匹配时返回 null
     */
    private String[] diffHalfMatchI(String longText, String shortText, int i) {
        // 以位置 i 的 1/4 长度子串作为种子
        String seed = longText.substring(i, i + longText.length() / 4);
        int j = -1;
        String bestCommon = "";
        String bestLongTextA = "", bestLongTextB = "";
        String bestShortTextA = "", bestShortTextB = "";
        while ((j = shortText.indexOf(seed, j + 1)) != -1) {
            int prefixLength = diffCommonPrefix(longText.substring(i), shortText.substring(j));
            int suffixLength = diffCommonSuffix(longText.substring(0, i), shortText.substring(0, j));
            if (bestCommon.length() < suffixLength + prefixLength) {
                bestCommon = shortText.substring(j - suffixLength, j) + shortText.substring(j, j + prefixLength);
                bestLongTextA = longText.substring(0, i - suffixLength);
                bestLongTextB = longText.substring(i + prefixLength);
                bestShortTextA = shortText.substring(0, j - suffixLength);
                bestShortTextB = shortText.substring(j + prefixLength);
            }
        }
        if (bestCommon.length() * 2 >= longText.length()) {
            return new String[]{bestLongTextA, bestLongTextB, bestShortTextA, bestShortTextB, bestCommon};
        } else {
            return null;
        }
    }

    /**
     * 通过消除语义上无意义的相等来减少编辑次数
     * @param diffs Diff 对象链表
     */
    public void diffCleanupSemantic(LinkedList<Diff> diffs) {
        if (diffs.isEmpty()) {
            return;
        }
        boolean changes = false;
        Stack<Diff> equalities = new Stack<>(); // 相等元素栈
        String lastEquality = null; // 始终等于 equalities.lastElement().text
        ListIterator<Diff> pointer = diffs.listIterator();
        // 相等之前变化的字符数
        int lengthInsertions1 = 0;
        int lengthDeletions1 = 0;
        // 相等之后变化的字符数
        int lengthInsertions2 = 0;
        int lengthDeletions2 = 0;
        Diff thisDiff = pointer.next();
        while (thisDiff != null) {
            if (thisDiff.operation == Operation.EQUAL) {
                // 发现相等
                equalities.push(thisDiff);
                lengthInsertions1 = lengthInsertions2;
                lengthDeletions1 = lengthDeletions2;
                lengthInsertions2 = 0;
                lengthDeletions2 = 0;
                lastEquality = thisDiff.text;
            } else {
                // 插入或删除操作
                if (thisDiff.operation == Operation.INSERT) {
                    lengthInsertions2 += thisDiff.text.length();
                } else {
                    lengthDeletions2 += thisDiff.text.length();
                }
                // 消除小于或等于两侧编辑量的相等
                if (lastEquality != null && (lastEquality.length() <= Math.max(lengthInsertions1, lengthDeletions1))
                        && (lastEquality.length() <= Math.max(lengthInsertions2, lengthDeletions2))) {
                    // 回退到有问题的相等处
                    while (thisDiff != equalities.lastElement()) {
                        thisDiff = pointer.previous();
                    }
                    pointer.next();

                    // 将相等替换为删除
                    pointer.set(new Diff(Operation.DELETE, lastEquality));
                    // 插入对应的插入操作
                    pointer.add(new Diff(Operation.INSERT, lastEquality));

                    equalities.pop(); // 丢弃刚删除的相等项
                    if (!equalities.empty()) {
                        // 丢弃前一个相等项(需要重新评估)
                        equalities.pop();
                    }
                    if (equalities.empty()) {
                        // 没有前序相等项,回退到开头
                        while (pointer.hasPrevious()) {
                            pointer.previous();
                        }
                    } else {
                        // 存在安全的相等项可作为回退点
                        thisDiff = equalities.lastElement();
                        while (thisDiff != pointer.previous()) {
                            // 有意为空循环,仅用于回退游标
                        }
                    }

                    lengthInsertions1 = 0; // 重置计数器
                    lengthInsertions2 = 0;
                    lengthDeletions1 = 0;
                    lengthDeletions2 = 0;
                    lastEquality = null;
                    changes = true;
                }
            }
            thisDiff = pointer.hasNext() ? pointer.next() : null;
        }

        // 规范化 diff
        if (changes) {
            diffCleanupMerge(diffs);
        }
        diffCleanupSemanticLossless(diffs);

        // 查找删除和插入之间的重叠
        // 例如：<del>abcxxx</del><ins>xxxdef</ins> → <del>abc</del>xxx<ins>def</ins>
        // 例如：<del>xxxabc</del><ins>defxxx</ins> → <ins>def</ins>xxx<del>abc</del>
        // 仅当重叠大小与前后的编辑相当时才提取
        pointer = diffs.listIterator();
        Diff prevDiff = null;
        thisDiff = null;
        if (pointer.hasNext()) {
            prevDiff = pointer.next();
            if (pointer.hasNext()) {
                thisDiff = pointer.next();
            }
        }
        while (prevDiff != null && thisDiff != null) {
            if (prevDiff.operation == Operation.DELETE && thisDiff.operation == Operation.INSERT) {
                String deletion = prevDiff.text;
                String insertion = thisDiff.text;
                int overlapLength1 = this.diffCommonOverlap(deletion, insertion);
                int overlapLength2 = this.diffCommonOverlap(insertion, deletion);
                if (overlapLength1 >= overlapLength2) {
                    if (overlapLength1 >= deletion.length() / 2.0 || overlapLength1 >= insertion.length() / 2.0) {
                        // 发现重叠：插入相等项并修剪周围编辑
                        pointer.previous();
                        pointer.add(new Diff(Operation.EQUAL, insertion.substring(0, overlapLength1)));
                        prevDiff.text = deletion.substring(0, deletion.length() - overlapLength1);
                        thisDiff.text = insertion.substring(overlapLength1);
                        // pointer.add 在游标前插入,无需跳过新元素
                    }
                } else {
                    if (overlapLength2 >= deletion.length() / 2.0 || overlapLength2 >= insertion.length() / 2.0) {
                        // 发现反向重叠：插入相等项并交换修剪周围编辑
                        pointer.previous();
                        pointer.add(new Diff(Operation.EQUAL, deletion.substring(0, overlapLength2)));
                        prevDiff.operation = Operation.INSERT;
                        prevDiff.text = insertion.substring(0, insertion.length() - overlapLength2);
                        thisDiff.operation = Operation.DELETE;
                        thisDiff.text = deletion.substring(overlapLength2);
                        // pointer.add 在游标前插入,无需跳过新元素
                    }
                }
                thisDiff = pointer.hasNext() ? pointer.next() : null;
            }
            prevDiff = thisDiff;
            thisDiff = pointer.hasNext() ? pointer.next() : null;
        }
    }

    /**
     * 查找两侧均为相等匹配的单次编辑,将编辑平移到单词边界对齐 例如：The c<ins>at c</ins>ame. → The <ins>cat </ins>came.
     * @param diffs Diff 对象链表
     */
    public void diffCleanupSemanticLossless(LinkedList<Diff> diffs) {
        String equality1, edit, equality2;
        String commonString;
        int commonOffset;
        int score, bestScore;
        String bestEquality1, bestEdit, bestEquality2;
        // 在起始位置创建新迭代器
        ListIterator<Diff> pointer = diffs.listIterator();
        Diff prevDiff = pointer.hasNext() ? pointer.next() : null;
        Diff thisDiff = pointer.hasNext() ? pointer.next() : null;
        Diff nextDiff = pointer.hasNext() ? pointer.next() : null;
        // 有意忽略首尾元素(无需检查)
        while (nextDiff != null) {
            if (thisDiff != null && prevDiff != null && prevDiff.operation == Operation.EQUAL
                    && nextDiff.operation == Operation.EQUAL) {
                // 单次编辑被相等项包围
                equality1 = prevDiff.text;
                edit = thisDiff.text;
                equality2 = nextDiff.text;

                // 首先,将编辑尽可能左移
                commonOffset = diffCommonSuffix(equality1, edit);
                if (commonOffset != 0) {
                    commonString = edit.substring(edit.length() - commonOffset);
                    equality1 = equality1.substring(0, equality1.length() - commonOffset);
                    edit = commonString + edit.substring(0, edit.length() - commonOffset);
                    equality2 = commonString + equality2;
                }

                // 然后逐字符向右移动,寻找最佳匹配位置
                bestEquality1 = equality1;
                bestEdit = edit;
                bestEquality2 = equality2;
                bestScore = diffCleanupSemanticScore(equality1, edit) + diffCleanupSemanticScore(edit, equality2);
                while (!edit.isEmpty() && !equality2.isEmpty() && edit.charAt(0) == equality2.charAt(0)) {
                    equality1 += edit.charAt(0);
                    edit = edit.substring(1) + equality2.charAt(0);
                    equality2 = equality2.substring(1);
                    score = diffCleanupSemanticScore(equality1, edit) + diffCleanupSemanticScore(edit, equality2);
                    // >= 鼓励编辑尾部出现空格而非开头
                    if (score >= bestScore) {
                        bestScore = score;
                        bestEquality1 = equality1;
                        bestEdit = edit;
                        bestEquality2 = equality2;
                    }
                }

                if (!prevDiff.text.equals(bestEquality1)) {
                    // 找到改进,保存回 diff
                    if (!bestEquality1.isEmpty()) {
                        prevDiff.text = bestEquality1;
                    } else {
                        pointer.previous(); // 越过 nextDiff
                        pointer.previous(); // 越过 thisDiff
                        pointer.previous(); // 越过 prevDiff
                        pointer.remove(); // 删除 prevDiff
                        pointer.next(); // 越过 thisDiff
                        pointer.next(); // 越过 nextDiff
                    }
                    thisDiff.text = bestEdit;
                    if (!bestEquality2.isEmpty()) {
                        nextDiff.text = bestEquality2;
                    } else {
                        pointer.remove(); // 删除 nextDiff
                        nextDiff = thisDiff;
                        thisDiff = prevDiff;
                    }
                }
            }
            prevDiff = thisDiff;
            thisDiff = nextDiff;
            nextDiff = pointer.hasNext() ? pointer.next() : null;
        }
    }

    /**
     * 给定两段字符串,计算边界落在逻辑边界上的内部匹配质量分数 分数范围 6(最佳)到 0(最差)
     * @param one 第一段字符串
     * @param two 第二段字符串
     * @return 匹配质量分数
     */
    private int diffCleanupSemanticScore(String one, String two) {
        if (one.isEmpty() || two.isEmpty()) {
            // 边缘情况得分最高
            return 6;
        }

        // 各语言对"空白字符"等概念的定义存在细微差异,此函数主要用于美化输出,
        // 因此选择使用各语言的本地特性而非强制完全一致
        char char1 = one.charAt(one.length() - 1);
        char char2 = two.charAt(0);
        boolean nonAlphaNumeric1 = !Character.isLetterOrDigit(char1);
        boolean nonAlphaNumeric2 = !Character.isLetterOrDigit(char2);
        boolean whitespace1 = nonAlphaNumeric1 && Character.isWhitespace(char1);
        boolean whitespace2 = nonAlphaNumeric2 && Character.isWhitespace(char2);
        boolean lineBreak1 = whitespace1 && Character.getType(char1) == Character.CONTROL;
        boolean lineBreak2 = whitespace2 && Character.getType(char2) == Character.CONTROL;
        boolean blankLine1 = lineBreak1 && BLANK_LINE_END.matcher(one).find();
        boolean blankLine2 = lineBreak2 && BLANK_LINE_START.matcher(two).find();

        if (blankLine1 || blankLine2) {
            // 空白行：5 分
            return 5;
        } else if (lineBreak1 || lineBreak2) {
            // 换行：4 分
            return 4;
        } else if (nonAlphaNumeric1 && !whitespace1 && whitespace2) {
            // 句尾：3 分
            return 3;
        } else if (whitespace1 || whitespace2) {
            // 空白字符：2 分
            return 2;
        } else if (nonAlphaNumeric1 || nonAlphaNumeric2) {
            // 非字母数字：1 分
            return 1;
        }
        return 0;
    }

    /**
     * 通过消除操作上无意义的相等来减少编辑次数
     * @param diffs Diff 对象链表
     */
    public void diffCleanupEfficiency(LinkedList<Diff> diffs) {
        if (diffs.isEmpty()) {
            return;
        }
        boolean changes = false;
        Stack<Diff> equalities = new Stack<>(); // 相等元素栈
        String lastEquality = null; // 始终等于 equalities.lastElement().text
        ListIterator<Diff> pointer = diffs.listIterator();
        // 最后一个相等之前是否存在插入操作
        boolean preIns = false;
        // 最后一个相等之前是否存在删除操作
        boolean preDel = false;
        // 最后一个相等之后是否存在插入操作
        boolean postIns = false;
        // 最后一个相等之后是否存在删除操作
        boolean postDel = false;
        Diff thisDiff = pointer.next();
        Diff safeDiff = thisDiff; // 已知不可拆分的最后一个 Diff
        while (thisDiff != null) {
            if (thisDiff.operation == Operation.EQUAL) {
                // 发现相等
                if (thisDiff.text.length() < diffEditCost && (postIns || postDel)) {
                    // 找到候选
                    equalities.push(thisDiff);
                    preIns = postIns;
                    preDel = postDel;
                    lastEquality = thisDiff.text;
                } else {
                    // 不是候选,也永远不会成为候选
                    equalities.clear();
                    lastEquality = null;
                    safeDiff = thisDiff;
                }
                postIns = postDel = false;
            } else {
                // 插入或删除操作
                if (thisDiff.operation == Operation.DELETE) {
                    postDel = true;
                } else {
                    postIns = true;
                }
                /*
                 * 五种需要拆分的情况： <ins>A</ins><del>B</del>XY<ins>C</ins><del>D</del>
                 * <ins>A</ins>X<ins>C</ins><del>D</del>
                 * <ins>A</ins><del>B</del>X<ins>C</ins>
                 * <ins>A</del>X<ins>C</ins><del>D</del>
                 * <ins>A</ins><del>B</del>X<del>C</del>
                 */
                if (lastEquality != null && ((preIns && preDel && postIns && postDel)
                        || ((lastEquality.length() < diffEditCost / 2) && ((preIns ? 1 : 0) + (preDel ? 1 : 0)
                        + (postIns ? 1 : 0) + (postDel ? 1 : 0)) == 3))) {
                    // 回退到有问题的相等处
                    while (thisDiff != equalities.lastElement()) {
                        thisDiff = pointer.previous();
                    }
                    pointer.next();

                    // 将相等替换为删除
                    pointer.set(new Diff(Operation.DELETE, lastEquality));
                    // 插入对应的插入操作
                    pointer.add(thisDiff = new Diff(Operation.INSERT, lastEquality));

                    equalities.pop(); // 丢弃刚删除的相等项
                    lastEquality = null;
                    if (preIns && preDel) {
                        // 未做出可能影响前序条目的修改,继续
                        postIns = postDel = true;
                        equalities.clear();
                        safeDiff = thisDiff;
                    } else {
                        if (!equalities.empty()) {
                            // 丢弃前一个相等项(需要重新评估)
                            equalities.pop();
                        }
                        if (equalities.empty()) {
                            // 没有前序可疑相等项,回退到最后一个已知安全 diff
                            thisDiff = safeDiff;
                        } else {
                            // 存在可回退的相等项
                            thisDiff = equalities.lastElement();
                        }
                        while (thisDiff != pointer.previous()) {
                            // 有意为空循环,仅用于回退游标
                        }
                        postIns = postDel = false;
                    }

                    changes = true;
                }
            }
            thisDiff = pointer.hasNext() ? pointer.next() : null;
        }

        if (changes) {
            diffCleanupMerge(diffs);
        }
    }

    /**
     * 重排并合并同类编辑段,合并相等部分任何编辑段在不超过相等项的情况下均可移动
     * @param diffs Diff 对象链表
     */
    public void diffCleanupMerge(LinkedList<Diff> diffs) {
        diffs.add(new Diff(Operation.EQUAL, "")); // 末尾添加占位条目
        ListIterator<Diff> pointer = diffs.listIterator();
        int countDelete = 0;
        int countInsert = 0;
        StringBuilder textDelete = new StringBuilder();
        StringBuilder textInsert = new StringBuilder();
        Diff thisDiff = pointer.next();
        Diff prevEqual = null;
        int commonLength;
        while (thisDiff != null) {
            switch (thisDiff.operation) {
                case INSERT:
                    countInsert++;
                    textInsert.append(thisDiff.text);
                    prevEqual = null;
                    break;
                case DELETE:
                    countDelete++;
                    textDelete.append(thisDiff.text);
                    prevEqual = null;
                    break;
                case EQUAL:
                    if (countDelete + countInsert > 1) {
                        boolean bothTypes = countDelete != 0 && countInsert != 0;
                        // 删除有问题的记录
                        pointer.previous(); // 反向移动
                        while (countDelete-- > 0) {
                            pointer.previous();
                            pointer.remove();
                        }
                        while (countInsert-- > 0) {
                            pointer.previous();
                            pointer.remove();
                        }
                        String insertText = textInsert.toString();
                        String deleteText = textDelete.toString();
                        if (bothTypes) {
                            // 分解出公共前缀
                            commonLength = diffCommonPrefix(insertText, deleteText);
                            if (commonLength != 0) {
                                if (pointer.hasPrevious()) {
                                    thisDiff = pointer.previous();
                                    assert thisDiff.operation == Operation.EQUAL : "前一个 diff 应为相等操作";
                                    thisDiff.text += insertText.substring(0, commonLength);
                                    pointer.next();
                                } else {
                                    pointer.add(new Diff(Operation.EQUAL, insertText.substring(0, commonLength)));
                                }
                                insertText = insertText.substring(commonLength);
                                deleteText = deleteText.substring(commonLength);
                            }
                            // 分解出公共后缀
                            commonLength = diffCommonSuffix(insertText, deleteText);
                            if (commonLength != 0) {
                                thisDiff = pointer.next();
                                thisDiff.text = insertText.substring(insertText.length() - commonLength)
                                        + thisDiff.text;
                                insertText = insertText.substring(0, insertText.length() - commonLength);
                                deleteText = deleteText.substring(0, deleteText.length() - commonLength);
                                pointer.previous();
                            }
                        }
                        // 插入合并后的记录
                        if (!deleteText.isEmpty()) {
                            pointer.add(new Diff(Operation.DELETE, deleteText));
                        }
                        if (!insertText.isEmpty()) {
                            pointer.add(new Diff(Operation.INSERT, insertText));
                        }
                        // 前进到相等项
                        thisDiff = pointer.hasNext() ? pointer.next() : null;
                    } else if (prevEqual != null) {
                        // 将此相等项与上一相等项合并
                        prevEqual.text += thisDiff.text;
                        pointer.remove();
                        thisDiff = pointer.previous();
                        pointer.next(); // 正向移动
                    }
                    countInsert = 0;
                    countDelete = 0;
                    textDelete = new StringBuilder();
                    textInsert = new StringBuilder();
                    prevEqual = thisDiff;
                    break;
                default:
                    break;
            }
            thisDiff = pointer.hasNext() ? pointer.next() : null;
        }
        if (diffs.getLast().text.isEmpty()) {
            diffs.removeLast(); // 移除末尾的占位条目
        }

        /*
         * 第二遍：查找两侧均为相等匹配的单次编辑,平移到一侧以消除相等项 例如：A<ins>BA</ins>C → <ins>AB</ins>AC
         */
        boolean changes = false;
        // 在起始位置创建新迭代器(而非回退当前迭代器)
        pointer = diffs.listIterator();
        Diff prevDiff = pointer.hasNext() ? pointer.next() : null;
        thisDiff = pointer.hasNext() ? pointer.next() : null;
        Diff nextDiff = pointer.hasNext() ? pointer.next() : null;
        // 有意忽略首尾元素(无需检查)
        while (nextDiff != null) {
            if (thisDiff != null && prevDiff != null && prevDiff.operation == Operation.EQUAL
                    && nextDiff.operation == Operation.EQUAL) {
                // 单次编辑被相等项包围
                if (thisDiff.text.endsWith(prevDiff.text)) {
                    // 将编辑平移到前一个相等项之上
                    thisDiff.text = prevDiff.text
                            + thisDiff.text.substring(0, thisDiff.text.length() - prevDiff.text.length());
                    nextDiff.text = prevDiff.text + nextDiff.text;
                    pointer.previous(); // 越过 nextDiff
                    pointer.previous(); // 越过 thisDiff
                    pointer.previous(); // 越过 prevDiff
                    pointer.remove(); // 删除 prevDiff
                    pointer.next(); // 越过 thisDiff
                    thisDiff = pointer.next(); // 越过 nextDiff
                    nextDiff = pointer.hasNext() ? pointer.next() : null;
                    changes = true;
                } else if (thisDiff.text.startsWith(nextDiff.text)) {
                    // 将编辑平移到下一个相等项之上
                    prevDiff.text += nextDiff.text;
                    thisDiff.text = thisDiff.text.substring(nextDiff.text.length()) + nextDiff.text;
                    pointer.remove(); // 删除 nextDiff
                    nextDiff = pointer.hasNext() ? pointer.next() : null;
                    changes = true;
                }
            }
            prevDiff = thisDiff;
            thisDiff = nextDiff;
            nextDiff = pointer.hasNext() ? pointer.next() : null;
        }
        // 如果发生了平移,需要重新排序并再次平移扫描
        if (changes) {
            diffCleanupMerge(diffs);
        }
    }

    /**
     * loc 为 text1 中的位置,计算并返回 text2 中的对应位置 例如："The cat" vs "The big cat",1→1,5→8
     * @param diffs Diff 对象链表
     * @param loc text1 中的位置
     * @return text2 中的匹配位置
     */
    public int diffXIndex(LinkedList<Diff> diffs, int loc) {
        int chars1 = 0;
        int chars2 = 0;
        int lastChars1 = 0;
        int lastChars2 = 0;
        Diff lastDiff = null;
        for (Diff aDiff : diffs) {
            if (aDiff.operation != Operation.INSERT) {
                // 相等或删除
                chars1 += aDiff.text.length();
            }
            if (aDiff.operation != Operation.DELETE) {
                // 相等或插入
                chars2 += aDiff.text.length();
            }
            if (chars1 > loc) {
                // 超出目标位置
                lastDiff = aDiff;
                break;
            }
            lastChars1 = chars1;
            lastChars2 = chars2;
        }
        if (lastDiff != null && lastDiff.operation == Operation.DELETE) {
            // 该位置已被删除
            return lastChars2;
        }
        // 加上剩余字符长度
        return lastChars2 + (loc - lastChars1);
    }

    /**
     * 将 Diff 列表转换为 HTML 格式报告
     * @param diffs Diff 对象链表
     * @return HTML 表示
     */
    public String diffPrettyHtml(LinkedList<Diff> diffs) {
        StringBuilder html = new StringBuilder();
        for (Diff aDiff : diffs) {
            String text = aDiff.text
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
            switch (aDiff.operation) {
                case INSERT:
                    html.append("<ins style=\"background:#e6ffe6;\">").append(text).append("</ins>");
                    break;
                case DELETE:
                    html.append("<del style=\"background:#ffe6e6;\">").append(text).append("</del>");
                    break;
                case EQUAL:
                    html.append("<span>").append(text).append("</span>");
                    break;
                default:
                    break;
            }
        }
        return html.toString();
    }

    /**
     * 计算并返回源文本(所有相等和删除)
     * @param diffs Diff 对象链表
     * @return 源文本
     */
    public String diffText1(LinkedList<Diff> diffs) {
        StringBuilder text = new StringBuilder();
        for (Diff aDiff : diffs) {
            if (aDiff.operation != Operation.INSERT) {
                text.append(aDiff.text);
            }
        }
        return text.toString();
    }

    /**
     * 计算并返回目标文本(所有相等和插入)
     * @param diffs Diff 对象链表
     * @return 目标文本
     */
    public String diffText2(LinkedList<Diff> diffs) {
        StringBuilder text = new StringBuilder();
        for (Diff aDiff : diffs) {
            if (aDiff.operation != Operation.DELETE) {
                text.append(aDiff.text);
            }
        }
        return text.toString();
    }

    /**
     * 计算 Levenshtein 距离,即插入、删除或替换的字符总数
     * @param diffs Diff 对象链表
     * @return 变更数量
     */
    public int diffLevenshtein(LinkedList<Diff> diffs) {
        int levenshtein = 0;
        int insertions = 0;
        int deletions = 0;
        for (Diff aDiff : diffs) {
            switch (aDiff.operation) {
                case INSERT:
                    insertions += aDiff.text.length();
                    break;
                case DELETE:
                    deletions += aDiff.text.length();
                    break;
                case EQUAL:
                    // 一次删除 + 一次插入 = 一次替换
                    levenshtein += Math.max(insertions, deletions);
                    insertions = 0;
                    deletions = 0;
                    break;
                default:
                    break;
            }
        }
        levenshtein += Math.max(insertions, deletions);
        return levenshtein;
    }

    /**
     * 将 diff 压缩为描述将 text1 转换为 text2 所需操作的编码字符串 例如：=3\t-2\t+ing 表示保留 3 个字符,删除 2 个字符,插入
     * "ing" 操作用制表符分隔,插入文本使用 %xx 编码转义
     * @param diffs Diff 对象链表
     * @return 差异文本
     */
    public String diffToDelta(LinkedList<Diff> diffs) {
        StringBuilder text = new StringBuilder();
        for (Diff aDiff : diffs) {
            switch (aDiff.operation) {
                case INSERT:
                    text.append("+")
                            .append(URLEncoder.encode(aDiff.text, StandardCharsets.UTF_8).replace('+', ' '))
                            .append("\t");
                    break;
                case DELETE:
                    text.append("-").append(aDiff.text.length()).append("\t");
                    break;
                case EQUAL:
                    text.append("=").append(aDiff.text.length()).append("\t");
                    break;
                default:
                    break;
            }
        }
        String delta = text.toString();
        if (!delta.isEmpty()) {
            // 去除末尾制表符
            delta = delta.substring(0, delta.length() - 1);
            delta = unescapeForEncodeUriCompatability(delta);
        }
        return delta;
    }

    /**
     * 给定原始 text1 和描述将 text1 转换为 text2 所需操作的编码字符串,计算完整 diff
     * @param text1 差异的源字符串
     * @param delta delta 文本
     * @return Diff 对象数组,无效时返回 null
     * @throws IllegalArgumentException 如果输入无效
     */
    public LinkedList<Diff> diffFromDelta(String text1, String delta) throws IllegalArgumentException {
        LinkedList<Diff> diffs = new LinkedList<>();
        int pointer = 0; // text1 中的游标位置
        String[] tokens = delta.split("\t");
        for (String token : tokens) {
            if (token.isEmpty()) {
                // 允许空白标记(来自末尾的 \t)
                continue;
            }
            // 每个标记以单字符参数开头,指定此标记的操作(删除、插入、相等)
            String param = token.substring(1);
            switch (token.charAt(0)) {
                case '+':
                    // decode 会将所有 "+" 变为 " ",需先还原
                    param = param.replace("+", "%2B");
                    try {
                        param = URLDecoder.decode(param, StandardCharsets.UTF_8);
                    } catch (IllegalArgumentException e) {
                        // 非法的 URI 转义序列
                        throw new IllegalArgumentException("非法的转义字符: " + param, e);
                    }
                    diffs.add(new Diff(Operation.INSERT, param));
                    break;
                case '-':
                    // fall through
                case '=':
                    int n;
                    try {
                        n = Integer.parseInt(param);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("无效的数字: " + param, e);
                    }
                    if (n < 0) {
                        throw new IllegalArgumentException("负数: " + param);
                    }
                    String text;
                    try {
                        text = text1.substring(pointer, pointer += n);
                    } catch (StringIndexOutOfBoundsException e) {
                        throw new IllegalArgumentException(
                                "Delta 长度 (" + pointer + ") 超过源文本长度 (" + text1.length() + ")", e);
                    }
                    if (token.charAt(0) == '=') {
                        diffs.add(new Diff(Operation.EQUAL, text));
                    } else {
                        diffs.add(new Diff(Operation.DELETE, text));
                    }
                    break;
                default:
                    // 其他字符均为错误
                    throw new IllegalArgumentException("无效的差异操作: " + token.charAt(0));
            }
        }
        if (pointer != text1.length()) {
            throw new IllegalArgumentException("Delta 长度 (" + pointer + ") 小于源文本长度 (" + text1.length() + ")");
        }
        return diffs;
    }

    // 匹配函数

    /**
     * 在 text 中定位 pattern 的最佳匹配位置(靠近 loc)未找到返回 -1
     * @param text 待搜索文本
     * @param pattern 待匹配模式
     * @param loc 搜索位置附近
     * @return 最佳匹配索引,未找到返回 -1
     */
    public int matchMain(String text, String pattern, int loc) {
        // 检查空输入
        if (text == null || pattern == null) {
            throw new IllegalArgumentException("输入参数为 null (matchMain)");
        }

        loc = Math.clamp(loc, 0, text.length());
        if (text.equals(pattern)) {
            // 快捷路径：完全相同
            return 0;
        } else if (text.isEmpty()) {
            // 无匹配内容
            return -1;
        } else if (loc + pattern.length() <= text.length() && text.startsWith(pattern, loc)) {
            // 在最佳位置精确匹配
            return loc;
        } else {
            // 执行模糊匹配
            return matchBitap(text, pattern, loc);
        }
    }

    /**
     * 使用 Bitap 算法在 text 中定位 pattern 的最佳匹配位置未找到匹配返回 -1
     * @param text 待搜索文本
     * @param pattern 待匹配模式
     * @param loc 搜索位置附近
     * @return 最佳匹配索引,未找到返回 -1
     */
    public int matchBitap(String text, String pattern, int loc) {
        assert (matchMaxBits == 0 || pattern.length() <= matchMaxBits) : "模式过长,不适用于此应用程序";

        // 初始化字母表
        Map<Character, Integer> s = matchAlphabet(pattern);

        // 超过此分数即放弃
        double scoreThreshold = matchThreshold;
        // 附近是否存在精确匹配？(加速)
        int bestLoc = text.indexOf(pattern, loc);
        if (bestLoc != -1) {
            scoreThreshold = Math.min(matchBitapScore(0, bestLoc, loc, pattern), scoreThreshold);
            // 反方向呢？(加速)
            bestLoc = text.lastIndexOf(pattern, loc + pattern.length());
            if (bestLoc != -1) {
                scoreThreshold = Math.min(matchBitapScore(0, bestLoc, loc, pattern), scoreThreshold);
            }
        }

        // 初始化位数组
        int matchMask = 1 << (pattern.length() - 1);
        bestLoc = -1;

        int binMin, binMid;
        int binMax = pattern.length() + text.length();
        // 空初始化,仅用于满足 Java 编译器
        int[] lastRd = new int[0];
        for (int d = 0; d < pattern.length(); d++) {
            // 扫描最佳匹配；每次迭代允许增加一个误差
            // 使用二分查找确定在当前误差级别下可以偏离 loc 多远
            binMin = 0;
            binMid = binMax;
            while (binMin < binMid) {
                if (matchBitapScore(d, loc + binMid, loc, pattern) <= scoreThreshold) {
                    binMin = binMid;
                } else {
                    binMax = binMid;
                }
                binMid = (binMax - binMin) / 2 + binMin;
            }
            // 将本轮迭代结果作为下一轮的上限
            binMax = binMid;
            int start = Math.max(1, loc - binMid + 1);
            int finish = Math.min(loc + binMid, text.length()) + pattern.length();

            int[] rd = new int[finish + 2];
            rd[finish + 1] = (1 << d) - 1;
            for (int j = finish; j >= start; j--) {
                int charMatch;
                if (text.length() <= j - 1 || !s.containsKey(text.charAt(j - 1))) {
                    // 超出范围
                    charMatch = 0;
                } else {
                    charMatch = s.get(text.charAt(j - 1));
                }
                if (d == 0) {
                    // 第一轮：精确匹配
                    rd[j] = ((rd[j + 1] << 1) | 1) & charMatch;
                } else {
                    // 后续轮次：模糊匹配
                    rd[j] = (((rd[j + 1] << 1) | 1) & charMatch) | (((lastRd[j + 1] | lastRd[j]) << 1) | 1)
                            | lastRd[j + 1];
                }
                if ((rd[j] & matchMask) != 0) {
                    double score = matchBitapScore(d, j - 1, loc, pattern);
                    // 此匹配几乎肯定会优于任何现有匹配,但仍需检查
                    if (score <= scoreThreshold) {
                        scoreThreshold = score;
                        bestLoc = j - 1;
                        if (bestLoc > loc) {
                            // 越过 loc 时,不超过当前距离 loc 的距离
                            start = Math.max(1, 2 * loc - bestLoc);
                        } else {
                            // 已越过 loc,后续只会更差
                            break;
                        }
                    }
                }
            }
            if (matchBitapScore(d + 1, loc, loc, pattern) > scoreThreshold) {
                // 在更高误差级别无望找到(更好的)匹配
                break;
            }
            lastRd = rd;
        }
        return bestLoc;
    }

    /**
     * 计算并返回匹配分数(e 个错误,x 位置)
     * @param e 匹配中的错误数
     * @param x 匹配位置
     * @param loc 期望的匹配位置
     * @param pattern 待匹配的模式
     * @return 匹配总分(0.0 = 好,1.0 = 差)
     */
    private double matchBitapScore(int e, int x, int loc, String pattern) {
        float accuracy = (float) e / pattern.length();
        int proximity = Math.abs(loc - x);
        if (matchDistance == 0) {
            // 避免除零错误
            return proximity == 0 ? accuracy : 1.0;
        }
        return accuracy + (proximity / (float) matchDistance);
    }

    /**
     * 初始化 Bitap 算法的字母表
     * @param pattern 待编码文本
     * @return 字符位置哈希
     */
    public Map<Character, Integer> matchAlphabet(String pattern) {
        Map<Character, Integer> s = new HashMap<>();
        char[] charPattern = pattern.toCharArray();
        for (char c : charPattern) {
            s.put(c, 0);
        }
        int i = 0;
        for (char c : charPattern) {
            s.put(c, s.get(c) | (1 << (pattern.length() - i - 1)));
            i++;
        }
        return s;
    }

    // 补丁函数

    /**
     * 增加上下文直到唯一,但不超过 matchMaxBits 限制
     * @param patch 待扩展的补丁
     * @param text 源文本
     */
    public void patchAddContext(Patch patch, String text) {
        if (text.isEmpty()) {
            return;
        }
        String pattern = text.substring(patch.start2, patch.start2 + patch.length1);
        int padding = 0;

        // 查找 pattern 在 text 中的首次和末次匹配,若找到两个不同匹配则增加 pattern 长度
        while (text.indexOf(pattern) != text.lastIndexOf(pattern)
                && pattern.length() < matchMaxBits - patchMargin - patchMargin) {
            padding += patchMargin;
            pattern = text.substring(Math.max(0, patch.start2 - padding),
                    Math.min(text.length(), patch.start2 + patch.length1 + padding));
        }
        // 再增加一块以获得更好的匹配
        padding += patchMargin;

        // 添加前缀
        String prefix = text.substring(Math.max(0, patch.start2 - padding), patch.start2);
        if (!prefix.isEmpty()) {
            patch.diffs.addFirst(new Diff(Operation.EQUAL, prefix));
        }
        // 添加后缀
        String suffix = text.substring(patch.start2 + patch.length1,
                Math.min(text.length(), patch.start2 + patch.length1 + padding));
        if (!suffix.isEmpty()) {
            patch.diffs.addLast(new Diff(Operation.EQUAL, suffix));
        }

        // 回退起始点
        patch.start1 -= prefix.length();
        patch.start2 -= prefix.length();
        // 扩展长度
        patch.length1 += prefix.length() + suffix.length();
        patch.length2 += prefix.length() + suffix.length();
    }

    /**
     * 计算将 text1 转换为 text2 所需的补丁列表(内部计算差异)
     * @param text1 原始文本
     * @param text2 新文本
     * @return Patch 对象链表
     */
    public LinkedList<Patch> patchMake(String text1, String text2) {
        if (text1 == null || text2 == null) {
            throw new IllegalArgumentException("输入参数为 null (patchMake)");
        }
        // 未提供差异,自行计算
        LinkedList<Diff> diffs = diffMain(text1, text2, true);
        if (diffs.size() > 2) {
            diffCleanupSemantic(diffs);
            diffCleanupEfficiency(diffs);
        }
        return patchMake(text1, diffs);
    }

    /**
     * 计算将 text1 转换为 text2 所需的补丁列表(text1 从提供的 diffs 中推导)
     * @param diffs text1 到 text2 的 Diff 对象数组
     * @return Patch 对象链表
     */
    public LinkedList<Patch> patchMake(LinkedList<Diff> diffs) {
        if (diffs == null) {
            throw new IllegalArgumentException("输入参数为 null (patchMake)");
        }
        // 未提供原始字符串,自行计算
        String text1 = diffText1(diffs);
        return patchMake(text1, diffs);
    }

    /**
     * 计算将 text1 转换为 text2 所需的补丁列表(text2 从 diffs 中推导)
     * @param text1 原始文本
     * @param diffs text1 到 text2 的 Diff 对象数组
     * @return Patch 对象链表
     */
    public LinkedList<Patch> patchMake(String text1, LinkedList<Diff> diffs) {
        if (text1 == null || diffs == null) {
            throw new IllegalArgumentException("输入参数为 null (patchMake)");
        }

        LinkedList<Patch> patches = new LinkedList<>();
        if (diffs.isEmpty()) {
            return patches; // 消除 null 情况
        }
        Patch patch = new Patch();
        int charCount1 = 0; // text1 中的字符位置
        int charCount2 = 0; // text2 中的字符位置
        // 从 text1(prePatchText)开始逐条应用 diffs,直到得到 text2(postPatchText)
        // 逐个重建补丁以确定上下文信息
        String prePatchText = text1;
        String postPatchText = text1;
        for (Diff aDiff : diffs) {
            if (patch.diffs.isEmpty() && aDiff.operation != Operation.EQUAL) {
                // 新补丁从此处开始
                patch.start1 = charCount1;
                patch.start2 = charCount2;
            }

            switch (aDiff.operation) {
                case INSERT:
                    patch.diffs.add(aDiff);
                    patch.length2 += aDiff.text.length();
                    postPatchText = postPatchText.substring(0, charCount2) + aDiff.text
                            + postPatchText.substring(charCount2);
                    break;
                case DELETE:
                    patch.length1 += aDiff.text.length();
                    patch.diffs.add(aDiff);
                    postPatchText = postPatchText.substring(0, charCount2)
                            + postPatchText.substring(charCount2 + aDiff.text.length());
                    break;
                case EQUAL:
                    if (aDiff.text.length() <= 2 * patchMargin && !patch.diffs.isEmpty() && aDiff != diffs.getLast()) {
                        // 补丁内部的小相等片段
                        patch.diffs.add(aDiff);
                        patch.length1 += aDiff.text.length();
                        patch.length2 += aDiff.text.length();
                    }

                    if (aDiff.text.length() >= 2 * patchMargin) {
                        // 开始新补丁的时机
                        if (!patch.diffs.isEmpty()) {
                            patchAddContext(patch, prePatchText);
                            patches.add(patch);
                            patch = new Patch();
                            // 与 Unidiff 不同,我们的补丁列表具有滚动上下文
                            // 更新前补丁文本和位置以反映刚完成的补丁应用
                            prePatchText = postPatchText;
                            charCount1 = charCount2;
                        }
                    }
                    break;
                default:
                    break;
            }

            // 更新当前字符计数
            if (aDiff.operation != Operation.INSERT) {
                charCount1 += aDiff.text.length();
            }
            if (aDiff.operation != Operation.DELETE) {
                charCount2 += aDiff.text.length();
            }
        }
        // 收集剩余的非空补丁
        if (!patch.diffs.isEmpty()) {
            patchAddContext(patch, prePatchText);
            patches.add(patch);
        }

        return patches;
    }

    /**
     * 深拷贝补丁数组
     * @param patches Patch 对象数组
     * @return Patch 对象数组
     */
    public LinkedList<Patch> patchDeepCopy(LinkedList<Patch> patches) {
        LinkedList<Patch> patchesCopy = new LinkedList<>();
        for (Patch aPatch : patches) {
            Patch patchCopy = new Patch();
            for (Diff aDiff : aPatch.diffs) {
                Diff diffCopy = new Diff(aDiff.operation, aDiff.text);
                patchCopy.diffs.add(diffCopy);
            }
            patchCopy.start1 = aPatch.start1;
            patchCopy.start2 = aPatch.start2;
            patchCopy.length1 = aPatch.length1;
            patchCopy.length2 = aPatch.length2;
            patchesCopy.add(patchCopy);
        }
        return patchesCopy;
    }

    /**
     * 将一组补丁应用到文本上返回修补后的文本以及标识各补丁是否成功应用的布尔数组
     * @param patches Patch 对象数组
     * @param text 原始文本
     * @return 两元素 Object 数组,包含新文本和布尔值数组
     */
    public Object[] patchApply(LinkedList<Patch> patches, String text) {
        if (patches.isEmpty()) {
            return new Object[]{text, new boolean[0]};
        }

        // 深拷贝补丁,避免修改原始数据
        patches = patchDeepCopy(patches);

        String nullPadding = patchAddPadding(patches);
        text = nullPadding + text + nullPadding;
        patchSplitMax(patches);

        int x = 0;
        // delta 跟踪前一个补丁的期望位置与实际位置之间的偏移量
        // 若期望位置分别在 10 和 20,但第一个补丁在 12 找到,则 delta 为 2,第二个补丁的有效期望位置为 22
        int delta = 0;
        boolean[] results = new boolean[patches.size()];
        for (Patch aPatch : patches) {
            int expectedLoc = aPatch.start2 + delta;
            String text1 = diffText1(aPatch.diffs);
            int startLoc;
            int endLoc = -1;
            if (text1.length() > this.matchMaxBits) {
                // patchSplitMax 仅在超大删除时才会产生超长模式
                startLoc = matchMain(text, text1.substring(0, this.matchMaxBits), expectedLoc);
                if (startLoc != -1) {
                    endLoc = matchMain(text, text1.substring(text1.length() - this.matchMaxBits),
                            expectedLoc + text1.length() - this.matchMaxBits);
                    if (endLoc == -1 || startLoc >= endLoc) {
                        // 无法找到有效的尾部上下文,丢弃此补丁
                        startLoc = -1;
                    }
                }
            } else {
                startLoc = matchMain(text, text1, expectedLoc);
            }
            if (startLoc == -1) {
                // 未找到匹配
                results[x] = false;
                // 将失败补丁的 delta 从后续补丁中扣除
                delta -= aPatch.length2 - aPatch.length1;
            } else {
                // 找到匹配
                results[x] = true;
                delta = startLoc - expectedLoc;
                String text2;
                if (endLoc == -1) {
                    text2 = text.substring(startLoc, Math.min(startLoc + text1.length(), text.length()));
                } else {
                    text2 = text.substring(startLoc, Math.min(endLoc + this.matchMaxBits, text.length()));
                }
                if (text1.equals(text2)) {
                    // 精确匹配,直接插入替换文本
                    text = text.substring(0, startLoc) + diffText2(aPatch.diffs)
                            + text.substring(startLoc + text1.length());
                } else {
                    // 非精确匹配：运行 diff 获取等效索引框架
                    LinkedList<Diff> diffs = diffMain(text1, text2, false);
                    if (text1.length() > this.matchMaxBits
                            && diffLevenshtein(diffs) / (float) text1.length() > this.patchDeleteThreshold) {
                        // 端点匹配但内容质量不可接受
                        results[x] = false;
                    } else {
                        diffCleanupSemanticLossless(diffs);
                        int index1 = 0;
                        for (Diff aDiff : aPatch.diffs) {
                            if (aDiff.operation != Operation.EQUAL) {
                                int index2 = diffXIndex(diffs, index1);
                                if (aDiff.operation == Operation.INSERT) {
                                    // 插入
                                    text = text.substring(0, startLoc + index2) + aDiff.text
                                            + text.substring(startLoc + index2);
                                } else if (aDiff.operation == Operation.DELETE) {
                                    // 删除
                                    text = text.substring(0, startLoc + index2) + text
                                            .substring(startLoc + diffXIndex(diffs, index1 + aDiff.text.length()));
                                }
                            }
                            if (aDiff.operation != Operation.DELETE) {
                                index1 += aDiff.text.length();
                            }
                        }
                    }
                }
            }
            x++;
        }
        // 去除填充
        text = text.substring(nullPadding.length(), text.length() - nullPadding.length());
        return new Object[]{text, results};
    }

    /**
     * 在文本开头和末尾添加填充,使边界能够匹配到内容仅供 patchApply 内部调用
     * @param patches Patch 对象数组
     * @return 添加到两侧的填充字符串
     */
    public String patchAddPadding(LinkedList<Patch> patches) {
        short paddingLength = this.patchMargin;
        StringBuilder sb = new StringBuilder();
        for (short x = 1; x <= paddingLength; x++) {
            sb.append((char) x);
        }
        String nullPadding = sb.toString();

        // 将所有补丁向前推进
        for (Patch aPatch : patches) {
            aPatch.start1 += paddingLength;
            aPatch.start2 += paddingLength;
        }

        // 在首个 diff 开头添加填充
        Patch patch = patches.getFirst();
        LinkedList<Diff> diffs = patch.diffs;
        if (diffs.isEmpty() || diffs.getFirst().operation != Operation.EQUAL) {
            // 添加填充相等项
            diffs.addFirst(new Diff(Operation.EQUAL, nullPadding));
            patch.start1 -= paddingLength; // 应为 0
            patch.start2 -= paddingLength; // 应为 0
            patch.length1 += paddingLength;
            patch.length2 += paddingLength;
        } else if (paddingLength > diffs.getFirst().text.length()) {
            // 扩展首个相等项
            Diff firstDiff = diffs.getFirst();
            int extraLength = paddingLength - firstDiff.text.length();
            firstDiff.text = nullPadding.substring(firstDiff.text.length()) + firstDiff.text;
            patch.start1 -= extraLength;
            patch.start2 -= extraLength;
            patch.length1 += extraLength;
            patch.length2 += extraLength;
        }

        // 在末尾 diff 之后添加填充
        patch = patches.getLast();
        diffs = patch.diffs;
        if (diffs.isEmpty() || diffs.getLast().operation != Operation.EQUAL) {
            // 添加填充相等项
            diffs.addLast(new Diff(Operation.EQUAL, nullPadding));
            patch.length1 += paddingLength;
            patch.length2 += paddingLength;
        } else if (paddingLength > diffs.getLast().text.length()) {
            // 扩展末尾相等项
            Diff lastDiff = diffs.getLast();
            int extraLength = paddingLength - lastDiff.text.length();
            lastDiff.text += nullPadding.substring(0, extraLength);
            patch.length1 += extraLength;
            patch.length2 += extraLength;
        }

        return nullPadding;
    }

    /**
     * 遍历补丁列表,拆分超出匹配算法最大长度限制的补丁仅供 patchApply 内部调用
     * @param patches Patch 对象链表
     */
    public void patchSplitMax(LinkedList<Patch> patches) {
        short patchSize = matchMaxBits;
        String preContext, postContext;
        Patch patch;
        int start1, start2;
        boolean empty;
        Operation diffType;
        String diffText;
        ListIterator<Patch> pointer = patches.listIterator();
        Patch bigPatch = pointer.hasNext() ? pointer.next() : null;
        while (bigPatch != null) {
            if (bigPatch.length1 <= matchMaxBits) {
                bigPatch = pointer.hasNext() ? pointer.next() : null;
                continue;
            }
            // 移除旧的大补丁
            pointer.remove();
            start1 = bigPatch.start1;
            start2 = bigPatch.start2;
            preContext = "";
            while (!bigPatch.diffs.isEmpty()) {
                // 创建若干较小的补丁
                patch = new Patch();
                empty = true;
                patch.start1 = start1 - preContext.length();
                patch.start2 = start2 - preContext.length();
                if (!preContext.isEmpty()) {
                    patch.length1 = patch.length2 = preContext.length();
                    patch.diffs.add(new Diff(Operation.EQUAL, preContext));
                }
                while (!bigPatch.diffs.isEmpty() && patch.length1 < patchSize - patchMargin) {
                    diffType = bigPatch.diffs.getFirst().operation;
                    diffText = bigPatch.diffs.getFirst().text;
                    if (diffType == Operation.INSERT) {
                        // 插入操作无伤大雅
                        patch.length2 += diffText.length();
                        start2 += diffText.length();
                        patch.diffs.addLast(bigPatch.diffs.removeFirst());
                        empty = false;
                    } else if (diffType == Operation.DELETE && patch.diffs.size() == 1
                            && patch.diffs.getFirst().operation == Operation.EQUAL
                            && diffText.length() > 2 * patchSize) {
                        // 大块删除,整块通过
                        patch.length1 += diffText.length();
                        start1 += diffText.length();
                        empty = false;
                        patch.diffs.add(new Diff(diffType, diffText));
                        bigPatch.diffs.removeFirst();
                    } else {
                        // 删除或相等,仅取可容纳的量
                        diffText = diffText.substring(0,
                                Math.min(diffText.length(), patchSize - patch.length1 - patchMargin));
                        patch.length1 += diffText.length();
                        start1 += diffText.length();
                        if (diffType == Operation.EQUAL) {
                            patch.length2 += diffText.length();
                            start2 += diffText.length();
                        } else {
                            empty = false;
                        }
                        patch.diffs.add(new Diff(diffType, diffText));
                        if (diffText.equals(bigPatch.diffs.getFirst().text)) {
                            bigPatch.diffs.removeFirst();
                        } else {
                            bigPatch.diffs.getFirst().text = bigPatch.diffs.getFirst().text
                                    .substring(diffText.length());
                        }
                    }
                }
                // 计算下一补丁的前置上下文
                preContext = diffText2(patch.diffs);
                preContext = preContext.substring(Math.max(0, preContext.length() - patchMargin));
                // 为本补丁追加末尾上下文
                if (diffText1(bigPatch.diffs).length() > patchMargin) {
                    postContext = diffText1(bigPatch.diffs).substring(0, patchMargin);
                } else {
                    postContext = diffText1(bigPatch.diffs);
                }
                if (!postContext.isEmpty()) {
                    patch.length1 += postContext.length();
                    patch.length2 += postContext.length();
                    if (!patch.diffs.isEmpty() && patch.diffs.getLast().operation == Operation.EQUAL) {
                        patch.diffs.getLast().text += postContext;
                    } else {
                        patch.diffs.add(new Diff(Operation.EQUAL, postContext));
                    }
                }
                if (!empty) {
                    pointer.add(patch);
                }
            }
            bigPatch = pointer.hasNext() ? pointer.next() : null;
        }
    }

    /**
     * 将补丁列表转换为文本表示
     * @param patches Patch 对象列表
     * @return 补丁的文本表示
     */
    public String patchToText(List<Patch> patches) {
        StringBuilder text = new StringBuilder();
        for (Patch aPatch : patches) {
            text.append(aPatch);
        }
        return text.toString();
    }

    /**
     * 解析补丁的文本表示并返回 Patch 对象列表
     * @param textLine 补丁的文本表示
     * @return Patch 对象列表
     * @throws IllegalArgumentException 如果输入无效
     */
    public List<Patch> patchFromText(String textLine) throws IllegalArgumentException {
        List<Patch> patches = new LinkedList<>();
        if (textLine.isEmpty()) {
            return patches;
        }
        List<String> textList = Arrays.asList(textLine.split("\n"));
        LinkedList<String> text = new LinkedList<>(textList);
        Patch patch;
        Matcher m;
        char sign;
        String line;
        while (!text.isEmpty()) {
            m = PATCH_HEADER.matcher(text.getFirst());
            if (!m.matches()) {
                throw new IllegalArgumentException("无效的补丁字符串: " + text.getFirst());
            }
            patch = new Patch();
            patches.add(patch);
            patch.start1 = Integer.parseInt(m.group(1));
            if (m.group(2).isEmpty()) {
                patch.start1--;
                patch.length1 = 1;
            } else if ("0".equals(m.group(2))) {
                patch.length1 = 0;
            } else {
                patch.start1--;
                patch.length1 = Integer.parseInt(m.group(2));
            }

            patch.start2 = Integer.parseInt(m.group(3));
            if (m.group(4).isEmpty()) {
                patch.start2--;
                patch.length2 = 1;
            } else if ("0".equals(m.group(4))) {
                patch.length2 = 0;
            } else {
                patch.start2--;
                patch.length2 = Integer.parseInt(m.group(4));
            }
            text.removeFirst();

            while (!text.isEmpty()) {
                try {
                    sign = text.getFirst().charAt(0);
                } catch (IndexOutOfBoundsException e) {
                    // 空行,跳过
                    text.removeFirst();
                    continue;
                }
                line = text.getFirst().substring(1);
                line = line.replace("+", "%2B"); // decode 会将所有 "+" 变为 " "
                try {
                    line = URLDecoder.decode(line, StandardCharsets.UTF_8);
                } catch (IllegalArgumentException e) {
                    // 非法的 URI 转义序列
                    throw new IllegalArgumentException("patchFromText 中存在非法转义: " + line, e);
                }
                if (sign == '-') {
                    // 删除
                    patch.diffs.add(new Diff(Operation.DELETE, line));
                } else if (sign == '+') {
                    // 插入
                    patch.diffs.add(new Diff(Operation.INSERT, line));
                } else if (sign == ' ') {
                    // 相等
                    patch.diffs.add(new Diff(Operation.EQUAL, line));
                } else if (sign == '@') {
                    // 下一补丁开始
                    break;
                } else {
                    // 无效字符
                    throw new IllegalArgumentException("无效的补丁模式 '" + sign + "' in: " + line);
                }
                text.removeFirst();
            }
        }
        return patches;
    }

    /**
     * Diff 操作类型枚举 链表结构示例：{Diff(Operation.DELETE, "Hello"), Diff(Operation.INSERT,
     * "Goodbye"), Diff(Operation.EQUAL, " world.")} 表示删除 "Hello",添加 "Goodbye",保留 "
     * world."
     */
    public enum Operation {

        /** 删除操作 */
        DELETE,
        /** 插入操作 */
        INSERT,
        /** 相等操作 */
        EQUAL

    }

    /**
     * diffLinesToChars() 方法的返回结果
     */
    public static class LinesToCharsResult {

        /** 转换后的文本1 */
        public String chars1;

        /** 转换后的文本2 */
        public String chars2;

        /** 行数组 */
        public List<String> lineArray;

        public LinesToCharsResult(String chars1, String chars2, List<String> lineArray) {
            this.chars1 = chars1;
            this.chars2 = chars2;
            this.lineArray = lineArray;
        }

    }

    /**
     * 单次差异操作
     */
    public static class Diff {

        /**
         * 操作类型：INSERT、DELETE 或 EQUAL
         */
        public Operation operation;

        /**
         * 与此差异操作关联的文本
         */
        public String text;

        /**
         * 使用指定操作和文本构造 Diff 对象
         * @param operation INSERT、DELETE 或 EQUAL 之一
         * @param text 待应用的文本
         */
        public Diff(Operation operation, String text) {
            // 使用指定操作和文本构造 Diff 对象
            this.operation = operation;
            this.text = text;
        }

        /**
         * 返回此 Diff 的人类可读版本
         * @return 文本表示
         */
        @Override
        public String toString() {
            String prettyText = this.text.replace('\n', '\u00b6');
            return "Diff(" + this.operation + ",\"" + prettyText + "\")";
        }

        /**
         * 计算 Diff 的哈希值 DMP 未使用此函数
         * @return 哈希值
         */
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = (operation == null) ? 0 : operation.hashCode();
            result += prime * ((text == null) ? 0 : text.hashCode());
            return result;
        }

        /**
         * 判断此 Diff 是否与另一 Diff 等价
         * @param obj 待比较的 Diff 对象
         * @return 等价返回 true,否则返回 false
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Diff other = (Diff) obj;
            if (operation != other.operation) {
                return false;
            }
            if (text == null) {
                if (other.text != null) {
                    return false;
                }
            } else if (!text.equals(other.text)) {
                return false;
            }
            return true;
        }

    }

    /**
     * 单次补丁操作
     */
    public static class Patch {

        public LinkedList<Diff> diffs;

        public int start1;

        public int start2;

        public int length1;

        public int length2;

        /**
         * 构造方法,初始化为空的差异列表
         */
        public Patch() {
            this.diffs = new LinkedList<>();
        }

        /**
         * 模拟 GNU diff 格式输出 头信息：@@ -382,8 +481,9 @@ 索引从 1 开始(不是 0)
         * @return GNU diff 格式字符串
         */
        @Override
        public String toString() {
            String coords1, coords2;
            if (this.length1 == 0) {
                coords1 = this.start1 + ",0";
            } else if (this.length1 == 1) {
                coords1 = Integer.toString(this.start1 + 1);
            } else {
                coords1 = (this.start1 + 1) + "," + this.length1;
            }
            if (this.length2 == 0) {
                coords2 = this.start2 + ",0";
            } else if (this.length2 == 1) {
                coords2 = Integer.toString(this.start2 + 1);
            } else {
                coords2 = (this.start2 + 1) + "," + this.length2;
            }
            StringBuilder text = new StringBuilder();
            text.append("@@ -").append(coords1).append(" +").append(coords2).append(" @@\n");
            // 使用 %xx 编码转义补丁体内容
            for (Diff aDiff : this.diffs) {
                switch (aDiff.operation) {
                    case INSERT:
                        text.append('+');
                        break;
                    case DELETE:
                        text.append('-');
                        break;
                    case EQUAL:
                        text.append(' ');
                        break;
                    default:
                        break;
                }
                text.append(URLEncoder.encode(aDiff.text, StandardCharsets.UTF_8).replace('+', ' ')).append("\n");
            }
            return unescapeForEncodeUriCompatability(text.toString());
        }

    }

}
