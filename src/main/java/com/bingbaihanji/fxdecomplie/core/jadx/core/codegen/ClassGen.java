package com.bingbaihanji.fxdecomplie.core.jadx.core.codegen;

import com.bingbaihanji.fxdecomplie.core.jadx.api.CommentsLevel;
import com.bingbaihanji.fxdecomplie.core.jadx.api.ICodeInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.api.ICodeWriter;
import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxArgs;
import com.bingbaihanji.fxdecomplie.core.jadx.api.args.IntegerFormat;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.annotations.NodeEnd;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.AccessFlags;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.annotations.EncodedType;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.annotations.EncodedValue;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.JadxAttrType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodeGenUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.FieldInitInsnAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.*;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.EnumClassAttr.EnumField;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.AccessInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.ClassInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.LiteralArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.PrimitiveType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.mods.ConstructorInsn;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.*;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.EncodedValueUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.android.AndroidResourcesUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.CodegenException;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 类代码生成器
 * <p>
 * 负责将 {@link ClassNode} 中间表示生成为 Java 源码文本，包括包声明、导入语句、类声明、
 * 字段、方法、内部类以及枚举字段等同时管理导入集合、类型/类名的书写方式 (短名或全名)
 * 与导入冲突检测内部类通过创建带 parentGen 的子 ClassGen 递归生成，导入统一汇聚到顶层
 */
public class ClassGen {

    /** 当前正在生成的类节点 */
    private final ClassNode cls;
    /** 父类生成器 (用于内部类场景)，顶层类为 null */
    private final ClassGen parentGen;
    /** 注解生成器 */
    private final AnnotationGen annotationGen;
    /** 是否为回退 (fallback)模式，回退模式使用全限定名且不做导入优化 */
    private final boolean fallback;
    /** 是否使用 import 简化类名 */
    private final boolean useImports;
    /** 是否显示不一致 (反编译异常)的代码 */
    private final boolean showInconsistentCode;
    /** 整数字面量的输出格式 (十进制/十六进制等) */
    private final IntegerFormat integerFormat;

    /** 当前类收集到的导入集合 */
    private final Set<ClassInfo> imports = new HashSet<>();
    /** 类声明结束时在代码写入器中的偏移量，用于判断类体是否为空 */
    private int clsDeclOffset;

    /** 类体代码是否已开始生成 */
    private boolean bodyGenStarted;

    /** 外层名称生成器 (匿名内部类内联时用于继承外层已用变量名) */
    @Nullable
    private NameGen outerNameGen;

    public ClassGen(ClassNode cls, JadxArgs jadxArgs) {
        this(cls, null, jadxArgs.isUseImports(), jadxArgs.isFallbackMode(), jadxArgs.isShowInconsistentCode(), jadxArgs.getIntegerFormat());
    }

    public ClassGen(ClassNode cls, ClassGen parentClsGen) {
        this(cls, parentClsGen, parentClsGen.useImports, parentClsGen.fallback, parentClsGen.showInconsistentCode,
                parentClsGen.integerFormat);
    }

    public ClassGen(ClassNode cls, ClassGen parentClsGen, boolean useImports, boolean fallback, boolean showBadCode,
                    IntegerFormat integerFormat) {
        this.cls = cls;
        this.parentGen = parentClsGen;
        this.fallback = fallback;
        this.useImports = useImports;
        this.showInconsistentCode = showBadCode;
        this.integerFormat = integerFormat;

        this.annotationGen = new AnnotationGen(cls, this);
    }

    private static boolean isBothClassesInOneTopClass(ClassInfo useCls, ClassInfo extClsInfo) {
        ClassInfo a = useCls.getTopParentClass();
        ClassInfo b = extClsInfo.getTopParentClass();
        if (a != null) {
            return a.equals(b);
        }
        // useCls - 本身即为顶层类
        return useCls.equals(b);
    }

    private static boolean isClassInnerFor(ClassInfo inner, ClassInfo parent) {
        if (inner.isInner()) {
            ClassInfo p = inner.getParentClass();
            return Objects.equals(p, parent) || isClassInnerFor(p, parent);
        }
        return false;
    }

