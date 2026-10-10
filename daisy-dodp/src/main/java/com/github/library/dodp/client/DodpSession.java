package com.github.library.dodp.client;

import com.github.library.dodp.soap.SoapClient;

import java.time.Instant;

/**
 * Tracks the DODP session state. The actual session token is held as HTTP
 * cookies inside the underlying {@link SoapClient}; this class records whether
 * the client is logged in and whether the initialization sequence
 * ({@code logOn} → {@code getServiceAttributes} →
 * {@code setReadingSystemAttributes}) has been completed.
 */
public final class DodpSession {

    private final SoapClient soapClient;
    private String username;
    private Instant loggedInAt;
    private boolean readingSystemAttributesSent;

    DodpSession(SoapClient soapClient) {
        this.soapClient = soapClient;
    }

    public synchronized boolean isLoggedIn() {
        return username != null;
    }

    public synchronized boolean isInitialized() {
        return isLoggedIn() && readingSystemAttributesSent;
    }

    public synchronized String getUsername() {
        return username;
    }

    public synchronized Instant getLoggedInAt() {
        return loggedInAt;
    }

    synchronized void markLoggedIn(String username) {
        this.username = username;
        this.loggedInAt = Instant.now();
        this.readingSystemAttributesSent = false;
    }

    synchronized void markReadingSystemAttributesSent() {
        this.readingSystemAttributesSent = true;
    }

    synchronized void clear() {
        this.username = null;
        this.loggedInAt = null;
        this.readingSystemAttributesSent = false;
    }

    /**
     * Discards all cookies (i.e. the server-side session token) and resets
     * local state. Used on {@code logOff} and when a session-expired fault is
     * detected before re-authentication.
     */
    public synchronized void invalidate() {
        soapClient.clearCookies();
        clear();
    }
}
