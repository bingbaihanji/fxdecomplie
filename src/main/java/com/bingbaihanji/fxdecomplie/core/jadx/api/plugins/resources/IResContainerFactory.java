package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.resources;

import com.bingbaihanji.fxdecomplie.core.jadx.api.ResourceFile;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen.ResContainer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;

/**
 * Factory for {@link ResContainer}. Can be used in plugins via
 * {@code context.getResourcesLoader().addResContainerFactory()} to implement content parsing in
 * files with
 * different formats.
 */
public interface IResContainerFactory {

    /**
     * Optional init method
     */
    default void init(RootNode root) {
    }

    /**
     * Checks if resource file is of expected format and tries to parse its content.
     *
     * @return {@link ResContainer} if file is of expected format, {@code null} otherwise.
     */
    @Nullable
    ResContainer create(ResourceFile resFile, InputStream inputStream) throws IOException;
}
