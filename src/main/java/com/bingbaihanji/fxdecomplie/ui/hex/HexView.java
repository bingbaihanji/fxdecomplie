package com.bingbaihanji.fxdecomplie.ui.hex;

import com.bingbaihanji.fxdecomplie.ui.hex.format.BuiltinFormatters;
import com.bingbaihanji.fxdecomplie.ui.hex.format.CopyFormatter;
import com.bingbaihanji.fxdecomplie.ui.hex.model.HighlightModel;
import com.bingbaihanji.fxdecomplie.ui.hex.model.SearchModel;
import com.bingbaihanji.fxdecomplie.ui.hex.model.SelectionModel;
import com.bingbaihanji.fxdecomplie.ui.hex.renderer.HexGridRenderer;
import com.bingbaihanji.fxdecomplie.ui.hex.renderer.MiniMapRenderer;
import com.bingbaihanji.fxdecomplie.ui.hex.tooltip.HexTooltipRenderer;
import com.bingbaihanji.fxdecomplie.ui.hex.util.HexViewMetrics;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.geometry.Orientation;

/**
 * A reusable JavaFX hex editor view component.
 *
 * Renders binary data in a hex-editor grid with:
 * - Address column + hex byte columns (8-byte separators) + ASCII column
 * - Foreground/background highlighting (callback + static region APIs)
 * - Mouse-driven selection with copy to clipboard
 * - MiniMap overview strip
 * - Search (Ctrl+F) and Goto (Ctrl+G)
 */
public class HexView extends Region {

    private final HexViewConfig config;
    private final SelectionModel selection;
    private final HighlightModel highlights;
    private final SearchModel search;
    private HexDataProvider provider = new ByteArrayProvider(new byte[0]);
    private HexViewMetrics metrics;
    private Canvas canvas;
    private ScrollBar scrollBar;
    private Pane canvasPane;
    private HexGridRenderer gridRenderer;
    private MiniMapRenderer miniMapRenderer;

    // Scroll state
    private long scrollRow = 0;
    private long totalRows = 0;

    // Search / Goto UI
    private TextField searchField;
    private TextField gotoField;

    // Selection drag state
    private boolean dragging = false;

    // Hover tracking (for tooltip)
    private long hoveredAddress = -1;
    private double hoverMouseX, hoverMouseY;
    private java.util.function.BiConsumer<Long, Integer> onHoverCallback;

    // Pending repaint
    private volatile boolean dirty = true;

    // Animation timer for continuous rendering
    private AnimationTimer renderTimer;

    public HexView() {
        this.config = new HexViewConfig(this::markDirty);
        this.selection = new SelectionModel(0);
        this.highlights = new HighlightModel();
        this.search = new SearchModel();

        // Sync grayOutZero
        this.config.grayOutZeroProperty().addListener((o, old, val) ->
                highlights.setGrayOutZero(val));
        highlights.setGrayOutZero(config.isGrayOutZero());

        // Config change listeners
        this.config.upperCaseHexProperty().addListener((o, old, val) -> markDirty());
        this.config.showAsciiProperty().addListener((o, old, val) -> markDirty());
        this.config.showMiniMapProperty().addListener((o, old, val) -> markDirty());
        this.config.bytesPerRowProperty().addListener((o, old, val) -> rebuildMetrics());
        this.config.fontProperty().addListener((o, old, val) -> rebuildMetrics());

        // Set minimum size so the region always gets reasonable space
        this.setMinSize(200, 100);

        setupLayout();
        setupEventHandlers();
        setupRenderLoop();

        this.setStyle("-fx-background-color: #19191c;");
        rebuildMetrics();
    }

    // ===================== Public API =====================

    public HexDataProvider getProvider() {
        return provider;
    }

    public void setProvider(HexDataProvider provider) {
        this.provider = provider;
        this.selection.setMaxAddress(provider.getSize() - 1);
        this.selection.clear();
        this.totalRows = computeTotalRows();
        this.scrollRow = 0;
        updateScrollbar();
        // Auto-apply builtin highlighters
        HexViewController.getInstance().applyHighlights(provider);
        markDirty();
    }

    public HexViewConfig getConfig() {
        return config;
    }

    public SelectionModel getSelection() {
        return selection;
    }

    public HighlightModel getHighlights() {
        return highlights;
    }

    public SearchModel getSearch() {
        return search;
    }

    /**
     * Set a callback invoked when the mouse hovers over a byte.
     * @param callback receives (address, size) or (-1, 0) when hover ends.
     */
    public void setOnHover(java.util.function.BiConsumer<Long, Integer> callback) {
        this.onHoverCallback = callback;
    }

