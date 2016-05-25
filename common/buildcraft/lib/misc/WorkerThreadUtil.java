package buildcraft.lib.misc;

import java.util.concurrent.*;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;

import com.google.common.base.Throwables;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;

import buildcraft.api.core.BCDebugging;
import buildcraft.api.core.BCLog;

/** Provides a pool of worker threads that can execute tasks. Each task should take no longer than (ideally) 5ms or at a
 * push 30ms. Each task is watched to make sure that it takes less time to complete that that, and if it takes longer
 * then a warning is logged. */
public class WorkerThreadUtil {
    private static final ExecutorService WORKING_POOL, DEPENDANT_WORKING_POOL, MONITORING_POOL;
    private static final boolean DEBUG = BCDebugging.shouldDebugLog("lib.threads");

    static {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int max = Math.max(1, availableProcessors / 3);
        if (DEBUG) {
            BCLog.logger.info("[lib.threads] Creating 2 thread pools with up to " + max + " threads each.");
        }

        ThreadFactory factory = new BasicThreadFactory.Builder().daemon(false).namingPattern("BuildCraft Worker Thread %d").build();
        RejectedExecutionHandler rejectHandler = new CallerRunsPolicy();
        WORKING_POOL = new ThreadPoolExecutor(0, max, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), factory, rejectHandler);

        factory = new BasicThreadFactory.Builder().daemon(false).namingPattern("BuildCraft Dependant Worker Thread %d").build();
        DEPENDANT_WORKING_POOL = new ThreadPoolExecutor(0, max, 60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), factory, rejectHandler);

        if (DEBUG) {
            factory = new BasicThreadFactory.Builder().daemon(false).namingPattern("BuildCraft Monitoring Thread %d").build();
            MONITORING_POOL = Executors.newCachedThreadPool(factory);
        } else {
            MONITORING_POOL = null;
        }
    }

    /** Executes a task. If this is in debug mode then If this takes longer than 30ms the it assumes that something has
     * gone wrong, and will notify the log that a task took too long. If it goes on for longer than 10s then it will
     * make a big error in the log. */
    public static void executeWorkTask(Runnable task) {
        TaskRunnable taskMonitor = new TaskRunnable(task);
        Future<?> future = WORKING_POOL.submit(taskMonitor);
        if (!future.isDone()) {
            executeMonitoringTask(new MonitorTask(taskMonitor, future, task.getClass()));
        }
    }

    /** Executes a task. If this is in debug mode then If this takes longer than 30ms the it assumes that something has
     * gone wrong, and will notify the log that a task took too long. If it goes on for longer than 10s then it will
     * make a big error in the log. */
    public static <T> Future<T> executeWorkTask(Callable<T> task) {
        TaskCallable<T> taskMonitor = new TaskCallable<>(task);
        Future<T> future = WORKING_POOL.submit(taskMonitor);
        if (!future.isDone()) {
            executeMonitoringTask(new MonitorTask(taskMonitor, future, task.getClass()));
        }
        return future;
    }

    public static <T> T executeWorkTaskWaiting(Callable<T> task) throws InterruptedException {
        try {
            return executeWorkTask(task).get();
        } catch (ExecutionException e) {
            // Something went wrong- this is NOT meant to happen.
            throw Throwables.propagate(e);
        }
    }

    /** Executes a task that is dependent on OTHER tasks run in {@link #executeWorkTask(Runnable)}. This is NOT
     * monitored, so you should make these tasks "delegate managers" rather than actual workers themselves. */
    public static void executeDependantTask(Runnable task) {
        DEPENDANT_WORKING_POOL.execute(task);
    }

    /** Executes a monitoring task. This is ONLY run when this is in debug mode, so ONLY use this for monitoring other
     * tasks. */
    public static void executeMonitoringTask(Runnable task) {
        if (DEBUG) {
            MONITORING_POOL.execute(task);
        }
    }

    private static abstract class Task {
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch end = new CountDownLatch(1);
    }

    private static class TaskRunnable extends Task implements Runnable {
        private final Runnable delegate;

        public TaskRunnable(Runnable delegate) {
            this.delegate = delegate;
        }

        @Override
        public void run() {
            start.countDown();
            try {
                delegate.run();
            } finally {
                end.countDown();
            }
        }
    }

    private static class TaskCallable<T> extends Task implements Callable<T> {
        private final Callable<T> delegate;

        public TaskCallable(Callable<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public T call() throws Exception {
            start.countDown();
            try {
                return delegate.call();
            } finally {
                end.countDown();
            }
        }
    }

    private static class MonitorTask implements Runnable {
        private final Task task;
        private final Future<?> future;
        private final String taskType;

        public MonitorTask(Task task, Future<?> future, Class<?> taskType) {
            this.task = task;
            this.future = future;
            this.taskType = taskType.getSimpleName();
        }

        @Override
        public void run() {
            try {
                runThrowable();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void runThrowable() throws InterruptedException {
            long startMonitor = System.currentTimeMillis();
            task.start.await();
            if (System.currentTimeMillis() - startMonitor > 30) {
                BCLog.logger.warn("[lib.threads] A task took a long time to start! (more than 30 ms) [" + taskType + "]");
            }
            try {
                future.get(30, TimeUnit.MILLISECONDS);
            } catch (ExecutionException e1) {
                // Ignore it- it will have been logged by the executor
            } catch (TimeoutException e) {
                BCLog.logger.warn("[lib.threads] A task took too long! (more than 30 ms) [" + taskType + "]");
                try {
                    future.get(9970, TimeUnit.MILLISECONDS);
                } catch (ExecutionException e1) {
                    // Ignore it- it will have been logged by the executor
                } catch (TimeoutException e1) {
                    BCLog.logger.warn("[lib.threads] A task took WAAAAY too long! (more than 10 seconds) [" + taskType + "]");
                    task.end.await();
                    BCLog.logger.info("[lib.threads] The task FINALLY completed after " + (System.currentTimeMillis() - startMonitor) + "ms");
                }
            }
        }
    }
}