    private static boolean checkInnerCollision(RootNode root, @Nullable ClassInfo useCls, ClassInfo searchCls) {
        if (useCls == null) {
            return false;
        }
        String shortName = searchCls.getAliasShortName();
        if (useCls.getAliasShortName().equals(shortName)) {
            return true;
        }
        ClassNode classNode = root.resolveClass(useCls);
        if (classNode != null) {
            for (ClassNode inner : classNode.getInnerClasses()) {
                if (inner.getAlias().equals(shortName)
                        && !inner.getFullName().equals(searchCls.getAliasFullName())) {
                    return true;
                }
            }
        }
        return checkInnerCollision(root, useCls.getParentClass(), searchCls);
    }

    /**
     * 检查当前包中是否已存在同名的类
     */
    private static boolean checkInPackageCollision(RootNode root, ClassInfo useCls, ClassInfo searchCls) {
        String currentPkg = useCls.getAliasPkg();
        if (currentPkg.equals(searchCls.getAliasPkg())) {
            // 待查找的类已经属于当前包
            return false;
        }
        String shortName = searchCls.getAliasShortName();
        return root.getClsp().isClsKnown(currentPkg + '.' + shortName);
    }

    private static void addClassUsageInfo(ICodeWriter code, ClassNode cls) {
        List<ClassNode> deps = cls.getDependencies();
        code.startLine("// deps - ").add(Integer.toString(deps.size()));
        for (ClassNode depCls : deps) {
            code.startLine("//  ").add(depCls.getClassInfo().getFullName());
        }
        List<ClassNode> useIn = cls.getUseIn();
        code.startLine("// use in - ").add(Integer.toString(useIn.size()));
        for (ClassNode useCls : useIn) {
            code.startLine("//  ").add(useCls.getClassInfo().getFullName());
        }
        List<MethodNode> useInMths = cls.getUseInMth();
        code.startLine("// use in methods - ").add(Integer.toString(useInMths.size()));
        for (MethodNode useMth : useInMths) {
            code.startLine("//  ").add(useMth.toString());
        }
    }

    static void addMthUsageInfo(ICodeWriter code, MethodNode mth) {
        List<MethodNode> useInMths = mth.getUseIn();
        code.startLine("// use in methods - ").add(Integer.toString(useInMths.size()));
        for (MethodNode useMth : useInMths) {
            code.startLine("//  ").add(useMth.toString());
        }
    }

    private static void addFieldUsageInfo(ICodeWriter code, FieldNode fieldNode) {
        List<MethodNode> useInMths = fieldNode.getUseIn();
        code.startLine("// use in methods - ").add(Integer.toString(useInMths.size()));
        for (MethodNode useMth : useInMths) {
            code.startLine("//  ").add(useMth.toString());
        }
    }

    public ClassNode getClassNode() {
        return cls;
    }

    /**
     * 生成完整的类源码
     * <p>
     * 先生成类体，再依次拼接包声明、导入语句和类体，返回带元数据的代码信息
     * 若类为 package-info 则走 {@link #makePackageInfo()} 分支
     *
     * @return 生成的代码信息
     * @throws CodegenException 代码生成失败时抛出
     */
    public ICodeInfo makeClass() throws CodegenException {
        if (cls.contains(AFlag.PACKAGE_INFO)) {
            return makePackageInfo();
        }
        ICodeWriter clsBody = cls.root().makeCodeWriter();
        addClassCode(clsBody);

        ICodeWriter clsCode = cls.root().makeCodeWriter();
        addPackage(clsCode);
        clsCode.newLine();
        addImports(clsCode);
        clsCode.add(clsBody);
        return clsCode.finish();
    }

    private void addPackage(ICodeWriter clsCode) {
        if (cls.getPackage().isEmpty()) {
            clsCode.add("// default package");
        } else {
            clsCode.add("package ").add(cls.getPackage()).add(';');
        }
    }

