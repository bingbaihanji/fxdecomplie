package com.bingbaihanji.fxdecomplie.ui.graph;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 方法级控制流图（CFG）分析器,基于 ASM Tree API 划分基本块并生成 DOT 格式
 *
 * <p>零 JavaFX 依赖,纯算法工具类</p>
 *
 * @author bingbaihanji
 * @date 2026-07-03
 */
public final class CfgAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(CfgAnalyzer.class);
    private static final int MAX_BLOCKS = 200;

    private CfgAnalyzer() {
        throw new AssertionError("utility class");
    }

    /**
     * 为指定方法生成控制流图 DOT 字符串
     *
     * @param classBytes 类字节码
     * @param methodName 方法名
     * @param methodDesc 方法描述符（如 "(II)V"）,可为 null（匹配第一个同名方法）
     * @return DOT 字符串,失败返回含错误信息的 DOT
     */
    public static String buildCfgDot(byte[] classBytes, String methodName, String methodDesc) {
        if (classBytes == null || classBytes.length == 0 || methodName == null) {
            return "digraph G { label=\"No data\" }";
        }
        try {
            ClassReader cr = new ClassReader(classBytes);
            ClassNode cn = new ClassNode();
            cr.accept(cn, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            MethodNode target = null;
            for (MethodNode mn : cn.methods) {
                if (mn.name.equals(methodName)) {
                    if (methodDesc == null || methodDesc.isEmpty()
                            || mn.desc.equals(methodDesc)) {
                        target = mn;
                        break;
                    }
                }
            }
            if (target == null) {
                return "digraph G { label=\"Method not found: " + escape(methodName) + "\" }";
            }
            if (target.instructions == null || target.instructions.size() == 0) {
                return "digraph G { label=\"Empty method: " + escape(methodName) + "\" }";
            }
            return buildDot(cn.name, target);
        } catch (Exception e) {
            log.debug("CFG分析失败: {}", methodName, e);
            return "digraph G { label=\"CFG error: " + escape(e.getMessage()) + "\" }";
        }
    }

    // ---- 基本块分析 + DOT 生成 ----

    /**
     * 对指定方法执行基本块划分并生成 DOT 格式的控制流图
     *
     * <p>算法步骤：</p>
     * <ol>
     *   <li>标记基本块起始位置（leaders）：方法入口、异常处理器入口、跳转目标、跳转后继</li>
     *   <li>根据 leaders 将指令序列划分为基本块</li>
     *   <li>分析基本块末尾指令确定控制流边（无条件跳转、条件跳转、switch、异常边）</li>
     *   <li>组装 DOT 字符串输出</li>
     * </ol>
     *
     * @param className 类内部名称（如 "com/example/Foo"）
     * @param mn        目标方法的 ASM MethodNode
     * @return DOT 格式的控制流图字符串
     */
    private static String buildDot(String className, MethodNode mn) {
        InsnList insns = mn.instructions;
        int size = insns.size();

        // 1. 标记基本块开始位置（leaders）
        BitSet leaders = new BitSet(size);
        leaders.set(0); // 方法入口
        // 标记异常处理器入口
        if (mn.tryCatchBlocks != null) {
            for (TryCatchBlockNode tcb : mn.tryCatchBlocks) {
                leaders.set(insns.indexOf(tcb.handler));
            }
        }
        // 标记跳转目标和跳转后的下一条指令
        for (int i = 0; i < size; i++) {
            AbstractInsnNode insn = insns.get(i);
            int opcode = insn.getOpcode();
            if (insn instanceof JumpInsnNode jmp) {
                leaders.set(insns.indexOf(jmp.label));
                if (opcode != Opcodes.GOTO) {
                    if (i + 1 < size) {
                        leaders.set(i + 1);
                    }
                }
            } else if (insn instanceof TableSwitchInsnNode ts) {
                leaders.set(insns.indexOf(ts.dflt));
                for (LabelNode lbl : ts.labels) {
                    leaders.set(insns.indexOf(lbl));
                }
                if (i + 1 < size) {
                    leaders.set(i + 1);
                }
            } else if (insn instanceof LookupSwitchInsnNode ls) {
                leaders.set(insns.indexOf(ls.dflt));
                for (LabelNode lbl : ls.labels) {
                    leaders.set(insns.indexOf(lbl));
                }
                if (i + 1 < size) {
                    leaders.set(i + 1);
                }
            } else if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN
                    || opcode == Opcodes.ATHROW) {
                if (i + 1 < size) {
                    leaders.set(i + 1);
                }
            }
        }

        // 2. 将指令划分到基本块
        List<BasicBlock> blocks = new ArrayList<>();
        Map<LabelNode, Integer> labelToBlock = new LinkedHashMap<>();
        int blockStart = 0;
        for (int i = 1; i <= size; i++) {
            if (i == size || leaders.get(i)) {
                List<String> lines = new ArrayList<>();
                for (int j = blockStart; j < i; j++) {
                    AbstractInsnNode insn = insns.get(j);
                    lines.add(insnText(insn, j));
                }
                BasicBlock bb = new BasicBlock(blocks.size(), blockStart, i - 1, lines);
                blocks.add(bb);
                // 记录标签→块映射
                for (int j = blockStart; j < i; j++) {
                    AbstractInsnNode insn = insns.get(j);
                    if (insn instanceof LabelNode ln) {
                        labelToBlock.put(ln, bb.id);
                    }
                }
                if (i < size) {
                    blockStart = i;
                }
            }
            if (blocks.size() >= MAX_BLOCKS) {
                break;
            }
        }

        // 3. 构建边
        record Edge(int from, int to, String label, String style) {
        }
        List<Edge> edges = new ArrayList<>();
        for (BasicBlock bb : blocks) {
            if (bb.endIdx < 0 || bb.endIdx >= size) {
                continue;
            }
            AbstractInsnNode last = insns.get(bb.endIdx);
            int opcode = last.getOpcode();
            // 无条件跳转
            if (last instanceof JumpInsnNode jmp && opcode == Opcodes.GOTO) {
                Integer to = labelToBlock.get(jmp.label);
                if (to != null) {
                    edges.add(new Edge(bb.id, to, "", "solid"));
                }
            } else if (last instanceof TableSwitchInsnNode ts) {
                Map<Integer, Integer> caseCounts = new LinkedHashMap<>();
                for (LabelNode lbl : ts.labels) {
                    Integer to = labelToBlock.get(lbl);
                    if (to != null) {
                        caseCounts.merge(to, 1, Integer::sum);
                    }
                }
                for (var entry : caseCounts.entrySet()) {
                    String lbl = entry.getValue() > 1 ? entry.getValue() + " cases" : "case";
                    edges.add(new Edge(bb.id, entry.getKey(), lbl, "dashed"));
                }
                Integer dfltTo = labelToBlock.get(ts.dflt);
                if (dfltTo != null) {
                    edges.add(new Edge(bb.id, dfltTo, "default", "dashed"));
                }
            } else if (last instanceof LookupSwitchInsnNode ls) {
                for (LabelNode lbl : ls.labels) {
                    Integer to = labelToBlock.get(lbl);
                    if (to != null) {
                        edges.add(new Edge(bb.id, to, "case", "dashed"));
                    }
                }
                Integer dfltTo = labelToBlock.get(ls.dflt);
                if (dfltTo != null) {
                    edges.add(new Edge(bb.id, dfltTo, "default", "dashed"));
                }
            } else if (last instanceof JumpInsnNode jmp) {
                // 条件跳转：true 分支
                Integer to = labelToBlock.get(jmp.label);
                if (to != null) {
                    edges.add(new Edge(bb.id, to, "true", "dashed"));
                }
                // false 分支：下一块
                if (bb.id + 1 < blocks.size()) {
                    edges.add(new Edge(bb.id, bb.id + 1, "false", "dashed"));
                }
            } else if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN
                    || opcode == Opcodes.ATHROW) {
                // 终结指令,无后继
            } else {
                // fall-through 到下一块
                if (bb.id + 1 < blocks.size()) {
                    edges.add(new Edge(bb.id, bb.id + 1, "", "solid"));
                }
            }
        }

        // 4. 异常边
        if (mn.tryCatchBlocks != null) {
            for (TryCatchBlockNode tcb : mn.tryCatchBlocks) {
                int handlerIdx = insns.indexOf(tcb.handler);
                for (BasicBlock bb : blocks) {
                    if (bb.startIdx <= insns.indexOf(tcb.start)
                            && bb.endIdx >= insns.indexOf(tcb.end)
                            || bb.startIdx <= insns.indexOf(tcb.end)
                            && bb.endIdx >= insns.indexOf(tcb.end)) {
                        Integer to = labelToBlock.get(tcb.handler);
                        if (to != null) {
                            String type = tcb.type != null
                                    ? tcb.type.substring(tcb.type.lastIndexOf('/') + 1) : "any";
                            edges.add(new Edge(bb.id, to, type, "dotted"));
                        }
                    }
                }
            }
        }

        // 5. 生成 DOT
        StringBuilder sb = new StringBuilder();
        sb.append("digraph \"").append(escape(className)).append(".")
                .append(escape(mn.name)).append("\" {\n");
        sb.append("  rankdir=TB;\n");
        sb.append("  node [shape=box, style=filled, fontname=\"Consolas\", "
                + "fontsize=10, fillcolor=\"#2d2d2d\", fontcolor=\"#d4d4d4\", "
                + "color=\"#569cd6\"];\n");
        sb.append("  edge [color=\"#808080\", fontcolor=\"#808080\", fontsize=9];\n");

        // 节点
        for (BasicBlock bb : blocks) {
            sb.append("  B").append(bb.id).append(" [label=\"B").append(bb.id);
            for (String line : bb.lines) {
                sb.append("\\l  ").append(escape(line));
            }
            sb.append("\\l\"];\n");
        }

        // 边
        for (Edge e : edges) {
            sb.append("  B").append(e.from).append(" -> B").append(e.to);
            if (!e.label.isEmpty()) {
                sb.append(" [label=\"").append(escape(e.label)).append("\"");
                if (!"solid".equals(e.style)) {
                    sb.append(", style=").append(e.style);
                }
                sb.append("]");
            } else if (!"solid".equals(e.style)) {
                sb.append(" [style=").append(e.style).append("]");
            }
            sb.append(";\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    /** 将单条 ASM 指令转换为可读文本,格式为 "序号: 操作码名" */
    private static String insnText(AbstractInsnNode insn, int idx) {
        int op = insn.getOpcode();
        if (op < 0) {
            return idx + ": " + insn.getClass().getSimpleName();
        }
        String name = org.objectweb.asm.util.Printer.OPCODES[op];
        return idx + ": " + name.toLowerCase();
    }

    /**
     * 转义 DOT 标签中的特殊字符（反斜杠、引号、换行、尖括号、花括号）
     *
     * @param s 原始字符串,可为 null
     * @return 转义后的字符串,null 返回空串
     */
    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("<", "\\<")
                .replace(">", "\\>").replace("{", "\\{").replace("}", "\\}");
    }

    // ---- 基本块数据 ----

    /**
     * 基本块数据结构
     * <p>一个基本块是一段连续指令序列,入口为第一条指令,出口为最后一条指令,
     * 内部不含跳转目标（除入口外）和跳转指令（除出口外）</p>
     */
    private static class BasicBlock {
        /** 基本块编号（从 0 开始） */
        final int id;
        /** 起始指令在 InsnList 中的索引 */
        final int startIdx;
        /** 结束指令在 InsnList 中的索引 */
        final int endIdx;
        /** 该基本块内所有指令的可读文本行 */
        final List<String> lines;

        BasicBlock(int id, int startIdx, int endIdx, List<String> lines) {
            this.id = id;
            this.startIdx = startIdx;
            this.endIdx = endIdx;
            this.lines = lines;
        }
    }
}
