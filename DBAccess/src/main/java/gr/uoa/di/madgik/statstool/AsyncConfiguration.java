package gr.uoa.di.madgik.statstool;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration
public class AsyncConfiguration {

    @Bean
    public ExecutorService taskExecutor() {
        // PriorityBlockingQueue orders tasks by QueryPriority: USER(0) < CACHE_UPDATE(1) < TRICKLE(2).
        // Pool size 4 preserves the existing admission-control limit.
        return new ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS, new PriorityBlockingQueue<>());
    }
}
