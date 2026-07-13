package com.bingbaihanji.fxdecomplie.core.jadx.api;

import com.bingbaihanji.fxdecomplie.core.jadx.api.impl.SimpleCodeInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.CustomResourcesLoader;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.resources.IResContainerFactory;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.resources.IResTableParserProvider;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.resources.IResourcesLoader;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.android.Res9patchStreamDecoder;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen.BinaryXMLParser;
import com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen.IResTableParser;
import com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen.ResContainer;
import com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen.ResTableBinaryParserProvider;
import com.bingbaihanji.fxdecomplie.core.jadx.zip.IZipEntry;
import com.bingbaihanji.fxdecomplie.core.jadx.zip.ZipContent;
import com.bingbaihanji.fxdecomplie.util.io.ByteUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static com.bingbaihanji.fxdecomplie.core.jadx.core.utils.files.IoUtils.READ_BUFFER_SIZE;


// TODO: 移动到 core 包

/**
 * 资源加载器，负责加载和解析 Android 应用中的各类资源文件 (如二进制 XML ARSC 资源表 图片等)
 * 实现了 {@link IResourcesLoader} 接口，支持自定义资源容器工厂和资源表解析器的扩展
 */
public final class ResourcesLoader implements IResourcesLoader {
    private static final Logger LOG = LoggerFactory.getLogger(ResourcesLoader.class);

    private final JadxDecompiler decompiler;

    private final List<IResTableParserProvider> resTableParserProviders = new ArrayList<>();
    private final List<IResContainerFactory> resContainerFactories = new ArrayList<>();

    private BinaryXMLParser binaryXmlParser;

    /**
     * 构造资源加载器实例
     *
     * @param decompiler 反编译器实例
     */
    ResourcesLoader(JadxDecompiler decompiler) {
        this.decompiler = decompiler;
        this.resTableParserProviders.add(new ResTableBinaryParserProvider());
    }

    /**
     * 使用指定的解码器解码资源文件的输入流
     * 支持从 ZIP 条目或普通文件中读取数据
     *
     * @param rf      资源文件
     * @param decoder 资源解码器
     * @param <T>     解码后的目标类型
     * @return 解码后的对象
     * @throws JadxException 解码失败时抛出
     */
    public static <T> T decodeStream(ResourceFile rf, ResourceDecoder<T> decoder) throws JadxException {
        try {
            IZipEntry zipEntry = rf.getZipEntry();
            if (zipEntry != null) {
                try (InputStream inputStream = zipEntry.getInputStream()) {
                    return decoder.decode(zipEntry.getUncompressedSize(), inputStream);
                }
            } else {
                File file = new File(rf.getOriginalName());
                try (InputStream inputStream = new BufferedInputStream(new FileInputStream(file))) {
                    return decoder.decode(file.length(), inputStream);
                }
            }
        } catch (Exception e) {
            throw new JadxException("Error decode: " + rf.getOriginalName(), e);
        }
    }

    /**
     * 加载指定资源文件的内容容器 (静态入口)
     * 解码失败时不会抛出异常，而是返回一个包含错误信息与堆栈的文本资源容器
     *
     * @param jadxRef 反编译器实例
     * @param rf      待加载的资源文件
     * @return 资源内容容器，解码失败时返回包含错误信息的文本容器
     */
    static ResContainer loadContent(JadxDecompiler jadxRef, ResourceFile rf) {
        try {
            ResourcesLoader resLoader = jadxRef.getResourcesLoader();
            return decodeStream(rf, (size, is) -> resLoader.loadContent(rf, is));
        } catch (JadxException e) {
            LOG.error("Decode error", e);
            ICodeWriter cw = jadxRef.getRoot().makeCodeWriter();
            cw.add("Error decode ").add(rf.getType().toString().toLowerCase());
            Utils.appendStackTrace(cw, e.getCause());
            return ResContainer.textResource(rf.getDeobfName(), cw.finish());
        }
    }

