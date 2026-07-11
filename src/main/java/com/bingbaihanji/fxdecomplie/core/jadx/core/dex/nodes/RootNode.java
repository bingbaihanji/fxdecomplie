package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bingbaihanji.fxdecomplie.core.jadx.api.DecompilationMode;
import com.bingbaihanji.fxdecomplie.core.jadx.api.ICodeCache;
import com.bingbaihanji.fxdecomplie.core.jadx.api.ICodeWriter;
import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxArgs;
import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxDecompiler;
import com.bingbaihanji.fxdecomplie.core.jadx.api.ResourceFile;
import com.bingbaihanji.fxdecomplie.core.jadx.api.ResourceType;
import com.bingbaihanji.fxdecomplie.core.jadx.api.ResourcesLoader;
import com.bingbaihanji.fxdecomplie.core.jadx.api.data.ICodeData;
import com.bingbaihanji.fxdecomplie.core.jadx.api.impl.passes.DecompilePassWrapper;
import com.bingbaihanji.fxdecomplie.core.jadx.api.impl.passes.PreparePassWrapper;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.ICodeLoader;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.IClassData;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass.JadxPass;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass.types.JadxDecompilePass;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass.types.JadxPassType;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass.types.JadxPreparePass;
import com.bingbaihanji.fxdecomplie.core.jadx.core.Jadx;
import com.bingbaihanji.fxdecomplie.core.jadx.core.ProcessClass;
import com.bingbaihanji.fxdecomplie.core.jadx.core.clsp.ClspGraph;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AttributeStorage;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.ClassInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.ConstStorage;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.FieldInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.InfoStorage;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.MethodInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.PackageInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.utils.MethodUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.utils.SelectFromDuplicates;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.utils.TypeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.DepthTraversal;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.IDexTreeVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference.TypeCompare;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference.TypeUpdate;
import com.bingbaihanji.fxdecomplie.core.jadx.core.export.GradleInfoStorage;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.CacheStorage;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.DebugChecks;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.ErrorsCounter;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.PassMerge;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.StringUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.android.AndroidResourcesUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen.IResTableParser;
import com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen.ManifestAttributes;
import com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen.ResourceStorage;
import com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen.entry.ResourceEntry;
import com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen.entry.ValuesParser;

/**
 * DEX 文件的根节点，是整个反编译模型的顶层容器。
 * <p>
 * 负责管理所有已加载的类（{@link ClassNode}）、包（{@link PackageNode}）、
 * 类路径图（{@link ClspGraph}）以及反编译遍历器（{@link IDexTreeVisitor}）。
 * 同时持有反编译参数（{@link JadxArgs}）、常量存储、信息存储和缓存等全局资源。
 */
public class RootNode {
	private static final Logger LOG = LoggerFactory.getLogger(RootNode.class);

	private final JadxArgs args;
	private final ErrorsCounter errorsCounter = new ErrorsCounter();
	private final StringUtils stringUtils;
	private final ConstStorage constValues;
	private final InfoStorage infoStorage = new InfoStorage();
	private final CacheStorage cacheStorage = new CacheStorage();
	private final TypeUpdate typeUpdate;
	private final MethodUtils methodUtils;
	private final TypeUtils typeUtils;
	private final AttributeStorage attributes = new AttributeStorage();

	private final List<ICodeDataUpdateListener> codeDataUpdateListeners = new ArrayList<>();
	private final GradleInfoStorage gradleInfoStorage = new GradleInfoStorage();

	private final Map<ClassInfo, ClassNode> clsMap = new HashMap<>();
	private final Map<String, ClassNode> rawClsMap = new HashMap<>();
	private List<ClassNode> classes = new ArrayList<>();

	private final Map<String, PackageNode> pkgMap = new HashMap<>();
	private final List<PackageNode> packages = new ArrayList<>();

	private List<IDexTreeVisitor> preDecompilePasses;
	private ProcessClass processClasses;

	private ClspGraph clsp;
	private @Nullable String appPackage;
	private @Nullable ClassNode appResClass;

	/**
	 * 可选的反编译器引用
	 */
	private @Nullable JadxDecompiler decompiler;

	private @Nullable ManifestAttributes manifestAttributes;

