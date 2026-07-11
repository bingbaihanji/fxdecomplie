package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.CommentsLevel;
import com.bingbaihanji.fxdecomplie.core.jadx.api.data.CommentStyle;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodeComment;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ICodeNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.ErrorsCounter;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;

public abstract class NotificationAttrNode extends LineAttrNode implements ICodeNode {

    public boolean checkCommentsLevel(CommentsLevel required) {
        return required.filter(this.root().getArgs().getCommentsLevel());
    }

    public void addError(String errStr, Throwable e) {
        ErrorsCounter.error(this, errStr, e);
    }

    public void addWarn(String warn) {
        ErrorsCounter.warning(this, warn);
        JadxCommentsAttr.add(this, CommentsLevel.WARN, warn);
        this.add(AFlag.INCONSISTENT_CODE);
    }

    public void addCodeComment(String comment) {
        addAttr(AType.CODE_COMMENTS, new CodeComment(comment, CommentStyle.LINE));
    }

    public void addCodeComment(String comment, CommentStyle style) {
        addAttr(AType.CODE_COMMENTS, new CodeComment(comment, style));
    }

    public void addWarnComment(String warn) {
        JadxCommentsAttr.add(this, CommentsLevel.WARN, warn);
    }

    public void addWarnComment(String warn, Throwable exc) {
        String commentStr = warn + root().getArgs().getCodeNewLineStr() + Utils.getStackTrace(exc);
        JadxCommentsAttr.add(this, CommentsLevel.WARN, commentStr);
    }

    public void addInfoComment(String commentStr) {
        JadxCommentsAttr.add(this, CommentsLevel.INFO, commentStr);
    }

    public void addDebugComment(String commentStr) {
        JadxCommentsAttr.add(this, CommentsLevel.DEBUG, commentStr);
    }

    public CommentsLevel getCommentsLevel() {
        return this.root().getArgs().getCommentsLevel();
    }
}
