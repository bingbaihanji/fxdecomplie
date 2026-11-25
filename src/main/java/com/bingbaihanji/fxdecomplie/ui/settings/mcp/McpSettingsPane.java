package com.bingbaihanji.fxdecomplie.ui.settings.mcp;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.mcp.McpServerManager;
import com.bingbaihanji.fxdecomplie.mcp.McpServerStatus;
import com.bingbaihanji.fxdecomplie.mcp.clientconfig.McpClientConfigBuilder;
import com.bingbaihanji.fxdecomplie.util.i18n.I18nUtil;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 设置页 MCP 面板：服务开关、监听地址/端口、协议、Token、导出开关、
 * 运行状态、健康检查和客户端 JSON。
 *
 * @author bingbaihanji
 */
public final class McpSettingsPane extends ScrollPane {

    private final AppConfig draft;
    private final McpServerManager manager;

    private final CheckBox enableCheck = new CheckBox();
    private final ComboBox<String> bindHostCombo = new ComboBox<>();
    private final Spinner<Integer> portSpinner = new Spinner<>();
    private final CheckBox streamableHttpCheck = new CheckBox();
    private final CheckBox legacySseCheck = new CheckBox();
    private final PasswordField tokenPassword = new PasswordField();
    private final TextField tokenPlain = new TextField();
    private final CheckBox showTokenCheck = new CheckBox();
    private final Button copyTokenButton = new Button();
    private final Button regenerateTokenButton = new Button();
    private final CheckBox exportEnabledCheck = new CheckBox();
    private final TextField advertisedHostField = new TextField();
    private final Label statusLabel = new Label();
    private final Button testButton = new Button();
    private final ComboBox<String> clientProtocolCombo = new ComboBox<>();
    private final TextArea clientJsonArea = new TextArea();
    private final Button copyJsonButton = new Button();

    private final Timeline statusTimeline;

    public McpSettingsPane(AppConfig draft, McpServerManager manager) {
        this.draft = draft;
        this.manager = manager;
        setFitToWidth(true);
        setContent(buildContent());
        bindValues();
        refreshClientJson();
        statusTimeline = new Timeline(new KeyFrame(Duration.seconds(2), e -> refreshStatus()));
        statusTimeline.setCycleCount(Animation.INDEFINITE);
        statusTimeline.play();
        refreshStatus();
    }

    private VBox buildContent() {
        Label title = new Label(I18nUtil.getStringOrDefault("settings.mcp", "MCP 服务"));
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        enableCheck.setText(I18nUtil.getStringOrDefault("settings.mcp.enabled", "启用 MCP 服务"));

        bindHostCombo.getItems().addAll(localAddressOptions());
        bindHostCombo.setPrefWidth(260);

        SpinnerValueFactory.IntegerSpinnerValueFactory portFactory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1024, 65535, 8765);
        portSpinner.setValueFactory(portFactory);
        portSpinner.setEditable(true);
        portSpinner.setPrefWidth(140);

        streamableHttpCheck.setText(I18nUtil.getStringOrDefault(
                "settings.mcp.streamableHttp", "Streamable HTTP（推荐）"));
        legacySseCheck.setText(I18nUtil.getStringOrDefault(
                "settings.mcp.legacySse", "旧版 HTTP+SSE"));

        showTokenCheck.setText(I18nUtil.getStringOrDefault(
                "settings.mcp.token.show", "显示 Token"));
        copyTokenButton.setText(I18nUtil.getStringOrDefault(
                "settings.mcp.token.copy", "复制 Token"));
        regenerateTokenButton.setText(I18nUtil.getStringOrDefault(
                "settings.mcp.token.regenerate", "重新生成"));
        exportEnabledCheck.setText(I18nUtil.getStringOrDefault(
                "settings.mcp.exportEnabled", "允许 MCP 导出工具写文件"));

        advertisedHostField.setPrefWidth(260);
        statusLabel.setWrapText(true);
        testButton.setText(I18nUtil.getStringOrDefault("settings.mcp.test", "测试连接"));

