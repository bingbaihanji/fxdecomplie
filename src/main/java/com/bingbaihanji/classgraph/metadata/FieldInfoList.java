 
package com.bingbaihanji.classgraph.metadata;

import com.bingbaihanji.classgraph.util.LogNode;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/** {@link FieldInfo} 对象的列表 */
public class FieldInfoList extends InfoList<FieldInfo> {
    /** 不可修改的空 {@link FieldInfoList} */
    static final FieldInfoList EMPTY_LIST = new FieldInfoList();
    /** serialVersionUID */
    private static final long serialVersionUID = 1L;

    static {
        EMPTY_LIST.makeUnmodifiable();
    }

    /**
     * 构造一个新的可修改的空 {@link FieldInfo} 对象列表
     */
    public FieldInfoList() {
        super();
    }

    /**
     * 根据大小提示构造一个新的可修改的空 {@link FieldInfo} 对象列表
     *
     * @param sizeHint
     *            大小提示
     */
    public FieldInfoList(final int sizeHint) {
        super(sizeHint);
    }

    /**
     * 根据给定的初始 {@link FieldInfo} 对象集合构造一个新的可修改的空 {@link FieldInfoList}
     *
     * @param fieldInfoCollection
     *            {@link FieldInfo} 对象的集合
     */
    public FieldInfoList(final Collection<FieldInfo> fieldInfoCollection) {
        super(fieldInfoCollection);
    }

    /**
     * 返回一个不可修改的空 {@link FieldInfoList}
     *
     * @return 不可修改的空 {@link FieldInfoList}
     */
    public static FieldInfoList emptyList() {
        return EMPTY_LIST;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取列表中引用的所有类的 {@link ClassInfo} 对象
     *
     * @param classNameToClassInfo
     *            从类名到 {@link ClassInfo} 的映射
     * @param refdClassInfo
     *            已引用的类信息集合
     * @param log
     *            日志
     */
    protected void findReferencedClassInfo(final Map<String, ClassInfo> classNameToClassInfo,
                                           final Set<ClassInfo> refdClassInfo, final LogNode log) {
        for (final FieldInfo fi : this) {
            fi.findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 检查此列表中是否包含具有给定名称的 {@link FieldInfo} 对象
     *
     * @param fieldName
     *            字段名称
     * @return 如果此列表包含具有给定名称的 {@link FieldInfo} 对象，则返回 true
     */
    public boolean containsName(final String fieldName) {
        for (final FieldInfo fi : this) {
            if (fi.getName().equals(fieldName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 查找此列表中满足给定过滤谓词条件的 {@link FieldInfo} 对象子集
     *
     * @param filter
     *            要应用的 {@link FieldInfoFilter}
     * @return 此列表中满足给定过滤谓词条件的 {@link FieldInfo} 对象子集
     */
    public FieldInfoList filter(final FieldInfoFilter filter) {
        final FieldInfoList fieldInfoFiltered = new FieldInfoList();
        for (final FieldInfo resource : this) {
            if (filter.accept(resource)) {
                fieldInfoFiltered.add(resource);
            }
        }
        return fieldInfoFiltered;
    }

    /**
     * 使用将 {@link FieldInfo} 对象映射为布尔值的谓词过滤 {@link FieldInfoList}，
     * 生成一个新的 {@link FieldInfoList}，其中包含谓词为 true 的所有项
     */
    @FunctionalInterface
    public interface FieldInfoFilter {
        /**
         * 是否允许某个 {@link FieldInfo} 列表项通过过滤器
         *
         * @param fieldInfo
         *            要过滤的 {@link FieldInfo} 项
         * @return 是否允许该项通过过滤器如果为 true，该项将被复制到输出列表；如果为 false，则被排除
         */
        boolean accept(FieldInfo fieldInfo);
    }
}