	public RootNode(JadxDecompiler decompiler) {
		this(decompiler, decompiler.getArgs());
	}

	/**
	 * 已废弃。推荐使用 {@link #RootNode(JadxDecompiler)}
	 */
	@Deprecated
	public RootNode(JadxArgs args) {
		this(null, args);
	}

	private RootNode(@Nullable JadxDecompiler decompiler, JadxArgs args) {
		this.decompiler = decompiler;
		this.args = args;
		this.preDecompilePasses = Jadx.getPreDecompilePassesList();
		this.processClasses = new ProcessClass(Jadx.getPassesList(args));
		this.stringUtils = new StringUtils(args);
		this.constValues = new ConstStorage(args);
		this.typeUpdate = new TypeUpdate(this);
		this.methodUtils = new MethodUtils(this);
		this.typeUtils = new TypeUtils(this);
	}

	/**
	 * 初始化根节点。当启用混淆还原或重命名标志时，初始化别名提供器和重命名条件。
	 */
	public void init() {
		if (args.isDeobfuscationOn() || !args.getRenameFlags().isEmpty()) {
			args.getAliasProvider().init(this);
		}
		if (args.isDeobfuscationOn()) {
			args.getRenameCondition().init(this);
		}
	}

	/**
	 * 从加载的输入源加载所有类
	 * @param loadedInputs 已加载的代码输入源列表
	 */
	public void loadClasses(List<ICodeLoader> loadedInputs) {
		for (ICodeLoader codeLoader : loadedInputs) {
			codeLoader.visitClasses(cls -> {
				try {
					addClassNode(new ClassNode(RootNode.this, cls));
				} catch (Exception e) {
					addDummyClass(cls, e);
				}
				Utils.checkThreadInterrupt();
			});
		}
	}

	/**
	 * 完成类加载后的处理流程：
	 * 1. 检测并修复重复类名
	 * 2. 打印已加载类、方法和指令的统计信息
	 * 3. 按名称排序类（顶层类排在内部类之前）
	 * 4. 可选地检测并移动内部类到其父类中
	 * 5. 排序包列表
	 */
	public void finishClassLoad() {
		if (classes.size() != clsMap.size()) {
			// 检测到类名重复
			fixDuplicatedClasses();
		}
		classes = new ArrayList<>(clsMap.values());

		// 打印已加载类的统计信息
		int mthCount = classes.stream().mapToInt(c -> c.getMethods().size()).sum();
		int insnsCount = classes.stream().flatMap(c -> c.getMethods().stream()).mapToInt(MethodNode::getInsnsCount).sum();
		LOG.info("Loaded classes: {}, methods: {}, instructions: {}", classes.size(), mthCount, insnsCount);

		// 按名称排序类，顶层类排在内部类之前
		classes.sort(Comparator.comparing(ClassNode::getRawName));

		if (args.isMoveInnerClasses()) {
			// 检测并移动内部类
			initInnerClasses();
		}
		// 排序包列表
		Collections.sort(packages);
	}

	/**
	 * 当类加载失败时，创建一个占位的合成类节点，用于记录错误信息。
	 *
	 * @param classData 原始类数据
	 * @param exc       加载过程中发生的异常
	 */
	private void addDummyClass(IClassData classData, Exception exc) {
		try {
			String typeStr = classData.getType();
			String name = null;
			try {
				ClassInfo clsInfo = ClassInfo.fromName(this, typeStr);
				if (clsInfo != null) {
					name = clsInfo.getShortName();
				}
			} catch (Exception e) {
				LOG.error("Failed to get name for class with type {}", typeStr, e);
			}
			if (name == null || name.isEmpty()) {
				name = "CLASS_" + typeStr;
			}
			ClassNode clsNode = ClassNode.addSyntheticClass(this, name, classData.getAccessFlags());
			ErrorsCounter.error(clsNode, "Load error", exc);
		} catch (Exception innerExc) {
			LOG.error("Failed to load class from file: {}", classData.getInputFileName(), exc);
		}
	}

