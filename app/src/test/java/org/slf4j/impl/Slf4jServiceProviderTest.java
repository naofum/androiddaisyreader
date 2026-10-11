package org.slf4j.impl;

import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * slf4j 2.x の ServiceLoader 経由で {@link FileLogger} が使われることを検証する。
 *
 * <p>META-INF/services の登録漏れ（＝ライブラリの slf4j ログが NOP で破棄される状態）を
 * 検出する回帰テスト。</p>
 */
public class Slf4jServiceProviderTest {

    @Test
    public void loggerFactoryProvidesFileLogger() {
        Logger logger = LoggerFactory.getLogger("test.slf4j");
        assertTrue("FileLogger が slf4j に接続されていること: " + logger.getClass().getName(),
                logger instanceof FileLogger);
    }
}
