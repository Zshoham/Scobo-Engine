package util;

import java.util.Comparator;
import java.util.concurrent.*;

public class TaskExecutor extends ThreadPoolExecutor {

    public static final int PRIORITY_LOW        = 1;
    public static final int DEFAULT_PRIORITY    = 0;
    public static final int HIGH_PRIORITY       = -1;

    @SuppressWarnings({"unchecked"})
    public TaskExecutor(int poolSize, int initialSize) {
        super(poolSize, poolSize,
                0L, TimeUnit.MILLISECONDS,
                (BlockingQueue) new PriorityBlockingQueue<Task>(initialSize, Task.comparator));
    }

    public void execute(Runnable runnable) {
        if (runnable instanceof Task)
            super.execute(runnable);

        super.execute(taskOf(runnable, 0));
    }

    public void execute(Runnable runnable, int priority) {
        if (runnable instanceof Task)
            super.execute(runnable);

        super.execute(taskOf(runnable, priority));
    }

    private static Task taskOf(Runnable runnable, int priority) {
        return new Task() {
            @Override
            public int getPriority() {
                return priority;
            }

            @Override
            public void run() {
                runnable.run();
            }
        };
    }

    private interface Task extends Runnable {

        Comparator<Task> comparator = Comparator.comparingInt(Task::getPriority);

        int getPriority();

        @Override
        void run();
    }
}