	/**
	 * 修复重复类名问题。当多个输入源中存在相同全限定名的类时，
	 * 通过 {@link SelectFromDuplicates} 策略选择保留其中一个，并移除其余重复项。
	 */
	private void fixDuplicatedClasses() {
		classes.stream()
				.collect(Collectors.groupingBy(ClassNode::getClassInfo))
				.entrySet()
				.stream()
				.filter(entry -> entry.getValue().size() > 1)
				.forEach(entry -> {
					ClassInfo clsInfo = entry.getKey();
					List<ClassNode> dupClsList = entry.getValue();
					ClassNode selectedCls = SelectFromDuplicates.process(dupClsList);

					// 仅在类映射中保留选定的类
					clsMap.put(clsInfo, selectedCls);
					rawClsMap.put(selectedCls.getRawName(), selectedCls);

					String selectedSource = selectedCls.getInputFileName();
					String sources = dupClsList.stream()
							.map(ClassNode::getInputFileName)
							.sorted()
							.collect(Collectors.joining("\n  "));
					LOG.warn("Found duplicated class: {}, count: {}, sources:"
							+ "\n  {}\n Keep class with source: {}, others will be removed.",
							clsInfo, dupClsList.size(), sources, selectedSource);
					selectedCls.addWarnComment("Classes with same name are omitted, all sources:\n  " + sources + '\n');
				});
	}

	/**
	 * 将类节点添加到根节点中，同时更新类信息映射和原始名称映射。
	 *
	 * @param clsNode 要添加的类节点
	 */
	public void addClassNode(ClassNode clsNode) {
		classes.add(clsNode);
		clsMap.put(clsNode.getClassInfo(), clsNode);
		rawClsMap.put(clsNode.getRawName(), clsNode);
	}

	/**
	 * 加载并处理 Android 资源文件。解析 resources.arsc 或 resources.pb 文件，
	 * 处理资源存储、更新混淆的资源文件名，并初始化 Manifest 属性。
	 *
	 * @param resLoader 资源加载器
	 * @param resources 资源文件列表
	 */
	public void loadResources(ResourcesLoader resLoader, List<ResourceFile> resources) {
		ResourceFile arsc = getResourceFile(resources);
		if (arsc == null) {
			LOG.debug("'resources.arsc' or 'resources.pb' file not found");
			return;
		}
		try {
			IResTableParser parser = ResourcesLoader.decodeStream(arsc, (size, is) -> resLoader.decodeTable(arsc, is));
			if (parser != null) {
				processResources(parser.getResStorage());
				updateObfuscatedFiles(parser, resources);
				initManifestAttributes().updateAttributes(parser);
			}
		} catch (Exception e) {
			LOG.error("Failed to parse 'resources.pb'/'.arsc' file", e);
		}
	}

	/**
	 * 从资源文件列表中查找 ARSC 类型的资源文件。
	 *
	 * @param resources 资源文件列表
	 * @return 找到的 ARSC 资源文件，未找到则返回 null
	 */
	private @Nullable ResourceFile getResourceFile(List<ResourceFile> resources) {
		for (ResourceFile rf : resources) {
			if (rf.getType() == ResourceType.ARSC) {
				return rf;
			}
		}
		return null;
	}

	/**
	 * 处理资源存储数据，设置资源名称常量、应用包名，并搜索应用资源类。
	 *
	 * @param resStorage 资源存储对象
	 */
	public void processResources(ResourceStorage resStorage) {
		constValues.setResourcesNames(resStorage.getResourcesNames());
		appPackage = resStorage.getAppPackage();
		appResClass = AndroidResourcesUtils.searchAppResClass(this, resStorage);
	}

	/**
	 * 初始化类路径图（{@link ClspGraph}）。加载 jadx 类集合文件，
	 * 将应用中的类添加到图中，并初始化缓存。
	 */
	public void initClassPath() {
		try {
			if (this.clsp == null) {
				ClspGraph newClsp = new ClspGraph(this);
				if (args.isLoadJadxClsSetFile()) {
					newClsp.loadClsSetFile();
				}
				newClsp.addApp(classes);
				newClsp.initCache();
				this.clsp = newClsp;
			}
		} catch (Exception e) {
			throw new JadxRuntimeException("Error loading jadx class set", e);
		}
	}

