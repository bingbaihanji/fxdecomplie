package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.DecompilationMode;
import com.bingbaihanji.fxdecomplie.core.jadx.api.ICodeCache;
import com.bingbaihanji.fxdecomplie.core.jadx.api.ICodeInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.api.JavaClass;
import com.bingbaihanji.fxdecomplie.core.jadx.api.impl.SimpleCodeInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.api.impl.SimpleCodeWriter;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeAnnotation;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.annotations.NodeDeclareRef;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.annotations.VarRef;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.IClassData;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.IFieldData;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.IMethodData;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.annotations.EncodedValue;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.JadxAttrType;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.types.*;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.impl.ListConsumer;
import com.bingbaihanji.fxdecomplie.core.jadx.api.usage.IUsageInfoData;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.InlinedAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.NotificationAttrNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.AccessInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.AccessInfo.AFType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.ClassInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.FieldInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.MethodInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.LiteralArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.utils.TypeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import com.bingbaihanji.fxdecomplie.util.collection.ListUtils;
import com.bingbaihanji.fxdecomplie.util.jadx.JadxConsts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ProcessState.LOADED;
import static com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ProcessState.NOT_LOADED;

/**
 * 类节点，表示 DEX 文件中的一个类
 * 负责管理类的字段 方法 内部类 泛型信息以及反编译缓存等
 * 实现了加载/卸载生命周期 代码生成 依赖管理和包更新通知等功能
 */
