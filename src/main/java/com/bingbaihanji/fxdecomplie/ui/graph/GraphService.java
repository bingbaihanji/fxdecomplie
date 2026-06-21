package com.bingbaihanji.fxdecomplie.ui.graph;

import com.bingbaihanji.fxdecomplie.ui.inheritance.InheritanceNode;
import javafx.scene.control.TreeItem;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 图形服务工具类，负责 DOT 字符串生成和类内方法调用解析
 *
 * @author bingbaihanji
 * @date 2026-06-21
 */
public final class GraphService {

    private static final Logger logger = LoggerFactory.getLogger(GraphService.class);

    /** 方法图节点数上限 */
    private static final int MAX_METHOD_NODES = 300;

    private GraphService() {
        throw new AssertionError("utility class");
    }

    /**
     * 从继承树生成 DOT 格式字符串
     *
     * @param root 继承树根节点
     * @return DOT 格式字符串
     */
    public static String toInheritanceDOT(TreeItem<InheritanceNode> root) {
        StringBuilder sb = new StringBuilder();
        sb.append("digraph InheritanceGraph {\n");
        sb.append("    rankdir=BT;\n");
        sb.append("    node [shape=box, style=filled, fontname=\"Consolas\"];\n");
        sb.append("    edge [arrowhead=onormal];\n");

        Set<String> seen = new LinkedHashSet<>();
        appendInheritanceNodes(sb, root, seen, 0);
        appendInheritanceEdges(sb, root, seen);

        sb.append("}\n");
        return sb.toString();
    }

    private static void appendInheritanceNodes(StringBuilder sb, TreeItem<InheritanceNode> item,
                                                Set<String> seen, int depth) {
        if (item == null || depth > 20) return;
        InheritanceNode node = item.getValue();
        if (node == null || seen.contains(node.className())) return;
        seen.add(node.className());

        String color = switch (node.relation()) {
            case SELF -> "#dcdcaa";
            case SUPER_CLASS -> "#569cd6";
            case SUBCLASS -> "#6a9955";
            case INTERFACE -> "#c586c0";
        };
        String label = shortName(node.displayName());
        sb.append("    \"").append(escapeDot(node.className()))
                .append("\" [label=\"").append(escapeDot(label))
                .append("\", fillcolor=\"").append(color).append("\"];\n");

        for (TreeItem<InheritanceNode> child : item.getChildren()) {
            appendInheritanceNodes(sb, child, seen, depth + 1);
        }
    }

    private static void appendInheritanceEdges(StringBuilder sb, TreeItem<InheritanceNode> item,
                                                Set<String> seen) {
        if (item == null) return;
        InheritanceNode parent = item.getValue();
        if (parent == null) return;

        for (TreeItem<InheritanceNode> child : item.getChildren()) {
            InheritanceNode childNode = child.getValue();
            if (childNode != null && seen.contains(parent.className())
                    && seen.contains(childNode.className())) {
                sb.append("    \"").append(escapeDot(childNode.className()))
                        .append("\" -> \"")
                        .append(escapeDot(parent.className())).append("\";\n");
            }
            appendInheritanceEdges(sb, child, seen);
        }
    }