	/**
	 * 更新混淆的资源文件名。通过资源表中的条目名称匹配资源文件，
	 * 并根据配置设置资源文件的别名（原始名称）。
	 *
	 * @param parser    资源表解析器
	 * @param resources 资源文件列表
	 */
	private void updateObfuscatedFiles(IResTableParser parser, List<ResourceFile> resources) {
		if (args.isSkipResources()) {
			return;
		}
		boolean useHeaders = args.isUseHeadersForDetectResourceExtensions();
		long start = System.currentTimeMillis();
		int renamedCount = 0;
		ResourceStorage resStorage = parser.getResStorage();
		ValuesParser valuesParser = new ValuesParser(parser.getStrings(), resStorage.getResourcesNames());
		Map<String, ResourceEntry> entryNames = new HashMap<>();
		for (ResourceEntry resEntry : resStorage.getResources()) {
			String val = valuesParser.getSimpleValueString(resEntry);
			if (val != null) {
				entryNames.put(val, resEntry);
			}
		}
		for (ResourceFile resource : resources) {
			ResourceEntry resEntry = entryNames.get(resource.getOriginalName());
			if (resEntry != null) {
				if (resource.setAlias(resEntry, useHeaders)) {
					renamedCount++;
				}
			}
		}
		if (LOG.isDebugEnabled()) {
			LOG.debug("Renamed obfuscated resources: {}, duration: {}ms", renamedCount, System.currentTimeMillis() - start);
		}
	}

	/**
	 * 初始化内部类关系。将内部类移动到其父类中，
	 * 并处理无法找到父类的内部类（将其标记为非内部类）。
	 */
	private void initInnerClasses() {
		// 移动内部类
		List<ClassNode> inner = new ArrayList<>();
		for (ClassNode cls : classes) {
			if (cls.getClassInfo().isInner()) {
				inner.add(cls);
			}
		}
		List<ClassNode> updated = new ArrayList<>();
		for (ClassNode cls : inner) {
			ClassInfo clsInfo = cls.getClassInfo();
			ClassNode parent = resolveParentClass(clsInfo);
			if (parent == null) {
				updated.add(cls);
				cls.notInner();
			} else {
				parent.addInnerClass(cls);
			}
		}
		// 重新加载已更新父类的内部类名称
		for (ClassNode updCls : updated) {
			for (ClassNode innerCls : updCls.getInnerClasses()) {
				innerCls.getClassInfo().updateNames(this);
			}
		}
		for (PackageNode pkg : packages) {
			pkg.getClasses().removeIf(cls -> cls.getClassInfo().isInner());
		}
	}

	/**
	 * 合并自定义遍历器到反编译流程中。对于预定义模式（FALLBACK、SIMPLE），
	 * 忽略自定义遍历器；否则将自定义的准备阶段和反编译阶段遍历器合并到现有流程中。
	 * 同时处理调试检查和禁用遍历器的配置。
	 *
	 * @param customPasses 按类型分组的自定义遍历器映射
	 */
	public void mergePasses(Map<JadxPassType, List<JadxPass>> customPasses) {
		DecompilationMode mode = args.getDecompilationMode();
		if (mode == DecompilationMode.FALLBACK || mode == DecompilationMode.SIMPLE) {
			// 对于预定义模式，忽略自定义（和插件）遍历器
			return;
		}

		new PassMerge(preDecompilePasses)
				.merge(customPasses.get(JadxPreparePass.TYPE), p -> new PreparePassWrapper((JadxPreparePass) p));
		new PassMerge(processClasses.getPasses())
				.merge(customPasses.get(JadxDecompilePass.TYPE), p -> new DecompilePassWrapper((JadxDecompilePass) p));

		if (args.isRunDebugChecks()) {
			preDecompilePasses = DebugChecks.insertPasses(preDecompilePasses);
			processClasses = new ProcessClass(DebugChecks.insertPasses(processClasses.getPasses()));
		}
		List<String> disabledPasses = args.getDisabledPasses();
		if (!disabledPasses.isEmpty()) {
			Set<String> disabledSet = new HashSet<>(disabledPasses);
			Predicate<IDexTreeVisitor> filter = p -> {
				if (disabledSet.contains(p.getName())) {
					LOG.debug("Disable pass: {}", p.getName());
					return true;
				}
				return false;
			};
			preDecompilePasses.removeIf(filter);
			processClasses.getPasses().removeIf(filter);
		}
	}

