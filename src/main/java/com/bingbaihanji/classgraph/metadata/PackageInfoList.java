 
package com.bingbaihanji.classgraph.metadata;

import java.util.Collection;

/** 一个 {@link PackageInfo} 对象的列表 */
public class PackageInfoList extends InfoList<PackageInfo> {
    /** 一个不可修改的 {@link PackageInfoList} */
    static final PackageInfoList EMPTY_LIST = new PackageInfoList() {
        /** 序列化版本UID */
        private static final long serialVersionUID = 1L;

        @Override
        public boolean add(final PackageInfo e) {
            throw new IllegalArgumentException("List is immutable");
        }

        @Override
        public void add(final int index, final PackageInfo element) {
            throw new IllegalArgumentException("List is immutable");
        }

        @Override
        public boolean remove(final Object o) {
            throw new IllegalArgumentException("List is immutable");
        }

        @Override
        public PackageInfo remove(final int index) {
            throw new IllegalArgumentException("List is immutable");
        }

        @Override
        public boolean addAll(final Collection<? extends PackageInfo> c) {
            throw new IllegalArgumentException("List is immutable");
        }

        @Override
        public boolean addAll(final int index, final Collection<? extends PackageInfo> c) {
            throw new IllegalArgumentException("List is immutable");
        }

        @Override
        public boolean removeAll(final Collection<?> c) {
            throw new IllegalArgumentException("List is immutable");
        }

        @Override
        public boolean retainAll(final Collection<?> c) {
            throw new IllegalArgumentException("List is immutable");
        }

        @Override
        public void clear() {
            throw new IllegalArgumentException("List is immutable");
        }

        @Override
        public PackageInfo set(final int index, final PackageInfo element) {
            throw new IllegalArgumentException("List is immutable");
        }
    };
    /** 序列化版本UID */
    private static final long serialVersionUID = 1L;

    /**
     * 构造函数
     */
    PackageInfoList() {
        super();
    }

    /**
     * 构造函数
     *
     * @param sizeHint
     *            大小提示
     */
    PackageInfoList(final int sizeHint) {
        super(sizeHint);
    }

    /**
     * 构造函数
     *
     * @param packageInfoCollection
     *            包信息集合
     */
    public PackageInfoList(final Collection<PackageInfo> packageInfoCollection) {
        super(packageInfoCollection);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 查找此列表中给定过滤谓词为真的 {@link PackageInfo} 对象的子集
     *
     * @param filter
     *            要应用的 {@link PackageInfoFilter}
     * @return 此列表中给定过滤谓词为真的 {@link PackageInfo} 对象的子集
     */
    public PackageInfoList filter(final PackageInfoFilter filter) {
        final PackageInfoList packageInfoFiltered = new PackageInfoList();
        for (final PackageInfo resource : this) {
            if (filter.accept(resource)) {
                packageInfoFiltered.add(resource);
            }
        }
        return packageInfoFiltered;
    }

    /**
     * 使用一个将 {@link PackageInfo} 对象映射到布尔值的谓词来过滤 {@link PackageInfoList}，
     * 生成一个新的 {@link PackageInfoList}，包含列表中所有谓词为真的项
     */
    @FunctionalInterface
    public interface PackageInfoFilter {
        /**
         * 是否允许一个 {@link PackageInfo} 列表项通过过滤器
         *
         * @param packageInfo
         *            要过滤的 {@link PackageInfo} 项
         * @return 是否允许该项通过过滤器如果为 true，则该项被复制到输出列表；如果为 false，则被排除
         */
        boolean accept(PackageInfo packageInfo);
    }
}
