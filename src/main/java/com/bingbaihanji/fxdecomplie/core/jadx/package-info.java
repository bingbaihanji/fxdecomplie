/**
 * JADX 反编译引擎核心包
 *
 * <p>本包移植自开源项目 <a href="https://github.com/skylot/jadx">JADX</a>，
 * 作为 fxdecomplie 项目的核心反编译引擎，负责将 DEX/Smali 字节码反编译为可读的 Java 源代码</p>
 *
 * <h2>子包结构</h2>
 * <ul>
 *   <li>{@code api} — 公开 API 层，定义反编译接口、插件扩展点、资源加载、选项配置等 
 *       与核心实现解耦，供上层模块调用</li>
 *   <li>{@code core} — 核心实现层，包含 DEX 解析、控制流分析、类型推断、代码生成、
 *       混淆还原（deobfuscation）、资源 XML 解析等反编译全流程</li>
 *   <li>{@code plugins} — 插件系统，包括输入源插件（如 Java class 文件加载）和插件管理器，
 *       支持扩展反编译能力</li>
 *   <li>{@code zip} — ZIP 归档文件的底层 I/O 工具，为 APK/JAR 等归档格式的读取提供支持</li>
 * </ul>
 *
 * <h2>与上游项目的关系</h2>
 * <p>本包代码基于 JADX 源码迁移适配而来，针对 fxdecomplie 项目的架构需求进行了定制调整
 * 后续升级时应注意与上游版本的差异，避免覆盖本地修改</p>
 *
 * @since 2026-07-11
 * @see com.bingbaihanji.fxdecomplie.decompiler.Decompiler 项目反编译器接口
 */
package com.bingbaihanji.fxdecomplie.core.jadx;




