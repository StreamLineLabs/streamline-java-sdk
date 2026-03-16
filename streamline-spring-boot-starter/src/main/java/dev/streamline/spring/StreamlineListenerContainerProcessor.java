package dev.streamline.spring;

import dev.streamline.client.Streamline;
import dev.streamline.client.consumer.Consumer;
import dev.streamline.client.consumer.ConsumerConfig;
import dev.streamline.client.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.SmartLifecycle;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Processes {@link StreamlineListener} annotations on Spring beans.
 *
 * <p>Implements {@link BeanPostProcessor} to discover annotated methods during bean
 * initialization and {@link SmartLifecycle} to manage consumer threads that poll
 * the Streamline broker and dispatch records to the annotated methods.
 *
 * <p>Each annotated method gets one consumer per declared topic. The consumer runs
 * on a daemon thread that polls and invokes the method for every received
 * {@link ConsumerRecord}.
 */
public class StreamlineListenerContainerProcessor implements BeanPostProcessor, SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(StreamlineListenerContainerProcessor.class);
    private static final Duration POLL_TIMEOUT = Duration.ofMillis(1000);
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 10;
    private static final long ERROR_BACKOFF_MS = 1000;

    private final Streamline streamline;
    private final List<ListenerContainer> containers = new ArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executor;

    public StreamlineListenerContainerProcessor(Streamline streamline) {
        this.streamline = streamline;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        for (Method method : bean.getClass().getDeclaredMethods()) {
            StreamlineListener annotation = method.getAnnotation(StreamlineListener.class);
            if (annotation != null) {
                validateListenerMethod(method);
                containers.add(new ListenerContainer(bean, method, annotation));
                log.info("Registered @StreamlineListener on {}.{} for topics {}",
                    bean.getClass().getSimpleName(), method.getName(),
                    String.join(", ", annotation.topics()));
            }
        }
        return bean;
    }

    /**
     * Validates that the annotated method has at least one parameter to receive a
     * {@link ConsumerRecord}.
     */
    void validateListenerMethod(Method method) {
        if (method.getParameterCount() == 0) {
            throw new IllegalArgumentException(
                "@StreamlineListener method " + method.getName()
                    + " must accept at least one parameter (ConsumerRecord)");
        }
    }

    @Override
    public void start() {
        if (containers.isEmpty() || !running.compareAndSet(false, true)) {
            return;
        }

        // Each container may create one consumer per topic
        int threadCount = containers.stream()
            .mapToInt(c -> c.annotation.topics().length)
            .sum();

        executor = Executors.newFixedThreadPool(threadCount, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("streamline-listener-" + t.getId());
            return t;
        });

        for (ListenerContainer container : containers) {
            for (String topic : container.annotation.topics()) {
                executor.submit(() -> container.run(streamline, running, topic));
            }
        }
        log.info("Started {} Streamline listener container(s) across {} topic(s)",
            containers.size(), threadCount);
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            for (ListenerContainer container : containers) {
                container.close();
            }
            if (executor != null) {
                executor.shutdownNow();
                try {
                    executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            log.info("Stopped Streamline listener containers");
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    /** Returns the number of registered listener containers (for testing). */
    int getContainerCount() {
        return containers.size();
    }

    /**
     * Holds the bean, method, and annotation metadata for a single
     * {@code @StreamlineListener} declaration. Creates and runs a Kafka consumer
     * on the given topic.
     */
    static class ListenerContainer {
        private final Object bean;
        private final Method method;
        private final StreamlineListener annotation;
        private final List<Consumer<String, String>> consumers = new ArrayList<>();

        ListenerContainer(Object bean, Method method, StreamlineListener annotation) {
            this.bean = bean;
            this.method = method;
            this.method.setAccessible(true);
            this.annotation = annotation;
        }

        void run(Streamline streamline, AtomicBoolean running, String topic) {
            String groupId = annotation.groupId().isEmpty()
                ? bean.getClass().getSimpleName() + "." + method.getName()
                : annotation.groupId();

            ConsumerConfig config = ConsumerConfig.builder()
                .groupId(groupId)
                .enableAutoCommit(false)
                .build();

            Consumer<String, String> consumer = streamline.consumer(topic, config);
            synchronized (consumers) {
                consumers.add(consumer);
            }
            consumer.subscribe();

            log.info("Listener container started for topic {} with group {}",
                topic, groupId);

            while (running.get()) {
                try {
                    List<ConsumerRecord<String, String>> records = consumer.poll(POLL_TIMEOUT);
                    for (ConsumerRecord<String, String> record : records) {
                        invokeListener(record);
                    }
                    if (!records.isEmpty()) {
                        consumer.commitSync();
                    }
                } catch (Exception e) {
                    if (running.get()) {
                        log.error("Error in listener container for topic {}: {}",
                            topic, e.getMessage(), e);
                        try {
                            Thread.sleep(ERROR_BACKOFF_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }

        private void invokeListener(ConsumerRecord<String, String> record) {
            try {
                method.invoke(bean, record);
            } catch (Exception e) {
                log.error("Error invoking @StreamlineListener {}.{}: {}",
                    bean.getClass().getSimpleName(), method.getName(), e.getMessage(), e);
            }
        }

        void close() {
            synchronized (consumers) {
                for (Consumer<String, String> consumer : consumers) {
                    try {
                        consumer.close();
                    } catch (Exception e) {
                        log.debug("Error closing consumer: {}", e.getMessage());
                    }
                }
                consumers.clear();
            }
        }
    }
}
