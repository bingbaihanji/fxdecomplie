package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.model.CommentData;
import com.bingbaihanji.fxdecomplie.util.AtomicFile;
import com.google.gson.*;
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
     * Gson 实例,注册了 CommentData 的自定义反序列化器,
     * 确保即使 JSON 中字段为 null,反序列化后 CommentData Record 的各字段也不会为 null
     * Gson 的 UnsafeAllocator 可能绕过 Record 紧凑构造器,因此需要显式适配
     */
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(CommentData.class, new CommentDataDeserializer())
            .setPrettyPrinting()
            .create();
    private static final Type COMMENT_LIST_TYPE = new TypeToken<List<CommentData>>() {
    }.getType();
    private static final java.util.concurrent.ConcurrentMap<String, java.util.concurrent.locks.ReentrantLock>
            FILE_LOCKS = new java.util.concurrent.ConcurrentHashMap<>();

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
        if (comment == null) {
            return;
        }
        String lockKey = workspaceHash + ":" + comment.className();
        var lock = FILE_LOCKS.computeIfAbsent(lockKey,
                k -> new java.util.concurrent.locks.ReentrantLock());
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
        var lock = FILE_LOCKS.computeIfAbsent(lockKey,
                k -> new java.util.concurrent.locks.ReentrantLock());
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
        var lock = FILE_LOCKS.computeIfAbsent(lockKey,
                k -> new java.util.concurrent.locks.ReentrantLock());
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
            List<CommentData> list = GSON.fromJson(json, COMMENT_LIST_TYPE);
            return list != null ? new ArrayList<>(list) : new ArrayList<>();
        } catch (IOException | com.google.gson.JsonSyntaxException e) {
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
                os.write(GSON.toJson(comments).getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static Path resolveFile(String workspaceHash, String className) {
        String safeWorkspace = safePathSegment(workspaceHash);
        String safeName = className.replace('/', '_').replace('\\', '_')
                .replace("..", "__");
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
     * CommentData 的自定义 Gson 反序列化器
     *
     * <p>Gson 可能通过 {@code UnsafeAllocator} 绕过 Record 的紧凑构造器直接创建实例,
     * 导致反序列化后 {@code text()}、{@code memberSignature()} 等字段为 null,
     * 调用方({@code CommentExportDecorator.insert()} 等)会抛出 NPE</p>
     *
     * <p>此反序列化器从 JSON 中逐字段提取值,对缺失或 null 的字段填入默认值,
     * 再通过规范的紧凑构造器创建 CommentData,确保各字段非 null</p>
     */
    private static final class CommentDataDeserializer implements JsonDeserializer<CommentData> {
        private static String getString(JsonObject obj, String name, String defaultValue) {
            JsonElement el = obj.get(name);
            if (el == null || el.isJsonNull()) {
                return defaultValue;
            }
            return el.getAsString();
        }

        private static int getInt(JsonObject obj, String name, int defaultValue) {
            JsonElement el = obj.get(name);
            if (el == null || el.isJsonNull()) {
                return defaultValue;
            }
            try {
                return el.getAsInt();
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        private static CommentData.CommentStyle getEnum(JsonObject obj, String name,
                                                        CommentData.CommentStyle defaultValue) {
            JsonElement el = obj.get(name);
            if (el == null || el.isJsonNull()) {
                return defaultValue;
            }
            try {
                return CommentData.CommentStyle.valueOf(el.getAsString());
            } catch (IllegalArgumentException e) {
                return defaultValue;
            }
        }

        @Override
        public CommentData deserialize(JsonElement json, Type typeOfT,
                                       JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
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
