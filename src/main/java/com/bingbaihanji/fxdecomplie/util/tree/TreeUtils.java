package com.bingbaihanji.fxdecomplie.util.tree;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通用树形结构工具,提供列表→树、树→列表、叶子节点查找、祖先链追溯等常用操作
 *
 * <h3>使用示例</h3> <pre>{@code
 * public class DeptVO implements TreeUtils.TreeNode<Long, DeptVO> {
 *
 *     private Long id;
 *     private Long parentId;
 *     private List<DeptVO> children;
 *
 *     public Long getId() { return id; }
 *     public Long getParentId() { return parentId; }
 *     public List<DeptVO> getChildren() { return children; }
 *     public void setChildren(List<DeptVO> children) { this.children = children; }
 * }
 *
 * // 扁平列表 → 树
 * List<DeptVO> tree = TreeUtils.buildTree(list);
 *
 * // 树 → 扁平列表
 * List<DeptVO> flat = TreeUtils.flatten(tree.get(0));
 *
 * // 获取所有叶子节点
 * List<DeptVO> leaves = TreeUtils.getLeafNodes(tree.get(0));
 *
 * // 获取从根到某节点的路径
 * Map<Long, DeptVO> map = flat.stream().collect(Collectors.toMap(DeptVO::getId, d -> d));
 * List<DeptVO> chain = TreeUtils.getParentChain(targetId, map);
 * }</pre>
 *
 * @author 冰白寒祭
 * @date 2026-06-10
 */
public final class TreeUtils {

    private TreeUtils() {
        throw new AssertionError("工具类不允许实例化");
    }

    /**
     * 将扁平节点列表构建为树形结构
     *
     * <p>
     * 算法流程：
     * <ol>
     * <li>收集所有节点 ID,用于判断"父节点是否存在"</li>
     * <li>建立 ID → 节点 的映射表</li>
     * <li>遍历所有节点：若父 ID 为 null 或不在映射中 → 根节点  否则挂载到对应父节点下</li>
     * </ol>
     * @param nodes 扁平节点列表
     * @param <N> 节点类型
     * @param <T> ID 类型
     * @return 根节点列表(节点可能有多棵独立的树) 列表为空或 null 时返回空列表
     */
    public static <T, N extends TreeNode<T, N>> List<N> buildTree(List<N> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return Collections.emptyList();
        }
        // 收集所有有效 ID,用于判断父 ID 是否指向已存在的节点
        Set<T> allIds = nodes.stream().map(TreeNode::getId).filter(Objects::nonNull).collect(Collectors.toSet());

        // 建立 ID → 节点 映射(保持原始顺序)
        Map<T, N> nodeMap = nodes.stream()
                .filter(n -> n.getId() != null)
                .collect(Collectors.toMap(TreeNode::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));

