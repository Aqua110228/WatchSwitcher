package nodomain.freeyourgadget.gadgetbridge.test;

import org.junit.After;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import nodomain.freeyourgadget.gadgetbridge.BuildConfig;
import nodomain.freeyourgadget.gadgetbridge.Logging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests dynamic enablement and disablement of file appenders.
 */
public class LoggingTest extends TestBase {

    public LoggingTest() {
    }

    /**
     * The root logger and the {@link Logging} singleton are process-wide, so a test that moves them
     * has to put them back for whatever runs next.
     */
    @After
    @Override
    public void tearDown() throws Exception {
        final Logging logging = Logging.getInstance();
        logging.setTraceLogging(false);
        logging.setFileLoggingEnabled(false);
        super.tearDown();
    }

    @Test
    public void testToggleLogging() {
        final Logging logging = Logging.getInstance();

        try {
            logging.setFileLoggingEnabled(true);
            assertNotNull(logging.getFileLogger());
            assertTrue(logging.getFileLogger().isStarted());

            logging.setFileLoggingEnabled(false);
            assertNull(logging.getFileLogger());

            logging.setFileLoggingEnabled(true);
            assertNotNull(logging.getFileLogger());
            assertTrue(logging.getFileLogger().isStarted());
        } catch (AssertionError ex) {
            logging.debugLoggingConfiguration();
            System.err.println(System.getProperty("java.class.path"));
            throw ex;
        }
    }

    @Test
    public void testRootLevelFollowsTheDestination() {
        // a log the user collects, or a build a developer reads logcat from, is worth the detail
        assertEquals(Level.DEBUG, Logging.resolveRootLevel(false, true, false));
        assertEquals(Level.DEBUG, Logging.resolveRootLevel(false, false, true));

        // trace goes on top of one of those
        assertEquals(Level.TRACE, Logging.resolveRootLevel(true, true, false));
        assertEquals(Level.TRACE, Logging.resolveRootLevel(true, false, true));
        assertEquals(Level.TRACE, Logging.resolveRootLevel(true, true, true));

        // nobody is reading it, trace switch included
        assertEquals(Level.INFO, Logging.resolveRootLevel(false, false, false));
        assertEquals(Level.INFO, Logging.resolveRootLevel(true, false, false));
    }

    @Test
    public void testTogglingTheFileLoggerMovesTheRootLevel() {
        final Logging logging = Logging.getInstance();
        final ch.qos.logback.classic.Logger root =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

        logging.setTraceLogging(false);

        logging.setFileLoggingEnabled(true);
        // Logging.flush() forces the buffered appender out with a debug statement, so a started
        // file logger has to imply that debug statements are emitted
        assertEquals(Level.DEBUG, root.getLevel());

        logging.setTraceLogging(true);
        assertEquals(Level.TRACE, root.getLevel());

        logging.setTraceLogging(false);
        logging.setFileLoggingEnabled(false);
        assertEquals(Logging.resolveRootLevel(false, false, BuildConfig.DEBUG), root.getLevel());
    }

    @Test
    public void testLogFormat() {
        String tempOut = Logging.formatBytes(new byte[] {0xa});
        assertEquals("0a", tempOut);

        tempOut = Logging.formatBytes(new byte[] {0xa, 1, (byte) 255});
        assertEquals("0a 01 ff", tempOut);

        assertEquals("", Logging.formatBytes(new byte[0]));
        assertEquals("(null)", Logging.formatBytes(null));
    }

    @Test
    public void testLazyLogFormatMatchesTheEagerOne() {
        final byte[] bytes = {0xa, 1, (byte) 255};

        assertEquals(Logging.formatBytes(bytes), Logging.lazyBytes(bytes).toString());
        assertEquals(Logging.formatBytes(new byte[0]), Logging.lazyBytes(new byte[0]).toString());
        assertEquals(Logging.formatBytes(null), Logging.lazyBytes(null).toString());
    }

    /**
     * A log left from an earlier day belongs to that day. When file logging starts it has to be
     * rolled over, not taken for today's and appended to.
     */
    @Test
    public void testLogLeftFromAnEarlierDayIsRolledOverWhenLoggingStarts() throws Exception {
        final Logging logging = Logging.getInstance();
        // start once to learn where the log lives, then stop so an old one can be put there
        logging.setFileLoggingEnabled(true);
        final File log = new File(logging.getLogPath());
        logging.setFileLoggingEnabled(false);

        Files.write(log.toPath(), "left from an earlier day\n".getBytes(StandardCharsets.UTF_8));
        assertTrue(log.setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2)));

        logging.setFileLoggingEnabled(true);
        LoggerFactory.getLogger(LoggingTest.class).info("written today");
        logging.flush();

        final String active = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
        assertFalse("the earlier day's log was appended to instead of rolled over",
                active.contains("left from an earlier day"));
        assertTrue(active.contains("written today"));
    }
}