        String httpLabel = I18nUtil.getStringOrDefault(
                "settings.mcp.streamableHttp", "Streamable HTTP");
        String sseLabel = I18nUtil.getStringOrDefault(
                "settings.mcp.legacySse", "Legacy HTTP+SSE");
        clientProtocolCombo.getItems().addAll(httpLabel, sseLabel);
        clientProtocolCombo.setValue(httpLabel);
        clientJsonArea.setEditable(false);
        clientJsonArea.setWrapText(false);
        clientJsonArea.setPrefRowCount(12);
        clientJsonArea.setStyle("-fx-font-family: 'Consolas', 'JetBrains Mono', monospace;");
        copyJsonButton.setText(I18nUtil.getStringOrDefault(
                "settings.mcp.copyJson", "复制 JSON"));

        GridPane serviceGrid = new GridPane();
        serviceGrid.setHgap(10);
        serviceGrid.setVgap(8);
        int row = 0;
        serviceGrid.add(enableCheck, 0, row++, 2, 1);
        serviceGrid.add(new Label(I18nUtil.getStringOrDefault(
                "settings.mcp.bindHost", "监听地址")), 0, row);
        serviceGrid.add(bindHostCombo, 1, row++);
        serviceGrid.add(new Label(I18nUtil.getStringOrDefault(
                "settings.mcp.port", "端口")), 0, row);
        serviceGrid.add(portSpinner, 1, row++);
        serviceGrid.add(new Label(I18nUtil.getStringOrDefault(
                "settings.mcp.protocol", "协议")), 0, row);
        VBox protocolBox = new VBox(6, streamableHttpCheck, legacySseCheck);
        serviceGrid.add(protocolBox, 1, row++);
        serviceGrid.add(new Label(I18nUtil.getStringOrDefault(
                "settings.mcp.token", "访问 Token")), 0, row);
        VBox tokenBox = new VBox(6, tokenPassword, tokenPlain, showTokenCheck,
                new VBox(6, copyTokenButton, regenerateTokenButton));
        serviceGrid.add(tokenBox, 1, row++);
        serviceGrid.add(new Label(I18nUtil.getStringOrDefault(
                "settings.mcp.exportEnabled", "导出")), 0, row);
        serviceGrid.add(new VBox(6, exportEnabledCheck,
                warning(I18nUtil.getStringOrDefault("settings.mcp.exportWarning",
                        "启用后 AI 可向任意绝对路径写文件，请只连接可信 agent。"))), 1, row++);
        serviceGrid.add(new Label(I18nUtil.getStringOrDefault(
                "settings.mcp.advertisedHost", "对外地址")), 0, row);
        serviceGrid.add(advertisedHostField, 1, row++);
        serviceGrid.add(new Label(I18nUtil.getStringOrDefault(
                "settings.mcp.status", "运行状态")), 0, row);
        serviceGrid.add(statusLabel, 1, row++);
        serviceGrid.add(testButton, 1, row);

        TitledPane servicePane = new TitledPane(
                I18nUtil.getStringOrDefault("settings.mcp", "MCP 服务"), serviceGrid);
        servicePane.setCollapsible(false);

        VBox clientBox = new VBox(8,
                new Label(I18nUtil.getStringOrDefault(
                        "settings.mcp.clientConfig", "客户端配置 JSON")),
                clientProtocolCombo,
                clientJsonArea,
                copyJsonButton);
        VBox.setVgrow(clientJsonArea, Priority.ALWAYS);
        TitledPane clientPane = new TitledPane(
                I18nUtil.getStringOrDefault("settings.mcp.clientConfig", "客户端配置"),
                clientBox);
        clientPane.setCollapsible(false);

        Label lanWarning = warning(I18nUtil.getStringOrDefault("settings.mcp.lanWarning",
                "绑定 0.0.0.0 会把服务和源码暴露到局域网，仅限可信网络。"));
        Label tokenHint = new Label(I18nUtil.getStringOrDefault(
                "settings.mcp.tokenHint", "Token 保存在 config/config.json，请勿分享该文件。"));

