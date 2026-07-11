package com.bingbaihanji.fxdecomplie.core.jadx.api;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.bingbaihanji.fxdecomplie.core.jadx.api.resources.ResourceContentType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;

import static com.bingbaihanji.fxdecomplie.core.jadx.api.resources.ResourceContentType.CONTENT_BINARY;
import static com.bingbaihanji.fxdecomplie.core.jadx.api.resources.ResourceContentType.CONTENT_TEXT;
import static com.bingbaihanji.fxdecomplie.core.jadx.api.resources.ResourceContentType.CONTENT_UNKNOWN;

/**
 * 资源文件类型枚举。
 * 根据文件扩展名对资源进行分类，用于确定文件的内容类型（二进制/文本/未知）。
 */
public enum ResourceType {
	/** 代码文件：DEX 字节码、JAR 归档、Java 类文件 */
	CODE(CONTENT_BINARY, ".dex", ".jar", ".class"),
	/** XML 文件 */
	XML(CONTENT_TEXT, ".xml"),
	/** Android 资源编译表（Android Resource Table） */
	ARSC(CONTENT_TEXT, ".arsc"),
	/** APK 安装包及其变体（APKM、APKS） */
	APK(CONTENT_BINARY, ".apk", ".apkm", ".apks"),
	/** 字体文件：TrueType、OpenType */
	FONT(CONTENT_BINARY, ".ttf", ".ttc", ".otf"),
	/** 图片文件：PNG、GIF、JPEG、WebP、BMP、TIFF */
	IMG(CONTENT_BINARY, ".png", ".gif", ".jpg", ".jpeg", ".webp", ".bmp", ".tiff"),
	/** 压缩归档文件：ZIP、RAR、7z、TAR、GZ 等 */
	ARCHIVE(CONTENT_BINARY, ".zip", ".rar", ".7zip", ".7z", ".arj", ".tar", ".gzip", ".bzip", ".bzip2", ".cab", ".cpio", ".ar", ".gz",
			".tgz", ".bz2"),
	/** 视频文件：MP4、MKV、WebM、AVI 等 */
	VIDEOS(CONTENT_BINARY, ".mp4", ".mkv", ".webm", ".avi", ".flv", ".3gp"),
	/** 音频文件：AAC、OGG、MP3、WAV 等 */
	SOUNDS(CONTENT_BINARY, ".aac", ".ogg", ".opus", ".mp3", ".wav", ".wma", ".mid", ".midi"),
	/** JSON 文件 */
	JSON(CONTENT_TEXT, ".json"),
	/** 文本文件：纯文本、配置文件、脚本、Markdown 等 */
	TEXT(CONTENT_TEXT, ".txt", ".ini", ".conf", ".yaml", ".properties", ".js", ".java", ".kt", ".md"),
	/** HTML 网页文件 */
	HTML(CONTENT_TEXT, ".html", ".htm"),
	/** 原生共享库（Linux .so 文件） */
	LIB(CONTENT_BINARY, ".so"),
	/** AndroidManifest.xml 清单文件 */
	MANIFEST(CONTENT_TEXT),
	/** 未知二进制文件 */
	UNKNOWN_BIN(CONTENT_BINARY, ".bin"),
	/** 未知类型文件（无法识别扩展名时的默认类型） */
	UNKNOWN(CONTENT_UNKNOWN);

	private final ResourceContentType contentType;
	private final String[] exts;

	ResourceType(ResourceContentType contentType, String... exts) {
		this.contentType = contentType;
		this.exts = exts;
	}

	/**
	 * 获取该资源类型的内容类型（二进制/文本/未知）。
	 *
	 * @return 资源内容类型
	 */
	public ResourceContentType getContentType() {
		return contentType;
	}

	/**
	 * 获取该资源类型对应的所有文件扩展名。
	 *
	 * @return 扩展名数组
	 */
	public String[] getExts() {
		return exts;
	}

	private static final Map<String, ResourceType> EXT_MAP = new HashMap<>();

	static {
		// 构建"扩展名 -> 资源类型"的映射表，重复扩展名将抛出异常
		for (ResourceType type : ResourceType.values()) {
			for (String ext : type.getExts()) {
				ResourceType prev = EXT_MAP.put(ext, type);
				if (prev != null) {
					throw new JadxRuntimeException("Duplicate extension in ResourceType: " + ext);
				}
			}
		}
	}

	/**
	 * 根据文件名推断资源类型。
	 * 优先识别 {@code resources.pb}（视为 ARSC），并对 {@code AndroidManifest.xml} 做特殊处理（视为 MANIFEST）。
	 *
	 * @param fileName 文件名
	 * @return 匹配到的资源类型，无法识别时返回 {@link #UNKNOWN}
	 */
	public static ResourceType getFileType(String fileName) {
		if (fileName.endsWith("/resources.pb")) {
			return ARSC;
		}
		int dot = fileName.lastIndexOf('.');
		if (dot != -1) {
			String ext = fileName.substring(dot).toLowerCase(Locale.ROOT);
			ResourceType resType = EXT_MAP.get(ext);
			if (resType != null) {
				if (resType == XML && "AndroidManifest.xml".equals(fileName)) {
					return MANIFEST;
				}
				return resType;
			}
		}
		return UNKNOWN;
	}
}
