package com.bingbaihanji.fxdecomplie.core.jadx.core.clsp;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.MethodInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IMethodDetails;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.DecodeException;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import com.bingbaihanji.fxdecomplie.util.JadxConsts;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * 类层次结构图，包含方法的附加信息
 * <p>
 * 用于管理类的继承关系、接口实现关系，并提供方法详情查询功能
 * 支持从类集合文件加载类路径信息，以及添加应用程序自身的类
 * </p>
 */
public class ClspGraph {
    private static final Logger LOG = LoggerFactory.getLogger(ClspGraph.class);
    private static final Set<String> OBJECT_SINGLE_SET = Collections.singleton(JadxConsts.CLASS_OBJECT);
    /** 根节点，包含整个反编译上下文的结构信息 */
    private final RootNode root;
    /** 引用到但未在类图中找到的缺失类集合 */
    private final Set<String> missingClasses = new HashSet<>();
    /** 类全限定名到类路径信息（{@link ClspClass}）的映射 */
    private Map<String, ClspClass> nameMap;
    /** 类名到其所有父类型（含祖先）集合的缓存 */
    private Map<String, Set<String>> superTypesCache;
    /** 类名到其所有实现类（子类型）列表的缓存 */
    private Map<String, List<String>> implementsCache;

    /**
     * 构造类层次结构图
     *
     * @param rootNode 根节点
     */
    public ClspGraph(RootNode rootNode) {
        this.root = rootNode;
    }

    /**
     * 从 .clst 文件加载类集合信息并添加到类路径中
     *
     * @throws IOException    如果读取文件时发生 I/O 错误
     * @throws DecodeException 如果解码文件内容时发生错误
     */
    public void loadClsSetFile() throws IOException, DecodeException {
        ClsSet set = new ClsSet(root);
        set.loadFromClstFile();
        addClasspath(set);
    }

    /**
     * 将类集合添加到类路径中如果类路径尚未初始化，则进行初始化 如果已加载，则抛出异常
     *
     * @param set 要添加的类集合
     * @throws JadxRuntimeException 如果类路径已经加载过
     */
    public void addClasspath(ClsSet set) {
        if (nameMap == null) {
            nameMap = new HashMap<>(set.getClassesCount());
            set.addToMap(nameMap);
        } else {
            throw new JadxRuntimeException("Classpath already loaded");
        }
    }

    /**
     * 将应用程序的类节点列表添加到类图中
     *
     * @param classes 要添加的类节点列表
     */
    public void addApp(List<ClassNode> classes) {
        if (nameMap == null) {
            nameMap = new HashMap<>(classes.size());
        }
        for (ClassNode cls : classes) {
            addClass(cls);
        }
    }

    /**
     * 初始化缓存，填充父类型缓存和接口实现缓存
     * 应在所有类添加完成后调用此方法
     */
    public void initCache() {
        fillSuperTypesCache();
        fillImplementsCache();
    }

    /**
     * 检查指定全限定名的类是否已知（已加载到类图中）
     *
     * @param fullName 类的全限定名
     * @return 如果类已知则返回 {@code true}，否则返回 {@code false}
     */
    public boolean isClsKnown(String fullName) {
        return nameMap.containsKey(fullName);
    }

    /**
     * 根据类型参数获取类的详细信息
     *
     * @param type 类型参数
     * @return 对应的 {@link ClspClass} 对象，如果未找到则返回 {@code null}
     */
    public ClspClass getClsDetails(ArgType type) {
        return nameMap.get(type.getObject());
    }

    /**
     * 获取方法的详细信息首先在当前类中查找，如果未找到则在父类中深度搜索
     *
     * @param methodInfo 方法信息
     * @return 方法的详细信息，如果未找到则返回 {@code null}
     */
    @Nullable
    public IMethodDetails getMethodDetails(MethodInfo methodInfo) {
        ClspClass cls = nameMap.get(methodInfo.getDeclClass().getRawName());
        if (cls == null) {
            return null;
        }
        ClspMethod clspMethod = getMethodFromClass(cls, methodInfo);
        if (clspMethod != null) {
            return clspMethod;
        }
        // 在父类中深度搜索
        for (ArgType parent : cls.getParents()) {
            ClspClass clspParent = getClspClass(parent);
            if (clspParent != null) {
                ClspMethod methodFromParent = getMethodFromClass(clspParent, methodInfo);
                if (methodFromParent != null) {
                    return methodFromParent;
                }
            }
        }
        // 未知方法，返回简单方法详情
        return new SimpleMethodDetails(methodInfo);
    }

    /**
     * 从指定类的方法映射中获取方法
     */
    private ClspMethod getMethodFromClass(ClspClass cls, MethodInfo methodInfo) {
        return cls.getMethodsMap().get(methodInfo.getShortId());
    }

    /**
     * 将类节点添加到名称映射中
     */
    private void addClass(ClassNode cls) {
        ArgType clsType = cls.getClassInfo().getType();
        String rawName = clsType.getObject();
        ClspClass clspClass = new ClspClass(clsType, -1, cls.getAccessFlags().rawValue(), ClspClassSource.APP);
        clspClass.setParents(ClsSet.makeParentsArray(cls));
        nameMap.put(rawName, clspClass);
    }

    /**
     * 判断类 {@code clsName} 是否为 {@code implClsName} 的实例（即是否继承或实现了后者）
     *
     * @param clsName     待判断的类名
     * @param implClsName 目标父类型 / 接口名
     * @return 如果 {@code clsName} 是 {@code implClsName} 的子类型则返回 {@code true}
     */
    public boolean isImplements(String clsName, String implClsName) {
        Set<String> anc = getSuperTypes(clsName);
        return anc.contains(implClsName);
    }