    /**
     * 从方法调用图生成 DOT 格式字符串
     *
     * @param graph 方法调用图
     * @return DOT 格式字符串
     */
    public static String toMethodDOT(MethodGraph graph) {
        if (graph == null || graph.methods().isEmpty()) {
            return "digraph G { label=\"无方法调用数据\" }";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("digraph MethodGraph {\n");
        sb.append("    rankdir=LR;\n");
        sb.append("    node [shape=box, style=filled, fontname=\"Consolas\", fillcolor=\"#3c3c3c\", "
                + "fontcolor=\"#d4d4d4\", color=\"#569cd6\"];\n");
        sb.append("    edge [color=\"#808080\"];\n");

        Set<String> seen = new LinkedHashSet<>();
        int count = 0;
        for (MethodNode node : graph.methods()) {
            if (count >= MAX_METHOD_NODES) break;
            String id = "N" + count;
            String label = node.displayName();
            sb.append("    ").append(id).append(" [label=\"")
                    .append(escapeDot(label)).append("\"];\n");
            seen.add(nodeKey(node));
            count++;
        }

        for (MethodEdge edge : graph.edges()) {
            String fromKey = nodeKey(edge.from());
            String toKey = nodeKey(edge.to());
            if (seen.contains(fromKey) && seen.contains(toKey)) {
                sb.append("    ").append(nodeId(edge.from()))
                        .append(" -> ").append(nodeId(edge.to())).append(";\n");
            }
        }

        if (graph.methods().size() > MAX_METHOD_NODES) {
            sb.append("    label=\"方法过多，已截断（显示前 ")
                    .append(MAX_METHOD_NODES).append(" 个）\";\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * 从 classBytes 解析类内方法调用关系
     *
     * @param classBytes 类文件字节码
     * @return 方法调用图，失败返回空图
     */
    public static MethodGraph parseMethodCalls(byte[] classBytes) {
        if (classBytes == null || classBytes.length == 0) {
            return new MethodGraph("", List.of(), Set.of());
        }
        try {
            ClassReader reader = new ClassReader(classBytes);
            MethodCallCollector collector = new MethodCallCollector();
            reader.accept(collector, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return collector.build();
        } catch (Exception e) {
            logger.debug("解析方法调用失败", e);
            return new MethodGraph("", List.of(), Set.of());
        }
    }

    /** 转义 DOT 字符串中的特殊字符 */
    public static String escapeDot(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("<", "\\<")
                .replace(">", "\\>")
                .replace("{", "\\{")
                .replace("}", "\\}");
    }

    private static String shortName(String fullName) {
        if (fullName == null) return "";
        int dot = fullName.lastIndexOf('.');
        return dot >= 0 ? fullName.substring(dot + 1) : fullName;
    }

    private static String nodeKey(MethodNode n) {
        return n.name() + n.descriptor();
    }

    private static int nodeId(MethodNode n) {
        return Math.abs(Objects.hash(n.name(), n.descriptor())) % MAX_METHOD_NODES;
    }

    /**
     * 方法调用图数据结构
     */
    public record MethodGraph(
            String ownerInternalName,
            List<MethodNode> methods,
            Set<MethodEdge> edges
    ) {
    }

    /**
     * 方法节点
     */
    public record MethodNode(String name, String descriptor, String displayName) {
    }

    /**
     * 方法调用边
     */
    public record MethodEdge(MethodNode from, MethodNode to) {
    }

    /** ASM ClassVisitor，扫描方法体中的类内部方法调用 */
    private static final class MethodCallCollector extends ClassVisitor {
        private String owner;
        private final Map<String, MethodNode> nodeMap = new LinkedHashMap<>();
        private final Set<MethodEdge> edges = new LinkedHashSet<>();

        MethodCallCollector() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String sig,
                          String superName, String[] interfaces) {
            this.owner = name;
            super.visit(version, access, name, sig, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc,
                                          String sig, String[] exceptions) {
            String displayName = name + descToDisplay(desc);
            MethodNode currentNode = new MethodNode(name, desc, displayName);
            nodeMap.putIfAbsent(nodeKey(currentNode), currentNode);

            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(int opcode, String ownerName, String mName,
                                            String mDesc, boolean itf) {
                    if (!owner.equals(ownerName)) return; // 仅类内部调用
                    if (nodeMap.size() >= MAX_METHOD_NODES) return;

                    String display = mName + descToDisplay(mDesc);
                    MethodNode target = new MethodNode(mName, mDesc, display);
                    nodeMap.putIfAbsent(nodeKey(target), target);
                    edges.add(new MethodEdge(currentNode, target));
                }
            };
        }

        MethodGraph build() {
            return new MethodGraph(owner, List.copyOf(nodeMap.values()), Set.copyOf(edges));
        }

        private static String descToDisplay(String desc) {
            return "(" + desc.substring(1, desc.indexOf(')')) + ")";
        }
    }
}
