package com.bingbaihanji.fxdecomplie.ui.hex.model;

import com.bingbaihanji.fxdecomplie.ui.hex.HexView;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.SimpleLongProperty;

/**
 * 选区模型,用于管理十六进制视图中的选区(选择范围)和光标位置 
 * <p>
 * 该模型维护三个关键状态：
 * <ul>
 *   <li><b>选区起始</b>(selectionStart)和<b>选区结束</b>(selectionEnd)：定义当前选中的连续地址区间 </li>
 *   <li><b>光标位置</b>(cursorPosition)：表示当前焦点所在的地址 </li>
 * </ul>
 * 支持通过 {@link #startSelection(long)}、{@link #extendSelection(long)} 和 {@link #select(long, long)}
 * 操作选区,并提供了查询选区大小、是否包含某地址等便捷方法 
 * 所有状态均以 JavaFX 属性({@link LongProperty})形式暴露,便于 UI 绑定和监听 
 * </p>
 *
 * @author BingBaiHanJi
 * @see HexView
 */
public class SelectionModel {

    /** 选区起始地址(-1 表示无选区) */
    private final LongProperty selectionStart = new SimpleLongProperty(-1);

    /** 选区结束地址(-1 表示无选区) */
    private final LongProperty selectionEnd = new SimpleLongProperty(-1);

    /** 光标当前位置(-1 表示无光标) */
    private final LongProperty cursorPosition = new SimpleLongProperty(-1);

    /** 允许的最大地址(通常为数据大小 - 1) */
    private long maxAddress;

    /**
     * 构造选区模型,并指定最大有效地址 
     *
     * @param maxAddress 数据源的最大地址(若数据为空则可为 -1)
     */
    public SelectionModel(long maxAddress) {
        this.maxAddress = maxAddress;
    }

    /**
     * 清除选区并将光标重置为 -1 
     */
    public void clear() {
        selectionStart.set(-1);
        selectionEnd.set(-1);
        cursorPosition.set(-1);
    }

    /**
     * 在指定地址处开始一个新的选区(起始和结束均设为该地址) 
     * <p>
     * 若地址超出有效范围(0 ~ maxAddress),则忽略操作 
     * </p>
     *
     * @param address 起始地址
     */
    public void startSelection(long address) {
        if (address < 0 || address > maxAddress) {
            return;
        }
        selectionStart.set(address);
        selectionEnd.set(address);
        cursorPosition.set(address);
    }

    /**
     * 将选区扩展到指定地址(保持起始不变,更新结束地址) 
     * <p>
     * 若当前无选区,则自动调用 {@link #startSelection(long)} 创建新选区 
     * 地址超出有效范围则忽略 
     * </p>
     *
     * @param address 要扩展到的地址
     */
    public void extendSelection(long address) {
        if (address < 0 || address > maxAddress) {
            return;
        }
        if (!hasSelection()) {
            startSelection(address);
            return;
        }
        selectionEnd.set(address);
        cursorPosition.set(address);
    }

    /**
     * 直接指定选区的起始和结束地址(自动排序) 
     * <p>
     * 若任一地址超出有效范围,则忽略操作 
     * </p>
     *
     * @param start 选区起始地址
     * @param end   选区结束地址
     */
    public void select(long start, long end) {
        if (start < 0 || end > maxAddress || start > maxAddress) {
            return;
        }
        selectionStart.set(Math.min(start, end));
        selectionEnd.set(Math.max(start, end));
        cursorPosition.set(selectionStart.get());
    }

    /**
     * 判断当前是否存在有效选区 
     *
     * @return 若选区起始和结束均 ≥ 0 则返回 {@code true}
     */
    public boolean hasSelection() {
        return selectionStart.get() >= 0 && selectionEnd.get() >= 0;
    }

    /**
     * 获取选区的最小地址(起始地址) 
     * <p>
     * 注：若选区起始 > 结束(通常不会发生,因为内部会自动排序),此方法仍返回两者中的较小值 
     * </p>
     *
     * @return 选区的最小地址
     */
    public long getMinAddress() {
        return Math.min(selectionStart.get(), selectionEnd.get());
    }

    /**
     * 获取选区的最大地址(结束地址) 
     *
     * @return 选区的最大地址
     */
    public long getMaxAddress() {
        return Math.max(selectionStart.get(), selectionEnd.get());
    }

    /**
     * 更新最大有效地址 若当前选区或光标位置超出新范围,则将其重置为 -1 
     *
     * @param max 新的最大地址
     */
    public void setMaxAddress(long max) {
        this.maxAddress = max;
        if (selectionStart.get() > max) {
            selectionStart.set(-1);
        }
        if (selectionEnd.get() > max) {
            selectionEnd.set(-1);
        }
        if (cursorPosition.get() > max) {
            cursorPosition.set(-1);
        }
    }

    /**
     * 获取选区的大小(字节数) 
     *
     * @return 选区覆盖的字节数,若无选区则返回 0
     */
    public long getSelectionSize() {
        return hasSelection() ? getMaxAddress() - getMinAddress() + 1 : 0;
    }

    /**
     * 判断指定地址是否位于当前选区内(含边界) 
     *
     * @param address 要检查的地址
     * @return 若 address 在选区范围内则返回 {@code true}
     */
    public boolean contains(long address) {
        return hasSelection() && address >= getMinAddress() && address <= getMaxAddress();
    }

    // ---------- JavaFX 属性访问 ----------

    /**
     * 获取选区起始地址的值 
     *
     * @return 起始地址,若无可为 -1
     */
    public long getSelectionStart() {
        return selectionStart.get();
    }

    /**
     * 返回选区起始地址的 {@link LongProperty},用于绑定和监听 
     *
     * @return 起始地址属性
     */
    public LongProperty selectionStartProperty() {
        return selectionStart;
    }

    /**
     * 获取选区结束地址的值 
     *
     * @return 结束地址,若无可为 -1
     */
    public long getSelectionEnd() {
        return selectionEnd.get();
    }

    /**
     * 返回选区结束地址的 {@link LongProperty} 
     *
     * @return 结束地址属性
     */
    public LongProperty selectionEndProperty() {
        return selectionEnd;
    }

    /**
     * 获取光标位置 
     *
     * @return 光标地址,若无可为 -1
     */
    public long getCursorPosition() {
        return cursorPosition.get();
    }

    /**
     * 返回光标位置的 {@link ReadOnlyLongProperty}(只读属性) 
     *
     * @return 光标位置属性(只读)
     */
    public ReadOnlyLongProperty cursorPositionProperty() {
        return cursorPosition;
    }
}