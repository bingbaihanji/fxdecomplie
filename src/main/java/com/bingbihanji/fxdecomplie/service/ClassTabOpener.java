package com.bingbihanji.fxdecomplie.service;

import com.bingbihanji.fxdecomplie.config.AppConfig;
import com.bingbihanji.fxdecomplie.decompiler.BytecodeCache;
import com.bingbihanji.fxdecomplie.decompiler.DecompilerContext;
import com.bingbihanji.fxdecomplie.decompiler.DecompilerFactory;
import com.bingbihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbihanji.fxdecomplie.model.CodeMetadata;
import com.bingbihanji.fxdecomplie.model.FileTreeNode;
import com.bingbihanji.fxdecomplie.model.OpenFile;
import com.bingbihanji.fxdecomplie.model.Workspace;
import com.bingbihanji.fxdecomplie.ui.WorkspaceTabManager;
import com.bingbihanji.fxdecomplie.ui.code.CodeEditorTab;
import com.bingbihanji.fxdecomplie.ui.code.LineNumberGutter;
import com.bingbihanji.fxdecomplie.ui.code.StatusBar;
import com.bingbihanji.fxdecomplie.ui.outline.OutlineParser;
import com.bingbihanji.fxdecomplie.ui.theme.VsCodeThemeLoader;
import com.bingbihanji.fxdecomplie.utils.I18nUtil;
import javafx.application.Platform;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TreeItem;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * 负责 class 文件的反编译和代码标签页的创建/刷新。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class ClassTabOpener {

    /** 应用配置 */
    private final AppConfig config;
    /** 编辑器主题数据 */
    private final VsCodeThemeLoader.ThemeData editorTheme;
    /** 状态栏引用 */
    private final StatusBar statusBar;
    /** L2 反编译源码缓存，避免重复反编译已打开的类 */
    private final DecompileCache decompileCache = new DecompileCache();
    /** 当前运行的反编译任务，用于在切换时取消 */
    private volatile Future<?> currentDecompileTask;

    public ClassTabOpener(AppConfig config, VsCodeThemeLoader.ThemeData editorTheme, StatusBar statusBar) {
        this.config = config;
        this.editorTheme = editorTheme;
        this.statusBar = statusBar;
    }

    /** 提取简短类名（去 .class 后缀） */
    private static String className(FileTreeNode node) {
        String name = node.getName();
        return name.endsWith(".class") ? name.substring(0, name.length() - 6) : name;
    }

    /** 计算工作区缓存键：使用完整路径 + mtime + size，消除路径碰撞和同路径替换误命中 */
    private static String computeWorkspaceKey(Workspace workspace) {
        File source = workspace.getSourceFile();
        Path path = source.toPath();
        long mtime = source.lastModified();
        long size = source.isFile() ? source.length() : directoryFingerprint(path);
        return (source.getAbsolutePath() + "_" + mtime + "_" + size)
                .replace(':', '_').replace('\\', '_').replace('/', '_');
    }

    private static long directoryFingerprint(Path dir) {
        try (var stream = Files.walk(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p) ^ Files.getLastModifiedTime(p).toMillis();
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .reduce(0L, (a, b) -> a * 31 + b);
        } catch (IOException e) {
            return 0L;
        }
    }

    /**
     * 判断异常是否由线程中断引起（用户导航取消任务，不应弹窗）。
     * 递归检查异常链中每一层，最终兜底检查当前线程中断标志。
     */
    private static boolean isInterruptRelated(Exception ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof InterruptedException) return true;
            cause = cause.getCause();
        }
        return Thread.currentThread().isInterrupted();
    }

    /**
     * 打开 class 文件的代码标签页。如果已打开则切换到已有标签页。
     *
     * @param node               文件树节点
     * @param workspace          工作区
     * @param codeTabPane        代码标签页面板
     * @param engine             反编译引擎
     * @param lineNumbersEnabled 是否显示行号
     */
    public void openClassTab(FileTreeNode node, Workspace workspace, TabPane codeTabPane,
                             DecompilerTypeEnum engine, boolean lineNumbersEnabled) {
        Tab existingTab = findOrRemoveOpenClassTab(codeTabPane, node, engine);
        if (existingTab != null) {
            codeTabPane.getSelectionModel().select(existingTab);
            return;
        }

        statusBar.setFilePath(I18nUtil.getString("status.decompiling", node.getFullPath()));

        BackgroundTasks.cancel(currentDecompileTask);
        currentDecompileTask = BackgroundTasks.run("Decompile-" + node.getName(), () -> {
            try {
                byte[] bytes = readClassBytes(node, workspace);
                if (bytes == null) {
                    Platform.runLater(() -> showAlert(I18nUtil.getString("dialog.error.title"),
                            I18nUtil.getString("dialog.read.unable", node.getFullPath())));
                    return;
                }

                String internalName = node.getFullPath().replace(".class", "");
                DecompileResult result = decompileWithCache(internalName, engine, bytes, node, workspace);
                String sourceCode = result.sourceCode();
                CodeMetadata metadata = result.metadata();
                OpenFile openFile = new OpenFile(className(node), node.getFullPath(), sourceCode, engine);

                Platform.runLater(() -> {
                    // ---- Ctrl+Click navigation callback: resolves class refs in decompiled source ----
                    Consumer<CodeMetadata.Reference> onNavigate = ref -> {
                        if (ref.type() == CodeMetadata.RefType.CLASS_REF) {
                            // ---- Convert dotted class ref (e.g. "com.example.Foo") to file path ----
                            String targetPath = ref.targetClass().replace('.', '/') + ".class";
                            // ---- Search the file tree for the referenced class node ----
                            FileTreeNode targetNode = findNodeByPath(workspace.getTreeRoot(), targetPath);
                            if (targetNode != null) {
                                // ---- Recursively open the target class in a new code tab ----
                                openClassTab(targetNode, workspace, codeTabPane, engine, lineNumbersEnabled);
                            }
                        }
                    };
                    CodeEditorTab codeTab = createCodeEditorTab(openFile, lineNumbersEnabled, bytes,
                            metadata, onNavigate);
                    codeTabPane.getTabs().add(codeTab);
                    codeTabPane.getSelectionModel().select(codeTab);
                    statusBar.setFilePath(
                            WorkspaceTabManager.formatClassPath(node.getFullPath()));
                    statusBar.setEncoding("UTF-8");
                    bindCaretPosition(codeTab);
                });
            } catch (Exception ex) {
                // 用户导航到其他类时任务被取消，中断异常是预期行为，不弹窗
                if (isInterruptRelated(ex)) {
                    Thread.interrupted();
                    return;
                }
                Platform.runLater(() -> showAlert(I18nUtil.getString("dialog.error.title"),
                        I18nUtil.getString("dialog.decompile.failed", ex.getMessage())));
            }
        });
    }

    /**
     * 用新引擎重新反编译当前标签页（同位置替换）。
     *
     * @param workspace          工作区
     * @param codeTabPane        代码标签页面板
     * @param currentTab         当前选中的标签页
     * @param engine             反编译引擎
     * @param lineNumbersEnabled 是否显示行号
     */
    public void refreshCurrentClassTab(Workspace workspace, TabPane codeTabPane, CodeEditorTab currentTab,
                                       DecompilerTypeEnum engine, boolean lineNumbersEnabled) {
        String fullPath = currentTab.getOpenFile().getFullPath();
        FileTreeNode node = findNodeByPath(workspace.getTreeRoot(), fullPath);
        if (node == null) {
            statusBar.setFilePath(I18nUtil.getString("status.locateFailed", fullPath));
            return;
        }

        int tabIndex = codeTabPane.getTabs().indexOf(currentTab);
        statusBar.setFilePath(I18nUtil.getString(
                "status.redecompiling", engine.name(), fullPath));

        BackgroundTasks.cancel(currentDecompileTask);
        currentDecompileTask = BackgroundTasks.run("Redecompile-" + node.getName(), () -> {
            try {
                byte[] bytes = readClassBytes(node, workspace);
                if (bytes == null) {
                    Platform.runLater(() -> showAlert(I18nUtil.getString("dialog.error.title"),
                            I18nUtil.getString("dialog.read.unable", fullPath)));
                    return;
                }

                String internalName = fullPath.replace(".class", "");
                DecompileResult result = decompileWithCache(internalName, engine, bytes, node, workspace);
                String sourceCode = result.sourceCode();
                CodeMetadata metadata = result.metadata();
                OpenFile openFile = new OpenFile(className(node), fullPath, sourceCode, engine);

                Platform.runLater(() -> {
                    Consumer<CodeMetadata.Reference> onNavigate = ref -> {
                        if (ref.type() == CodeMetadata.RefType.CLASS_REF) {
                            String targetPath = ref.targetClass().replace('.', '/') + ".class";
                            FileTreeNode targetNode = findNodeByPath(workspace.getTreeRoot(), targetPath);
                            if (targetNode != null) {
                                openClassTab(targetNode, workspace, codeTabPane, engine, lineNumbersEnabled);
                            }
                        }
                    };
                    CodeEditorTab replacement = createCodeEditorTab(openFile, lineNumbersEnabled, bytes,
                            metadata, onNavigate);
                    codeTabPane.getTabs().set(tabIndex, replacement);
                    codeTabPane.getSelectionModel().select(replacement);
                    bindCaretPosition(replacement);
                    statusBar.setFilePath(
                            WorkspaceTabManager.formatClassPath(fullPath));
                    statusBar.setEncoding("UTF-8");
                });
            } catch (Exception ex) {
                // 用户导航到其他类时任务被取消，中断异常是预期行为，不弹窗
                if (isInterruptRelated(ex)) {
                    Thread.interrupted();
                    return;
                }
                Platform.runLater(() -> showAlert(I18nUtil.getString("dialog.error.title"),
                        I18nUtil.getString("dialog.decompile.failed", ex.getMessage())));
            }
        });
    }

    /** 取消当前运行的反编译任务 */
    public void cancelCurrentTask() {
        BackgroundTasks.cancel(currentDecompileTask);
    }

    /**
     * 打开文本文件标签页（XML/JSON/YML/properties/.java 等）。
     * 读取字节码转为 UTF-8 文本，在只读 CodeArea 中显示。
     */
    public void openTextFileTab(FileTreeNode node, Workspace workspace, TabPane codeTabPane) {
        // 去重检查
        for (Tab tab : codeTabPane.getTabs()) {
            if (tab.getText().equals(node.getName())) {
                codeTabPane.getSelectionModel().select(tab);
                return;
            }
        }

        statusBar.setFilePath(I18nUtil.getString("status.reading", node.getFullPath()));

        BackgroundTasks.run("TextFile-" + node.getName(), () -> {
            try {
                byte[] bytes = readFileBytes(node, workspace);
                if (bytes == null) {
                    Platform.runLater(() -> showAlert(I18nUtil.getString("dialog.error.title"),
                            I18nUtil.getString("dialog.read.unable", node.getFullPath())));
                    return;
                }

                String text = new String(bytes, StandardCharsets.UTF_8);

                Platform.runLater(() -> {
                    jfx.incubator.scene.control.richtext.CodeArea codeArea =
                            new jfx.incubator.scene.control.richtext.CodeArea();
                    codeArea.getStyleClass().add("code-editor");
                    codeArea.setSyntaxDecorator(
                            com.bingbihanji.fxdecomplie.ui.code.TextFileDecorator.instance());
                    codeArea.setEditable(false);
                    codeArea.setWrapText(config.decompiler.wrapText);
                    LineNumberGutter.setEnabled(codeArea, true);
                    codeArea.setText(text);
                    codeArea.setFont(javafx.scene.text.Font.font(config.theme.fontFamily, config.theme.fontSize));

                    Tab tab = new Tab(node.getName(), codeArea);
                    tab.setOnClosed(e -> {
                    }); // no cleanup needed
                    codeTabPane.getTabs().add(tab);
                    codeTabPane.getSelectionModel().select(tab);

                    statusBar.setFilePath(node.getFullPath());
                    statusBar.setEncoding("UTF-8");
                });
            } catch (Exception ex) {
                if (ex instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
                    Thread.interrupted();
                    return;
                }
                Platform.runLater(() -> showAlert(I18nUtil.getString("dialog.error.title"),
                        I18nUtil.getString("dialog.read.failed", ex.getMessage())));
            }
        });
    }

    /** 读取文件字节码（文本文件版本，和 readClassBytes 逻辑相同） */
    private byte[] readFileBytes(FileTreeNode node, Workspace workspace) throws IOException {
        byte[] bytes = node.getCachedBytes();
        if (bytes != null) return bytes;
        if (!workspace.isArchive()) {
            File source = workspace.getSourceFile();
            return Files.readAllBytes(new File(source, node.getFullPath()).toPath());
        }
        // For archives, bytes should have been cached in ClassDiscoverer
        return null;
    }

    /** 创建代码编辑器标签页 */
    private CodeEditorTab createCodeEditorTab(OpenFile openFile, boolean lineNumbersEnabled,
                                              byte[] classBytes, CodeMetadata metadata,
                                              Consumer<CodeMetadata.Reference> onNavigate) {
        return new CodeEditorTab(
                openFile, editorTheme,
                config.theme.fontFamily, config.theme.fontSize,
                config.decompiler.wrapText, lineNumbersEnabled,
                classBytes, metadata, onNavigate
        );
    }

    /** 查找已打开的同名 class 标签页，移除不同引擎的重复标签页 */
    private Tab findOrRemoveOpenClassTab(TabPane codeTabPane, FileTreeNode node, DecompilerTypeEnum engine) {
        java.util.List<Tab> toRemove = new java.util.ArrayList<>();
        for (Tab tab : codeTabPane.getTabs()) {
            if (tab instanceof CodeEditorTab codeTab
                    && codeTab.getOpenFile().getFullPath().equals(node.getFullPath())) {
                if (codeTab.getOpenFile().getEngine() == engine) {
                    return tab;
                }
                toRemove.add(tab);
            }
        }
        codeTabPane.getTabs().removeAll(toRemove);
        return null;
    }

    /** 读取类字节码（依次尝试节点缓存、工作区索引、全局缓存、磁盘读取） */
    private byte[] readClassBytes(FileTreeNode node, Workspace workspace) throws IOException {
        byte[] bytes = node.getCachedBytes();
        if (bytes != null) return bytes;
        String internalName = node.getFullPath().replace(".class", "");
        bytes = workspace.getIndex().getClassBytes(internalName);
        if (bytes != null) return bytes;
        bytes = BytecodeCache.get(internalName);
        if (bytes != null) return bytes;
        if (!workspace.isArchive()) {
            File source = workspace.getSourceFile();
            return Files.readAllBytes(new File(source, node.getFullPath()).toPath());
        }
        return null;
    }

    /** 在文件树中查找指定路径的节点（公开，供快速打开使用） */
    public FileTreeNode findNodeByPath(TreeItem<FileTreeNode> item, String fullPath) {
        FileTreeNode node = item.getValue();
        if (node != null && fullPath.equals(node.getFullPath())) return node;
        for (TreeItem<FileTreeNode> child : item.getChildren()) {
            FileTreeNode found = findNodeByPath(child, fullPath);
            if (found != null) return found;
        }
        return null;
    }

    /** 绑定光标位置到状态栏 */
    private void bindCaretPosition(CodeEditorTab codeTab) {
        codeTab.getCodeArea().caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            if (newPos != null) {
                statusBar.setCursorPosition(newPos.index() + 1, newPos.charIndex() + 1);
            }
        });
    }

    /**
     * 共享的反编译+缓存逻辑：依次查询 L2 内存缓存、L3 磁盘缓存，最后执行反编译。
     * 命中 L3 时回填 L2，反编译结果同时写入 L2 和 L3。
     * workspaceKey 使用路径 + mtime + size，避免同路径文件替换后命中旧缓存。
     */
    private DecompileResult decompileWithCache(String internalName, DecompilerTypeEnum engine,
                                               byte[] bytes, FileTreeNode node, Workspace workspace) {
        String optionsHash = "default";
        String wsKey = computeWorkspaceKey(workspace);

        // ---- L2: in-memory decompile cache (fastest path) ----
        String sourceCode = decompileCache.get(wsKey, internalName, engine, optionsHash);

        // ---- L2 miss: try L3 disk-persisted cache ----
        if (sourceCode == null) {
            sourceCode = DiskCodeCache.load(wsKey, internalName, engine);
            if (sourceCode != null) {
                // ---- L3 hit: back-fill L2 so next lookup is instantaneous ----
                decompileCache.put(wsKey, internalName, engine, optionsHash, sourceCode);
            }
        }

        // ---- L2+L3 miss: execute actual decompilation ----
        if (sourceCode == null) {
            // 线程复用可能残留中断标志，反编译前再次清除
            Thread.interrupted();
            sourceCode = DecompilerFactory.getDecompiler(engine)
                    .decompile(node.getFullPath(), bytes,
                            DecompilerContext.fromWorkspaceIndex(workspace.getIndex()));
            // ---- Save decompiled result to L2 (immediate) ----
            decompileCache.put(wsKey, internalName, engine, optionsHash, sourceCode);

            // ---- Save to L3 disk cache asynchronously (non-blocking) ----
            final String finalSource = sourceCode;
            BackgroundTasks.run("DiskCache-" + internalName, () -> {
                DiskCodeCache.save(wsKey, internalName, engine, finalSource);
            });
        }

        // ---- Extract metadata for Ctrl+Click navigation ----
        CodeMetadata metadata = OutlineParser.extractMetadata(sourceCode);
        return new DecompileResult(sourceCode, metadata);
    }

    /** 显示错误弹窗 */
    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR, message);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.showAndWait();
        });
    }

    /** 反编译结果，包含源码和元数据 */
    private record DecompileResult(String sourceCode, CodeMetadata metadata) {
    }

}
