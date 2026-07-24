package com.bingbaihanji.classgraph.classpath;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClasspathElementState")
class ClasspathElementStateTest {

    @Test
    @DisplayName("ordinal progression is correct")
    void ordinalProgression() {
        assertTrue(ClasspathElementState.CREATED.ordinal()
            < ClasspathElementState.INITIALIZED.ordinal());
        assertTrue(ClasspathElementState.INITIALIZED.ordinal()
            < ClasspathElementState.PATHS_SCANNED.ordinal());
        assertTrue(ClasspathElementState.PATHS_SCANNED.ordinal()
            < ClasspathElementState.CLASSES_LINKED.ordinal());
    }

    @Nested
    @DisplayName("isAtLeast")
    class IsAtLeast {

        @Test
        @DisplayName("same state is at least itself")
        void sameState() {
            assertTrue(ClasspathElementState.INITIALIZED
                .isAtLeast(ClasspathElementState.INITIALIZED));
        }

        @Test
        @DisplayName("later state is at least earlier state")
        void laterState() {
            assertTrue(ClasspathElementState.CLASSES_LINKED
                .isAtLeast(ClasspathElementState.CREATED));
        }

        @Test
        @DisplayName("earlier state is not at least later state")
        void earlierStateNotAtLeastLater() {
            assertFalse(ClasspathElementState.CREATED
                .isAtLeast(ClasspathElementState.INITIALIZED));
        }
    }

    @Nested
    @DisplayName("isTerminal")
    class IsTerminal {

        @Test
        @DisplayName("FAILED is terminal")
        void failedIsTerminal() {
            assertTrue(ClasspathElementState.FAILED.isTerminal());
        }

        @Test
        @DisplayName("CLOSED is terminal")
        void closedIsTerminal() {
            assertTrue(ClasspathElementState.CLOSED.isTerminal());
        }

        @Test
        @DisplayName("PATHS_SCANNED is not terminal")
        void pathsScannedNotTerminal() {
            assertFalse(ClasspathElementState.PATHS_SCANNED.isTerminal());
        }
    }

    @Nested
    @DisplayName("permission checks")
    class PermissionChecks {

        @Test
        @DisplayName("canScanPaths is true from INITIALIZED onwards")
        void canScanPaths() {
            assertFalse(ClasspathElementState.CREATED.canScanPaths());
            assertTrue(ClasspathElementState.INITIALIZED.canScanPaths());
            assertTrue(ClasspathElementState.PATHS_SCANNED.canScanPaths());
            assertTrue(ClasspathElementState.CLASSES_LINKED.canScanPaths());
            assertFalse(ClasspathElementState.FAILED.canScanPaths());
            assertFalse(ClasspathElementState.CLOSED.canScanPaths());
        }

        @Test
        @DisplayName("canLinkClasses is true only from PATHS_SCANNED")
        void canLinkClasses() {
            assertFalse(ClasspathElementState.INITIALIZED.canLinkClasses());
            assertTrue(ClasspathElementState.PATHS_SCANNED.canLinkClasses());
            assertFalse(ClasspathElementState.CLASSES_LINKED.canLinkClasses());
        }
    }
}
