package com.bingbaihanji.fxdecomplie.ui.view;

import com.bingbaihanji.fxdecomplie.ui.theme.VsCodeThemeLoader;
import com.bingbaihanji.fxdecomplie.ui.theme.VsCodeThemeLoader.ThemeData;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

/**
 * Hex 表格视图 — WebView + &lt;table&gt; + CSS 变量列宽 + sticky 表头 + 拖拽分隔线。
 *
 * <p>列宽由三个 CSS 自定义属性控制（--col-off/--col-hex/--col-asc），
 * 拖拽表头右侧手柄时 JS 更新对应变量，所有行同步变化。</p>
 *
 * @author bingbaihanji
 */
public class HexTabView extends StackPane {

    private static final int BYTES_PER_ROW = 16;
    private static final int MAX_BYTES = 512 * 1024;

    private static final String HTML_TEMPLATE = """
            <!DOCTYPE html>
            <html><head><meta charset="UTF-8">
            <style>
            :root{
              --c-off:100px;
              --c-hex:400px;
              --c-asc:180px;
            }
            *{margin:0;padding:0;box-sizing:border-box;}
            body{background:%1$s;padding:0;overflow:auto;
                 font-family:'Fira Code',Consolas,'Courier New',monospace;
                 font-size:13px;line-height:1.4;color:%2$s;}
            table{table-layout:fixed;
                  width:calc(var(--c-off) + var(--c-hex) + var(--c-asc));
                  border-collapse:collapse;}
            td,th{padding:1px 8px;white-space:pre;overflow:hidden;}
            /* 三列宽度统一绑定 CSS 变量 */
            td:nth-child(1),th:nth-child(1){width:var(--c-off);}
            td:nth-child(2),th:nth-child(2){width:var(--c-hex);}
            td:nth-child(3),th:nth-child(3){width:var(--c-asc);}
            /* 表头 — sticky 固定，作为拖拽手柄的定位父级 */
            thead th{position:sticky;top:0;z-index:5;
                     border:1px solid %3$s;background:%4$s;
                     font-weight:bold;color:%5$s;text-align:left;}
            /* 列间竖线 */
            .roff{border-right:1px solid %3$s;}
            /* 奇偶行 */
            .ev td{border-bottom:1px solid %6$s;}
            .od td{border-bottom:1px solid %6$s;background:%7$s;}
            /* 拖拽手柄 — 8px 宽，中心对齐 th 右边框线 */
            .rhandle{position:absolute;right:-5px;top:0;width:8px;height:100%%;
                     cursor:col-resize;z-index:10;}
            .rhandle:hover,.rhandle.active{background:rgba(255,255,255,0.1);}
            </style></head><body>
            <table>
            <thead><tr>
              <th class="roff">Offset<span class="rhandle" data-col="off"></span></th>
              <th class="roff">Hex<span class="rhandle" data-col="hex"></span></th>
              <th>ASCII<span class="rhandle" data-col="asc"></span></th>
            </tr></thead>
            <tbody>
            %8$s
            </tbody></table>
            <script>
            var root=document.documentElement;
            var props={off:'--c-off',hex:'--c-hex',asc:'--c-asc'};
            var drag=null;
            document.querySelectorAll('.rhandle').forEach(function(h){
              h.addEventListener('mousedown',function(e){
                e.preventDefault();e.stopPropagation();
                var p=props[this.getAttribute('data-col')];
                var cur=parseFloat(getComputedStyle(root).getPropertyValue(p));
                drag={prop:p,startW:cur,sx:e.clientX,el:this};
                this.classList.add('active');
              });
            });
            document.addEventListener('mousemove',function(e){
              if(!drag) return;
              var nw=Math.max(40, drag.startW+(e.clientX-drag.sx));
              root.style.setProperty(drag.prop, nw+'px');
            });
            document.addEventListener('mouseup',function(){
              if(drag){drag.el.classList.remove('active');drag=null;}
            });
            </script>
            </body></html>
            """;

    private final WebEngine engine;
    private ThemeData theme;
    private byte[] currentData;