    /** Copy the current selection using the given formatter */
    public void copyAs(CopyFormatter formatter) {
        if (!selection.hasSelection()) {
            return;
        }
        long addr = selection.getMinAddress();
        int size = (int) selection.getSelectionSize();
        if (size <= 0) {
            return;
        }
        byte[] data = new byte[size];
        int n = provider.read(addr, data, 0, size);
        if (n <= 0) {
            return;
        }
        String text = formatter.format(data, addr);
        var content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    /** Show search bar */
    public void showSearch() {
        searchField.setVisible(true);
        searchField.requestFocus();
    }

    /** Show goto bar */
    public void showGoto() {
        gotoField.setVisible(true);
        gotoField.requestFocus();
    }

    /** Jump so the given address is visible */
    public void jumpTo(long address) {
        int bytesPerRow = config.getBytesPerRow();
        if (bytesPerRow == 0) {
            bytesPerRow = 16;
        }
        scrollRow = address / bytesPerRow;
        updateScrollbar();
        markDirty();
    }

    /** Force redraw on next pulse */
    public void markDirty() {
        dirty = true;
    }

    // ===================== Layout Setup =====================

    private void setupLayout() {
        // Use a plain Pane — it does NOT auto-layout children, so we have full control
        this.canvasPane = new Pane();
        this.canvasPane.setStyle("-fx-background-color: #19191c;");

        this.canvas = new Canvas();
        // Bind canvas size to pane size — canvas auto-fills
        this.canvas.widthProperty().bind(this.canvasPane.widthProperty());
        this.canvas.heightProperty().bind(this.canvasPane.heightProperty());

        this.scrollBar = new ScrollBar();
        this.scrollBar.setOrientation(Orientation.VERTICAL);
        this.scrollBar.setMin(0);
        this.scrollBar.setMax(100);
        this.scrollBar.setVisibleAmount(10);
        this.scrollBar.setBlockIncrement(1);

        // Search / goto overlay
        this.searchField = new TextField();
        this.searchField.setPromptText("Search (hex or text)...");
        this.searchField.setVisible(false);
        this.searchField.setMaxWidth(300);
        this.searchField.setStyle(
                "-fx-background-color: #2a2a2e; -fx-text-fill: #ddd; -fx-prompt-text-fill: #888;");

        this.gotoField = new TextField();
        this.gotoField.setPromptText("Goto offset (hex)...");
        this.gotoField.setVisible(false);
        this.gotoField.setMaxWidth(250);
        this.gotoField.setStyle(
                "-fx-background-color: #2a2a2e; -fx-text-fill: #ddd; -fx-prompt-text-fill: #888;");

        var overlay = new VBox(4, searchField, gotoField);
        overlay.setPadding(new Insets(4));
        overlay.setPickOnBounds(false); // clicks pass through to canvas

        // Position overlay at top-right
        overlay.layoutXProperty().bind(
                canvasPane.widthProperty().subtract(overlay.widthProperty()).subtract(8));
        overlay.setLayoutY(4);

        canvasPane.getChildren().addAll(canvas, overlay);

        // Add to Region
        this.getChildren().addAll(canvasPane, scrollBar);
    }

    // ===================== Render Loop =====================

    private void setupRenderLoop() {
        renderTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (dirty && getWidth() > 0 && getHeight() > 0) {
                    dirty = false;
                    drawCanvas();
                }
            }
        };
        renderTimer.start();
    }

    // ===================== Metrics =====================

    private void rebuildMetrics() {
        this.metrics = new HexViewMetrics(config.getFont(), config.getBytesPerRow(),
                config.getAddressWidth());
        this.selection.setMaxAddress(provider.getSize() - 1);
        this.totalRows = computeTotalRows();
        updateScrollbar();
        markDirty();
    }

    private long computeTotalRows() {
        long size = provider.getSize();
        if (size == 0) {
            return 0;
        }
        int bpr = config.getBytesPerRow();
        if (bpr == 0) {
            bpr = 16;
        }
        return (size + bpr - 1) / bpr;
    }

    private void updateScrollbar() {
        scrollBar.setMin(0);
        double headerH = metrics != null ? metrics.getHeaderHeight() : 0;
        double gridH = Math.max(0, canvas.getHeight() - headerH);
        double visRows = gridH > 0 && metrics != null ? metrics.visibleRows(gridH) : 10;
        long maxScroll = Math.max(0, totalRows - Math.max(1, (long) Math.floor(visRows)));
        scrollRow = Math.max(0, Math.min(scrollRow, maxScroll));
        scrollBar.setMax(maxScroll);
        scrollBar.setValue(scrollRow);
        scrollBar.setVisibleAmount(Math.max(1, visRows));
        scrollBar.setUnitIncrement(1);
    }

    // ===================== Sizing =====================

    @Override
    protected double computePrefWidth(double height) {
        double mmWidth = config.isShowMiniMap()
                ? config.getMiniMapWidth() * 8 + 10 : 0;
        double mw = metrics != null ? metrics.getTotalWidth() : 600;
        return mw + 20 + mmWidth;
    }

    @Override
    protected double computePrefHeight(double width) {
        return 600;
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        double scrollW = scrollBar.prefWidth(-1);
        double canvasW = w - scrollW;

        // Pane fills the left area
        canvasPane.resizeRelocate(0, 0, canvasW, h);

        // Scrollbar on the right edge
        scrollBar.resizeRelocate(canvasW, 0, scrollW, h);

        // Canvas size is bound to pane, but force a redraw after resize
        markDirty();
    }

    // ===================== Rendering =====================

    private void drawCanvas() {
        if (metrics == null) {
            rebuildMetrics();
        }
        if (metrics == null) {
            return;
        }

        double canvasW = canvas.getWidth();
        double canvasH = canvas.getHeight();
        if (canvasW <= 0 || canvasH <= 0) {
            return;
        }

        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Lazy-init / update renderers (only when metrics change)
        if (gridRenderer == null) {
            gridRenderer = new HexGridRenderer(gc, metrics);
        } else {
            gridRenderer.setMetrics(metrics);
        }
        if (miniMapRenderer == null) {
            miniMapRenderer = new MiniMapRenderer(gc);
        }

        double headerH = metrics.getHeaderHeight();
        double gridH = canvasH - headerH;
        if (gridH <= 0) {
            return;
        }

        // --- Draw header ---
        gridRenderer.drawHeader(0, config);

        // --- Draw hex grid below header ---
        gc.save();
        gc.translate(0, headerH);
        gridRenderer.draw(provider, selection, highlights, config,
                scrollRow, canvasW, gridH);

        // --- Draw minimap ---
        if (config.isShowMiniMap()) {
            int mmWidth = config.getMiniMapWidth() * 8;
            miniMapRenderer.draw(canvasW - mmWidth - 10, 0,
                    mmWidth, gridH, provider, metrics, config,
                    scrollRow, totalRows);
        }

        gc.restore();

        // --- Draw hover tooltip ---
        if (hoveredAddress >= 0 && hoveredAddress < provider.getSize()) {
            // Translate back to canvas coords for tooltip positioning
            HexTooltipRenderer.draw(gc, hoverMouseX, hoverMouseY,
                    hoveredAddress, provider, metrics, canvasW - 2, canvasH);
        }

        updateScrollbar();
    }

    // ===================== Event Handling =====================

    private void setupEventHandlers() {
        scrollBar.valueProperty().addListener((obs, old, val) -> {
            scrollRow = val.longValue();
            markDirty();
        });

        // Mouse wheel on canvasPane
        canvasPane.setOnScroll(e -> {
            double multiplier = e.isControlDown() ? 10 : 1;
            long delta = (long) (-e.getDeltaY() * multiplier / 40);
            scrollRow = Math.max(0, Math.min(Math.max(0, totalRows - 1), scrollRow + delta));
            updateScrollbar();
            markDirty();
        });

        canvas.setOnMousePressed(e -> {
            canvas.requestFocus();
            if (metrics == null) {
                return;
            }
            double headerH = metrics.getHeaderHeight();
            if (e.getY() < headerH) {
                return;
            }
            double gridY = e.getY() - headerH;
            int row = metrics.pixelToRow(gridY);
            long addr = (scrollRow + row) * config.getBytesPerRow();

            int hexCol = metrics.pixelToHexColumn(e.getX());
            int asciiCol = metrics.pixelToAsciiColumn(e.getX());

            if (hexCol >= 0) {
                addr += hexCol;
            } else if (asciiCol >= 0) {
                addr += asciiCol;
            }

            if (addr < 0 || addr >= provider.getSize()) {
                return;
            }

            if (e.isShiftDown()) {
                selection.extendSelection(addr);
            } else {
                selection.startSelection(addr);
            }
            dragging = true;
            markDirty();
        });

        canvas.setOnMouseDragged(e -> {
            if (!dragging || metrics == null) {
                return;
            }
            double headerH = metrics.getHeaderHeight();
            if (e.getY() < headerH) {
                return;
            }
            double gridY = e.getY() - headerH;
            int row = metrics.pixelToRow(gridY);
            long addr = (scrollRow + row) * config.getBytesPerRow();
            int hexCol = metrics.pixelToHexColumn(e.getX());
            int asciiCol = metrics.pixelToAsciiColumn(e.getX());
            if (hexCol >= 0) {
                addr += hexCol;
            } else if (asciiCol >= 0) {
                addr += asciiCol;
            }
            if (addr < 0 || addr >= provider.getSize()) {
                return;
            }
            selection.extendSelection(addr);
            markDirty();
        });

        canvas.setOnMouseReleased(e -> {
            dragging = false;
        });

        // Mouse move — track hovered address for tooltip
        canvas.setOnMouseMoved(e -> {
            if (metrics == null || provider.getSize() == 0) return;
            double headerH = metrics.getHeaderHeight();
            if (e.getY() < headerH) {
                if (hoveredAddress >= 0) {
                    hoveredAddress = -1;
                    if (onHoverCallback != null) onHoverCallback.accept(-1L, 0);
                    markDirty();
                }
                return;
            }
            double gridY = e.getY() - headerH;
            int row = metrics.pixelToRow(gridY);
            long rowAddr = (scrollRow + row) * config.getBytesPerRow();
            int hexCol = metrics.pixelToHexColumn(e.getX());
            int asciiCol = metrics.pixelToAsciiColumn(e.getX());
            long newHover;
            if (hexCol >= 0) {
                newHover = rowAddr + hexCol;
            } else if (asciiCol >= 0) {
                newHover = rowAddr + asciiCol;
            } else {
                if (hoveredAddress >= 0) {
                    hoveredAddress = -1;
                    if (onHoverCallback != null) onHoverCallback.accept(-1L, 0);
                    markDirty();
                }
                return;
            }
            if (newHover >= provider.getSize()) return;
            if (newHover != hoveredAddress) {
                hoveredAddress = newHover;
                hoverMouseX = e.getX();
                hoverMouseY = e.getY();
                if (onHoverCallback != null) onHoverCallback.accept(hoveredAddress, 1);
                markDirty();
            }
        });

        canvas.setFocusTraversable(true);

        // Keyboard shortcuts
        canvas.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.F && e.isControlDown()) {
                showSearch();
                e.consume();
            } else if (e.getCode() == KeyCode.G && e.isControlDown()) {
                showGoto();
                e.consume();
            } else if (e.getCode() == KeyCode.A && e.isControlDown()) {
                long sz = provider.getSize();
                if (sz > 0) {
                    selection.select(0, sz - 1);
                }
                markDirty();
                e.consume();
            } else if (e.getCode() == KeyCode.C && e.isControlDown() && !e.isShiftDown()) {
                copyAs(BuiltinFormatters.HEX_SPACED);
                e.consume();
            } else if (e.getCode() == KeyCode.C && e.isControlDown() && e.isShiftDown()) {
                copyAs(BuiltinFormatters.C_ARRAY);
                e.consume();
            } else if (e.getCode() == KeyCode.J && e.isControlDown() && e.isShiftDown()) {
                copyAs(BuiltinFormatters.JAVA_ARRAY);
                e.consume();
            } else if (e.getCode() == KeyCode.R && e.isControlDown() && e.isShiftDown()) {
                copyAs(BuiltinFormatters.RUST_ARRAY);
                e.consume();
            } else if (e.getCode() == KeyCode.P && e.isControlDown() && e.isShiftDown()) {
                copyAs(BuiltinFormatters.PYTHON_BYTES);
                e.consume();
            }
        });

        // --- Search field ---
        searchField.setOnAction(e -> {
            String q = searchField.getText().trim();
            if (q.isEmpty()) {
                return;
            }
            boolean isHex = looksLikeHexQuery(q);
            int count = search.search(provider, q, isHex);
            if (count > 0) {
                long addr = search.getMatchAddresses().get(0);
                jumpTo(addr);
                int needleLen = isHex
                        ? search.getLastQuery().replaceAll("[^0-9a-fA-F]", "").length() / 2
                        : search.getLastQuery().length();
                selection.select(addr, addr + needleLen - 1);
                // Highlight all matches as background
                highlights.clearAll();
                HexViewController.getInstance().applyHighlights(provider);
                for (long matchAddr : search.getMatchAddresses()) {
                    highlights.addBackgroundRegion(matchAddr, needleLen,
                            javafx.scene.paint.Color.rgb(255, 200, 0, 0.35));
                }
                markDirty();
            }
        });

        // --- Goto field ---
        gotoField.setOnAction(e -> {
            try {
                String text = gotoField.getText().trim();
                if (text.startsWith("0x") || text.startsWith("0X")) {
                    text = text.substring(2);
                }
                long addr = Long.parseLong(text, 16);
                jumpTo(addr);
                selection.startSelection(addr);
                markDirty();
            } catch (NumberFormatException ignored) {
            }
        });
    }

    private static boolean looksLikeHexQuery(String query) {
        String stripped = query.replaceAll("[^0-9a-fA-F]", "");
        if (stripped.length() < 2 || stripped.length() % 2 != 0) {
            return false;
        }
        return query.matches("(?i)^(0x)?[0-9a-f]{2}([\\s:_-]*(0x)?[0-9a-f]{2})*$");
    }
}
