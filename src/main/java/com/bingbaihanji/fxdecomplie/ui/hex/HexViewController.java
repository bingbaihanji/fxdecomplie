package com.bingbaihanji.fxdecomplie.ui.hex;

import com.bingbaihanji.fxdecomplie.ui.hex.analyzer.DataAnalyzer;
import com.bingbaihanji.fxdecomplie.ui.hex.analyzer.DataAnalyzers;
import com.bingbaihanji.fxdecomplie.ui.hex.highlight.BuiltinHighlighter;
import com.bingbaihanji.fxdecomplie.ui.hex.model.PatternModel;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * HexView单例控制器用于管理全局的 HexView 配置和服务
 * <p>
 * 提供数据分析器({@link DataAnalyzer})和内建高亮器({@link BuiltinHighlighter})的注册与获取
 * 以及颜色主题、悬停回调等全局配置 
 * </p>
 *
 * @author BingBaiHanJi
 * @see HexView
 * @see DataAnalyzer
 * @see BuiltinHighlighter
 */
public final class HexViewController {

    private static final HexViewController INSTANCE = new HexViewController();

    /** 已注册的数据分析器列表(线程安全) */
    private final List<DataAnalyzer> analyzers = new CopyOnWriteArrayList<>();

    /** 已注册的内建高亮器列表(线程安全) */
    private final List<BuiltinHighlighter> highlighters = new CopyOnWriteArrayList<>();

    /** 选中区域背景色(半透明薄荷绿) */
    private final Color selectionColor = rgba(0x60, 0xC0, 0x80, 0x80);

    /** 提示框背景色(深灰半透明) */
    private final Color tooltipBgColor = rgba(0x1E, 0x1E, 0x22, 0xF0);

    /** 提示框边框色(灰) */
    private final Color tooltipBorderColor = Color.rgb(0x50, 0x50, 0x55, 1.0);

    // 以下为各类型数据块的标记颜色
    private final Color magicColor = rgba(0xF4, 0x47, 0x47, 0x40);      // Magic 标识
    private final Color versionColor = rgba(0x44, 0x9F, 0xD8, 0x40);    // 版本信息
    private final Color poolColor = rgba(0x6E, 0xC0, 0x77, 0x35);       // 常量池
    private final Color accessColor = rgba(0xF0, 0x90, 0x30, 0x45);     // 访问标志
    private final Color methodColor = rgba(0xA0, 0x70, 0xD0, 0x40);     // 方法
    private final Color fieldColor = rgba(0xD0, 0x90, 0xD0, 0x40);      // 字段
    private final Color interfaceColor = rgba(0x40, 0xC0, 0xC0, 0x40);  // 接口
    private final Color attributeColor = rgba(0xC0, 0xC0, 0x40, 0x40);  // 属性
    private final Color unknownColor = rgba(0x80, 0x80, 0x80, 0x30);    // 未知区域

    /**
     * 私有构造方法注册默认的数据分析器 
     */
    private HexViewController() {
        DataAnalyzers.registerDefaults(this);
    }

    /**
     * 辅助方法：创建带 Alpha 通道的 {@link Color} 
     *
     * @param r 红色分量 (0-255)
     * @param g 绿色分量 (0-255)
     * @param b 蓝色分量 (0-255)
     * @param a Alpha 分量 (0-255)
     * @return 对应的 {@link Color} 对象
     */
    private static Color rgba(int r, int g, int b, int a) {
        return Color.rgb(r, g, b, a / 255.0);
    }

    /**
     * 获取单例实例 
     *
     * @return 唯一的 {@link HexViewController} 实例
     */
    public static HexViewController getInstance() {
        return INSTANCE;
    }

    /**
     * 将指定的 {@link HexView} 附加到全局控制器 
     * <p>
     * 设置高亮样式提供器并绑定鼠标悬停回调 
     * </p>
     *
     * @param hexView 要附加的 HexView 实例
     */
    public void attach(HexView hexView) {
        hexView.getHighlights().setPatternProvider(addr -> hexView.getPatternModel().getColorAt(addr));
        hexView.setOnHover(this::onHover);
    }

    /**
     * 注册一个数据分析器 
     *
     * @param analyzer 要注册的 {@link DataAnalyzer} 实例
     */
    public void registerAnalyzer(DataAnalyzer analyzer) {
        analyzers.add(analyzer);
    }

    /**
     * 获取当前所有已注册的数据分析器列表 
     *
     * @return 不可修改的视图？实际返回的是可变的 {@link CopyOnWriteArrayList}但请勿直接修改
     */
    public List<DataAnalyzer> getAnalyzers() {
        return analyzers;
    }

    /**
     * 使用所有已注册的数据分析器对指定数据片段进行分析并合并结果 
     *
     * @param address  数据的起始绝对地址
     * @param fullData 完整的字节数组
     * @param offset   当前分析片段在 {@code fullData} 中的起始偏移
     * @param length   分析片段的长度
     * @return 所有分析器产生的 {@link DataAnalyzer.Result} 列表
     */
    public List<DataAnalyzer.Result> analyze(long address, byte[] fullData, int offset, int length) {
        List<DataAnalyzer.Result> results = new ArrayList<>();
        for (DataAnalyzer a : analyzers) {
            results.addAll(a.analyze(address, fullData, offset, length));
        }
        return results;
    }

    /**
     * 注册一个内建高亮器 
     *
     * @param highlighter 要注册的 {@link BuiltinHighlighter} 实例
     */
    public void registerHighlighter(BuiltinHighlighter highlighter) {
        highlighters.add(highlighter);
    }

    /**
     * 获取所有已注册的内建高亮器列表 
     *
     * @return 高亮器列表(可修改但不建议直接修改)
     */
    public List<BuiltinHighlighter> getHighlighters() {
        return highlighters;
    }

    /**
     * 根据数据提供者内容应用高亮到指定的 {@link PatternModel} 
     * <p>
     * 遍历已注册的高亮器选择第一个匹配的高亮器执行高亮操作 
     * 若 {@code patternModel} 为 {@code null} 则直接返回 
     * </p>
     *
     * @param provider      数据提供者用于检测匹配条件
     * @param patternModel  目标模式模型高亮结果将填充至此
     */
    public void applyHighlights(HexDataProvider provider, PatternModel patternModel) {
        if (patternModel == null) {
            return;
        }
        patternModel.clear();
        for (BuiltinHighlighter h : highlighters) {
            if (h.matches(provider)) {
                h.highlight(provider, patternModel);
                break;
            }
        }
    }

    /**
     * 鼠标悬停回调(当前为空实现) 
     * <p>
     * 子类可覆写此方法以自定义悬停行为 
     * </p>
     *
     * @param address 悬停位置的地址
     * @param size    悬停区域的字节大小
     */
    private void onHover(long address, int size) {
        // 预留扩展暂不实现
    }

    // ---------- 颜色获取方法 ----------

    public Color getSelectionColor() {
        return selectionColor;
    }

    public Color getMagicColor() {
        return magicColor;
    }

    public Color getVersionColor() {
        return versionColor;
    }

    public Color getPoolColor() {
        return poolColor;
    }

    public Color getAccessColor() {
        return accessColor;
    }

    public Color getMethodColor() {
        return methodColor;
    }

    public Color getFieldColor() {
        return fieldColor;
    }

    public Color getInterfaceColor() {
        return interfaceColor;
    }

    public Color getAttributeColor() {
        return attributeColor;
    }

    public Color getUnknownColor() {
        return unknownColor;
    }
}