package util;

import static util.TaskManager.TaskType;

/**
 * Encapsulates a group of tasks in order to be able to reason
 * about them as one unit.
 * Task Groups should be used when there is a clear grouping of tasks
 * that can all be added to the group before all the added tasks are
 * completed.<br>
 * Example Usage:
 * <pre>
 *     //some CPU tasks that need to be executed
 *     Runnable[] CPUTasks = getCPU();
 *     //some IO tasks that need to be executed
 *     Runnable[] IOTasks = getIO();
 *
 *     // create CPU task group
 *     TaskGroup CPUgroup = TaskManager.getTaskGroup(GroupType.IO);
 *     //create IO task group
 *     TaskGroup IOgroup = TaskManager.getTaskGroup(GroupType.COMPUTE);
 *
 *     // add the CPU tasks to the CPU group
 *     for (Runnable r : CPUTasks)
 *          CPUgroup.add(r);
 *
 *     // add the IO tasks to the IO group
 *     for (Runnable r : IOTasks)
 *          IOGroup.add(r);
 *
 *      CPUgroup.awaitCompletion();
 *      System.out.println("all CPU tasks have been completed");
 *
 *      IOgroup.awaitCompletion();
 *      System.out.println("all IO tasks have been completed");
 * </pre>
 */
public final class TaskGroup {

    //TODO: add ability to start the waiting only after all tasks have been added.

    /**
     * defines the type of the tasks that will be executed in this group.
     */
    private TaskType type;
    private CountLatch latch;
    private TaskManager manager;

    protected TaskGroup(TaskManager manager, TaskType type) {
        this.manager = manager;
        this.type = type;
        latch = new CountLatch(0);
    }

    /**
     * Adds a task to the group
     * and schedules it through the TaskManager
     * as a {@link TaskGroup#type} task.
     * @param task a task to be executed
     */
    public void add(Runnable task) {
        latch.countUp();
        if (type == TaskType.COMPUTE)
            manager.executeCPU(task);
        if (type == TaskType.IO)
            manager.executeIO(task);
    }

    /**
     * notifies the task group that a task has completed.
     * calling this method is required when a task finishes
     * in order to update the await mechanism.
     */
    public void complete() {
        latch.countDown();
    }

    /**
     * Causes the calling thread to wait until
     * every task that was added to the group using {@link TaskGroup#add(Runnable)}
     * has called the {@link TaskGroup#complete()} method.
     *
     * <p>
     *     <em>note that the thread may be notified before all
     *     the planned tasks for this group have completed</em> for example
     *     if there was a delay in adding tasks and tasks 1,2,3 were completed
     *     before task 4 could be added to the group.
     *     When using Task Group take care to have a clear batch of tasks that can be executed.
     * </p>
     */
    public void awaitCompletion() {
        try { latch.await(); }
        catch (InterruptedException e) {
            Logger.getInstance().warn(e);
        }
    }
}
