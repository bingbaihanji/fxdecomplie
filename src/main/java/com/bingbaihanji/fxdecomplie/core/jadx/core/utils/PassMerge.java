package com.bingbaihanji.fxdecomplie.core.jadx.core.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass.JadxPass;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass.JadxPassInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.IDexTreeVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;

/**
 * 自定义 Pass 合并器。
 * <p>
 * 负责将用户自定义的 {@link JadxPass} 按其声明的顺序依赖（runAfter / runBefore）
 * 合并到已有的访问者（{@link IDexTreeVisitor}）列表中的正确位置。
 */
public class PassMerge {

	/** 目标访问者列表，合并后的自定义 Pass 将插入到此列表中 */
	private final List<IDexTreeVisitor> visitors;

	/** 本次待合并的所有自定义 Pass 名称集合 */
	private Set<String> mergePassesNames;
	/** 访问者到其名称的映射表 */
	private Map<IDexTreeVisitor, String> namesMap;

	/**
	 * 构造 Pass 合并器。
	 *
	 * @param visitors 目标访问者列表
	 */
	public PassMerge(List<IDexTreeVisitor> visitors) {
		this.visitors = visitors;
	}

	/**
	 * 将自定义 Pass 合并到访问者列表中。
	 *
	 * @param customPasses 待合并的自定义 Pass 列表
	 * @param wrap         将 {@link JadxPass} 包装为 {@link IDexTreeVisitor} 的转换函数
	 */
	public void merge(List<JadxPass> customPasses, Function<JadxPass, IDexTreeVisitor> wrap) {
		if (Utils.isEmpty(customPasses)) {
			return;
		}
		List<MergePass> mergePasses = ListUtils.map(customPasses, p -> new MergePass(p, wrap.apply(p), p.getInfo()));
		linkDeps(mergePasses);
		mergePasses.sort(new ExtDepsComparator(visitors).thenComparing(InvertedDepsComparator.INSTANCE));

		namesMap = new IdentityHashMap<>();
		visitors.forEach(p -> namesMap.put(p, p.getName()));
		mergePasses.forEach(p -> namesMap.put(p.getVisitor(), p.getName()));

		mergePassesNames = mergePasses.stream().map(MergePass::getName).collect(Collectors.toSet());

		for (MergePass mergePass : mergePasses) {
			int pos = searchInsertPos(mergePass);
			if (pos == -1) {
				visitors.add(mergePass.getVisitor());
			} else {
				visitors.add(pos, mergePass.getVisitor());
			}
		}
	}

	/**
	 * 计算指定 Pass 在访问者列表中的插入位置。
	 *
	 * @param pass 待插入的 Pass
	 * @return 插入位置索引；返回 -1 表示追加到末尾
	 */
	private int searchInsertPos(MergePass pass) {
		List<String> runAfter = pass.after();
		List<String> runBefore = pass.before();
		if (runAfter.isEmpty() && runBefore.isEmpty()) {
			return -1; // 追加到末尾
		}
		if (ListUtils.isSingleElement(runAfter, JadxPassInfo.START)) {
			return 0;
		}
		if (ListUtils.isSingleElement(runBefore, JadxPassInfo.END)) {
			return -1;
		}
		int visitorsCount = visitors.size();
		Map<String, Integer> namePosMap = new HashMap<>(visitorsCount);
		for (int i = 0; i < visitorsCount; i++) {
			namePosMap.put(namesMap.get(visitors.get(i)), i);
		}
		int after = -1;
		for (String name : runAfter) {
			Integer pos = namePosMap.get(name);
			if (pos != null) {
				after = Math.max(after, pos);
			} else {
				if (mergePassesNames.contains(name)) {
					// 忽略已知的 Pass
					continue;
				}
				throw new JadxRuntimeException("Ordering pass not found: " + name
						+ ", listed in 'runAfter' of pass: " + pass
						+ "\n all passes: " + ListUtils.map(visitors, namesMap::get));
			}
		}
		int before = Integer.MAX_VALUE;
		for (String name : runBefore) {
			Integer pos = namePosMap.get(name);
			if (pos != null) {
				before = Math.min(before, pos);
			} else {
				if (mergePassesNames.contains(name)) {
					// 忽略已知的 Pass
					continue;
				}
				throw new JadxRuntimeException("Ordering pass not found: " + name
						+ ", listed in 'runBefore' of pass: " + pass
						+ "\n all passes: " + ListUtils.map(visitors, namesMap::get));
			}
		}
		if (before <= after) {
			throw new JadxRuntimeException("Conflict order requirements for pass: " + pass
					+ "\n run after: " + runAfter
					+ "\n run before: " + runBefore
					+ "\n passes: " + ListUtils.map(visitors, namesMap::get));
		}
		if (after == -1) {
			if (before == Integer.MAX_VALUE) {
				// 未指定顺序，追加到末尾
				return -1;
			}
			return before;
		}
		int pos = after + 1;
		return pos >= visitorsCount ? -1 : pos;
	}