    private void addImports(ICodeWriter clsCode) {
        int importsCount = imports.size();
        if (importsCount != 0) {
            imports.stream()
                    .sorted(Comparator.comparing(ClassInfo::getAliasFullName))
                    .forEach(classInfo -> {
                        clsCode.startLine("import ");
                        ClassNode classNode = cls.root().resolveClass(classInfo);
                        if (classNode != null) {
                            clsCode.attachAnnotation(classNode);
                        }
                        clsCode.add(classInfo.getAliasFullName());
                        clsCode.add(';');
                    });
            clsCode.newLine();
            imports.clear();
        }
    }

    private ICodeInfo makePackageInfo() {
        ICodeWriter code = cls.root().makeCodeWriter();
        annotationGen.addForClass(code);
        code.newLine();
        code.attachDefinition(cls);
        addPackage(code);
        code.newLine();
        addImports(code);
        return code.finish();
    }

    public void addClassCode(ICodeWriter code) throws CodegenException {
        if (cls.contains(AFlag.DONT_GENERATE)) {
            return;
        }
        if (false) {
            addClassUsageInfo(code, cls);
        }
        addClassDeclaration(code);
        addClassBody(code);
    }

    /**
     * 生成类的声明部分
     * <p>
     * 包含注释、注解、访问修饰符、class/interface/enum 关键字、类名、泛型参数、
     * 父类 (extends)与实现接口 (implements)等
     *
     * @param clsCode 代码写入器
     */
    public void addClassDeclaration(ICodeWriter clsCode) {
        AccessInfo af = cls.getAccessFlags();
        if (af.isInterface()) {
            af = af.remove(AccessFlags.ABSTRACT)
                    .remove(AccessFlags.STATIC);
        }
        // 顶层类 (非内部类)不允许使用 'static' 和 'private' 修饰符
        if (!cls.getClassInfo().isInner()) {
            af = af.remove(AccessFlags.STATIC).remove(AccessFlags.PRIVATE);
        }

        CodeGenUtils.addComments(clsCode, cls);
        CodeGenUtils.addClassRenamedComment(clsCode, cls);
        CodeGenUtils.addErrors(clsCode, cls);
        CodeGenUtils.addSourceFileInfo(clsCode, cls);
        CodeGenUtils.addInputFileInfo(clsCode, cls);

        annotationGen.addForClass(clsCode);
        clsCode.startLineWithNum(cls.getSourceLine()).add(af.makeString(cls.checkCommentsLevel(CommentsLevel.INFO)));
        if (af.isInterface()) {
            if (af.isAnnotation()) {
                clsCode.add('@');
            }
            clsCode.add("interface ");
        } else if (af.isEnum()) {
            clsCode.add("enum ");
        } else {
            clsCode.add("class ");
        }
        clsCode.attachDefinition(cls);
        clsCode.add(cls.getClassInfo().getAliasShortName());

        addGenericTypeParameters(clsCode, cls.getGenericTypeParameters(), true);
        clsCode.add(' ');

        ArgType sup = cls.getSuperClass();
        if (sup != null
                && !sup.equals(ArgType.OBJECT)
                && !cls.contains(AFlag.REMOVE_SUPER_CLASS)) {
            clsCode.add("extends ");
            useClass(clsCode, sup);
            clsCode.add(' ');
        }

        if (!cls.getInterfaces().isEmpty() && !af.isAnnotation()) {
            if (cls.getAccessFlags().isInterface()) {
                clsCode.add("extends ");
            } else {
                clsCode.add("implements ");
            }
            for (Iterator<ArgType> it = cls.getInterfaces().iterator(); it.hasNext(); ) {
                ArgType interf = it.next();
                useClass(clsCode, interf);
                if (it.hasNext()) {
                    clsCode.add(", ");
                }
            }
            if (!cls.getInterfaces().isEmpty()) {
                clsCode.add(' ');
            }
        }
    }

