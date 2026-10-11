package org.slf4j.impl;

import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.helpers.NOPMDCAdapter;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

/**
 * slf4j 2.x 用のサービスプロバイダ。
 *
 * <p>slf4j 2.x は {@code META-INF/services/org.slf4j.spi.SLF4JServiceProvider} を
 * {@link java.util.ServiceLoader} で検出してロガーを解決する。ここでファイル＋Logcat
 * 出力の {@link FileLoggerFactory} を slf4j に接続する。</p>
 *
 * <p>GLUE: {@code app/src/main/resources/META-INF/services/org.slf4j.spi.SLF4JServiceProvider}
 * にこのクラス名を記載すること。</p>
 */
public final class Slf4jServiceProvider implements SLF4JServiceProvider {

    /** 実装が対象とする slf4j-api のバージョン（slf4j 本体の実装に合わせる）。 */
    public static final String REQUESTED_API_VERSION = "2.0.99";

    private final ILoggerFactory loggerFactory = new FileLoggerFactory();
    private final IMarkerFactory markerFactory = new BasicMarkerFactory();
    private final MDCAdapter mdcAdapter = new NOPMDCAdapter();

    @Override
    public ILoggerFactory getLoggerFactory() {
        return loggerFactory;
    }

    @Override
    public IMarkerFactory getMarkerFactory() {
        return markerFactory;
    }

    @Override
    public MDCAdapter getMDCAdapter() {
        return mdcAdapter;
    }

    @Override
    public String getRequestedApiVersion() {
        return REQUESTED_API_VERSION;
    }

    @Override
    public void initialize() {
        // 追加の初期化は不要
    }
}
