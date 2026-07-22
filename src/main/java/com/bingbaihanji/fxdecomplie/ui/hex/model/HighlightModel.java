package com.bingbaihanji.fxdecomplie.ui.hex.model;

import javafx.scene.paint.Color;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 高亮模型,用于管理十六进制视图中字节的前景色和背景色 
 * <p>
 * 支持两种颜色来源：
 * <ul>
 *   <li><b>回调(Callback)</b>：动态计算颜色,适用于需要根据上下文(如地址、数据值)实时决定颜色的场景 </li>
 *   <li><b>区域(Region)</b>：静态范围着色,适用于已知固定区间需要统一颜色标记的场景 </li>
 * </ul>
 * 背景色支持多层叠加(通过透明度混合),前景色则按优先级(回调优先于区域)取第一个有效值 
 * 此外,可启用“灰色零字节”功能,将值为 0x00 的字节显示为灰色(半透明) 
 * </p>
 *
 * @author BingBaiHanJi
 * @see HighlightCallback
 * @see PatternColorProvider
 * @see HighlightRegion
 */
public class HighlightModel {

    // ---------- 颜色来源列表(线程安全) ----------
    private final List<HighlightCallback> foregroundCallbacks = new CopyOnWriteArrayList<>();
    private final List<HighlightCallback> backgroundCallbacks = new CopyOnWriteArrayList<>();
    private final List<HighlightRegion> foregroundRegions = new CopyOnWriteArrayList<>();
    private final List<HighlightRegion> backgroundRegions = new CopyOnWriteArrayList<>();

    /** 模式颜色提供者(通常来自 {@link PatternModel}),用于背景色叠加 */
    private volatile PatternColorProvider patternProvider;

    /** 是否将零字节(0x00)显示为灰色(半透明) */
    private volatile boolean grayOutZero = true;

    /**
     * 将两种颜色进行 Alpha 混合(上层颜色置于下方颜色之上) 
     *
     * @param below 底层颜色
     * @param above 上层颜色(含透明度)
     * @return 混合后的颜色
     */
    private static Color blend(Color below, Color above) {
        double a = above.getOpacity();
        double invA = 1.0 - a;
        return new Color(above.getRed() * a + below.getRed() * invA,
                above.getGreen() * a + below.getGreen() * invA,
                above.getBlue() * a + below.getBlue() * invA,
                Math.min(1.0, below.getOpacity() + a));
    }

    // ---------- 前景色管理 ----------

    /**
     * 添加一个前景色回调,用于动态计算特定地址/数据的前景色 
     * <p>
     * 回调会按添加顺序依次尝试,返回第一个非空 {@link Optional} 结果 
     * </p>
     *
     * @param cb 前景色回调
     */
    public void addForegroundCallback(HighlightCallback cb) {
        foregroundCallbacks.add(cb);
    }

    /**
     * 移除一个已添加的前景色回调 
     *
     * @param cb 要移除的回调
     */
    public void removeForegroundCallback(HighlightCallback cb) {
        foregroundCallbacks.remove(cb);
    }

    /**
     * 添加一个静态前景色区域,指定区间内的所有字节使用同一颜色 
     *
     * @param addr  起始地址(包含)
     * @param size  区间长度(字节)
     * @param color 颜色
     */
    public void addForegroundRegion(long addr, long size, Color color) {
        foregroundRegions.add(new HighlightRegion(addr, size, color));
    }

    /**
     * 获取指定地址和数据字节的前景色 
     * <p>
     * 优先级：回调 > 前景区域 > 灰色零字节(若启用) > 空(默认颜色由渲染器决定) 
     * </p>
     *
     * @param address 地址
     * @param data    该地址处的字节值
     * @return 前景色(若存在),否则返回 {@link Optional#empty()}
     */
    public Optional<Color> getForegroundColor(long address, byte data) {
        // 回调优先
        for (var cb : foregroundCallbacks) {
            var c = cb.apply(address, data);
            if (c.isPresent()) {
                return c;
            }
        }
        // 静态区域
        for (var r : foregroundRegions) {
            if (r.contains(address)) {
                return Optional.of(r.color);
            }
        }
        // 灰色零字节
        if (grayOutZero && data == 0x00) {
            return Optional.of(Color.gray(0.5));
        }
        return Optional.empty();
    }

    // ---------- 背景色管理 ----------

    /**
     * 添加一个背景色回调 
     *
     * @param cb 背景色回调
     */
    public void addBackgroundCallback(HighlightCallback cb) {
        backgroundCallbacks.add(cb);
    }

