package org.qortal.repository.hsqldb;

import com.google.common.primitives.Longs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ciyam.at.Timestamp;
import org.qortal.controller.Controller;
import org.qortal.crypto.Crypto;
import org.qortal.data.at.ATData;
import org.qortal.data.at.ATStateData;
import org.qortal.repository.ATRepository;
import org.qortal.repository.DataException;
import org.qortal.utils.ByteArray;

import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;

import org.qortal.data.account.AccountData;
import org.qortal.at.ATExecInstrumentation;

public class HSQLDBATRepository implements ATRepository {

	private static final Logger LOGGER = LogManager.getLogger(HSQLDBATRepository.class);

	/*
	 * ATExecutionQueue is derived from canonical ATRuntime/ATNextIncoming state. Verify it once per JVM startup before
	 * trusting it on the hot path, and repair from canonical state if a local database is missing/stale.
	 */
	private static volatile boolean executionQueueVerified = false;

	protected HSQLDBRepository repository;

	private boolean runtimeRowsVerified = false;

	public HSQLDBATRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	/*
	 * Mutable runtime fields were moved to ATRuntime so normal AT execution no longer rewrites the wide ATs row that
	 * contains immutable deployment/code bytes. The fallback keeps older/incomplete databases readable until ATRuntime
	 * has been backfilled or repaired.
	 */
	private static String runtimeColumn(String columnName) {
		return "CASE WHEN ATRuntime.AT_address IS NULL THEN ATs." + columnName + " ELSE ATRuntime." + columnName + " END";
	}

	// ATs