	/**
	 * 运行反编译前的准备阶段。遍历所有准备阶段的遍历器，
	 * 对每个非内部类执行深度优先遍历。记录每个遍历器的执行耗时（DEBUG 级别）。
	 */
	public void runPreDecompileStage() {
		boolean debugEnabled = LOG.isDebugEnabled();
		for (IDexTreeVisitor pass : preDecompilePasses) {
			Utils.checkThreadInterrupt();
			long start = debugEnabled ? System.currentTimeMillis() : 0;
			try {
				pass.init(this);
			} catch (Exception e) {
				LOG.error("Visitor init failed: {}", pass.getClass().getSimpleName(), e);
			}
			for (ClassNode cls : classes) {
				if (cls.isInner()) {
					continue;
				}
				DepthTraversal.visit(pass, cls);
			}
			if (debugEnabled) {
				LOG.debug("Prepare pass: '{}' - {}ms", pass, System.currentTimeMillis() - start);
			}
		}
	}

	/**
	 * 为单个类运行反编译前的准备阶段遍历器。
	 *
	 * @param cls 要处理的类节点
	 */
	public void runPreDecompileStageForClass(ClassNode cls) {
		for (IDexTreeVisitor pass : preDecompilePasses) {
			DepthTraversal.visit(pass, cls);
		}
	}

	// TODO: 为重新加载遍历器列表创建更好的 API
	/**
	 * 重置所有遍历器列表为默认状态，用于插件或自定义遍历器变更后重新加载。
	 */
	public void resetPasses() {
		preDecompilePasses.clear();
		preDecompilePasses.addAll(Jadx.getPreDecompilePassesList());

		processClasses.getPasses().clear();
		processClasses.getPasses().addAll(Jadx.getPassesList(args));
	}

	/**
	 * 重启所有遍历器。卸载所有类的缓存数据，清除属性，并重新运行准备阶段。
	 */
	public void restartVisitors() {
		for (ClassNode cls : classes) {
			cls.unload();
			cls.clearAttributes();
			cls.unloadFromCache();
		}
		runPreDecompileStage();
	}

	public List<ClassNode> getClasses() {
		return classes;
	}

	public List<ClassNode> getClassesWithoutInner() {
		return getClasses(false);
	}

	public List<ClassNode> getClasses(boolean includeInner) {
		if (includeInner) {
			return classes;
		}
		List<ClassNode> notInnerClasses = new ArrayList<>();
		for (ClassNode cls : classes) {
			if (!cls.getClassInfo().isInner()) {
				notInnerClasses.add(cls);
			}
		}
		return notInnerClasses;
	}

	public List<PackageNode> getPackages() {
		return packages;
	}

	public @Nullable PackageNode resolvePackage(String fullPkg) {
		return pkgMap.get(fullPkg);
	}

	public @Nullable PackageNode resolvePackage(@Nullable PackageInfo pkgInfo) {
		return pkgInfo == null ? null : pkgMap.get(pkgInfo.getFullName());
	}

	public void addPackage(PackageNode pkg) {
		pkgMap.put(pkg.getPkgInfo().getFullName(), pkg);
		packages.add(pkg);
	}

	public void removePackage(PackageNode pkg) {
		if (pkgMap.remove(pkg.getPkgInfo().getFullName()) != null) {
			packages.remove(pkg);
			PackageNode parentPkg = pkg.getParentPkg();
			if (parentPkg != null) {
				parentPkg.getSubPackages().remove(pkg);
				if (parentPkg.isEmpty()) {
					removePackage(parentPkg);
				}
			}
			for (PackageNode subPkg : pkg.getSubPackages()) {
				removePackage(subPkg);
			}
		}
	}

	public void sortPackages() {
		Collections.sort(packages);
	}

	public void removeClsFromPackage(PackageNode pkg, ClassNode cls) {
		boolean removed = pkg.getClasses().remove(cls);
		if (removed && pkg.isEmpty()) {
			removePackage(pkg);
		}
	}

	/**
	 * Update sub packages
	 */
	public void runPackagesUpdate() {
		for (PackageNode pkg : getPackages()) {
			if (pkg.isRoot()) {
				pkg.updatePackages();
			}
		}
	}

	@Nullable
	public ClassNode resolveClass(ClassInfo clsInfo) {
		return clsMap.get(clsInfo);
	}