        List<N> roots = new ArrayList<>();
        for (N node : nodes) {
            T pid = node.getParentId();
            // 无父节点 或 父节点不在当前数据中 → 视为根
            if (pid == null || !allIds.contains(pid)) {
                roots.add(node);
            } else {
                N parent = nodeMap.get(pid);
                if (parent != null) {
                    addChild(parent, node);
                }
            }
        }
        return roots;
    }

    /**
     * 将子节点挂载到父节点的 children 列表中
     */
    private static <T, N extends TreeNode<T, N>> void addChild(N parent, N child) {
        List<N> children = parent.getChildren();
        if (children == null) {
            children = new ArrayList<>();
            parent.setChildren(children);
        }
        children.add(child);
    }

    /**
     * 将树扁平化为列表(前序遍历)
     * @param root 树的根节点
     * @param <N> 节点类型
     * @param <T> ID 类型
     * @return 前序遍历后的扁平列表 root 为 null 时返回空列表
     */
    public static <T, N extends TreeNode<T, N>> List<N> flatten(N root) {
        List<N> result = new ArrayList<>();
        flattenDfs(root, result);
        return result;
    }

    /**
     * 前序遍历的递归实现
     */
    private static <T, N extends TreeNode<T, N>> void flattenDfs(N node, List<N> result) {
        if (node == null) {
            return;
        }
        result.add(node);
        List<N> children = node.getChildren();
        if (children != null) {
            for (N child : children) {
                flattenDfs(child, result);
            }
        }
    }

    /**
     * 获取树中所有叶子节点(没有子节点的节点)
     * @param root 树的根节点
     * @param <N> 节点类型
     * @param <T> ID 类型
     * @return 叶子节点列表
     */
    public static <T, N extends TreeNode<T, N>> List<N> getLeafNodes(N root) {
        return flatten(root).stream()
                .filter(n -> n.getChildren() == null || n.getChildren().isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 获取从根节点到指定节点的路径链(结果从根开始,到目标节点结束)
     *
     * <p>
     * 通过给定的 ID → 节点 映射表,从目标节点沿 {@code parentId} 逐级向上追溯至根, 最后反转得到从根到目标的顺序
     * </p>
     * @param id 目标节点 ID
     * @param map ID → 节点 的映射表(通常由 {@link #flatten} + {@code Collectors.toMap} 生成)
     * @param <N> 节点类型
     * @param <T> ID 类型
     * @return 从根到目标节点的路径链 找不到目标节点时返回空列表
     */
    public static <T, N extends TreeNode<T, N>> List<N> getParentChain(T id, Map<T, N> map) {
        List<N> chain = new ArrayList<>();
        N current = map.get(id);
        // 从目标节点向上追溯至根
        while (current != null) {
            chain.add(current);
            T pid = current.getParentId();
            current = pid != null ? map.get(pid) : null;
        }
        // 反转得到根→目标的正向顺序
        Collections.reverse(chain);
        return chain;
    }

    /**
     * 在树中查找指定 ID 的节点(深度优先搜索)
     * @param root 树的根节点
     * @param id 目标节点 ID,不能为 null
     * @param <N> 节点类型
     * @param <T> ID 类型
     * @return 匹配的节点,未找到返回 null
     */
    public static <T, N extends TreeNode<T, N>> N findNode(N root, T id) {
        Objects.requireNonNull(id, "id 不能为 null");
        if (root == null) {
            return null;
        }
        return findNodeDfs(root, id);
    }

    /**
     * 深度优先递归查找
     */
    private static <T, N extends TreeNode<T, N>> N findNodeDfs(N node, T id) {
        if (id.equals(node.getId())) {
            return node;
        }
        List<N> children = node.getChildren();
        if (children != null) {
            for (N child : children) {
                N found = findNodeDfs(child, id);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * 获取树中某节点的所有后代节点
     * @param root 树的根节点
     * @param id 目标节点 ID,不能为 null
     * @param <N> 节点类型
     * @param <T> ID 类型
     * @return 所有后代节点列表(前序遍历),节点不存在或无后代时返回空列表
     */
    public static <T, N extends TreeNode<T, N>> List<N> getAllDescendants(N root, T id) {
        Objects.requireNonNull(id, "id 不能为 null");
        N node = findNode(root, id);
        if (node == null) {
            return Collections.emptyList();
        }
        List<N> result = new ArrayList<>();
        List<N> children = node.getChildren();
        if (children != null) {
            for (N child : children) {
                flattenDfs(child, result);
            }
        }
        return result;
    }

    /**
     * 对树的每一层子树按指定比较器递归排序
     * @param root 树的根节点
     * @param comparator 排序比较器,不能为 null
     * @param <N> 节点类型
     * @param <T> ID 类型
     */
    public static <T, N extends TreeNode<T, N>> void sort(N root, Comparator<N> comparator) {
        Objects.requireNonNull(comparator, "comparator 不能为 null");
        if (root == null) {
            return;
        }
        sortRecursive(root, comparator);
    }

    /**
     * 递归排序子树
     */
    private static <T, N extends TreeNode<T, N>> void sortRecursive(N node, Comparator<N> comparator) {
        List<N> children = node.getChildren();
        if (children != null && !children.isEmpty()) {
            children.sort(comparator);
            for (N child : children) {
                sortRecursive(child, comparator);
            }
        }
    }

    /**
     * 树节点接口,实体实现此接口即可参与树构建
     *
     * <p>
     * 符合 CRTP(Curiously Recurring Template Pattern)设计： 子类将自己作为类型参数传入,避免调用方手动强转
     * </p>
     *
     * @param <T> 节点 ID 的类型(如 Long、Integer、String)
     * @param <N> 节点自身类型(实现类的类型)
     */
    public interface TreeNode<T, N extends TreeNode<T, N>> {

        /** 节点唯一标识 */
        T getId();

        /** 父节点 ID,根节点返回 {@code null} */
        T getParentId();

        /** 子节点列表 */
        List<N> getChildren();

        /** 设置子节点列表 */
        void setChildren(List<N> children);

    }

}
