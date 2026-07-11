package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors;

import com.bingbaihanji.fxdecomplie.core.jadx.api.data.*;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodeComment;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.IAttributeNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.*;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@JadxVisitor(
        name = "AttachComments",
        desc = "Attach user code comments",
        runBefore = {
                ProcessInstructionsVisitor.class
        }
)
public class AttachCommentsVisitor extends AbstractVisitor {

    private static final Logger LOG = LoggerFactory.getLogger(AttachCommentsVisitor.class);

    private Map<String, List<ICodeComment>> clsCommentsMap;

    private static void applyComments(ClassNode cls, List<ICodeComment> clsComments) {
        for (ICodeComment comment : clsComments) {
            IJavaNodeRef nodeRef = comment.getNodeRef();
            switch (nodeRef.getType()) {
                case CLASS:
                    addComment(cls, comment);
                    break;

                case FIELD:
                    FieldNode fieldNode = cls.searchFieldByShortId(nodeRef.getShortId());
                    if (fieldNode == null) {
                        LOG.warn("Field reference not found: {}", nodeRef);
                    } else {
                        addComment(fieldNode, comment);
                    }
                    break;

                case METHOD:
                    MethodNode methodNode = cls.searchMethodByShortId(nodeRef.getShortId());
                    if (methodNode == null) {
                        LOG.warn("Method reference not found: {}", nodeRef);
                    } else {
                        IJavaCodeRef codeRef = comment.getCodeRef();
                        if (codeRef == null) {
                            addComment(methodNode, comment);
                        } else {
                            processCustomAttach(methodNode, codeRef, comment);
                        }
                    }
                    break;
            }
        }
    }

    private static InsnNode getInsnByOffset(MethodNode mth, int offset) {
        try {
            return mth.getInstructions()[offset];
        } catch (Exception e) {
            LOG.warn("Insn reference not found in: {} with offset: {}", mth, offset);
            return null;
        }
    }

    private static void processCustomAttach(MethodNode mth, IJavaCodeRef codeRef, ICodeComment comment) {
        CodeRefType attachType = codeRef.getAttachType();
        switch (attachType) {
            case INSN: {
                InsnNode insn = getInsnByOffset(mth, codeRef.getIndex());
                addComment(insn, comment);
                break;
            }
            default:
                throw new JadxRuntimeException("Unexpected attach type: " + attachType);
        }
    }

    private static void addComment(@Nullable IAttributeNode node, ICodeComment comment) {
        if (node == null) {
            return;
        }
        node.addAttr(AType.CODE_COMMENTS, new CodeComment(comment));
    }

    @Override
    public void init(RootNode root) throws JadxException {
        updateCommentsData(root.getArgs().getCodeData());
        root.registerCodeDataUpdateListener(this::updateCommentsData);
    }

    @Override
    public boolean visit(ClassNode cls) {
        List<ICodeComment> clsComments = getCommentsData(cls);
        if (!clsComments.isEmpty()) {
            applyComments(cls, clsComments);
        }
        cls.getInnerClasses().forEach(this::visit);
        return false;
    }

    private List<ICodeComment> getCommentsData(ClassNode cls) {
        if (clsCommentsMap == null) {
            return Collections.emptyList();
        }
        List<ICodeComment> clsComments = clsCommentsMap.get(cls.getClassInfo().getRawName());
        if (clsComments == null) {
            return Collections.emptyList();
        }
        return clsComments;
    }

    private void updateCommentsData(@Nullable ICodeData data) {
        if (data == null) {
            this.clsCommentsMap = Collections.emptyMap();
        } else {
            this.clsCommentsMap = data.getComments().stream()
                    .collect(Collectors.groupingBy(c -> c.getNodeRef().getDeclaringClass()));
        }
    }
}
