package dev.streamline.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages a pool of connections to Streamline brokers.
 *
 * <p>This class handles connection lifecycle, including automatic reconnection,
 * health checking, and exponential backoff retry for connection creation.
 * The pool is thread-safe and limits concurrent connections via a semaphore.
 *
 * <p>Connections are lazily created on first {@link #acquire()} and returned
 * to the idle queue on {@link #release(Connection)}. Unhealthy connections
 * are discarded automatically.
 */
public class ConnectionPool implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(ConnectionPool.class);

    static final int MAX_RETRY_ATTEMPTS = 3;
    static final long BASE_BACKOFF_MS = 100;
    static final long MAX_BACKOFF_MS = 5000;

    private final StreamlineConfig config;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final int poolSize;
    private final Semaphore permits;
    private final ConcurrentLinkedQueue<Connection> idleConnections;
    private final AtomicInteger totalConnections;

    public ConnectionPool(StreamlineConfig config) {
        this.config = config;
        this.poolSize = config.getConnectionPoolSize();
        this.permits = new Semaphore(poolSize, true);
        this.idleConnections = new ConcurrentLinkedQueue<>();
        this.totalConnections = new AtomicInteger(0);
        log.debug("Connection pool created for {} with size {}", config.getBootstrapServers(), poolSize);
    }

    /**
     * Acquires a connection from the pool. Blocks up to the configured connect
     * timeout if all connections are in use. Idle connections are health-checked
     * before being returned; unhealthy ones are discarded.
     *
     * @return a healthy connection
     * @throws StreamlineException if the pool is closed, timed out, or connection creation fails
     */
    public Connection acquire() {
        if (closed.get()) {
            throw new StreamlineException("Connection pool is closed");
        }

        try {
            if (!permits.tryAcquire(config.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)) {
                throw StreamlineException.timeout("Connection pool acquire — all "
                        + poolSize + " connections in use");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StreamlineException("Interrupted while acquiring connection", e);
        }

        if (closed.get()) {
            permits.release();
            throw new StreamlineException("Connection pool is closed");
        }

        // Try reusing an idle connection, discarding unhealthy ones
        Connection conn;
        while ((conn = idleConnections.poll()) != null) {
            if (conn.isHealthy()) {
                log.trace("Reusing idle connection to {}", conn.getServer());
                return conn;
            }
            log.debug("Discarding unhealthy idle connection to {}", conn.getServer());
            totalConnections.decrementAndGet();
        }

        return createConnectionWithRetry();
    }

    /**
     * Releases a connection back to the pool. If the pool is closed or the
     * connection is unhealthy, the connection is discarded instead.
     *
     * @param connection the connection to release
     */
    public void release(Connection connection) {
        if (connection == null) {
            return;
        }

        if (closed.get() || !connection.isHealthy()) {
            log.trace("Discarding connection to {}", connection.getServer());
            connection.markClosed();
            totalConnections.decrementAndGet();
            permits.release();
            return;
        }

        idleConnections.offer(connection);
        permits.release();
        log.trace("Connection released back to pool");
    }

    /**
     * Checks if the connection pool is healthy.
     *
     * @return true if the pool is open and can serve connections
     */
    public boolean isHealthy() {
        return !closed.get();
    }

    /**
     * Returns the number of connections currently sitting idle in the pool.
     */
    public int getIdleCount() {
        return idleConnections.size();
    }

    /**
     * Returns the total number of connections managed by this pool (idle + in-use).
     */
    public int getTotalConnections() {
        return totalConnections.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            log.debug("Closing connection pool");

            Connection conn;
            while ((conn = idleConnections.poll()) != null) {
                conn.markClosed();
                totalConnections.decrementAndGet();
            }

            log.debug("Connection pool closed");
        }
    }

    private Connection createConnectionWithRetry() {
        Exception lastException = null;

        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                Connection conn = createConnection(config.getBootstrapServers());
                totalConnections.incrementAndGet();
                log.debug("Created new connection to {} (attempt {})",
                        config.getBootstrapServers(), attempt + 1);
                return conn;
            } catch (Exception e) {
                lastException = e;
                long backoff = Math.min(BASE_BACKOFF_MS * (1L << attempt), MAX_BACKOFF_MS);
                log.warn("Connection attempt {} failed, retrying in {}ms: {}",
                        attempt + 1, backoff, e.getMessage());

                if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        permits.release();
                        throw new StreamlineException("Interrupted during connection retry", ie);
                    }
                }
            }
        }

        permits.release();
        throw StreamlineException.connectionFailed(config.getBootstrapServers(), lastException);
    }

    /**
     * Creates a new connection. Package-private so tests can override to simulate failures.
     */
    Connection createConnection(String server) {
        return new Connection(server);
    }

    /**
     * Represents a single connection to a Streamline broker.
     */
    public static class Connection {
        private final String server;
        private final long createdAt;
        private volatile boolean connected;

        public Connection(String server) {
            this.server = server;
            this.createdAt = System.nanoTime();
            this.connected = true;
        }

        public String getServer() {
            return server;
        }

        /**
         * Returns the time this connection was created (in nanoseconds, see {@link System#nanoTime()}).
         */
        public long getCreatedAt() {
            return createdAt;
        }

        /**
         * Returns {@code true} if this connection is still usable.
         */
        public boolean isHealthy() {
            return connected;
        }

        /**
         * Marks this connection as closed / unusable.
         */
        void markClosed() {
            this.connected = false;
        }
    }
}
