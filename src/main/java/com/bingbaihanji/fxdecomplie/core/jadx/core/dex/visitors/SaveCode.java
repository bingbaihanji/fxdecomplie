package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors;

import com.bingbaihanji.fxdecomplie.core.jadx.api.ICodeInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxArgs;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.files.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * 代码保存工具类。
 * <p>
 * 负责将反编译生成的代码保存到文件系统。根据配置的输出格式（Java 或 JSON）
 * 自动确定文件扩展名，并通过安全检查确保只保存有效的类文件。
 * </p>
 */
public class SaveCode {
    private static final Logger LOG = LoggerFactory.getLogger(SaveCode.class);

    /** 私有构造函数，防止实例化工具类 */
    private SaveCode() {
    }

    /**
     * 将反编译后的类代码保存到指定目录。
     * <p>
     * 保存前会进行以下检查：
     * <ul>
     *   <li>跳过标记为 DONT_GENERATE 的类</li>
     *   <li>验证代码不为空且非空内容</li>
     *   <li>检查是否启用了跳过文件保存的配置</li>
     *   <li>验证文件名的安全性</li>
     * </ul>
     *
     * @param dir  保存的目标根目录
     * @param cls  类节点，用于获取类信息和配置
     * @param code 反编译生成的代码信息，不能为 null
     * @throws JadxRuntimeException 如果代码信息为 null
     */
    public static void save(File dir, ClassNode cls, ICodeInfo code) {
        if (cls.contains(AFlag.DONT_GENERATE)) {
            return;
        }
        if (code == null) {
            throw new JadxRuntimeException("Code not generated for class " + cls.getFullName());
        }
        if (code == ICodeInfo.EMPTY) {
            return;
        }
        String codeStr = code.getCodeStr();
        // 跳过空代码内容
        if (codeStr.isEmpty()) {
            return;
        }
        JadxArgs args = cls.root().getArgs();
        // 如果配置了跳过文件保存，则直接返回
        if (args.isSkipFilesSave()) {
            return;
        }
        // 拼接文件名：类的完整路径 + 输出格式对应的扩展名
        String fileName = cls.getClassInfo().getAliasFullPath() + getFileExtension(cls.root());
        // 安全检查：验证文件名不包含非法字符
        if (!args.getSecurity().isValidEntryName(fileName)) {
            return;
        }
        save(codeStr, new File(dir, fileName));
    }

    /**
     * 将代码信息对象保存到指定文件。
     *
     * @param codeInfo 代码信息对象，包含待保存的代码字符串
     * @param file     目标文件
     */
    public static void save(ICodeInfo codeInfo, File file) {
        save(codeInfo.getCodeStr(), file);
    }

    /**
     * 将代码字符串保存到文件，使用 UTF-8 编码。
     * <p>
     * 如果目标文件的父目录不存在，会自动创建。
     * 保存失败时仅记录错误日志，不抛出异常。
     * </p>
     *
     * @param code 待保存的代码字符串
     * @param file 目标文件
     */
    public static void save(String code, File file) {
        File outFile = IoUtils.prepareFile(file);
        try (PrintWriter out = new PrintWriter(outFile, StandardCharsets.UTF_8)) {
            out.println(code);
        } catch (Exception e) {
            LOG.error("Save file error", e);
        }
    }

    /**
     * 根据当前输出格式返回代码文件的扩展名。
     *
     * @param root 根节点，用于获取输出格式配置
     * @return Java 格式返回 {@code .java}，JSON 格式返回 {@code .json}
     * @throws JadxRuntimeException 如果输出格式未知
     */
    public static String getFileExtension(RootNode root) {
        JadxArgs.OutputFormatEnum outputFormat = root.getArgs().getOutputFormat();
        return switch (outputFormat) {
            case JAVA -> ".java";
            case JSON -> ".json";
            default -> throw new JadxRuntimeException("Unknown output format: " + outputFormat);
        };
    }
}
