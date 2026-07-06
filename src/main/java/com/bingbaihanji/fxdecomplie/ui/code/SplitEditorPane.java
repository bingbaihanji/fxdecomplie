package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.util.I18nUtil;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 多分屏编辑器容器，按需管理 1-3 个并排 TabPane（cells）。
 *
 * <p>类似 IDEA 的 Split Editor：默认仅一个主 panel，右键标签选择 "向右拆分"
 * 时动态创建右侧 panel。每个 cell 独立持有 CodeEditorTab，
 * 同 class 可用不同反编译引擎在各分屏中对比。关闭分屏时 tab 移回主 panel。</p>
 *
 * @author bingbaihanji
 * @date 2026-06-23
 */
public final class SplitEditorPane extends StackPane {

    /** 最大分屏数 */
    private static final int MAX_CELLS = 3;
    /** cell 最小宽度（防止拖拽分隔线完全覆盖 cell） */
    private static final double MIN_CELL_WIDTH = 200.0;

    private final SplitPane splitPane;
    /** 所有 cell（cells[0] 始终存在，其余按需创建；关闭中间 cell 后自动压缩数组） */
    private final TabPane[] cells = new TabPane[MAX_CELLS];
    /** 外部拖放配置（来自 WorkspaceTabManager） */
    private final AppConfig dragDropConfig;
    /** 编辑器主题（用于外部拖放） */
    private final com.bingbaihanji.fxdecomplie.ui.theme.VsCodeThemeLoader.ThemeData dragDropTheme;
    /** 最近获得焦点的 cell（基于引用而非索引，避免压缩后索引错位） */
    private TabPane focusedCell;
    /** 当前活跃 cell 数 (= splitPane.getItems().size()) */
    private int activeCount = 1;
    /** 分屏状态变化回调（用于同步主 panel 的勾选框） */
    private Runnable onSplitStateChanged;

    public SplitEditorPane() {
        this(null, null);
    }

    public SplitEditorPane(AppConfig dragDropConfig,
                           com.bingbaihanji.fxdecomplie.ui.theme.VsCodeThemeLoader.ThemeData dragDropTheme) {
        this.dragDropConfig = dragDropConfig;
        this.dragDropTheme = dragDropTheme;

        splitPane = new SplitPane();
        splitPane.getStyleClass().add("split-editor-pane");
        getChildren().add(splitPane);

        // 默认只创建主 cell（cell 0）
        cells[0] = createCell();
        splitPane.getItems().add(cells[0]);
        // 单 cell 时隐藏分隔线
        splitPane.setDividerPositions(1.0);
    }

    /** 选择一个与 exclude 不同的引擎 */
    public static DecompilerTypeEnum chooseDifferentEngine(DecompilerTypeEnum exclude) {
        for (DecompilerTypeEnum e : DecompilerTypeEnum.values()) {
            if (e != exclude) {
                return e;
            }
        }
        return DecompilerTypeEnum.VINEFLOWER;
    }

    /** 创建新 cell 并插入到 SplitPane 指定位置 */
    private TabPane createCellAt(int posInSplitPane) {
        TabPane newCell = createCell();
        splitPane.getItems().add(posInSplitPane, newCell);
        activeCount = splitPane.getItems().size();
        syncCellsArray();
        return newCell;
    }

    // ==================== 公开 API ====================

