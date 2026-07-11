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
 * 图形服务工具类,负责 DOT 字符串生成和类内方法调用解析
 *
 * @author bingbaihanji
 * @date 2026-06-21
 */
public final class GraphService {

    private static final Logger log = LoggerFactory.getLogger(GraphService.class);

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

    /**
     * 递归遍历继承树,向 StringBuilder 追加节点声明
     *
     * <p>深度超过 20 层的子树会被截断,防止异常深度树导致 DOT 过大</p>
     *
     * @param sb    DOT 输出缓冲区
     * @param item  当前树节点
     * @param seen  已输出节点集合(防重)
     * @param depth 当前递归深度
     */
    private static void appendInheritanceNodes(StringBuilder sb, TreeItem<InheritanceNode> item,
                                               Set<String> seen, int depth) {
        if (item == null || depth > 20) {
            return;
        }
        InheritanceNode node = item.getValue();
        if (node == null || seen.contains(node.className())) {
            return;
        }
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

    /**
     * 递归为继承树生成边
     *
     * <p>边的方向总是指向父节点(配合 rankdir=BT 实现"自底向上"的继承层次)：
     * SUPER_CLASS / INTERFACE → child 是 parent 的父/接口,边 parent→child(指向更上层)；
     * SUBCLASS → child 是 parent 的子类,边 child→parent(指向被分析的类)</p>
     */
    private static void appendInheritanceEdges(StringBuilder sb, TreeItem<InheritanceNode> item,
                                               Set<String> seen) {
        if (item == null) {
            return;
        }
        InheritanceNode parent = item.getValue();
        if (parent == null) {
            return;
        }

        for (TreeItem<InheritanceNode> child : item.getChildren()) {
            InheritanceNode childNode = child.getValue();
            if (childNode != null && seen.contains(parent.className())
                    && seen.contains(childNode.className())) {
                if (childNode.relation() == InheritanceNode.RelationType.SUPER_CLASS
                        || childNode.relation() == InheritanceNode.RelationType.INTERFACE) {
                    // 父类/接口：箭头指向更上层
                    appendInheritanceEdge(sb, parent.className(), childNode.className());
                } else if (childNode.relation() == InheritanceNode.RelationType.SUBCLASS) {
                    // 子类：箭头指向被分析的类(parent)
                    appendInheritanceEdge(sb, childNode.className(), parent.className());
                }
            }
            appendInheritanceEdges(sb, child, seen);
        }
    }

    /**
     * 向 DOT 缓冲区追加一条继承边
     *
     * @param sb   DOT 输出缓冲区
     * @param from 起始节点标识(类全限定名)
     * @param to   目标节点标识(类全限定名)
     */
    private static void appendInheritanceEdge(StringBuilder sb, String from, String to) {
        sb.append("    \"").append(escapeDot(from))
                .append("\" -> \"")
                .append(escapeDot(to)).append("\";\n");
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

        Map<String, Integer> nodeIndex = new LinkedHashMap<>();
        int count = 0;
        for (MethodNode node : graph.methods()) {
            if (count >= MAX_METHOD_NODES) {
                break;
            }
            String key = nodeKey(node);
            nodeIndex.put(key, count);
            String id = "N" + count;
            String label = node.displayName();
            sb.append("    ").append(id).append(" [label=\"")
                    .append(escapeDot(label)).append("\"];\n");
            count++;
        }

        for (MethodEdge edge : graph.edges()) {
            Integer fromIdx = nodeIndex.get(nodeKey(edge.from()));
            Integer toIdx = nodeIndex.get(nodeKey(edge.to()));
            if (fromIdx != null && toIdx != null) {
                sb.append("    N").append(fromIdx)
                        .append(" -> N").append(toIdx).append(";\n");
            }
        }

        if (graph.methods().size() > MAX_METHOD_NODES) {
            sb.append("    label=\"方法过多,已截断(显示前 ")
                    .append(MAX_METHOD_NODES).append(" 个)\";\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * 从 classBytes 解析类内方法调用关系
     *
     * @param classBytes 类文件字节码
     * @return 方法调用图,失败返回空图
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
            log.debug("解析方法调用失败", e);
            return new MethodGraph("", List.of(), Set.of());
        }
    }

    /** 转义 DOT 字符串中的特殊字符 */
    public static String escapeDot(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("<", "\\<")
                .replace(">", "\\>")
                .replace("{", "\\{")
                .replace("}", "\\}");
    }

    /**
     * 从全限定类名中提取简短类名(去除包名前缀)
     *
     * @param fullName 全限定类名,如 "java.util.ArrayList"
     * @return 简短类名,如 "ArrayList"；null 输入返回空串
     */
    private static String shortName(String fullName) {
        if (fullName == null) {
            return "";
        }
        int dot = fullName.lastIndexOf('.');
        return dot >= 0 ? fullName.substring(dot + 1) : fullName;
    }

    /**
     * 生成方法节点的唯一键(方法名 + 描述符),用于去重和查找
     *
     * @param n 方法节点
     * @return 唯一键字符串
     */
    private static String nodeKey(MethodNode n) {
        return n.name() + n.descriptor();
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

    /**
     * ASM ClassVisitor,扫描方法体中的类内部方法调用,构建方法调用图
     *
     * <p>遍历每个方法的字节码指令,收集 INVOKEVIRTUAL/INVOKESTATIC 等调用指令,
     * 仅记录目标类与当前类相同的方法调用(类内部调用)</p>
     */
    private static final class MethodCallCollector extends ClassVisitor {
        /** 方法节点映射(键 = 方法名+描述符) */
        private final Map<String, MethodNode> nodeMap = new LinkedHashMap<>();
        /** 方法调用边集合(已去重) */
        private final Set<MethodEdge> edges = new LinkedHashSet<>();
        /** 当前正在解析的类内部名称 */
        private String owner;

        MethodCallCollector() {
            super(Opcodes.ASM9);
        }

        /**
         * 将 JVM 方法描述符转为可读形式
         * 例: "(IJ)Ljava/lang/String;" → "(IJ)"
         *
         * @param desc JVM 方法描述符,不可为 null
         * @return 参数部分的可读表示,异常输入返回 "(?)"
         */
        private static String descToDisplay(String desc) {
            if (desc == null || desc.length() < 3) {
                return "(?)";
            }
            int close = desc.indexOf(')');
            if (close <= 1) {
                return "(?)";
            }
            return "(" + desc.substring(1, close) + ")";
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
                    if (!owner.equals(ownerName)) {
                        return; // 仅类内部调用
                    }
                    if (nodeMap.size() >= MAX_METHOD_NODES) {
                        return;
                    }

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
    }
}
