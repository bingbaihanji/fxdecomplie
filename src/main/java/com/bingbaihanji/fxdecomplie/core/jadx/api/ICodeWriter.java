package com.bingbaihanji.fxdecomplie.core.jadx.api;

import java.util.Map;

import org.jetbrains.annotations.ApiStatus;

import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeAnnotation;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeNodeRef;

/**
 * 代码写入器接口，用于构建反编译后的代码输出。
 * <p>
 * 该接口提供了向输出缓冲区追加文本、管理缩进级别、
 * 附加元数据注解以及将最终结果生成为 {@link ICodeInfo} 的方法。
 * 实现类可在支持的情况下嵌入定义引用和源码行号映射等元数据信息。
 * </p>
 */
public interface ICodeWriter {

	/**
	 * 检查当前写入器是否支持元数据（如定义引用、行号映射等）。
	 *
	 * @return 如果支持元数据则返回 {@code true}
	 */
	boolean isMetadataSupported();

	/**
	 * 开始新的一行（不含内容）。
	 *
	 * @return 当前写入器实例，便于链式调用
	 */
	ICodeWriter startLine();

	/**
	 * 以指定字符作为行首内容，开始新的一行。
	 *
	 * @param c 行首字符
	 * @return 当前写入器实例，便于链式调用
	 */
	ICodeWriter startLine(char c);

	/**
	 * 以指定字符串作为行首内容，开始新的一行。
	 *
	 * @param str 行首字符串
	 * @return 当前写入器实例，便于链式调用
	 */
	ICodeWriter startLine(String str);

	/**
	 * 开始新的一行，并关联源码行号。
	 *
	 * @param sourceLine 对应的源码行号
	 * @return 当前写入器实例，便于链式调用
	 */
	ICodeWriter startLineWithNum(int sourceLine);

	/**
	 * 追加多行字符串内容。
	 *
	 * @param str 要追加的多行字符串
	 * @return 当前写入器实例，便于链式调用
	 */
	ICodeWriter addMultiLine(String str);

	/**
	 * 在当前行追加字符串内容。
	 *
	 * @param str 要追加的字符串
	 * @return 当前写入器实例，便于链式调用
	 */
	ICodeWriter add(String str);

	/**
	 * 在当前行追加单个字符。
	 *
	 * @param c 要追加的字符
	 * @return 当前写入器实例，便于链式调用
	 */
	ICodeWriter add(char c);

	/**
	 * 将另一个代码写入器的内容追加到当前输出中。
	 *
	 * @param code 要合并的另一个代码写入器
	 * @return 当前写入器实例，便于链式调用
	 */
	ICodeWriter add(ICodeWriter code);

	/**
	 * 插入一个换行符，结束当前行并开始新的一行。
	 *
	 * @return 当前写入器实例，便于链式调用
	 */
	ICodeWriter newLine();

	/**
	 * 向当前行追加缩进空格（缩进量由当前缩进级别决定）。
	 *
	 * @return 当前写入器实例，便于链式调用
	 */
	ICodeWriter addIndent();

	/**
	 * 增加一级缩进级别。
	 */
	void incIndent();

	/**
	 * 减少一级缩进级别。
	 */
	void decIndent();

	/**
	 * 获取当前的缩进级别。
	 *
	 * @return 当前的缩进级别数值
	 */
	int getIndent();

	/**
	 * 设置当前的缩进级别。
	 *
	 * @param indent 要设置的缩进级别数值
	 */
	void setIndent(int indent);

	/**
	 * 返回当前行号（仅在支持元数据时有效）。
	 *
	 * @return 当前行号
	 */
	int getLine();

	/**
	 * 返回起始行位置（仅在支持元数据时有效）。
	 *
	 * @return 起始行位置偏移量
	 */
	int getLineStartPos();

	/**
	 * 在当前代码位置附加一个定义引用（如类、方法、字段的定义）。
	 *
	 * @param obj 要附加的代码节点引用
	 */
	void attachDefinition(ICodeNodeRef obj);

	/**
	 * 在当前代码位置附加一个注解。
	 *
	 * @param obj 要附加的代码注解
	 */
	void attachAnnotation(ICodeAnnotation obj);

	/**
	 * 在当前行附加一个行级注解。
	 *
	 * @param obj 要附加的行级注解
	 */
	void attachLineAnnotation(ICodeAnnotation obj);

	/**
	 * 在当前代码位置关联源码行号。
	 *
	 * @param sourceLine 要关联的源码行号
	 */
	void attachSourceLine(int sourceLine);

	/**
	 * 完成代码写入操作，生成最终的 {@link ICodeInfo} 结果。
	 *
	 * @return 包含所有写入内容和元数据的代码信息对象
	 */
	ICodeInfo finish();

	/**
	 * 获取当前已写入的完整代码字符串。
	 *
	 * @return 代码字符串
	 */
	String getCodeStr();

	/**
	 * 获取当前已写入代码的字符总长度。
	 *
	 * @return 代码长度（字符数）
	 */
	int getLength();

	/**
	 * 获取底层的原始 {@link StringBuilder} 缓冲区。
	 *
	 * @return 原始字符串构建器
	 */
	StringBuilder getRawBuf();

	/**
	 * 获取原始注解映射表（内部使用）。
	 *
	 * @return 行号到注解的映射表
	 */
	@ApiStatus.Internal
	Map<Integer, ICodeAnnotation> getRawAnnotations();
}
