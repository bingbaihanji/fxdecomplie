package com.bingbaihanji.fxdecomplie.service.reference;

import com.bingbaihanji.classgraph.metadata.ClassInfo;
import com.bingbaihanji.fxdecomplie.model.ClassIndexEntry;
import com.bingbaihanji.fxdecomplie.model.MemberIndexEntry;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.model.reference.InheritanceReferenceGroup;
import com.bingbaihanji.fxdecomplie.model.reference.InheritanceReferenceNode;
import com.bingbaihanji.fxdecomplie.model.reference.InheritanceReferenceTree;
import com.bingbaihanji.fxdecomplie.model.reference.Kind;
import com.bingbaihanji.fxdecomplie.util.i18n.I18nUtil;
import org.objectweb.asm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 继承引用分析服务,构建包含类级别和方法级别关系的继承引用树
 *
 * <p>类级别:父类、接口、注解、实现类、子类</p>
 * <p>方法级别:重写的方法、被子类重写的方法、继承但未重写的方法</p>
 *
 * @author bingbaihanji
 * @date 2026-07-20
 */
public final class InheritanceReferenceService {

    private static final Logger log = LoggerFactory.getLogger(InheritanceReferenceService.class);
    private static final int MAX_OVERFLOW = 200;
    private static final int MAX_METHOD_ITEMS = 50;

    private InheritanceReferenceService() {
        throw new AssertionError("utility class");
    }

    /**
     * 构建指定类的完整继承引用树(类级别 + 方法级别)
     *
     * @param workspace         当前工作区
     * @param fullPath          目标类完整路径
     * @param currentClassBytes 当前类字节码
     * @return 继承引用树
     */
    public static InheritanceReferenceTree buildTree(Workspace workspace, String fullPath,
                                                     byte[] currentClassBytes) {
        if (fullPath == null || fullPath.isBlank()) {
            return emptyTree(I18nUtil.getString("inheritance.status.failed"));
        }
        String internalName = normalizeInternalName(fullPath);
        byte[] bytes = currentClassBytes != null && currentClassBytes.length > 0
                ? currentClassBytes
                : readCurrentBytes(workspace, internalName);

        if (bytes == null || bytes.length < 10) {
            return emptyTree(I18nUtil.getString("inheritance.unavailable"));
        }

        LocalMetadata meta = parseLocal(bytes);
        if (meta == null) {
            return emptyTree(I18nUtil.getString("inheritance.unavailable"));
        }

        InheritanceReferenceIndex index = InheritanceReferenceIndexService.getIfReady(workspace);

        InheritanceReferenceNode root = new InheritanceReferenceNode(
                meta.name, simpleName(meta.name), Kind.SELF,
                resolveFullPath(workspace, meta.name, index), 0, true);

        List<InheritanceReferenceGroup> groups = new ArrayList<>();

        // ── 类级别分组 ──
        if (meta.superName != null && !"java/lang/Object".equals(meta.superName)) {
            groups.add(singleGroup(Kind.SUPER_CLASS,
                    I18nUtil.getString("inheritance.group.super"),
                    List.of(nodeFor(workspace, meta.superName, Kind.SUPER_CLASS, 1, index)),
                    false));
        }

        List<InheritanceReferenceNode> interfaceNodes = new ArrayList<>();
        for (String itf : meta.interfaces) {
            interfaceNodes.add(nodeFor(workspace, itf, Kind.INTERFACE, 1, index));
        }
        if (!interfaceNodes.isEmpty()) {
            groups.add(singleGroup(Kind.INTERFACE,
                    I18nUtil.getString("inheritance.group.interfaces"),
                    interfaceNodes, false));
        }

        // 注解：优先从 ClassGraph 全局索引获取，回退到本地 ASM 解析
        List<InheritanceReferenceNode> annotationNodes = new ArrayList<>();
        if (index != null) {
            ClassInfo ci = index.classInfo(meta.name);
            if (ci != null) {
                for (var ai : ci.getAnnotationInfo()) {
                    annotationNodes.add(nodeFor(workspace, ai.getName(), Kind.ANNOTATION, 1, index));
                }
            }
        }
        // 回退：索引未就绪时使用本地解析
        if (annotationNodes.isEmpty() && !meta.annotations.isEmpty()) {
            for (String ann : meta.annotations) {
                annotationNodes.add(nodeFor(workspace, ann, Kind.ANNOTATION, 1, index));
            }
        }
        if (!annotationNodes.isEmpty()) {
            groups.add(singleGroup(Kind.ANNOTATION,
                    I18nUtil.getString("inheritance.group.annotations"),
                    annotationNodes, false));
        }

        // ── 方法级别分组:重写的方法 ──
        addMethodOverrideGroups(workspace, meta, groups);

        // ── 方法级别分组:被重写的方法 ──
        addOverriddenBySubclassesGroup(workspace, meta, groups);

        // ── 方法级别分组:未重写的继承方法 ──
        addInheritedMethodsGroup(workspace, meta, groups);

        // ── 全局关系(需要 ClassGraph 索引) ──
        boolean partial = false;
        String statusMessage = I18nUtil.getString("inheritance.status.ready");
        if (index == null) {
            partial = true;
            statusMessage = I18nUtil.getString("inheritance.status.building");
            InheritanceReferenceIndexService.getOrStart(workspace);
        } else {
            addImplementationGroup(workspace, index, meta.name, groups);
            addSubclassGroup(workspace, index, meta.name, groups);
            addAnnotatedClassesGroup(workspace, index, meta, groups);
        }

        return new InheritanceReferenceTree(root, groups, partial, statusMessage);
    }

