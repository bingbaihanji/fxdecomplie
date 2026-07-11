package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.fixaccessmodifiers;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.AccessFlags;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.AccessInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ICodeNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;

import java.util.function.Consumer;

class VisibilityUtils {

    private final RootNode root;

    VisibilityUtils(RootNode rootNode) {
        this.root = rootNode;
    }

    private static void visitDeclaringNodes(ICodeNode targetNode, Consumer<ICodeNode> action) {
        ICodeNode currentNode = targetNode;
        do {
            action.accept(currentNode);
            currentNode = currentNode.getDeclaringClass();
        } while (currentNode != null);
    }

    private static boolean inSamePkg(ClassNode cls1, ClassNode cls2) {
        return cls1.getPackageNode().equals(cls2.getPackageNode());
    }

    private static boolean inSameTopClass(ClassNode cls1, ClassNode cls2) {
        return cls1.getTopParentClass().equals(cls2.getTopParentClass());
    }

    void checkVisibility(ICodeNode targetNode, ICodeNode callerNode, OnBadVisibilityCallback callback) {
        ClassNode targetCls = targetNode instanceof ClassNode
                ? (ClassNode) targetNode
                : targetNode.getDeclaringClass();

        ClassNode callerCls = callerNode instanceof ClassNode
                ? (ClassNode) callerNode
                : callerNode.getDeclaringClass();

        if (targetCls.equals(callerCls) || inSameTopClass(targetCls, callerCls)) {
            return;
        }

        if (inSamePkg(targetCls, callerCls)) {
            visitDeclaringNodes(targetNode, node -> {
                if (node.getAccessFlags().isPrivate()) {
                    callback.onBadVisibility(node, 0); // PACKAGE_PRIVATE
                }
            });
        } else {
            visitDeclaringNodes(targetNode, node -> {
                AccessInfo nodeVisFlags = node.getAccessFlags().getVisibility();
                if (nodeVisFlags.isPublic()) {
                    return;
                }

                if (nodeVisFlags.isPrivate() || nodeVisFlags.isPackagePrivate()) {
                    ClassNode nodeDeclaringCls = node.getDeclaringClass();
                    int expectedVisFlag = nodeDeclaringCls != null && isSuperType(callerCls, nodeDeclaringCls)
                            ? AccessFlags.PROTECTED
                            : AccessFlags.PUBLIC;

                    callback.onBadVisibility(node, expectedVisFlag);
                } else if (nodeVisFlags.isProtected()) {
                    ClassNode nodeDeclaringCls = node.getDeclaringClass();
                    if (nodeDeclaringCls == null || !isSuperType(callerCls, nodeDeclaringCls)) {
                        callback.onBadVisibility(node, AccessFlags.PUBLIC);
                    }
                } else {
                    throw new JadxRuntimeException(nodeVisFlags + " is not supported");
                }
            });
        }
    }

    private boolean isSuperType(ClassNode cls, ClassNode superCls) {
        return root.getClsp().getSuperTypes(cls.getRawName()).stream()
                .anyMatch(x -> x.equals(superCls.getRawName()));
    }

    interface OnBadVisibilityCallback {
        void onBadVisibility(ICodeNode node, int expectedVisFlag);
    }
}
