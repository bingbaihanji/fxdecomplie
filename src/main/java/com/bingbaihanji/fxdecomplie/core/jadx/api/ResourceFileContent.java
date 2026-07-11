package com.bingbaihanji.fxdecomplie.core.jadx.api;

import com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen.ResContainer;

/**
 * 带有内容的资源文件
 * <p>
 * 与 {@link ResourceFile} 不同，该类在创建时便直接持有资源的代码内容（{@link ICodeInfo}），
 * 适用于内容已在内存中、无需再从文件加载的场景
 */
public class ResourceFileContent extends ResourceFile {
    /** 资源的代码内容 */
    private final ICodeInfo content;

    /**
     * 构造带有内容的资源文件
     *
     * @param name    资源名称
     * @param type    资源类型
     * @param content 资源的代码内容
     */
    public ResourceFileContent(String name, ResourceType type, ICodeInfo content) {
        super(null, name, type);
        this.content = content;
    }

    /** 加载资源内容，直接以持有的文本内容构建资源容器 */
    @Override
    public ResContainer loadContent() {
        return ResContainer.textResource(getDeobfName(), content);
    }
}
