package util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.LinkedList;

/**
 * Simplistic Logger class.
 * This class is a singleton, as such in order to use it call {@link Logger#getInstance()}
 * Supports Three logging levels :
 * <ul>
 *     <li>MESSAGE - use {@link Logger#message(String)} to log message</li>
 *     <li>WARNING - use {@link Logger#warn(String)} to log warning</li>
 *     <li>ERROR - use {@link Logger#error(String)} to log error</li>
 * </ul>
 * <p>
 *     The logs are not written to the log file instantly, this is done to improve preformance
 *     and not force consecutive IO when it could be avoided.
 *     The logs will be written into the file when they reach a size of 1KB, otherwise to force
 *     the logger to write to the log file use {@link Logger#flushLog()}
 * </p>
 */
public final class Logger {


    private static final String MESSAGE_PREFIX = "[ MESSAGE ]: ";
    private static final String WARNING_PREFIX = "[ WARNING ]: ";
    private static final String ERROR_PREFIX = "[ ERROR ]: ";


    /*
    buffer that stores all the incoming log calls
    once the buffer exceeds some predefined amount
    all the logs will be pushed to the log file.
     */
    private LinkedList<String> logBuffer;
    private String logPath;

    private int bufferSizeBytes;

    private Logger() {
        logPath = Configuration.getInstance().getLogPath();
        logBuffer = new LinkedList<>();
        this.bufferSizeBytes = 0;

        try {
            new File(logPath).createNewFile();
        } catch (IOException e) {
            System.err.println("[IO ERROR]: invalid log file path");
            System.exit(1);
        }
    }

    private static Logger LOG;

    public static Logger getInstance() {
        if (LOG == null)
            LOG = new Logger();

        return LOG;
    }

    /**
     * Logs a message in the format: [ TIME ][ MESSAGE ]: message
     * <p> Messages are intended as part of the
     * regular operation of the program, and as
     * a tool to output information such as debug data
     * timings and more.
     *
     * @param message a message to be logged.
     */
    public synchronized void message(String message) {
        message = message.replace("\n", "\n\t");
        if (message.charAt(message.length() - 1) != '\n')
            message += "\n";
        message = getTime() + MESSAGE_PREFIX + message;
        logBuffer.addLast(message);
        tryFlushLog(message.length());
    }

    /**
     * Logs a warning in the format: [ TIME ][ WARNING ]: warning
     * <p> Warnings are intended as errors or exceptions
     * that the program can recover from and continue execution.
     *
     * @param warning a warning to be logged.
     */
    public synchronized void warn(String warning) {
        warning = warning.replace("\n", "\n\t");
        if (warning.charAt(warning.length() - 1) != '\n')
            warning += "\n";
        warning = getTime() + WARNING_PREFIX + warning;
        logBuffer.addLast(warning);
        tryFlushLog(warning.length());
    }

    /**
     * Logs a warning in the format: [ TIME ][ WARNING ]: warning
     * <p> Warnings are intended as errors or exceptions
     * that the program can recover from and continue execution.
     *
     * @param exception a warning about an {@link Exception} to be logged.
     */
    public synchronized void warn(Exception exception) {
        StringBuilder warning = new StringBuilder(getTime() + WARNING_PREFIX);
        logException(exception, warning);
        logBuffer.addLast(warning.toString());
        tryFlushLog(logBuffer.getLast().length());
    }

    /**
     * Logs a warning in the format: [ TIME ][ ERROR ]: error
     * <p> Errors are intended as problems or exceptions
     * that the program can <em>not</em> recover from.
     *
     * @param error an error to be logged.
     */
    public synchronized void error(String error) {
        error = error.replace("\n", "\n\t");
        if (error.charAt(error.length() - 1) != '\n')
            error += "\n";
        error = getTime() + ERROR_PREFIX + error;
        logBuffer.addLast(error);
        tryFlushLog(error.length());
    }

    /**
     * Logs a warning in the format: [ TIME ][ ERROR ]: error
     * <p> Errors are intended as problems or exceptions
     * that the program can <em>not</em> recover from.
     *
     * @param exception an error in the form of an {@link Exception} to be logged.
     */
    public synchronized void error(Exception exception) {
        StringBuilder error = new StringBuilder(getTime() + ERROR_PREFIX);
        logException(exception, error);
        logBuffer.addLast(error.toString());
        tryFlushLog(logBuffer.getLast().length());
    }

    // formats the exception into a string and adds it to the log buffer.
    private void logException(Exception exception, StringBuilder stringBuilder) {
        StackTraceElement[] stackTraceElements = exception.getStackTrace();
        for (int i = 0; i < stackTraceElements.length; i++) {
            if (i != 0) stringBuilder.append("\t");
            stringBuilder.append(stackTraceElements[i].toString());
            stringBuilder.append("\n");
        }
    }

    /**
     * Flushes the log buffer into the log file,
     * this method should be called when the logger is
     * about to be destroyed or otherwise become unavailable.
     */
    public void flushLog() {
        byte[] toWrite = new byte[bufferSizeBytes];
        int i = 0;
        for (String str : logBuffer) {
            System.arraycopy(str.getBytes(), 0, toWrite, i, str.length());
            i += str.length();
        }

        try {
            Files.write(Paths.get(this.logPath), toWrite, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[IO ERROR]: cannot write to log file");
            System.exit(1);
        }
    }

    /*
     checks if the log buffer has become large enough to flush
     if so flush it.
     */
    private void tryFlushLog(int logSizeBytes) {
        this.bufferSizeBytes += logSizeBytes;
        int kiloByte = (int) Math.pow(2, 10);
        // we flush the log if it takes up more than 1KB.
        if (bufferSizeBytes >= kiloByte) {
            flushLog();
        }
    }

    // creates a formatted string of the current date and time.
    private String getTime() {
        return "[ " + new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(Calendar.getInstance().getTime()) + " ]";
    }

}
