package util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;

public final class Logger {

    private String logPath;

    private LinkedList<String> logBuffer;

    private int bufferSizeBytes;

    private static final String MESSAGE_PREFIX = "[MESSAGE]: ";
    private static final String WARNING_PREFIX = "[WARNING]: ";
    private static final String ERROR_PREFIX = "[ERROR]: ";


    private Logger() {
        logPath = "LOG.txt";
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

    public static Logger getLogger() {
        if (LOG == null)
            LOG = new Logger();

        return LOG;
    }

    public synchronized void message(String message) {
        message = message.replace("\n", "\n\t");
        if (message.charAt(message.length() - 1) != '\n')
            message += "\n";
        logBuffer.addLast(MESSAGE_PREFIX + message);
        tryFlushLog(message.length());
    }

    public synchronized void warn(String warning) {
        warning = warning.replace("\n", "\n\t");
        if (warning.charAt(warning.length() - 1) != '\n')
            warning += "\n";
        logBuffer.addLast(WARNING_PREFIX + warning);
        tryFlushLog(warning.length());
    }

    public synchronized void warn(Exception exception) {
        StringBuilder warning = new StringBuilder(WARNING_PREFIX);
        logException(exception, warning);
        logBuffer.addLast(warning.toString());
        tryFlushLog(logBuffer.getLast().length());
    }

    public synchronized void error(String error) {
        error = error.replace("\n", "\n\t");
        if (error.charAt(error.length() - 1) != '\n')
            error += "\n";
        logBuffer.addLast(ERROR_PREFIX + error);
        tryFlushLog(error.length());
    }

    public synchronized void error(Exception exception) {
        StringBuilder error = new StringBuilder(ERROR_PREFIX);
        logException(exception, error);
        logBuffer.addLast(error.toString());
        tryFlushLog(logBuffer.getLast().length());
    }

    private void logException(Exception exception, StringBuilder stringBuilder) {
        StackTraceElement[] stackTraceElements = exception.getStackTrace();
        for (int i = 0; i < stackTraceElements.length; i++) {
            if (i != 0) stringBuilder.append("\t");
            stringBuilder.append(stackTraceElements[i].toString());
            stringBuilder.append("\n");
        }
    }

    private void tryFlushLog(int logSizeBytes) {
        this.bufferSizeBytes += logSizeBytes;
        int kiloByte = 2 ^ 10;
        // we flush the log if it takes up more than 1KB.
        if (logSizeBytes >= kiloByte) {
            flushLog();
        }
    }

    private void flushLog() {
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
}
