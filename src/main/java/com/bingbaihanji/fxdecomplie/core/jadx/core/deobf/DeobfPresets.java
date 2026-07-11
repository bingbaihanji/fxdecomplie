package com.bingbaihanji.fxdecomplie.core.jadx.core.deobf;

import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxArgs;
import com.bingbaihanji.fxdecomplie.core.jadx.api.args.GeneratedRenamesMappingFileMode;
import com.bingbaihanji.fxdecomplie.core.jadx.api.deobf.IAliasProvider;
import com.bingbaihanji.fxdecomplie.core.jadx.api.deobf.impl.AlwaysRename;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.ClassInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.FieldInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.MethodInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.*;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.files.IoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

public class DeobfPresets {
    private static final Logger LOG = LoggerFactory.getLogger(DeobfPresets.class);

    private static final Charset MAP_FILE_CHARSET = UTF_8;

    private final Path deobfMapFile;

    private final Map<String, String> pkgPresetMap = new HashMap<>();
    private final Map<String, String> clsPresetMap = new HashMap<>();
    private final Map<String, String> fldPresetMap = new HashMap<>();
    private final Map<String, String> mthPresetMap = new HashMap<>();

    private DeobfPresets(Path deobfMapFile) {
        this.deobfMapFile = deobfMapFile;
    }

    public static DeobfPresets build(RootNode root) {
        Path deobfMapPath = getPathDeobfMapPath(root);
        if (root.getArgs().getGeneratedRenamesMappingFileMode() != GeneratedRenamesMappingFileMode.IGNORE) {
            LOG.debug("Deobfuscation map file set to: {}", deobfMapPath);
        }
        return new DeobfPresets(deobfMapPath);
    }

    private static Path getPathDeobfMapPath(RootNode root) {
        JadxArgs jadxArgs = root.getArgs();
        File deobfMapFile = jadxArgs.getGeneratedRenamesMappingFile();
        if (deobfMapFile != null) {
            return deobfMapFile.toPath();
        }
        List<File> inputFiles = jadxArgs.getInputFiles();
        if (!inputFiles.isEmpty()) {
            Path inputFilePath = inputFiles.get(0).toPath().toAbsolutePath();
            String baseName = IoUtils.getPathBaseName(inputFilePath);
            return inputFilePath.getParent().resolve(baseName + ".jobf");
        }
        // 无输入文件时（如直接反编译内存中的类），回退到输出目录
        return jadxArgs.getOutDir().toPath().toAbsolutePath().resolve("default.jobf");
    }

    private static String[] splitAndTrim(String str) {
        String[] v = str.substring(2).split("=");
        for (int i = 0; i < v.length; i++) {
            v[i] = v[i].trim();
        }
        return v;
    }

    /**
     * Loads deobfuscator presets
     */
    public boolean load() {
        if (!Files.exists(deobfMapFile)) {
            return false;
        }
        LOG.info("Loading obfuscation map from: {}", deobfMapFile.toAbsolutePath());
        try {
            List<String> lines = Files.readAllLines(deobfMapFile, MAP_FILE_CHARSET);
            for (String l : lines) {
                l = l.trim();
                if (l.isEmpty() || l.startsWith("#")) {
                    continue;
                }
                String[] va = splitAndTrim(l);
                if (va.length != 2) {
                    continue;
                }
                String origName = va[0];
                String alias = va[1];
                switch (l.charAt(0)) {
                    case 'p':
                        pkgPresetMap.put(origName, alias);
                        break;
                    case 'c':
                        clsPresetMap.put(origName, alias);
                        break;
                    case 'f':
                        fldPresetMap.put(origName, alias);
                        break;
                    case 'm':
                        mthPresetMap.put(origName, alias);
                        break;
                    case 'v':
                        // deprecated
                        break;
                }
            }
            return true;
        } catch (Exception e) {
            LOG.error("Failed to load deobfuscation map file '{}'", deobfMapFile.toAbsolutePath(), e);
            return false;
        }
    }

