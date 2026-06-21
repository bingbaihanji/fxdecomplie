package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.model.CommentData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 注释持久化管理工具类
 *
 * <p>存储路径：&lt;appDir&gt;/fxdecomplie/comments/&lt;workspaceHash&gt;/&lt;classInternalName&gt;.json</p>
 *
 * @author bingbaihanji
 * @date 2026-06-21
 */
public final class CommentManager {

    private static final Logger logger = LoggerFactory.getLogger(CommentManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type COMMENT_LIST_TYPE = new TypeToken<List<CommentData>>() {}.getType();

    private static volatile Path commentRootDir;

    private CommentManager() {
        throw new AssertionError("utility class");
    }

    /** 设置注释存储根目录 */
    public static void setRootDir(Path rootDir) {
        commentRootDir = rootDir;
    }

    /** @return 注释存储根目录 */
    public static Path getCommentDir() {
        if (commentRootDir != null) {
            return commentRootDir;
        }
        return Paths.get("fxdecomplie", "comments");
    }

    /**
     * 保存注释
     *
     * @param workspaceHash 工作区 hash
     * @param comment       注释数据
     */
    public static void save(String workspaceHash, CommentData comment) {
        if (comment == null) return;
        try {
            List<CommentData> existing = loadAll(workspaceHash, comment.className());
            // 更新同位置已有注释，否则追加
            boolean replaced = false;
            for (int i = 0; i < existing.size(); i++) {
                CommentData c = existing.get(i);
                if (c.line() == comment.line()
                        && c.memberSignature().equals(comment.memberSignature())) {
                    existing.set(i, comment);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                existing.add(comment);
            }
            writeFile(workspaceHash, comment.className(), existing);
            logger.debug("注释已保存: {}#L{}", comment.className(), comment.line());
        } catch (Exception e) {
            logger.error("保存注释失败", e);
        }
    }

    /**
     * 加载某个类的全部注释
     *
     * @param workspaceHash 工作区 hash
     * @param className     类全限定路径
     * @return 注释列表
     */
    public static List<CommentData> load(String workspaceHash, String className) {
        return loadAll(workspaceHash, className);
    }

    /**
     * 删除注释
     */
    public static boolean delete(String workspaceHash, String className,
                                  String memberSignature, int line, String time) {
        try {
            List<CommentData> existing = loadAll(workspaceHash, className);
            boolean removed = existing.removeIf(c ->
                    c.line() == line
                            && c.memberSignature().equals(memberSignature == null ? "" : memberSignature)
                            && c.time().equals(time));
            if (removed) {
                if (existing.isEmpty()) {
                    Files.deleteIfExists(resolveFile(workspaceHash, className));
                } else {
                    writeFile(workspaceHash, className, existing);
                }
            }
            return removed;
        } catch (Exception e) {
            logger.error("删除注释失败", e);
            return false;
        }
    }

    private static List<CommentData> loadAll(String workspaceHash, String className) {
        Path file = resolveFile(workspaceHash, className);
        if (!Files.isRegularFile(file)) {
            return new ArrayList<>();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            List<CommentData> list = GSON.fromJson(json, COMMENT_LIST_TYPE);
            return list != null ? new ArrayList<>(list) : new ArrayList<>();
        } catch (IOException e) {
            logger.debug("读取注释文件失败: {}", file, e);
            return new ArrayList<>();
        }
    }

    private static void writeFile(String workspaceHash, String className,
                                   List<CommentData> comments) throws IOException {
        Path file = resolveFile(workspaceHash, className);
        Files.createDirectories(file.getParent());
        String json = GSON.toJson(comments);
        Files.writeString(file, json, StandardCharsets.UTF_8);
    }

    private static Path resolveFile(String workspaceHash, String className) {
        // 安全编码：将路径分隔符替换为安全字符
        String safeName = className.replace('/', '_').replace('\\', '_')
                .replace("..", "__");
        return getCommentDir().resolve(workspaceHash).resolve(safeName + ".json");
    }
}
