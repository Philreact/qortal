package org.qortal.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ThreadCpuMonitor extends Thread {

	private static final Logger LOGGER = LogManager.getLogger(ThreadCpuMonitor.class);
	private static final long SAMPLE_INTERVAL = 10_000L;
	private static final int TOP_THREAD_LIMIT = 12;
	private static final int TOP_GROUP_LIMIT = 8;
	private static final Pattern TRAILING_NUMBER = Pattern.compile("[- #:]\\d+$");

	private static final ThreadCpuMonitor INSTANCE = new ThreadCpuMonitor();

	private final ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
	private final Map<Long, Long> previousCpuTimes = new HashMap<>();
	private final AtomicBoolean stopping = new AtomicBoolean(false);
	private long previousSampleNanos;

	public static ThreadCpuMonitor getInstance() {
		return INSTANCE;
	}

	private ThreadCpuMonitor() {
		setName("Thread CPU Monitor");
		setDaemon(true);
	}

	@Override
	public void run() {
		if (!this.threadMxBean.isThreadCpuTimeSupported()) {
			LOGGER.info("[ThreadCPU] Thread CPU time is not supported by this JVM");
			return;
		}

		if (!this.threadMxBean.isThreadCpuTimeEnabled())
			this.threadMxBean.setThreadCpuTimeEnabled(true);

		this.previousSampleNanos = System.nanoTime();
		sample(false);

		while (!this.stopping.get()) {
			try {
				Thread.sleep(SAMPLE_INTERVAL);
			} catch (InterruptedException e) {
				// Fall through and either stop or take one final sample.
			}

			sample(true);
		}
	}

	public void shutdown() {
		this.stopping.set(true);
		this.interrupt();
	}

	private void sample(boolean shouldLog) {
		long now = System.nanoTime();
		long elapsedNanos = Math.max(1L, now - this.previousSampleNanos);
		this.previousSampleNanos = now;

		long[] threadIds = this.threadMxBean.getAllThreadIds();
		ThreadInfo[] threadInfos = this.threadMxBean.getThreadInfo(threadIds, 0);
		List<ThreadCpuSample> samples = new ArrayList<>();
		Map<Long, Long> nextCpuTimes = new HashMap<>();

		for (int i = 0; i < threadIds.length; ++i) {
			long threadId = threadIds[i];
			long cpuTime = this.threadMxBean.getThreadCpuTime(threadId);
			if (cpuTime < 0L)
				continue;

			nextCpuTimes.put(threadId, cpuTime);

			Long previousCpuTime = this.previousCpuTimes.get(threadId);
			if (previousCpuTime == null)
				continue;

			long cpuDelta = Math.max(0L, cpuTime - previousCpuTime);
			if (cpuDelta == 0L)
				continue;

			ThreadInfo threadInfo = threadInfos[i];
			String threadName = threadInfo == null ? "thread-" + threadId : threadInfo.getThreadName();
			Thread.State threadState = threadInfo == null ? null : threadInfo.getThreadState();
			long blockedCount = threadInfo == null ? -1L : threadInfo.getBlockedCount();
			long waitedCount = threadInfo == null ? -1L : threadInfo.getWaitedCount();
			samples.add(new ThreadCpuSample(threadName, normalizeThreadName(threadName), threadState,
					cpuDelta, blockedCount, waitedCount));
		}

		this.previousCpuTimes.clear();
		this.previousCpuTimes.putAll(nextCpuTimes);

		if (!shouldLog || samples.isEmpty())
			return;

		samples.sort(Comparator.comparingLong(ThreadCpuSample::getCpuNanos).reversed());

		long totalCpuNanos = samples.stream().mapToLong(ThreadCpuSample::getCpuNanos).sum();
		String topThreads = samples.stream()
				.limit(TOP_THREAD_LIMIT)
				.map(sample -> sample.format(elapsedNanos))
				.collect(Collectors.joining(" | "));

		String topGroups = samples.stream()
				.collect(Collectors.groupingBy(ThreadCpuSample::getGroupName, Collectors.summingLong(ThreadCpuSample::getCpuNanos)))
				.entrySet()
				.stream()
				.sorted(Map.Entry.<String, Long>comparingByValue().reversed())
				.limit(TOP_GROUP_LIMIT)
				.map(entry -> String.format("%s cpu_ms=%.3f cpu_pct=%.2f",
						entry.getKey(), nanosToMillis(entry.getValue()), percent(entry.getValue(), elapsedNanos)))
				.collect(Collectors.joining(" | "));

		LOGGER.info("[ThreadCPU] interval_ms={} total_sampled_cpu_ms={} top_threads=\"{}\" top_groups=\"{}\"",
				Math.round(nanosToMillis(elapsedNanos)),
				String.format("%.3f", nanosToMillis(totalCpuNanos)),
				topThreads,
				topGroups);
	}

	private static String normalizeThreadName(String threadName) {
		String normalized = TRAILING_NUMBER.matcher(threadName).replaceFirst("");

		if (normalized.startsWith("Network-"))
			return "Network";
		if (normalized.startsWith("NetworkData-"))
			return "NetworkData";
		if (normalized.startsWith("PeerSender-"))
			return "PeerSender";
		if (normalized.startsWith("DiskIO-"))
			return "DiskIO";
		if (normalized.startsWith("ChunkReader-"))
			return "ChunkReader";
		if (normalized.startsWith("NTP:"))
			return "NTP";

		return normalized;
	}

	private static double nanosToMillis(long nanos) {
		return nanos / 1_000_000.0;
	}

	private static double percent(long cpuNanos, long elapsedNanos) {
		return cpuNanos * 100.0 / elapsedNanos;
	}

	private static class ThreadCpuSample {
		private final String threadName;
		private final String groupName;
		private final Thread.State threadState;
		private final long cpuNanos;
		private final long blockedCount;
		private final long waitedCount;

		private ThreadCpuSample(String threadName, String groupName, Thread.State threadState,
				long cpuNanos, long blockedCount, long waitedCount) {
			this.threadName = threadName;
			this.groupName = groupName;
			this.threadState = threadState;
			this.cpuNanos = cpuNanos;
			this.blockedCount = blockedCount;
			this.waitedCount = waitedCount;
		}

		private String getGroupName() {
			return this.groupName;
		}

		private long getCpuNanos() {
			return this.cpuNanos;
		}

		private String format(long elapsedNanos) {
			return String.format("%s cpu_ms=%.3f cpu_pct=%.2f state=%s blocked=%d waited=%d",
					this.threadName,
					nanosToMillis(this.cpuNanos),
					percent(this.cpuNanos, elapsedNanos),
					this.threadState,
					this.blockedCount,
					this.waitedCount);
		}
	}
}
