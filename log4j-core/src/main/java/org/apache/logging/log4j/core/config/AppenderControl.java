/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.core.config;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AppenderLoggingException;
import org.apache.logging.log4j.core.filter.AbstractFilterable;
import org.apache.logging.log4j.core.filter.Filterable;

/**
 * Wraps an {@link Appender} with details an appender implementation shouldn't need to know about.
 */
public class AppenderControl extends AbstractFilterable {

    private static final long serialVersionUID = 1L;

    private final ThreadLocal<AppenderControl> recursive = new ThreadLocal<>();

    private final Appender appender;

    private final Level level;

    private final int intLevel;

    /**
     * Constructor.
     * @param appender The target Appender.
     * @param level the Level to filter on.
     * @param filter the Filter(s) to apply.
     */
    public AppenderControl(final Appender appender, final Level level, final Filter filter) {
        super(filter);
        this.appender = appender;
        this.level = level;
        this.intLevel = level == null ? Level.ALL.intLevel() : level.intLevel();
        start();
    }

    /**
     * Returns the Appender.
     * @return the Appender.
     */
    public Appender getAppender() {
        return appender;
    }

    /**
     * Call the appender.
     * @param event The event to process.
     */
    public void callAppender(final LogEvent event) {
        if (shouldSkip(event)) {
            return;
        }
        callAppenderPreventRecursion(event);
    }

    private boolean shouldSkip(final LogEvent event) {
        return isFilteredByAppenderControl(event) || isFilteredByLevel(event) || isRecursiveCall();
    }

    private boolean isFilteredByAppenderControl(final LogEvent event) {
        return getFilter() != null && Filter.Result.DENY == getFilter().filter(event);
    }

    private boolean isFilteredByLevel(final LogEvent event) {
        return level != null && intLevel < event.getLevel().intLevel();
    }

    private boolean isRecursiveCall() {
        if (recursive.get() != null) {
            appenderErrorHandlerMessage("Recursive call to appender ");
            return true;
        }
        return false;
    }
    
    private String appenderErrorHandlerMessage(final String prefix) {
        String result = createErrorMsg(prefix);
        appender.getHandler().error(result);
        return result;
    }

    private void callAppenderPreventRecursion(final LogEvent event) {
        try {
            recursive.set(this);            
            callAppender0(event);
        } finally {
            recursive.set(null);
        }
    }

    private void callAppender0(final LogEvent event) {
        ensureAppenderStarted();
        if (!isFilteredByAppender(event)) {
            tryCallAppender(event);
        }
    }

    private void ensureAppenderStarted() {
        if (!appender.isStarted()) {
            handleError("Attempted to append to non-started appender ");
        }
    }

    private void handleError(final String prefix) {
        final String msg = appenderErrorHandlerMessage(prefix);
        if (!appender.ignoreExceptions()) {
            throw new AppenderLoggingException(msg);
        }
    }

    private String createErrorMsg(final String prefix) {
        return prefix + appender.getName();
    }
    
    private boolean isFilteredByAppender(final LogEvent event) {
        return appender instanceof Filterable && ((Filterable) appender).isFiltered(event);
    }

    private void tryCallAppender(final LogEvent event) {
        try {
            appender.append(event);
        } catch (final RuntimeException ex) {
            handleAppenderError(ex);
        } catch (final Exception ex) {
            handleAppenderError(new AppenderLoggingException(ex));
        }
    }

    private void handleAppenderError(final RuntimeException ex) {
        appender.getHandler().error(createErrorMsg("An exception occurred processing Appender "), ex);
        if (!appender.ignoreExceptions()) {
            throw ex;
        }
    }
}
