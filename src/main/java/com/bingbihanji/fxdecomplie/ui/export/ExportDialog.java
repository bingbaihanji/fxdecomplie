package com.bingbihanji.fxdecomplie.ui.export;

import com.bingbihanji.fxdecomplie.config.AppConfig;
import com.bingbihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbihanji.fxdecomplie.model.ExportConfig;
import com.bingbihanji.fxdecomplie.utils.I18nUtil;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Export option and progress dialogs.
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class ExportDialog {

    private ExportDialog() {
        throw new AssertionError("utility class");
    }

    public static Optional<ExportConfig> show(Window owner, AppConfig appConfig,
                                              DecompilerTypeEnum currentEngine) {
        Dialog<ExportConfig> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(I18nUtil.getString("dialog.export.options.title"));
        dialog.setHeaderText(null);

        ButtonType exportButtonType = new ButtonType(
                I18nUtil.getString("dialog.export.start"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(exportButtonType, ButtonType.CANCEL);

        ComboBox<DecompilerTypeEnum> engineCombo = new ComboBox<>();
        engineCombo.getItems().addAll(DecompilerTypeEnum.values());
        engineCombo.setValue(parseEngine(appConfig.export.defaultEngine, currentEngine));
        engineCombo.setMaxWidth(Double.MAX_VALUE);

        ComboBox<ExportConfig.Format> formatCombo = new ComboBox<>();
        formatCombo.getItems().addAll(ExportConfig.Format.values());
        formatCombo.setValue(parseFormat(appConfig.export.defaultFormat));
        formatCombo.setMaxWidth(Double.MAX_VALUE);

        ComboBox<ExportConfig.ConflictPolicy> conflictCombo = new ComboBox<>();
        conflictCombo.getItems().addAll(ExportConfig.ConflictPolicy.values());
        conflictCombo.setValue(parseConflictPolicy(appConfig.export.conflictPolicy));
        conflictCombo.setMaxWidth(Double.MAX_VALUE);

        CheckBox resourcesCheck = new CheckBox(I18nUtil.getString("dialog.export.resources"));
        resourcesCheck.setSelected(appConfig.export.exportResources);

        TextField outputField = new TextField(appConfig.export.lastPath);
        outputField.setPromptText(I18nUtil.getString("dialog.export.path.prompt"));
        HBox.setHgrow(outputField, Priority.ALWAYS);

        javafx.scene.control.Button browseButton = new javafx.scene.control.Button(
                I18nUtil.getString("dialog.export.browse"));
        browseButton.setOnAction(event -> {
            Path selected = chooseOutput(owner, formatCombo.getValue(), outputField.getText());
            if (selected != null) {
                outputField.setText(selected.toString());
            }
        });

        HBox outputBox = new HBox(6, outputField, browseButton);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.setPadding(new Insets(12));
        form.addRow(0, new Label(I18nUtil.getString("dialog.export.engine")), engineCombo);
        form.addRow(1, new Label(I18nUtil.getString("dialog.export.format")), formatCombo);
        form.addRow(2, new Label(I18nUtil.getString("dialog.export.path")), outputBox);
        form.addRow(3, new Label(I18nUtil.getString("dialog.export.conflict")), conflictCombo);
        form.add(resourcesCheck, 1, 4);

        dialog.getDialogPane().setContent(form);

        Node exportButton = dialog.getDialogPane().lookupButton(exportButtonType);
        exportButton.addEventFilter(ActionEvent.ACTION, event -> {
            if (outputField.getText() == null || outputField.getText().isBlank()) {
                showInlineError(dialog, I18nUtil.getString("dialog.export.path.required"));
                event.consume();
            }
        });

        dialog.setResultConverter(button -> {
            if (button != exportButtonType) {
                return null;
            }
            Path outputPath = normalizeOutputPath(Path.of(outputField.getText()),
                    formatCombo.getValue());
            return new ExportConfig(outputPath, engineCombo.getValue(), formatCombo.getValue(),
                    conflictCombo.getValue(), resourcesCheck.isSelected());
        });

        return dialog.showAndWait();
    }

    public static ProgressHandle showProgress(Window owner) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(I18nUtil.getString("dialog.export.progress.title"));
        dialog.setHeaderText(null);

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        Label currentFileLabel = new Label(I18nUtil.getString("dialog.export.progress.preparing"));
        currentFileLabel.setMaxWidth(Double.MAX_VALUE);

        VBox content = new VBox(10, currentFileLabel, progressBar);
        content.setPadding(new Insets(12));
        dialog.getDialogPane().setContent(content);

        ButtonType cancelType = new ButtonType(
                I18nUtil.getString("dialog.export.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(cancelType);

        ProgressHandle handle = new ProgressHandle(dialog, progressBar, currentFileLabel);
        Node cancelButton = dialog.getDialogPane().lookupButton(cancelType);
        cancelButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            cancelButton.setDisable(true);
            currentFileLabel.setText(I18nUtil.getString("dialog.export.progress.canceling"));
            handle.cancel();
        });
        dialog.setOnCloseRequest(event -> {
            event.consume();
            cancelButton.setDisable(true);
            currentFileLabel.setText(I18nUtil.getString("dialog.export.progress.canceling"));
            handle.cancel();
        });

        dialog.show();
        return handle;
    }

    private static Path chooseOutput(Window owner, ExportConfig.Format format, String currentValue) {
        File initialDirectory = initialDirectory(currentValue);
        if (format == ExportConfig.Format.ZIP) {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(I18nUtil.getString("dialog.export.chooseZip"));
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("ZIP", "*.zip"));
            if (initialDirectory != null) {
                chooser.setInitialDirectory(initialDirectory);
            }
            File selected = chooser.showSaveDialog(owner);
            return selected == null ? null : normalizeOutputPath(selected.toPath(), format);
        }

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(I18nUtil.getString("dialog.export.chooseDir"));
        if (initialDirectory != null) {
            chooser.setInitialDirectory(initialDirectory);
        }
        File selected = chooser.showDialog(owner);
        return selected == null ? null : selected.toPath();
    }

    private static File initialDirectory(String currentValue) {
        if (currentValue == null || currentValue.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(currentValue);
            if (Files.isDirectory(path)) {
                return path.toFile();
            }
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null && Files.isDirectory(parent)) {
                return parent.toFile();
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static Path normalizeOutputPath(Path path, ExportConfig.Format format) {
        if (format != ExportConfig.Format.ZIP) {
            return path;
        }
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        if (fileName.toLowerCase().endsWith(".zip")) {
            return path;
        }
        return path.resolveSibling(fileName + ".zip");
    }

    private static void showInlineError(Dialog<?> dialog, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.WARNING, message);
        alert.initOwner(dialog.getDialogPane().getScene().getWindow());
        alert.setTitle(I18nUtil.getString("dialog.export.title"));
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private static ExportConfig.Format parseFormat(String value) {
        try {
            return ExportConfig.Format.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return ExportConfig.Format.DIR;
        }
    }

    private static DecompilerTypeEnum parseEngine(String value, DecompilerTypeEnum fallback) {
        try {
            return DecompilerTypeEnum.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return fallback != null ? fallback : DecompilerTypeEnum.VINEFLOWER;
        }
    }

    private static ExportConfig.ConflictPolicy parseConflictPolicy(String value) {
        try {
            return ExportConfig.ConflictPolicy.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return ExportConfig.ConflictPolicy.OVERWRITE;
        }
    }

    public static final class ProgressHandle {
        private final Dialog<Void> dialog;
        private final ProgressBar progressBar;
        private final Label currentFileLabel;
        private Runnable onCancel = () -> {
        };

        private ProgressHandle(Dialog<Void> dialog, ProgressBar progressBar,
                               Label currentFileLabel) {
            this.dialog = dialog;
            this.progressBar = progressBar;
            this.currentFileLabel = currentFileLabel;
        }

        public void setOnCancel(Runnable onCancel) {
            this.onCancel = onCancel != null ? onCancel : () -> {
            };
        }

        public void update(String currentPath, int percent) {
            progressBar.setProgress(Math.max(0, Math.min(100, percent)) / 100.0);
            currentFileLabel.setText(I18nUtil.getString(
                    "dialog.export.progress.current", percent, currentPath));
        }

        public void close() {
            dialog.close();
        }

        private void cancel() {
            onCancel.run();
        }
    }
}
