 
package com.bingbaihanji.classgraph.metadata;

import java.util.Collection;

/** {@link ModuleInfo} 对象列表 */
public class ModuleInfoList extends InfoList<ModuleInfo> {
    /** 序列化版本 UID */
    private static final long serialVersionUID = 1L;

    /**
     * 构造函数
     */
    ModuleInfoList() {
        super();
    }

    /**
     * 构造函数
     *
     * @param sizeHint
     *            大小提示
     */
    ModuleInfoList(final int sizeHint) {
        super(sizeHint);
    }

    /**
     * 构造函数
     *
     * @param moduleInfoCollection
     *            模块信息集合
     */
    public ModuleInfoList(final Collection<ModuleInfo> moduleInfoCollection) {
        super(moduleInfoCollection);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 查找此列表中满足给定过滤谓词的 {@link ModuleInfo} 对象子集
     *
     * @param filter
     *            要应用的 {@link ModuleInfoFilter} 过滤器
     * @return 此列表中满足给定过滤谓词的 {@link ModuleInfo} 对象子集
     */
    public ModuleInfoList filter(final ModuleInfoFilter filter) {
        final ModuleInfoList moduleInfoFiltered = new ModuleInfoList();
        for (final ModuleInfo resource : this) {
            if (filter.accept(resource)) {
                moduleInfoFiltered.add(resource);
            }
        }
        return moduleInfoFiltered;
    }

    /**
     * 使用将 {@link ModuleInfo} 对象映射为布尔值的谓词过滤 {@link ModuleInfoList}，
     * 为列表中谓词为 true 的所有项生成另一个 {@link ModuleInfoList}
     */
    @FunctionalInterface
    public interface ModuleInfoFilter {
        /**
         * 是否允许 {@link ModuleInfo} 列表项通过过滤器
         *
         * @param moduleInfo
         *            要过滤的 {@link ModuleInfo} 项
         * @return 是否允许该项通过过滤器如果为 true，则将其复制到输出列表；如果为 false，则将其排除
         */
        boolean accept(ModuleInfo moduleInfo);
    }
}
