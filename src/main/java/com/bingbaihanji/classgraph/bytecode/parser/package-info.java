/**
 * Class 文件解析器的 DTO 和工具类。
 *
 * <p>包含从 class 文件字节码中提取的结构化数据对象（DTO），
 * 以及与 {@code ClassParser} 解耦的独立工具组件。</p>
 *
 * <p>核心类型：
 * <ul>
 *   <li>{@link com.bingbaihanji.classgraph.bytecode.parser.ParsedClassFile} — 完整解析结果</li>
 *   <li>{@link com.bingbaihanji.classgraph.bytecode.parser.ParsedField} — 字段 DTO</li>
 *   <li>{@link com.bingbaihanji.classgraph.bytecode.parser.ParsedMethod} — 方法 DTO</li>
 *   <li>{@link com.bingbaihanji.classgraph.bytecode.parser.ParsedAnnotation} — 注解 DTO</li>
 *   <li>{@link com.bingbaihanji.classgraph.bytecode.parser.ExternalClassReferenceCollector} — 外部引用收集</li>
 *   <li>{@link com.bingbaihanji.classgraph.bytecode.parser.MetadataLinker} — 类图关系链接</li>
 * </ul>
 */
package com.bingbaihanji.classgraph.bytecode.parser;
