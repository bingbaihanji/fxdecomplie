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

public final class HexDetailsDialog {

    private HexDetailsDialog() {
        throw new AssertionError("utility class");
    }

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

        GridPane summary = new GridPane();
        summary.setHgap(12);
        summary.setVgap(6);
        summary.setPadding(new Insets(0, 0, 8, 0));
        addRow(summary, 0, tr("hex.details.offset", "Offset"), "0x" + hex(address, 8));
        addRow(summary, 1, tr("hex.details.decimal", "Decimal"), Long.toString(address));
        addRow(summary, 2, tr("hex.details.byte", "Byte"), data.byteLine());
        addRow(summary, 3, tr("hex.details.selection", "Selection"), data.selectionLine());
        addRow(summary, 4, tr("hex.details.region", "Region"), data.regionLine());

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

    private static String formatPreview(byte[] bytes, long address) {
        StringBuilder sb = new StringBuilder();
        for (int row = 0; row < bytes.length; row += 16) {
            sb.append(String.format("%08X  ", address + row));
            int end = Math.min(row + 16, bytes.length);
            for (int i = row; i < end; i++) {
                sb.append(String.format("%02X ", bytes[i] & 0xFF));
            }
            for (int i = end; i < row + 16; i++) {
                sb.append("   ");
            }
            sb.append(' ');
            for (int i = row; i < end; i++) {
                int c = bytes[i] & 0xFF;
                sb.append(c >= 32 && c < 127 ? (char) c : '.');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static void addRow(GridPane grid, int row, String label, String value) {
        Label labelNode = new Label(label + ":");
        Label valueNode = new Label(value);
        valueNode.setWrapText(true);
        grid.add(labelNode, 0, row);
        grid.add(valueNode, 1, row);
    }

    private static String hex(long value, int width) {
        return String.format("%0" + width + "X", value);
    }

    private static String tr(String key, String fallback, Object... args) {
        return I18nUtil.getStringOrDefault(key, fallback, args);
    }

    private record DetailData(String byteLine, String selectionLine,
                              String regionLine, String text) {
    }
}
