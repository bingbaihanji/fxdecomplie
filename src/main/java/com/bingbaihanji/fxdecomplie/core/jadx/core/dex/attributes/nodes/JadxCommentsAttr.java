package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.CommentsLevel;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttrType;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.IAttributeNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;

import java.util.*;
import java.util.stream.Collectors;

public class JadxCommentsAttr implements IJadxAttribute {

    private final Map<CommentsLevel, Set<String>> comments = new EnumMap<>(CommentsLevel.class);

    public static void add(IAttributeNode node, CommentsLevel level, String comment) {
        initFor(node).add(level, comment);
    }

    private static JadxCommentsAttr initFor(IAttributeNode node) {
        JadxCommentsAttr currentAttr = node.get(AType.JADX_COMMENTS);
        if (currentAttr != null) {
            return currentAttr;
        }
        JadxCommentsAttr newAttr = new JadxCommentsAttr();
        node.addAttr(newAttr);
        return newAttr;
    }

    public void add(CommentsLevel level, String comment) {
        comments.computeIfAbsent(level, l -> new HashSet<>()).add(comment);
    }

    public List<String> formatAndFilter(CommentsLevel level) {
        if (level == CommentsLevel.NONE || level == CommentsLevel.USER_ONLY) {
            return Collections.emptyList();
        }
        return comments.entrySet().stream()
                .filter(e -> e.getKey().filter(level))
                .flatMap(e -> {
                    String levelName = e.getKey().name();
                    return e.getValue().stream()
                            .map(v -> "JADXB " + levelName + ": " + v);
                })
                .sorted()
                .collect(Collectors.toList());
    }

    public Map<CommentsLevel, Set<String>> getComments() {
        return comments;
    }

    @Override
    public IJadxAttrType<JadxCommentsAttr> getAttrType() {
        return AType.JADX_COMMENTS;
    }

    @Override
    public String toString() {
        return "JadxCommentsAttr{\n "
                + Utils.listToString(comments.entrySet(), "\n ",
                e -> e.getKey() + ": \n -> " + Utils.listToString(e.getValue(), "\n -> "))
                + '}';
    }
}
