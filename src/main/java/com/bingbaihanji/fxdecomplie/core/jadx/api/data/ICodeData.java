package com.bingbaihanji.fxdecomplie.core.jadx.api.data;

import java.util.List;

/**
 * 代码数据接口。
 * <p>
 * 表示与反编译代码关联的元数据，目前主要包含代码注释和重命名信息。
 * 这些数据通常来自外部注释文件或用户编辑，用于在反编译结果中注入额外信息。
 */
public interface ICodeData {

	/**
	 * 获取所有代码注释列表。
	 *
	 * @return 代码注释列表，若无注释则返回空列表
	 */
	List<ICodeComment> getComments();

	/**
	 * 获取所有重命名信息列表。
	 *
	 * @return 重命名列表，若无重命名则返回空列表
	 */
	List<ICodeRename> getRenames();

	/**
	 * 判断当前代码数据是否为空（不包含任何注释或重命名信息）。
	 *
	 * @return 若不包含任何数据则返回 {@code true}，否则返回 {@code false}
	 */
	boolean isEmpty();
}
