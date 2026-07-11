package com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils;

import com.bingbaihanji.fxdecomplie.core.jadx.api.data.CommentStyle;
import com.bingbaihanji.fxdecomplie.core.jadx.api.data.ICodeComment;

public class CodeComment {

	private final String comment;
	private final CommentStyle style;

	public CodeComment(String comment, CommentStyle style) {
		this.comment = comment;
		this.style = style;
	}

	public CodeComment(ICodeComment comment) {
		this(comment.getComment(), comment.getStyle());
	}

	public String getComment() {
		return comment;
	}

	public CommentStyle getStyle() {
		return style;
	}

	@Override
	public String toString() {
		return "CodeComment{" + style + ": '" + comment + "'}";
	}
}
