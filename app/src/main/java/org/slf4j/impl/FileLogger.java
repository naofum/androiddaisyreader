package org.slf4j.impl;

import org.androiddaisyreader.utils.LogFile;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MarkerIgnoringBase;
import org.slf4j.helpers.MessageFormatter;

/**
 * ファイル＋Logcat に出力する slf4j ロガー実装。
 * ライブラリ（daisy-libraries）の slf4j ログを LogFile に橋渡しする。
 */
public final class FileLogger extends MarkerIgnoringBase {

    private static final long serialVersionUID = 1L;

    private final String name;

    FileLogger(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isTraceEnabled() {
        return false;
    }

    @Override
    public void trace(String msg) {
        LogFile.v(name, msg);
    }

    @Override
    public void trace(String format, Object arg) {
        LogFile.v(name, MessageFormatter.format(format, arg).getMessage());
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
        LogFile.v(name, MessageFormatter.format(format, arg1, arg2).getMessage());
    }

    @Override
    public void trace(String format, Object... arguments) {
        LogFile.v(name, MessageFormatter.arrayFormat(format, arguments).getMessage());
    }

    @Override
    public void trace(String msg, Throwable t) {
        LogFile.v(name, msg);
    }

    @Override
    public boolean isDebugEnabled() {
        return true;
    }

    @Override
    public void debug(String msg) {
        LogFile.d(name, msg);
    }

    @Override
    public void debug(String format, Object arg) {
        LogFile.d(name, MessageFormatter.format(format, arg).getMessage());
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        LogFile.d(name, MessageFormatter.format(format, arg1, arg2).getMessage());
    }

    @Override
    public void debug(String format, Object... arguments) {
        LogFile.d(name, MessageFormatter.arrayFormat(format, arguments).getMessage());
    }

    @Override
    public void debug(String msg, Throwable t) {
        LogFile.d(name, msg);
    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public void info(String msg) {
        LogFile.i(name, msg);
    }

    @Override
    public void info(String format, Object arg) {
        LogFile.i(name, MessageFormatter.format(format, arg).getMessage());
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        LogFile.i(name, MessageFormatter.format(format, arg1, arg2).getMessage());
    }

    @Override
    public void info(String format, Object... arguments) {
        LogFile.i(name, MessageFormatter.arrayFormat(format, arguments).getMessage());
    }

    @Override
    public void info(String msg, Throwable t) {
        LogFile.i(name, msg);
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public void warn(String msg) {
        LogFile.w(name, msg);
    }

    @Override
    public void warn(String format, Object arg) {
        LogFile.w(name, MessageFormatter.format(format, arg).getMessage());
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        LogFile.w(name, MessageFormatter.format(format, arg1, arg2).getMessage());
    }

    @Override
    public void warn(String format, Object... arguments) {
        LogFile.w(name, MessageFormatter.arrayFormat(format, arguments).getMessage());
    }

    @Override
    public void warn(String msg, Throwable t) {
        LogFile.w(name, msg);
    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    @Override
    public void error(String msg) {
        LogFile.e(name, msg);
    }

    @Override
    public void error(String format, Object arg) {
        LogFile.e(name, MessageFormatter.format(format, arg).getMessage());
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        LogFile.e(name, MessageFormatter.format(format, arg1, arg2).getMessage());
    }

    @Override
    public void error(String format, Object... arguments) {
        FormattingTuple tuple = MessageFormatter.arrayFormat(format, arguments);
        LogFile.e(name, tuple.getMessage(), tuple.getThrowable());
    }

    @Override
    public void error(String msg, Throwable t) {
        LogFile.e(name, msg, t);
    }
}
