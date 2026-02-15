package dev.streamline.spring;

import java.lang.annotation.*;

/**
 * Annotation for marking methods as Streamline message listeners.
 *
 * <p>Example usage:
 * <pre>{@code
 * @StreamlineListener(topics = "events", groupId = "my-service")
 * public void handleEvent(Event event) {
 *     // Process event
 * }
 *
 * @StreamlineListener(topics = {"orders", "payments"}, groupId = "order-processor")
 * public void handleOrderEvents(ConsumerRecord<String, OrderEvent> record) {
 *     // Process with full record access
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface StreamlineListener {

    /**
     * The topics to subscribe to.
     */
    String[] topics();

    /**
     * The consumer group ID.
     */
    String groupId() default "";

    /**
     * Concurrency level (number of consumer threads).
     */
    int concurrency() default 1;

    /**
     * Whether to auto-start the listener.
     */
    boolean autoStartup() default true;

    /**
     * The container factory bean name.
     */
    String containerFactory() default "";

    /**
     * Error handler bean name for processing errors.
     */
    String errorHandler() default "";
}
