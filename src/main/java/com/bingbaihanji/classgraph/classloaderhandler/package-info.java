//  20 种 ClassLoader 处理器
//
//  内建支持 20 种不同 ClassLoader，覆盖主流 Java 框架和运行时：
//
//  - Spring Boot（SpringBootRestartClassLoaderHandler）
//  - Quarkus（QuarkusClassLoaderHandler）
//  - Tomcat（TomcatWebappClassLoaderBaseHandler）
//  - JBoss / WildFly（JBossClassLoaderHandler）
//  - OSGi（FelixClassLoaderHandler, EquinoxClassLoaderHandler）
//  - WebLogic / WebSphere
//  - JPMS 模块系统
package com.bingbaihanji.classgraph.classloaderhandler;