    /**
     * 生成泛型类型参数列表，形如 {@code <T, K extends A & B>}
     *
     * @param code             代码写入器
     * @param generics         泛型参数列表
     * @param classDeclaration 是否为类声明处 (类声明处会为上界类型补充导入)
     * @return 若生成了泛型参数返回 true，否则返回 false
     */
    public boolean addGenericTypeParameters(ICodeWriter code, List<ArgType> generics, boolean classDeclaration) {
        if (generics == null || generics.isEmpty()) {
            return false;
        }
        code.add('<');
        int i = 0;
        for (ArgType genericInfo : generics) {
            if (i != 0) {
                code.add(", ");
            }
            if (genericInfo.isGenericType()) {
                code.add(genericInfo.getObject());
            } else {
                useClass(code, genericInfo);
            }
            List<ArgType> list = genericInfo.getExtendTypes();
            if (list != null && !list.isEmpty()) {
                code.add(" extends ");
                for (Iterator<ArgType> it = list.iterator(); it.hasNext(); ) {
                    ArgType g = it.next();
                    if (g.isGenericType()) {
                        code.add(g.getObject());
                    } else {
                        useClass(code, g);
                        if (classDeclaration
                                && !cls.getClassInfo().isInner()
                                && cls.root().getArgs().isUseImports()) {
                            addImport(ClassInfo.fromType(cls.root(), g));
                        }
                    }
                    if (it.hasNext()) {
                        code.add(" & ");
                    }
                }
            }
            i++;
        }
        code.add('>');
        return true;
    }

    /**
     * 生成类体 (花括号内的内容)，字段、内部类与方法
     *
     * @param clsCode 代码写入器
     * @throws CodegenException 代码生成失败时抛出
     */
    public void addClassBody(ICodeWriter clsCode) throws CodegenException {
        addClassBody(clsCode, false);
    }

    /**
     * 生成类体 (花括号内的内容)
     *
     * @param clsCode        代码写入器
     * @param printClassName 是否将原始类名作为注释输出 (例如用于内联类)
     * @throws CodegenException 代码生成失败时抛出
     */
    public void addClassBody(ICodeWriter clsCode, boolean printClassName) throws CodegenException {
        clsCode.add('{');
        if (printClassName && cls.checkCommentsLevel(CommentsLevel.INFO)) {
            clsCode.add(" // from class: " + cls.getClassInfo().getFullName());
        }
        setBodyGenStarted(true);
        clsDeclOffset = clsCode.getLength();
        clsCode.incIndent();
        addFields(clsCode);
        addInnerClsAndMethods(clsCode);
        clsCode.decIndent();
        clsCode.startLine('}');
        clsCode.attachAnnotation(NodeEnd.VALUE);
    }

    private void addInnerClsAndMethods(ICodeWriter clsCode) {
        Stream.of(cls.getInnerClasses(), cls.getMethods())
                .flatMap(Collection::stream)
                .filter(node -> !skipNode(node))
                .sorted(Comparator.comparingInt(LineAttrNode::getSourceLine))
                .forEach(node -> {
                    if (node instanceof ClassNode) {
                        addInnerClass(clsCode, (ClassNode) node);
                    } else {
                        addMethod(clsCode, (MethodNode) node);
                    }
                });
    }

    private boolean skipNode(NotificationAttrNode node) {
        if (fallback) {
            return false;
        }
        if (false) {
            if (node.contains(AType.JADX_COMMENTS)) {
                return false;
            }
        }
        return node.contains(AFlag.DONT_GENERATE);
    }

    private void addInnerClass(ICodeWriter code, ClassNode innerCls) {
        try {
            ClassGen inClGen = new ClassGen(innerCls, getParentGen());
            code.newLine();
            inClGen.addClassCode(code);
            imports.addAll(inClGen.getImports());
        } catch (Exception e) {
            innerCls.addError("Inner class code generation error", e);
        }
    }

    private boolean isInnerClassesPresents() {
        for (ClassNode innerCls : cls.getInnerClasses()) {
            if (!innerCls.contains(AType.ANONYMOUS_CLASS)) {
                return true;
            }
        }
        return false;
    }