	@Override
	public ATData fromATAddress(String atAddress) throws DataException {
		String sql = "SELECT creator, created_when, version, asset_id, code_bytes, code_hash, "
				+ runtimeColumn("is_sleeping") + ", "
				+ runtimeColumn("sleep_until_height") + ", "
				+ runtimeColumn("is_finished") + ", "
				+ runtimeColumn("had_fatal_error") + ", "
				+ runtimeColumn("is_frozen") + ", "
				+ runtimeColumn("frozen_balance") + ", "
				+ runtimeColumn("sleep_until_message_timestamp") + " "
				+ "FROM ATs "
				+ "LEFT OUTER JOIN ATRuntime ON ATRuntime.AT_address = ATs.AT_address "
				+ "WHERE ATs.AT_address = ? LIMIT 1";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, atAddress)) {
			if (resultSet == null)
				return null;

			byte[] creatorPublicKey = resultSet.getBytes(1);
			long created = resultSet.getLong(2);
			int version = resultSet.getInt(3);
			long assetId = resultSet.getLong(4);
			byte[] codeBytes = resultSet.getBytes(5); // Actually BLOB
			byte[] codeHash = resultSet.getBytes(6);
			boolean isSleeping = resultSet.getBoolean(7);

			Integer sleepUntilHeight = resultSet.getInt(8);
			if (sleepUntilHeight == 0 && resultSet.wasNull())
				sleepUntilHeight = null;

			boolean isFinished = resultSet.getBoolean(9);
			boolean hadFatalError = resultSet.getBoolean(10);
			boolean isFrozen = resultSet.getBoolean(11);

			Long frozenBalance = resultSet.getLong(12);
			if (frozenBalance == 0 && resultSet.wasNull())
				frozenBalance = null;

			Long sleepUntilMessageTimestamp = resultSet.getLong(13);
			if (sleepUntilMessageTimestamp == 0 && resultSet.wasNull())
				sleepUntilMessageTimestamp = null;

			return new ATData(atAddress, creatorPublicKey, created, version, assetId, codeBytes, codeHash,
					isSleeping, sleepUntilHeight, isFinished, hadFatalError, isFrozen, frozenBalance,
					sleepUntilMessageTimestamp);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch AT from repository", e);
		}
	}

	@Override
	public List<ATData> fromATAddresses(List<String> atAddresses) throws DataException {
		String sql = "SELECT creator, created_when, version, asset_id, code_bytes, code_hash, "
				+ runtimeColumn("is_sleeping") + ", "
				+ runtimeColumn("sleep_until_height") + ", "
				+ runtimeColumn("is_finished") + ", "
				+ runtimeColumn("had_fatal_error") + ", "
				+ runtimeColumn("is_frozen") + ", "
				+ runtimeColumn("frozen_balance") + ", "
				+ runtimeColumn("sleep_until_message_timestamp") + ", ATs.AT_address "
				+ "FROM ATs "
				+ "LEFT OUTER JOIN ATRuntime ON ATRuntime.AT_address = ATs.AT_address "
				+ "WHERE ATs.AT_address IN ("
				+ String.join(", ", Collections.nCopies(atAddresses.size(), "?"))
				+ ")"
				;

		List<ATData> list;
		try (ResultSet resultSet = this.repository.checkedExecute(sql, atAddresses.toArray(new String[atAddresses.size()]))) {
			if (resultSet == null) {
				return new ArrayList<>(0);
			}

			list = new ArrayList<>(atAddresses.size());

			do {
				byte[] creatorPublicKey = resultSet.getBytes(1);
				long created = resultSet.getLong(2);
				int version = resultSet.getInt(3);
				long assetId = resultSet.getLong(4);
				byte[] codeBytes = resultSet.getBytes(5); // Actually BLOB
				byte[] codeHash = resultSet.getBytes(6);
				boolean isSleeping = resultSet.getBoolean(7);

				Integer sleepUntilHeight = resultSet.getInt(8);
				if (sleepUntilHeight == 0 && resultSet.wasNull())
					sleepUntilHeight = null;

				boolean isFinished = resultSet.getBoolean(9);
				boolean hadFatalError = resultSet.getBoolean(10);
				boolean isFrozen = resultSet.getBoolean(11);

				Long frozenBalance = resultSet.getLong(12);
				if (frozenBalance == 0 && resultSet.wasNull())
					frozenBalance = null;

				Long sleepUntilMessageTimestamp = resultSet.getLong(13);
				if (sleepUntilMessageTimestamp == 0 && resultSet.wasNull())
					sleepUntilMessageTimestamp = null;

				String atAddress = resultSet.getString(14);

				list.add(new ATData(atAddress, creatorPublicKey, created, version, assetId, codeBytes, codeHash,
						isSleeping, sleepUntilHeight, isFinished, hadFatalError, isFrozen, frozenBalance,
						sleepUntilMessageTimestamp));
			} while ( resultSet.next());

			return list;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch AT from repository", e);
		}
	}

	@Override
	public boolean exists(String atAddress) throws DataException {
		try {
			return this.repository.exists("ATs", "AT_address = ?", atAddress);
		} catch (SQLException e) {
			throw new DataException("Unable to check for AT in repository", e);
		}
	}

	@Override
	public byte[] getCreatorPublicKey(String atAddress) throws DataException {
		String sql = "SELECT creator FROM ATs WHERE AT_address = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, atAddress)) {
			if (resultSet == null)
				return null;

			return resultSet.getBytes(1);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch AT creator's public key from repository", e);
		}
	}

	@Override
	public List<ATData> getAllExecutableATs() throws DataException {
		this.ensureRuntimeRowsExist();

		String sql = "SELECT ATs.AT_address, creator, created_when, version, asset_id, code_bytes, code_hash, "
				+ "ATRuntime.is_sleeping, ATRuntime.sleep_until_height, ATRuntime.had_fatal_error, "
				+ "ATRuntime.is_frozen, ATRuntime.frozen_balance, ATRuntime.sleep_until_message_timestamp "
				+ "FROM ATs "
				+ "JOIN ATRuntime ON ATRuntime.AT_address = ATs.AT_address "
				+ "WHERE ATRuntime.is_finished = false "
				+ "ORDER BY created_when ASC, ATs.AT_address DESC";

		List<ATData> executableATs = new ArrayList<>();

		ATExecInstrumentation inst = ATExecInstrumentation.peek();
		long tOpenStart = System.nanoTime();
		try (ResultSet resultSet = this.repository.checkedExecute(sql)) {
			long afterOpenNanos = System.nanoTime();
			if (inst != null)
				inst.exec_repo_fetch_executable_openResultSetNanos += afterOpenNanos - tOpenStart;
			long tIterStart = afterOpenNanos;
			if (resultSet == null) {
				if (inst != null)
					inst.exec_repo_fetch_executable_iterateRowsNanos += System.nanoTime() - tIterStart;
				return executableATs;
			}

			boolean isFinished = false;

			do {
				String atAddress = resultSet.getString(1);
				byte[] creatorPublicKey = resultSet.getBytes(2);
				long created = resultSet.getLong(3);
				int version = resultSet.getInt(4);
				long assetId = resultSet.getLong(5);
				byte[] codeBytes = resultSet.getBytes(6); // Actually BLOB
				byte[] codeHash = resultSet.getBytes(7);
				boolean isSleeping = resultSet.getBoolean(8);

				Integer sleepUntilHeight = resultSet.getInt(9);
				if (sleepUntilHeight == 0 && resultSet.wasNull())
					sleepUntilHeight = null;

				boolean hadFatalError = resultSet.getBoolean(10);
				boolean isFrozen = resultSet.getBoolean(11);

				Long frozenBalance = resultSet.getLong(12);
				if (frozenBalance == 0 && resultSet.wasNull())
					frozenBalance = null;

				Long sleepUntilMessageTimestamp = resultSet.getLong(13);
				if (sleepUntilMessageTimestamp == 0 && resultSet.wasNull())
					sleepUntilMessageTimestamp = null;

				ATData atData = new ATData(atAddress, creatorPublicKey, created, version, assetId, codeBytes, codeHash,
						isSleeping, sleepUntilHeight, isFinished, hadFatalError, isFrozen, frozenBalance,
						sleepUntilMessageTimestamp);

				executableATs.add(atData);
			} while (resultSet.next());

			if (inst != null)
				inst.exec_repo_fetch_executable_iterateRowsNanos += System.nanoTime() - tIterStart;
			return executableATs;

		} catch (SQLException e) {
			throw new DataException("Unable to fetch executable ATs from repository", e);
		}
	}

	@Override
	public List<ATData> getExecutableATs(int blockHeight) throws DataException {
		this.ensureExecutionQueueVerified();
		this.verifyExecutionQueueForHeight(blockHeight);

		// Query the derived scheduler instead of scanning every unfinished AT. The verifier above keeps fork safety by
		// comparing due rows against canonical runtime/cursor state before the queue is used.
		String sql = "SELECT ATs.AT_address, creator, created_when, version, asset_id, code_bytes, code_hash, "
				+ "ATRuntime.is_sleeping, ATRuntime.sleep_until_height, ATRuntime.had_fatal_error, "
				+ "ATRuntime.is_frozen, ATRuntime.frozen_balance, ATRuntime.sleep_until_message_timestamp "
				+ "FROM ATExecutionQueue "
				+ "JOIN ATs ON ATs.AT_address = ATExecutionQueue.AT_address "
				+ "JOIN ATRuntime ON ATRuntime.AT_address = ATs.AT_address "
				+ "WHERE ATExecutionQueue.next_height <= ? "
				+ "AND ATRuntime.is_finished = false "
				+ "ORDER BY ATs.created_when ASC, ATs.AT_address DESC";

		List<ATData> executableATs = new ArrayList<>();

		ATExecInstrumentation inst = ATExecInstrumentation.peek();
		long tOpenStart = System.nanoTime();
		try (ResultSet resultSet = this.repository.checkedExecute(sql, blockHeight)) {
			long afterOpenNanos = System.nanoTime();
			if (inst != null)
				inst.exec_repo_fetch_executable_openResultSetNanos += afterOpenNanos - tOpenStart;
			long tIterStart = afterOpenNanos;
			if (resultSet == null) {
				if (inst != null)
					inst.exec_repo_fetch_executable_iterateRowsNanos += System.nanoTime() - tIterStart;
				return executableATs;
			}

			boolean isFinished = false;

			do {
				String atAddress = resultSet.getString(1);
				byte[] creatorPublicKey = resultSet.getBytes(2);
				long created = resultSet.getLong(3);
				int version = resultSet.getInt(4);
				long assetId = resultSet.getLong(5);
				byte[] codeBytes = resultSet.getBytes(6); // Actually BLOB
				byte[] codeHash = resultSet.getBytes(7);
				boolean isSleeping = resultSet.getBoolean(8);

				Integer sleepUntilHeight = resultSet.getInt(9);
				if (sleepUntilHeight == 0 && resultSet.wasNull())
					sleepUntilHeight = null;

				boolean hadFatalError = resultSet.getBoolean(10);
				boolean isFrozen = resultSet.getBoolean(11);

				Long frozenBalance = resultSet.getLong(12);
				if (frozenBalance == 0 && resultSet.wasNull())
					frozenBalance = null;

				Long sleepUntilMessageTimestamp = resultSet.getLong(13);
				if (sleepUntilMessageTimestamp == 0 && resultSet.wasNull())
					sleepUntilMessageTimestamp = null;

				ATData atData = new ATData(atAddress, creatorPublicKey, created, version, assetId, codeBytes, codeHash,
						isSleeping, sleepUntilHeight, isFinished, hadFatalError, isFrozen, frozenBalance,
						sleepUntilMessageTimestamp);

				executableATs.add(atData);
			} while (resultSet.next());

			if (inst != null)
				inst.exec_repo_fetch_executable_iterateRowsNanos += System.nanoTime() - tIterStart;
			return executableATs;

		} catch (SQLException e) {
			throw new DataException("Unable to fetch queued executable ATs from repository", e);
		}
	}

	private void verifyExecutionQueueForHeight(int blockHeight) throws DataException {
		Set<String> canonicalDueATs = fetchCanonicalDueATs(blockHeight);
		Set<String> queuedDueATs = fetchQueuedDueATs(blockHeight);

		if (canonicalDueATs.equals(queuedDueATs))
			return;

		// The queue is a rebuildable acceleration structure, so a mismatch is repaired from canonical runtime/cursor
		// state. If repair still disagrees, fail validation instead of using a potentially wrong wake set.
		Set<String> affectedATs = new LinkedHashSet<>(canonicalDueATs);
		affectedATs.addAll(queuedDueATs);

		recomputeATExecutionQueueForATs(affectedATs);

		Set<String> repairedQueuedDueATs = fetchQueuedDueATs(blockHeight);
		if (!canonicalDueATs.equals(repairedQueuedDueATs))
			rebuildATExecutionQueue();

		repairedQueuedDueATs = fetchQueuedDueATs(blockHeight);
		if (!canonicalDueATs.equals(repairedQueuedDueATs))
			throw new DataException(String.format("AT execution queue mismatch at height %d after rebuild", blockHeight));
	}

	private Set<String> fetchCanonicalDueATs(int blockHeight) throws DataException {
		Set<String> dueATs = new LinkedHashSet<>();

		String noMessageSleepSql = "SELECT AT_address "
				+ "FROM ATRuntime "
				+ "WHERE is_finished = false "
				+ "AND sleep_until_message_timestamp IS NULL";

		String heightWakeSql = "SELECT AT_address "
				+ "FROM ATRuntime "
				+ "WHERE is_finished = false "
				+ "AND sleep_until_height IS NOT NULL "
				+ "AND sleep_until_height != 0 "
				+ "AND sleep_until_height <= ?";

		String messageWakeSql = "SELECT ATRuntime.AT_address "
				+ "FROM ATNextIncoming "
				+ "JOIN ATRuntime ON ATRuntime.AT_address = ATNextIncoming.AT_address "
				+ "WHERE ATRuntime.is_finished = false "
				+ "AND ATNextIncoming.block_height < ? "
				+ "AND ATNextIncoming.sleep_until_message_timestamp = ATRuntime.sleep_until_message_timestamp";

		try {
			addATAddresses(dueATs, this.repository.checkedExecute(noMessageSleepSql));
			addATAddresses(dueATs, this.repository.checkedExecute(heightWakeSql, blockHeight));
			addATAddresses(dueATs, this.repository.checkedExecute(messageWakeSql, blockHeight));
		} catch (SQLException e) {
			throw new DataException("Unable to fetch canonical due ATs", e);
		}

		return dueATs;
	}

	private Set<String> fetchQueuedDueATs(int blockHeight) throws DataException {
		Set<String> dueATs = new LinkedHashSet<>();
		String sql = "SELECT AT_address "
				+ "FROM ATExecutionQueue "
				+ "WHERE next_height <= ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, blockHeight)) {
			addATAddresses(dueATs, resultSet);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch queued due ATs", e);
		}

		return dueATs;
	}

	private static void addATAddresses(Set<String> atAddresses, ResultSet resultSet) throws SQLException {
		if (resultSet == null)
			return;

		try (resultSet) {
			do {
				atAddresses.add(resultSet.getString(1));
			} while (resultSet.next());
		}
	}

	private void ensureExecutionQueueVerified() throws DataException {
		if (executionQueueVerified)
			return;

		synchronized (HSQLDBATRepository.class) {
			if (executionQueueVerified)
				return;

			ensureRuntimeRowsExist();
			rebuildATNextIncoming();
			rebuildATExecutionQueue();
			executionQueueVerified = true;
		}
	}

	private void ensureRuntimeRowsExist() throws DataException {
		if (this.runtimeRowsVerified)
			return;

		String sql = "INSERT INTO ATRuntime (AT_address, is_sleeping, sleep_until_height, is_finished, had_fatal_error, "
				+ "is_frozen, frozen_balance, sleep_until_message_timestamp, current_state_height) "
				+ "SELECT ATs.AT_address, ATs.is_sleeping, ATs.sleep_until_height, ATs.is_finished, ATs.had_fatal_error, "
				+ "ATs.is_frozen, ATs.frozen_balance, ATs.sleep_until_message_timestamp, "
				+ "CASE "
					+ "WHEN ATCurrentState.height IS NOT NULL "
						+ "AND (ATs.current_state_height IS NULL OR ATCurrentState.height >= ATs.current_state_height) "
						+ "THEN ATCurrentState.height "
					+ "ELSE ATs.current_state_height "
				+ "END "
				+ "FROM ATs "
				+ "LEFT OUTER JOIN ATRuntime ON ATRuntime.AT_address = ATs.AT_address "
				+ "LEFT OUTER JOIN ATCurrentState ON ATCurrentState.AT_address = ATs.AT_address "
				+ "WHERE ATRuntime.AT_address IS NULL";

		try {
			this.repository.executeCheckedUpdate(sql);
			this.runtimeRowsVerified = true;
		} catch (SQLException e) {
			throw new DataException("Unable to verify AT runtime rows", e);
		}
	}

	@Override
	public List<ATData> getATsByFunctionality(byte[] codeHash, Boolean isExecutable, Integer limit, Integer offset, Boolean reverse) throws DataException {
		StringBuilder sql = new StringBuilder(512);
		List<Object> bindParams = new ArrayList<>();

		sql.append("SELECT ATs.AT_address, creator, created_when, version, asset_id, code_bytes, ")
				.append(runtimeColumn("is_sleeping")).append(", ")
				.append(runtimeColumn("sleep_until_height")).append(", ")
				.append(runtimeColumn("is_finished")).append(", ")
				.append(runtimeColumn("had_fatal_error")).append(", ")
				.append(runtimeColumn("is_frozen")).append(", ")
				.append(runtimeColumn("frozen_balance")).append(", ")
				.append(runtimeColumn("sleep_until_message_timestamp")).append(" ")
				.append("FROM ATs ")
				.append("LEFT OUTER JOIN ATRuntime ON ATRuntime.AT_address = ATs.AT_address ")
				.append("WHERE code_hash = ? ");
		bindParams.add(codeHash);

		if (isExecutable != null) {
			sql.append("AND ").append(runtimeColumn("is_finished")).append(" != ? ");
			bindParams.add(isExecutable);
		}

		sql.append("ORDER BY created_when ");
		if (reverse != null && reverse)
			sql.append("DESC");

		HSQLDBRepository.limitOffsetSql(sql, limit, offset);

		List<ATData> matchingATs = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql.toString(), bindParams.toArray())) {
			if (resultSet == null)
				return matchingATs;

			do {
				String atAddress = resultSet.getString(1);
				byte[] creatorPublicKey = resultSet.getBytes(2);
				long created = resultSet.getLong(3);
				int version = resultSet.getInt(4);
				long assetId = resultSet.getLong(5);
				byte[] codeBytes = resultSet.getBytes(6); // Actually BLOB
				boolean isSleeping = resultSet.getBoolean(7);

				Integer sleepUntilHeight = resultSet.getInt(8);
				if (sleepUntilHeight == 0 && resultSet.wasNull())
					sleepUntilHeight = null;

				boolean isFinished = resultSet.getBoolean(9);

				boolean hadFatalError = resultSet.getBoolean(10);
				boolean isFrozen = resultSet.getBoolean(11);

				Long frozenBalance = resultSet.getLong(12);
				if (frozenBalance == 0 && resultSet.wasNull())
					frozenBalance = null;

				Long sleepUntilMessageTimestamp = resultSet.getLong(13);
				if (sleepUntilMessageTimestamp == 0 && resultSet.wasNull())
					sleepUntilMessageTimestamp = null;

				ATData atData = new ATData(atAddress, creatorPublicKey, created, version, assetId, codeBytes, codeHash,
						isSleeping, sleepUntilHeight, isFinished, hadFatalError, isFrozen, frozenBalance,
						sleepUntilMessageTimestamp);

				matchingATs.add(atData);
			} while (resultSet.next());

			return matchingATs;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch matching ATs from repository", e);
		}
	}

	@Override
	public List<ATData> getAllATsByFunctionality(Set<ByteArray> codeHashes, Boolean isExecutable) throws DataException {
		StringBuilder sql = new StringBuilder(512);
		List<Object> bindParams = new ArrayList<>();

		sql.append("SELECT ATs.AT_address, creator, created_when, version, asset_id, code_bytes, ")
				.append(runtimeColumn("is_sleeping")).append(", ")
				.append(runtimeColumn("sleep_until_height")).append(", ")
				.append(runtimeColumn("is_finished")).append(", ")
				.append(runtimeColumn("had_fatal_error")).append(", ")
				.append(runtimeColumn("is_frozen")).append(", ")
				.append(runtimeColumn("frozen_balance")).append(", code_hash, ")
				.append(runtimeColumn("sleep_until_message_timestamp")).append(" ")
				.append("FROM ");

		// (VALUES (?), (?), ...) AS ATCodeHashes (code_hash)
		sql.append("(VALUES ");

		boolean isFirst = true;
		for (ByteArray codeHash : codeHashes) {
			if (!isFirst)
				sql.append(", ");
			else
				isFirst = false;

			sql.append("(CAST(? AS VARBINARY(256)))");
			bindParams.add(codeHash.value);
		}
		sql.append(") AS ATCodeHashes (code_hash) ");

		sql.append("JOIN ATs ON ATs.code_hash = ATCodeHashes.code_hash ");
		sql.append("LEFT OUTER JOIN ATRuntime ON ATRuntime.AT_address = ATs.AT_address ");

		if (isExecutable != null) {
			sql.append("AND ").append(runtimeColumn("is_finished")).append(" != ? ");
			bindParams.add(isExecutable);
		}

		List<ATData> matchingATs = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql.toString(), bindParams.toArray())) {
			if (resultSet == null)
				return matchingATs;

			do {
				String atAddress = resultSet.getString(1);
				byte[] creatorPublicKey = resultSet.getBytes(2);
				long created = resultSet.getLong(3);
				int version = resultSet.getInt(4);
				long assetId = resultSet.getLong(5);
				byte[] codeBytes = resultSet.getBytes(6); // Actually BLOB
				boolean isSleeping = resultSet.getBoolean(7);

				Integer sleepUntilHeight = resultSet.getInt(8);
				if (sleepUntilHeight == 0 && resultSet.wasNull())
					sleepUntilHeight = null;

				boolean isFinished = resultSet.getBoolean(9);

				boolean hadFatalError = resultSet.getBoolean(10);
				boolean isFrozen = resultSet.getBoolean(11);

				Long frozenBalance = resultSet.getLong(12);
				if (frozenBalance == 0 && resultSet.wasNull())
					frozenBalance = null;

				byte[] codeHash = resultSet.getBytes(13);
				Long sleepUntilMessageTimestamp = resultSet.getLong(14);

				ATData atData = new ATData(atAddress, creatorPublicKey, created, version, assetId, codeBytes, codeHash,
						isSleeping, sleepUntilHeight, isFinished, hadFatalError, isFrozen, frozenBalance, sleepUntilMessageTimestamp);

				matchingATs.add(atData);
			} while (resultSet.next());

			return matchingATs;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch matching ATs from repository", e);
		}
	}

	@Override
	public Integer getATCreationBlockHeight(String atAddress) throws DataException {
		String sql = "SELECT block_height "
				+ "FROM DeployATTransactions "
				+ "JOIN Transactions USING (signature) "
				+ "WHERE AT_address = ? "
				+ "LIMIT 1";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, atAddress)) {
			if (resultSet == null)
				return null;

			return resultSet.getInt(1);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch AT's creation block height from repository", e);
		}
	}

	@Override
	public void save(ATData atData) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("ATs");

		saveHelper.bind("AT_address", atData.getATAddress()).bind("creator", atData.getCreatorPublicKey()).bind("created_when", atData.getCreation())
				.bind("version", atData.getVersion()).bind("asset_id", atData.getAssetId())
				.bind("code_bytes", atData.getCodeBytes()).bind("code_hash", atData.getCodeHash())
				.bind("is_sleeping", atData.getIsSleeping()).bind("sleep_until_height", atData.getSleepUntilHeight())
				.bind("is_finished", atData.getIsFinished()).bind("had_fatal_error", atData.getHadFatalError()).bind("is_frozen", atData.getIsFrozen())
				.bind("frozen_balance", atData.getFrozenBalance()).bind("sleep_until_message_timestamp", atData.getSleepUntilMessageTimestamp());

		try {
			saveHelper.execute(this.repository);
			saveRuntimeState(atData, null);
		} catch (SQLException e) {
			throw new DataException("Unable to save AT into repository", e);
		}

		recomputeATExecutionQueueForAT(atData.getATAddress());
	}

	@Override
	public void updateRuntimeState(ATData atData) throws DataException {
		String sql = "UPDATE ATs SET "
				+ "is_sleeping = ?, sleep_until_height = ?, is_finished = ?, had_fatal_error = ?, "
				+ "is_frozen = ?, frozen_balance = ?, sleep_until_message_timestamp = ? "
				+ "WHERE AT_address = ?";

		try {
			saveRuntimeState(atData, null);
			this.repository.executeCheckedUpdate(sql,
					atData.getIsSleeping(), atData.getSleepUntilHeight(), atData.getIsFinished(),
					atData.getHadFatalError(), atData.getIsFrozen(), atData.getFrozenBalance(),
					atData.getSleepUntilMessageTimestamp(), atData.getATAddress());
		} catch (SQLException e) {
			throw new DataException("Unable to update AT runtime state in repository", e);
		}
	}

	@Override
	public void updateRuntimeStates(Collection<ATData> atDataList) throws DataException {
		updateRuntimeStates(atDataList, null);
	}

	@Override
	public void updateRuntimeStates(Collection<ATData> atDataList, Integer currentStateHeight) throws DataException {
		if (atDataList == null || atDataList.isEmpty())
			return;

		boolean updateCurrentStateHeight = currentStateHeight != null;
		String sql = updateCurrentStateHeight
				? "UPDATE ATRuntime SET is_sleeping = ?, sleep_until_height = ?, is_finished = ?, had_fatal_error = ?, "
					+ "is_frozen = ?, frozen_balance = ?, sleep_until_message_timestamp = ?, current_state_height = ? "
					+ "WHERE AT_address = ?"
				: "UPDATE ATRuntime SET is_sleeping = ?, sleep_until_height = ?, is_finished = ?, had_fatal_error = ?, "
					+ "is_frozen = ?, frozen_balance = ?, sleep_until_message_timestamp = ? "
					+ "WHERE AT_address = ?";

		Lock readLock = HSQLDBRepository.CHECKPOINT_GATE.readLock();
		readLock.lock();
		try {
			this.repository.markTransactionStarted();

			List<ATData> atData = new ArrayList<>(atDataList);
			List<ATData> missingRuntimeRows = new ArrayList<>();

			try (PreparedStatement preparedStatement = this.repository.prepareStatement(sql)) {
				for (ATData atDataItem : atData) {
					bindRuntimeStateUpdate(preparedStatement, atDataItem, currentStateHeight);
					preparedStatement.addBatch();
				}

				int[] updateCounts = preparedStatement.executeBatch();
				for (int i = 0; i < updateCounts.length; ++i)
					// JDBC can return SUCCESS_NO_INFO for successful batch updates, so only exact zero means missing.
					if (updateCounts[i] == 0)
						missingRuntimeRows.add(atData.get(i));
			}

			for (ATData missingRuntimeRow : missingRuntimeRows)
				saveRuntimeState(missingRuntimeRow, currentStateHeight);
		} catch (SQLException e) {
			throw new DataException("Unable to batch update AT runtime states", this.repository.examineException(e));
		} finally {
			readLock.unlock();
		}
	}

	@Override
	public void delete(String atAddress) throws DataException {
		try {
			this.repository.delete("ATNextIncoming", "AT_address = ?", atAddress);
			this.repository.delete("ATIncomingTransactions", "AT_address = ?", atAddress);
			this.repository.delete("ATExecutionQueue", "AT_address = ?", atAddress);
			this.repository.delete("ATCurrentState", "AT_address = ?", atAddress);
			this.repository.delete("ATRuntime", "AT_address = ?", atAddress);
			this.repository.delete("ATs", "AT_address = ?", atAddress);
			// AT States also deleted via ON DELETE CASCADE
		} catch (SQLException e) {
			throw new DataException("Unable to delete AT from repository", e);
		}
	}

	private void saveRuntimeState(ATData atData, Integer currentStateHeight) throws SQLException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("ATRuntime");
		saveHelper.bind("AT_address", atData.getATAddress())
				.bind("is_sleeping", atData.getIsSleeping()).bind("sleep_until_height", atData.getSleepUntilHeight())
				.bind("is_finished", atData.getIsFinished()).bind("had_fatal_error", atData.getHadFatalError())
				.bind("is_frozen", atData.getIsFrozen()).bind("frozen_balance", atData.getFrozenBalance())
				.bind("sleep_until_message_timestamp", atData.getSleepUntilMessageTimestamp());

		if (currentStateHeight != null)
			saveHelper.bind("current_state_height", currentStateHeight);

		saveHelper.execute(this.repository);
	}

	private static void bindRuntimeState(PreparedStatement preparedStatement, ATData atData,
			Integer currentStateHeight) throws SQLException {
		bindRuntimeState(preparedStatement, atData, currentStateHeight, 1);
	}

	private static void bindRuntimeState(PreparedStatement preparedStatement, ATData atData,
			Integer currentStateHeight, int offset) throws SQLException {
		preparedStatement.setString(offset, atData.getATAddress());
		preparedStatement.setBoolean(offset + 1, atData.getIsSleeping());
		preparedStatement.setObject(offset + 2, atData.getSleepUntilHeight());
		preparedStatement.setBoolean(offset + 3, atData.getIsFinished());
		preparedStatement.setBoolean(offset + 4, atData.getHadFatalError());
		preparedStatement.setBoolean(offset + 5, atData.getIsFrozen());
		preparedStatement.setObject(offset + 6, atData.getFrozenBalance());
		preparedStatement.setObject(offset + 7, atData.getSleepUntilMessageTimestamp());

		if (currentStateHeight != null)
			preparedStatement.setInt(offset + 8, currentStateHeight);
	}

	private static void bindRuntimeStateUpdate(PreparedStatement preparedStatement, ATData atData,
			Integer currentStateHeight) throws SQLException {
		preparedStatement.setBoolean(1, atData.getIsSleeping());
		preparedStatement.setObject(2, atData.getSleepUntilHeight());
		preparedStatement.setBoolean(3, atData.getIsFinished());
		preparedStatement.setBoolean(4, atData.getHadFatalError());
		preparedStatement.setBoolean(5, atData.getIsFrozen());
		preparedStatement.setObject(6, atData.getFrozenBalance());
		preparedStatement.setObject(7, atData.getSleepUntilMessageTimestamp());

		if (currentStateHeight != null) {
			preparedStatement.setInt(8, currentStateHeight);
			preparedStatement.setString(9, atData.getATAddress());
		} else {
			preparedStatement.setString(8, atData.getATAddress());
		}
	}

	// AT State

	private byte[] verifyAndResolveStateData(String atAddress, int height, byte[] stateHash, byte[] blobStateData,
			byte[] legacyStateData, boolean requireStateData) throws DataException {
		/*
		 * During rollout both storage paths can exist. Always verify bytes against ATStates.state_hash, and when both
		 * blob and legacy bytes exist require them to be identical so the content-addressed path cannot silently diverge.
		 */
		if (blobStateData != null) {
			byte[] blobStateHash = Crypto.digest(blobStateData);
			if (!Arrays.equals(blobStateHash, stateHash))
				throw new DataException(String.format("AT state blob hash mismatch for %s at height %d", atAddress, height));

			if (legacyStateData != null && !Arrays.equals(blobStateData, legacyStateData))
				throw new DataException(String.format("AT state blob differs from legacy state data for %s at height %d", atAddress, height));

			return blobStateData;
		}

		if (legacyStateData != null) {
			byte[] legacyStateHash = Crypto.digest(legacyStateData);
			if (!Arrays.equals(legacyStateHash, stateHash))
				throw new DataException(String.format("Legacy AT state data hash mismatch for %s at height %d", atAddress, height));

			return legacyStateData;
		}

		if (requireStateData)
			throw new DataException(String.format("Missing AT state data for %s at height %d", atAddress, height));

		return null;
	}

	@Override
	public ATStateData getATStateAtHeight(String atAddress, int height) throws DataException {
		int atTrimHeight = getAtTrimHeight();

		String sql = "SELECT ATStatesData.state_data, ATStateBlobs.state_data, state_hash, fees, is_initial, sleep_until_message_timestamp "
				+ "FROM ATStates "
				+ "LEFT OUTER JOIN ATStatesData USING (AT_address, height) "
				+ "LEFT OUTER JOIN ATStateBlobs USING (state_hash) "
				+ "WHERE ATStates.AT_address = ? AND ATStates.height = ? "
				+ "LIMIT 1";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, atAddress, height)) {
			if (resultSet == null)
				return null;

			byte[] legacyStateData = resultSet.getBytes(1); // Actually BLOB
			byte[] blobStateData = resultSet.getBytes(2);
			byte[] stateHash = resultSet.getBytes(3);
			long fees = resultSet.getLong(4);
			boolean isInitial = resultSet.getBoolean(5);

			Long sleepUntilMessageTimestamp = resultSet.getLong(6);
			if (sleepUntilMessageTimestamp == 0 && resultSet.wasNull())
				sleepUntilMessageTimestamp = null;

			byte[] stateData = null;
			if (legacyStateData != null) {
				stateData = verifyAndResolveStateData(atAddress, height, stateHash, blobStateData, legacyStateData, false);
			} else if (blobStateData != null && height >= atTrimHeight) {
				stateData = verifyAndResolveStateData(atAddress, height, stateHash, blobStateData, null, false);
			}

			return new ATStateData(atAddress, height, stateData, stateHash, fees, isInitial, sleepUntilMessageTimestamp);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch AT state from repository", e);
		}
	}

	@Override
	public ATStateData getLatestATState(String atAddress) throws DataException {
		String sql = "SELECT height, ATStateBlobs.state_data, ATStatesData.state_data, state_hash, fees, is_initial, sleep_until_message_timestamp "
				+ "FROM ATStates "
				+ "LEFT OUTER JOIN ATStateBlobs USING (state_hash) "
				+ "LEFT OUTER JOIN ATStatesData USING (AT_address, height) "
				+ "WHERE ATStates.AT_address = ? "
				// Order by AT_address and height to use compound primary key as index
				// Both must be the same direction (DESC) also
				+ "ORDER BY ATStates.AT_address DESC, ATStates.height DESC "
				+ "LIMIT 1 ";

		ATExecInstrumentation inst = ATExecInstrumentation.peek();
		long tOpenStart = System.nanoTime();
		try (ResultSet resultSet = this.repository.checkedExecute(sql, atAddress)) {
			long afterOpenNanos = System.nanoTime();
			if (inst != null)
				inst.exec_repo_getLatest_openResultSetNanos += afterOpenNanos - tOpenStart;
			long tReadStart = afterOpenNanos;

			if (resultSet == null) {
				if (inst != null)
					inst.exec_repo_getLatest_readRowNanos += System.nanoTime() - tReadStart;
				return null;
			}

			int height = resultSet.getInt(1);
			byte[] blobStateData = resultSet.getBytes(2); // Actually BLOB
			byte[] legacyStateData = resultSet.getBytes(3);
			byte[] stateHash = resultSet.getBytes(4);
			long fees = resultSet.getLong(5);
			boolean isInitial = resultSet.getBoolean(6);

			Long sleepUntilMessageTimestamp = resultSet.getLong(7);
			if (sleepUntilMessageTimestamp == 0 && resultSet.wasNull())
				sleepUntilMessageTimestamp = null;

			byte[] stateData = verifyAndResolveStateData(atAddress, height, stateHash, blobStateData, legacyStateData, true);

			if (inst != null)
				inst.exec_repo_getLatest_readRowNanos += System.nanoTime() - tReadStart;
			return new ATStateData(atAddress, height, stateData, stateHash, fees, isInitial, sleepUntilMessageTimestamp);

		} catch (SQLException e) {
			throw new DataException("Unable to fetch latest AT state from repository", e);
		}
	}

	@Override
	public List<ATStateData> getLatestATStates(List<String> atAddresses) throws DataException{
		if (atAddresses.isEmpty())
			return new ArrayList<>(0);

		String sql = "SELECT RequestedATs.AT_address, LatestATStates.height, LatestATStates.blob_state_data, "
				+ "LatestATStates.legacy_state_data, "
				+ "LatestATStates.state_hash, LatestATStates.fees, LatestATStates.is_initial, "
				+ "LatestATStates.sleep_until_message_timestamp "
				+ "FROM (VALUES "
				+ String.join(", ", Collections.nCopies(atAddresses.size(), "(?)"))
				+ ") AS RequestedATs (AT_address) "
				+ "CROSS JOIN LATERAL ("
					+ "SELECT height, ATStateBlobs.state_data AS blob_state_data, ATStatesData.state_data AS legacy_state_data, "
					+ "state_hash, fees, is_initial, sleep_until_message_timestamp "
					+ "FROM ATStates "
					+ "LEFT OUTER JOIN ATStateBlobs USING (state_hash) "
					+ "LEFT OUTER JOIN ATStatesData USING (AT_address, height) "
					+ "WHERE ATStates.AT_address = RequestedATs.AT_address "
					+ "ORDER BY ATStates.AT_address DESC, ATStates.height DESC "
					+ "LIMIT 1"
				+ ") AS LatestATStates";

		List<ATStateData> stateDataList;

		try (ResultSet resultSet = this.repository.checkedExecute(sql, atAddresses.toArray(new String[atAddresses.size()]))) {
			if (resultSet == null)
				return new ArrayList<>(0);

			stateDataList = new ArrayList<>();

			do {
				String atAddress = resultSet.getString(1);
				int height = resultSet.getInt(2);
				byte[] blobStateData = resultSet.getBytes(3); // Actually BLOB
				byte[] legacyStateData = resultSet.getBytes(4);
				byte[] stateHash = resultSet.getBytes(5);
				long fees = resultSet.getLong(6);
				boolean isInitial = resultSet.getBoolean(7);

				Long sleepUntilMessageTimestamp = resultSet.getLong(8);
				if (sleepUntilMessageTimestamp == 0 && resultSet.wasNull())
					sleepUntilMessageTimestamp = null;

				byte[] stateData = verifyAndResolveStateData(atAddress, height, stateHash, blobStateData, legacyStateData, true);

				stateDataList.add(new ATStateData(atAddress, height, stateData, stateHash, fees, isInitial, sleepUntilMessageTimestamp));
			} while( resultSet.next());
		} catch (SQLException e) {
			throw new DataException("Unable to fetch latest AT state from repository", e);
		}

		return stateDataList;
	}

	@Override
	public List<ATStateData> getCurrentATStates(List<String> atAddresses) throws DataException {
		if (atAddresses.isEmpty())
			return new ArrayList<>(0);

		return getCurrentATStatesDirect(atAddresses);
	}

	private List<ATStateData> getCurrentATStatesDirect(List<String> atAddresses) throws DataException {
		if (atAddresses.isEmpty())
			return new ArrayList<>(0);

		/*
		 * Current-state lookup is the execution hot path. Resolve bytes through ATStateBlobs first, with legacy
		 * ATStatesData still joined for rollout verification/fallback. Canonical state identity remains the
		 * ATStates.state_hash row, not the pointer/cache tables.
		 */
		String sql = "SELECT RequestedATs.AT_address, CurrentATStates.height, "
				+ "CurrentATStates.blob_state_data, CurrentATStates.legacy_state_data, "
				+ "CurrentATStates.state_hash, CurrentATStates.fees, CurrentATStates.is_initial, "
				+ "CurrentATStates.sleep_until_message_timestamp "
				+ "FROM (VALUES "
				+ String.join(", ", Collections.nCopies(atAddresses.size(), "(?)"))
				+ ") AS RequestedATs (AT_address) "
				+ "CROSS JOIN LATERAL ("
					+ "SELECT height, ATStateBlobs.state_data AS blob_state_data, "
					+ "ATStatesData.state_data AS legacy_state_data, "
					+ "ATStates.state_hash, fees, is_initial, sleep_until_message_timestamp "
					+ "FROM ATStates "
					+ "LEFT OUTER JOIN ATStateBlobs USING (state_hash) "
					+ "LEFT OUTER JOIN ATStatesData USING (AT_address, height) "
					+ "WHERE ATStates.AT_address = RequestedATs.AT_address "
					+ "AND (ATStateBlobs.state_hash IS NOT NULL OR ATStatesData.AT_address IS NOT NULL) "
					+ "ORDER BY ATStates.AT_address DESC, ATStates.height DESC "
					+ "LIMIT 1"
				+ ") AS CurrentATStates";

		List<ATStateData> stateDataList = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, atAddresses.toArray(new String[atAddresses.size()]))) {
			if (resultSet == null)
				return stateDataList;

			do {
				String atAddress = resultSet.getString(1);
				int height = resultSet.getInt(2);
				byte[] blobStateData = resultSet.getBytes(3);
				byte[] legacyStateData = resultSet.getBytes(4);
				byte[] stateHash = resultSet.getBytes(5);
				long fees = resultSet.getLong(6);
				boolean isInitial = resultSet.getBoolean(7);

				Long sleepUntilMessageTimestamp = resultSet.getLong(8);
				if (sleepUntilMessageTimestamp == 0 && resultSet.wasNull())
					sleepUntilMessageTimestamp = null;

				byte[] stateData = verifyAndResolveStateData(atAddress, height, stateHash, blobStateData, legacyStateData, true);

				stateDataList.add(new ATStateData(atAddress, height, stateData, stateHash, fees, isInitial, sleepUntilMessageTimestamp));
			} while (resultSet.next());
		} catch (SQLException e) {
			throw new DataException("Unable to fetch current AT states from repository", e);
		}

		return stateDataList;
	}

	@Override
	public void rebuildATCurrentStates() throws DataException {
		try {
			// Rebuild from retained canonical ATStates and any available state bytes source.
			this.repository.executeCheckedUpdate("DELETE FROM ATCurrentState");
			this.repository.executeCheckedUpdate("INSERT INTO ATCurrentState (AT_address, height) "
					+ "SELECT AT_address, MAX(height) "
					+ "FROM ATStates "
					+ "LEFT OUTER JOIN ATStateBlobs USING (state_hash) "
					+ "LEFT OUTER JOIN ATStatesData USING (AT_address, height) "
					+ "WHERE ATStateBlobs.state_hash IS NOT NULL OR ATStatesData.AT_address IS NOT NULL "
					+ "GROUP BY AT_address");
			this.repository.executeCheckedUpdate("UPDATE ATs SET current_state_height = ("
					+ "SELECT height FROM ATCurrentState WHERE ATCurrentState.AT_address = ATs.AT_address)");
			this.repository.executeCheckedUpdate("UPDATE ATRuntime SET current_state_height = ("
					+ "SELECT height FROM ATCurrentState WHERE ATCurrentState.AT_address = ATRuntime.AT_address)");
		} catch (SQLException e) {
			throw new DataException("Unable to rebuild AT current state pointer cache", e);
		}
	}

	@Override
	public void rebuildATStateBlobs() throws DataException {
		// ATStateBlobs is content-addressed storage derived from legacy ATStatesData during rollout.
		// Rebuilding is idempotent because duplicate hashes are ignored.
		String selectSql = "SELECT ATStates.state_hash, ATStatesData.state_data, ATStates.height "
				+ "FROM ATStates "
				+ "JOIN ATStatesData USING (AT_address, height)";

		String insertSql = "INSERT INTO ATStateBlobs "
				+ "(state_hash, state_data, created_height, state_data_length) VALUES (?, ?, ?, ?) "
				+ "ON DUPLICATE KEY UPDATE state_hash = state_hash";

		Lock readLock = HSQLDBRepository.CHECKPOINT_GATE.readLock();
		readLock.lock();
		try {
			this.repository.markTransactionStarted();

			try (ResultSet resultSet = this.repository.checkedExecute(selectSql);
					PreparedStatement preparedStatement = this.repository.prepareStatement(insertSql)) {
				if (resultSet == null)
					return;

				do {
					byte[] stateData = resultSet.getBytes(2);
					preparedStatement.setBytes(1, resultSet.getBytes(1));
					preparedStatement.setBytes(2, stateData);
					preparedStatement.setInt(3, resultSet.getInt(3));
					preparedStatement.setInt(4, stateData.length);
					preparedStatement.addBatch();
				} while (resultSet.next());

				preparedStatement.executeBatch();
			}
		} catch (SQLException e) {
			throw new DataException("Unable to rebuild AT state blobs", this.repository.examineException(e));
		} finally {
			readLock.unlock();
		}
	}

	@Override
	public int getATStateBlobCount() throws DataException {
		String sql = "SELECT COUNT(*) FROM ATStateBlobs";

		try (ResultSet resultSet = this.repository.checkedExecute(sql)) {
			if (resultSet == null)
				return 0;

			return resultSet.getInt(1);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch AT state blob count", e);
		}
	}

	@Override
	public void updateCurrentATState(String atAddress, int height) throws DataException {
		HSQLDBSaver saveHelper = new HSQLDBSaver("ATCurrentState");
		saveHelper.bind("AT_address", atAddress)
				.bind("height", height);

		try {
			saveHelper.execute(this.repository);
			setCurrentStateHeight(atAddress, height);
		} catch (SQLException e) {
			throw new DataException("Unable to update AT current state pointer", e);
		}
	}

	@Override
	public void updateCurrentATStates(Collection<String> atAddresses, int height) throws DataException {
		if (atAddresses == null || atAddresses.isEmpty())
			return;

		// Keep all three current-height copies aligned: ATCurrentState is rebuildable, while ATs/ATRuntime keep the
		// latest pointer near the hot runtime rows to avoid repeated max-height lookups.
		String currentStateSql = "INSERT INTO ATCurrentState (AT_address, height) VALUES (?, ?) "
				+ "ON DUPLICATE KEY UPDATE AT_address = ?, height = ?";
		String atsSql = "UPDATE ATs SET current_state_height = ? WHERE AT_address = ?";
		String runtimeSql = "UPDATE ATRuntime SET current_state_height = ? WHERE AT_address = ?";

		Lock readLock = HSQLDBRepository.CHECKPOINT_GATE.readLock();
		readLock.lock();
		try {
			this.repository.markTransactionStarted();

			Set<String> uniqueAtAddresses = new LinkedHashSet<>(atAddresses);
			try (PreparedStatement preparedStatement = this.repository.prepareStatement(currentStateSql)) {
				for (String atAddress : uniqueAtAddresses) {
					preparedStatement.setString(1, atAddress);
					preparedStatement.setInt(2, height);
					preparedStatement.setString(3, atAddress);
					preparedStatement.setInt(4, height);
					preparedStatement.addBatch();
				}

				preparedStatement.executeBatch();
			}

			try (PreparedStatement preparedStatement = this.repository.prepareStatement(atsSql)) {
				for (String atAddress : uniqueAtAddresses) {
					preparedStatement.setInt(1, height);
					preparedStatement.setString(2, atAddress);
					preparedStatement.addBatch();
				}

				preparedStatement.executeBatch();
			}

			try (PreparedStatement preparedStatement = this.repository.prepareStatement(runtimeSql)) {
				for (String atAddress : uniqueAtAddresses) {
					preparedStatement.setInt(1, height);
					preparedStatement.setString(2, atAddress);
					preparedStatement.addBatch();
				}

				preparedStatement.executeBatch();
			}
		} catch (SQLException e) {
			throw new DataException("Unable to batch update AT current state pointers", this.repository.examineException(e));
		} finally {
			readLock.unlock();
		}
	}

	@Override
	public void revertCurrentATState(String atAddress) throws DataException {
		String sql = "SELECT height "
				+ "FROM ATStates "
				+ "WHERE ATStates.AT_address = ? "
				+ "ORDER BY ATStates.AT_address DESC, ATStates.height DESC "
				+ "LIMIT 1";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, atAddress)) {
			Integer height = null;
			if (resultSet != null) {
				height = resultSet.getInt(1);
				if (height == 0 && resultSet.wasNull())
					height = null;
			}

			if (height == null) {
				this.repository.delete("ATCurrentState", "AT_address = ?", atAddress);
				setCurrentStateHeight(atAddress, null);
			} else {
				updateCurrentATState(atAddress, height);
			}
		} catch (SQLException e) {
			throw new DataException("Unable to recompute AT current state pointer", e);
		}
	}

	private void setCurrentATStateFromPreviousHeight(String atAddress, Integer previousHeight, boolean isInitial) throws DataException, SQLException {
		// Orphan rollback can usually trust ATStates.previous_height and avoid a max-height scan.
		if (previousHeight != null) {
			updateCurrentATState(atAddress, previousHeight);
			return;
		}

		if (isInitial) {
			this.repository.delete("ATCurrentState", "AT_address = ?", atAddress);
			setCurrentStateHeight(atAddress, null);
			return;
		}

		revertCurrentATState(atAddress);
	}

	private DeletedATStatePointerInfo fetchDeletedATStatePointerInfo(String atAddress, int height) throws DataException {
		// Compare all pointer copies before deleting the row; if a newer pointer already exists, the orphaned row is not
		// the active state and the current pointer should be left alone.
		String sql = "SELECT previous_height, is_initial, "
				+ "(SELECT MAX(pointer_height) FROM ("
				+ "SELECT current_state_height AS pointer_height FROM ATRuntime WHERE AT_address = ? AND current_state_height IS NOT NULL "
				+ "UNION ALL "
				+ "SELECT current_state_height AS pointer_height FROM ATs WHERE AT_address = ? AND current_state_height IS NOT NULL "
				+ "UNION ALL "
				+ "SELECT height AS pointer_height FROM ATCurrentState WHERE AT_address = ?"
				+ ") AS CurrentPointers) AS current_height "
				+ "FROM ATStates WHERE AT_address = ? AND height = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, atAddress, atAddress, atAddress, atAddress, height)) {
			if (resultSet == null)
				return null;

			Integer previousHeight = resultSet.getInt(1);
			if (previousHeight == 0 && resultSet.wasNull())
				previousHeight = null;

			Integer currentHeight = resultSet.getInt(3);
			if (currentHeight == 0 && resultSet.wasNull())
				currentHeight = null;

			return new DeletedATStatePointerInfo(previousHeight, resultSet.getBoolean(2), currentHeight);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch deleted AT state pointer info", e);
		}
	}

	private static class DeletedATStatePointerInfo {
		private final Integer previousHeight;
		private final boolean isInitial;
		private final Integer currentHeight;

		private DeletedATStatePointerInfo(Integer previousHeight, boolean isInitial, Integer currentHeight) {
			this.previousHeight = previousHeight;
			this.isInitial = isInitial;
			this.currentHeight = currentHeight;
		}
	}

	private void setCurrentStateHeight(String atAddress, Integer height) throws SQLException {
		this.repository.executeCheckedUpdate("UPDATE ATRuntime SET current_state_height = ? WHERE AT_address = ?", height, atAddress);
		this.repository.executeCheckedUpdate("UPDATE ATs SET current_state_height = ? WHERE AT_address = ?", height, atAddress);
	}

	@Override
	public List<ATStateData> getMatchingFinalATStates(byte[] codeHash, byte[] buyerPublicKey, byte[] sellerPublicKey, Boolean isFinished,
			  Integer dataByteOffset, Long expectedValue, Integer minimumFinalHeight,
			  Integer limit, Integer offset, Boolean reverse) throws DataException {
		StringBuilder sql = new StringBuilder(1024);
		List<Object> bindParams = new ArrayList<>();

		sql.append("SELECT ATs.AT_address, height, state_data, state_hash, fees, is_initial, FinalATStates.sleep_until_message_timestamp "
				+ "FROM ATs "
				+ "LEFT OUTER JOIN ATRuntime ON ATRuntime.AT_address = ATs.AT_address "
				+ "CROSS JOIN LATERAL("
					+ "SELECT height, COALESCE(ATStateBlobs.state_data, ATStatesData.state_data) AS state_data, "
					+ "ATStates.state_hash, fees, is_initial, sleep_until_message_timestamp "
					+ "FROM ATStates "
					+ "LEFT OUTER JOIN ATStateBlobs USING (state_hash) "
					+ "LEFT OUTER JOIN ATStatesData USING (AT_address, height) "
					+ "WHERE ATStates.AT_address = ATs.AT_address "
					+ "AND (ATStateBlobs.state_hash IS NOT NULL OR ATStatesData.AT_address IS NOT NULL) ");

		if (minimumFinalHeight != null) {
			sql.append("AND ATStates.height >= ? ");
			bindParams.add(minimumFinalHeight);
		}

		// Order by AT_address and height to use compound primary key as index
		// Both must be the same direction (DESC) also
		sql.append("ORDER BY ATStates.height DESC LIMIT 1) AS FinalATStates ");

		// Optional JOIN with ATTRANSACTIONS for buyerAddress
		if (buyerPublicKey != null && buyerPublicKey.length > 0) {
			sql.append("JOIN ATTRANSACTIONS tx ON tx.at_address = ATs.AT_address ");
		}

		sql.append("WHERE ATs.code_hash = ? ");
		bindParams.add(codeHash);

		if (isFinished != null) {
			sql.append("AND ").append(runtimeColumn("is_finished")).append(" = ? ");
			bindParams.add(isFinished);
		}

		if (dataByteOffset != null && expectedValue != null) {
			sql.append("AND SUBSTRING(state_data FROM ? FOR 8) = ? ");

			// We convert our long on Java-side to control endian
			byte[] rawExpectedValue = Longs.toByteArray(expectedValue);

			// SQL binary data offsets start at 1
			bindParams.add(dataByteOffset + 1);
			bindParams.add(rawExpectedValue);
		}

		if (buyerPublicKey != null && buyerPublicKey.length > 0 ) {
			// the buyer must be the recipient of the transaction and not the creator of the AT
			sql.append("AND tx.recipient = ? AND ATs.creator != ? ");

			bindParams.add(Crypto.toAddress(buyerPublicKey));
			bindParams.add(buyerPublicKey);
		}


		if (sellerPublicKey != null && sellerPublicKey.length > 0) {
			sql.append("AND ATs.creator = ? ");
			bindParams.add(sellerPublicKey);
		}

		sql.append(" ORDER BY FinalATStates.height ");
		if (reverse != null && reverse)
			sql.append("DESC");

		HSQLDBRepository.limitOffsetSql(sql, limit, offset);

		List<ATStateData> atStates = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql.toString(), bindParams.toArray())) {
			if (resultSet == null)
				return atStates;

			do {
				String atAddress = resultSet.getString(1);
				int height = resultSet.getInt(2);
				byte[] stateData = resultSet.getBytes(3); // Actually BLOB
				byte[] stateHash = resultSet.getBytes(4);
				long fees = resultSet.getLong(5);
				boolean isInitial = resultSet.getBoolean(6);

				Long sleepUntilMessageTimestamp = resultSet.getLong(7);
				if (sleepUntilMessageTimestamp == 0 && resultSet.wasNull())
					sleepUntilMessageTimestamp = null;

				ATStateData atStateData = new ATStateData(atAddress, height, stateData, stateHash, fees, isInitial, sleepUntilMessageTimestamp);

				atStates.add(atStateData);
			} while (resultSet.next());

			return atStates;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch matching AT states from repository", e);
		}
	}

	@Override
	public List<ATStateData> getMatchingFinalATStatesQuorum(byte[] codeHash, Boolean isFinished,
			Integer dataByteOffset, Long expectedValue,
			int minimumCount, int maximumCount, long minimumPeriod) throws DataException {
		// We need most recent entry first so we can use its timestamp to slice further results
		List<ATStateData> mostRecentStates = this.getMatchingFinalATStates(codeHash, null, null, isFinished,
				dataByteOffset, expectedValue, null,
				1, 0, true);

		if (mostRecentStates == null)
			return null;

		if (mostRecentStates.isEmpty())
			return mostRecentStates;

		ATStateData mostRecentState = mostRecentStates.get(0);

		StringBuilder sql = new StringBuilder(1024);
		List<Object> bindParams = new ArrayList<>();

		sql.append("SELECT ATs.AT_address, height, state_data, state_hash, fees, is_initial, sleep_until_message_timestamp "
				+ "FROM ATs "
				+ "LEFT OUTER JOIN ATRuntime ON ATRuntime.AT_address = ATs.AT_address "
				+ "CROSS JOIN LATERAL("
					+ "SELECT height, COALESCE(ATStateBlobs.state_data, ATStatesData.state_data) AS state_data, "
					+ "ATStates.state_hash, fees, is_initial, sleep_until_message_timestamp "
					+ "FROM ATStates "
					+ "LEFT OUTER JOIN ATStateBlobs USING (state_hash) "
					+ "LEFT OUTER JOIN ATStatesData USING (AT_address, height) "
					+ "WHERE ATStates.AT_address = ATs.AT_address "
					+ "AND (ATStateBlobs.state_hash IS NOT NULL OR ATStatesData.AT_address IS NOT NULL) ");

		// Order by AT_address and height to use compound primary key as index
		// Both must be the same direction (DESC) also
		sql.append("ORDER BY ATStates.AT_address DESC, ATStates.height DESC "
					+ "LIMIT 1 "
				+ ") AS FinalATStates "
				+ "WHERE code_hash = ? ");
		bindParams.add(codeHash);

		if (isFinished != null) {
			sql.append("AND ").append(runtimeColumn("is_finished")).append(" = ? ");
			bindParams.add(isFinished);
		}

		if (dataByteOffset != null && expectedValue != null) {
			sql.append("AND SUBSTRING(state_data FROM ? FOR 8) = ? ");

			// We convert our long on Java-side to control endian
			byte[] rawExpectedValue = Longs.toByteArray(expectedValue);

			// SQL binary data offsets start at 1
			bindParams.add(dataByteOffset + 1);
			bindParams.add(rawExpectedValue);
		}

		// Slice so that we meet both minimumCount and minimumPeriod
		int minimumHeight = mostRecentState.getHeight() - (int) (minimumPeriod / 60 * 1000L); // XXX assumes 60 second blocks

		sql.append("AND (FinalATStates.height >= ? OR ROWNUM() < ?) ");
		bindParams.add(minimumHeight);
		bindParams.add(minimumCount);

		sql.append("ORDER BY FinalATStates.height DESC LIMIT ?");
		bindParams.add(maximumCount);

		List<ATStateData> atStates = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql.toString(), bindParams.toArray())) {
			if (resultSet == null)
				return atStates;

			do {
				String atAddress = resultSet.getString(1);
				int height = resultSet.getInt(2);
				byte[] stateData = resultSet.getBytes(3); // Actually BLOB
				byte[] stateHash = resultSet.getBytes(4);
				long fees = resultSet.getLong(5);
				boolean isInitial = resultSet.getBoolean(6);
				Long sleepUntilMessageTimestamp = resultSet.getLong(7);

				ATStateData atStateData = new ATStateData(atAddress, height, stateData, stateHash, fees, isInitial,
						sleepUntilMessageTimestamp);

				atStates.add(atStateData);
			} while (resultSet.next());

			return atStates;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch matching AT states from repository", e);
		}
	}

	@Override
	public List<ATStateData> getBlockATStatesAtHeight(int height) throws DataException {
		String sql = "SELECT AT_address, state_hash, fees, is_initial "
				+ "FROM ATs "
				+ "JOIN ATStates "
				+ "ON ATStates.AT_address = ATs.AT_address "
				+ "WHERE height = ? "
				+ "ORDER BY created_when ASC, AT_address DESC";

		List<ATStateData> atStates = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, height)) {
			if (resultSet == null)
				return atStates; // No atStates in this block

			// NB: do-while loop because .checkedExecute() implicitly calls ResultSet.next() for us
			do {
				String atAddress = resultSet.getString(1);
				byte[] stateHash = resultSet.getBytes(2);
				long fees = resultSet.getLong(3);
				boolean isInitial = resultSet.getBoolean(4);

				ATStateData atStateData = new ATStateData(atAddress, height, stateHash, fees, isInitial);
				atStates.add(atStateData);
			} while (resultSet.next());
		} catch (SQLException e) {
			throw new DataException("Unable to fetch AT states for this height from repository", e);
		}

		return atStates;
	}


	@Override
	public void rebuildLatestAtStates(int maxHeight) throws DataException {
		// latestATStatesLock is to prevent concurrent updates on LatestATStates
		// that could result in one process using a partial or empty dataset
		// because it was in the process of being rebuilt by another thread
		synchronized (this.repository.latestATStatesLock) {
			LOGGER.trace("Rebuilding latest AT states...");

			// Rebuild cache of latest AT states that we can't trim
			String deleteSql = "DELETE FROM LatestATStates";
			try {
				this.repository.executeCheckedUpdate(deleteSql);
			} catch (SQLException e) {
				repository.examineException(e);
				throw new DataException("Unable to delete temporary latest AT states cache from repository", e);
			}

			String insertSql = "INSERT INTO LatestATStates ("
					+ "SELECT AT_address, height FROM ATs "
					+ "CROSS JOIN LATERAL("
					+ "SELECT height FROM ATStates "
					+ "WHERE ATStates.AT_address = ATs.AT_address "
					+ "AND height <= ?"
					+ "ORDER BY AT_address DESC, height DESC LIMIT 1"
					+ ") "
					+ ")";
			try {
				this.repository.executeCheckedUpdate(insertSql, maxHeight);
			} catch (SQLException e) {
				repository.examineException(e);
				throw new DataException("Unable to populate temporary latest AT states cache in repository", e);
			}
			this.repository.saveChanges();
			LOGGER.trace("Rebuilt latest AT states");
		}
	}


	@Override
	public int getAtTrimHeight() throws DataException {
		String sql = "SELECT AT_trim_height FROM DatabaseInfo";

		try (ResultSet resultSet = this.repository.checkedExecute(sql)) {
			if (resultSet == null)
				return 0;

			return resultSet.getInt(1);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch AT state trim height from repository", e);
		}
	}

	@Override
	public void setAtTrimHeight(int trimHeight) throws DataException {
		// trimHeightsLock is to prevent concurrent update on DatabaseInfo
		// that could result in "transaction rollback: serialization failure"
		synchronized (this.repository.trimHeightsLock) {
			String updateSql = "UPDATE DatabaseInfo SET AT_trim_height = ?";

			try {
				this.repository.executeCheckedUpdate(updateSql, trimHeight);
				this.repository.saveChanges();
			} catch (SQLException e) {
				this.repository.examineException(e);
				throw new DataException("Unable to set AT state trim height in repository", e);
			}
		}
	}

	@Override
	public int trimAtStates(int minHeight, int maxHeight, int limit) throws DataException {
		if (minHeight >= maxHeight)
			return 0;

		// latestATStatesLock is to prevent concurrent updates on LatestATStates
		// that could result in one process using a partial or empty dataset
		// because it was in the process of being rebuilt by another thread
		synchronized (this.repository.latestATStatesLock) {

			// We're often called so no need to trim all states in one go.
			// Limit updates to reduce CPU and memory load.
			String sql = "DELETE FROM ATStatesData "
					+ "WHERE height BETWEEN ? AND ? "
					+ "AND NOT EXISTS("
					+ "SELECT TRUE FROM LatestATStates "
					+ "WHERE LatestATStates.AT_address = ATStatesData.AT_address "
					+ "AND LatestATStates.height = ATStatesData.height"
					+ ") "
					+ "LIMIT ?";

			try {
				int modifiedRows = this.repository.executeCheckedUpdate(sql, minHeight, maxHeight, limit);
				this.repository.saveChanges();
				return modifiedRows;

			} catch (SQLException e) {
				repository.examineException(e);
				throw new DataException("Unable to trim AT states in repository", e);
			}
		}
	}


	@Override
	public int getAtPruneHeight() throws DataException {
		String sql = "SELECT AT_prune_height FROM DatabaseInfo";

		try (ResultSet resultSet = this.repository.checkedExecute(sql)) {
			if (resultSet == null)
				return 0;

			return resultSet.getInt(1);
		} catch (SQLException e) {
			throw new DataException("Unable to fetch AT state prune height from repository", e);
		}
	}

	@Override
	public void setAtPruneHeight(int pruneHeight) throws DataException {
		// trimHeightsLock is to prevent concurrent update on DatabaseInfo
		// that could result in "transaction rollback: serialization failure"
		synchronized (this.repository.trimHeightsLock) {
			String updateSql = "UPDATE DatabaseInfo SET AT_prune_height = ?";

			try {
				this.repository.executeCheckedUpdate(updateSql, pruneHeight);
				this.repository.saveChanges();
			} catch (SQLException e) {
				repository.examineException(e);
				throw new DataException("Unable to set AT state prune height in repository", e);
			}
		}
	}

	@Override
	public int pruneAtStates(int minHeight, int maxHeight) throws DataException {
		// latestATStatesLock is to prevent concurrent updates on LatestATStates
		// that could result in one process using a partial or empty dataset
		// because it was in the process of being rebuilt by another thread
		synchronized (this.repository.latestATStatesLock) {

			int deletedCount = 0;

			for (int height = minHeight; height <= maxHeight; height++) {

				// Give up if we're stopping
				if (Controller.isStopping()) {
					return deletedCount;
				}

				// Get latest AT states for this height
				List<String> atAddresses = new ArrayList<>();
				String updateSql = "SELECT AT_address FROM LatestATStates WHERE height = ?";
				try (ResultSet resultSet = this.repository.checkedExecute(updateSql, height)) {
					if (resultSet != null) {
						do {
							String atAddress = resultSet.getString(1);
							atAddresses.add(atAddress);

						} while (resultSet.next());
					}
				} catch (SQLException e) {
					throw new DataException("Unable to fetch latest AT states from repository", e);
				}

				List<ATStateData> atStates = this.getBlockATStatesAtHeight(height);
				for (ATStateData atState : atStates) {
					//LOGGER.info("Found atState {} at height {}", atState.getATAddress(), atState.getHeight());

					// Give up if we're stopping
					if (Controller.isStopping()) {
						return deletedCount;
					}

					if (atAddresses.contains(atState.getATAddress())) {
						// We don't want to delete this AT state because it is still active
						LOGGER.trace("Skipping atState {} at height {}", atState.getATAddress(), atState.getHeight());
						continue;
					}

					// Safe to delete everything else for this height
					try {
						this.repository.delete("ATStates", "AT_address = ? AND height = ?",
								atState.getATAddress(), atState.getHeight());
						deletedCount++;
					} catch (SQLException e) {
						throw new DataException("Unable to delete AT state data from repository", e);
					}
				}
			}
			this.repository.saveChanges();

			return deletedCount;
		}
	}


	@Override
	public boolean hasAtStatesHeightIndex() throws DataException {
		String sql = "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.SYSTEM_INDEXINFO where INDEX_NAME='ATSTATESHEIGHTINDEX'";

		try (ResultSet resultSet = this.repository.checkedExecute(sql)) {
			return resultSet != null;

		} catch (SQLException e) {
			throw new DataException("Unable to check for ATStatesHeightIndex in repository", e);
		}
	}


	@Override
	public void save(ATStateData atStateData) throws DataException {
		save(atStateData, true);
	}

	@Override
	public void save(ATStateData atStateData, boolean updateCurrentState) throws DataException {
		save(atStateData, updateCurrentState, true);
	}

	@Override
	public void save(ATStateData atStateData, boolean updateCurrentState, boolean writeLegacyStateData) throws DataException {
		// We shouldn't ever save partial ATStateData
		if (atStateData.getStateHash() == null || atStateData.getHeight() == null)
			throw new IllegalArgumentException("Refusing to save partial AT state into repository!");

		// ATStates is the canonical per-height record. State bytes are stored by hash in ATStateBlobs, with optional
		// legacy ATStatesData writes kept for rollout/backwards compatibility.
		ATExecInstrumentation inst = ATExecInstrumentation.peek();
		HSQLDBSaver atStatesSaver = new HSQLDBSaver("ATStates");
		long tPreviousHeight = System.nanoTime();
		Integer previousHeight = getValidPrecomputedPreviousHeight(atStateData);
		if (previousHeight == null)
			previousHeight = fetchPreviousATStateHeight(atStateData.getATAddress(), atStateData.getHeight());
		if (inst != null)
			inst.apply_fees_sum_update_fetchPreviousHeightNanos += System.nanoTime() - tPreviousHeight;

		atStatesSaver.bind("AT_address", atStateData.getATAddress()).bind("height", atStateData.getHeight())
				.bind("state_hash", atStateData.getStateHash())
				.bind("fees", atStateData.getFees()).bind("is_initial", atStateData.isInitial())
				.bind("sleep_until_message_timestamp", atStateData.getSleepUntilMessageTimestamp())
				.bind("previous_height", previousHeight);

		try {
			long t = System.nanoTime();
			atStatesSaver.execute(this.repository);
			if (inst != null)
				inst.apply_fees_sum_update_saveAtStatesRowNanos += System.nanoTime() - t;
		} catch (SQLException e) {
			throw new DataException("Unable to save AT state into repository", e);
		}

		if (atStateData.getStateData() != null) {
			saveATStateBlob(atStateData);

			if (writeLegacyStateData) {
				if (inst != null) {
					inst.apply_fees_saveAtStatesDataBlobCount++;
					inst.apply_fees_saveAtStatesDataBlobBytes += atStateData.getStateData().length;
				}

				HSQLDBSaver atStatesDataSaver = new HSQLDBSaver("ATStatesData");

				atStatesDataSaver.bind("AT_address", atStateData.getATAddress()).bind("height", atStateData.getHeight())
						.bind("state_data", atStateData.getStateData());

				try {
					long t = System.nanoTime();
					atStatesDataSaver.execute(this.repository);
					if (inst != null)
						inst.apply_fees_sum_update_saveAtStatesDataBlobNanos += System.nanoTime() - t;
				} catch (SQLException e) {
					throw new DataException("Unable to save AT state data into repository", e);
				}
			}

			if (updateCurrentState) {
				long t = System.nanoTime();
				updateCurrentATState(atStateData.getATAddress(), atStateData.getHeight());
				if (inst != null)
					inst.apply_fees_sum_update_currentStatePointerNanos += System.nanoTime() - t;
			}
		} else {
			try {
				long t = System.nanoTime();
				this.repository.delete("ATStatesData", "AT_address = ? AND height = ?",
						atStateData.getATAddress(), atStateData.getHeight());
				if (inst != null)
					inst.apply_fees_sum_update_deleteAtStatesDataBlobNanos += System.nanoTime() - t;

				if (updateCurrentState) {
					t = System.nanoTime();
					revertCurrentATState(atStateData.getATAddress());
					if (inst != null)
						inst.apply_fees_sum_update_currentStatePointerNanos += System.nanoTime() - t;
				}
			} catch (SQLException e) {
				throw new DataException("Unable to delete AT state data from repository", e);
			}
		}
	}

	@Override
	public void save(Collection<ATStateData> atStateDataList, boolean updateCurrentState) throws DataException {
		save(atStateDataList, updateCurrentState, true);
	}

	@Override
	public void save(Collection<ATStateData> atStateDataList, boolean updateCurrentState, boolean writeLegacyStateData) throws DataException {
		if (atStateDataList == null || atStateDataList.isEmpty())
			return;

		for (ATStateData atStateData : atStateDataList)
			if (atStateData.getStateHash() == null || atStateData.getHeight() == null)
				throw new IllegalArgumentException("Refusing to save partial AT state into repository!");

		ATExecInstrumentation inst = ATExecInstrumentation.peek();
		long tPreviousHeights = System.nanoTime();
		Map<String, Integer> previousHeightByState = resolvePreviousATStateHeights(atStateDataList);
		if (inst != null)
			inst.apply_fees_sum_update_fetchPreviousHeightNanos += System.nanoTime() - tPreviousHeights;

		String atStatesSql = "INSERT INTO ATStates "
				+ "(AT_address, height, state_hash, fees, is_initial, sleep_until_message_timestamp, previous_height) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?) "
				+ "ON DUPLICATE KEY UPDATE "
				+ "AT_address = ?, height = ?, state_hash = ?, fees = ?, is_initial = ?, sleep_until_message_timestamp = ?, previous_height = ?";

		String atStatesDataSql = "INSERT INTO ATStatesData "
				+ "(AT_address, height, state_data) "
				+ "VALUES (?, ?, ?) "
				+ "ON DUPLICATE KEY UPDATE "
				+ "AT_address = ?, height = ?, state_data = ?";

		String atStateBlobSql = "INSERT INTO ATStateBlobs "
				+ "(state_hash, state_data, created_height, state_data_length) "
				+ "VALUES (?, ?, ?, ?) "
				+ "ON DUPLICATE KEY UPDATE state_hash = state_hash";

		String deleteAtStatesDataSql = "DELETE FROM ATStatesData WHERE AT_address = ? AND height = ?";

		/*
		 * Batch the canonical state metadata first, then insert only missing content-addressed blobs. Multiple AT state
		 * rows can reference the same state_hash, so this avoids rewriting identical large blobs while preserving full
		 * per-height history in ATStates.
		 */
		Lock readLock = HSQLDBRepository.CHECKPOINT_GATE.readLock();
		long tLockWait = System.nanoTime();
		readLock.lock();
		if (inst != null)
			inst.apply_fees_sum_update_checkpointReadLockWaitNanos += System.nanoTime() - tLockWait;
		try {
			long tTransactionStart = System.nanoTime();
			this.repository.markTransactionStarted();
			if (inst != null)
				inst.apply_fees_sum_update_markTransactionStartedNanos += System.nanoTime() - tTransactionStart;

			long tPrepareAtStates = System.nanoTime();
			try (PreparedStatement preparedStatement = this.repository.prepareStatement(atStatesSql)) {
				if (inst != null)
					inst.apply_fees_sum_update_prepareAtStatesStatementNanos += System.nanoTime() - tPrepareAtStates;

				long tBindAtStates = System.nanoTime();
				for (ATStateData atStateData : atStateDataList) {
					bindATStatesRow(preparedStatement, atStateData,
							previousHeightByState.get(atStateKey(atStateData.getATAddress(), atStateData.getHeight())));
					preparedStatement.addBatch();
				}
				if (inst != null)
					inst.apply_fees_sum_update_bindAtStatesRowsNanos += System.nanoTime() - tBindAtStates;

				long t = System.nanoTime();
				preparedStatement.executeBatch();
				if (inst != null) {
					inst.apply_fees_sum_update_executeAtStatesRowsNanos += System.nanoTime() - t;
					inst.apply_fees_sum_update_saveAtStatesRowNanos += System.nanoTime() - t;
				}
			}

			boolean hasStateDataRows = false;
			Map<ByteArray, ATStateData> stateDataByHash = new HashMap<>();

			// Deduplicate candidate blobs within this block before checking which hashes already exist in the DB.
			for (ATStateData atStateData : atStateDataList) {
				if (atStateData.getStateData() == null)
					continue;

				hasStateDataRows = true;

				ByteArray stateHash = ByteArray.wrap(atStateData.getStateHash());
				stateDataByHash.putIfAbsent(stateHash, atStateData);
			}
			if (inst != null)
				inst.apply_fees_candidateATStateBlobCount += stateDataByHash.size();

			long tBlobLookup = System.nanoTime();
			Set<ByteArray> existingStateHashes = fetchExistingATStateBlobHashes(stateDataByHash.keySet());
			if (inst != null) {
				inst.apply_fees_existingATStateBlobCount += existingStateHashes.size();
				inst.apply_fees_sum_update_lookupATStateBlobHashesNanos += System.nanoTime() - tBlobLookup;
				inst.apply_fees_sum_update_saveATStateBlobsNanos += System.nanoTime() - tBlobLookup;
			}

			long tPrepareBlobStatement = System.nanoTime();
			try (PreparedStatement blobStatement = this.repository.prepareStatement(atStateBlobSql)) {
				if (inst != null)
					inst.apply_fees_sum_update_prepareATStateBlobsStatementNanos += System.nanoTime() - tPrepareBlobStatement;

				boolean hasBlobInserts = false;
				long tBindBlobs = System.nanoTime();

				for (Map.Entry<ByteArray, ATStateData> entry : stateDataByHash.entrySet()) {
					if (existingStateHashes.contains(entry.getKey()))
						continue;

					ATStateData atStateData = entry.getValue();

					if (inst != null) {
						inst.apply_fees_saveATStateBlobCount++;
						inst.apply_fees_saveATStateBlobBytes += atStateData.getStateData().length;
					}

					bindATStateBlobRow(blobStatement, atStateData);
					blobStatement.addBatch();
					hasBlobInserts = true;
				}
				if (inst != null)
					inst.apply_fees_sum_update_bindATStateBlobRowsNanos += System.nanoTime() - tBindBlobs;

				if (hasBlobInserts) {
					long tBlobInsert = System.nanoTime();
					blobStatement.executeBatch();
					if (inst != null) {
						inst.apply_fees_sum_update_executeATStateBlobRowsNanos += System.nanoTime() - tBlobInsert;
						inst.apply_fees_sum_update_saveATStateBlobsNanos += System.nanoTime() - tBlobInsert;
					}
				}
			}

			if (writeLegacyStateData && hasStateDataRows) {
				try (PreparedStatement preparedStatement = this.repository.prepareStatement(atStatesDataSql)) {
					for (ATStateData atStateData : atStateDataList) {
						if (atStateData.getStateData() == null)
								continue;

						if (inst != null) {
							inst.apply_fees_saveAtStatesDataBlobCount++;
							inst.apply_fees_saveAtStatesDataBlobBytes += atStateData.getStateData().length;
						}

						bindATStatesDataRow(preparedStatement, atStateData);
						preparedStatement.addBatch();
					}

					long t = System.nanoTime();
					preparedStatement.executeBatch();
					if (inst != null)
						inst.apply_fees_sum_update_saveAtStatesDataBlobNanos += System.nanoTime() - t;
				}
			}

			try (PreparedStatement preparedStatement = this.repository.prepareStatement(deleteAtStatesDataSql)) {
				boolean hasDeleteRows = false;

				for (ATStateData atStateData : atStateDataList) {
					if (atStateData.getStateData() != null)
						continue;

					preparedStatement.setString(1, atStateData.getATAddress());
					preparedStatement.setInt(2, atStateData.getHeight());
					preparedStatement.addBatch();
					hasDeleteRows = true;
				}

				if (hasDeleteRows) {
					long t = System.nanoTime();
					preparedStatement.executeBatch();
					if (inst != null)
						inst.apply_fees_sum_update_deleteAtStatesDataBlobNanos += System.nanoTime() - t;
				}
			}
		} catch (SQLException e) {
			throw new DataException("Unable to batch save AT state data into repository", this.repository.examineException(e));
		} finally {
			readLock.unlock();
		}

		if (updateCurrentState) {
			for (ATStateData atStateData : atStateDataList) {
				long t = System.nanoTime();
				if (atStateData.getStateData() == null)
					revertCurrentATState(atStateData.getATAddress());
				else
					updateCurrentATState(atStateData.getATAddress(), atStateData.getHeight());
				if (inst != null)
					inst.apply_fees_sum_update_currentStatePointerNanos += System.nanoTime() - t;
			}
		}
	}

	private static void bindATStatesRow(PreparedStatement preparedStatement, ATStateData atStateData, Integer previousHeight) throws SQLException {
		preparedStatement.setString(1, atStateData.getATAddress());
		preparedStatement.setInt(2, atStateData.getHeight());
		preparedStatement.setBytes(3, atStateData.getStateHash());
		preparedStatement.setLong(4, atStateData.getFees());
		preparedStatement.setBoolean(5, atStateData.isInitial());
		preparedStatement.setObject(6, atStateData.getSleepUntilMessageTimestamp());
		preparedStatement.setObject(7, previousHeight);

		preparedStatement.setString(8, atStateData.getATAddress());
		preparedStatement.setInt(9, atStateData.getHeight());
		preparedStatement.setBytes(10, atStateData.getStateHash());
		preparedStatement.setLong(11, atStateData.getFees());
		preparedStatement.setBoolean(12, atStateData.isInitial());
		preparedStatement.setObject(13, atStateData.getSleepUntilMessageTimestamp());
		preparedStatement.setObject(14, previousHeight);
	}

	private static void bindATStatesDataRow(PreparedStatement preparedStatement, ATStateData atStateData) throws SQLException {
		preparedStatement.setString(1, atStateData.getATAddress());
		preparedStatement.setInt(2, atStateData.getHeight());
		preparedStatement.setBytes(3, atStateData.getStateData());

		preparedStatement.setString(4, atStateData.getATAddress());
		preparedStatement.setInt(5, atStateData.getHeight());
		preparedStatement.setBytes(6, atStateData.getStateData());
	}

	private Set<ByteArray> fetchExistingATStateBlobHashes(Set<ByteArray> stateHashes) throws DataException {
		Set<ByteArray> existingStateHashes = new LinkedHashSet<>();

		if (stateHashes == null || stateHashes.isEmpty())
			return existingStateHashes;

		List<ByteArray> stateHashList = new ArrayList<>(stateHashes);
		final int batchSize = 500;
		for (int start = 0; start < stateHashList.size(); start += batchSize) {
			int end = Math.min(start + batchSize, stateHashList.size());
			List<ByteArray> batchStateHashes = stateHashList.subList(start, end);
			String bindPlaceholders = String.join(", ", Collections.nCopies(batchStateHashes.size(), "?"));
			String sql = "SELECT state_hash FROM ATStateBlobs WHERE state_hash IN (" + bindPlaceholders + ")";
			Object[] bindParams = batchStateHashes.stream().map(stateHash -> stateHash.value).toArray();

			try (ResultSet resultSet = this.repository.checkedExecute(sql, bindParams)) {
				if (resultSet == null)
					continue;

				do {
					existingStateHashes.add(ByteArray.copyOf(resultSet.getBytes(1)));
				} while (resultSet.next());
			} catch (SQLException e) {
				throw new DataException("Unable to fetch existing AT state blob hashes", e);
			}
		}

		return existingStateHashes;
	}

	private void saveATStateBlob(ATStateData atStateData) throws DataException {
		ATExecInstrumentation inst = ATExecInstrumentation.peek();
		if (inst != null) {
			inst.apply_fees_saveATStateBlobCount++;
			inst.apply_fees_saveATStateBlobBytes += atStateData.getStateData().length;
		}

		String sql = "INSERT INTO ATStateBlobs "
				+ "(state_hash, state_data, created_height, state_data_length) VALUES (?, ?, ?, ?) "
				+ "ON DUPLICATE KEY UPDATE state_hash = state_hash";

		Lock readLock = HSQLDBRepository.CHECKPOINT_GATE.readLock();
		readLock.lock();
		try (PreparedStatement preparedStatement = this.repository.prepareStatement(sql)) {
			this.repository.markTransactionStarted();
			bindATStateBlobRow(preparedStatement, atStateData);
			long t = System.nanoTime();
			preparedStatement.execute();
			if (inst != null)
				inst.apply_fees_sum_update_saveATStateBlobsNanos += System.nanoTime() - t;
		} catch (SQLException e) {
			throw new DataException("Unable to save AT state blob into repository", this.repository.examineException(e));
		} finally {
			readLock.unlock();
		}
	}

	private static void bindATStateBlobRow(PreparedStatement preparedStatement, ATStateData atStateData) throws SQLException {
		preparedStatement.setBytes(1, atStateData.getStateHash());
		preparedStatement.setBytes(2, atStateData.getStateData());
		preparedStatement.setInt(3, atStateData.getHeight());
		preparedStatement.setInt(4, atStateData.getStateData().length);
	}

	private Integer fetchPreviousATStateHeight(String atAddress, int height) throws DataException {
		// Prefer pointer tables for the previous height. If those are unavailable or stale, fall back to canonical
		// ATStates so a bad pointer cannot corrupt the rollback chain.
		String currentPointerSql = "SELECT MAX(pointer_height) FROM ("
				+ "SELECT current_state_height AS pointer_height FROM ATRuntime WHERE AT_address = ? AND current_state_height < ? "
				+ "UNION ALL "
				+ "SELECT current_state_height AS pointer_height FROM ATs WHERE AT_address = ? AND current_state_height < ? "
				+ "UNION ALL "
				+ "SELECT height AS pointer_height FROM ATCurrentState WHERE AT_address = ? AND height < ?"
				+ ") AS CurrentPointers";

		try (ResultSet resultSet = this.repository.checkedExecute(currentPointerSql, atAddress, height, atAddress, height, atAddress, height)) {
			if (resultSet != null) {
				Integer previousHeight = resultSet.getInt(1);
				if (!(previousHeight == 0 && resultSet.wasNull()))
					return previousHeight;
			}
		} catch (SQLException e) {
			throw new DataException("Unable to fetch previous AT state height from current pointer", e);
		}

		String fallbackSql = "SELECT MAX(height) FROM ATStates WHERE AT_address = ? AND height < ?";

		try (ResultSet resultSet = this.repository.checkedExecute(fallbackSql, atAddress, height)) {
			if (resultSet == null)
				return null;

			Integer previousHeight = resultSet.getInt(1);
			return previousHeight == 0 && resultSet.wasNull() ? null : previousHeight;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch previous AT state height", e);
		}
	}

	private Map<String, Integer> resolvePreviousATStateHeights(Collection<ATStateData> atStateDataList) throws DataException {
		Map<String, Integer> previousHeightByState = new HashMap<>();
		List<ATStateData> statesNeedingRepositoryLookup = new ArrayList<>();

		if (atStateDataList == null || atStateDataList.isEmpty())
			return previousHeightByState;

		for (ATStateData atStateData : atStateDataList) {
			Integer previousHeight = getValidPrecomputedPreviousHeight(atStateData);
			if (previousHeight != null) {
				previousHeightByState.put(atStateKey(atStateData.getATAddress(), atStateData.getHeight()), previousHeight);
				continue;
			}

			statesNeedingRepositoryLookup.add(atStateData);
		}

		if (!statesNeedingRepositoryLookup.isEmpty())
			previousHeightByState.putAll(fetchPreviousATStateHeights(statesNeedingRepositoryLookup));

		return previousHeightByState;
	}

	private static Integer getValidPrecomputedPreviousHeight(ATStateData atStateData) {
		Integer previousHeight = atStateData.getPreviousHeight();
		if (previousHeight == null)
			return null;

		if (previousHeight < atStateData.getHeight())
			return previousHeight;

		return null;
	}

	private Map<String, Integer> fetchPreviousATStateHeights(Collection<ATStateData> atStateDataList) throws DataException {
		Map<String, Integer> previousHeightByState = new HashMap<>();

		if (atStateDataList == null || atStateDataList.isEmpty())
			return previousHeightByState;

		Map<String, List<ATStateData>> statesByAddress = new HashMap<>();
		Set<String> atAddresses = new LinkedHashSet<>();
		for (ATStateData atStateData : atStateDataList) {
			atAddresses.add(atStateData.getATAddress());
			statesByAddress.computeIfAbsent(atStateData.getATAddress(), key -> new ArrayList<>()).add(atStateData);
		}

		Map<String, Integer> runtimeHeights = fetchCurrentStateHeights("ATRuntime", "current_state_height", atAddresses);
		Map<String, Integer> atHeights = fetchCurrentStateHeights("ATs", "current_state_height", atAddresses);
		Map<String, Integer> currentStateHeights = fetchCurrentStateHeights("ATCurrentState", "height", atAddresses);

		for (ATStateData atStateData : atStateDataList) {
			String atAddress = atStateData.getATAddress();
			int stateHeight = atStateData.getHeight();
			Integer previousHeight = null;

			previousHeight = maxPreviousHeight(previousHeight, runtimeHeights.get(atAddress), stateHeight);
			previousHeight = maxPreviousHeight(previousHeight, atHeights.get(atAddress), stateHeight);
			previousHeight = maxPreviousHeight(previousHeight, currentStateHeights.get(atAddress), stateHeight);

			if (previousHeight != null)
				previousHeightByState.put(atStateKey(atAddress, stateHeight), previousHeight);
		}

		fillMissingPreviousATStateHeights(previousHeightByState, statesByAddress);

		return previousHeightByState;
	}

	private Map<String, Integer> fetchCurrentStateHeights(String tableName, String heightColumnName, Set<String> atAddresses) throws DataException {
		Map<String, Integer> heightsByAddress = new HashMap<>();

		if (atAddresses == null || atAddresses.isEmpty())
			return heightsByAddress;

		List<String> uniqueATAddresses = new ArrayList<>(atAddresses);
		final int batchSize = 500;
		for (int start = 0; start < uniqueATAddresses.size(); start += batchSize) {
			int end = Math.min(start + batchSize, uniqueATAddresses.size());
			List<String> batchATAddresses = uniqueATAddresses.subList(start, end);
			String bindPlaceholders = String.join(", ", Collections.nCopies(batchATAddresses.size(), "?"));
			String sql = "SELECT AT_address, " + heightColumnName + " FROM " + tableName
					+ " WHERE AT_address IN (" + bindPlaceholders + ")";

			try (ResultSet resultSet = this.repository.checkedExecute(sql, batchATAddresses.toArray())) {
				if (resultSet == null)
					continue;

				do {
					Integer height = resultSet.getInt(2);
					if (height == 0 && resultSet.wasNull())
						continue;

					heightsByAddress.put(resultSet.getString(1), height);
				} while (resultSet.next());
			} catch (SQLException e) {
				throw new DataException("Unable to fetch AT current state heights from " + tableName, e);
			}
		}

		return heightsByAddress;
	}

	private void fillMissingPreviousATStateHeights(Map<String, Integer> previousHeightByState,
			Map<String, List<ATStateData>> statesByAddress) throws DataException {
		String sql = "SELECT MAX(height) FROM ATStates WHERE AT_address = ? AND height < ?";

		for (Map.Entry<String, List<ATStateData>> entry : statesByAddress.entrySet()) {
			String atAddress = entry.getKey();

			for (ATStateData atStateData : entry.getValue()) {
				String atStateKey = atStateKey(atAddress, atStateData.getHeight());
				if (previousHeightByState.containsKey(atStateKey))
					continue;

				try (ResultSet resultSet = this.repository.checkedExecute(sql, atAddress, atStateData.getHeight())) {
					if (resultSet == null)
						continue;

					Integer previousHeight = resultSet.getInt(1);
					if (!(previousHeight == 0 && resultSet.wasNull()))
						previousHeightByState.put(atStateKey, previousHeight);
				} catch (SQLException e) {
					throw new DataException("Unable to fetch fallback previous AT state height", e);
				}
			}
		}
	}

	private static Integer maxPreviousHeight(Integer currentPreviousHeight, Integer candidatePreviousHeight, int stateHeight) {
		if (candidatePreviousHeight == null || candidatePreviousHeight >= stateHeight)
			return currentPreviousHeight;

		if (currentPreviousHeight == null || candidatePreviousHeight > currentPreviousHeight)
			return candidatePreviousHeight;

		return currentPreviousHeight;
	}

	private static String atStateKey(String atAddress, int height) {
		return atAddress + '\n' + height;
	}

	@Override
	public void delete(String atAddress, int height) throws DataException {
		DeletedATStatePointerInfo pointerInfo = fetchDeletedATStatePointerInfo(atAddress, height);

		try {
			this.repository.delete("ATStates", "AT_address = ? AND height = ?", atAddress, height);
			this.repository.delete("ATStatesData", "AT_address = ? AND height = ?", atAddress, height);

			if (pointerInfo == null)
				revertCurrentATState(atAddress);
			else if (pointerInfo.currentHeight != null && pointerInfo.currentHeight > height)
				return;
			else
				setCurrentATStateFromPreviousHeight(atAddress, pointerInfo.previousHeight, pointerInfo.isInitial);
		} catch (SQLException e) {
			throw new DataException("Unable to delete AT state from repository", e);
		}
	}

	@Override
	public void deleteATStates(int height) throws DataException {
		List<String> affectedAtAddresses = new ArrayList<>();
		String sql = "SELECT AT_address FROM ATRuntime WHERE current_state_height = ? "
				+ "UNION SELECT AT_address FROM ATs WHERE current_state_height = ? "
				+ "UNION SELECT AT_address FROM ATCurrentState WHERE height = ?";

		try {
			try (ResultSet resultSet = this.repository.checkedExecute(sql, height, height, height)) {
				if (resultSet != null)
					do {
						affectedAtAddresses.add(resultSet.getString(1));
					} while (resultSet.next());
			}

			this.repository.delete("ATStates", "height = ?", height);
			this.repository.delete("ATStatesData", "height = ?", height);

			for (String atAddress : affectedAtAddresses)
				revertCurrentATState(atAddress);
		} catch (SQLException e) {
			throw new DataException("Unable to delete AT states from repository", e);
		}
	}

	// Finding transactions for ATs to process

	@Override
	public void rebuildATIncomingTransactions() throws DataException {
		try {
			// Derived inbox: rebuild only from confirmed canonical transaction subtype tables and existing AT addresses.
			this.repository.executeCheckedUpdate("DELETE FROM ATIncomingTransactions");

			this.repository.executeCheckedUpdate(buildInsertATIncomingTransactionsSql(null));
		} catch (SQLException e) {
			throw new DataException("Unable to rebuild AT incoming transaction cache", e);
		}
	}

	@Override
	public void rebuildATNextIncoming() throws DataException {
		try {
			this.repository.executeCheckedUpdate("DELETE FROM ATNextIncoming");
		} catch (SQLException e) {
			throw new DataException("Unable to clear AT next incoming cache", e);
		}

		List<String> sleepingATs = new ArrayList<>();
		String sql = "SELECT ATs.AT_address FROM ATs "
				+ "LEFT OUTER JOIN ATRuntime ON ATRuntime.AT_address = ATs.AT_address "
				+ "WHERE " + runtimeColumn("sleep_until_message_timestamp") + " IS NOT NULL";

		try (ResultSet resultSet = this.repository.checkedExecute(sql)) {
			if (resultSet != null)
				do {
					sleepingATs.add(resultSet.getString(1));
				} while (resultSet.next());
		} catch (SQLException e) {
			throw new DataException("Unable to fetch sleeping ATs for next incoming rebuild", e);
		}

		recomputeNextIncomingForATs(sleepingATs);
	}

	@Override
	public void rebuildATIncomingCaches() throws DataException {
		rebuildATIncomingTransactions();
		rebuildATNextIncoming();
		rebuildATExecutionQueue();
	}

	@Override
	public void rebuildATExecutionQueue() throws DataException {
		// Recompute each AT's earliest possible wake height from canonical runtime metadata plus the derived inbox cursor.
		String selectSql = "SELECT ATs.AT_address, ATRuntime.is_finished, ATRuntime.sleep_until_height, "
				+ "ATRuntime.sleep_until_message_timestamp, ATNextIncoming.sleep_until_message_timestamp, "
				+ "ATNextIncoming.block_height "
				+ "FROM ATs "
				+ "JOIN ATRuntime ON ATRuntime.AT_address = ATs.AT_address "
				+ "LEFT OUTER JOIN ATNextIncoming ON ATNextIncoming.AT_address = ATs.AT_address";

		String insertSql = "INSERT INTO ATExecutionQueue (AT_address, next_height) VALUES (?, ?)";

		Lock readLock = HSQLDBRepository.CHECKPOINT_GATE.readLock();
		readLock.lock();
		try {
			this.repository.markTransactionStarted();
			this.repository.executeCheckedUpdate("DELETE FROM ATExecutionQueue");

			try (ResultSet resultSet = this.repository.checkedExecute(selectSql);
					PreparedStatement preparedStatement = this.repository.prepareStatement(insertSql)) {
				if (resultSet == null)
					return;

				boolean hasRows = false;
				do {
					String atAddress = resultSet.getString(1);
					boolean isFinished = resultSet.getBoolean(2);

					Integer sleepUntilHeight = resultSet.getInt(3);
					if (sleepUntilHeight == 0 && resultSet.wasNull())
						sleepUntilHeight = null;

					Long sleepUntilMessageTimestamp = resultSet.getLong(4);
					if (sleepUntilMessageTimestamp == 0 && resultSet.wasNull())
						sleepUntilMessageTimestamp = null;

					Long cursorSleepTimestamp = resultSet.getLong(5);
					if (cursorSleepTimestamp == 0 && resultSet.wasNull())
						cursorSleepTimestamp = null;

					Integer incomingHeight = resultSet.getInt(6);
					if (incomingHeight == 0 && resultSet.wasNull())
						incomingHeight = null;

					Integer nextHeight = calculateNextExecutionHeight(isFinished, sleepUntilHeight,
							sleepUntilMessageTimestamp, cursorSleepTimestamp, incomingHeight);
					if (nextHeight == null)
						continue;

					preparedStatement.setString(1, atAddress);
					preparedStatement.setInt(2, nextHeight);
					preparedStatement.addBatch();
					hasRows = true;
				} while (resultSet.next());

				if (hasRows)
					preparedStatement.executeBatch();
			}
		} catch (SQLException e) {
			throw new DataException("Unable to rebuild AT execution queue", this.repository.examineException(e));
		} finally {
			readLock.unlock();
		}
	}

	@Override
	public void recomputeATExecutionQueueForAT(String atAddress) throws DataException {
		recomputeATExecutionQueueForATs(Collections.singleton(atAddress));
	}

	@Override
	public void recomputeATExecutionQueueForATs(Collection<String> atAddresses) throws DataException {
		if (atAddresses == null || atAddresses.isEmpty())
			return;

		List<String> uniqueATAddresses = new ArrayList<>(new LinkedHashSet<>(atAddresses));

		Lock readLock = HSQLDBRepository.CHECKPOINT_GATE.readLock();
		readLock.lock();
		try {
			this.repository.markTransactionStarted();

			final int batchSize = 500;
			for (int start = 0; start < uniqueATAddresses.size(); start += batchSize) {
				int end = Math.min(start + batchSize, uniqueATAddresses.size());
				List<String> batchATAddresses = uniqueATAddresses.subList(start, end);

				String bindPlaceholders = String.join(", ", Collections.nCopies(batchATAddresses.size(), "?"));
				Object[] bindParams = batchATAddresses.toArray(new Object[batchATAddresses.size()]);

				this.repository.executeCheckedUpdate("DELETE FROM ATExecutionQueue WHERE AT_address IN ("
						+ bindPlaceholders + ")", bindParams);

				String insertSql = "INSERT INTO ATExecutionQueue (AT_address, next_height) "
						+ "SELECT ATRuntime.AT_address, " + atExecutionQueueNextHeightSql() + " "
						+ "FROM ATRuntime "
						+ "LEFT OUTER JOIN ATNextIncoming ON ATNextIncoming.AT_address = ATRuntime.AT_address "
						+ "WHERE ATRuntime.AT_address IN (" + bindPlaceholders + ") "
						+ "AND ATRuntime.is_finished = false "
						+ "AND (ATRuntime.sleep_until_message_timestamp IS NULL "
							+ "OR (ATRuntime.sleep_until_height IS NOT NULL AND ATRuntime.sleep_until_height != 0) "
							+ "OR (ATNextIncoming.block_height IS NOT NULL "
								+ "AND ATNextIncoming.sleep_until_message_timestamp = ATRuntime.sleep_until_message_timestamp))";

				this.repository.executeCheckedUpdate(insertSql, bindParams);
			}
		} catch (SQLException e) {
			throw new DataException("Unable to batch recompute AT execution queue rows", this.repository.examineException(e));
		} finally {
			readLock.unlock();
		}
	}

	private static Integer calculateNextExecutionHeight(boolean isFinished, Integer sleepUntilHeight,
			Long sleepUntilMessageTimestamp, Long cursorSleepTimestamp, Integer incomingHeight) {
		if (isFinished)
			return null;

		if (sleepUntilMessageTimestamp == null)
			return 0;

		Integer nextHeight = null;
		if (sleepUntilHeight != null && sleepUntilHeight != 0)
			nextHeight = sleepUntilHeight;

		if (incomingHeight != null && cursorSleepTimestamp != null
				&& cursorSleepTimestamp.longValue() == sleepUntilMessageTimestamp.longValue()) {
			int messageWakeHeight = incomingHeight + 1;
			if (nextHeight == null || messageWakeHeight < nextHeight)
				nextHeight = messageWakeHeight;
		}

		return nextHeight;
	}

	private static String atExecutionQueueNextHeightSql() {
		return "CASE "
				+ "WHEN ATRuntime.sleep_until_message_timestamp IS NULL THEN 0 "
				+ "WHEN ATRuntime.sleep_until_height IS NOT NULL "
					+ "AND ATRuntime.sleep_until_height != 0 "
					+ "AND ATNextIncoming.block_height IS NOT NULL "
					+ "AND ATNextIncoming.sleep_until_message_timestamp = ATRuntime.sleep_until_message_timestamp "
					+ "THEN CASE "
						+ "WHEN ATRuntime.sleep_until_height <= ATNextIncoming.block_height + 1 "
							+ "THEN ATRuntime.sleep_until_height "
						+ "ELSE ATNextIncoming.block_height + 1 "
					+ "END "
				+ "WHEN ATRuntime.sleep_until_height IS NOT NULL "
					+ "AND ATRuntime.sleep_until_height != 0 "
					+ "THEN ATRuntime.sleep_until_height "
				+ "WHEN ATNextIncoming.block_height IS NOT NULL "
					+ "AND ATNextIncoming.sleep_until_message_timestamp = ATRuntime.sleep_until_message_timestamp "
					+ "THEN ATNextIncoming.block_height + 1 "
			+ "END";
	}

	@Override
	public List<String> recordATIncomingTransactionsForBlock(int height) throws DataException {
		List<String> affectedATs = fetchATIncomingRecipientsForBlock(height);

		try {
			this.repository.executeCheckedUpdate("DELETE FROM ATIncomingTransactions WHERE block_height = ?", height);
			this.repository.executeCheckedUpdate(buildInsertATIncomingTransactionsSql("T.block_height = ?"), height, height, height);
		} catch (SQLException e) {
			throw new DataException("Unable to record AT incoming transactions for block", e);
		}

		return affectedATs;
	}

	@Override
	public List<String> recordATIncomingTransactionsForBlock(int height, Collection<IncomingTransactionInfo> incomingTransactions) throws DataException {
		if (incomingTransactions == null || incomingTransactions.isEmpty()) {
			try {
				this.repository.executeCheckedUpdate("DELETE FROM ATIncomingTransactions WHERE block_height = ?", height);
			} catch (SQLException e) {
				throw new DataException("Unable to clear AT incoming transactions for block", e);
			}

			return Collections.emptyList();
		}

		List<IncomingTransactionInfo> candidates = new ArrayList<>();
		Set<String> candidateATAddresses = new LinkedHashSet<>();
		for (IncomingTransactionInfo incomingTransaction : incomingTransactions) {
			if (incomingTransaction == null || incomingTransaction.atAddress == null)
				continue;

			if (incomingTransaction.height != height)
				throw new DataException("AT incoming transaction candidate height does not match linked block height");

			candidates.add(incomingTransaction);
			candidateATAddresses.add(incomingTransaction.atAddress);
		}

		if (candidates.isEmpty()) {
			try {
				this.repository.executeCheckedUpdate("DELETE FROM ATIncomingTransactions WHERE block_height = ?", height);
			} catch (SQLException e) {
				throw new DataException("Unable to clear AT incoming transactions for block", e);
			}

			return Collections.emptyList();
		}

		Set<String> existingATAddresses = new LinkedHashSet<>();

		try {
			// The block layer supplies candidate recipients cheaply while assigning sequences; this filter preserves the
			// old semantics by only indexing recipients that are currently known ATs.
			List<String> candidateATAddressList = new ArrayList<>(candidateATAddresses);
			final int batchSize = 500;
			for (int start = 0; start < candidateATAddressList.size(); start += batchSize) {
				int end = Math.min(start + batchSize, candidateATAddressList.size());
				List<String> batchATAddresses = candidateATAddressList.subList(start, end);

				String bindPlaceholders = String.join(", ", Collections.nCopies(batchATAddresses.size(), "?"));
				try (ResultSet resultSet = this.repository.checkedExecute("SELECT AT_address FROM ATs WHERE AT_address IN ("
						+ bindPlaceholders + ")", batchATAddresses.toArray(new Object[batchATAddresses.size()]))) {
					if (resultSet != null)
						do {
							existingATAddresses.add(resultSet.getString(1));
						} while (resultSet.next());
				}
			}

			this.repository.executeCheckedUpdate("DELETE FROM ATIncomingTransactions WHERE block_height = ?", height);

			List<Object[]> insertRows = new ArrayList<>();
			Set<String> affectedATs = new LinkedHashSet<>();
			for (IncomingTransactionInfo candidate : candidates) {
				if (!existingATAddresses.contains(candidate.atAddress))
					continue;

				insertRows.add(new Object[] {
						candidate.atAddress,
						candidate.height,
						candidate.sequence,
						candidate.signature
				});
				affectedATs.add(candidate.atAddress);
			}

			this.repository.executeCheckedBatchUpdate("INSERT INTO ATIncomingTransactions "
					+ "(AT_address, block_height, block_sequence, signature) VALUES (?, ?, ?, ?)", insertRows);

			return new ArrayList<>(affectedATs);
		} catch (SQLException e) {
			throw new DataException("Unable to record supplied AT incoming transactions for block", e);
		}
	}

	@Override
	public List<String> deleteATIncomingTransactionsForBlock(int height) throws DataException {
		List<String> affectedATs = new ArrayList<>();
		String sql = "SELECT DISTINCT AT_address FROM ATIncomingTransactions WHERE block_height = ?";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, height)) {
			if (resultSet != null)
				do {
					affectedATs.add(resultSet.getString(1));
				} while (resultSet.next());

			this.repository.executeCheckedUpdate("DELETE FROM ATIncomingTransactions WHERE block_height = ?", height);
		} catch (SQLException e) {
			throw new DataException("Unable to delete AT incoming transactions for block", e);
		}

		return affectedATs;
	}

	@Override
	public void recomputeNextIncomingForAT(String atAddress) throws DataException {
		Long sleepUntilMessageTimestamp = null;
		String sql = "SELECT " + runtimeColumn("sleep_until_message_timestamp") + " "
				+ "FROM ATs "
				+ "LEFT OUTER JOIN ATRuntime ON ATRuntime.AT_address = ATs.AT_address "
				+ "WHERE ATs.AT_address = ? LIMIT 1";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, atAddress)) {
			if (resultSet == null) {
				this.repository.delete("ATNextIncoming", "AT_address = ?", atAddress);
				return;
			}

			sleepUntilMessageTimestamp = resultSet.getLong(1);
			if (sleepUntilMessageTimestamp == 0 && resultSet.wasNull())
				sleepUntilMessageTimestamp = null;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch AT sleep timestamp for next incoming recompute", e);
		}

		try {
			this.repository.delete("ATNextIncoming", "AT_address = ?", atAddress);
		} catch (SQLException e) {
			throw new DataException("Unable to clear stale AT next incoming cursor", e);
		}

		if (sleepUntilMessageTimestamp == null)
			return;

		// Store the wake cursor with the exact sleep timestamp that produced it. Readers reject mismatched timestamps,
		// which makes stale rows harmless after AT runtime changes or orphan rollback.
		Timestamp previousTxTimestamp = new Timestamp(sleepUntilMessageTimestamp);
		NextTransactionInfo nextTransactionInfo = findNextTransactionCanonical(atAddress,
				previousTxTimestamp.blockHeight, previousTxTimestamp.transactionSequence);

		HSQLDBSaver saveHelper = new HSQLDBSaver("ATNextIncoming");
		saveHelper.bind("AT_address", atAddress)
				.bind("sleep_until_message_timestamp", sleepUntilMessageTimestamp)
				.bind("block_height", nextTransactionInfo == null ? null : nextTransactionInfo.height)
				.bind("block_sequence", nextTransactionInfo == null ? null : nextTransactionInfo.sequence)
				.bind("signature", nextTransactionInfo == null ? null : nextTransactionInfo.signature);

		try {
			saveHelper.execute(this.repository);
		} catch (SQLException e) {
			throw new DataException("Unable to save AT next incoming cursor", e);
		}
	}

	@Override
	public void recomputeNextIncomingForATs(Collection<String> atAddresses) throws DataException {
		if (atAddresses == null || atAddresses.isEmpty())
			return;

		for (String atAddress : new LinkedHashSet<>(atAddresses))
			recomputeNextIncomingForAT(atAddress);
	}

	@Override
	public Map<String, NextTransactionInfo> getNextIncomingForATs(Collection<ATData> atDataList, int blockHeight) throws DataException {
		Map<String, NextTransactionInfo> nextIncomingByAT = new HashMap<>();

		if (atDataList == null || atDataList.isEmpty())
			return nextIncomingByAT;

		Map<String, Long> sleepTimestampByAT = new HashMap<>();
		for (ATData atData : atDataList) {
			Long sleepUntilMessageTimestamp = atData.getSleepUntilMessageTimestamp();
			if (sleepUntilMessageTimestamp == null)
				continue;

			Integer sleepUntilHeight = atData.getSleepUntilHeight();
			if (sleepUntilHeight != null && sleepUntilHeight != 0 && blockHeight >= sleepUntilHeight)
				continue;

			sleepTimestampByAT.put(atData.getATAddress(), sleepUntilMessageTimestamp);
		}

		if (sleepTimestampByAT.isEmpty())
			return nextIncomingByAT;

		// Only load cursors for ATs that are message-sleeping and not already height-wakeable. Missing or stale cursor
		// rows are recomputed before the method returns, so callers can use null as an explicit "no message" result.
		List<String> atAddresses = new ArrayList<>(sleepTimestampByAT.keySet());
		String sql = "SELECT AT_address, sleep_until_message_timestamp, block_height, block_sequence, signature "
				+ "FROM ATNextIncoming "
				+ "WHERE AT_address IN ("
				+ String.join(", ", Collections.nCopies(atAddresses.size(), "?"))
				+ ")";

		Set<String> foundCursorATs = new LinkedHashSet<>();
		Set<String> staleCursorATs = new LinkedHashSet<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, atAddresses.toArray(new String[atAddresses.size()]))) {
			if (resultSet != null)
				do {
					String atAddress = resultSet.getString(1);
					foundCursorATs.add(atAddress);

					long cursorSleepTimestamp = resultSet.getLong(2);
					if (cursorSleepTimestamp != sleepTimestampByAT.get(atAddress)) {
						staleCursorATs.add(atAddress);
						continue;
					}

					Integer nextHeight = resultSet.getInt(3);
					if (nextHeight == 0 && resultSet.wasNull()) {
						nextIncomingByAT.put(atAddress, null);
						continue;
					}

					int nextSequence = resultSet.getInt(4);
					byte[] nextSignature = resultSet.getBytes(5);
					nextIncomingByAT.put(atAddress, new NextTransactionInfo(nextHeight, nextSequence, nextSignature));
				} while (resultSet.next());
		} catch (SQLException e) {
			throw new DataException("Unable to fetch AT next incoming cursors", e);
		}

		Set<String> missingOrStaleATs = new LinkedHashSet<>(atAddresses);
		missingOrStaleATs.removeAll(foundCursorATs);
		missingOrStaleATs.addAll(staleCursorATs);

		for (String atAddress : missingOrStaleATs) {
			recomputeNextIncomingForAT(atAddress);
			NextTransactionInfo nextTransactionInfo = fetchNextIncomingCursor(atAddress, sleepTimestampByAT.get(atAddress));
			if (nextTransactionInfo != null)
				nextIncomingByAT.put(atAddress, nextTransactionInfo);
			else
				nextIncomingByAT.put(atAddress, null);
		}

		return nextIncomingByAT;
	}

	private List<String> fetchATIncomingRecipientsForBlock(int height) throws DataException {
		List<String> affectedATs = new ArrayList<>();
		String sql = "SELECT DISTINCT AT_address FROM ("
				+ "SELECT PT.recipient AS AT_address "
				+ "FROM PaymentTransactions PT "
				+ "JOIN Transactions T USING (signature) "
				+ "JOIN ATs ON ATs.AT_address = PT.recipient "
				+ "WHERE T.block_height = ? "
				+ "UNION "
				+ "SELECT MT.recipient AS AT_address "
				+ "FROM MessageTransactions MT "
				+ "JOIN Transactions T USING (signature) "
				+ "JOIN ATs ON ATs.AT_address = MT.recipient "
				+ "WHERE T.block_height = ? "
				+ "UNION "
				+ "SELECT ATT.recipient AS AT_address "
				+ "FROM ATTransactions ATT "
				+ "JOIN Transactions T USING (signature) "
				+ "JOIN ATs ON ATs.AT_address = ATT.recipient "
				+ "WHERE T.block_height = ?"
				+ ") AS IncomingATs";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, height, height, height)) {
			if (resultSet != null)
				do {
					affectedATs.add(resultSet.getString(1));
				} while (resultSet.next());
		} catch (SQLException e) {
			throw new DataException("Unable to fetch AT incoming recipients for block", e);
		}

		return affectedATs;
	}

	private String buildInsertATIncomingTransactionsSql(String whereClause) {
		String paymentWhere = whereClause == null ? "" : "WHERE " + whereClause + " ";
		String messageWhere = whereClause == null ? "" : "WHERE " + whereClause + " ";
		String atWhere = whereClause == null ? "" : "WHERE " + whereClause + " ";

		return "INSERT INTO ATIncomingTransactions (AT_address, block_height, block_sequence, signature) "
				+ "SELECT AT_address, block_height, block_sequence, signature FROM ("
				+ "SELECT PT.recipient AS AT_address, T.block_height, T.block_sequence, T.signature "
				+ "FROM PaymentTransactions PT "
				+ "JOIN Transactions T USING (signature) "
				+ "JOIN ATs ON ATs.AT_address = PT.recipient "
				+ paymentWhere
				+ "UNION "
				+ "SELECT MT.recipient AS AT_address, T.block_height, T.block_sequence, T.signature "
				+ "FROM MessageTransactions MT "
				+ "JOIN Transactions T USING (signature) "
				+ "JOIN ATs ON ATs.AT_address = MT.recipient "
				+ messageWhere
				+ "UNION "
				+ "SELECT ATT.recipient AS AT_address, T.block_height, T.block_sequence, T.signature "
				+ "FROM ATTransactions ATT "
				+ "JOIN Transactions T USING (signature) "
				+ "JOIN ATs ON ATs.AT_address = ATT.recipient "
				+ atWhere
				+ ") AS Incoming "
				+ "WHERE block_height IS NOT NULL AND block_sequence IS NOT NULL";
	}

	private NextTransactionInfo fetchNextIncomingCursor(String atAddress, long expectedSleepTimestamp) throws DataException {
		String sql = "SELECT block_height, block_sequence, signature "
				+ "FROM ATNextIncoming "
				+ "WHERE AT_address = ? AND sleep_until_message_timestamp = ? "
				+ "LIMIT 1";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, atAddress, expectedSleepTimestamp)) {
			if (resultSet == null)
				return null;

			Integer nextHeight = resultSet.getInt(1);
			if (nextHeight == 0 && resultSet.wasNull())
				return null;

			return new NextTransactionInfo(nextHeight, resultSet.getInt(2), resultSet.getBytes(3));
		} catch (SQLException e) {
			throw new DataException("Unable to fetch recomputed AT next incoming cursor", e);
		}
	}

	private NextTransactionInfo findNextTransactionFromIncoming(String recipient, int height, int sequence) throws DataException {
		NextTransactionInfo sameHeightNext = findNextTransactionFromIncomingWithSql(
				"SELECT block_height, block_sequence, signature "
						+ "FROM ATIncomingTransactions "
						+ "WHERE AT_address = ? AND block_height = ? AND block_sequence > ? "
						+ "ORDER BY block_sequence ASC, signature ASC "
						+ "LIMIT 1",
				recipient, height, sequence);
		if (sameHeightNext != null)
			return sameHeightNext;

		return findNextTransactionFromIncomingWithSql(
				"SELECT block_height, block_sequence, signature "
						+ "FROM ATIncomingTransactions "
						+ "WHERE AT_address = ? AND block_height > ? "
						+ "ORDER BY block_height ASC, block_sequence ASC, signature ASC "
						+ "LIMIT 1",
				recipient, height);
	}

	private NextTransactionInfo findNextTransactionFromIncomingWithSql(String sql, Object... bindParams) throws DataException {
		try (ResultSet resultSet = this.repository.checkedExecute(sql, bindParams)) {
			if (resultSet == null)
				return null;

			return new NextTransactionInfo(resultSet.getInt(1), resultSet.getInt(2), resultSet.getBytes(3));
		} catch (SQLException e) {
			throw new DataException("Unable to find next transaction from AT incoming cache", e);
		}
	}

	private NextTransactionInfo findNextTransactionCanonical(String recipient, int height, int sequence) throws DataException {
		// We only need to search for a subset of transaction types: MESSAGE, PAYMENT or AT

		String sql = "SELECT block_height, block_sequence, Transactions.signature "
				+ "FROM ("
					+ "SELECT signature FROM PaymentTransactions WHERE recipient = ? "
					+ "UNION "
					+ "SELECT signature FROM MessageTransactions WHERE recipient = ? "
					+ "UNION "
					+ "SELECT signature FROM ATTransactions WHERE recipient = ?"
				+ ") AS SelectedTransactions "
				+ "JOIN Transactions USING (signature) "
				+ "WHERE (block_height > ? OR (block_height = ? AND block_sequence > ?)) "
				+ "ORDER BY block_height ASC, block_sequence ASC "
				+ "LIMIT 1";

		try (ResultSet resultSet = this.repository.checkedExecute(sql, recipient, recipient, recipient, height, height, sequence)) {
			if (resultSet == null)
				return null;

			return new NextTransactionInfo(resultSet.getInt(1), resultSet.getInt(2), resultSet.getBytes(3));
		} catch (SQLException e) {
			throw new DataException("Unable to find canonical next transaction to AT from repository", e);
		}
	}

	@Override
	public NextTransactionInfo findNextTransaction(String recipient, int height, int sequence) throws DataException {
		ATExecInstrumentation inst = ATExecInstrumentation.peek();
		long tCall = System.nanoTime();
		try {
			return findNextTransactionFromIncoming(recipient, height, sequence);
		} finally {
			if (inst != null)
				inst.noteRepoFindNext(System.nanoTime() - tCall);
		}
	}

	// Other

	public void checkConsistency() throws DataException {
		String missingSql = "SELECT ATs.AT_address, LatestATState.height FROM ATs "
				+ "CROSS JOIN LATERAL("
					+ "SELECT height, state_hash FROM ATStates "
					+ "WHERE ATStates.AT_address = ATs.AT_address "
					+ "ORDER BY AT_address DESC, height DESC "
					+ "LIMIT 1"
				+ ") AS LatestATState (height, state_hash) "
				+ "LEFT OUTER JOIN ATStateBlobs "
				+ "ON ATStateBlobs.state_hash = LatestATState.state_hash "
				+ "LEFT OUTER JOIN ATStatesData "
				+ "ON ATStatesData.AT_address = ATs.AT_address AND ATStatesData.height = LatestATState.height "
				+ "WHERE ATStateBlobs.state_hash IS NULL AND ATStatesData.AT_address IS NULL";

		try (ResultSet resultSet = this.repository.checkedExecute(missingSql)) {
			if (resultSet != null)
				throw new DataException("Missing latest AT state data/blob for " + resultSet.getString(1)
						+ " at height " + resultSet.getInt(2));
		} catch (SQLException e) {
			throw new DataException("Unable to check AT repository consistency", e);
		}

		String verifySql = "SELECT ATs.AT_address, LatestATState.height, LatestATState.state_hash, "
				+ "ATStateBlobs.state_data, ATStatesData.state_data "
				+ "FROM ATs "
				+ "CROSS JOIN LATERAL("
					+ "SELECT height, state_hash FROM ATStates "
					+ "WHERE ATStates.AT_address = ATs.AT_address "
					+ "ORDER BY AT_address DESC, height DESC "
					+ "LIMIT 1"
				+ ") AS LatestATState (height, state_hash) "
				+ "LEFT OUTER JOIN ATStateBlobs "
				+ "ON ATStateBlobs.state_hash = LatestATState.state_hash "
				+ "LEFT OUTER JOIN ATStatesData "
				+ "ON ATStatesData.AT_address = ATs.AT_address AND ATStatesData.height = LatestATState.height";

		try (ResultSet resultSet = this.repository.checkedExecute(verifySql)) {
			if (resultSet == null)
				return;

			do {
				String atAddress = resultSet.getString(1);
				int height = resultSet.getInt(2);
				byte[] stateHash = resultSet.getBytes(3);
				byte[] blobStateData = resultSet.getBytes(4);
				byte[] legacyStateData = resultSet.getBytes(5);

				verifyAndResolveStateData(atAddress, height, stateHash, blobStateData, legacyStateData, true);
			} while (resultSet.next());
		} catch (SQLException e) {
			throw new DataException("Unable to verify AT repository consistency", e);
		}
	}

}
