/**
 * 可复用的 JavaFX 窗口工具包
 *
 * <p>使用 {@link com.bingbaihanji.windows.jfx.WindowToolkit} 作为
 * 平台无关操作的主要入口点Windows 特定的 JNA 代码位于
 * {@code com.bingbaihanji.javafx.fx.win32} 包中,有意隐藏在
 * {@link com.bingbaihanji.windows.jfx.WindowPlatformProvider} 之后
 * 平台无关的原生类型定义位于 {@code com.bingbaihanji.javafx.jna} 包中</p>
 *
 * <p>迁移到另一个 JavaFX 项目的检查清单：</p>
 * <ul>
 *   <li>复制本包、{@code fx.win32} 包和 {@code jna} 包；</li>
 *   <li>保留 JNA 依赖({@code jna} 和 {@code jna-platform})；</li>
 *   <li>在 {@code module-info.java} 中导出顶层包；</li>
 *   <li>向 {@code com.sun.jna} 开放 {@code fx.win32} 包；</li>
 *   <li>在应用程序关闭时调用 {@link com.bingbaihanji.windows.jfx.WindowToolkit#shutdown()}</li>
 * </ul>
 */
package com.bingbaihanji.windows.jfx;
