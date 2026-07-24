/**
 * 元数据查询层 — CQRS-lite 模式的读/写分离。
 *
 * <p>核心类型：
 * <ul>
 *   <li>{@link com.bingbaihanji.classgraph.metadata.query.ClassMetadataReader} —
 *       只读接口，所有查询操作的契约</li>
 *   <li>{@link com.bingbaihanji.classgraph.metadata.query.MutableClassMetadata} —
 *       可变构建器，扫描阶段使用</li>
 *   <li>{@link com.bingbaihanji.classgraph.metadata.query.MetadataQueryService} —
 *       统一查询入口，封装过滤/聚合/统计</li>
 *   <li>{@link com.bingbaihanji.classgraph.metadata.query.MetadataGuards} —
 *       Guard clause 工具，消除 null 检查和空集合处理</li>
 * </ul>
 *
 * <p>使用模式：
 * <pre>{@code
 * // 构建阶段
 * MutableClassMetadata builder = new MutableClassMetadata("com/example/Foo")
 *     .modifiers(0x0001)
 *     .superclassName("java/lang/Object");
 * FrozenMetadata frozen = builder.freeze();
 *
 * // 查询阶段
 * MetadataQueryService service = MetadataGuards.queryServiceFrom(readers);
 * List<ClassMetadataReader> interfaces = service.findAllInterfaces();
 * MetadataStatistics stats = service.statistics();
 * }</pre>
 */
package com.bingbaihanji.classgraph.metadata.query;
