package util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * A synchronization aid that allows one or more threads to wait until
 * a set of operations being performed in other threads completes.
 *
 * <p>
 *     The {@code CountLatch} is very similar to the {@link java.util.concurrent.CountDownLatch}
 *     it provides the same functionality only adding the ability to {@link CountLatch#countUp()}
 *     in addition to to {@link CountDownLatch#countDown()}.
 * </p>
 *
 * <p>
 *     The {@code CountLatch} is initialized to some count, the count may be thought of
 *     as the number of tasks/threads that need to complete before some other waiting threads
 *     can continue. The count can change through the {@link CountLatch#countDown()} and
 *     {@link CountLatch#countUp()}, whenever the count reaches 0 all the threads waiting
 *     using the {@link CountLatch#await()} method will be notified.
 * </p>
 * <em> The maximum count is {@link Long#MAX_VALUE} </em>
 */
public class CountLatch {


    /**
     * The underlying synchronization mechanism used for the CountLatch.
     * Uses the AQS as a basic mutex.
     */
    private class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 1L;

        public Sync() { }

        @Override
        protected int tryAcquireShared(int arg) {
            return count.get() == 0 ? 1 : -1;
        }

        @Override
        protected boolean tryReleaseShared(int arg) {
            return true;
        }
    }


    /**
     * the number of times {@link #countDown} must be invoked
     * before threads can pass through {@link CountLatch#await}
     */
    private final AtomicLong count;
    private final Sync sync;

    /**
     * Creates a {@code CountLatch} with a specified
     * initial count.
     * @param initialCount the desired initial count of the latch.
     * @see CountLatch#count
     */
    public CountLatch(final long initialCount) {
        this.count = new AtomicLong(initialCount);
        this.sync = new Sync();
    }


    /**
     * Causes the current thread to wait until the latch has counted down to
     * zero, unless the thread is {@linkplain Thread#interrupt interrupted}.
     *
     * <p>If the current count is zero then this method returns immediately.
     *
     * <p>If the current count is greater than zero then the current
     * thread becomes disabled for thread scheduling purposes and lies
     * dormant until one of two things happen:
     * <ul>
     * <li>The count reaches zero due to invocations of the
     * {@link CountLatch#countDown} method; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread.
     * </ul>
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting,
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * @throws InterruptedException if the current thread is interrupted
     *         while waiting
     */
    public void await() throws InterruptedException {
        sync.acquireSharedInterruptibly(1);
    }

    /**
     * Increments the count of the latch.
     *
     * @return the new count of the latch
     * @see #countUp(int)
     */
    public long countUp() {
        return count.incrementAndGet();
    }

    /**
     * Adds {@code delta} to the count of the latch.
     *
     * @param delta the amount to add to the latch
     * @return the new count of the latch
     * @see #countUp()
     */
    public long countUp(int delta) {
        if (delta < 0)
            throw new IllegalArgumentException("countUp does not support negative numbers, use countDown to decrease count instead");

        return count.addAndGet(delta);
    }

    /**
     * Decrements the count of the latch, releasing all waiting threads if
     * the count reaches zero.
     *
     * @return the new count of the latch
     */
    public long countDown() {
        final long current = count.decrementAndGet();
        if (current == 0)
            sync.releaseShared(0);

        return current;
    }
}
