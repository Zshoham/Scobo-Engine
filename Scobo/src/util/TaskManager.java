package util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


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

    /*
    Executor services used to execute the incoming tasks,
    the separation between IO and CPU tasks is made in order to allow
    all the IO threads to run while all the CPU threads are running
    without there being a situation where there are more IO or CPU threads
    than the amount available.
     */
    private ExecutorService IOExecutor;
    private ExecutorService CPUExecutor;

    private TaskManager() {
        // it is assumed the system is using an HDD thus having only one IO thread available.
        IOExecutor = Executors.newSingleThreadExecutor();
        CPUExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
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
     * @param type the task type {IO, COMPUTE}
     * @return a Task Group object capable of executing tasks.
     */
    public static TaskGroup getTaskGroup(TaskType type) {
        return new TaskGroup(getInstance(), type);
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
     * Enqueue's the task into the CPU task queue, the task
     * will execute when its turn arrives.
     * @param task a task to execute
     */
    public void executeCPU(Runnable task) {
        CPUExecutor.execute(task);
    }
}