    /** 创建一个 TabPane cell */
    private TabPane createCell() {
        TabPane pane = new TabPane();
        pane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        pane.setMinWidth(MIN_CELL_WIDTH);
        pane.getStyleClass().add("code-tab-pane");

        // 选中变化 → 更新焦点跟踪（基于 cell 引用，避免压缩后索引错位）
        pane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            focusedCell = pane;
            if (newTab instanceof CodeEditorTab ct) {
                ct.setSplitEditorPane(this);
            }
        });

        // tab 关闭监听 → 折叠空 cell
        pane.getTabs().addListener((javafx.collections.ListChangeListener<Tab>) change -> {
            while (change.next()) {
                if (change.wasRemoved()) {
                    Platform.runLater(() -> checkCollapseCell(pane));
                }
            }
        });

        // 安装右键菜单
        installContextMenu(pane);

        // 安装跨窗口拖放
        if (dragDropConfig != null && dragDropTheme != null) {
            CodeOnlyWindow.installDragDropHandlers(pane, dragDropConfig, dragDropTheme);
        }

        return pane;
    }

    /** @return 主 TabPane（始终存在，cell 0） */
    public TabPane primaryTabPane() {
        return cells[0];
    }

    /** @return 所有已创建的 TabPane，按 splitPane 左→右顺序排列 */
    public List<TabPane> allTabPanes() {
        List<TabPane> result = new ArrayList<>(MAX_CELLS);
        for (Node node : splitPane.getItems()) {
            if (node instanceof TabPane tp) {
                result.add(tp);
            }
        }
        return result;
    }

    /** @return 当前激活的 cell 数 */
    public int activeCellCount() {
        return activeCount;
    }

    /** @return 给定 tab 所在的 TabPane，未找到时返回 {@code null} */
    public TabPane tabPaneFor(Tab tab) {
        for (Node node : splitPane.getItems()) {
            if (node instanceof TabPane cell && cell.getTabs().contains(tab)) {
                return cell;
            }
        }
        return null;
    }

    /** @return 获得焦点的 cell 中选中的 CodeEditorTab */
    public CodeEditorTab currentCodeTab() {
        TabPane focused = focusedCell;
        if (focused == null || !splitPane.getItems().contains(focused)) {
            focused = cells[0];
        }
        Tab selected = focused.getSelectionModel().getSelectedItem();
        return selected instanceof CodeEditorTab ct ? ct : null;
    }

    /** 对所有 cell 中的 CodeEditorTab 执行操作 */
    public void forEachTab(Consumer<CodeEditorTab> action) {
        for (TabPane cell : cells) {
            if (cell == null) {
                continue;
            }
            for (Tab tab : cell.getTabs()) {
                if (tab instanceof CodeEditorTab ct) {
                    action.accept(ct);
                }
            }
        }
    }

    /**
     * 在 sourceTab 右侧创建新分屏。已达上限 3 则返回 {@code null}。
     *
     * @return 新创建的 cell，已达上限时返回 {@code null}
     */
    public TabPane splitRight(CodeEditorTab sourceTab) {
        if (activeCount >= MAX_CELLS) {
            return null;
        }

        // 以 splitPane 中的实际位置为准（cells 数组可能因压缩而产生空洞）
        int srcPos = 0;
        if (sourceTab != null) {
            TabPane srcPane = tabPaneFor(sourceTab);
            srcPos = splitPane.getItems().indexOf(srcPane);
            if (srcPos < 0) {
                srcPos = 0;
            }
        }
        int targetPos = srcPos + 1;
        if (targetPos > splitPane.getItems().size()) {
            targetPos = splitPane.getItems().size();
        }

        TabPane newCell = createCellAt(targetPos);
        activeCount = splitPane.getItems().size();
        rebalanceDividers();
        return newCell;
    }

    /** 设置分屏状态变化回调 */
    public void setOnSplitStateChanged(Runnable callback) {
        this.onSplitStateChanged = callback;
    }

    /** 关闭所有非主 cell，回到单 panel 状态 */
    public void closeAllSplits() {
        for (int i = MAX_CELLS - 1; i > 0; i--) {
            if (cells[i] != null && splitPane.getItems().contains(cells[i])) {
                closeSplit(cells[i]);
            }
        }
    }

    // ==================== 内部方法 ====================

    /** 关闭指定 cell（折叠），将其 tab 移到主 cell */
    public void closeSplit(TabPane cell) {
        if (cell == primaryTabPane() || cell == null) {
            return;
        }
        if (!splitPane.getItems().contains(cell)) {
            return; // 已关闭，防止递归重入
        }

        // 将 cell 中的 tab 移到主 cell
        List<Tab> tabs = new ArrayList<>(cell.getTabs());
        cell.getTabs().clear();
        for (Tab tab : tabs) {
            primaryTabPane().getTabs().add(tab);
        }

        splitPane.getItems().remove(cell);
        // 清除聚焦引用（如果指向已关闭的 cell）
        if (focusedCell == cell) {
            focusedCell = null;
        }
        activeCount = splitPane.getItems().size();
        rebalanceDividers(); // 内部调用 syncCellsArray 自动压缩
    }

    private int findCellIndex(TabPane cell) {
        for (int i = 0; i < MAX_CELLS; i++) {
            if (cells[i] == cell) {
                return i;
            }
        }
        return -1;
    }

    /** tab 关闭后检查 cell 是否为空，为空时右侧 cell 左移替代 */
    private void checkCollapseCell(TabPane cell) {
        if (!cell.getTabs().isEmpty()) {
            return;
        }
        if (cell == primaryTabPane()) {
            // 主 cell 为空 → 右侧第一个有内容的 cell 左移替代
            promoteRightToPrimary();
        } else {
            closeSplit(cell);
        }
    }

    /** 主 cell 为空时，将右侧第一个 cell 的内容移入，并折叠右侧 cell */
    private void promoteRightToPrimary() {
        // 找右侧第一个有 tab 的 cell
        TabPane donor = null;
        for (int i = 1; i < MAX_CELLS; i++) {
            if (cells[i] != null && !cells[i].getTabs().isEmpty()
                    && splitPane.getItems().contains(cells[i])) {
                donor = cells[i];
                break;
            }
        }
        if (donor == null) {
            return;
        } // 右侧也没有内容，保持 cell0 空着

        // 将 donor 的 tab 移到 cell0
        List<Tab> tabs = new ArrayList<>(donor.getTabs());
        donor.getTabs().clear();
        for (Tab tab : tabs) {
            cells[0].getTabs().add(tab);
        }
        // 关闭 donor cell（会自动压缩数组）
        closeSplit(donor);
    }

    /** 将 cells 数组与 splitPane 实际顺序同步（插入中间 cell 后必须调用） */
    private void syncCellsArray() {
        java.util.Arrays.fill(cells, null);
        int i = 0;
        for (Node node : splitPane.getItems()) {
            if (node instanceof TabPane tp && i < MAX_CELLS) {
                cells[i++] = tp;
            }
        }
    }

    /** 按活跃 cell 数量重新均分分隔线 */
    private void rebalanceDividers() {
        // 使用 splitPane.getItems().size() 而非 activeCount 作为权威来源，
        // 避免手动维护的 activeCount 与实际不符时设置错误的分隔线位置
        int count = splitPane.getItems().size();
        activeCount = count;
        if (count <= 1) {
            splitPane.setDividerPositions(1.0);
        } else if (count == 2) {
            splitPane.setDividerPositions(0.5);
        } else {
            splitPane.setDividerPositions(0.333, 0.667);
        }
        syncCellsArray();
        notifySplitStateChanged();
    }

    // ==================== 右键菜单 ====================

    private void notifySplitStateChanged() {
        if (onSplitStateChanged != null) {
            Platform.runLater(onSplitStateChanged);
        }
    }

    // ==================== 辅助方法 ====================

    private void installContextMenu(TabPane pane) {
        pane.setOnContextMenuRequested(event -> {
            ContextMenu menu = new ContextMenu();
            Tab current = pane.getSelectionModel().getSelectedItem();

            // 固定标签页
            CheckMenuItem pin = new CheckMenuItem(I18nUtil.getString("context.pinTab"));
            pin.setDisable(current == null);
            pin.setSelected(current != null && Boolean.TRUE.equals(current.getProperties().get("pinned")));
            pin.setOnAction(e -> {
                if (current != null) {
                    current.getProperties().put("pinned", pin.isSelected());
                    if (current instanceof CodeEditorTab ct) {
                        ct.updatePinnedDisplay(pin.isSelected());
                    }
                }
            });

            // 切换引擎
            Menu engineMenu = new Menu(I18nUtil.getString("context.switchEngine"));
            if (current instanceof CodeEditorTab ct) {
                ToggleGroup eg = new ToggleGroup();
                DecompilerTypeEnum curEngine = ct.getOpenFile().engine();
                for (DecompilerTypeEnum eng : DecompilerTypeEnum.values()) {
                    RadioMenuItem ri = new RadioMenuItem(eng.name());
                    ri.setToggleGroup(eg);
                    ri.setSelected(eng == curEngine);
                    ri.setOnAction(ev -> ct.switchEngine(eng));
                    engineMenu.getItems().add(ri);
                }
            }

            // 向右拆分（委托给 tab 的 requestSplit，统一处理 cell 创建 + 内容打开）
            MenuItem splitRight = new MenuItem(I18nUtil.getString("context.splitRight"));
            splitRight.setDisable(current == null || activeCount >= MAX_CELLS);
            splitRight.setOnAction(e -> {
                if (current instanceof CodeEditorTab ct) {
                    ct.requestSplit();
                }
            });

            // 关闭分屏（仅非主 cell 可用）
            MenuItem closeSplitItem = new MenuItem(I18nUtil.getString("context.closeSplit"));
            closeSplitItem.setDisable(pane == primaryTabPane());
            closeSplitItem.setOnAction(e -> closeSplit(pane));

            // 在新窗口打开
            MenuItem openInNewWindow = new MenuItem(I18nUtil.getString("context.openInNewWindow"));
            openInNewWindow.setDisable(!(current instanceof CodeEditorTab));
            openInNewWindow.setOnAction(e -> {
                if (current instanceof CodeEditorTab codeTab) {
                    javafx.stage.Window window = pane.getScene().getWindow();
                    javafx.stage.Stage owner = window instanceof javafx.stage.Stage s ? s : null;
                    CodeOnlyWindow.openFrom(codeTab, dragDropConfig, owner);
                }
            });

            // 关闭其他
            MenuItem closeOthers = new MenuItem(I18nUtil.getString("context.closeOthers"));
            closeOthers.setOnAction(e -> {
                if (current != null) {
                    pane.getTabs().removeIf(t -> t != current);
                }
            });

            // 关闭右侧
            MenuItem closeRight = new MenuItem(I18nUtil.getString("context.closeRight"));
            closeRight.setDisable(current == null);
            closeRight.setOnAction(e -> {
                if (current == null) {
                    return;
                }
                int index = pane.getTabs().indexOf(current);
                var toClose = pane.getTabs().stream()
                        .skip(index + 1L)
                        .filter(t -> !Boolean.TRUE.equals(t.getProperties().get("pinned")))
                        .toList();
                pane.getTabs().removeAll(toClose);
            });

            // 关闭未固定
            MenuItem closeUnpinned = new MenuItem(I18nUtil.getString("context.closeUnpinned"));
            closeUnpinned.setOnAction(e -> pane.getTabs().removeIf(t ->
                    !Boolean.TRUE.equals(t.getProperties().get("pinned"))));

            // 关闭全部
            MenuItem closeAll = new MenuItem(I18nUtil.getString("context.closeAll"));
            closeAll.setOnAction(e -> pane.getTabs().clear());

            menu.getItems().addAll(pin, new SeparatorMenuItem(),
                    engineMenu, new SeparatorMenuItem(),
                    splitRight, closeSplitItem, new SeparatorMenuItem(),
                    openInNewWindow, new SeparatorMenuItem(),
                    closeOthers, closeRight, closeUnpinned, closeAll);
            menu.show(pane, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }
}
