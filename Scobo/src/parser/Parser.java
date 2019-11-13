package parser;

import java.util.HashSet;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Parser {

    private ExecutorService IOPool;
    private ExecutorService CPUPool;
    private static final int BATCH_SIZE = 10;

    private String corpusPath;

    protected Boolean readWaiter = Boolean.FALSE;
    protected Boolean parseWaiter = Boolean.FALSE;

    private HashSet<String> uniqueTerms;

    public Parser(String path) {
        IOPool = Executors.newFixedThreadPool(1);
        CPUPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        this.corpusPath = path;
        uniqueTerms = new HashSet<>();
    }

    public void start() { new ReadFile(corpusPath, this); }
    public int getBatchSize() { return Parser.BATCH_SIZE; }
    public void executeIOTask(Runnable task) { this.IOPool.execute(task); }
    public void executeCPUTask(Runnable task) { this.CPUPool.execute(task); }

    public void awaitRead() throws InterruptedException {
        synchronized (readWaiter) {
            if (!this.readWaiter) readWaiter.wait();
        }
        IOPool.shutdown();
        IOPool.awaitTermination(1, TimeUnit.MINUTES);
    }

    public void awaitParse() throws InterruptedException {
        synchronized (parseWaiter) {
            if (!this.parseWaiter) parseWaiter.wait();
        }
        CPUPool.shutdown();
        CPUPool.awaitTermination(1, TimeUnit.MINUTES);
    }

    public HashSet<String> getUniqueTerms() { return this.uniqueTerms; }
}