        VBox root = new VBox(12, title, servicePane, lanWarning, tokenHint, clientPane);
        root.setPadding(new Insets(12));
        return root;
    }

    private static Label warning(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-text-fill: #f44747;");
        return label;
    }

    private void bindValues() {
        var mcp = draft.mcp();
        enableCheck.setSelected(mcp.enabled());
        if (!bindHostCombo.getItems().contains(mcp.bindHost())) {
            bindHostCombo.getItems().add(0, mcp.bindHost());
        }
        bindHostCombo.setValue(mcp.bindHost());
        bindHostCombo.valueProperty().addListener((obs, old, value) -> {
            if (value == null) {
                return;
            }
            String advertised = advertisedHostField.getText() == null
                    ? "" : advertisedHostField.getText().trim();
            if (advertised.isBlank() || advertised.equals("127.0.0.1")) {
                if (!"0.0.0.0".equals(value) && !"127.0.0.1".equals(value)) {
                    advertisedHostField.setText(value);
                } else if ("0.0.0.0".equals(value)) {
                    advertisedHostField.setText(firstLanAddress());
                }
            }
            refreshClientJson();
        });
        portSpinner.getValueFactory().setValue(mcp.port());
        streamableHttpCheck.setSelected(mcp.streamableHttpEnabled());
        legacySseCheck.setSelected(mcp.legacySseEnabled());
        String initialToken = mcp.authToken();
        if (mcp.enabled() && (initialToken == null || initialToken.isBlank())) {
            initialToken = newToken();
        }
        tokenPassword.setText(initialToken);
        tokenPlain.setText(initialToken);
        tokenPlain.setVisible(false);
        tokenPlain.setManaged(false);
        exportEnabledCheck.setSelected(mcp.exportEnabled());
        String advertised = mcp.advertisedHost();
        if (advertised == null || advertised.isBlank()
                || (advertised.equals("127.0.0.1")
                && !mcp.bindHost().equals("127.0.0.1"))) {
            advertised = "0.0.0.0".equals(mcp.bindHost())
                    ? firstLanAddress() : mcp.bindHost();
        }
        advertisedHostField.setText(advertised);

        tokenPlain.textProperty().bindBidirectional(tokenPassword.textProperty());
        showTokenCheck.selectedProperty().addListener((obs, old, show) -> {
            tokenPlain.setVisible(show);
            tokenPlain.setManaged(show);
            tokenPassword.setVisible(!show);
            tokenPassword.setManaged(!show);
        });
        copyTokenButton.setOnAction(e -> copyText(tokenPassword.getText()));
        regenerateTokenButton.setOnAction(e -> {
            String token = newToken();
            tokenPassword.setText(token);
            tokenPlain.setText(token);
            refreshClientJson();
        });
        testButton.setOnAction(e -> testConnection());
        clientProtocolCombo.valueProperty().addListener((obs, old, value) -> refreshClientJson());
        copyJsonButton.setOnAction(e -> copyText(clientJsonArea.getText()));
        enableCheck.selectedProperty().addListener((obs, old, enabled) -> {
            if (enabled && (tokenPassword.getText() == null
                    || tokenPassword.getText().isBlank())) {
                String token = newToken();
                tokenPassword.setText(token);
                tokenPlain.setText(token);
            }
            refreshClientJson();
        });
        streamableHttpCheck.selectedProperty().addListener((obs, old, value) -> refreshClientJson());
        legacySseCheck.selectedProperty().addListener((obs, old, value) -> refreshClientJson());
        portSpinner.valueProperty().addListener((obs, old, value) -> refreshClientJson());
        advertisedHostField.textProperty().addListener((obs, old, value) -> refreshClientJson());
        tokenPassword.textProperty().addListener((obs, old, value) -> refreshClientJson());
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return "fxd_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void copyText(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text == null ? "" : text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private static String firstLanAddress() {
        for (String value : localAddressOptions()) {
            if (!value.equals("127.0.0.1") && !value.equals("0.0.0.0")) {
                return value;
            }
        }
        return "127.0.0.1";
    }

    private static List<String> localAddressOptions() {
        List<String> values = new ArrayList<>();
        values.add("127.0.0.1");
        values.add("0.0.0.0");
        try {
            for (NetworkInterface networkInterface : Collections.list(
                    NetworkInterface.getNetworkInterfaces())) {
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (address.isLoopbackAddress() || !address.getHostAddress()
                            .matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
                        continue;
                    }
                    if (!values.contains(address.getHostAddress())) {
                        values.add(address.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
            // 无法枚举网卡时只保留本地回环与 0.0.0.0
        }
        return values;
    }

    private void refreshClientJson() {
        String protocol = clientProtocolCombo.getValue();
        boolean legacySse = protocol != null && protocol.toLowerCase(Locale.ROOT).contains("sse");
        clientJsonArea.setText(McpClientConfigBuilder.buildJson(currentDraft(), legacySse));
    }

    private AppConfig currentDraft() {
        AppConfig copy = draft.copy();
        var mcp = copy.mcp();
        mcp.enabled(enableCheck.isSelected());
        mcp.bindHost(bindHostCombo.getValue());
        mcp.port(portSpinner.getValue());
        mcp.streamableHttpEnabled(streamableHttpCheck.isSelected());
        mcp.legacySseEnabled(legacySseCheck.isSelected());
        mcp.authToken(tokenPassword.getText() == null ? "" : tokenPassword.getText().trim());
        mcp.exportEnabled(exportEnabledCheck.isSelected());
        mcp.advertisedHost(advertisedHost());
        return copy;
    }

    private String advertisedHost() {
        String value = advertisedHostField.getText() == null
                ? "" : advertisedHostField.getText().trim();
        if (!value.isBlank()) {
            return value;
        }
        String bindHost = bindHostCombo.getValue();
        return "0.0.0.0".equals(bindHost) ? firstLanAddress()
                : bindHost == null || bindHost.isBlank() ? "127.0.0.1" : bindHost;
    }

    private void refreshStatus() {
        McpServerStatus status = manager.status();
        String prefix = status.running()
                ? I18nUtil.getStringOrDefault("settings.mcp.status.running", "运行中")
                : I18nUtil.getStringOrDefault("settings.mcp.status.stopped", "已停止");
        String text = prefix + " " + status.bindHost() + ":" + status.port();
        if (!status.running() && !"stopped".equals(status.message())) {
            text += " — " + status.message();
        }
        statusLabel.setText(text);
        statusLabel.setStyle(status.running() ? "-fx-text-fill: #89d185;" : "-fx-text-fill: #cccccc;");
    }

    private void testConnection() {
        String host = bindHostCombo.getValue();
        if (host == null || "0.0.0.0".equals(host) || host.isBlank()) {
            host = advertisedHost();
        }
        int port = portSpinner.getValue();
        testButton.setDisable(true);
        statusLabel.setText(I18nUtil.getStringOrDefault("settings.mcp.testing", "正在测试连接..."));
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(3)).build();
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + host + ":" + port + "/health"))
                    .timeout(java.time.Duration.ofSeconds(3))
                    .GET().build();
        } catch (IllegalArgumentException e) {
            testButton.setDisable(false);
            statusLabel.setText(I18nUtil.getStringOrDefault(
                    "settings.mcp.test.failed", "连接失败"));
            statusLabel.setStyle("-fx-text-fill: #f44747;");
            return;
        }
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200)
                .exceptionally(error -> false)
                .thenAccept(ok -> Platform.runLater(() -> {
                    testButton.setDisable(false);
                    statusLabel.setText(ok
                            ? I18nUtil.getStringOrDefault("settings.mcp.test.ok", "连接成功")
                            : I18nUtil.getStringOrDefault("settings.mcp.test.failed", "连接失败"));
                    statusLabel.setStyle(ok ? "-fx-text-fill: #89d185;" : "-fx-text-fill: #f44747;");
                }));
    }

    public void applyTo(AppConfig target) {
        if (enableCheck.isSelected() && !streamableHttpCheck.isSelected()
                && !legacySseCheck.isSelected()) {
            throw new IllegalArgumentException(I18nUtil.getStringOrDefault(
                    "settings.mcp.invalidProtocol", "至少启用一种协议"));
        }
        var mcp = target.mcp();
        mcp.enabled(enableCheck.isSelected());
        mcp.bindHost(bindHostCombo.getValue());
        mcp.port(portSpinner.getValue());
        mcp.streamableHttpEnabled(streamableHttpCheck.isSelected());
        mcp.legacySseEnabled(legacySseCheck.isSelected());
        String token = tokenPassword.getText() == null ? "" : tokenPassword.getText().trim();
        if (mcp.enabled() && token.isBlank()) {
            token = newToken();
        }
        mcp.authToken(token);
        mcp.exportEnabled(exportEnabledCheck.isSelected());
        mcp.advertisedHost(advertisedHost());
    }

    public void stopPolling() {
        statusTimeline.stop();
    }
}
