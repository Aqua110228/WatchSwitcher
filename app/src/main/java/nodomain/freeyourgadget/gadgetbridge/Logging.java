/*  Copyright (C) 2016-2024 Andreas Shimokawa, Carsten Pfeiffer, Daniele
    Gobbetti, José Rebelo, Pavel Elagin, Petr Vaněk

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.spi.LifeCycle;
import ch.qos.logback.core.util.FileSize;
import ch.qos.logback.core.util.StatusPrinter;
import nodomain.freeyourgadget.gadgetbridge.util.FileUtils;

public class Logging {
    private static final Logger LOG = LoggerFactory.getLogger(Logging.class);

    private static final Logging INSTANCE = new Logging();

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private Logging() {
    }

    private String logDirectory;
    private FileAppender<ILoggingEvent> fileLogger;
    private boolean initialized = false;
    private boolean traceEnabled = false;

    public static Logging getInstance() {
        return INSTANCE;
    }

    public void initialize(final boolean enable, final boolean trace) {
        setFileLoggingEnabled(enable);
        setTraceLogging(trace);
        // prepare for log shutdown
        if (!initialized) {
            final Thread thread = new Thread(this::shutdown, "shutdownHook");
            final Runtime runtime = Runtime.getRuntime();
            runtime.addShutdownHook(thread);
            initialized = true;
        }
    }

    public void setFileLoggingEnabled(final boolean enable) {
        try {
            if (!isFileLoggerInitialized()) {
                init();
            }
            if (enable) {
                startFileLogger();
            } else {
                stopFileLogger();
            }
            LOG.info(
                    "Gadgetbridge version: {}-{}{} {} {}",
                    BuildConfig.VERSION_NAME,
                    BuildConfig.GIT_HASH_SHORT,
                    BuildConfig.GIT_DIRTY_STATUS,
                    BuildConfig.FLAVOR,
                    BuildConfig.BUILD_TYPE
            );

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
                LOG.info(
                        "Android: SDK_INT={} SECURITY_PATCH={}",
                        Build.VERSION.SDK_INT,
                        Build.VERSION.SECURITY_PATCH
                );
            } else {
                LOG.info(
                        "Android: SDK_INT={} SDK_INT_FULL={} SECURITY_PATCH={}",
                        Build.VERSION.SDK_INT,
                        Build.VERSION.SDK_INT_FULL,
                        Build.VERSION.SECURITY_PATCH
                );
            }
        } catch (final Exception ex) {
            LOG.error("External files dir not available, cannot log to file", ex);
            stopFileLogger();
        }
    }

    /// flush the log buffer and stop file logging
    public void shutdown() {
        try {
            stopFileLogger();
        } catch (Throwable ignored) {
        }

        try {
            LifeCycle lifeCycle = (LifeCycle) LoggerFactory.getILoggerFactory();
            lifeCycle.stop();
        } catch (Throwable ignored) {
        }
    }

    @Nullable
    public String getLogPath() {
        if (fileLogger != null)
            return fileLogger.getFile();
        else
            return null;
    }

    public void flush() {
        if (fileLogger != null && !fileLogger.isImmediateFlush()) {
            fileLogger.setImmediateFlush(true);
            // Write something so it's actually flushed
            LOG.debug("Flushing logs");
            fileLogger.setImmediateFlush(false);
        }
    }

    public boolean isFileLoggerInitialized() {
        return logDirectory != null;
    }

    public void setTraceLogging(final boolean traceEnabled) {
        this.traceEnabled = traceEnabled;
        applyRootLevel();
    }

    /**
     * The level at which the log is worth producing, given where it would be written. Below DEBUG a
     * statement is neither formatted nor written, though its arguments are still evaluated, so a
     * costly argument is only skipped where the call site defers it or sits inside an
     * {@code isDebugEnabled()} block.
     * <p>
     * Detail is only worth producing where it can be read back: a log file the user collects, or a
     * build a developer is attached to. Logcat on a release build is neither, so both TRACE and
     * DEBUG require one of those.
     * <p>
     * A file logger implies at least DEBUG: {@link #flush()} writes a debug statement to force the
     * buffered appender out to disk, and the log a user shares is expected to hold the detail.
     */
    @VisibleForTesting
    public static Level resolveRootLevel(final boolean traceEnabled,
                                         final boolean fileLogging,
                                         final boolean debugBuild) {
        if (!fileLogging && !debugBuild) {
            return Level.INFO;
        }
        return traceEnabled ? Level.TRACE : Level.DEBUG;
    }

    private void applyRootLevel() {
        final Level level = resolveRootLevel(traceEnabled, fileLogger != null, BuildConfig.DEBUG);
        try {
            ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            root.setLevel(level);
        } catch (final Throwable e) {
            LOG.error("Error changing log level", e);
        }
    }

    public void debugLoggingConfiguration() {
        // For debugging problems with the logback configuration
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        // print logback's internal status
        StatusPrinter.print(lc);
//        Logger logger = LoggerFactory.getLogger(Logging.class);
    }

    protected String createLogDirectory() throws IOException {
        return FileUtils.getExternalFilesDir().getAbsolutePath();
    }

    private void init() throws IOException {
        LOG.debug("Initializing logging");
        logDirectory = createLogDirectory();
        if (logDirectory == null) {
            throw new IllegalArgumentException("log directory must not be null");
        }
    }

    private void startFileLogger() {
        if (fileLogger != null) {
            LOG.warn("Logger already started");
            return;
        }

        if (logDirectory == null) {
            LOG.error("Can't start file logging without a log directory");
            return;
        }

        final FileAppender<ILoggingEvent> fileAppender = createFileAppender(logDirectory);
        fileAppender.start();
        attachLogger(fileAppender);
        fileLogger = fileAppender;
        applyRootLevel();
    }

    public void stopFileLogger() {
        if (fileLogger != null) {
            if (fileLogger.isStarted()) {
                fileLogger.stop();
            }

            detachLogger(fileLogger);

            fileLogger = null;
        }

        applyRootLevel();
    }

    private static void attachLogger(final Appender<ILoggingEvent> logger) {
        try {
            ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            if (!root.isAttached(logger)) {
                root.addAppender(logger);
            }
        } catch (final Throwable e) {
            LOG.error("Error attaching logger appender", e);
        }
    }

    private static void detachLogger(final Appender<ILoggingEvent> logger) {
        try {
            ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            if (logger != null && root.isAttached(logger)) {
                root.detachAppender(logger);
            }
        } catch (final Throwable e) {
            LOG.error("Error detaching logger appender", e);
        }
    }

    @VisibleForTesting
    public FileAppender<ILoggingEvent> getFileLogger() {
        return fileLogger;
    }

    /**
     * The {@link #formatBytes(byte[])} of an array, produced only if it is written.
     *
     * @see nodomain.freeyourgadget.gadgetbridge.util.GB#lazyHexdump(byte[])
     */
    @NonNull
    public static Object lazyBytes(@Nullable final byte[] bytes) {
        return new Object() {
            @NonNull
            @Override
            public String toString() {
                return formatBytes(bytes);
            }
        };
    }

    @NonNull
    public static String formatBytes(@Nullable final byte[] bytes) {
        if (bytes == null) {
            return "(null)";
        } else if (bytes.length == 0) {
            return "";
        }

        // two hex digits per byte, single space between, none trailing
        final char[] chars = new char[bytes.length * 3 - 1];
        int at = 0;
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                chars[at++] = ' ';
            }
            final int b = bytes[i] & 0xff;
            chars[at++] = HEX_CHARS[b >>> 4];
            chars[at++] = HEX_CHARS[b & 0x0f];
        }
        return new String(chars);
    }

    private static FileAppender<ILoggingEvent> createFileAppender(final String logDirectory) {
        final LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();

        final PatternLayoutEncoder ple = new PatternLayoutEncoder();
        //ple.setPattern("%date %level [%thread] %logger{10} [%file:%line] %msg%n");
        ple.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{1} - %msg%n");
        ple.setContext(lc);
        ple.start();

        final SizeAndTimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new SizeAndTimeBasedRollingPolicy<>();
        final RollingFileAppender<ILoggingEvent> fileAppender = new RollingFileAppender<>();

        rollingPolicy.setContext(lc);
        rollingPolicy.setFileNamePattern(logDirectory + "/gadgetbridge-%d{yyyy-MM-dd}.%i.log.zip");
        rollingPolicy.setParent(fileAppender);
        rollingPolicy.setMaxFileSize(FileSize.valueOf(BuildConfig.DEBUG ? "100MB" : "10MB"));
        rollingPolicy.setMaxHistory(10);
        rollingPolicy.setTotalSizeCap(FileSize.valueOf(BuildConfig.DEBUG ? "200MB" : "100MB"));

        fileAppender.setContext(lc);
        fileAppender.setName("FILE");
        fileAppender.setLazy(true);
        fileAppender.setFile(logDirectory + "/gadgetbridge.log");
        fileAppender.setEncoder(ple);
        // to debug crashes, set immediateFlush to true, otherwise keep it false to improve throughput
        fileAppender.setImmediateFlush(false);
        fileAppender.setRollingPolicy(rollingPolicy);

        // Only once the appender has its file: the policy dates the current period from that file's
        // age, and without it a log left from an earlier day is taken for today's and never rolled over.
        rollingPolicy.start();

        return fileAppender;
    }
}
