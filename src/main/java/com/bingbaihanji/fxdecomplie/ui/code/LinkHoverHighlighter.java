package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.ui.CodeLinkHandler;
import com.bingbaihanji.fxdecomplie.ui.theme.RegexHighlighter;
import javafx.application.Platform;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import jfx.incubator.scene.control.richtext.CodeArea;
import jfx.incubator.scene.control.richtext.SyntaxDecorator;
import jfx.incubator.scene.control.richtext.TextPos;
import jfx.incubator.scene.control.richtext.model.CodeTextModel;
import jfx.incubator.scene.control.richtext.model.RichParagraph;
import jfx.incubator.scene.control.richtext.model.StyleAttributeMap;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ctrl hover link feedback for navigable code tokens.
 */
final class LinkHoverHighlighter {

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "(?<MULTICOMMENT>/\\*[\\s\\S]*?\\*/)"
                    + "|(?<SINGLECOMMENT>//[^\n]*)"
                    + "|(?<STRING>\"(?:\\\\.|[^\"\\\\])*\")"
                    + "|(?<ANNOTATION>@[a-zA-Z_][a-zA-Z0-9_.]*)"
                    + "|(?<NUMBER>\\b\\d+\\.?\\d*[fFlLdD]?\\b)"
                    + "|(?<IDENTIFIER>\\b[a-zA-Z_][a-zA-Z0-9_]*\\b)"
    );
    private static final String PROPERTY_KEY = "fxdecomplie.linkHoverHighlighter";
    private static final StyleAttributeMap LINK_STYLE = StyleAttributeMap.builder()
            .setTextColor(Color.web("#4fc1ff"))
            .setUnderline(true)
            .build();

    private final CodeArea codeArea;
    private final RegexHighlighter baseHighlighter;
    private final javafx.event.EventHandler<KeyEvent> keyPressedHandler = event -> {
        if (event.isControlDown()) {
            ctrlDown = true;
        }
    };
    private final javafx.event.EventHandler<MouseEvent> mouseMovedHandler = this::handleMouseMoved;
    private final javafx.event.EventHandler<MouseEvent> mouseExitedHandler = event -> clearHover();
    private final javafx.event.EventHandler<KeyEvent> keyReleasedHandler = event -> {
        if (!event.isControlDown()) {
            ctrlDown = false;
            clearHover();
        }
    };
    private boolean ctrlDown;
    private int hoverStart = -1;
    private int hoverEnd = -1;
    private boolean disposed;

    private LinkHoverHighlighter(CodeArea codeArea, RegexHighlighter baseHighlighter) {
        this.codeArea = codeArea;
        this.baseHighlighter = baseHighlighter;
    }

    static LinkHoverHighlighter install(CodeArea codeArea, RegexHighlighter baseHighlighter) {
        uninstall(codeArea);
        if (codeArea == null || baseHighlighter == null) {
            return null;
        }
        LinkHoverHighlighter highlighter = new LinkHoverHighlighter(codeArea, baseHighlighter);
        codeArea.getProperties().put(PROPERTY_KEY, highlighter);
        codeArea.addEventHandler(MouseEvent.MOUSE_MOVED, highlighter.mouseMovedHandler);
        codeArea.addEventHandler(MouseEvent.MOUSE_EXITED, highlighter.mouseExitedHandler);
        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, highlighter.keyPressedHandler);
        codeArea.addEventFilter(KeyEvent.KEY_RELEASED, highlighter.keyReleasedHandler);
        return highlighter;
    }

    static void uninstall(CodeArea codeArea) {
        if (codeArea == null) {
            return;
        }
        Object existing = codeArea.getProperties().remove(PROPERTY_KEY);
        if (existing instanceof LinkHoverHighlighter highlighter) {
            highlighter.dispose();
        }
    }

    void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        codeArea.removeEventHandler(MouseEvent.MOUSE_MOVED, mouseMovedHandler);
        codeArea.removeEventHandler(MouseEvent.MOUSE_EXITED, mouseExitedHandler);
        codeArea.removeEventFilter(KeyEvent.KEY_PRESSED, keyPressedHandler);
        codeArea.removeEventFilter(KeyEvent.KEY_RELEASED, keyReleasedHandler);
        clearHover();
    }

    private void handleMouseMoved(MouseEvent event) {
        if (!event.isControlDown() && !ctrlDown) {
            clearHover();
            return;
        }
        TextPos pos = codeArea.getTextPosition(event.getScreenX(), event.getScreenY());
        CodeLinkHandler.NavigationToken token =
                CodeLinkHandler.navigationTokenRangeAt(codeArea.getText(), pos);
        if (token == null) {
            clearHover();
            return;
        }
        setHover(token.startOffset(), token.endOffset());
    }

    private void setHover(int start, int end) {
        if (start < 0 || end <= start) {
            clearHover();
            return;
        }
        if (hoverStart == start && hoverEnd == end) {
            return;
        }
        hoverStart = start;
        hoverEnd = end;
        applyDecorator();
    }

    private void clearHover() {
        if (hoverStart < 0 && hoverEnd < 0) {
            return;
        }
        hoverStart = -1;
        hoverEnd = -1;
        if (!disposed) {
            Platform.runLater(() -> codeArea.setSyntaxDecorator(baseHighlighter));
        }
    }

    private void applyDecorator() {
        int start = hoverStart;
        int end = hoverEnd;
        SyntaxDecorator decorator = start >= 0 && end > start
                ? new LinkSyntaxDecorator(baseHighlighter, start, end)
                : baseHighlighter;
        Platform.runLater(() -> codeArea.setSyntaxDecorator(decorator));
    }

    private static final class LinkSyntaxDecorator implements SyntaxDecorator {

        private final RegexHighlighter highlighter;
        private final int linkStart;
        private final int linkEnd;

        LinkSyntaxDecorator(RegexHighlighter highlighter, int linkStart, int linkEnd) {
            this.highlighter = highlighter;
            this.linkStart = linkStart;
            this.linkEnd = linkEnd;
        }

        private static void appendWithLinkOverride(RichParagraph.Builder builder,
                                                   String segment, int segmentStart,
                                                   int linkStart, int linkEnd,
                                                   StyleAttributeMap tokenStyle) {
            int segmentEnd = segmentStart + segment.length();
            int start = Math.max(segmentStart, linkStart);
            int end = Math.min(segmentEnd, linkEnd);
            if (start >= end) {
                builder.addSegment(segment, tokenStyle);
                return;
            }
            int localStart = start - segmentStart;
            int localEnd = end - segmentStart;
            if (localStart > 0) {
                builder.addSegment(segment.substring(0, localStart), tokenStyle);
            }
            builder.addSegment(segment.substring(localStart, localEnd), LINK_STYLE);
            if (localEnd < segment.length()) {
                builder.addSegment(segment.substring(localEnd), tokenStyle);
            }
        }

        private static String extractGroupName(Matcher matcher) {
            for (String name : List.of("MULTICOMMENT", "SINGLECOMMENT", "STRING",
                    "ANNOTATION", "NUMBER", "IDENTIFIER")) {
                if (matcher.group(name) != null) {
                    return name;
                }
            }
            return null;
        }

        private static int computeParagraphOffset(CodeTextModel model, int paragraphIndex) {
            int offset = 0;
            for (int i = 0; i < paragraphIndex; i++) {
                String paragraphText = model.getPlainText(i);
                offset += (paragraphText != null ? paragraphText.length() : 0) + 1;
            }
            return offset;
        }

        @Override
        public RichParagraph createRichParagraph(CodeTextModel model, int paragraphIndex) {
            int paragraphOffset = computeParagraphOffset(model, paragraphIndex);
            String text = model.getPlainText(paragraphIndex);
            if (text == null || text.isEmpty()) {
                return RichParagraph.builder().build();
            }
            int paragraphEnd = paragraphOffset + text.length();
            int localStart = Math.max(0, linkStart - paragraphOffset);
            int localEnd = Math.min(text.length(), linkEnd - paragraphOffset);
            if (linkEnd <= paragraphOffset || linkStart >= paragraphEnd || localEnd <= localStart) {
                return highlighter.createRichParagraph(model, paragraphIndex);
            }
            return buildParagraph(text, localStart, localEnd);
        }

        private RichParagraph buildParagraph(String text, int localStart, int localEnd) {
            RichParagraph.Builder builder = RichParagraph.builder();
            Matcher matcher = TOKEN_PATTERN.matcher(text);
            int lastEnd = 0;
            while (matcher.find()) {
                if (matcher.start() > lastEnd) {
                    appendWithLinkOverride(builder, text.substring(lastEnd, matcher.start()),
                            lastEnd, localStart, localEnd,
                            highlighter.classifyToken(text.substring(lastEnd, matcher.start()),
                                    null, text, matcher.start(), lastEnd));
                }
                String token = matcher.group();
                String groupName = extractGroupName(matcher);
                StyleAttributeMap tokenStyle = highlighter.classifyToken(
                        token, groupName, text, matcher.end(), matcher.start());
                appendWithLinkOverride(builder, token, matcher.start(),
                        localStart, localEnd, tokenStyle);
                lastEnd = matcher.end();
            }
            if (lastEnd < text.length()) {
                String remaining = text.substring(lastEnd);
                appendWithLinkOverride(builder, remaining, lastEnd, localStart, localEnd,
                        highlighter.classifyToken(remaining, null, text, text.length(), lastEnd));
            }
            return builder.build();
        }

        @Override
        public void handleChange(CodeTextModel model, TextPos start, TextPos end,
                                 int linesRemoved, int linesAdded, int charIndex) {
        }
    }
}
