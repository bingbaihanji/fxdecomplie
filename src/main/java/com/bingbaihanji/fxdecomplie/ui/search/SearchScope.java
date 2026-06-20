package com.bingbaihanji.fxdecomplie.ui.search;

/**
 * Search execution scope selected by the search dialog.
 */
public enum SearchScope {
    ALL(true),
    CLASS(false),
    METHOD(true),
    CODE(true),
    RESOURCE(false),
    COMMENT(true),
    BYTECODE(false);

    private final boolean sourceCacheRelevant;

    SearchScope(boolean sourceCacheRelevant) {
        this.sourceCacheRelevant = sourceCacheRelevant;
    }

    /**
     * @return true when full-source cache can change the result set for this scope
     */
    public boolean sourceCacheRelevant() {
        return sourceCacheRelevant;
    }
}
