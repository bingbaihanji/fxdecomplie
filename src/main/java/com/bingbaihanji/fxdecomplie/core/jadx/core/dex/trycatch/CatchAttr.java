package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.trycatch;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;

import java.util.Comparator;
import java.util.List;

/**
 * catch 捕获属性
 * 作为附加在指令上的属性，用于记录该指令可能触发的异常处理器（catch 分支）列表
 * 处理器按其偏移量排序
 */
public class CatchAttr implements IJadxAttribute {

    /** 该 catch 属性关联的异常处理器列表 */
    private final List<ExceptionHandler> handlers;

    private CatchAttr(List<ExceptionHandler> handlers) {
        this.handlers = handlers;
    }

    /**
     * 构建 catch 属性处理器列表将按处理器偏移量升序排序
     *
     * @param handlers 异常处理器列表
     * @return 构建好的 catch 属性
     */
    public static CatchAttr build(List<ExceptionHandler> handlers) {
        handlers.sort(Comparator.comparingInt(ExceptionHandler::getHandlerOffset));
        return new CatchAttr(handlers);
    }

    /**
     * @return 关联的异常处理器列表
     */
    public List<ExceptionHandler> getHandlers() {
        return handlers;
    }

    @Override
    public AType<CatchAttr> getAttrType() {
        return AType.EXC_CATCH;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CatchAttr)) {
            return false;
        }
        CatchAttr catchAttr = (CatchAttr) o;
        return getHandlers().equals(catchAttr.getHandlers());
    }

    @Override
    public int hashCode() {
        return getHandlers().hashCode();
    }

    @Override
    public String toString() {
        return "Catch: " + Utils.listToString(getHandlers());
    }
}