    public HexTabView(ThemeData theme) {
        this.theme = theme != null ? theme : VsCodeThemeLoader.defaultDark();
        WebView webView = new WebView();
        this.engine = webView.getEngine();
        webView.setContextMenuEnabled(false);
        webView.setMinHeight(60);
        getChildren().add(webView);
        load(null);
    }

    public void setTheme(ThemeData newTheme) {
        if (newTheme == null) return;
        this.theme = newTheme;
        if (currentData != null) render();
    }

    public void load(byte[] data) {
        this.currentData = data;
        render();
    }

    private void render() {
        if (currentData == null || currentData.length == 0) {
            engine.loadContent("<html><body></body></html>");
            return;
        }
        engine.loadContent(formatHtml(currentData, theme));
    }

    // === 颜色 ===

    static String toHex(Color c) {
        return String.format("#%02X%02X%02X", (int) (c.getRed() * 255), (int) (c.getGreen() * 255), (int) (c.getBlue() * 255));
    }

    static String lighten(Color c, double f) {
        return String.format("#%02X%02X%02X", (int) ((c.getRed() + (1 - c.getRed()) * f) * 255), (int) ((c.getGreen() + (1 - c.getGreen()) * f) * 255), (int) ((c.getBlue() + (1 - c.getBlue()) * f) * 255));
    }

    static String darken(Color c, double f) {
        return String.format("#%02X%02X%02X", (int) (c.getRed() * (1 - f) * 255), (int) (c.getGreen() * (1 - f) * 255), (int) (c.getBlue() * (1 - f) * 255));
    }

    static HexColors deriveColors(ThemeData theme) {
        Color bg = theme.editorBackground();
        Color fg = theme.editorForeground();
        if (isDark(bg)) {
            return new HexColors(toHex(bg), toHex(fg), lighten(bg, 0.12), lighten(bg, 0.08), darken(fg, 0.35), lighten(bg, 0.06), lighten(bg, 0.03));
        } else {
            return new HexColors(toHex(bg), toHex(fg), darken(bg, 0.15), darken(bg, 0.08), lighten(fg, 0.30), darken(bg, 0.04), darken(bg, 0.025));
        }
    }

    private static boolean isDark(Color c) {
        return 0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue() < 0.5;
    }

    // === HTML ===

    static String formatHtml(byte[] data, ThemeData theme) {
        HexColors c = deriveColors(theme);
        int limit = Math.min(data.length, MAX_BYTES);
        int totalRows = (limit + BYTES_PER_ROW - 1) / BYTES_PER_ROW;

        StringBuilder rows = new StringBuilder(4096);
        for (int row = 0; row < totalRows; row++) {
            int offset = row * BYTES_PER_ROW;
            String cls = (row & 1) == 0 ? "ev" : "od";
            rows.append("<tr class='").append(cls).append("'>");

            rows.append("<td class='roff'>")
                    .append(String.format("%08X", offset)).append("</td>");

            rows.append("<td class='roff'>");
            for (int i = 0; i < BYTES_PER_ROW; i++) {
                if (i == 8) rows.append(' ');
                int pos = offset + i;
                if (pos < limit) {
                    rows.append(String.format("%02X ", data[pos] & 0xFF));
                } else {
                    rows.append("   ");
                }
            }
            rows.append("</td>");

            rows.append("<td>");
            for (int i = 0; i < BYTES_PER_ROW; i++) {
                int pos = offset + i;
                if (pos < limit) {
                    int v = data[pos] & 0xFF;
                    rows.append(v >= 0x20 && v < 0x7F ? (char) v : '.');
                } else {
                    rows.append(' ');
                }
            }
            rows.append("</td></tr>");
        }

        return HTML_TEMPLATE.formatted(
                c.bodyBg, c.text, c.border, c.headerBg,
                c.secondaryText, c.gridLine, c.altRowBg,
                rows.toString());
    }

    public record HexColors(String bodyBg, String text, String border, String headerBg,
                            String secondaryText, String gridLine, String altRowBg) {
    }
}
