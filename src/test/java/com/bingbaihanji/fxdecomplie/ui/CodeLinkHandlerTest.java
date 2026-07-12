package com.bingbaihanji.fxdecomplie.ui;

import jfx.incubator.scene.control.richtext.TextPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CodeLinkHandlerTest {

    @Test
    void packageDeclarationUsesWholePackageToken() {
        String source = "package com.example.a;\nclass Foo {}";

        assertEquals("com.example.a",
                CodeLinkHandler.navigationTokenAt(source, TextPos.ofLeading(0, 13)));
    }

    @Test
    void wildcardImportUsesPackageToken() {
        String source = "import com.example.a.*;\nclass Foo {}";

        assertEquals("com.example.a",
                CodeLinkHandler.navigationTokenAt(source, TextPos.ofLeading(0, 16)));
    }

    @Test
    void importPackageSegmentUsesPackageTokenButClassSegmentUsesClassToken() {
        String source = "import com.example.Foo;\nclass Bar {}";

        assertEquals("com.example",
                CodeLinkHandler.navigationTokenAt(source, TextPos.ofLeading(0, 12)));
        assertEquals("com.example.Foo",
                CodeLinkHandler.navigationTokenAt(source, TextPos.ofLeading(0, 19)));
    }
}