    /**
     * 获取实现（或继承）指定类型的所有类
     *
     * @param clsName 类型名
     * @return 所有子类型的类名列表，若无则返回空列表
     */
    public List<String> getImplementations(String clsName) {
        List<String> list = implementsCache.get(clsName);
        return list == null ? Collections.emptyList() : list;
    }

    /**
     * 填充接口实现缓存，建立“父类型 -&gt; 子类型列表”的映射
     */
    private void fillImplementsCache() {
        Map<String, List<String>> map = new HashMap<>(nameMap.size());
        List<String> classes = new ArrayList<>(nameMap.keySet());
        Collections.sort(classes);
        for (String cls : classes) {
            for (String st : getSuperTypes(cls)) {
                map.computeIfAbsent(st, v -> new ArrayList<>()).add(cls);
            }
        }
        implementsCache = map;
    }

    /**
     * 查找两个类的公共祖先类型
     *
     * @param clsName     第一个类名
     * @param implClsName 第二个类名
     * @return 公共祖先的类名 若 {@code implClsName} 未知则返回 {@code null}
     */
    public String getCommonAncestor(String clsName, String implClsName) {
        if (clsName.equals(implClsName)) {
            return clsName;
        }
        ClspClass cls = nameMap.get(implClsName);
        if (cls == null) {
            missingClasses.add(clsName);
            return null;
        }
        if (isImplements(clsName, implClsName)) {
            return implClsName;
        }
        Set<String> anc = getSuperTypes(clsName);
        return searchCommonParent(anc, cls);
    }

    /**
     * 在指定类的父类型中递归搜索，返回第一个存在于祖先集合中的类型
     *
     * @param anc 祖先类型集合
     * @param cls 待搜索其父类型的类
     * @return 命中的公共父类型名，未找到则返回 {@code null}
     */
    private String searchCommonParent(Set<String> anc, ClspClass cls) {
        for (ArgType p : cls.getParents()) {
            String name = p.getObject();
            if (anc.contains(name)) {
                return name;
            }
            ClspClass nCls = getClspClass(p);
            if (nCls != null) {
                String r = searchCommonParent(anc, nCls);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    /**
     * 获取指定类的所有父类型（包含直接与间接祖先、接口）
     *
     * @param clsName 类名
     * @return 父类型名集合，若无则返回空集合
     */
    public Set<String> getSuperTypes(String clsName) {
        Set<String> result = superTypesCache.get(clsName);
        return result == null ? Collections.emptySet() : result;
    }

    /**
     * 填充父类型缓存，为每个类计算其全部祖先类型集合
     * 针对空集合、单元素集合（尤其是 Object）做了内存优化
     */
    private void fillSuperTypesCache() {
        Map<String, Set<String>> map = new HashMap<>(nameMap.size());
        Set<String> tmpSet = new HashSet<>();
        for (Map.Entry<String, ClspClass> entry : nameMap.entrySet()) {
            ClspClass cls = entry.getValue();
            tmpSet.clear();
            addSuperTypes(cls, tmpSet);
            Set<String> result;
            int size = tmpSet.size();
            switch (size) {
                case 0: {
                    result = Collections.emptySet();
                    break;
                }
                case 1: {
                    String supCls = tmpSet.iterator().next();
                    if (supCls.equals(JadxConsts.CLASS_OBJECT)) {
                        result = OBJECT_SINGLE_SET;
                    } else {
                        result = Collections.singleton(supCls);
                    }
                    break;
                }
                default: {
                    result = new HashSet<>(tmpSet);
                    break;
                }
            }
            map.put(cls.getName(), result);
        }
        superTypesCache = map;
    }

    /**
     * 递归收集指定类的所有父类型，结果写入 {@code result} 集合
     *
     * @param cls    起始类
     * @param result 用于收集父类型名的集合
     */
    private void addSuperTypes(ClspClass cls, Set<String> result) {
        for (ArgType parentType : cls.getParents()) {
            if (parentType == null) {
                continue;
            }
            ClspClass parentCls = getClspClass(parentType);
            if (parentCls != null) {
                boolean isNew = result.add(parentCls.getName());
                if (isNew) {
                    addSuperTypes(parentCls, result);
                }
            } else {
                // 父类型未知
                result.add(parentType.getObject());
            }
        }
    }

    /**
     * 根据类型获取对应的类路径信息 若未找到则将其记录到缺失类集合中
     *
     * @param clsType 类型
     * @return 对应的 {@link ClspClass}，未找到则返回 {@code null}
     */
    @Nullable
    private ClspClass getClspClass(ArgType clsType) {
        ClspClass clspClass = nameMap.get(clsType.getObject());
        if (clspClass == null) {
            missingClasses.add(clsType.getObject());
        }
        return clspClass;
    }

    /**
     * 打印引用到但未找到的缺失类信息
     * 以 WARN 级别输出缺失数量，在 DEBUG 级别下逐个列出类名
     */
    public void printMissingClasses() {
        int count = missingClasses.size();
        if (count == 0) {
            return;
        }
        LOG.warn("Found {} references to unknown classes", count);
        if (LOG.isDebugEnabled()) {
            List<String> clsNames = new ArrayList<>(missingClasses);
            Collections.sort(clsNames);
            for (String cls : clsNames) {
                LOG.debug("  {}", cls);
            }
        }
    }
}
