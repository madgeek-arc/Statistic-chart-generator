package gr.uoa.di.madgik.statstool.repositories;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

/**
 * A FutureTask that carries a priority for use in a PriorityBlockingQueue.
 * Lower priority value = higher execution priority (USER=0 beats TRICKLE=2).
 */
class PrioritizedFutureTask<T> extends FutureTask<T> implements Comparable<Runnable> {

    private final int priority;

    PrioritizedFutureTask(Callable<T> callable, int priority) {
        super(callable);
        this.priority = priority;
    }

    @Override
    public int compareTo(Runnable other) {
        int otherPriority = (other instanceof PrioritizedFutureTask)
                ? ((PrioritizedFutureTask<?>) other).priority
                : QueryPriority.USER.value;
        return Integer.compare(this.priority, otherPriority);
    }
}
