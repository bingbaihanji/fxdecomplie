package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes;

/**
 * 属性标志枚举。
 * 用于标记节点（方法、基本块、指令等）的二进制状态标志（设置或未设置）。
 * 每个标志以 {@link java.util.EnumSet} 形式存储在 {@link AttributeStorage} 中，
 * 使用一个 long 值即可容纳所有标志（最多 64 个）。
 */
public enum AFlag {
	/** 方法入口基本块 */
	MTH_ENTER_BLOCK,
	/** 方法出口基本块 */
	MTH_EXIT_BLOCK,

	/** try 块入口 */
	TRY_ENTER,
	/** try 块出口 */
	TRY_LEAVE,

	/** 循环开始 */
	LOOP_START,
	/** 循环结束 */
	LOOP_END,

	/** 编译器合成/系统生成 */
	SYNTHETIC,

	RETURN, // 该块仅包含 return 指令
	/** 原始 return 指令 */
	ORIG_RETURN,

	/** 不要包裹此块 */
	DONT_WRAP,
	/** 不要内联此块 */
	DONT_INLINE,
	/** 不要内联常量 */
	DONT_INLINE_CONST,
	DONT_INVERT, // 不要反转此 if 语句
	DONT_GENERATE, // 正常处理，但不输出到生成的代码中
	COMMENT_OUT, // 正常处理，但在生成代码中注释掉该指令
	REMOVE, // 可被完全移除
	REMOVE_SUPER_CLASS, // 不要添加父类

	HIDDEN, // 该指令在其他指令内部使用，但未列在参数列表中
	CONVERTED_ENUM, // 枚举类已成功还原为原始形式

	DONT_RENAME, // 反混淆时不要重命名
	FORCE_RAW_NAME, // 强制使用原始名称而非别名

	/** 已添加到区域 */
	ADDED_TO_REGION,
	/** 重复标记 */
	DUPLICATED,

	// 此循环条件已被合并，或者不应对其施加单指令限制
	ALLOW_MULTIPLE_INSNS_LOOP_COND,

	/** 异常顶部分割节点 */
	EXC_TOP_SPLITTER,
	/** 异常底部分割节点 */
	EXC_BOTTOM_SPLITTER,
	/** finally 块中的指令 */
	FINALLY_INSNS,
	/** 忽略 throw 分割 */
	IGNORE_THROW_SPLIT,

	/** 跳过第一个参数 */
	SKIP_FIRST_ARG,
	SKIP_ARG, // 在调用指令中跳过该参数
	/** 不跳过任何参数 */
	NO_SKIP_ARGS,

	/** 匿名构造函数 */
	ANONYMOUS_CONSTRUCTOR,
	/** 内联实例字段 */
	INLINE_INSTANCE_FIELD,

	/** this 引用 */
	THIS,
	/** super 引用 */
	SUPER,

	/** 包信息类 */
	PACKAGE_INFO,

	/**
	 * 标记 Android 资源类
	 */
	ANDROID_R_CLASS,

	/**
	 * RegisterArg 属性，用于标记方法参数
	 */
	METHOD_ARGUMENT,

	/**
	 * RegisterArg 或 SSAVar 的类型不可更改
	 */
	IMMUTABLE_TYPE,

	/**
	 * 强制使用内联赋值方式内联该指令
	 */
	FORCE_ASSIGN_INLINE,

	CUSTOM_DECLARE, // 此寄存器的变量不需要声明
	/** 声明变量 */
	DECLARE_VAR,

	/** else-if 链 */
	ELSE_IF_CHAIN,

	/** 已包裹 */
	WRAPPED,
	/** 算术单操作数 */
	ARITH_ONEARG,

	/** 贯穿（穿透到下一个 case） */
	FALL_THROUGH,

	/** 可变参数调用 */
	VARARG_CALL,

	/**
	 * 使用显式类型常量：如 '(byte) 1' 或带类型后缀 '7L'
	 */
	EXPLICIT_PRIMITIVE_TYPE,
	/** 显式类型转换 */
	EXPLICIT_CAST,
	SOFT_CAST, // 用于辅助类型推断的合成转换（允许泛型未检查转换）

	INCONSISTENT_CODE, // 反编译不一致警告

	REQUEST_IF_REGION_OPTIMIZE, // 再次运行 if 区域访问器进行优化
	/** 请求代码缩减 */
	REQUEST_CODE_SHRINK,

	/** 候选内联方法 */
	METHOD_CANDIDATE_FOR_INLINE,
	USE_LINES_HINTS, // 方法中的源代码行号信息可信

	/** 禁用块锁定 */
	DISABLE_BLOCKS_LOCK,

	// 类处理标志
	RESTART_CODEGEN, // 必须重新执行代码生成
	RELOAD_AT_CODEGEN_STAGE, // 类在 'process' 阶段无法分析，需在 'codegen' 阶段前卸载
	CLASS_DEEP_RELOAD, // 在 process 阶段前执行深度类卸载（重载）
	CLASS_UNLOADED, // 类已被完全卸载

	DONT_UNLOAD_CLASS, // 代码生成后不要卸载类（仅用于测试和调试！）

	/** 解析 Java JSR 指令 */
	RESOLVE_JAVA_JSR,
	/** 计算后支配关系 */
	COMPUTE_POST_DOM,
}