    /**
     * 解码图片资源对 .9.png 点九图执行专门解码，其余图片以文件链接形式返回
     *
     * @param rf          资源文件
     * @param inputStream 图片输入流
     * @return 资源内容容器
     */
    private static ResContainer decodeImage(ResourceFile rf, InputStream inputStream) {
        String name = rf.getDeobfName();
        if (name.endsWith(".9.png")) {
            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                Res9patchStreamDecoder decoder = new Res9patchStreamDecoder();
                if (decoder.decode(inputStream, os)) {
                    return ResContainer.decodedData(rf.getDeobfName(), os.toByteArray());
                }
            } catch (Exception e) {
                LOG.error("Failed to decode 9-patch png image, path: {}", name, e);
            }
        }
        return ResContainer.resourceFileLink(rf);
    }

    /**
     * 以 UTF-8 编码将输入流读取为代码信息对象
     *
     * @param is 输入流
     * @return 代码信息对象
     * @throws IOException 读取失败时抛出
     */
    public static ICodeInfo loadToCodeWriter(InputStream is) throws IOException {
        return loadToCodeWriter(is, StandardCharsets.UTF_8);
    }

    /**
     * 以指定字符集将输入流读取为代码信息对象
     *
     * @param is      输入流
     * @param charset 字符集
     * @return 代码信息对象
     * @throws IOException 读取失败时抛出
     */
    public static ICodeInfo loadToCodeWriter(InputStream is, Charset charset) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(READ_BUFFER_SIZE);
        is.transferTo(baos);
        return new SimpleCodeInfo(baos.toString(charset));
    }

    /**
     * 加载所有输入文件中的资源
     *
     * @param root 根节点
     * @return 加载到的资源文件列表
     */
    List<ResourceFile> load(RootNode root) {
        init(root);
        List<File> inputFiles = decompiler.getArgs().getInputFiles();
        List<ResourceFile> list = new ArrayList<>(inputFiles.size());
        for (File file : inputFiles) {
            loadFile(list, file);
        }
        return list;
    }

    /**
     * 初始化资源表解析器提供者和资源容器工厂
     *
     * @param root 根节点
     */
    private void init(RootNode root) {
        for (IResTableParserProvider resTableParserProvider : resTableParserProviders) {
            try {
                resTableParserProvider.init(root);
            } catch (Exception e) {
                throw new JadxRuntimeException("Failed to init res table provider: " + resTableParserProvider);
            }
        }
        for (IResContainerFactory resContainerFactory : resContainerFactories) {
            try {
                resContainerFactory.init(root);
            } catch (Exception e) {
                throw new JadxRuntimeException("Failed to init res container factory: " + resContainerFactory);
            }
        }
    }

    /**
     * 添加自定义资源容器工厂
     *
     * @param resContainerFactory 资源容器工厂实例
     */
    @Override
    public void addResContainerFactory(IResContainerFactory resContainerFactory) {
        resContainerFactories.add(resContainerFactory);
    }

    /**
     * 添加自定义资源表解析器提供者
     *
     * @param resTableParserProvider 资源表解析器提供者实例
     */
    @Override
    public void addResTableParserProvider(IResTableParserProvider resTableParserProvider) {
        resTableParserProviders.add(resTableParserProvider);
    }

    /**
     * 根据资源类型将输入流解析为对应的内容容器
     * 会优先尝试自定义资源容器工厂，其次按类型 (清单/XML ARSC 图片等)分别处理
     *
     * @param resFile     资源文件
     * @param inputStream 资源输入流
     * @return 资源内容容器
     * @throws IOException 读取或解析失败时抛出
     */
    private ResContainer loadContent(ResourceFile resFile, InputStream inputStream) throws IOException {
        for (IResContainerFactory customFactory : resContainerFactories) {
            ResContainer resContainer = customFactory.create(resFile, inputStream);
            if (resContainer != null) {
                return resContainer;
            }
        }
        switch (resFile.getType()) {
            case MANIFEST:
            case XML:
                ICodeInfo content = loadBinaryXmlParser().parse(inputStream);
                return ResContainer.textResource(resFile.getDeobfName(), content);

            case ARSC:
                return decodeTable(resFile, inputStream).decodeFiles();

            case IMG:
                return decodeImage(resFile, inputStream);

            default:
                return ResContainer.resourceFileLink(resFile);
        }
    }

    /**
     * 解析 ARSC/PB 资源表，返回资源表解析器
     *
     * @param resFile 资源文件 (类型必须为 {@link ResourceType#ARSC})
     * @param is      资源表输入流
     * @return 完成解析的资源表解析器
     * @throws IOException              读取失败时抛出
     * @throws IllegalArgumentException 资源类型不是 ARSC 时抛出
     */
    public IResTableParser decodeTable(ResourceFile resFile, InputStream is) throws IOException {
        if (resFile.getType() != ResourceType.ARSC) {
            throw new IllegalArgumentException("Unexpected resource type for decode: " + resFile.getType() + ", expect '.pb'/'.arsc'");
        }
        IResTableParser parser = null;
        for (IResTableParserProvider provider : resTableParserProviders) {
            parser = provider.getParser(resFile);
            if (parser != null) {
                break;
            }
        }
        if (parser == null) {
            throw new JadxRuntimeException("Unknown type of resource file: " + resFile.getOriginalName());
        }
        parser.setBaseFileName(resFile.getDeobfName());
        parser.decode(is);
        return parser;
    }

    /**
     * 加载单个文件的资源优先尝试自定义资源加载器，若均无法处理则回退到默认加载逻辑
     *
     * @param list 资源文件列表 (结果收集到此列表)
     * @param file 待加载的文件
     */
    private void loadFile(List<ResourceFile> list, File file) {
        if (file == null || file.isDirectory()) {
            return;
        }

        // 首先尝试使用自定义加载器加载资源
        for (CustomResourcesLoader loader : decompiler.getCustomResourcesLoaders()) {
            if (loader.load(this, list, file)) {
                LOG.debug("Custom loader used for {}", file.getAbsolutePath());
                return;
            }
        }

        // 若没有自定义解码器能够解码资源，则使用默认解码器
        defaultLoadFile(list, file, "");
    }

    /**
     * 默认的文件加载逻辑若为 ZIP/压缩包则遍历其条目逐一加入，否则作为单个资源文件加入
     *
     * @param list   资源文件列表 (结果收集到此列表)
     * @param file   待加载的文件
     * @param subDir 条目名称前缀 (子目录)，用于区分嵌套归档
     */
    public void defaultLoadFile(List<ResourceFile> list, File file, String subDir) {
        if (ByteUtils.isZipFile(file)) {
            try {
                ZipContent zipContent = decompiler.getZipReader().open(file);
                // 此处暂不关闭 zip，条目内容将在后续读取
                decompiler.addCloseable(zipContent);
                for (IZipEntry entry : zipContent.getEntries()) {
                    addEntry(list, file, entry, subDir);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to open zip file: " + file.getAbsolutePath(), e);
            }
        } else {
            ResourceType type = ResourceType.getFileType(file.getAbsolutePath());
            list.add(ResourceFile.createResourceFile(decompiler, file, type));
        }
    }

    /**
     * 将 ZIP 中的单个条目转换为资源文件并加入列表目录条目会被跳过
     *
     * @param list    资源文件列表 (结果收集到此列表)
     * @param zipFile 所属的 ZIP 文件
     * @param entry   ZIP 条目
     * @param subDir  条目名称前缀 (子目录)
     */
    public void addEntry(List<ResourceFile> list, File zipFile, IZipEntry entry, String subDir) {
        if (entry.isDirectory()) {
            return;
        }
        String name = entry.getName();
        ResourceType type = ResourceType.getFileType(name);
        ResourceFile rf = ResourceFile.createResourceFile(decompiler, subDir + name, type);
        if (rf != null) {
            rf.setZipEntry(entry);
            list.add(rf);
        }
    }

    /**
     * 懒加载并返回二进制 XML 解析器 (延迟初始化，线程安全)
     *
     * @return 二进制 XML 解析器实例
     */
    private synchronized BinaryXMLParser loadBinaryXmlParser() {
        if (binaryXmlParser == null) {
            binaryXmlParser = new BinaryXMLParser(decompiler.getRoot());
        }
        return binaryXmlParser;
    }

    /**
     * 资源解码器接口，用于将输入流解码为指定类型的对象
     *
     * @param <T> 解码后的目标类型
     */
    public interface ResourceDecoder<T> {
        T decode(long size, InputStream is) throws IOException;
    }
}