    public void save() throws IOException {
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, String> pkgEntry : pkgPresetMap.entrySet()) {
            list.add(String.format("p %s = %s", pkgEntry.getKey(), pkgEntry.getValue()));
        }
        for (Map.Entry<String, String> clsEntry : clsPresetMap.entrySet()) {
            list.add(String.format("c %s = %s", clsEntry.getKey(), clsEntry.getValue()));
        }
        for (Map.Entry<String, String> fldEntry : fldPresetMap.entrySet()) {
            list.add(String.format("f %s = %s", fldEntry.getKey(), fldEntry.getValue()));
        }
        for (Map.Entry<String, String> mthEntry : mthPresetMap.entrySet()) {
            list.add(String.format("m %s = %s", mthEntry.getKey(), mthEntry.getValue()));
        }
        Collections.sort(list);
        if (list.isEmpty()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Deobfuscation map is empty, not saving it");
            }
            return;
        }
        Files.write(deobfMapFile, list, MAP_FILE_CHARSET,
                StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        LOG.info("Deobfuscation map file saved as: {}", deobfMapFile);
    }

    public void fill(RootNode root) {
        for (PackageNode pkg : root.getPackages()) {
            if (pkg.isLeaf()) { // ignore middle packages
                if (pkg.hasParentAlias()) {
                    pkgPresetMap.put(pkg.getPkgInfo().getFullName(), pkg.getAliasPkgInfo().getFullName());
                } else if (pkg.hasAlias()) {
                    pkgPresetMap.put(pkg.getPkgInfo().getFullName(), pkg.getAliasPkgInfo().getName());
                }
            }
        }
        for (ClassNode cls : root.getClasses()) {
            ClassInfo classInfo = cls.getClassInfo();
            if (classInfo.hasAlias()) {
                clsPresetMap.put(classInfo.makeRawFullName(), classInfo.getAliasShortName());
            }
            for (FieldNode fld : cls.getFields()) {
                FieldInfo fieldInfo = fld.getFieldInfo();
                if (fieldInfo.hasAlias()) {
                    fldPresetMap.put(fieldInfo.getRawFullId(), fld.getAlias());
                }
            }
            for (MethodNode mth : cls.getMethods()) {
                MethodInfo methodInfo = mth.methodInfo();
                if (methodInfo.hasAlias()) {
                    mthPresetMap.put(methodInfo.getRawFullId(), methodInfo.getAlias());
                }
            }
        }
    }

    public void apply(RootNode root) {
        DeobfuscatorVisitor.process(root,
                AlwaysRename.INSTANCE,
                new IAliasProvider() {
                    @Override
                    public String forPackage(PackageNode pkg) {
                        return pkgPresetMap.get(pkg.getPkgInfo().getFullName());
                    }

                    @Override
                    public String forClass(ClassNode cls) {
                        return getForCls(cls.getClassInfo());
                    }

                    @Override
                    public String forField(FieldNode fld) {
                        return getForFld(fld.getFieldInfo());
                    }

                    @Override
                    public String forMethod(MethodNode mth) {
                        return getForMth(mth.methodInfo());
                    }
                });
    }

    public void initIndexes(IAliasProvider aliasProvider) {
        aliasProvider.initIndexes(pkgPresetMap.size(), clsPresetMap.size(), fldPresetMap.size(), mthPresetMap.size());
    }

    public String getForCls(ClassInfo cls) {
        if (clsPresetMap.isEmpty()) {
            return null;
        }
        return clsPresetMap.get(cls.makeRawFullName());
    }

    public String getForFld(FieldInfo fld) {
        if (fldPresetMap.isEmpty()) {
            return null;
        }
        return fldPresetMap.get(fld.getRawFullId());
    }

    public String getForMth(MethodInfo mth) {
        if (mthPresetMap.isEmpty()) {
            return null;
        }
        return mthPresetMap.get(mth.getRawFullId());
    }

    public void clear() {
        pkgPresetMap.clear();
        clsPresetMap.clear();
        fldPresetMap.clear();
        mthPresetMap.clear();
    }

    public Path getDeobfMapFile() {
        return deobfMapFile;
    }

    public Map<String, String> getPkgPresetMap() {
        return pkgPresetMap;
    }

    public Map<String, String> getClsPresetMap() {
        return clsPresetMap;
    }

    public Map<String, String> getFldPresetMap() {
        return fldPresetMap;
    }

    public Map<String, String> getMthPresetMap() {
        return mthPresetMap;
    }
}
