package gr.uoa.di.madgik.statstool.repositories;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PrioritizedFutureTaskTest {

    private static PrioritizedFutureTask<Void> task(int priority) {
        return new PrioritizedFutureTask<>(() -> null, priority);
    }

    @Test
    public void user_beatsUpdate_beatsTrickle() {
        PrioritizedFutureTask<Void> user   = task(QueryPriority.USER.value);
        PrioritizedFutureTask<Void> update = task(QueryPriority.CACHE_UPDATE.value);
        PrioritizedFutureTask<Void> trickle = task(QueryPriority.TRICKLE.value);

        assertTrue(user.compareTo(update)  < 0, "USER must sort before CACHE_UPDATE");
        assertTrue(update.compareTo(trickle) < 0, "CACHE_UPDATE must sort before TRICKLE");
        assertTrue(user.compareTo(trickle) < 0, "USER must sort before TRICKLE");
    }

    @Test
    public void samePriority_comparesToZero() {
        PrioritizedFutureTask<Void> t1 = task(QueryPriority.USER.value);
        PrioritizedFutureTask<Void> t2 = task(QueryPriority.USER.value);
        assertEquals(0, t1.compareTo(t2));
    }

    @Test
    public void nonPrioritizedRunnable_treatedAsUser() {
        PrioritizedFutureTask<Void> trickle = task(QueryPriority.TRICKLE.value);
        Runnable plain = () -> {};
        assertTrue(trickle.compareTo(plain) > 0,
                "TRICKLE must sort after a plain Runnable (treated as USER priority)");
    }

    @Test
    public void reverseOrdering_isSymmetric() {
        PrioritizedFutureTask<Void> user   = task(QueryPriority.USER.value);
        PrioritizedFutureTask<Void> trickle = task(QueryPriority.TRICKLE.value);
        assertTrue(trickle.compareTo(user) > 0, "TRICKLE must sort after USER");
    }
}
