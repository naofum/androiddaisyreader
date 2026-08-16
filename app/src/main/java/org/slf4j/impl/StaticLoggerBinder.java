package org.slf4j.impl;

import org.slf4j.ILoggerFactory;
import org.slf4j.spi.LoggerFactoryBinder;

/**
 * slf4j 1.7 のロガーファクトリバインダー。
 * ファイル＋Logcat 出力の {@link FileLoggerFactory} を返す。
 */
public final class StaticLoggerBinder implements LoggerFactoryBinder {

    private static final StaticLoggerBinder SINGLETON = new StaticLoggerBinder();

    public static final String REQUESTED_API_VERSION = "1.7.36";

    private final ILoggerFactory loggerFactory = new FileLoggerFactory();

    private StaticLoggerBinder() {
    }

    public static StaticLoggerBinder getSingleton() {
        return SINGLETON;
    }

    @Override
    public ILoggerFactory getLoggerFactory() {
        return loggerFactory;
    }

    @Override
    public String getLoggerFactoryClassStr() {
        return FileLoggerFactory.class.getName();
    }
}
