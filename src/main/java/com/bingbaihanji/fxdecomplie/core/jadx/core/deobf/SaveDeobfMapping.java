package com.bingbaihanji.fxdecomplie.core.jadx.core.deobf;

import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxArgs;
import com.bingbaihanji.fxdecomplie.core.jadx.api.args.GeneratedRenamesMappingFileMode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.json.JsonMappingGen;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.AbstractVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;

/**
 * 保存反混淆映射的访问器。
 * <p>
 * 在处理根节点时，根据配置将反混淆重命名映射写入文件（文本格式），
 * 或在开启 JSON 输出时额外导出 JSON 格式的映射。
 * </p>
 */
public class SaveDeobfMapping extends AbstractVisitor {
	private static final Logger LOG = LoggerFactory.getLogger(SaveDeobfMapping.class);

	/**
	 * 初始化时根据参数配置保存反混淆映射。
	 * <p>
	 * 当开启反混淆或未开启 JSON 输出时，保存文本格式映射文件；
	 * 当开启 JSON 输出时，额外导出 JSON 格式映射。
	 * </p>
	 *
	 * @param root AST 根节点
	 * @throws JadxException 初始化过程中可能抛出的异常
	 */
	@Override
	public void init(RootNode root) throws JadxException {
		JadxArgs args = root.getArgs();
		if (args.isDeobfuscationOn() || !args.isJsonOutput()) {
			saveMappings(root);
		}
		if (args.isJsonOutput()) {
			JsonMappingGen.dump(root);
		}
	}

	/**
	 * 将反混淆映射保存到文件。
	 * <p>
	 * 根据 {@link GeneratedRenamesMappingFileMode} 决定是否写入：
	 * 当模式不需要写入时直接返回；当模式为 READ_OR_SAVE 且映射文件已存在时也跳过写入。
	 * 否则清空并重新填充映射后保存，保存失败时记录错误日志。
	 * </p>
	 *
	 * @param root AST 根节点
	 */
	private void saveMappings(RootNode root) {
		GeneratedRenamesMappingFileMode mode = root.getArgs().getGeneratedRenamesMappingFileMode();
		if (!mode.shouldWrite()) {
			return;
		}
		DeobfPresets mapping = DeobfPresets.build(root);
		Path deobfMapFile = mapping.getDeobfMapFile();
		if (mode == GeneratedRenamesMappingFileMode.READ_OR_SAVE && Files.exists(deobfMapFile)) {
			return;
		}
		try {
			mapping.clear();
			mapping.fill(root);
			mapping.save();
		} catch (Exception e) {
			LOG.error("保存反混淆映射文件 '{}' 失败", deobfMapFile.toAbsolutePath(), e);
		}
	}

	/**
	 * 获取该访问器的名称。
	 *
	 * @return 访问器名称
	 */
	@Override
	public String getName() {
		return "SaveDeobfMapping";
	}
}
