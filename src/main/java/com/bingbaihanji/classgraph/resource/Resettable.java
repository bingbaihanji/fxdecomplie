 
package com.bingbaihanji.classgraph.resource;

/**
 * 可回收对象的接口，当调用 {@link RecycleOnClose#close()} 回收对象时需要重置该对象
 */
public interface Resettable {
    /** 重置一个可回收对象(在对象被回收时调用) */
    void reset();
}