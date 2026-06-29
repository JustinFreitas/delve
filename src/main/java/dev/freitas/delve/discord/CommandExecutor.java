package dev.freitas.delve.discord;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * Runs command invocations off the JDA gateway thread on a virtual-thread-per-task executor. This is
 * the Java analogue of ukulele's coroutine {@code CommandScope}: one failing command never affects
 * the others, exceptions are logged rather than swallowed, and {@link #destroy()} shuts the pool down
 * cleanly. Java 25 virtual threads keep blocking work (JDBC saves) cheap.
 */
@Component
public class CommandExecutor implements DisposableBean {

    private final Logger log = LoggerFactory.getLogger(CommandExecutor.class);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public void submit(String label, Runnable task) {
        executor.submit(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                log.error("Unhandled exception in command coroutine [{}]", label, t);
            }
        });
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
