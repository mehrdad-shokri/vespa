// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import java.io.Closeable;
import java.util.function.BiConsumer;

/**
 * CloseableInvoker is an abstract implementation of {@link Closeable} with an additional hook for
 * executing code at closing. Classes that extend CloseableInvoker need to override {@link #release()}
 * instead of {@link #close()} which is final to avoid accidental overriding.
 *
 * @author ollivir
 */
public abstract class CloseableInvoker implements Closeable {
    protected abstract void release();

    private BiConsumer<Boolean, Long> teardown = null;
    private boolean success = false;
    private long startTime = 0;

    public void teardown(BiConsumer<Boolean, Long> teardown) {
        this.teardown = teardown;
        this.startTime = System.currentTimeMillis();
    }

    protected void setFinalStatus(boolean success) {
        this.success = success;
    }

    @Override
    public final void close() {
        if (teardown != null) {
            teardown.accept(success, System.currentTimeMillis() - startTime);
            teardown = null;
        }
        release();
    }
}