    private void addMethod(ICodeWriter code, MethodNode mth) {
        if (skipMethod(mth)) {
            return;
        }
        if (code.getLength() != clsDeclOffset) {
            code.newLine();
        }
        int savedIndent = code.getIndent();
        try {
            addMethodCode(code, mth);
        } catch (Exception e) {
            if (mth.getParentClass().getTopParentClass().contains(AFlag.RESTART_CODEGEN)) {
                throw new JadxRuntimeException("Method generation error", e);
            }
            mth.addError("Method generation error", e);
            CodeGenUtils.addErrors(code, mth);
            code.setIndent(savedIndent);
        }
    }

    /**
     * 内联方法的附加检查，判断是否应跳过该方法的代码生成
     */
    private boolean skipMethod(MethodNode mth) {
        if (cls.root().getArgs().getDecompilationMode().isSpecial()) {
            // 特殊反编译模式下显示所有方法
            return false;
        }
        MethodInlineAttr inlineAttr = mth.get(AType.METHOD_INLINE);
        if (inlineAttr == null || inlineAttr.notNeeded()) {
            return false;
        }
        try {
            if (mth.getUseIn().isEmpty()) {
                mth.add(AFlag.DONT_GENERATE);
                return true;
            }
            List<MethodNode> useInCompleted = mth.getUseIn().stream()
                    .filter(m -> m.getTopParentClass().getState().isProcessComplete())
                    .collect(Collectors.toList());
            if (useInCompleted.isEmpty()) {
                mth.add(AFlag.DONT_GENERATE);
                return true;
            }
            mth.addDebugComment("Method not inlined, still used in: " + useInCompleted);
            return false;
        } catch (Exception e) {
            // 检查失败 => 保留该方法
            mth.addWarnComment("Failed to check method usage", e);
            return false;
        }
    }

