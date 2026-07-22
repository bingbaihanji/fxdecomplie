package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.model.CommentData;
import com.bingbaihanji.fxdecomplie.util.io.AtomicFile;
import com.bingbaihanji.utils.json.JSONUtils;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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

    private static final Logger log = LoggerFactory.getLogger(CommentManager.class);
    /**
     * Jackson ObjectMapper,注册了 CommentData 的自定义反序列化器,
     * 确保即使 JSON 中字段为 null,反序列化后 CommentData Record 的各字段也不会为 null
     */
    private static final ObjectMapper MAPPER = JSONUtils.getPrettyMapper().copy()
            .registerModule(new SimpleModule()
                    .addDeserializer(CommentData.class, new CommentDataDeserializer()));
    private static final TypeReference<List<CommentData>> COMMENT_LIST_TYPE = new TypeReference<>() {
    };
    /**
     * 分段锁数组：按 lockKey 的哈希映射到固定数量的锁，保证同一文件始终映射到同一把锁
     * <p>
     * 相比"每 key 一把锁的 ConcurrentHashMap"，分段锁内存有界 (永不增长 无泄漏)，
     * 且不存在"移除锁条目 → 其他线程重建新锁"导致两个线程持不同锁进入临界区的竞态
     * 不同文件偶发共享同一分段锁只会带来可忽略的额外串行化，不影响正确性
     */
    private static final int LOCK_STRIPES = 64;
    private static final java.util.concurrent.locks.ReentrantLock[] FILE_LOCKS =
            new java.util.concurrent.locks.ReentrantLock[LOCK_STRIPES];
    private static volatile Path commentRootDir;

    static {
        for (int i = 0; i < LOCK_STRIPES; i++) {
            FILE_LOCKS[i] = new java.util.concurrent.locks.ReentrantLock();
        }
    }

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

    /** 按 lockKey 哈希映射到固定分段锁，同一 key 始终返回同一把锁 */
    private static java.util.concurrent.locks.ReentrantLock lockFor(String lockKey) {
        int idx = (lockKey.hashCode() & 0x7fffffff) % LOCK_STRIPES;
        return FILE_LOCKS[idx];
    }

    /**
     * 保存注释
     *
     * @param workspaceHash 工作区 hash
     * @param comment       注释数据
     */
    public static void save(String workspaceHash, CommentData comment) {
        if (comment == null) {
            return;
        }
        String lockKey = workspaceHash + ":" + comment.className();
        var lock = lockFor(lockKey);
        lock.lock();
        try {
            List<CommentData> existing = loadAll(workspaceHash, comment.className());
            // 更新同位置已有注释,否则追加
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
            log.debug("注释已保存: {}#L{}", comment.className(), comment.line());
        } catch (Exception e) {
            log.error("保存注释失败", e);
        } finally {
            lock.unlock();
            // 不移除锁条目,防止 unlock→remove 窗口期其他线程创建新锁导致并发写
        }
    }

    /**
     * 加载某个类的全部注释(获取读锁,防止读到并发写入的半截 JSON)
     *
     * @param workspaceHash 工作区 hash
     * @param className     类全限定路径
     * @return 注释列表
     */
    public static List<CommentData> load(String workspaceHash, String className) {
        String lockKey = workspaceHash + ":" + className;
        var lock = lockFor(lockKey);
        lock.lock();
        try {
            return loadAll(workspaceHash, className);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 删除注释
     */
    public static boolean delete(String workspaceHash, String className,
                                 String memberSignature, int line, String time) {
        String lockKey = workspaceHash + ":" + className;
        var lock = lockFor(lockKey);
        lock.lock();
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
            log.error("删除注释失败", e);
            return false;
        } finally {
            lock.unlock();
            // 不移除锁条目,防止 unlock→remove 窗口期其他线程创建新锁导致并发写
        }
    }

    private static List<CommentData> loadAll(String workspaceHash, String className) {
        Path file = resolveFile(workspaceHash, className);
        if (!Files.isRegularFile(file)) {
            return new ArrayList<>();
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            List<CommentData> list = MAPPER.readValue(json, COMMENT_LIST_TYPE);
            return list != null ? new ArrayList<>(list) : new ArrayList<>();
        } catch (IOException | RuntimeException e) {
            log.warn("读取注释文件失败,将视为空列表: {}", file, e);
            return new ArrayList<>();
        }
    }

    private static void writeFile(String workspaceHash, String className,
                                  List<CommentData> comments) throws IOException {
        Path file = resolveFile(workspaceHash, className);
        Files.createDirectories(file.getParent());
        AtomicFile af = new AtomicFile(file.toFile());
        af.write(os -> {
            try {
                os.write(MAPPER.writeValueAsString(comments).getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        });
    }

    private static Path resolveFile(String workspaceHash, String className) {
        String safeWorkspace = safePathSegment(workspaceHash);
        String safeName = safePathSegment(className);
        return getCommentDir().resolve(safeWorkspace).resolve(safeName + ".json");
    }

    private static String safePathSegment(String raw) {
        if (raw == null || raw.isBlank()) {
            return "_";
        }
        return raw.replace('\\', '_')
                .replace('/', '_')
                .replace(':', '_')
                .replace('*', '_')
                .replace('?', '_')
                .replace('"', '_')
                .replace('<', '_')
                .replace('>', '_')
                .replace('|', '_')
                .replace("..", "__");
    }

    /**
     * CommentData 的自定义 Jackson 反序列化器
     *
     * <p>从 JSON 中逐字段提取值,对缺失或 null 的字段填入默认值,
     * 再通过规范的紧凑构造器创建 CommentData,确保各字段非 null</p>
     */
    private static final class CommentDataDeserializer extends JsonDeserializer<CommentData> {
        private static String getString(JsonNode node, String name, String defaultValue) {
            JsonNode el = node.get(name);
            if (el == null || el.isNull()) {
                return defaultValue;
            }
            return el.asText();
        }

        private static int getInt(JsonNode node, String name, int defaultValue) {
            JsonNode el = node.get(name);
            if (el == null || el.isNull()) {
                return defaultValue;
            }
            try {
                return el.asInt();
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        private static CommentData.CommentStyle getEnum(JsonNode node, String name,
                                                        CommentData.CommentStyle defaultValue) {
            JsonNode el = node.get(name);
            if (el == null || el.isNull()) {
                return defaultValue;
            }
            try {
                return CommentData.CommentStyle.valueOf(el.asText());
            } catch (IllegalArgumentException e) {
                return defaultValue;
            }
        }

        @Override
        public CommentData deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode obj = ctxt.readTree(p);
            String className = getString(obj, "className", "");
            String memberSignature = getString(obj, "memberSignature", "");
            int line = getInt(obj, "line", 0);
            String sourceHash = getString(obj, "sourceHash", "");
            String optionsHash = getString(obj, "optionsHash", "");
            CommentData.CommentStyle style = getEnum(obj, "style", CommentData.CommentStyle.LINE);
            String text = getString(obj, "text", "");
            String author = getString(obj, "author", "");
            String time = getString(obj, "time", "");
            return new CommentData(className, memberSignature, line, sourceHash,
                    optionsHash, style, text, author, time);
        }
    }
}
