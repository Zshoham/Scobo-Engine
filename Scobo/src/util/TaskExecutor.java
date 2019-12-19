package util;

import java.util.Comparator;
import java.util.concurrent.*;

/**
 * Thread pool used to execute tasks with varying priority.
 * This implementation is practically identical to {@code ThreadPoolExecutor}
 * apart from the ability to execute tasks with a given priority.
 *
 * @see ThreadPoolExecutor
 */
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

    /**
     * Enqueues a task with default priority to be
     * executed when there is an available thread.
     * @param runnable a task to be executed.
     */
    public void execute(Runnable runnable) {
        if (runnable instanceof Task)
            super.execute(runnable);

        super.execute(taskOf(runnable, 0));
    }

    /**
     * Enqueues a task with default priority to be
     * executed when there is an available thread.
     * @param runnable a task to be executed.
     * @param priority the tasks priority {-1 (HIGH), 0 (DEFAULT), 1 (LOW)}
     */
    public void execute(Runnable runnable, int priority) {
        if (runnable instanceof Task)
            super.execute(runnable);

        super.execute(taskOf(runnable, priority));
    }

    // creates a task from the given runnable and priority.
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

    // a comparable runnable that can have a priority of execution.
    private interface Task extends Runnable {

        Comparator<Task> comparator = Comparator.comparingInt(Task::getPriority);

        int getPriority();

        @Override
        void run();
    }
}