	@Nullable
	public ClassNode resolveClass(ArgType clsType) {
		if (!clsType.isTypeKnown() || clsType.isGenericType()) {
			return null;
		}
		if (clsType.getWildcardBound() == ArgType.WildcardBound.UNBOUND) {
			return null;
		}
		if (clsType.isGeneric()) {
			clsType = ArgType.object(clsType.getObject());
		}
		return resolveClass(ClassInfo.fromType(this, clsType));
	}

	@Nullable
	public ClassNode resolveClass(String fullName) {
		ClassInfo clsInfo = ClassInfo.fromName(this, fullName);
		return resolveClass(clsInfo);
	}

	@Nullable
	public ClassNode resolveRawClass(String rawFullName) {
		return rawClsMap.get(rawFullName);
	}

	/**
	 * Find and correct the parent of an inner class.
	 * <br>
	 * Sometimes inner ClassInfo generated wrong parent info.
	 * e.g. inner is `Cls$mth$1`, current parent = `Cls$mth`, real parent = `Cls`
	 */
	@Nullable
	public ClassNode resolveParentClass(ClassInfo clsInfo) {
		ClassInfo parentInfo = clsInfo.getParentClass();
		ClassNode parentNode = resolveClass(parentInfo);
		if (parentNode == null && parentInfo != null) {
			String parClsName = parentInfo.getFullName();
			// strip last part as method name
			int sep = parClsName.lastIndexOf('.');
			if (sep > 0 && sep != parClsName.length() - 1) {
				String mthName = parClsName.substring(sep + 1);
				String upperParClsName = parClsName.substring(0, sep);
				ClassNode tmpParent = resolveClass(upperParClsName);
				if (tmpParent != null && tmpParent.searchMethodByShortName(mthName) != null) {
					parentNode = tmpParent;
					clsInfo.convertToInner(parentNode);
				}
			}
		}
		return parentNode;
	}

	/**
	 * Searches for ClassNode by its full name (original or alias name)
	 * <br>
	 * Warning: This method has a runtime of O(n) (n = number of classes).
	 * If you need to call it more than once consider {@link #buildFullAliasClassCache()} instead
	 */
	@Nullable
	public ClassNode searchClassByFullAlias(String fullName) {
		for (ClassNode cls : classes) {
			ClassInfo classInfo = cls.getClassInfo();
			if (classInfo.getFullName().equals(fullName)
					|| classInfo.getAliasFullName().equals(fullName)) {
				return cls;
			}
		}
		return null;
	}

	public Map<String, ClassNode> buildFullAliasClassCache() {
		Map<String, ClassNode> classNameCache = new HashMap<>(classes.size());
		for (ClassNode cls : classes) {
			ClassInfo classInfo = cls.getClassInfo();
			String fullName = classInfo.getFullName();
			String alias = classInfo.getAliasFullName();
			classNameCache.put(fullName, cls);
			if (alias != null && !fullName.equals(alias)) {
				classNameCache.put(alias, cls);
			}
		}
		return classNameCache;
	}

	public List<ClassNode> searchClassByShortName(String shortName) {
		List<ClassNode> list = new ArrayList<>();
		for (ClassNode cls : classes) {
			if (cls.getClassInfo().getShortName().equals(shortName)) {
				list.add(cls);
			}
		}
		return list;
	}

	@Nullable
	public MethodNode resolveMethod(@NotNull MethodInfo mth) {
		ClassNode cls = resolveClass(mth.getDeclClass());
		if (cls == null) {
			return null;
		}
		MethodNode methodNode = cls.searchMethod(mth);
		if (methodNode != null) {
			return methodNode;
		}
		return deepResolveMethod(cls, mth.makeSignature(false));
	}

	public @NotNull MethodNode resolveDirectMethod(String rawClsName, String mthShortId) {
		ClassNode clsNode = resolveRawClass(rawClsName);
		if (clsNode == null) {
			throw new RuntimeException("Class not found: " + rawClsName);
		}
		MethodNode methodNode = clsNode.searchMethodByShortId(mthShortId);
		if (methodNode == null) {
			throw new RuntimeException("Method not found: " + rawClsName + "." + mthShortId);
		}
		return methodNode;
	}

