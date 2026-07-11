package com.bingbaihanji.fxdecomplie.core.jadx.core.plugins.versions;

/**
 * 版本号比较器，用于对 jadx 插件版本号进行标准化后比较大小。
 * <p>
 * 支持的版本号格式包括：{@code jadx-gui-1.2.3}、{@code jadx-1.2.3}、
 * {@code v1.2.3}、{@code r10}（发行号）等，统一清洗为纯数字点分格式后进行比较。
 * </p>
 */
public class VersionComparator {

	private VersionComparator() {
	}

	/**
	 * 检查并比较两个版本号字符串。
	 * 先对输入字符串做标准化清洗，再逐段比较数值大小。
	 *
	 * @param str1 第一个版本号字符串
	 * @param str2 第二个版本号字符串
	 * @return 比较结果：负值表示 str1 小于 str2，零表示相等，正值表示 str1 大于 str2
	 */
	public static int checkAndCompare(String str1, String str2) {
		return compare(clean(str1), clean(str2));
	}

	/**
	 * 清洗版本号字符串，去除常见前缀（如 {@code jadx-gui-}、{@code jadx-}、
	 * {@code v}、发行号 {@code r}）并转换为统一的点分数字格式。
	 *
	 * @param str 原始版本号字符串
	 * @return 清洗后的版本号字符串，纯数字以 {@code .} 分隔
	 */
	private static String clean(String str) {
		if (str == null || str.isEmpty()) {
			return "";
		}
		String result = str.trim().toLowerCase();
		if (result.startsWith("jadx-gui-")) {
			result = result.substring(9);
		}
		if (result.startsWith("jadx-")) {
			result = result.substring(5);
		}
		if (result.charAt(0) == 'v') {
			result = result.substring(1);
		}
		if (result.charAt(0) == 'r') {
			result = result.substring(1);
			int dot = result.indexOf('.');
			if (dot != -1) {
				result = result.substring(0, dot);
			}
		}
		// 将包版本视为版本号的一部分
		result = result.replace('-', '.');
		return result;
	}

	/**
	 * 比较两个清洗后的点分版本号字符串。
	 * 按 {@code .} 拆分后逐段比较，跳过相等的前缀段，找到第一个不同的段时比较其整数值。
	 * 若一段是另一段的零后缀（如 {@code 1.2.0} 与 {@code 1.2}），视为相等。
	 *
	 * @param str1 清洗后的第一个版本号
	 * @param str2 清洗后的第二个版本号
	 * @return 比较结果：负值表示 str1 小于 str2，零表示相等，正值表示 str1 大于 str2
	 */
	private static int compare(String str1, String str2) {
		String[] s1 = str1.split("\\.");
		int l1 = s1.length;
		String[] s2 = str2.split("\\.");
		int l2 = s2.length;

		int i = 0;
		// 跳过相等的部分
		while (i < l1 && i < l2) {
			if (!s1[i].equals(s2[i])) {
				break;
			}
			i++;
		}
		// 比较第一个不相等的数字部分
		if (i < l1 && i < l2) {
			return Integer.valueOf(s1[i]).compareTo(Integer.valueOf(s2[i]));
		}
		boolean checkFirst = l1 > l2;
		boolean zeroTail = isZeroTail(checkFirst ? s1 : s2, i);
		if (zeroTail) {
			return 0;
		}
		return checkFirst ? 1 : -1;
	}

	/**
	 * 检查从指定位置开始，剩余的所有段是否全为零。
	 * 用于判断较长的版本号是否仅为带有零后缀的短版本号（如 {@code 1.2.0.0} 等价于 {@code 1.2}）。
	 *
	 * @param arr 版本号分段数组
	 * @param pos 开始检查的位置
	 * @return 如果从 pos 开始的所有段值均为 0，返回 {@code true}；否则返回 {@code false}
	 */
	private static boolean isZeroTail(String[] arr, int pos) {
		for (int i = pos; i < arr.length; i++) {
			if (Integer.parseInt(arr[i]) != 0) {
				return false;
			}
		}
		return true;
	}
}