    // ── 方法级别分析 ──

    /**
     * 添加"重写的方法"分组:
     * 列出当前类中重写了父类或接口方法的所有方法
     */
    private static void addMethodOverrideGroups(Workspace workspace, LocalMetadata meta,
                                                List<InheritanceReferenceGroup> groups) {
        if (meta.methods.isEmpty()) {
            return;
        }

        // 收集父类 + 接口的所有方法
        Map<String, Set<String>> ancestorMethods = new LinkedHashMap<>(); // methodName -> descriptors
        collectAncestorMethods(workspace, meta.superName, ancestorMethods, new HashSet<>());
        for (String itf : meta.interfaces) {
            collectAncestorMethods(workspace, itf, ancestorMethods, new HashSet<>());
        }

        if (ancestorMethods.isEmpty()) {
            return;
        }

        List<InheritanceReferenceNode> overrides = new ArrayList<>();
        for (MethodData m : meta.methods) {
            if (!isOverridableMethod(m)) {
                continue;
            }
            Set<String> descriptors = ancestorMethods.get(m.name);
            if (descriptors != null && descriptors.contains(m.descriptor)) {
                String owner = findMethodOwner(workspace, meta.superName, meta.interfaces,
                        m.name, m.descriptor);
                String ownerDisplay = owner != null ? " ← " + simpleName(owner) : "";
                overrides.add(InheritanceReferenceNode.methodNode(
                        m.name + ownerDisplay, null, Kind.OVERRIDES,
                        meta.name, resolveFullPath(workspace, meta.name), 1));
            }
        }

        if (!overrides.isEmpty()) {
            // 限制数量
            if (overrides.size() > MAX_METHOD_ITEMS) {
                overrides = overrides.subList(0, MAX_METHOD_ITEMS);
            }
            groups.add(singleGroup(Kind.OVERRIDES,
                    I18nUtil.getString("inheritance.group.overrides"),
                    overrides, overrides.size() > 10));
        }
    }

