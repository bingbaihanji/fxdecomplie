package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.rename;

import com.bingbaihanji.fxdecomplie.core.jadx.api.data.ICodeData;
import com.bingbaihanji.fxdecomplie.core.jadx.api.data.ICodeRename;
import com.bingbaihanji.fxdecomplie.core.jadx.api.data.IJavaCodeRef;
import com.bingbaihanji.fxdecomplie.core.jadx.api.data.IJavaNodeRef;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.RegisterArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.SSAVar;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.AbstractVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.InitCodeVariables;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.JadxVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.debuginfo.DebugInfoApplyVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 代码重命名访问器，负责将外部传入的重命名数据应用到方法中的变量和参数上
 * <p>
 * 该访问器在变量初始化和调试信息应用之后执行，通过 {@link ICodeRename} 数据将
 * 变量、方法参数等实体重命名为指定的新名称
 * </p>
 */
@JadxVisitor(
        name = "ApplyCodeRename",
        desc = "重命名方法中的变量和其他实体",
        runAfter = {
                InitCodeVariables.class,
                DebugInfoApplyVisitor.class
        }
)
public class CodeRenameVisitor extends AbstractVisitor {

    private static final Logger LOG = LoggerFactory.getLogger(CodeRenameVisitor.class);

    /**
     * 以类全限定名为键、该类的所有重命名列表为值的映射表
     */
    private Map<String, List<ICodeRename>> clsRenamesMap;

    /**
     * 为指定类逐个应用重命名规则
     * <p>
     * 当前仅支持方法级别的代码引用重命名 ({@code RefType.METHOD})，
     * 通过方法的短 ID 查找对应的方法节点，然后调用 {@link #processRename} 执行实际重命名
     * </p>
     *
     * @param cls     目标类节点
     * @param renames 重命名规则列表
     */
    private static void applyRenames(ClassNode cls, List<ICodeRename> renames) {
        for (ICodeRename rename : renames) {
            IJavaNodeRef nodeRef = rename.getNodeRef();
            if (nodeRef.getType() == IJavaNodeRef.RefType.METHOD) {
                MethodNode methodNode = cls.searchMethodByShortId(nodeRef.getShortId());
                if (methodNode == null) {
                    LOG.warn("未找到方法引用：{}", nodeRef);
                } else {
                    IJavaCodeRef codeRef = rename.getCodeRef();
                    if (codeRef != null) {
                        processRename(methodNode, codeRef, rename);
                    }
                }
            }
        }
    }

    /**
     * 根据代码引用的附加类型执行具体的重命名操作
     * <p>
     * 支持两种重命名类型：
     * <ul>
     *   <li><b>MTH_ARG</b> —— 方法参数重命名，通过参数索引定位</li>
     *   <li><b>VAR</b> —— 局部变量重命名，通过寄存器编号和 SSA 版本号联合定位
     *       其中 index 的高 16 位为寄存器编号，低 16 位为 SSA 版本</li>
     * </ul>
     *
     * @param mth     目标方法节点
     * @param codeRef 代码引用，描述要重命名的实体位置
     * @param rename  重命名数据，包含新名称
     */
    private static void processRename(MethodNode mth, IJavaCodeRef codeRef, ICodeRename rename) {
        switch (codeRef.getAttachType()) {
            case MTH_ARG: {
                List<RegisterArg> argRegs = mth.getArgRegs();
                int argNum = codeRef.getIndex();
                if (argNum < argRegs.size()) {
                    argRegs.get(argNum).getSVar().getCodeVar().setName(rename.getNewName());
                } else {
                    LOG.warn("方法参数引用索引不正确 {}，应小于 {}", argNum, argRegs.size());
                }
                break;
            }
            case VAR: {
                int regNum = codeRef.getIndex() >> 16;
                int ssaVer = codeRef.getIndex() & 0xFFFF;
                for (SSAVar ssaVar : mth.getSVars()) {
                    if (ssaVar.getRegNum() == regNum && ssaVar.getVersion() == ssaVer) {
                        ssaVar.getCodeVar().setName(rename.getNewName());
                        return;
                    }
                }
                LOG.warn("无法通过 {}_{} 找到变量引用", regNum, ssaVer);
                break;
            }

            default:
                LOG.warn("代码引用重命名类型 {} 暂不支持", codeRef.getAttachType());
                break;
        }
    }

    /**
     * 初始化访问器，从根节点的参数中加载重命名数据，并注册数据变更监听器
     *
     * @param root AST 根节点
     * @throws JadxException 初始化过程中可能抛出的异常
     */
    @Override
    public void init(RootNode root) throws JadxException {
        updateRenamesMap(root.getArgs().getCodeData());
        root.registerCodeDataUpdateListener(this::updateRenamesMap);
    }

    /**
     * 访问类节点，对其应用重命名规则，并递归处理内部类
     *
     * @param cls 待处理的类节点
     * @return 始终返回 false，表示继续遍历
     */
    @Override
    public boolean visit(ClassNode cls) {
        List<ICodeRename> renames = getRenames(cls);
        if (!renames.isEmpty()) {
            applyRenames(cls, renames);
        }
        cls.getInnerClasses().forEach(this::visit);
        return false;
    }

    /**
     * 获取指定类对应的重命名规则列表
     *
     * @param cls 类节点
     * @return 该类的重命名规则列表，若不存在则返回空列表
     */
    private List<ICodeRename> getRenames(ClassNode cls) {
        if (clsRenamesMap == null) {
            return Collections.emptyList();
        }
        List<ICodeRename> clsComments = clsRenamesMap.get(cls.getClassInfo().getRawName());
        if (clsComments == null) {
            return Collections.emptyList();
        }
        return clsComments;
    }

    /**
     * 从外部传入的代码数据中更新重命名映射表
     * <p>
     * 将 {@link ICodeData} 中的所有重命名条目按声明类分组，
     * 并过滤掉不包含代码引用的条目 (即仅保留需要实际重命名变量/参数的条目)
     * </p>
     *
     * @param data 外部代码数据，可能为 null (表示无重命名数据)
     */
    private void updateRenamesMap(@Nullable ICodeData data) {
        if (data == null) {
            this.clsRenamesMap = Collections.emptyMap();
        } else {
            this.clsRenamesMap = data.getRenames().stream()
                    .filter(r -> r.getCodeRef() != null)
                    .collect(Collectors.groupingBy(r -> r.getNodeRef().getDeclaringClass()));
        }
    }
}
