package com.bingbaihanji.fxdecomplie.core.jadx.core.utils;

import com.bingbaihanji.fxdecomplie.core.jadx.api.IDecompileScheduler;
import com.bingbaihanji.fxdecomplie.core.jadx.api.JavaClass;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 反编译调度器，负责将待反编译的类分组成批次
 * <p>
 * 根据类之间的依赖关系进行排序和分组，以优化反编译顺序并减少线程间的锁竞争
 * 实现了 {@link IDecompileScheduler} 接口
 */
public class DecompilerScheduler implements IDecompileScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(DecompilerScheduler.class);

    private static final int MERGED_BATCH_SIZE = 16;
    private static final boolean DEBUG_BATCHES = false;

    private static List<DepInfo> sumDependencies(List<JavaClass> classes) {
        List<DepInfo> deps = new ArrayList<>(classes.size());
        for (JavaClass cls : classes) {
            int count = 0;
            for (JavaClass dep : cls.getDependencies()) {
                count += 1 + dep.getTotalDepsCount();
            }
            deps.add(new DepInfo(cls, count));
        }
        Collections.sort(deps);
        return deps;
    }

    private static List<List<JavaClass>> buildFallback(List<JavaClass> classes) {
        return classes.stream()
                .sorted(Comparator.comparingInt(c -> c.getClassNode().getTotalDepsCount()))
                .map(Collections::singletonList)
                .collect(Collectors.toList());
    }

    private static void check(List<List<JavaClass>> result, List<JavaClass> classes) {
        int classInBatches = result.stream().mapToInt(List::size).sum();
        if (classes.size() != classInBatches) {
            throw new JadxRuntimeException(
                    "Incorrect number of classes in result batch: " + classInBatches + ", expected: " + classes.size());
        }
    }

    /**
     * 构建反编译批次
     * <p>
     * 根据类之间的依赖关系将待反编译的类分组成多个批次构建失败（如栈溢出或其他异常）时，
     * 会回退到简单的按依赖数排序的单类批次方案
     *
     * @param classes 待反编译的类列表
     * @return 分组后的批次列表
     */
    @Override
    public List<List<JavaClass>> buildBatches(List<JavaClass> classes) {
        try {
            long start = System.currentTimeMillis();
            List<List<JavaClass>> result = internalBatches(classes);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Build decompilation batches in {}ms for {} classes",
                        System.currentTimeMillis() - start, classes.size());
            }
            if (DEBUG_BATCHES) {
                check(result, classes);
            }
            return result;
        } catch (StackOverflowError | BootstrapMethodError e) {
            LOG.warn("Stack overflow while building decompile batches, continue with fallback");
        } catch (Exception e) {
            LOG.warn("Build batches failed (continue with fallback)", e);
        }
        return buildFallback(classes);
    }

    /**
     * 将依赖较多的类排到最后
     * 为单个类的依赖构建批次，以避免其它线程对这些依赖类加锁而产生竞争
     *
     * @param classes 待反编译的类列表
     * @return 分组后的批次列表
     */
    public List<List<JavaClass>> internalBatches(List<JavaClass> classes) {
        List<DepInfo> deps = sumDependencies(classes);
        Set<JavaClass> added = new HashSet<>(classes.size());
        Comparator<JavaClass> cmpDepSize = Comparator.comparingInt(JavaClass::getTotalDepsCount);
        List<List<JavaClass>> result = new ArrayList<>();
        List<JavaClass> mergedBatch = new ArrayList<>(MERGED_BATCH_SIZE);
        for (DepInfo depInfo : deps) {
            JavaClass cls = depInfo.getCls();
            if (!added.add(cls)) {
                continue;
            }
            int depsSize = cls.getTotalDepsCount();
            if (depsSize == 0) {
                // 将无依赖的类加入合并批次
                mergedBatch.add(cls);
                if (mergedBatch.size() >= MERGED_BATCH_SIZE) {
                    result.add(mergedBatch);
                    mergedBatch = new ArrayList<>(MERGED_BATCH_SIZE);
                }
            } else {
                List<JavaClass> batch = new ArrayList<>();
                for (JavaClass dep : cls.getDependencies()) {
                    JavaClass topDep = dep.getTopParentClass();
                    if (!added.contains(topDep)) {
                        batch.add(topDep);
                        added.add(topDep);
                    }
                }
                batch.sort(cmpDepSize);
                batch.add(cls);
                result.add(Utils.lockList(batch));
            }
        }
        if (!mergedBatch.isEmpty()) {
            result.add(mergedBatch);
        }
        if (DEBUG_BATCHES) {
            dumpBatchesStats(classes, result, deps);
        }
        return result;
    }

    private void dumpBatchesStats(List<JavaClass> classes, List<List<JavaClass>> result, List<DepInfo> deps) {
        int clsInBatches = result.stream().mapToInt(List::size).sum();
        double avg = result.stream().mapToInt(List::size).average().orElse(-1);
        int maxSingleDeps = classes.stream().mapToInt(JavaClass::getTotalDepsCount).max().orElse(-1);
        int maxSubDeps = deps.stream().mapToInt(DepInfo::getDepsCount).max().orElse(-1);
        LOG.info("Batches stats:"
                + "\n input classes: " + classes.size()
                + ",\n classes in batches: " + clsInBatches
                + ",\n batches: " + result.size()
                + ",\n average batch size: " + String.format("%.2f", avg)
                + ",\n max single deps count: " + maxSingleDeps
                + ",\n max sub deps count: " + maxSubDeps);
    }

    /**
     * 依赖信息，记录某个类及其累计依赖数量，用于按依赖数排序
     */
    private static final class DepInfo implements Comparable<DepInfo> {
        private final JavaClass cls;
        private final int depsCount;

        private DepInfo(JavaClass cls, int depsCount) {
            this.cls = cls;
            this.depsCount = depsCount;
        }

        public JavaClass getCls() {
            return cls;
        }

        public int getDepsCount() {
            return depsCount;
        }

        @Override
        public int compareTo(@NotNull DecompilerScheduler.DepInfo o) {
            int deps = Integer.compare(depsCount, o.depsCount);
            if (deps == 0) {
                return cls.getClassNode().compareTo(o.cls.getClassNode());
            }
            return deps;
        }

        @Override
        public String toString() {
            return cls + ":" + depsCount;
        }
    }
}