    /**
     * 添加"被子类重写的方法"分组:
     * 查找子类中重写了当前类方法的所有方法
     */
    private static void addOverriddenBySubclassesGroup(Workspace workspace, LocalMetadata meta,
                                                       List<InheritanceReferenceGroup> groups) {
        WorkspaceIndex wsIndex = workspaceIndexOf(workspace);
        if (wsIndex == null || meta.methods.isEmpty()) {
            return;
        }

        Set<String> descendants = collectDescendants(wsIndex, meta.name);
        if (descendants.isEmpty()) {
            return;
        }

        List<InheritanceReferenceNode> overriddenBy = new ArrayList<>();
        for (MethodData m : meta.methods) {
            if (!isOverridableMethod(m)) {
                continue;
            }
            for (String subName : descendants) {
                if (overriddenBy.size() >= MAX_METHOD_ITEMS) {
                    break;
                }
                if (hasMethod(wsIndex, subName, m.name, m.descriptor)) {
                    String subPath = resolveFullPath(workspace, subName);
                    overriddenBy.add(InheritanceReferenceNode.methodNode(
                            simpleName(subName) + "." + m.name, m.descriptor,
                            Kind.OVERRIDDEN_BY, subName,
                            subPath != null ? subPath : subName + ".class", 1));
                }
            }
        }

        if (!overriddenBy.isEmpty()) {
            groups.add(singleGroup(Kind.OVERRIDDEN_BY,
                    I18nUtil.getString("inheritance.group.overriddenBy"),
                    overriddenBy, overriddenBy.size() > 10));
        }
    }

    /**
     * 添加"未重写的继承方法"分组:
     * 列出父类和接口中定义但当前类未重写的方法
     */
    private static void addInheritedMethodsGroup(Workspace workspace, LocalMetadata meta,
                                                 List<InheritanceReferenceGroup> groups) {
        // 仅对非接口类显示继承方法
        boolean isInterface = Modifier.isInterface(meta.access);
        if (isInterface) {
            return;
        }

        // 收集当前类已重写的方法名+描述符
        Set<String> overriddenKeys = meta.methods.stream()
                .filter(InheritanceReferenceService::isOverridableMethod)
                .map(m -> m.name + ":" + m.descriptor)
                .collect(Collectors.toSet());

        // 从父类和接口收集未重写的方法
        List<InheritanceReferenceNode> inherited = new ArrayList<>();

        WorkspaceIndex wsIndex = workspaceIndexOf(workspace);
        // 处理父类
        if (meta.superName != null && !"java/lang/Object".equals(meta.superName)) {
            addInheritedFrom(workspace, wsIndex, meta.superName, overriddenKeys,
                    inherited, meta.superName);
        }
        // 处理接口
        for (String itf : meta.interfaces) {
            addInheritedFrom(workspace, wsIndex, itf, overriddenKeys, inherited, itf);
        }

        if (!inherited.isEmpty()) {
            groups.add(singleGroup(Kind.INHERITED,
                    I18nUtil.getString("inheritance.group.inheritedMethods"),
                    inherited, true));
        }
    }

    /** 从指定祖先类收集未被重写的方法 */
    private static void addInheritedFrom(Workspace workspace, WorkspaceIndex wsIndex,
                                         String ancestorName, Set<String> overriddenKeys,
                                         List<InheritanceReferenceNode> inherited,
                                         String sourceName) {
        if (inherited.size() >= MAX_METHOD_ITEMS) {
            return;
        }

        List<MethodData> ancestorMethods = getMethods(workspace, wsIndex, ancestorName);

        for (MethodData m : ancestorMethods) {
            if (inherited.size() >= MAX_METHOD_ITEMS) {
                break;
            }
            if (!isOverridableMethod(m)) {
                continue;
            }
            String key = m.name + ":" + m.descriptor;
            if (!overriddenKeys.contains(key)) {
                inherited.add(InheritanceReferenceNode.methodNode(
                        m.name, null, Kind.INHERITED, sourceName,
                        resolveFullPath(workspace, ancestorName), 1));
                overriddenKeys.add(key);
            }
        }
    }

    // ── 辅助方法 ──

