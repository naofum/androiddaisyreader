package org.slf4j.impl;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * {@link FileLogger} を生成する ILoggerFactory 実装。
 */
public final class FileLoggerFactory implements ILoggerFactory {

    private final ConcurrentMap<String, Logger> loggerMap = new ConcurrentHashMap<>();

    @Override
    public Logger getLogger(String name) {
        Logger logger = loggerMap.get(name);
        if (logger != null) {
            return logger;
        }
        Logger newInstance = new FileLogger(name);
        Logger old = loggerMap.putIfAbsent(name, newInstance);
        return old == null ? newInstance : old;
    }
}
