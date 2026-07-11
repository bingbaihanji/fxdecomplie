package com.bingbaihanji.fxdecomplie.core.jadx.api.metadata;

public interface ICodeAnnotation {

    AnnType getAnnType();

    enum AnnType {
        CLASS,
        FIELD,
        METHOD,
        PKG,
        VAR,
        VAR_REF,
        DECLARATION,
        OFFSET,
        END // class or method body end
    }
}