    /** 收集祖先类(父类+接口)的所有方法 */
    private static void collectAncestorMethods(Workspace workspace, String ancestorName,
                                               Map<String, Set<String>> result,
                                               Set<String> visited) {
        if (ancestorName == null || "java/lang/Object".equals(ancestorName)
                || !visited.add(ancestorName)) {
            return;
        }
        // 跳过标准库
        if (ancestorName.startsWith("java/") || ancestorName.startsWith("javax/")) {
            return;
        }

        WorkspaceIndex wsIndex = workspaceIndexOf(workspace);
        List<MethodData> methods = getMethods(workspace, wsIndex, ancestorName);

        for (MethodData m : methods) {
            if (isOverridableMethod(m)) {
                result.computeIfAbsent(m.name, k -> new LinkedHashSet<>()).add(m.descriptor);
            }
        }

        // 递归收集父类的方法
        ClassIndexEntry entry = wsIndex != null ? wsIndex.findClass(ancestorName) : null;
        String superName = null;
        if (entry != null) {
            superName = entry.superName();
        } else {
            byte[] bytes = readCurrentBytes(workspace, ancestorName);
            if (bytes != null) {
                LocalMetadata ancestorMeta = parseLocal(bytes);
                if (ancestorMeta != null) {
                    superName = ancestorMeta.superName;
                }
            }
        }
        if (superName != null && !"java/lang/Object".equals(superName)) {
            collectAncestorMethods(workspace, superName, result, visited);
        }
    }

    /** 查找方法所在的直接祖先(父类或接口) */
    private static String findMethodOwner(Workspace workspace, String superName,
                                          List<String> interfaces, String methodName,
                                          String descriptor) {
        // 先检查接口
        for (String itf : interfaces) {
            if (hasMethod(workspaceIndexOf(workspace), itf, methodName, descriptor)) {
                return itf;
            }
        }
        // 再检查父类链
        if (superName != null && hasMethod(workspaceIndexOf(workspace), superName, methodName, descriptor)) {
            return superName;
        }
        return null;
    }

