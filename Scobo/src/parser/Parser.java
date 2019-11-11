package parser;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Parser {

    private ExecutorService IOPool;
    private ExecutorService CPUPool;

    private String corpusPath;

    private Runnable onFinishRead;
    private Runnable onFinishParse;

    protected Boolean readWaiter = Boolean.FALSE;
    protected Boolean parseWaiter = Boolean.FALSE;

    private static final int BATCH_SIZE = 10;

    public Parser(String path) {
        IOPool = Executors.newFixedThreadPool(1);
        CPUPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        this.corpusPath = path;
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
        onFinishRead.run();
    }

    public void awaitParse() throws InterruptedException {
        synchronized (parseWaiter) {
            if (!this.parseWaiter) parseWaiter.wait();
        }
        CPUPool.shutdown();
        CPUPool.awaitTermination(1, TimeUnit.MINUTES);
        onFinishParse.run();
    }

    public void setOnFinishRead(Runnable task) {
       this.onFinishRead = task;
    }

    public void setOnFinishParse(Runnable task) {
        this.onFinishParse = task;
    }

}
