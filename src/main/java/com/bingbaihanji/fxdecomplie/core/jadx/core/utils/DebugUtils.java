package com.bingbaihanji.fxdecomplie.core.jadx.core.utils;

import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.api.ICodeWriter;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.api.impl.SimpleCodeWriter;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.ConditionGen;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.InsnGen;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.MethodGen;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.IAttributeNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.MethodOverrideAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.*;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.regions.Region;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.regions.conditions.IfCondition;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.regions.loops.LoopRegion;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.AbstractVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.DotGraphVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.IDexTreeVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions.DepthRegionTraversal;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions.TracedRegionVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.CodegenException;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Use these methods only for debug purpose.
 * CheckStyle will reject usage of this class.
 */
public class DebugUtils {
    public static final Predicate<MethodNode> TEST_MTH_FILTER = mth -> "test".equals(mth.getName());
    private static final Logger LOG = LoggerFactory.getLogger(DebugUtils.class);
    private static Map<String, Long> execTimes;

    private DebugUtils() {
    }

    public static void dump(MethodNode mth) {
        dump(mth, "dump");
    }

    public static void dumpRaw(MethodNode mth, String desc, Predicate<MethodNode> dumpCondition) {
        if (dumpCondition.test(mth)) {
            dumpRaw(mth, desc);
        }
    }

    public static void dumpRawTest(MethodNode mth, String desc) {
        dumpRaw(mth, desc, TEST_MTH_FILTER);
    }

    public static void dumpRaw(MethodNode mth, String desc) {
        File out = new File("test-graph-" + desc + "-tmp");
        DotGraphVisitor.dumpRaw().save(out, mth);
    }

    public static IDexTreeVisitor dumpRawVisitor(String desc) {
        return new AbstractVisitor() {
            @Override
            public void visit(MethodNode mth) throws JadxException {
                dumpRaw(mth, desc);
            }
        };
    }

    public static IDexTreeVisitor dumpRawVisitor(String desc, Predicate<MethodNode> filter) {
        return new AbstractVisitor() {
            @Override
            public void visit(MethodNode mth) {
                if (filter.test(mth)) {
                    dumpRaw(mth, desc);
                }
            }
        };
    }

    public static IDexTreeVisitor dumpRawTestVisitor(String desc) {
        return dumpRawVisitor(desc, TEST_MTH_FILTER);
    }

    public static void dump(MethodNode mth, String desc) {
        File out = new File("test-graph-" + desc + "-tmp");
        DotGraphVisitor.dump().save(out, mth);
        DotGraphVisitor.dumpRaw().save(out, mth);
        DotGraphVisitor.dumpRegions().save(out, mth);
    }

    public static void printRegionsWithBlock(MethodNode mth, BlockNode block) {
        Set<IRegion> regions = new LinkedHashSet<>();
        DepthRegionTraversal.traverse(mth, new TracedRegionVisitor() {
            @Override
            public void processBlockTraced(MethodNode mth, IBlock container, IRegion currentRegion) {
                if (block.equals(container)) {
                    regions.add(currentRegion);
                }
            }
        });
        LOG.debug(" Found block: {} in regions: {}", block, regions);
    }

    public static IDexTreeVisitor printRegionsVisitor() {
        return new AbstractVisitor() {
            @Override
            public void visit(MethodNode mth) throws JadxException {
                printRegions(mth, true);
            }
        };
    }

    public static void printRegions(MethodNode mth) {
        printRegions(mth, false);
    }

    public static void printRegions(MethodNode mth, boolean printInsns) {
        Region mthRegion = mth.getRegion();
        if (mthRegion == null) {
            return;
        }
        printRegion(mth, mthRegion, printInsns);
    }

    public static void printRegion(MethodNode mth, IRegion region, boolean printInsns) {
        ICodeWriter cw = new SimpleCodeWriter();
        cw.startLine('|').add(mth.toString());
        printRegion(mth, region, cw, "|  ", printInsns);
        LOG.debug("{}{}", '\n', cw.finish().getCodeStr());
    }

    private static void printRegion(MethodNode mth, IRegion region, ICodeWriter cw, String indent, boolean printInsns) {
        printWithAttributes(cw, indent, region.toString(), region);
        indent += "|  ";
        printRegionSpecificInfo(cw, indent, mth, region, printInsns);
        for (IContainer container : region.getSubBlocks()) {
            if (container instanceof IRegion) {
                printRegion(mth, (IRegion) container, cw, indent, printInsns);
            } else {
                printWithAttributes(cw, indent, container.toString(), container);
                if (printInsns && container instanceof IBlock) {
                    IBlock block = (IBlock) container;
                    printInsns(mth, cw, indent, block);
                }
            }
        }
    }

