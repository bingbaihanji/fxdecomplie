package com.bingbaihanji.fxdecomplie.ui.hex.model;

import com.bingbaihanji.fxdecomplie.ui.hex.HexDataProvider;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 搜索模型,用于在二进制数据中执行字节序列搜索(支持十六进制和文本模式) 
 * <p>
 * 该类维护搜索状态,包括上次查询字符串、查询类型(十六进制/文本)、匹配地址列表
 * 以及当前匹配索引,并支持遍历匹配结果(下一个/上一个) 
 * 搜索算法采用滑动窗口,分块读取数据以提高大文件搜索效率 
 * </p>
 *
 * @author BingBaiHanJi
 * @see HexDataProvider
 */
public class SearchModel {

    /** 所有匹配的起始地址列表 */
    private final List<Long> matchAddresses = new ArrayList<>();

    /** 上次执行的查询字符串 */
    private String lastQuery = "";

    /** 上次查询是否为十六进制模式(否则为文本模式) */
    private boolean isHexQuery = true;

    /** 当前匹配索引(用于 next/prev 导航),-1 表示未开始 */
    private int currentMatchIndex = -1;

    /**
     * 将十六进制查询字符串解析为字节数组 
     * <p>
     * 会去除所有非十六进制字符(0-9a-fA-F),若处理后长度为奇数则返回空数组 
     * </p>
     *
     * @param query 十六进制查询字符串(可包含空格、下划线等分隔符)
     * @return 解析后的字节数组,若无效则返回空数组
     */
    private static byte[] parseHexQuery(String query) {
        String stripped = query.replaceAll("[^0-9a-fA-F]", "");
        if (stripped.length() % 2 != 0) {
            return new byte[0];
        }
        byte[] result = new byte[stripped.length() / 2];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(stripped.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    /**
     * 在数据提供者中执行搜索 
     * <p>
     * 根据查询类型(十六进制或文本)将查询转换为字节序列,然后在整个数据中
     * 查找所有匹配位置 搜索使用分块缓冲区,通过重叠滑动避免跨块漏检 
     * 结果存储在内部匹配地址列表中,并将当前匹配索引重置为 0(若有匹配) 
     * </p>
     *
     * @param provider 数据提供者
     * @param query    查询字符串(十六进制或普通文本)
     * @param isHex    若为 {@code true} 则将查询视为十六进制字符串,否则视为 UTF-8 文本
     * @return 匹配的个数
     */
    public int search(HexDataProvider provider, String query, boolean isHex) {
        this.lastQuery = query;
        this.isHexQuery = isHex;
        matchAddresses.clear();
        currentMatchIndex = -1;
        if (query.isEmpty()) {
            return 0;
        }
        byte[] needle = isHex ? parseHexQuery(query) : query.getBytes(StandardCharsets.UTF_8);
        if (needle.length == 0) {
            return 0;
        }
        long size = provider.getSize();
        if (size < needle.length) {
            return 0;
        }
        byte[] buf = new byte[65536 + needle.length];
        long bufAddr = 0;
        while (bufAddr < size) {
            int toRead = (int) Math.min(65536 + needle.length, size - bufAddr);
            int n = provider.read(bufAddr, buf, 0, toRead);
            if (n < needle.length) {
                break;
            }
            int searchLen = n - needle.length + 1;
            for (int i = 0; i < searchLen; i++) {
                boolean match = true;
                for (int j = 0; j < needle.length; j++) {
                    if (buf[i + j] != needle[j]) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    matchAddresses.add(bufAddr + i);
                }
            }
            // 滑动窗口：保留末尾 overlap 字节用于下一轮跨块匹配
            if (n > needle.length) {
                bufAddr += n - needle.length + 1;
                if (bufAddr < size) {
                    int overlap = Math.min(needle.length, n);
                    System.arraycopy(buf, n - overlap, buf, 0, overlap);
                }
            } else {
                break;
            }
        }
        if (!matchAddresses.isEmpty()) {
            currentMatchIndex = 0;
        }
        return matchAddresses.size();
    }

    /**
     * 移动到下一个匹配位置(循环) 
     *
     * @return 下一个匹配的起始地址,若没有匹配则返回 -1
     */
    public long nextMatch() {
        if (matchAddresses.isEmpty()) {
            return -1;
        }
        currentMatchIndex = (currentMatchIndex + 1) % matchAddresses.size();
        return matchAddresses.get(currentMatchIndex);
    }

    /**
     * 移动到上一个匹配位置(循环) 
     *
     * @return 上一个匹配的起始地址,若没有匹配则返回 -1
     */
    public long prevMatch() {
        if (matchAddresses.isEmpty()) {
            return -1;
        }
        currentMatchIndex = (currentMatchIndex - 1 + matchAddresses.size()) % matchAddresses.size();
        return matchAddresses.get(currentMatchIndex);
    }

    // ---------- 属性访问 ----------

    /**
     * 获取所有匹配起始地址的列表(不可修改视图？实际返回内部列表,但建议只读) 
     *
     * @return 匹配地址列表
     */
    public List<Long> getMatchAddresses() {
        return matchAddresses;
    }

    /**
     * 获取当前匹配索引 
     *
     * @return 当前索引(从 0 开始),若没有匹配或未搜索则返回 -1
     */
    public int getCurrentMatchIndex() {
        return currentMatchIndex;
    }

    /**
     * 获取匹配总数 
     *
     * @return 匹配个数
     */
    public int getMatchCount() {
        return matchAddresses.size();
    }

    /**
     * 获取上次执行的查询字符串 
     *
     * @return 查询字符串
     */
    public String getLastQuery() {
        return lastQuery;
    }

    /**
     * 判断上次查询是否为十六进制模式 
     *
     * @return {@code true} 表示十六进制查询
     */
    public boolean isHexQuery() {
        return isHexQuery;
    }

    /**
     * 清空所有搜索结果,重置状态 
     */
    public void clear() {
        matchAddresses.clear();
        currentMatchIndex = -1;
        lastQuery = "";
    }
}