	@Nullable
	private MethodNode deepResolveMethod(@NotNull ClassNode cls, String signature) {
		for (MethodNode m : cls.getMethods()) {
			if (m.getMethodInfo().getShortId().startsWith(signature)) {
				return m;
			}
		}
		MethodNode found;
		ArgType superClass = cls.getSuperClass();
		if (superClass != null) {
			ClassNode superNode = resolveClass(superClass);
			if (superNode != null) {
				found = deepResolveMethod(superNode, signature);
				if (found != null) {
					return found;
				}
			}
		}
		for (ArgType iFaceType : cls.getInterfaces()) {
			ClassNode iFaceNode = resolveClass(iFaceType);
			if (iFaceNode != null) {
				found = deepResolveMethod(iFaceNode, signature);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	@Nullable
	public FieldNode resolveField(FieldInfo field) {
		ClassNode cls = resolveClass(field.getDeclClass());
		if (cls == null) {
			return null;
		}
		FieldNode fieldNode = cls.searchField(field);
		if (fieldNode != null) {
			return fieldNode;
		}
		return deepResolveField(cls, field);
	}

	@Nullable
	private FieldNode deepResolveField(@NotNull ClassNode cls, FieldInfo fieldInfo) {
		FieldNode field = cls.searchFieldByNameAndType(fieldInfo);
		if (field != null) {
			return field;
		}
		ArgType superClass = cls.getSuperClass();
		if (superClass != null) {
			ClassNode superNode = resolveClass(superClass);
			if (superNode != null) {
				FieldNode found = deepResolveField(superNode, fieldInfo);
				if (found != null) {
					return found;
				}
			}
		}
		for (ArgType iFaceType : cls.getInterfaces()) {
			ClassNode iFaceNode = resolveClass(iFaceType);
			if (iFaceNode != null) {
				FieldNode found = deepResolveField(iFaceNode, fieldInfo);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	public ProcessClass getProcessClasses() {
		return processClasses;
	}

	public List<IDexTreeVisitor> getPasses() {
		return processClasses.getPasses();
	}

	public List<IDexTreeVisitor> getPreDecompilePasses() {
		return preDecompilePasses;
	}

	public void initPasses() {
		processClasses.initPasses(this);
	}

	public ICodeWriter makeCodeWriter() {
		JadxArgs jadxArgs = this.args;
		return jadxArgs.getCodeWriterProvider().apply(jadxArgs);
	}

	public void registerCodeDataUpdateListener(ICodeDataUpdateListener listener) {
		this.codeDataUpdateListeners.add(listener);
	}

	public void notifyCodeDataListeners() {
		ICodeData codeData = args.getCodeData();
		codeDataUpdateListeners.forEach(l -> l.updated(codeData));
	}

	public ClspGraph getClsp() {
		return clsp;
	}

	public ErrorsCounter getErrorsCounter() {
		return errorsCounter;
	}

	@Nullable
	public String getAppPackage() {
		return appPackage;
	}

	@Nullable
	public ClassNode getAppResClass() {
		return appResClass;
	}

	public StringUtils getStringUtils() {
		return stringUtils;
	}

	public ConstStorage getConstValues() {
		return constValues;
	}

	public InfoStorage getInfoStorage() {
		return infoStorage;
	}

	public CacheStorage getCacheStorage() {
		return cacheStorage;
	}

	public JadxArgs getArgs() {
		return args;
	}

	public @Nullable JadxDecompiler getDecompiler() {
		return decompiler;
	}

	public TypeUpdate getTypeUpdate() {
		return typeUpdate;
	}

	public TypeCompare getTypeCompare() {
		return typeUpdate.getTypeCompare();
	}

	public ICodeCache getCodeCache() {
		return args.getCodeCache();
	}

	public MethodUtils getMethodUtils() {
		return methodUtils;
	}

	public TypeUtils getTypeUtils() {
		return typeUtils;
	}

	public AttributeStorage getAttributes() {
		return attributes;
	}

	public GradleInfoStorage getGradleInfoStorage() {
		return gradleInfoStorage;
	}

	public synchronized ManifestAttributes initManifestAttributes() {
		ManifestAttributes attrs = manifestAttributes;
		if (attrs == null) {
			attrs = new ManifestAttributes(args.getSecurity());
			manifestAttributes = attrs;
		}
		return attrs;
	}
}
