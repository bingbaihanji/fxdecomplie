package com.bingbaihanji;

import java.nio.charset.StandardCharsets;

/**
 *
 * @author bingbaihanji
 * @date 2026-07-13 18:26:45
 * @description //TODO
 */
public class Test {

    @org.junit.jupiter.api.Test
    public void test() {
        byte[] bytes = new byte[]{0x28, 0x29, 0x4C, 0x6A, 0x61, 0x76, 0x61, 0x2F, 0x6C, 0x61, 0x6E, 0x67, 0x2F, 0x4C, 0x6F, 0x6E, 0x67, 0x3B};
        System.out.println(new String(bytes, StandardCharsets.UTF_8));
    }
}
