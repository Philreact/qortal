package org.qortal.arbitrary;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qortal.arbitrary.ArbitraryDataFile.ResourceIdType;
import org.qortal.arbitrary.misc.Service;
import org.qortal.controller.arbitrary.ArbitraryDataBuildManager;
import org.qortal.controller.arbitrary.ArbitraryResourceStateRegistry;
import org.qortal.data.arbitrary.ArbitraryResourceStatus;
import org.qortal.repository.DataException;
import org.qortal.test.common.Common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class ArbitraryDataReaderConcurrencyTests extends Common {
    private String resource;
    private ExecutorService executor;
    private final CountDownLatch fetching = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger fetches = new AtomicInteger();
    private TestReader owner;

    @Before
    public void setUp() throws Exception {
        Common.useDefaultSettings();
        this.resource = UUID.randomUUID().toString();
        this.executor = Executors.newFixedThreadPool(2);
        this.owner = new TestReader(false, 60_000L);
    }

    @After
    public void tearDown() throws Exception {
        this.release.countDown();
        this.executor.shutdownNow();
        assertTrue(this.executor.awaitTermination(5, TimeUnit.SECONDS));
        Path working = this.owner.getUncompressedPath().getParent();
        if (Files.isDirectory(working))
            FileUtils.deleteDirectory(working.toFile());
        else
            Files.deleteIfExists(working);
        assertFalse(ArbitraryDataBuildManager.getInstance().getBuildInProgress());
    }

    @Test
    public void concurrentReadersShareCompletedBuildWithoutServingPartialFiles() throws Exception {
        Future<?> first = startOwner();
        TestReader follower = new TestReader(false, 60_000L);
        AtomicReference<Thread> waitingThread = new AtomicReference<>();
        Future<?> second = this.executor.submit(() -> {
            waitingThread.set(Thread.currentThread());
            follower.loadSynchronously(true);
            return null;
        });
        awaitFollower(waitingThread);
        assertFalse(follower.isCachedDataAvailable());
        assertFalse(second.isDone());
        assertTrue(follower.loadAsynchronously(false, 1));
        this.release.countDown();
        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);
        assertEquals(1, this.fetches.get());
        assertEquals(this.owner.getFilePath(), follower.getFilePath());
        assertEquals("complete", Files.readString(follower.getFilePath().resolve("payload")));
    }

    @Test
    public void waitingCallerTimeoutCannotDeleteOrReleaseOwnersOutput() throws Exception {
        Future<?> first = startOwner();
        TestReader follower = new TestReader(false, 50L);
        try {
            follower.loadSynchronously(true);
            fail("Expected a timeout");
        } catch (DataException e) {
            assertTrue(e.getCause() instanceof TimeoutException);
        }
        assertTrue(this.owner.isBuilding());
        assertEquals("partial", Files.readString(this.owner.getUncompressedPath().resolve("payload")));
        this.release.countDown();
        first.get(5, TimeUnit.SECONDS);
        assertEquals(1, this.fetches.get());
        assertEquals("complete", Files.readString(this.owner.getFilePath().resolve("payload")));
    }

    @Test
    public void interruptingWaiterLeavesOwnerRunning() throws Exception {
        assertInterruptingWaiterLeavesOwnerRunning(60_000L);
    }

    @Test
    public void interruptingBuildWorkerWaitLeavesOwnerRunning() throws Exception {
        assertInterruptingWaiterLeavesOwnerRunning(0L);
    }

    private void assertInterruptingWaiterLeavesOwnerRunning(long waitTimeoutMillis) throws Exception {
        Future<?> first = startOwner();
        TestReader follower = new TestReader(false, 60_000L);
        AtomicReference<Thread> waitingThread = new AtomicReference<>();
        Future<Boolean> second = this.executor.submit(() -> {
            waitingThread.set(Thread.currentThread());
            try {
                follower.loadSynchronously(true, waitTimeoutMillis);
                return false;
            } catch (DataException e) {
                return Thread.currentThread().isInterrupted() && e.getCause() instanceof InterruptedException;
            }
        });
        awaitFollower(waitingThread);
        waitingThread.get().interrupt();
        assertTrue(second.get(5, TimeUnit.SECONDS));
        assertTrue(this.owner.isBuilding());
        assertTrue(Files.exists(this.owner.getUncompressedPath().resolve("payload")));
        this.release.countDown();
        first.get(5, TimeUnit.SECONDS);
        assertEquals(1, this.fetches.get());
    }

    @Test
    public void failureIsSharedAndNextCallerCanRetry() throws Exception {
        this.owner = new TestReader(true, 60_000L);
        Future<?> first = startOwner();
        TestReader follower = new TestReader(false, 60_000L);
        AtomicReference<Thread> waitingThread = new AtomicReference<>();
        Future<?> second = this.executor.submit(() -> {
            waitingThread.set(Thread.currentThread());
            follower.loadSynchronously(true);
            return null;
        });
        awaitFollower(waitingThread);
        this.release.countDown();
        for (Future<?> future : new Future<?>[] { first, second }) {
            try {
                future.get(5, TimeUnit.SECONDS);
                fail("Expected the shared build failure");
            } catch (ExecutionException e) {
                assertTrue(e.getCause() instanceof IOException);
            }
        }
        assertEquals(1, this.fetches.get());
        assertFalse(this.owner.isBuilding());
        assertFalse(Files.exists(this.owner.getUncompressedPath()));
        TestReader retry = new TestReader(false, 60_000L);
        retry.loadSynchronously(true);
        assertEquals(2, this.fetches.get());
        assertEquals("complete", Files.readString(retry.getFilePath().resolve("payload")));
    }

    @Test
    public void preparationFailureDoesNotLeakOwnershipOrBuildCounter() throws Exception {
        Path working = this.owner.getUncompressedPath().getParent();
        Files.createDirectories(working.getParent());
        Files.writeString(working, "a file prevents creating the working directory");
        try {
            this.owner.loadSynchronously(true);
            fail("Expected working directory creation to fail");
        } catch (DataException expected) {
            assertFalse(this.owner.isBuilding());
            assertFalse(ArbitraryDataBuildManager.getInstance().getBuildInProgress());
        }
        Files.delete(working);
        this.release.countDown();
        this.owner.loadSynchronously(true);
        assertEquals(1, this.fetches.get());
    }

    @Test
    public void forcedRebuildDoesNotAcceptConcurrentCacheHit() throws Exception {
        Files.createDirectories(this.owner.getUncompressedPath());
        Files.writeString(this.owner.getUncompressedPath().resolve("payload"), "old cache");
        CountDownLatch cached = new CountDownLatch(1);
        CountDownLatch resumeCache = new CountDownLatch(1);
        TestReader cacheReader = new TestReader(false, 60_000L) {
            @Override
            boolean loadFromCache(boolean overwrite) {
                boolean available = super.loadFromCache(overwrite);
                cached.countDown();
                try {
                    if (!resumeCache.await(5, TimeUnit.SECONDS))
                        throw new AssertionError("Cache reader was not released");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
                return available;
            }
        };
        Future<?> first = this.executor.submit(() -> {
            cacheReader.loadSynchronously(false);
            return null;
        });
        try {
            assertTrue(cached.await(5, TimeUnit.SECONDS));
            TestReader rebuilding = new TestReader(false, 60_000L);
            AtomicReference<Thread> waitingThread = new AtomicReference<>();
            Future<?> second = this.executor.submit(() -> {
                waitingThread.set(Thread.currentThread());
                rebuilding.loadSynchronously(true);
                return null;
            });
            awaitFollower(waitingThread);
            this.release.countDown();
            resumeCache.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertEquals(1, this.fetches.get());
            assertEquals("complete", Files.readString(rebuilding.getFilePath().resolve("payload")));
        } finally {
            resumeCache.countDown();
        }
    }

    @Test
    public void buildWorkerCanJoinBeyondNormalCallerTimeout() throws Exception {
        Future<?> first = startOwner();
        TestReader follower = new TestReader(false, 50L);
        AtomicReference<Thread> waitingThread = new AtomicReference<>();
        Future<?> second = this.executor.submit(() -> {
            waitingThread.set(Thread.currentThread());
            follower.loadSynchronously(true, 0L);
            return null;
        });
        awaitFollower(waitingThread);
        try {
            second.get(150, TimeUnit.MILLISECONDS);
            fail("Queue worker must keep waiting for the active build");
        } catch (TimeoutException expected) {
            assertTrue(this.owner.isBuilding());
        }
        this.release.countDown();
        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);
        assertEquals(1, this.fetches.get());
        assertEquals("complete", Files.readString(follower.getFilePath().resolve("payload")));
    }

    @Test
    public void fatalBuildFailureDoesNotLeavePartialDataAsUsableCache() throws Exception {
        this.release.countDown();
        this.owner = new TestReader(false, 60_000L) {
            @Override
            void fetch() throws IOException, DataException {
                super.fetch();
                throw new AssertionError("Deliberate fatal build failure");
            }
        };
        try {
            this.owner.loadSynchronously(true);
            fail("Expected fatal build failure");
        } catch (AssertionError expected) {
            assertEquals("Deliberate fatal build failure", expected.getMessage());
        }
        assertFalse(this.owner.isBuilding());
        assertFalse(this.owner.isCachedDataAvailable());
        assertFalse(Files.exists(this.owner.getUncompressedPath()));
    }

    @Test
    public void synchronousBuildInvalidatesStatusOnStartAndCompletion() throws Exception {
        this.owner = new TestReader(false, 60_000L, ResourceIdType.NAME);
        ArbitraryResourceStateRegistry registry = ArbitraryResourceStateRegistry.getInstance();
        AtomicInteger calculations = new AtomicInteger();
        ArbitraryResourceStateRegistry.Reconciliation calculate = () -> {
            calculations.incrementAndGet();
            return new ArbitraryResourceStatus(this.owner.isBuilding()
                    ? ArbitraryResourceStatus.Status.BUILDING : ArbitraryResourceStatus.Status.READY);
        };
        assertEquals(ArbitraryResourceStatus.Status.READY,
                registry.getOrReconcile(Service.ARBITRARY_DATA, this.resource, null, null, calculate).getStatus());
        Future<?> first = startOwner();
        assertEquals(ArbitraryResourceStatus.Status.BUILDING,
                registry.getOrReconcile(Service.ARBITRARY_DATA, this.resource, null, null, calculate).getStatus());
        this.release.countDown();
        first.get(5, TimeUnit.SECONDS);
        assertEquals(ArbitraryResourceStatus.Status.READY,
                registry.getOrReconcile(Service.ARBITRARY_DATA, this.resource, null, null, calculate).getStatus());
        assertEquals(3, calculations.get());
    }

    private Future<?> startOwner() throws Exception {
        Future<?> future = this.executor.submit(() -> {
            this.owner.loadSynchronously(true);
            return null;
        });
        assertTrue(this.fetching.await(5, TimeUnit.SECONDS));
        return future;
    }

    private static void awaitFollower(AtomicReference<Thread> reference) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Thread thread = reference.get();
            if (thread != null && (thread.getState() == Thread.State.TIMED_WAITING
                    || thread.getState() == Thread.State.WAITING)) {
                for (StackTraceElement frame : thread.getStackTrace()) {
                    if (frame.getMethodName().equals("awaitLoad"))
                        return;
                }
            }
            Thread.sleep(10L);
        }
        fail("Follower did not wait for the active build");
    }

    private class TestReader extends ArbitraryDataReader {
        private final boolean failFetch;
        private final long timeout;

        TestReader(boolean failFetch, long timeout) throws DataException {
            this(failFetch, timeout, ResourceIdType.FILE_HASH);
        }

        TestReader(boolean failFetch, long timeout, ResourceIdType type) throws DataException {
            super(resource, type, Service.ARBITRARY_DATA, null);
            this.failFetch = failFetch;
            this.timeout = timeout;
        }

        @Override
        long loadWaitTimeoutMillis() {
            return this.timeout;
        }

        @Override
        void fetch() throws IOException, DataException {
            fetches.incrementAndGet();
            Files.createDirectories(this.getUncompressedPath());
            Files.writeString(this.getUncompressedPath().resolve("payload"), "partial");
            fetching.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS))
                    throw new DataException("Test build was not released");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DataException("Test build interrupted", e);
            }
            if (this.failFetch)
                throw new IOException("Deliberate fetch failure");
            Files.writeString(this.getUncompressedPath().resolve("payload"), "complete");
            this.setFilePath(this.getUncompressedPath());
        }
    }
}