    /** 检查指定类是否有匹配名称+描述符的方法 */
    private static boolean hasMethod(WorkspaceIndex wsIndex, String internalName,
                                     String methodName, String descriptor) {
        if (wsIndex == null) {
            return false;
        }
        ClassIndexEntry entry = wsIndex.findClass(internalName);
        if (entry != null) {
            for (MemberIndexEntry m : entry.methods()) {
                if (m.name().equals(methodName) && m.descriptor().equals(descriptor)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isOverridableMethod(MethodData method) {
        if (method == null || method.name == null || method.name.startsWith("<")) {
            return false;
        }
        return !Modifier.isStatic(method.access)
                && !Modifier.isPrivate(method.access)
                && !Modifier.isFinal(method.access);
    }

    /** 获取类的方法列表(优先解析字节码以保留访问标志,失败时回退到索引) */
    private static List<MethodData> getMethods(Workspace workspace, WorkspaceIndex wsIndex,
                                               String internalName) {
        if (internalName == null || internalName.isBlank()) {
            return List.of();
        }
        byte[] bytes = readCurrentBytes(workspace, internalName);
        if (bytes != null) {
            LocalMetadata metadata = parseLocal(bytes);
            if (metadata != null) {
                return metadata.methods;
            }
        }
        if (wsIndex == null) {
            return List.of();
        }
        ClassIndexEntry entry = wsIndex.findClass(internalName);
        if (entry == null) {
            return List.of();
        }
        List<MethodData> result = new ArrayList<>();
        for (MemberIndexEntry m : entry.methods()) {
            result.add(new MethodData(m.name(), m.descriptor(), 0));
        }
        return result;
    }

    // ── 全局索引相关(现有逻辑) ──

    private static void addImplementationGroup(Workspace workspace, InheritanceReferenceIndex index,
                                               String internalName, List<InheritanceReferenceGroup> groups) {
        ClassInfo ci = index.classInfo(internalName);
        if (ci == null || !ci.isInterface()) {
            return;
        }
        List<String> impls = collectImplementations(index, internalName);
        if (impls.isEmpty()) {
            return;
        }
        groups.add(buildOverflowGroup(workspace, Kind.IMPLEMENTATION,
                I18nUtil.getString("inheritance.group.implementations"),
                impls, 1, true, index));
    }

    private static void addSubclassGroup(Workspace workspace, InheritanceReferenceIndex index,
                                         String internalName, List<InheritanceReferenceGroup> groups) {
        ClassInfo ci = index.classInfo(internalName);
        if (ci != null && ci.isInterface()) {
            return;
        }
        List<String> subs = index.subclassesOf(internalName);
        if (subs.isEmpty()) {
            return;
        }
        groups.add(buildOverflowGroup(workspace, Kind.SUBCLASS,
                I18nUtil.getString("inheritance.group.subclasses"),
                subs, 1, true, index));
    }

    private static void addAnnotatedClassesGroup(Workspace workspace, InheritanceReferenceIndex index,
                                                 LocalMetadata meta,
                                                 List<InheritanceReferenceGroup> groups) {
        if (!Modifier.isAnnotation(meta.access)) {
            return;
        }
        List<String> annotated = index.annotatedBy(meta.name);
        if (annotated.isEmpty()) {
            return;
        }
        groups.add(buildOverflowGroup(workspace, Kind.ANNOTATION,
                I18nUtil.getString("inheritance.group.annotatedClasses"),
                annotated, 1, true, index));
    }

    private static Set<String> collectDescendants(WorkspaceIndex index, String internalName) {
        if (index == null || internalName == null || internalName.isBlank()) {
            return Set.of();
        }
        Map<String, List<String>> byParent = index.subclassesByParent();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>(byParent.getOrDefault(internalName, List.of()));
        while (!queue.isEmpty() && result.size() < MAX_OVERFLOW) {
            String child = queue.removeFirst();
            if (!result.add(child)) {
                continue;
            }
            for (String grandChild : byParent.getOrDefault(child, List.of())) {
                if (!result.contains(grandChild)) {
                    queue.addLast(grandChild);
                }
            }
        }
        return result;
    }

    private static List<String> collectImplementations(InheritanceReferenceIndex index, String interfaceName) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(interfaceName);
        Set<String> visited = new HashSet<>();
        while (!queue.isEmpty() && result.size() < MAX_OVERFLOW) {
            String current = queue.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            for (String impl : index.implementationsOf(current)) {
                ClassInfo info = index.classInfo(impl);
                if (info != null && info.isInterface()) {
                    queue.addLast(impl);
                } else {
                    result.add(impl);
                }
            }
            for (String subInterface : index.subclassesOf(current)) {
                ClassInfo info = index.classInfo(subInterface);
                if (info != null && info.isInterface()) {
                    queue.addLast(subInterface);
                }
            }
        }
        return List.copyOf(result);
    }

    private static InheritanceReferenceGroup buildOverflowGroup(Workspace workspace, Kind kind,
                                                                String title, List<String> names,
                                                                int depth, boolean collapsible,
                                                                InheritanceReferenceIndex index) {
        List<InheritanceReferenceNode> children = new ArrayList<>();
        int limit = Math.min(names.size(), MAX_OVERFLOW);
        for (int i = 0; i < limit; i++) {
            children.add(nodeFor(workspace, names.get(i), kind, depth, index));
        }
        if (names.size() > MAX_OVERFLOW) {
            int overflow = names.size() - MAX_OVERFLOW;
            children.add(new InheritanceReferenceNode("",
                    I18nUtil.getString("inheritance.group.overflow", String.valueOf(overflow)),
                    Kind.UNRESOLVED, "", depth + 1, false));
        }
        return singleGroup(kind, title, children, collapsible);
    }

    private static InheritanceReferenceGroup singleGroup(Kind kind, String title,
                                                         List<InheritanceReferenceNode> children,
                                                         boolean collapsible) {
        return new InheritanceReferenceGroup(kind, title, children, collapsible);
    }

    private static InheritanceReferenceNode nodeFor(Workspace workspace, String internalName,
                                                    Kind kind, int depth) {
        return nodeFor(workspace, internalName, kind, depth,
                InheritanceReferenceIndexService.getIfReady(workspace));
    }

    private static InheritanceReferenceNode nodeFor(Workspace workspace, String internalName,
                                                    Kind kind, int depth,
                                                    InheritanceReferenceIndex index) {
        String fullPath = resolveFullPath(workspace, internalName, index);
        return new InheritanceReferenceNode(internalName, simpleName(internalName), kind,
                fullPath, depth, fullPath != null && !fullPath.isBlank());
    }

    private static String resolveFullPath(Workspace workspace, String internalName) {
        return resolveFullPath(workspace, internalName,
                InheritanceReferenceIndexService.getIfReady(workspace));
    }

    private static String resolveFullPath(Workspace workspace, String internalName,
                                          InheritanceReferenceIndex index) {
        if (workspace == null) {
            return null;
        }
        if (index != null) {
            String fp = index.fullPathOf(internalName);
            if (fp != null && !fp.isBlank()) {
                return fp;
            }
        }
        WorkspaceIndex wsIndex = workspace.getIndex();
        if (wsIndex != null && wsIndex != WorkspaceIndex.EMPTY) {
            var entry = wsIndex.findClass(internalName);
            if (entry != null) {
                return entry.fullPath();
            }
        }
        return null;
    }

    private static byte[] readCurrentBytes(Workspace workspace, String internalName) {
        if (workspace == null) {
            return null;
        }
        WorkspaceIndex index = workspace.getIndex();
        if (index != null && index != WorkspaceIndex.EMPTY) {
            return index.getClassBytes(internalName);
        }
        return null;
    }

    private static WorkspaceIndex workspaceIndexOf(Workspace workspace) {
        if (workspace == null) {
            return null;
        }
        WorkspaceIndex idx = workspace.getIndex();
        return idx == WorkspaceIndex.EMPTY ? null : idx;
    }

    private static String normalizeInternalName(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.replace('\\', '/').strip();
        if (normalized.endsWith(".class")) {
            normalized = normalized.substring(0, normalized.length() - 6);
        }
        return normalized;
    }

    private static String simpleName(String internalName) {
        int idx = internalName.lastIndexOf('/');
        return idx >= 0 ? internalName.substring(idx + 1) : internalName;
    }

    private static InheritanceReferenceTree emptyTree(String message) {
        InheritanceReferenceNode root = new InheritanceReferenceNode("", "", Kind.SELF, "", 0, false);
        return new InheritanceReferenceTree(root, List.of(), false, message);
    }

    /**
     * 解析类字节码,提取类级别和方法级别元数据
     */
    private static LocalMetadata parseLocal(byte[] bytes) {
        try {
            ClassReader reader = new ClassReader(bytes);
            LocalVisitor visitor = new LocalVisitor();
            reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return new LocalMetadata(visitor.name, visitor.access, visitor.superName,
                    List.copyOf(visitor.interfaces), List.copyOf(visitor.annotations),
                    List.copyOf(visitor.methods));
        } catch (Exception e) {
            log.warn("ASM parse failed in InheritanceReferenceService", e);
            return null;
        }
    }

    // ── 内部类型 ──

    private static final class LocalVisitor extends ClassVisitor {
        final List<String> interfaces = new ArrayList<>();
        final List<String> annotations = new ArrayList<>();
        final List<MethodData> methods = new ArrayList<>();
        String name;
        int access;
        String superName;

        LocalVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            this.name = name;
            this.access = access;
            this.superName = superName;
            if (interfaces != null) {
                this.interfaces.addAll(List.of(interfaces));
            }
        }

        @Override
        public org.objectweb.asm.AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            Type type = Type.getType(descriptor);
            String internalName = type.getInternalName();
            if (internalName != null && !internalName.isBlank()) {
                annotations.add(internalName);
            }
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            methods.add(new MethodData(name, descriptor, access));
            return null; // 不需要访问方法体
        }
    }

    /**
     * 轻量级方法数据(不完整解析,仅提取名称 描述符和访问标志)
     */
    private record MethodData(String name, String descriptor, int access) {
    }

    /**
     * 类级别元数据(含方法列表)
     */
    private record LocalMetadata(String name, int access, String superName,
                                 List<String> interfaces, List<String> annotations,
                                 List<MethodData> methods) {
    }

    // 本地引用 java.lang.reflect.Modifier,避免歧义
    private static final class Modifier {
        static boolean isStatic(int access) {
            return (access & Opcodes.ACC_STATIC) != 0;
        }

        static boolean isPrivate(int access) {
            return (access & Opcodes.ACC_PRIVATE) != 0;
        }

        static boolean isFinal(int access) {
            return (access & Opcodes.ACC_FINAL) != 0;
        }

        static boolean isInterface(int access) {
            return (access & Opcodes.ACC_INTERFACE) != 0;
        }

        static boolean isAnnotation(int access) {
            return (access & Opcodes.ACC_ANNOTATION) != 0;
        }
    }
}
