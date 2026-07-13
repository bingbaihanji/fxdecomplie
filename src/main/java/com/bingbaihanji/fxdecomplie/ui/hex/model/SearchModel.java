package com.bingbaihanji.fxdecomplie.ui.hex.model;

import com.bingbaihanji.fxdecomplie.ui.hex.HexDataProvider;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SearchModel {
    private final List<Long> matchAddresses = new ArrayList<>();
    private String lastQuery = "";
    private boolean isHexQuery = true;
    private int currentMatchIndex = -1;

    private static byte[] parseHexQuery(String query) {
        String stripped = query.replaceAll("[^0-9a-fA-F]", "");
        if (stripped.length() % 2 != 0) return new byte[0];
        byte[] result = new byte[stripped.length() / 2];
        for (int i = 0; i < result.length; i++)
            result[i] = (byte) Integer.parseInt(stripped.substring(i * 2, i * 2 + 2), 16);
        return result;
    }

    public int search(HexDataProvider provider, String query, boolean isHex) {
        this.lastQuery = query; this.isHexQuery = isHex;
        matchAddresses.clear(); currentMatchIndex = -1;
        if (query.isEmpty()) return 0;
        byte[] needle = isHex ? parseHexQuery(query) : query.getBytes(StandardCharsets.UTF_8);
        if (needle.length == 0) return 0;
        long size = provider.getSize();
        if (size < needle.length) return 0;
        byte[] buf = new byte[65536 + needle.length];
        long bufAddr = 0;
        while (bufAddr < size) {
            int toRead = (int) Math.min(65536 + needle.length, size - bufAddr);
            int n = provider.read(bufAddr, buf, 0, toRead);
            if (n < needle.length) break;
            int searchLen = n - needle.length + 1;
            for (int i = 0; i < searchLen; i++) {
                boolean match = true;
                for (int j = 0; j < needle.length; j++) { if (buf[i + j] != needle[j]) { match = false; break; } }
                if (match) matchAddresses.add(bufAddr + i);
            }
            if (n > needle.length) { bufAddr += n - needle.length + 1; if (bufAddr < size) { int overlap = Math.min(needle.length, n); System.arraycopy(buf, n - overlap, buf, 0, overlap); } }
            else break;
        }
        if (!matchAddresses.isEmpty()) currentMatchIndex = 0;
        return matchAddresses.size();
    }

    public long nextMatch() { if (matchAddresses.isEmpty()) return -1; currentMatchIndex = (currentMatchIndex + 1) % matchAddresses.size(); return matchAddresses.get(currentMatchIndex); }
    public long prevMatch() { if (matchAddresses.isEmpty()) return -1; currentMatchIndex = (currentMatchIndex - 1 + matchAddresses.size()) % matchAddresses.size(); return matchAddresses.get(currentMatchIndex); }
    public List<Long> getMatchAddresses() { return matchAddresses; }
    public int getCurrentMatchIndex() { return currentMatchIndex; }
    public int getMatchCount() { return matchAddresses.size(); }
    public String getLastQuery() { return lastQuery; }
    public boolean isHexQuery() { return isHexQuery; }
    public void clear() { matchAddresses.clear(); currentMatchIndex = -1; lastQuery = ""; }
}