	/**
	 * 合并过程中使用的 Pass 包装对象，保存原始 Pass、对应的访问者以及可修改的依赖列表。
	 */
	private static final class MergePass {
		private final JadxPass pass;
		private final IDexTreeVisitor visitor;
		private final JadxPassInfo info;
		// 复制依赖列表以便后续修改
		private final List<String> before;
		private final List<String> after;

		private MergePass(JadxPass pass, IDexTreeVisitor visitor, JadxPassInfo info) {
			this.pass = pass;
			this.visitor = visitor;
			this.info = info;
			this.before = new ArrayList<>(info.runBefore());
			this.after = new ArrayList<>(info.runAfter());
		}

		public JadxPass getPass() {
			return pass;
		}

		public IDexTreeVisitor getVisitor() {
			return visitor;
		}

		public String getName() {
			return info.getName();
		}

		public JadxPassInfo getInfo() {
			return info;
		}

		public List<String> before() {
			return before;
		}

		public List<String> after() {
			return after;
		}

		@Override
		public String toString() {
			return info.getName();
		}
	}

	/**
	 * 将依赖关系构建为双向链接。
	 * <p>
	 * 即若 A 声明 runAfter B，则同时为 B 补充 runBefore A，反之亦然，
	 * 以便后续排序时能够从两个方向感知依赖。
	 */
	private static void linkDeps(List<MergePass> mergePasses) {
		Map<String, MergePass> map = mergePasses.stream().collect(Collectors.toMap(MergePass::getName, p -> p));
		for (MergePass pass : mergePasses) {
			for (String after : pass.getInfo().runAfter()) {
				MergePass beforePass = map.get(after);
				if (beforePass != null) {
					beforePass.before().add(pass.getName());
				}
			}
			for (String before : pass.getInfo().runBefore()) {
				MergePass afterPass = map.get(before);
				if (afterPass != null) {
					afterPass.after().add(pass.getName());
				}
			}
		}
	}

	/**
	 * 将与访问者存在依赖关系的 Pass 排在其他 Pass 之前。
	 */
	private static class ExtDepsComparator implements Comparator<MergePass> {
		private final Set<String> names;

		public ExtDepsComparator(List<IDexTreeVisitor> visitors) {
			this.names = visitors.stream()
					.map(IDexTreeVisitor::getName)
					.collect(Collectors.toSet());
		}

		@Override
		public int compare(MergePass first, MergePass second) {
			boolean isFirst = containsVisitor(first.before()) || containsVisitor(first.after());
			boolean isSecond = containsVisitor(second.before()) || containsVisitor(second.after());
			return -Boolean.compare(isFirst, isSecond);
		}

		private boolean containsVisitor(List<String> deps) {
			for (String dep : deps) {
				if (names.contains(dep)) {
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * 按反向依赖排序，即：若某个 Pass 依赖另一个 Pass，则将其排在被依赖者之前。
	 */
	private static class InvertedDepsComparator implements Comparator<MergePass> {
		public static final InvertedDepsComparator INSTANCE = new InvertedDepsComparator();

		@Override
		public int compare(MergePass first, MergePass second) {
			if (first.before().contains(second.getName())
					|| first.after().contains(second.getName())) {
				return 1;
			}
			if (second.before().contains(first.getName())
					|| second.after().contains(first.getName())) {
				return -1;
			}
			return 0;
		}
	}
}