    private boolean isMethodsPresents() {
        for (MethodNode mth : cls.getMethods()) {
            if (!mth.contains(AFlag.DONT_GENERATE)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成单个方法的完整代码 (方法定义 + 方法体)
     * <p>
     * 无代码的方法 (抽象/接口方法)只生成定义并以分号结尾 对于代码异常、回退模式或
     * 标记不一致的方法使用回退方式生成方法体
     *
     * @param code 代码写入器
     * @param mth  方法节点
     * @throws CodegenException 代码生成失败时抛出
     */
    public void addMethodCode(ICodeWriter code, MethodNode mth) throws CodegenException {
        CodeGenUtils.addErrorsAndComments(code, mth);
        if (mth.isNoCode()) {
            MethodGen mthGen = new MethodGen(this, mth);
            mthGen.addDefinition(code);
            code.add(';');
        } else {
            boolean badCode = mth.contains(AFlag.INCONSISTENT_CODE);
            if (badCode && showInconsistentCode) {
                badCode = false;
            }
            MethodGen mthGen;
            if (badCode || fallback || mth.contains(AType.JADX_ERROR)) {
                mthGen = MethodGen.getFallbackMethodGen(mth);
            } else {
                mthGen = new MethodGen(this, mth);
            }
            if (mthGen.addDefinition(code)) {
                code.add(' ');
            }
            code.add('{');
            code.incIndent();
            mthGen.addInstructions(code);
            code.decIndent();
            code.startLine('}');
            code.attachAnnotation(NodeEnd.VALUE);
        }
    }

    private void addFields(ICodeWriter code) throws CodegenException {
        addEnumFields(code);
        for (FieldNode f : cls.getFields()) {
            addField(code, f);
        }
    }

    /**
     * 生成单个字段的代码，包含注释、注解、访问修饰符、类型、字段名以及初始化值/常量值
     *
     * @param code 代码写入器
     * @param f    字段节点
     */
    public void addField(ICodeWriter code, FieldNode f) {
        if (f.contains(AFlag.DONT_GENERATE)) {
            return;
        }
        if (f.contains(JadxAttrType.ANNOTATION_LIST)
                || f.contains(AType.JADX_COMMENTS)
                || f.contains(AType.CODE_COMMENTS)
                || f.getFieldInfo().hasAlias()) {
            code.newLine();
        }
        if (false) {
            addFieldUsageInfo(code, f);
        }
        CodeGenUtils.addComments(code, f);
        if (f.getFieldInfo().hasAlias()) {
            CodeGenUtils.addRenamedComment(code, f, f.getName());
        }
        annotationGen.addForField(code, f);

        code.startLine(f.getAccessFlags().makeString(f.checkCommentsLevel(CommentsLevel.INFO)));
        useType(code, f.getType());
        code.add(' ');
        code.attachDefinition(f);
        code.add(f.getAlias());

        FieldInitInsnAttr initInsnAttr = f.get(AType.FIELD_INIT_INSN);
        if (initInsnAttr != null) {
            InsnGen insnGen = makeInsnGen(initInsnAttr.getInsnMth());
            code.add(" = ");
            addInsnBody(insnGen, code, initInsnAttr.getInsn());
        } else {
            EncodedValue constVal = f.get(JadxAttrType.CONSTANT_VALUE);
            if (constVal != null) {
                code.add(" = ");
                if (constVal.getType() == EncodedType.ENCODED_NULL) {
                    code.add(TypeGen.literalToString(0, f.getType(), cls, fallback));
                } else {
                    Object val = EncodedValueUtils.convertToConstValue(constVal);
                    if (val instanceof LiteralArg) {
                        long lit = ((LiteralArg) val).getLiteral();
                        code.add(getIntegerString(lit, f.getType()));
                    } else {
                        annotationGen.encodeValue(cls.root(), code, constVal);
                    }
                }
            }
        }
        code.add(';');
    }

    private String getIntegerString(long lit, ArgType type) {
        if (integerFormat != IntegerFormat.DECIMAL && AndroidResourcesUtils.isResourceFieldValue(cls, type)) {
            return String.format("0x%08x", lit);
        }
        // 强制字面量类型与字段类型一致 (Java 字节码可能使用不同的类型)
        return TypeGen.literalToString(lit, type, cls, fallback);
    }

    private boolean isFieldsPresents() {
        for (FieldNode field : cls.getFields()) {
            if (!field.contains(AFlag.DONT_GENERATE)) {
                return true;
            }
        }
        return false;
    }

    private void addEnumFields(ICodeWriter code) throws CodegenException {
        EnumClassAttr enumFields = cls.get(AType.ENUM_CLASS);
        if (enumFields == null) {
            return;
        }
        InsnGen igen = null;
        for (Iterator<EnumField> it = enumFields.getFields().iterator(); it.hasNext(); ) {
            EnumField f = it.next();

            CodeGenUtils.addComments(code, f.getField());
            code.startLine(f.getField().getAlias());
            ConstructorInsn constrInsn = f.getConstrInsn();
            MethodNode callMth = cls.root().resolveMethod(constrInsn.getCallMth());
            int skipCount = getEnumCtrSkipArgsCount(callMth);
            if (constrInsn.getArgsCount() > skipCount) {
                if (igen == null) {
                    igen = makeInsnGen(enumFields.getStaticMethod());
                }
                igen.generateMethodArguments(code, constrInsn, 0, callMth);
            }
            if (f.getCls() != null) {
                code.add(' ');
                new ClassGen(f.getCls(), this).addClassBody(code, true);
            }
            if (it.hasNext()) {
                code.add(',');
            }
        }
        if (isMethodsPresents() || isFieldsPresents() || isInnerClassesPresents()) {
            if (enumFields.getFields().isEmpty()) {
                code.startLine();
            }
            code.add(';');
            if (isFieldsPresents()) {
                code.newLine();
            }
        }
    }

    private int getEnumCtrSkipArgsCount(@Nullable MethodNode callMth) {
        if (callMth != null) {
            SkipMethodArgsAttr skipArgsAttr = callMth.get(AType.SKIP_MTH_ARGS);
            if (skipArgsAttr != null) {
                return skipArgsAttr.getSkipCount();
            }
        }
        return 0;
    }

    private InsnGen makeInsnGen(MethodNode mth) {
        MethodGen mthGen = new MethodGen(this, mth);
        return new InsnGen(mthGen, false);
    }

    private void addInsnBody(InsnGen insnGen, ICodeWriter code, InsnNode insn) {
        try {
            insnGen.makeInsn(insn, code, InsnGen.Flags.BODY_ONLY_NOWRAP);
        } catch (Exception e) {
            cls.addError("Failed to generate init code", e);
        }
    }

    /**
     * 将类型写入代码，自动处理基本类型、对象类型、数组类型与泛型类型
     *
     * @param code 代码写入器
     * @param type 待写入的类型
     */
    public void useType(ICodeWriter code, ArgType type) {
        PrimitiveType stype = type.getPrimitiveType();
        if (stype == null) {
            code.add(type.toString());
        } else if (stype == PrimitiveType.OBJECT) {
            if (type.isGenericType()) {
                code.add(type.getObject());
            } else {
                useClass(code, type);
            }
        } else if (stype == PrimitiveType.ARRAY) {
            useType(code, type.getArrayElement());
            code.add("[]");
        } else {
            code.add(stype.getLongName());
        }
    }

    /**
     * 按原始类名 (内部形式)写入类引用
     *
     * @param code   代码写入器
     * @param rawCls 原始类名
     */
    public void useClass(ICodeWriter code, String rawCls) {
        useClass(code, ArgType.object(rawCls));
    }

    /**
     * 按类型写入类引用，处理外部类、内部类嵌套与泛型信息
     *
     * @param code 代码写入器
     * @param type 类类型
     */
    public void useClass(ICodeWriter code, ArgType type) {
        ArgType outerType = type.getOuterType();
        if (outerType != null) {
            useClass(code, outerType);
            code.add('.');
            addInnerType(code, type);
            return;
        }
        useClass(code, ClassInfo.fromType(cls.root(), type));
        addGenerics(code, type);
    }

    private void addInnerType(ICodeWriter code, ArgType baseType) {
        ArgType innerType = baseType.getInnerType();
        ArgType outerType = innerType.getOuterType();
        if (outerType != null) {
            useClassWithShortName(code, baseType, outerType);
            code.add('.');
            addInnerType(code, innerType);
            return;
        }
        useClassWithShortName(code, baseType, innerType);
    }

    private void useClassWithShortName(ICodeWriter code, ArgType baseType, ArgType type) {
        String fullNameObj;
        if (type.getObject().contains(".")) {
            fullNameObj = type.getObject();
        } else {
            fullNameObj = baseType.getObject();
        }
        ClassInfo classInfo = ClassInfo.fromName(cls.root(), fullNameObj);
        ClassNode classNode = cls.root().resolveClass(classInfo);
        if (classNode != null) {
            code.attachAnnotation(classNode);
        }
        code.add(classInfo.getAliasShortName());
        addGenerics(code, type);
    }

    private void addGenerics(ICodeWriter code, ArgType type) {
        List<ArgType> generics = type.getGenericTypes();
        if (generics != null) {
            code.add('<');
            int len = generics.size();
            for (int i = 0; i < len; i++) {
                if (i != 0) {
                    code.add(", ");
                }
                ArgType gt = generics.get(i);
                ArgType wt = gt.getWildcardType();
                if (wt != null) {
                    ArgType.WildcardBound bound = gt.getWildcardBound();
                    code.add(bound.getStr());
                    if (bound != ArgType.WildcardBound.UNBOUND) {
                        useType(code, wt);
                    }
                } else {
                    useType(code, gt);
                }
            }
            code.add('>');
        }
    }

    public void useClass(ICodeWriter code, ClassInfo classInfo) {
        ClassNode classNode = cls.root().resolveClass(classInfo);
        if (classNode != null) {
            useClass(code, classNode);
        } else {
            addClsName(code, classInfo);
        }
    }

    public void useClass(ICodeWriter code, ClassNode classNode) {
        code.attachAnnotation(classNode);
        addClsName(code, classNode.getClassInfo());
    }

    public void addClsName(ICodeWriter code, ClassInfo classInfo) {
        String clsName = useClassInternal(cls.getClassInfo(), classInfo);
        code.add(clsName);
    }

    public void addClsShortNameForced(ICodeWriter code, ClassInfo classInfo) {
        code.add(classInfo.getAliasShortName());
        if (!isBothClassesInOneTopClass(cls.getClassInfo(), classInfo)) {
            addImport(classInfo);
        }
    }

    private String useClassInternal(ClassInfo useCls, ClassInfo extClsInfo) {
        String fullName = extClsInfo.getAliasFullName();
        if (fallback || !useImports) {
            return fullName;
        }
        String shortName = extClsInfo.getAliasShortName();
        if (useCls.equals(extClsInfo)) {
            return shortName;
        }
        if (extClsInfo.getAliasPkg().isEmpty()) {
            // 默认包不生成 import
            return shortName;
        }
        if (isClassInnerFor(useCls, extClsInfo)) {
            return shortName;
        }
        if (extClsInfo.isInner()) {
            return expandInnerClassName(useCls, extClsInfo);
        }
        if (checkInnerCollision(cls.root(), useCls, extClsInfo)
                || checkInPackageCollision(cls.root(), useCls, extClsInfo)) {
            return fullName;
        }
        if (isBothClassesInOneTopClass(useCls, extClsInfo)) {
            return shortName;
        }
        // java.lang 包下的顶层类不生成 import (不含子包)
        if ("java.lang".equals(extClsInfo.getPackage()) && extClsInfo.getParentClass() == null) {
            return shortName;
        }
        if (extClsInfo.getAliasPkg().equals(useCls.getAliasPkg())) {
            if (!extClsInfo.isInner()) {
                // 同一个包下的类不生成 import
                return shortName;
            }
            fullName = extClsInfo.getAliasNameWithoutPackage();
        }
        for (ClassInfo importCls : getImports()) {
            if (!importCls.equals(extClsInfo)
                    && importCls.getAliasShortName().equals(shortName)) {
                if (extClsInfo.isInner()) {
                    String parent = useClassInternal(useCls, extClsInfo.getParentClass());
                    return parent + '.' + shortName;
                } else {
                    return fullName;
                }
            }
        }
        addImport(extClsInfo);
        return shortName;
    }

    private String expandInnerClassName(ClassInfo useCls, ClassInfo extClsInfo) {
        List<ClassInfo> clsList = new ArrayList<>();
        clsList.add(extClsInfo);
        ClassInfo parentCls = extClsInfo.getParentClass();
        boolean addImport = true;
        while (parentCls != null) {
            if (parentCls == useCls || isClassInnerFor(useCls, parentCls)) {
                addImport = false;
                break;
            }
            clsList.add(parentCls);
            parentCls = parentCls.getParentClass();
        }
        Collections.reverse(clsList);
        if (addImport) {
            ClassInfo top = clsList.get(0);
            if (top != extClsInfo) {
                String usedName = useClassInternal(useCls, top);
                if (!usedName.equals(top.getAliasShortName())) {
                    // 无法使用顶层类短名时，回退使用全限定名
                    return extClsInfo.getAliasFullName();
                }
            } else {
                addImport(top);
            }
        }
        return Utils.listToString(clsList, ".", ClassInfo::getAliasShortName);
    }

    private void addImport(ClassInfo classInfo) {
        if (parentGen != null) {
            parentGen.addImport(classInfo);
        } else {
            imports.add(classInfo);
        }
    }

    public Set<ClassInfo> getImports() {
        if (parentGen != null) {
            return parentGen.getImports();
        } else {
            return imports;
        }
    }

    public ClassGen getParentGen() {
        return parentGen == null ? this : parentGen;
    }

    public AnnotationGen getAnnotationGen() {
        return annotationGen;
    }

    public boolean isFallbackMode() {
        return fallback;
    }

    public boolean isBodyGenStarted() {
        return bodyGenStarted;
    }

    public void setBodyGenStarted(boolean bodyGenStarted) {
        this.bodyGenStarted = bodyGenStarted;
    }

    @Nullable
    public NameGen getOuterNameGen() {
        return outerNameGen;
    }

    public void setOuterNameGen(@NotNull NameGen outerNameGen) {
        this.outerNameGen = outerNameGen;
    }
}
