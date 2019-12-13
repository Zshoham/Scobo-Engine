package util;

import java.util.Collection;
import java.util.concurrent.CountDownLatch;

import static util.TaskManager.*;

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
 *     TaskGroup CPUGroup = TaskManager.getTaskGroup(GroupType.IO);
 *     //create IO task group
 *     TaskGroup IOGroup = TaskManager.getTaskGroup(GroupType.COMPUTE);
 *
 *     // open the group to ensure that awaitCompletion works as intended.
 *     CPUGroup.openGroup();
 *     // add the CPU tasks to the CPU group
 *     for (Runnable r : CPUTasks)
 *          CPUGroup.add(r);
 *     CPUGroup.closeGroup();
 *
 *     // open the group to ensure that awaitCompletion works as intended.
 *     IOGroup.openGroup();
 *     // add the IO tasks to the IO group
 *     for (Runnable r : IOTasks)
 *          IOGroup.add(r);
 *     CPUGroup.closeGroup();
 *
 *     CPUGroup.awaitCompletion();
 *     System.out.println("all CPU tasks have been completed");
 *
 *     IOGroup.awaitCompletion();
 *     System.out.println("all IO tasks have been completed");
 * </pre>
 */
public final class TaskGroup {

    /**
     * defines the type of the tasks that will be executed in this group.
     */
    private TaskType type;
    private TaskPriority priority;
    private TaskManager manager;
    private CountLatch latch;
    private CountDownLatch groupLatch;
    private volatile boolean isOpen;

    protected TaskGroup(TaskManager manager, TaskType type, TaskPriority priority) {
        this.manager = manager;
        this.type = type;
        this.priority = priority;
        latch = new CountLatch(0);
        groupLatch = new CountDownLatch(0);
        isOpen = false;
    }

    /**
     * Adds a task to the group
     * and schedules it through the TaskManager
     * as a {@link TaskGroup#type} task.
     *
     * @param task a task to be executed
     */
    public void add(Runnable task) {
        latch.countUp();
        if (type == TaskType.COMPUTE)
            manager.executeCPU(task, priority.getVal());
        if (type == TaskType.IO)
            manager.executeIO(task, priority.getVal());
    }

    /**
     * Adds a collection of tasks to the group
     * and schedules them through the TaskManager
     * as a {@link TaskGroup#type} task.
     * <p>
     *     Calling {@link #awaitCompletion()} after using this method ensures that all
     *     the tasks added will complete before the thread calling
     *     {@link #awaitCompletion()} will be notified.
     * </p>
     *
     * @param tasks a collection of tasks to add to the group
     */
    public void add(Collection<? extends Runnable> tasks) {
        for (Runnable task : tasks) {
            if (type == TaskType.COMPUTE)
                manager.executeCPU(task, priority.getVal());
            if (type == TaskType.IO)
                manager.executeIO(task, priority.getVal());
        }
        latch.countUp(tasks.size());
    }

    /**
     * Ensures that if a threads calls {@link #awaitCompletion()} on this group, it will not
     * be notified before {@link TaskGroup#closeGroup()} is called.
     * <p>
     *     This is intended in order to ensure that a batch of tasks that cannot
     *     be executed using {@link #add(Collection)} will all be executed before
     *     a thread calling {@link #awaitCompletion()} is notified.
     *     This is done by calling {@code openGroup} before calling {@link #awaitCompletion}
     *     then adding the tasks to the group, and calling {@link #closeGroup()} when all
     *     tasks have been added.
     *     (see the example in the class documentation)
     * </p>
     * <p> Calling {@code openGroup} more than once, has no effect.
     *
     * @see #closeGroup()
     */
    public synchronized void openGroup() {
        if (!isOpen) {
            this.isOpen = true;
            this.groupLatch = new CountDownLatch(1);
        }
    }

    /**
     * Notifies the group that all tasks have been added and threads
     * that are waiting using {@link #awaitCompletion()} will be notified once
     * the tasks group is empty.
     * <p> Calling {@code closeGroup} more than once, has no effect.
     */
    public synchronized void closeGroup() {
        if (isOpen){
            this.isOpen = false;
            groupLatch.countDown();
        }
    }

    /**
     * notifies the task group that a task has completed.
     * calling this method is required when a task finishes
     * in order to update the {@link #awaitCompletion()} mechanism.
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
     *     <em>The calling thread may be notified before all
     *     the planned tasks for this group have completed.</em> for example
     *     if there was a delay in adding tasks and tasks 1,2,3 were completed
     *     before task 4 could be added to the group.
     *     When using Task Group take care to have a clear batch of tasks that can be executed.
     * </p>
     * <p> In order to be assured this issue does not arise use {@link TaskGroup#openGroup()}
     */
    public void awaitCompletion() {
        try {
            groupLatch.await();
            latch.await();
        }
        catch (InterruptedException e) {
            Logger.getInstance().warn(e);
        }
    }
}
