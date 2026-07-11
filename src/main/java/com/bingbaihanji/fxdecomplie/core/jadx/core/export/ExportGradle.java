package com.bingbaihanji.fxdecomplie.core.jadx.core.export;

import java.io.File;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bingbaihanji.fxdecomplie.core.jadx.api.ResourceFile;
import com.bingbaihanji.fxdecomplie.core.jadx.api.ResourceType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.export.gen.AndroidGradleGenerator;
import com.bingbaihanji.fxdecomplie.core.jadx.core.export.gen.IExportGradleGenerator;
import com.bingbaihanji.fxdecomplie.core.jadx.core.export.gen.SimpleJavaGradleGenerator;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.android.AndroidManifestParser;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;

/**
 * Gradle 项目导出器。
 * <p>
 * 负责根据输入的根节点、项目目录和资源文件列表，
 * 自动检测导出类型（Android 应用、Android 库或简单 Java 项目），
 * 并生成对应的 Gradle 构建文件。
 */
public class ExportGradle {

	private static final Logger LOG = LoggerFactory.getLogger(ExportGradle.class);
	private final RootNode root;
	private final File projectDir;
	private final List<ResourceFile> resources;
	private IExportGradleGenerator generator;

	/**
	 * 构造 Gradle 项目导出器。
	 *
	 * @param root        根节点，包含反编译后的类和参数信息
	 * @param projectDir  项目输出目录
	 * @param resources   资源文件列表
	 */
	public ExportGradle(RootNode root, File projectDir, List<ResourceFile> resources) {
		this.root = root;
		this.projectDir = projectDir;
		this.resources = resources;
	}

	/**
	 * 初始化导出器。
	 * <p>
	 * 检测导出类型，创建对应的 Gradle 生成器，初始化生成器并创建输出目录。
	 *
	 * @return 输出目录信息
	 */
	public OutDirs init() {
		ExportGradleType exportType = getExportGradleType();
		LOG.info("Export Gradle project using '{}' template", exportType);
		switch (exportType) {
			case ANDROID_APP:
			case ANDROID_LIBRARY:
				generator = new AndroidGradleGenerator(root, projectDir, resources, exportType);
				break;
			case SIMPLE_JAVA:
				generator = new SimpleJavaGradleGenerator(root, projectDir, resources);
				break;
			default:
				throw new JadxRuntimeException("Unexpected export type: " + exportType);
		}
		generator.init();
		OutDirs outDirs = generator.getOutDirs();
		outDirs.makeDirs();
		return outDirs;
	}

	/**
	 * 获取导出的 Gradle 项目类型。
	 * <p>
	 * 优先使用命令行参数指定的类型；若未指定或为 AUTO，则使用自动检测的类型。
	 *
	 * @return 导出类型枚举值
	 */
	private ExportGradleType getExportGradleType() {
		ExportGradleType argsExportType = root.getArgs().getExportGradleType();
		ExportGradleType detectedType = detectExportType(root, resources);
		if (argsExportType == null
				|| argsExportType == ExportGradleType.AUTO
				|| argsExportType == detectedType) {
			return detectedType;
		}
		return argsExportType;
	}

	/**
	 * 根据资源文件自动检测导出的 Gradle 项目类型。
	 * <p>
	 * 检测规则：
	 * <ul>
	 *   <li>存在 AndroidManifest 且包含 classes.jar 资源 → Android 库项目</li>
	 *   <li>存在 AndroidManifest 且包含 ARSC 资源 → Android 应用项目</li>
	 *   <li>其余情况 → 简单 Java 项目</li>
	 * </ul>
	 *
	 * @param root      根节点
	 * @param resources 资源文件列表
	 * @return 自动检测得到的导出类型
	 */
	public static ExportGradleType detectExportType(RootNode root, List<ResourceFile> resources) {
		ResourceFile androidManifest = AndroidManifestParser.getAndroidManifest(resources);
		if (androidManifest != null) {
			if (resources.stream().anyMatch(r -> "classes.jar".equals(r.getOriginalName()))) {
				return ExportGradleType.ANDROID_LIBRARY;
			}
			if (resources.stream().anyMatch(r -> r.getType() == ResourceType.ARSC)) {
				return ExportGradleType.ANDROID_APP;
			}
		}
		return ExportGradleType.SIMPLE_JAVA;
	}

	/**
	 * 生成 Gradle 构建文件。
	 * <p>
	 * 委托已初始化的生成器写出对应模板的 Gradle 项目文件，调用前须先执行 {@link #init()}。
	 */
	public void generateGradleFiles() {
		generator.generateFiles();
	}

}
