package com.bingbaihanji.fxdecomplie.service.reference;

import com.bingbaihanji.fxdecomplie.core.classgraph.ClassInfo;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.model.reference.InheritanceReferenceGroup;
import com.bingbaihanji.fxdecomplie.model.reference.InheritanceReferenceNode;
import com.bingbaihanji.fxdecomplie.model.reference.InheritanceReferenceTree;
import com.bingbaihanji.fxdecomplie.model.reference.Kind;
import com.bingbaihanji.fxdecomplie.util.i18n.I18nUtil;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class InheritanceReferenceService {

    private static final int MAX_OVERFLOW = 200;

    private InheritanceReferenceService() {
        throw new AssertionError("utility class");
    }

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

        // Parse with ASM to get superName, interfaces, annotations
        LocalMetadata meta = parseLocal(bytes);
        if (meta == null) {
            return emptyTree(I18nUtil.getString("inheritance.unavailable"));
        }

        InheritanceReferenceNode root = new InheritanceReferenceNode(
                meta.name, simpleName(meta.name), Kind.SELF,
                resolveFullPath(workspace, meta.name), 0, true);

        List<InheritanceReferenceGroup> groups = new ArrayList<>();

        // Super class
        if (meta.superName != null && !"java/lang/Object".equals(meta.superName)) {
            groups.add(singleGroup(Kind.SUPER_CLASS,
                    I18nUtil.getString("inheritance.group.super"),
                    List.of(nodeFor(workspace, meta.superName, Kind.SUPER_CLASS, 1)),
                    false));
        }

        // Interfaces
        List<InheritanceReferenceNode> interfaceNodes = new ArrayList<>();
        for (String itf : meta.interfaces) {
            interfaceNodes.add(nodeFor(workspace, itf, Kind.INTERFACE, 1));
        }
        if (!interfaceNodes.isEmpty()) {
            groups.add(singleGroup(Kind.INTERFACE,
                    I18nUtil.getString("inheritance.group.interfaces"),
                    interfaceNodes, false));
        }

        // Annotations
        if (!meta.annotations.isEmpty()) {
            List<InheritanceReferenceNode> annotationNodes = new ArrayList<>();
            for (String ann : meta.annotations) {
                annotationNodes.add(nodeFor(workspace, ann, Kind.ANNOTATION, 1));
            }
            groups.add(singleGroup(Kind.ANNOTATION,
                    I18nUtil.getString("inheritance.group.annotations"),
                    annotationNodes, false));
        }

        // Global relations
        InheritanceReferenceIndex index = InheritanceReferenceIndexService.getIfReady(workspace);
        boolean partial = false;
        String statusMessage = I18nUtil.getString("inheritance.status.ready");
        if (index == null) {
            partial = true;
            statusMessage = I18nUtil.getString("inheritance.status.building");
            InheritanceReferenceIndexService.getOrStart(workspace);
        } else {
            addImplementationGroup(workspace, index, meta.name, groups);
            addSubclassGroup(workspace, index, meta.name, groups);
        }

        return new InheritanceReferenceTree(root, groups, partial, statusMessage);
    }

    private static void addImplementationGroup(Workspace workspace, InheritanceReferenceIndex index,
                                               String internalName, List<InheritanceReferenceGroup> groups) {
        ClassInfo ci = index.classInfo(internalName);
        if (ci == null || !ci.isInterface()) {
            return;
        }
        List<String> impls = index.implementationsOf(internalName);
        if (impls.isEmpty()) {
            return;
        }
        groups.add(buildOverflowGroup(workspace, Kind.IMPLEMENTATION,
                I18nUtil.getString("inheritance.group.implementations"),
                impls, 1, true));
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
                subs, 1, true));
    }

    private static InheritanceReferenceGroup buildOverflowGroup(Workspace workspace, Kind kind,
                                                                String title, List<String> names,
                                                                int depth, boolean collapsible) {
        List<InheritanceReferenceNode> children = new ArrayList<>();
        int limit = Math.min(names.size(), MAX_OVERFLOW);
        for (int i = 0; i < limit; i++) {
            children.add(nodeFor(workspace, names.get(i), kind, depth));
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
        String fullPath = resolveFullPath(workspace, internalName);
        return new InheritanceReferenceNode(internalName, simpleName(internalName), kind,
                fullPath, depth, fullPath != null && !fullPath.isBlank());
    }

    private static String resolveFullPath(Workspace workspace, String internalName) {
        if (workspace == null) {
            return null;
        }
        InheritanceReferenceIndex index = InheritanceReferenceIndexService.getIfReady(workspace);
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

    private static LocalMetadata parseLocal(byte[] bytes) {
        try {
            ClassReader reader = new ClassReader(bytes);
            LocalVisitor visitor = new LocalVisitor();
            reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return new LocalMetadata(visitor.name, visitor.superName,
                    List.copyOf(visitor.interfaces), List.copyOf(visitor.annotations));
        } catch (Exception e) {
            return null;
        }
    }

    private static final class LocalVisitor extends ClassVisitor {
        String name;
        String superName;
        final List<String> interfaces = new ArrayList<>();
        final List<String> annotations = new ArrayList<>();

        LocalVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            this.name = name;
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
    }

    private record LocalMetadata(String name, String superName,
                                 List<String> interfaces, List<String> annotations) {}
}