    private static void printRegionSpecificInfo(ICodeWriter cw, String indent,
                                                MethodNode mth, IRegion region, boolean printInsns) {
        if (region instanceof LoopRegion) {
            LoopRegion loop = (LoopRegion) region;
            IfCondition condition = loop.getCondition();
            if (printInsns && condition != null) {
                ConditionGen conditionGen = new ConditionGen(new InsnGen(MethodGen.getFallbackMethodGen(mth), true));
                cw.startLine(indent).add("|> ");
                try {
                    conditionGen.add(cw, condition);
                } catch (Exception e) {
                    cw.startLine(indent).add(">!! ").add(condition.toString());
                }
            }
        }
    }

    private static void printInsns(MethodNode mth, ICodeWriter cw, String indent, IBlock block) {
        for (InsnNode insn : block.getInstructions()) {
            try {
                MethodGen mg = MethodGen.getFallbackMethodGen(mth);
                InsnGen ig = new InsnGen(mg, true);
                ICodeWriter code = new SimpleCodeWriter();
                ig.makeInsn(insn, code);
                String codeStr = code.getCodeStr();

                List<String> insnStrings = Stream.of(codeStr.split("\\R"))
                        .filter(CodegenEscapeUtils::notBlank)
                        .map(s -> "|> " + s)
                        .collect(Collectors.toList());
                Iterator<String> it = insnStrings.iterator();
                while (true) {
                    String insnStr = it.next();
                    if (it.hasNext()) {
                        cw.startLine(indent).add(insnStr);
                    } else {
                        printWithAttributes(cw, indent, insnStr, insn);
                        break;
                    }
                }
            } catch (CodegenException e) {
                cw.startLine(indent).add(">!! ").add(insn.toString());
            }
        }
    }

    private static void printWithAttributes(ICodeWriter cw, String indent, String codeStr, IAttributeNode attrNode) {
        String str = attrNode.isAttrStorageEmpty() ? codeStr : codeStr + ' ' + attrNode.getAttributesString();
        List<String> attrStrings = Stream.of(str.split("\\R"))
                .filter(CodegenEscapeUtils::notBlank)
                .collect(Collectors.toList());
        Iterator<String> it = attrStrings.iterator();
        if (!it.hasNext()) {
            return;
        }
        cw.startLine(indent).add(it.next());
        while (it.hasNext()) {
            cw.startLine(indent).add("|+  ").add(it.next());
        }
    }

    public static void printMap(Map<?, ?> map, String desc) {
        LOG.debug("Map {} (size = {}):", desc, map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            LOG.debug("  {}: {}", entry.getKey(), entry.getValue());
        }
    }

    public static void printStackTrace(String label) {
        LOG.debug("StackTrace: {}\n{}", label, Utils.getFullStackTrace(new Exception()));
    }

    public static void printMethodOverrideTop(RootNode root) {
        LOG.debug("Methods override top 10:");
        root.getClasses().stream()
                .flatMap(c -> c.getMethods().stream())
                .filter(m -> m.contains(AType.METHOD_OVERRIDE))
                .map(m -> m.get(AType.METHOD_OVERRIDE))
                .filter(o -> !o.getOverrideList().isEmpty())
                .filter(distinctByKey(methodOverrideAttr -> methodOverrideAttr.getRelatedMthNodes().size()))
                .filter(distinctByKey(MethodOverrideAttr::getRelatedMthNodes))
                .sorted(Comparator.comparingInt(o -> -o.getRelatedMthNodes().size()))
                .limit(10)
                .forEach(o -> LOG.debug("  {} : {}", o.getRelatedMthNodes().size(), Utils.last(o.getOverrideList())));
    }

    private static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    public static void initExecTimes() {
        execTimes = new ConcurrentHashMap<>();
    }

    public static void mergeExecTimeFromStart(String tag, long startTimeMillis) {
        mergeExecTime(tag, System.currentTimeMillis() - startTimeMillis);
    }

    public static void mergeExecTime(String tag, long execTimeMillis) {
        execTimes.merge(tag, execTimeMillis, Long::sum);
    }

    public static void printExecTimes() {
        System.out.println("Exec times:");
        execTimes.forEach((tag, time) -> System.out.println(" " + tag + ": " + time + "ms"));
    }

    public static void printExecTimesWithTotal(long totalMillis) {
        System.out.println("Exec times: total " + totalMillis + "ms");
        execTimes.forEach((tag, time) -> System.out.println(" " + tag + ": " + time + "ms"
                + String.format(" (%.2f%%)", time * 100. / (double) totalMillis)));
    }
}
