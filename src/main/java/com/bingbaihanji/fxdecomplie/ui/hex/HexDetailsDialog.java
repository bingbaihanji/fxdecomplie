package com.bingbaihanji.fxdecomplie.ui.hex;

import com.bingbaihanji.fxdecomplie.ui.DialogHelper;
import com.bingbaihanji.fxdecomplie.ui.hex.analyzer.DataAnalyzer;
import com.bingbaihanji.fxdecomplie.ui.hex.model.PatternModel;
import com.bingbaihanji.fxdecomplie.util.i18n.I18nUtil;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;

/**
 * 十六进制详情对话框,用于展示指定地址处的详细字节信息 
 * <p>
 * 该类为工具类,提供静态方法 {@link #show} 弹出一个模态对话框,
 * 内容包括当前地址的偏移量、原始字节值、选区范围、所在区域路径、
 * 数据预览、结构区域列表以及数据分析器生成的解释值 
 * </p>
 * <p>
 * 对话框提供“复制详情”按钮,可将所有信息以文本形式复制到系统剪贴板 
 * </p>
 *
 * @author bingbaihanji
 * @see HexDataProvider
 * @see PatternModel
 * @see DataAnalyzer
 */
public final class HexDetailsDialog {

    /**
     * 私有构造方法,防止实例化 
     */
    private HexDetailsDialog() {
        throw new AssertionError("utility class");
    }

