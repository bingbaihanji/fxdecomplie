package com.bingbaihanji.fxdecomplie.ui.uml;

import java.util.List;

/** 类的方法成员 */
public record UmlMethod(String name, List<String> params, String returnType,
                        Visibility visibility, boolean isStatic, boolean isAbstract,
                        boolean isConstructor) {
}
