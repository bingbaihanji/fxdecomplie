package com.bingbaihanji.javafx;

import com.bingbaihanji.windows.jfx.DefaultWindowTheme;
import com.bingbaihanji.windows.jfx.FxTools;
import com.bingbaihanji.windows.platform.win32.NativeWindowsTools;
import com.sun.jna.platform.win32.WinDef;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 *
 * @author bingbaihanji
 * @date 2026-06-21 04:56:08
 * @description //TODO
 */
public class WindowsTest {
    static void main() {
        Windows.main();
    }

    public static class Windows extends Application {
        private final static String WINDOW_TITLE = "这是一个牛逼窗口";

        static void main() {
            launch();
        }

        @Override
        public void start(Stage primaryStage) throws Exception {


            BorderPane root = new BorderPane();
            root.setStyle("-fx-background-color: #000000");
            Scene scene = new Scene(root, 800, 600);
            primaryStage.setScene(scene);
            primaryStage.setTitle(WINDOW_TITLE);
            primaryStage.show();
            DefaultWindowTheme.applyWindowDarkMode(primaryStage);
            setWindowsSty(primaryStage);
        }

        public static void setWindowsSty(Stage stage) {
            WinDef.HWND windowHandle = FxTools.getWindowHandle(stage);
            if (windowHandle == null) {
                return;
            }

            NativeWindowsTools.extendFrameIntoClientArea(windowHandle);
//            NativeWindowsTools.setClickThrough(windowHandle,true);
            NativeWindowsTools. setWindowAlpha(windowHandle,0.5f);
            NativeWindowsTools. bringToFront(windowHandle );
            NativeWindowsTools. flashWindow(windowHandle ,10,700);

        }

    }

}