    /**
     * 移除背景色回调 
     *
     * @param cb 要移除的回调
     */
    public void removeBackgroundCallback(HighlightCallback cb) {
        backgroundCallbacks.remove(cb);
    }

    /**
     * 添加一个静态背景色区域 
     *
     * @param addr  起始地址
     * @param size  区间长度
     * @param color 颜色
     */
    public void addBackgroundRegion(long addr, long size, Color color) {
        backgroundRegions.add(new HighlightRegion(addr, size, color));
    }

    /**
     * 设置模式颜色提供者(通常来自 {@link PatternModel}) 
     * <p>
     * 提供的颜色将作为背景色的基础层,与其他背景来源(区域、回调)叠加混合 
     * </p>
     *
     * @param p 颜色提供者,可为 {@code null}
     */
    public void setPatternProvider(PatternColorProvider p) {
        this.patternProvider = p;
    }

    /**
     * 获取指定地址的背景色(支持多层叠加混合) 
     * <p>
     * 叠加顺序(从底层到上层)：
     * <ol>
     *   <li>模式颜色提供者({@link PatternColorProvider})</li>
     *   <li>静态背景区域(按添加顺序)</li>
     *   <li>背景回调(按添加顺序)</li>
     * </ol>
     * 每一层都通过 Alpha 混合叠加到已有颜色上,最终返回合成的颜色 
     * </p>
     *
     * @param address 地址
     * @return 背景色(若存在),否则返回 {@link Optional#empty()}
     */
    public Optional<Color> getBackgroundColor(long address) {
        Color result = null;

        // 1. 模式颜色提供者
        PatternColorProvider pp = patternProvider;
        if (pp != null) {
            Color pc = pp.getColorAt(address);
            if (pc != null) {
                result = pc;
            }
        }

        // 2. 静态背景区域(按顺序叠加)
        for (var r : backgroundRegions) {
            if (r.contains(address)) {
                result = result == null ? r.color : blend(result, r.color);
            }
        }

        // 3. 背景回调(按顺序叠加)
        for (var cb : backgroundCallbacks) {
            var c = cb.apply(address, (byte) 0);
            if (c.isPresent()) {
                result = result == null ? c.get() : blend(result, c.get());
            }
        }

        return Optional.ofNullable(result);
    }

    // ---------- 属性访问 ----------

    /**
     * 返回是否启用“灰色零字节”功能 
     *
     * @return {@code true} 表示零字节显示为灰色
     */
    public boolean isGrayOutZero() {
        return grayOutZero;
    }

    /**
     * 设置是否启用“灰色零字节”功能 
     *
     * @param v {@code true} 启用,{@code false} 禁用
     */
    public void setGrayOutZero(boolean v) {
        this.grayOutZero = v;
    }

    /**
     * 清除所有高亮(回调、区域、提供者不自动清除) 
     * <p>
     * 注意：此方法不会清除 {@link #patternProvider},如需清除需单独设置为 {@code null}
     * </p>
     */
    public void clearAll() {
        foregroundCallbacks.clear();
        backgroundCallbacks.clear();
        foregroundRegions.clear();
        backgroundRegions.clear();
    }

    // ---------- 内部接口和记录 ----------

    /**
     * 高亮回调函数接口,用于动态计算颜色 
     * <p>
     * 实现类可根据地址和字节值返回一个可选的 {@link Color}
     * </p>
     */
    @FunctionalInterface
    public interface HighlightCallback {
        /**
         * 计算指定地址和字节值对应的颜色 
         *
         * @param address 地址
         * @param data    字节值
         * @return 可选的颜色(若返回空,则继续使用下一级颜色源)
         */
        Optional<Color> apply(long address, byte data);
    }

    /**
     * 模式颜色提供者接口,用于从外部模型(如 {@link PatternModel})获取区域颜色 
     */
    @FunctionalInterface
    public interface PatternColorProvider {
        /**
         * 获取指定地址对应的颜色(可能来自模型中的区域) 
         *
         * @param address 地址
         * @return 颜色,若不存在则返回 {@code null}
         */
        Color getColorAt(long address);
    }

    /**
     * 高亮区域记录,表示一段连续地址区间使用同一颜色 
     *
     * @param address 起始地址(包含)
     * @param size    区间长度(字节)
     * @param color   颜色
     */
    public record HighlightRegion(long address, long size, Color color) {
        /**
         * 判断指定地址是否位于该区域内 
         *
         * @param addr 待检查的地址
         * @return 若 {@code address <= addr < address + size} 则返回 {@code true}
         */
        public boolean contains(long addr) {
            return addr >= address && addr < address + size;
        }
    }
}