package parser;

import java.util.HashSet;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Parser {

    private ExecutorService IOPool;
    private ExecutorService CPUPool;
    private static final int BATCH_SIZE = 10;

    private String corpusPath;

    CountDownLatch readLatch = new CountDownLatch(1);
    CountDownLatch parseLatch = new CountDownLatch(1);

    private HashSet<String> uniqueTerms;

    public Parser(String path) {
        IOPool = Executors.newSingleThreadExecutor();
        CPUPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        this.corpusPath = path;
        uniqueTerms = new HashSet<>();
    }

    public void start() { new ReadFile(corpusPath, this); }

    public void executeIOTask(Runnable task) { this.IOPool.execute(task); }
    public void executeCPUTask(Runnable task) { this.CPUPool.execute(task); }

    public void awaitRead() throws InterruptedException {
        readLatch.await();

        IOPool.shutdown();
        if (!IOPool.awaitTermination(1, TimeUnit.MINUTES)){
            IOPool.shutdownNow();
            CPUPool.shutdownNow();
            throw new InterruptedException("reading the corpus is taking abnormally long");
        }
    }

    public void awaitParse() throws InterruptedException {
        parseLatch.await();

        CPUPool.shutdown();
        if (!CPUPool.awaitTermination(1, TimeUnit.MINUTES)) {
            IOPool.shutdownNow();
            CPUPool.shutdownNow();
            throw new InterruptedException("parsing the corpus is taking abnormally long");
        }
    }

    public int getBatchSize() { return Parser.BATCH_SIZE; }
    public HashSet<String> getUniqueTerms() { return this.uniqueTerms; }
}