    /**
     * 显示字节详情对话框 
     *
     * @param owner          所属窗口(可为 null)
     * @param provider       数据提供者,用于读取字节内容
     * @param patternModel   模式模型,提供区域信息(可为 null)
     * @param address        当前焦点地址
     * @param selectionStart 选区起始地址(若无可传 -1)
     * @param selectionEnd   选区结束地址(若无可传 -1)
     */
    public static void show(Window owner, HexDataProvider provider, PatternModel patternModel,
                            long address, long selectionStart, long selectionEnd) {
        if (provider == null || address < 0 || address >= provider.getSize()) {
            return;
        }
        DetailData data = buildDetailData(provider, patternModel, address, selectionStart, selectionEnd);

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(tr("hex.details.title", "Byte Details"));
        dialog.setHeaderText(tr("hex.details.header", "Offset 0x{0}", hex(address, 8)));
        if (owner != null) {
            dialog.initOwner(owner);
        }
        DialogHelper.applyNativeStyle(dialog);
        dialog.setResizable(true);

        ButtonType copyType = new ButtonType(tr("hex.copy.details", "Copy Details"),
                ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(copyType, ButtonType.CLOSE);

        // 顶部摘要网格
        GridPane summary = new GridPane();
        summary.setHgap(12);
        summary.setVgap(6);
        summary.setPadding(new Insets(0, 0, 8, 0));
        addRow(summary, 0, tr("hex.details.offset", "Offset"), "0x" + hex(address, 8));
        addRow(summary, 1, tr("hex.details.decimal", "Decimal"), Long.toString(address));
        addRow(summary, 2, tr("hex.details.byte", "Byte"), data.byteLine());
        addRow(summary, 3, tr("hex.details.selection", "Selection"), data.selectionLine());
        addRow(summary, 4, tr("hex.details.region", "Region"), data.regionLine());

        // 详细文本区域
        TextArea detailArea = new TextArea(data.text());
        detailArea.setEditable(false);
        detailArea.setWrapText(false);
        detailArea.setPrefColumnCount(82);
        detailArea.setPrefRowCount(20);
        VBox.setVgrow(detailArea, Priority.ALWAYS);

        VBox content = new VBox(8, summary, detailArea);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(720, 520);

        Button copyButton = (Button) dialog.getDialogPane().lookupButton(copyType);
        copyButton.setOnAction(event -> {
            ClipboardContent clipboard = new ClipboardContent();
            clipboard.putString(data.text());
            Clipboard.getSystemClipboard().setContent(clipboard);
            event.consume();
        });

        dialog.show();
    }

    /**
     * 构建详情数据对象 
     *
     * @param provider       数据提供者
     * @param patternModel   模式模型
     * @param address        当前地址
     * @param selectionStart 选区起始
     * @param selectionEnd   选区结束
     * @return 包含摘要信息和完整文本的 {@link DetailData} 对象
     */
    private static DetailData buildDetailData(HexDataProvider provider, PatternModel patternModel,
                                              long address, long selectionStart, long selectionEnd) {
        long size = provider.getSize();
        int previewLen = (int) Math.min(32, size - address);
        byte[] preview = new byte[previewLen];
        provider.read(address, preview, 0, previewLen);
        int raw = previewLen == 0 ? 0 : preview[0] & 0xFF;
        char ascii = raw >= 32 && raw < 127 ? (char) raw : '.';

        HexViewController ctrl = HexViewController.getInstance();
        PatternModel patterns = patternModel == null ? new PatternModel() : patternModel;
        String path = patterns.getPathAt(address);
        List<PatternModel.Region> regions = patterns.getRegionsAt(address);
        List<DataAnalyzer.Result> analyzerResults = ctrl.analyze(address, preview, 0, previewLen);

        String selectionLine = selectionStart >= 0 && selectionEnd >= selectionStart
                ? "0x" + hex(selectionStart, 8) + " - 0x" + hex(selectionEnd, 8)
                  + " (" + (selectionEnd - selectionStart + 1) + " bytes)"
                : tr("hex.details.none", "None");
        String regionLine = path == null || path.isBlank()
                ? tr("hex.details.none", "None")
                : path;
        String binary = String.format("%8s", Integer.toBinaryString(raw)).replace(' ', '0');
        String byteLine = String.format("0x%02X  dec=%d  bin=%s  ASCII='%s'",
                raw, raw, binary, ascii);

        StringBuilder text = new StringBuilder();
        text.append(tr("hex.details.offset", "Offset")).append(": 0x").append(hex(address, 8))
                .append(" (").append(address).append(")\n");
        text.append(tr("hex.details.byte", "Byte")).append(": ").append(byteLine).append('\n');
        text.append(tr("hex.details.selection", "Selection")).append(": ").append(selectionLine).append('\n');
        text.append(tr("hex.details.region", "Region")).append(": ").append(regionLine).append("\n\n");

        text.append(tr("hex.details.preview", "Preview")).append(":\n");
        text.append(formatPreview(preview, address)).append("\n");

        text.append(tr("hex.details.regions", "Structure Regions")).append(":\n");
        if (regions.isEmpty()) {
            text.append("  ").append(tr("hex.details.none", "None")).append('\n');
        } else {
            for (PatternModel.Region region : regions) {
                text.append("  ").append(region.name())
                        .append(" [0x").append(hex(region.address(), 8))
                        .append(" + ").append(region.size()).append("] ");
                if (region.description() != null && !region.description().isBlank()) {
                    text.append(region.description());
                }
                text.append('\n');
            }
        }

        text.append('\n').append(tr("hex.details.analysis", "Interpreted Values")).append(":\n");
        if (analyzerResults.isEmpty()) {
            text.append("  ").append(tr("hex.details.none", "None")).append('\n');
        } else {
            for (DataAnalyzer.Result result : analyzerResults) {
                text.append("  ").append(result.label()).append(": ")
                        .append(result.value()).append('\n');
            }
        }

        return new DetailData(byteLine, selectionLine, regionLine, text.toString());
    }

    /**
     * 将字节数组格式化为十六进制预览字符串(每行 16 字节) 
     *
     * @param bytes   要格式化的字节数组
     * @param address 起始地址,用于显示偏移
     * @return 格式化后的多行文本
     */
    private static String formatPreview(byte[] bytes, long address) {
        StringBuilder sb = new StringBuilder();
        for (int row = 0; row < bytes.length; row += 16) {
            sb.append(String.format("%08X  ", address + row));
            int end = Math.min(row + 16, bytes.length);
            for (int i = row; i < end; i++) {
                sb.append(String.format("%02X ", bytes[i] & 0xFF));
            }
            sb.repeat("   ", Math.max(0, row + 16 - end));
            sb.append(' ');
            for (int i = row; i < end; i++) {
                int c = bytes[i] & 0xFF;
                sb.append(c >= 32 && c < 127 ? (char) c : '.');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * 向摘要网格中添加一行(标签 + 值) 
     *
     * @param grid  目标 GridPane
     * @param row   行索引
     * @param label 标签文本
     * @param value 值文本
     */
    private static void addRow(GridPane grid, int row, String label, String value) {
        Label labelNode = new Label(label + ":");
        Label valueNode = new Label(value);
        valueNode.setWrapText(true);
        grid.add(labelNode, 0, row);
        grid.add(valueNode, 1, row);
    }

    /**
     * 将长整型数值格式化为指定宽度的十六进制字符串(大写) 
     *
     * @param value 数值
     * @param width 宽度(不足前补零)
     * @return 格式化后的字符串
     */
    private static String hex(long value, int width) {
        return String.format("%0" + width + "X", value);
    }

    /**
     * 本地化字符串获取(带默认值) 
     *
     * @param key     国际化键
     * @param fallback 默认文本
     * @param args     格式化参数
     * @return 本地化后的字符串
     */
    private static String tr(String key, String fallback, Object... args) {
        return I18nUtil.getStringOrDefault(key, fallback, args);
    }

    /**
     * 详情数据记录,封装了摘要信息和完整文本 
     *
     * @param byteLine     字节信息摘要行
     * @param selectionLine 选区摘要行
     * @param regionLine   区域摘要行
     * @param text         完整的详情文本(用于复制和显示)
     */
    private record DetailData(String byteLine, String selectionLine,
                              String regionLine, String text) {
    }
}