package util;


/**
 * <p>
 *     Simple task manager, servers as basically a wrapper for Executor service.
 *     This class implements the singleton pattern, in order to attain an instance
 *     use {@link TaskManager#getInstance()}.
 * </p>
 * <p>
 *     You may also attain an instance of {@link TaskGroup} using
 *     {@link TaskManager#getTaskGroup(TaskType)} it is recommended to
 *     use TaskGroups when it is needed to treat a group of tasks
 *     as one unit e.g - needing to wait for a batch of task to complete.
 * </p>
 *
 *
 */
public final class TaskManager {

    public enum TaskType {IO, COMPUTE}

    public enum TaskPriority {
        HIGH(TaskExecutor.HIGH_PRIORITY),
        DEFAULT(TaskExecutor.DEFAULT_PRIORITY),
        LOW(TaskExecutor.PRIORITY_LOW);

        private final int val;
        TaskPriority(int val) { this.val = val; }

        public int getVal() { return this.val; }
    }
    /*
    Executor services used to execute the incoming tasks,
    the separation between IO and CPU tasks is made in order to allow
    all the IO threads to run while all the CPU threads are running
    without there being a situation where there are more IO or CPU threads
    than the amount available.
     */
    private TaskExecutor IOExecutor;
    private TaskExecutor CPUExecutor;

    private TaskManager() {
        final int IOThreads = Runtime.getRuntime().availableProcessors();
        final int CPUThreads = Runtime.getRuntime().availableProcessors();

        IOExecutor = new TaskExecutor(IOThreads, 128);
        CPUExecutor = new TaskExecutor(CPUThreads, 32768);
    }

    private static TaskManager taskManager = null;

    public static TaskManager getInstance() {
        if (taskManager == null)
            taskManager = new TaskManager();
        return taskManager;
    }

    /**
     * Creates a Task Group that can be used to execute tasks
     * as part of group and treat all the tasks executed through
     * the group as a single unit.
     * <p><em> The Task group will have MEDIUM priority
     * @param type the task type {IO, COMPUTE}
     * @return a Task Group object capable of executing tasks.
     */
    public static TaskGroup getTaskGroup(TaskType type) {
        return new TaskGroup(getInstance(), type, TaskPriority.DEFAULT);
    }

    public static TaskGroup getTaskGroup(TaskType type, TaskPriority priority) {
        return new TaskGroup(getInstance(), type, priority);
    }

    /**
     * Enqueue's the task into the IO task queue, the task
     * will execute when its turn arrives.
     * @param task a task to execute
     */
    public void executeIO(Runnable task) {
        IOExecutor.execute(task);
    }

    /**
     * Enqueue's the task into the IO task queue, the task
     * will execute when its turn arrives.
     * @param task task a task to execute
     * @param priority priority of the task in the queue, higher priority
     *                  means the tasks turn will come sooner.
     */
    public void executeIO(Runnable task, int priority) {
        IOExecutor.execute(task, priority);
    }

    /**
     * Enqueue's the task into the CPU task queue, the task
     * will execute when its turn arrives.
     * @param task a task to execute
     */
    public void executeCPU(Runnable task) {
        CPUExecutor.execute(task);
    }

    /**
     * Enqueue's the task into the CPU task queue, the task
     * will execute when its turn arrives.
     * @param task task a task to execute
     * @param priority priority of the task in the queue, higher priority
     *                  means the tasks turn will come sooner.
     */
    public void executeCPU(Runnable task, int priority) {
        CPUExecutor.execute(task, priority);
    }
}
