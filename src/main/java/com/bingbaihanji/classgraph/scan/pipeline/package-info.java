/**
 * 扫描管道框架 — 将 {@link com.bingbaihanji.classgraph.scan.Scanner} 的扫描过程
 * 分解为一系列有序的 {@link com.bingbaihanji.classgraph.scan.pipeline.ScanStep} 步骤。
 *
 * <p>核心概念：
 * <ul>
 *   <li>{@link com.bingbaihanji.classgraph.scan.pipeline.ScanStep} — 单个处理步骤</li>
 *   <li>{@link com.bingbaihanji.classgraph.scan.pipeline.ScanContext} — 步骤间共享状态</li>
 *   <li>{@link com.bingbaihanji.classgraph.scan.pipeline.ScanPipeline} — 编排引擎</li>
 * </ul>
 *
 * <p>此框架遵循 Chain of Responsibility 模式，允许独立开发和测试每个步骤。</p>
 */
package com.bingbaihanji.classgraph.scan.pipeline;