public class ClassNode extends NotificationAttrNode
        implements ILoadable, ICodeNode, IPackageUpdate, Comparable<ClassNode> {
    private static final Logger LOG = LoggerFactory.getLogger(ClassNode.class);
    private static final Object DECOMPILE_WITH_MODE_SYNC = new Object();
    /** 根节点引用 */
    private final RootNode root;
    /** 原始类数据 */
    private final IClassData clsData;
    /** 类的元信息 (包名 类名等) */
    private final ClassInfo clsInfo;
    /** 所属包节点 */
    private PackageNode packageNode;
    /** 访问标志 */
    private AccessInfo accessFlags;
    /** 父类类型 */
    private ArgType superClass;
    /** 实现的接口列表 */
    private List<ArgType> interfaces;
    /** 泛型参数列表 */
    private List<ArgType> generics = Collections.emptyList();
    /** 输入文件名 */
    private String inputFileName;
    /** 方法列表 */
    private List<MethodNode> methods;
    /** 字段列表 */
    private List<FieldNode> fields;
    /** 内部类列表 */
    private List<ClassNode> innerClasses = Collections.emptyList();
    /** 被内联的类列表 */
    private List<ClassNode> inlinedClasses = Collections.emptyList();
    /** 缓存的 smali 反汇编代码 */
    private String smali;
    /** 内部类存储其外部类引用，非内部类存储自身引用 */
    private ClassNode parentClass = this;
    /** 处理状态 (volatile 保证多线程可见性) */
    private volatile ProcessState state = ProcessState.NOT_LOADED;
    /** 加载阶段 */
    private LoadStage loadStage = LoadStage.NONE;
    /**
     * 本类依赖的顶级类列表 (仅对顶级类有效，内部类为空)
     */
    private List<ClassNode> dependencies = Collections.emptyList();
    /**
     * 代码生成阶段所需的顶级类列表
     */
    private List<ClassNode> codegenDeps = Collections.emptyList();
    /**
     * 使用了本类的类列表
     */
    private List<ClassNode> useIn = Collections.emptyList();
    /**
     * 使用了本类的方法列表 (仅包含指令引用，不包含定义)
     */
    private List<MethodNode> useInMth = Collections.emptyList();
    /** 方法信息到方法节点的缓存映射 */
    private Map<MethodInfo, MethodNode> mthInfoMap = Collections.emptyMap();
    private JavaClass javaNode;

    /**
     * 从类数据构建类节点
     *
     * @param root 根节点
     * @param cls  类数据
     */
    public ClassNode(RootNode root, IClassData cls) {
        this.root = root;
        this.clsInfo = ClassInfo.fromType(root, ArgType.object(cls.getType()));
        this.packageNode = PackageNode.getForClass(root, clsInfo.getPackage(), this);
        this.clsData = cls.copy();
        load(clsData, false);
    }

    /**
     * 创建空类 (私有构造，仅供合成类使用)
     */
    private ClassNode(RootNode root, ClassInfo clsInfo, int accessFlags) {
        this.root = root;
        this.clsData = null;
        this.clsInfo = clsInfo;
        this.interfaces = new ArrayList<>();
        this.methods = new ArrayList<>();
        this.fields = new ArrayList<>();
        this.accessFlags = new AccessInfo(accessFlags, AFType.CLASS);
        this.packageNode = PackageNode.getForClass(root, clsInfo.getPackage(), this);
    }

    /**
     * 处理特殊类，如 package-info 类 (标记为不重命名)
     */
    private static void processSpecialClasses(ClassNode cls) {
        if ("package-info".equals(cls.getName()) && cls.getFields().isEmpty() && cls.getMethods().isEmpty()) {
            cls.add(AFlag.PACKAGE_INFO);
            cls.add(AFlag.DONT_RENAME);
        }
    }

    /**
     * 处理类的特殊属性，将 AnnotationDefault 从类级别移动到方法级别，并检查源文件属性
     */
    private static void processAttributes(ClassNode cls) {
        // 将 AnnotationDefault 从类移动到方法 (DEX 特有)
        AnnotationDefaultClassAttr defAttr = cls.get(JadxAttrType.ANNOTATION_DEFAULT_CLASS);
        if (defAttr != null) {
            cls.remove(JadxAttrType.ANNOTATION_DEFAULT_CLASS);
            for (Map.Entry<String, EncodedValue> entry : defAttr.getValues().entrySet()) {
                MethodNode mth = cls.searchMethodByShortName(entry.getKey());
                if (mth != null) {
                    mth.addAttr(new AnnotationDefaultAttr(entry.getValue()));
                } else {
                    cls.addWarnComment("Method from annotation default annotation not found: " + entry.getKey());
                }
            }
        }

        // 检查源文件属性
        if (!cls.checkSourceFilenameAttr()) {
            cls.remove(JadxAttrType.SOURCE_FILE);
        }
    }

    /**
     * 添加一个合成类 (由 jadx 内部生成，非原始输入中的类)
     *
     * @param root        根节点
     * @param name        类全名
     * @param accessFlags 访问标志
     * @return 新创建的合成类节点
     */
    public static ClassNode addSyntheticClass(RootNode root, String name, int accessFlags) {
        ClassInfo clsInfo = ClassInfo.fromName(root, name);
        ClassNode existCls = root.resolveClass(clsInfo);
        if (existCls != null) {
            throw new JadxRuntimeException("Class already exist: " + name);
        }
        return addSyntheticClass(root, clsInfo, accessFlags);
    }

    public static ClassNode addSyntheticClass(RootNode root, ClassInfo clsInfo, int accessFlags) {
        ClassNode cls = new ClassNode(root, clsInfo, accessFlags);
        cls.add(AFlag.SYNTHETIC);
        cls.setInputFileName("synthetic");
        cls.setState(ProcessState.PROCESS_COMPLETE);
        root.addClassNode(cls);
        return cls;
    }

    /**
     * 将代码中找到的节点定义位置保存到对应节点，并校验变量引用的有效性
     */
    private static void processDefinitionAnnotations(ICodeInfo codeInfo) {
        Map<Integer, ICodeAnnotation> annotations = codeInfo.getCodeMetadata().getAsMap();
        if (annotations.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, ICodeAnnotation> entry : annotations.entrySet()) {
            ICodeAnnotation ann = entry.getValue();
            if (ann.getAnnType() == AnnType.DECLARATION) {
                NodeDeclareRef declareRef = (NodeDeclareRef) ann;
                int pos = entry.getKey();
                declareRef.setDefPos(pos);
                declareRef.getNode().setDefPosition(pos);
            }
        }
        // 校验变量引用
        annotations.values().removeIf(v -> {
            if (v.getAnnType() == ICodeAnnotation.AnnType.VAR_REF) {
                VarRef varRef = (VarRef) v;
                if (varRef.getRefPos() == 0) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Var reference '{}' incorrect (ref pos is zero) and was removed from metadata", varRef);
                    }
                    return true;
                }
                return false;
            }
            return false;
        });
    }

    /**
     * 从类数据加载类信息，包括访问标志 父类 接口 字段和方法等
     *
     * @param cls       类数据
     * @param reloading 是否为重新加载 (重新加载时会恢复使用信息)
     */
    private void load(IClassData cls, boolean reloading) {
        try {
            addAttrs(cls.getAttributes());
            this.accessFlags = new AccessInfo(getAccessFlags(cls), AFType.CLASS);
            this.superClass = checkSuperType(cls);
            this.interfaces = Utils.collectionMap(cls.getInterfacesTypes(), ArgType::object);
            setInputFileName(cls.getInputFileName());

            ListConsumer<IFieldData, FieldNode> fieldsConsumer = new ListConsumer<>(fld -> FieldNode.build(this, fld));
            ListConsumer<IMethodData, MethodNode> methodsConsumer = new ListConsumer<>(mth -> MethodNode.build(this, mth));
            cls.visitFieldsAndMethods(fieldsConsumer, methodsConsumer);
            this.fields = fieldsConsumer.getResult();
            this.methods = methodsConsumer.getResult();
            if (reloading) {
                restoreUsageData();
            }
            initStaticValues(fields);
            processAttributes(this);
            processSpecialClasses(this);
            buildCache();

            // TODO: 实现模块属性解析
            if (this.accessFlags.isModuleInfo()) {
                this.addWarnComment("Modules not supported yet");
            }
        } catch (Exception e) {
            throw new JadxRuntimeException("Error decode class: " + clsInfo, e);
        }
    }

    /**
     * 恢复类的使用信息数据 (重新加载时调用)
     */
    private void restoreUsageData() {
        IUsageInfoData usageInfoData = root.getArgs().getUsageInfoCache().get(root);
        if (usageInfoData != null) {
            usageInfoData.applyForClass(this);
        } else {
            LOG.warn("Can't restore usage data for class: {}", this);
        }
    }

    /**
     * 检查并获取父类类型
     * java.lang.Object 和 module-info 没有父类
     */
    private ArgType checkSuperType(IClassData cls) {
        String superType = cls.getSuperType();
        if (superType == null) {
            if (clsInfo.getType().getObject().equals(JadxConsts.CLASS_OBJECT)) {
                // java.lang.Object 没有父类
                return null;
            }
            if (this.accessFlags.isModuleInfo()) {
                // module-info 也没有父类
                return null;
            }
            throw new JadxRuntimeException("No super class in " + clsInfo.getType());
        }
        return ArgType.object(superType);
    }

    /**
     * 更新类的泛型数据 (泛型参数 父类类型 接口类型)
     */
    public void updateGenericClsData(List<ArgType> generics, ArgType superClass, List<ArgType> interfaces) {
        this.generics = generics;
        this.superClass = superClass;
        this.interfaces = interfaces;
    }

    /**
     * 获取类的访问标志，优先使用内部类属性中定义的标志
     */
    private int getAccessFlags(IClassData cls) {
        InnerClassesAttr innerClassesAttr = get(JadxAttrType.INNER_CLASSES);
        if (innerClassesAttr != null) {
            InnerClsInfo innerClsInfo = innerClassesAttr.getMap().get(cls.getType());
            if (innerClsInfo != null) {
                return innerClsInfo.getAccessFlags();
            }
        }
        return cls.getAccessFlags();
    }

    /**
     * 初始化静态字段的默认值
     * 字节码可能省略对 0 值的字段初始化，此处为所有静态 final 字段添加显式初始化
     * 如果在类初始化方法中找到赋值语句，错误的初始化将被移除
     */
    private void initStaticValues(List<FieldNode> fields) {
        if (fields.isEmpty()) {
            return;
        }
        for (FieldNode fld : fields) {
            AccessInfo accFlags = fld.getAccessFlags();
            if (accFlags.isStatic() && accFlags.isFinal() && fld.get(JadxAttrType.CONSTANT_VALUE) == null) {
                fld.addAttr(EncodedValue.NULL);
            }
        }
    }

    /**
     * 检查源文件名属性是否有效无效的源文件名 (如默认名 与类名重复等)将被移除
     *
     * @return true 如果源文件名有效，false 如果应该被移除
     */
    private boolean checkSourceFilenameAttr() {
        SourceFileAttr sourceFileAttr = get(JadxAttrType.SOURCE_FILE);
        if (sourceFileAttr == null) {
            return true;
        }
        String fileName = sourceFileAttr.getFileName();
        if (fileName.endsWith(".java")) {
            fileName = fileName.substring(0, fileName.length() - 5);
        }
        if (fileName.isEmpty() || "SourceFile".equals(fileName)) {
            return false;
        }
        if (clsInfo != null) {
            String name = clsInfo.getShortName();
            if (fileName.equals(name)) {
                return false;
            }
            ClassInfo parentCls = clsInfo.getParentClass();
            while (parentCls != null) {
                String parentName = parentCls.getShortName();
                if (parentName.equals(fileName) || parentName.startsWith(fileName + '$')) {
                    return false;
                }
                parentCls = parentCls.getParentClass();
            }
            if (fileName.contains("$") && fileName.endsWith('$' + name)) {
                return false;
            }
            if (name.contains("$") && name.startsWith(fileName)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查顶级父类是否已完成处理
     */
    public boolean checkProcessed() {
        return getTopParentClass().getState().isProcessComplete();
    }

    /**
     * 确保类已处理完成，否则抛出异常
     */
    public void ensureProcessed() {
        if (!checkProcessed()) {
            ClassNode topParentClass = getTopParentClass();
            throw new JadxRuntimeException("Expected class to be processed at this point,"
                    + " class: " + topParentClass + ", state: " + topParentClass.getState());
        }
    }

    /**
     * 反编译本类，优先从缓存获取结果
     */
    public ICodeInfo decompile() {
        return decompile(true);
    }

    /**
     * 使用指定的反编译模式反编译本类
     * 警告：慢速操作！请谨慎使用！
     *
     * @param mode 反编译模式
     * @return 反编译生成的代码信息
     */
    public ICodeInfo decompileWithMode(DecompilationMode mode) {
        switch (mode) {
            case AUTO:
            case RESTRUCTURE:
                return decompile(true);

            case SIMPLE:
            case FALLBACK:
                synchronized (DECOMPILE_WITH_MODE_SYNC) {
                    try {
                        unload();
                        ICodeInfo code = root.getProcessClasses().forceGenerateCodeForMode(this, mode);
                        return Utils.getOrElse(code, ICodeInfo.EMPTY);
                    } finally {
                        unload();
                    }
                }

            default:
                throw new JadxRuntimeException("Unknown mode: " + mode);
        }
    }

    /**
     * 获取本类的反编译代码 (优先使用缓存)
     */
    public ICodeInfo getCode() {
        return decompile(true);
    }

    /**
     * 强制重新反编译本类 (深度重载，不使用缓存)
     */
    public ICodeInfo reloadCode() {
        add(AFlag.CLASS_DEEP_RELOAD);
        return decompile(false);
    }

    /**
     * 卸载本类的代码，从缓存中移除并释放已加载的数据
     */
    public void unloadCode() {
        if (state == NOT_LOADED) {
            return;
        }
        add(AFlag.CLASS_UNLOADED);
        unloadFromCache();
        deepUnload();
    }

    /**
     * 深度卸载本类及其所有内部类，清除属性并从原始类数据重新加载
     */
    public void deepUnload() {
        if (clsData == null) {
            // 手动添加的类 (无原始数据)，无需处理
            return;
        }
        clearAttributes();
        unload();
        root().getConstValues().removeForClass(this);
        load(clsData, true);

        innerClasses.forEach(ClassNode::deepUnload);
    }

    public void unloadFromCache() {
        if (isInner()) {
            return;
        }
        ICodeCache codeCache = root().getCodeCache();
        codeCache.remove(getRawName());
    }

    private synchronized ICodeInfo decompile(boolean searchInCache) {
        if (isInner()) {
            return ICodeInfo.EMPTY;
        }
        ICodeCache codeCache = root().getCodeCache();
        String clsRawName = getRawName();
        if (searchInCache) {
            ICodeInfo code = codeCache.get(clsRawName);
            if (code != ICodeInfo.EMPTY) {
                return code;
            }
        }
        ICodeInfo codeInfo = generateClassCode();
        if (codeInfo != ICodeInfo.EMPTY) {
            codeCache.add(clsRawName, codeInfo);
        }
        return codeInfo;
    }

    private ICodeInfo generateClassCode() {
        try {
            if (false) {
                LOG.debug("Decompiling class: {}", this);
            }
            ICodeInfo codeInfo = root.getProcessClasses().generateCode(this);
            processDefinitionAnnotations(codeInfo);
            return codeInfo;
        } catch (StackOverflowError | Exception e) {
            addError("Code generation failed", e);
            return new SimpleCodeInfo(Utils.getStackTrace(e));
        }
    }

    /**
     * 从代码缓存中获取本类的反编译结果
     *
     * @return 缓存的代码信息，若缓存为空则返回 null
     */
    @Nullable
    public ICodeInfo getCodeFromCache() {
        ICodeCache codeCache = root().getCodeCache();
        String clsRawName = getRawName();
        ICodeInfo codeInfo = codeCache.get(clsRawName);
        if (codeInfo == ICodeInfo.EMPTY) {
            return null;
        }
        return codeInfo;
    }

    /**
     * 加载本类：依次加载所有方法和内部类，并将状态设置为已加载
     */
    @Override
    public void load() {
        for (MethodNode mth : getMethods()) {
            try {
                mth.load();
            } catch (Exception e) {
                mth.addError("Method load error", e);
            }
        }
        for (ClassNode innerCls : getInnerClasses()) {
            innerCls.load();
        }
        setState(LOADED);
    }

    /**
     * 卸载本类：卸载所有方法 内部类 字段及属性，并重置状态为未加载
     */
    @Override
    public void unload() {
        if (state == NOT_LOADED) {
            return;
        }
        synchronized (clsInfo) { // 反编译同步
            methods.forEach(MethodNode::unload);
            innerClasses.forEach(ClassNode::unload);
            fields.forEach(FieldNode::unload);
            unloadAttributes();
            setState(NOT_LOADED);
            this.loadStage = LoadStage.NONE;
            this.smali = null;
        }
    }

    private void buildCache() {
        mthInfoMap = new HashMap<>(methods.size());
        for (MethodNode mth : methods) {
            mthInfoMap.put(mth.methodInfo(), mth);
        }
    }

    @Nullable
    public ArgType getSuperClass() {
        return superClass;
    }

    public List<ArgType> getInterfaces() {
        return interfaces;
    }

    public List<ArgType> getGenericTypeParameters() {
        return generics;
    }

    /**
     * 获取类的类型，若存在泛型参数则返回带泛型的类型
     */
    public ArgType getType() {
        ArgType clsType = clsInfo.getType();
        if (Utils.notEmpty(generics)) {
            return ArgType.generic(clsType, generics);
        }
        return clsType;
    }

    public List<MethodNode> getMethods() {
        return methods;
    }

    public List<FieldNode> getFields() {
        return fields;
    }

    public void addField(FieldNode fld) {
        if (fields == null || fields.isEmpty()) {
            fields = new ArrayList<>(1);
        }
        fields.add(fld);
    }

    public @Nullable IFieldInfoRef getConstField(Object obj) {
        return getConstField(obj, true);
    }

    public @Nullable IFieldInfoRef getConstField(Object obj, boolean searchGlobal) {
        return root().getConstValues().getConstField(this, obj, searchGlobal);
    }

    public @Nullable IFieldInfoRef getConstFieldByLiteralArg(LiteralArg arg) {
        return root().getConstValues().getConstFieldByLiteralArg(this, arg);
    }

    public FieldNode searchField(FieldInfo field) {
        for (FieldNode f : fields) {
            if (f.getFieldInfo().equals(field)) {
                return f;
            }
        }
        return null;
    }

    public FieldNode searchFieldByNameAndType(FieldInfo field) {
        for (FieldNode f : fields) {
            if (f.getFieldInfo().equalsNameAndType(field)) {
                return f;
            }
        }
        return null;
    }

    public FieldNode searchFieldByName(String name) {
        for (FieldNode f : fields) {
            if (f.getName().equals(name)) {
                return f;
            }
        }
        return null;
    }

    public FieldNode searchFieldByShortId(String shortId) {
        for (FieldNode f : fields) {
            if (f.getFieldInfo().getShortId().equals(shortId)) {
                return f;
            }
        }
        return null;
    }

    public MethodNode searchMethod(MethodInfo mth) {
        return mthInfoMap.get(mth);
    }

    public MethodNode searchMethodByShortId(String shortId) {
        for (MethodNode m : methods) {
            if (m.methodInfo().getShortId().equals(shortId)) {
                return m;
            }
        }
        return null;
    }

    /**
     * 按原始短名称返回第一个匹配的方法
     * 注意：方法名不是唯一的 (同一个类可能有多个同名但签名不同的方法)
     */
    @Nullable
    public MethodNode searchMethodByShortName(String name) {
        for (MethodNode m : methods) {
            if (m.methodInfo().getName().equals(name)) {
                return m;
            }
        }
        return null;
    }

    @Override
    public ClassNode getDeclaringClass() {
        return isInner() ? parentClass : null;
    }

    public ClassNode getParentClass() {
        return parentClass;
    }

    public void notInner() {
        this.clsInfo.notInner(root);
        this.parentClass = this;
    }

    /**
     * 修改类名及包名 (如果提供了全限定名)
     * 使用前导点号可将类移动到默认包
     * 内部类的包名不可修改
     */
    @Override
    public void rename(String newName) {
        if (newName.indexOf('.') == -1) {
            clsInfo.changeShortName(newName);
            return;
        }
        // 提供了全限定名
        ClassInfo newClsInfo = ClassInfo.fromNameWithoutCache(root, newName, clsInfo.isInner());
        // 修改类所属包
        String newPkg = newClsInfo.getPackage();
        String newShortName = newClsInfo.getShortName();
        if (clsInfo.isInner()) {
            if (!newPkg.equals(clsInfo.getPackage())) {
                addWarn("Can't change package for inner class: " + this + " to " + newName);
            }
            clsInfo.changeShortName(newShortName);
        } else {
            if (changeClassNodePackage(newPkg)) {
                clsInfo.changePkgAndName(newPkg, newShortName);
            } else {
                clsInfo.changeShortName(newShortName);
            }
        }
    }

    private boolean changeClassNodePackage(String fullPkg) {
        if (fullPkg.equals(clsInfo.getAliasPkg())) {
            return false;
        }
        if (clsInfo.isInner()) {
            throw new JadxRuntimeException("Can't change package for inner class: " + clsInfo);
        }
        root.removeClsFromPackage(packageNode, this);
        packageNode = PackageNode.getForClass(root, fullPkg, this);
        root.sortPackages();
        return true;
    }

    public void removeAlias() {
        if (!clsInfo.isInner()) {
            changeClassNodePackage(clsInfo.getPackage());
        }
        clsInfo.removeAlias();
    }

    @Override
    public void onParentPackageUpdate(PackageNode updatedPkg) {
        if (clsInfo.isInner()) {
            return;
        }
        clsInfo.changePkg(packageNode.getAliasPkgInfo().getFullName());
    }

    public PackageNode getPackageNode() {
        return packageNode;
    }

    public ClassNode getTopParentClass() {
        ClassNode parent = getParentClass();
        return parent == this ? this : parent.getTopParentClass();
    }

    public void visitParentClasses(Consumer<ClassNode> consumer) {
        ClassNode currentCls = this;
        ClassNode parentCls = currentCls.getParentClass();
        while (parentCls != currentCls) {
            consumer.accept(parentCls);
            currentCls = parentCls;
            parentCls = currentCls.getParentClass();
        }
    }

    public void visitSuperTypes(BiConsumer<ArgType, ArgType> consumer) {
        TypeUtils typeUtils = root.getTypeUtils();
        ArgType thisType = this.getType();
        if (!superClass.equals(ArgType.OBJECT)) {
            consumer.accept(thisType, superClass);
            typeUtils.visitSuperTypes(superClass, consumer);
        }
        for (ArgType iface : interfaces) {
            consumer.accept(thisType, iface);
            typeUtils.visitSuperTypes(iface, consumer);
        }
    }

    public boolean hasNotGeneratedParent() {
        if (contains(AFlag.DONT_GENERATE)) {
            return true;
        }
        ClassNode parent = getParentClass();
        if (parent == this) {
            return false;
        }
        return parent.hasNotGeneratedParent();
    }

    public List<ClassNode> getInnerClasses() {
        return innerClasses;
    }

    public List<ClassNode> getInlinedClasses() {
        return inlinedClasses;
    }

    /**
     * 递归获取所有内部类及被内联的类
     *
     * @param resultClassesSet 所有识别到的内部类和内联类都将添加到该集合中
     */
    public void getInnerAndInlinedClassesRecursive(Set<ClassNode> resultClassesSet) {
        for (ClassNode innerCls : innerClasses) {
            if (resultClassesSet.add(innerCls)) {
                innerCls.getInnerAndInlinedClassesRecursive(resultClassesSet);
            }
        }
        for (ClassNode inlinedCls : inlinedClasses) {
            if (resultClassesSet.add(inlinedCls)) {
                inlinedCls.getInnerAndInlinedClassesRecursive(resultClassesSet);
            }
        }
    }

    public void getInnerClassesRecursive(Set<ClassNode> resultClassesSet) {
        for (ClassNode innerCls : innerClasses) {
            if (resultClassesSet.add(innerCls)) {
                innerCls.getInnerAndInlinedClassesRecursive(resultClassesSet);
            }
        }
    }

    public void addInnerClass(ClassNode cls) {
        if (innerClasses.isEmpty()) {
            innerClasses = new ArrayList<>(5);
        }
        innerClasses.add(cls);
        cls.parentClass = this;
    }

    public void addInlinedClass(ClassNode cls) {
        if (inlinedClasses.isEmpty()) {
            inlinedClasses = new ArrayList<>(5);
        }
        cls.addAttr(new InlinedAttr(this));
        inlinedClasses.add(cls);
    }

    public boolean isEnum() {
        return getAccessFlags().isEnum()
                && getSuperClass() != null
                && getSuperClass().getObject().equals(ArgType.ENUM.getObject());
    }

    public boolean isAnonymous() {
        return contains(AType.ANONYMOUS_CLASS);
    }

    public boolean isSynthetic() {
        return contains(AFlag.SYNTHETIC);
    }

    public boolean isInner() {
        return parentClass != this;
    }

    public boolean isTopClass() {
        return parentClass == this;
    }

    @Nullable
    public MethodNode getClassInitMth() {
        return searchMethodByShortId("<clinit>()V");
    }

    @Nullable
    public MethodNode getDefaultConstructor() {
        for (MethodNode mth : methods) {
            if (mth.isDefaultConstructor()) {
                return mth;
            }
        }
        return null;
    }

    @Override
    public AccessInfo getAccessFlags() {
        return accessFlags;
    }

    @Override
    public void setAccessFlags(AccessInfo accessFlags) {
        this.accessFlags = accessFlags;
    }

    @Override
    public RootNode root() {
        return root;
    }

    @Override
    public String typeName() {
        return "class";
    }

    public String getRawName() {
        return clsInfo.getRawName();
    }

    /**
     * 内部类信息 (请勿在代码生成和外部 API 中使用)
     */
    public ClassInfo getClassInfo() {
        return clsInfo;
    }

    public String getName() {
        return clsInfo.getShortName();
    }

    public String getAlias() {
        return clsInfo.getAliasShortName();
    }

    /**
     * 已废弃请使用 {@link #getAlias()}
     */
    @Deprecated
    public String getShortName() {
        return clsInfo.getAliasShortName();
    }

    public String getFullName() {
        return clsInfo.getAliasFullName();
    }

    public String getPackage() {
        return clsInfo.getAliasPkg();
    }

    /**
     * 获取本类及其所有内部类 内联类的 smali 反汇编代码 (结果会被缓存)
     */
    public String getDisassembledCode() {
        if (smali == null) {
            SimpleCodeWriter code = new SimpleCodeWriter(root.getArgs());
            getDisassembledCode(code);
            Set<ClassNode> allInlinedClasses = new LinkedHashSet<>();
            getInnerAndInlinedClassesRecursive(allInlinedClasses);
            for (ClassNode innerClass : allInlinedClasses) {
                innerClass.getDisassembledCode(code);
            }
            smali = code.finish().getCodeStr();
        }
        return smali;
    }

    protected void getDisassembledCode(SimpleCodeWriter code) {
        if (clsData == null) {
            code.startLine(String.format("###### Class %s is created by jadx", getFullName()));
            return;
        }
        code.startLine(String.format("###### Class %s (%s)", getFullName(), getRawName()));
        try {
            code.startLine(clsData.getDisassembledCode());
        } catch (Exception e) {
            code.startLine("Failed to disassemble class:");
            code.startLine(Utils.getStackTrace(e));
        }
    }

    /**
     * 底层类数据访问
     *
     * @return 对于 jadx 生成的类返回 null
     */
    public @Nullable IClassData getClsData() {
        return clsData;
    }

    public ProcessState getState() {
        return state;
    }

    public void setState(ProcessState state) {
        this.state = state;
    }

    public LoadStage getLoadStage() {
        return loadStage;
    }

    public void setLoadStage(LoadStage loadStage) {
        this.loadStage = loadStage;
    }

    public void reloadAtCodegenStage() {
        ClassNode topCls = this.getTopParentClass();
        if (topCls.getLoadStage() == LoadStage.CODEGEN_STAGE) {
            throw new JadxRuntimeException("Class not yet loaded at codegen stage: " + topCls);
        }
        topCls.add(AFlag.RELOAD_AT_CODEGEN_STAGE);
    }

    public List<ClassNode> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<ClassNode> dependencies) {
        this.dependencies = dependencies;
    }

    public void removeDependency(ClassNode dep) {
        this.dependencies = ListUtils.safeRemoveAndTrim(this.dependencies, dep);
    }

    public List<ClassNode> getCodegenDeps() {
        return codegenDeps;
    }

    public void setCodegenDeps(List<ClassNode> codegenDeps) {
        this.codegenDeps = codegenDeps;
    }

    public void addCodegenDep(ClassNode dep) {
        if (!codegenDeps.contains(dep)) {
            this.codegenDeps = ListUtils.safeAdd(this.codegenDeps, dep);
        }
    }

    public int getTotalDepsCount() {
        return dependencies.size() + codegenDeps.size();
    }

    @Override
    public List<ClassNode> getUseIn() {
        return useIn;
    }

    public void setUseIn(List<ClassNode> useIn) {
        this.useIn = useIn;
    }

    public List<MethodNode> getUseInMth() {
        return useInMth;
    }

    public void setUseInMth(List<MethodNode> useInMth) {
        this.useInMth = useInMth;
    }

    @Override
    public String getInputFileName() {
        return inputFileName;
    }

    public void setInputFileName(String inputFileName) {
        this.inputFileName = inputFileName;
    }

    public JavaClass getJavaNode() {
        return javaNode;
    }

    public void setJavaNode(JavaClass javaNode) {
        this.javaNode = javaNode;
    }

    @Override
    public AnnType getAnnType() {
        return AnnType.CLASS;
    }

    @Override
    public int hashCode() {
        return clsInfo.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof ClassNode) {
            ClassNode other = (ClassNode) o;
            return clsInfo.equals(other.clsInfo);
        }
        return false;
    }

    @Override
    public int compareTo(@NotNull ClassNode o) {
        return this.clsInfo.compareTo(o.clsInfo);
    }

    @Override
    public String toString() {
        return clsInfo.getFullName();
    }
}
