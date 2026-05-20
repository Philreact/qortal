package org.qortal.repository;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.block.Block;
import org.qortal.crypto.Crypto;
import org.qortal.data.at.ATData;
import org.qortal.data.block.BlockData;
import org.qortal.data.transaction.ATTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.gui.SplashFrame;
import org.qortal.settings.Settings;
import org.qortal.transaction.Transaction;
import org.qortal.transform.block.BlockTransformation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static org.qortal.transaction.Transaction.TransactionType.AT;

public abstract class RepositoryManager {
	private static final Logger LOGGER = LogManager.getLogger(RepositoryManager.class);

	private static final int AT_DERIVED_CACHES_BUILD_VERSION = 1;

	private static RepositoryFactory repositoryFactory = null;

	/** null if no checkpoint requested, TRUE for quick checkpoint, false for slow/full checkpoint. */
	private static Boolean quickCheckpointRequested = null;

	public static RepositoryFactory getRepositoryFactory() {
		return repositoryFactory;
	}

	public static void setRepositoryFactory(RepositoryFactory newRepositoryFactory) {
		repositoryFactory = newRepositoryFactory;
	}

	public static boolean wasPristineAtOpen() throws DataException {
		if (repositoryFactory == null)
			throw new DataException("No repository available");

		return repositoryFactory.wasPristineAtOpen();
	}

	public static Repository getRepository() throws DataException {
		if (repositoryFactory == null)
			throw new DataException("No repository available");

		return repositoryFactory.getRepository();
	}

	public static Repository tryRepository() throws DataException {
		if (repositoryFactory == null)
			throw new DataException("No repository available");

		return repositoryFactory.tryRepository();
	}

	public static void closeRepositoryFactory() throws DataException {
		repositoryFactory.close();
		repositoryFactory = null;
	}

	public static void backup(boolean quick, String name, Long timeout) throws TimeoutException {
		try (final Repository repository = getRepository()) {
			repository.backup(quick, name, timeout);
		} catch (DataException e) {
			// Backup is best-effort so don't complain
		}
	}

	public static boolean needsTransactionSequenceRebuild(Repository repository) throws DataException {
		// Check if we have any transactions without a block_sequence
		List<byte[]> testSignatures = repository.getTransactionRepository().getSignaturesMatchingCustomCriteria(
				null, Arrays.asList("block_height IS NOT NULL AND block_sequence IS NULL"), new ArrayList<>(), 100);
		if (testSignatures.isEmpty()) {
			// block_sequence intact, so assume complete
			return false;
		}

		return true;
	}

	public static boolean rebuildTransactionSequences(Repository repository) throws DataException {
		if (Settings.getInstance().isLite()) {
			// Lite nodes have no blockchain
			return false;
		}
		if (Settings.getInstance().isTopOnly()) {
			// topOnly nodes are unable to perform this reindex, and so are temporarily unsupported
			throw new DataException("topOnly nodes are now unsupported, as they are missing data required for a db reshape");
		}

		try {
			// Check if we have any unpopulated block_sequence values for the first 1000 blocks
			if (!needsTransactionSequenceRebuild(repository)) {
				// block_sequence already populated for the first 1000 blocks, so assume complete.
				// We avoid checkpointing and prevent the node from starting up in the case of a rebuild failure, so
				// we shouldn't ever be left in a partially rebuilt state.
				return false;
			}

			LOGGER.info("Rebuilding transaction sequences - this will take a while...");

			SplashFrame.getInstance().updateStatus("Rebuilding transactions - please wait...");

			int blockchainHeight = repository.getBlockRepository().getBlockchainHeight();
			int totalTransactionCount = 0;

			for (int height = 1; height <= blockchainHeight; ++height) {
				List<TransactionData> inputTransactions = new ArrayList<>();

				// Fetch block and transactions
				BlockData blockData = repository.getBlockRepository().fromHeight(height);
				boolean loadedFromArchive = false;
				if (blockData == null) {
					// Get (non-AT) transactions from the archive
					BlockTransformation blockTransformation = BlockArchiveReader.getInstance().fetchBlockAtHeight(height);
					blockData = blockTransformation.getBlockData();
					inputTransactions = blockTransformation.getTransactions(); // This doesn't include AT transactions
					loadedFromArchive = true;
				}
				else {
					// Get transactions from db
					Block block = new Block(repository, blockData);
					for (Transaction transaction : block.getTransactions()) {
						inputTransactions.add(transaction.getTransactionData());
					}
				}

				if (blockData == null) {
					throw new DataException("Missing block data");
				}

				List<TransactionData> transactions = new ArrayList<>();

				if (loadedFromArchive) {
					List<TransactionData> transactionDataList = new ArrayList<>(blockData.getTransactionCount());
					// Fetch any AT transactions in this block
					List<byte[]> atSignatures = repository.getTransactionRepository().getSignaturesMatchingCriteria(null, null, height, height);
					for (byte[] s : atSignatures) {
						TransactionData transactionData = repository.getTransactionRepository().fromSignature(s);
						if (transactionData.getType() == AT) {
							transactionDataList.add(transactionData);
						}
					}

					List<ATTransactionData> atTransactions = new ArrayList<>();
					for (TransactionData transactionData : transactionDataList) {
						ATTransactionData atTransactionData = (ATTransactionData) transactionData;
						atTransactions.add(atTransactionData);
					}

					// Create sorted list of ATs by creation time
					List<ATData> ats = new ArrayList<>();

					for (ATTransactionData atTransactionData : atTransactions) {
						ATData atData = repository.getATRepository().fromATAddress(atTransactionData.getATAddress());
						boolean hasExistingEntry = ats.stream().anyMatch(a -> Objects.equals(a.getATAddress(), atTransactionData.getATAddress()));
						if (!hasExistingEntry) {
							ats.add(atData);
						}
					}

					// Sort list of ATs by creation date
					ats.sort(Comparator.comparingLong(ATData::getCreation));

					// Loop through unique ATs
					for (ATData atData : ats) {
						List<ATTransactionData> thisAtTransactions = atTransactions.stream()
								.filter(t -> Objects.equals(t.getATAddress(), atData.getATAddress()))
								.collect(Collectors.toList());

						int count = thisAtTransactions.size();

						if (count == 1) {
							ATTransactionData atTransactionData = thisAtTransactions.get(0);
							transactions.add(atTransactionData);
						}
						else if (count == 2) {
							String atCreatorAddress = Crypto.toAddress(atData.getCreatorPublicKey());

							ATTransactionData atTransactionData1 = thisAtTransactions.stream()
									.filter(t -> !Objects.equals(t.getRecipient(), atCreatorAddress))
									.findFirst().orElse(null);
							transactions.add(atTransactionData1);

							ATTransactionData atTransactionData2 = thisAtTransactions.stream()
									.filter(t -> Objects.equals(t.getRecipient(), atCreatorAddress))
									.findFirst().orElse(null);
							transactions.add(atTransactionData2);
						}
						else if (count > 2) {
							LOGGER.info("Error: AT has more than 2 output transactions");
						}
					}
				}

				// Add all the regular transactions now that AT transactions have been handled
				transactions.addAll(inputTransactions);
				totalTransactionCount += transactions.size();

				// Loop through and update sequences
				for (int sequence = 0; sequence < transactions.size(); ++sequence) {
					TransactionData transactionData = transactions.get(sequence);

					// Update transaction's sequence in repository
					repository.getTransactionRepository().updateBlockSequence(transactionData.getSignature(), sequence);
				}

				if (height % 10000 == 0) {
					LOGGER.info("Rebuilt sequences for {} blocks (total transactions: {})", height, totalTransactionCount);
				}

				repository.saveChanges();
			}

			LOGGER.info("Completed rebuild of transaction sequences.");
			return true;
		}
		catch (DataException e) {
			LOGGER.info("Unable to rebuild transaction sequences: {}. The database may have been left in an inconsistent state.", e.getMessage());

			// Throw an exception so that the node startup is halted, allowing for a retry next time.
			repository.discardChanges();
			throw new DataException("Rebuild of transaction sequences failed.");
		}
	}

	public static void populateATDerivedCachesIfNecessary(Repository repository) throws DataException {
		if (Settings.getInstance().isLite())
			return;

		Connection connection = repository.getConnection();

		try {
			int databaseVersion = org.qortal.repository.hsqldb.HSQLDBDatabaseUpdates.fetchDatabaseVersion(connection);

			if (databaseVersion <= 52 || isATDerivedCachesPopulated(connection))
				return;

			SplashFrame.getInstance().updateStatus("Rebuilding AT indexes - please wait...");
			LOGGER.info("Rebuilding AT derived caches after database upgrade...");

			boolean autoCommit = connection.getAutoCommit();
			connection.setAutoCommit(false);

			try (Statement stmt = connection.createStatement()) {
				// ATStateBlobs is content-addressed, so duplicate state hashes are inserted once and shared by all
				// ATStates rows. The existing repository helper performs hash de-duplication safely during rollout.
				repository.getATRepository().rebuildATStateBlobs();

				// Rebuild the current-state pointer before ATRuntime so the runtime rows inherit the latest state height.
				repository.getATRepository().rebuildATCurrentStates();

				stmt.execute("DELETE FROM ATRuntime");
				stmt.execute("INSERT INTO ATRuntime (AT_address, is_sleeping, sleep_until_height, is_finished, had_fatal_error, "
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
						+ "LEFT OUTER JOIN ATCurrentState ON ATCurrentState.AT_address = ATs.AT_address");

				repository.getATRepository().rebuildATIncomingTransactions();
				repository.getATRepository().rebuildATNextIncoming();

				stmt.execute("DELETE FROM ATExecutionQueue");
				stmt.execute("INSERT INTO ATExecutionQueue (AT_address, next_height) "
						+ "SELECT ATRuntime.AT_address, "
						+ "CASE "
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
						+ "END "
						+ "FROM ATRuntime "
						+ "LEFT OUTER JOIN ATNextIncoming ON ATNextIncoming.AT_address = ATRuntime.AT_address "
						+ "WHERE ATRuntime.is_finished = false "
						+ "AND (ATRuntime.sleep_until_message_timestamp IS NULL "
							+ "OR (ATRuntime.sleep_until_height IS NOT NULL AND ATRuntime.sleep_until_height != 0) "
							+ "OR (ATNextIncoming.block_height IS NOT NULL "
								+ "AND ATNextIncoming.sleep_until_message_timestamp = ATRuntime.sleep_until_message_timestamp))");

				try (PreparedStatement updateFlagStatement = connection.prepareStatement(
						"UPDATE DatabaseInfo SET at_derived_caches_build_version = ?")) {
					updateFlagStatement.setInt(1, AT_DERIVED_CACHES_BUILD_VERSION);
					updateFlagStatement.executeUpdate();
				}

				connection.commit();
				connection.setAutoCommit(autoCommit);
			} catch (SQLException | DataException e) {
				connection.rollback();
				connection.setAutoCommit(autoCommit);
				throw e;
			}

			LOGGER.info("Completed AT derived cache rebuild.");
		} catch (SQLException e) {
			throw new DataException("Unable to populate AT derived caches", e);
		} finally {
			SplashFrame.getInstance().updateStatus("Proceeding to Start Qortal ...");
		}
	}

	private static boolean isATDerivedCachesPopulated(Connection connection) throws SQLException {
		String sql = "SELECT at_derived_caches_build_version FROM DatabaseInfo WHERE at_derived_caches_build_version >= ?";

		try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
			preparedStatement.setInt(1, AT_DERIVED_CACHES_BUILD_VERSION);

			try (ResultSet resultSet = preparedStatement.executeQuery()) {
				return resultSet.next();
			}
		}
	}

	public static void setRequestedCheckpoint(Boolean quick) {
		quickCheckpointRequested = quick;
	}

	public static Boolean getRequestedCheckpoint() {
		return quickCheckpointRequested;
	}

	public static void rebuild() throws DataException {
		RepositoryFactory oldRepositoryFactory = repositoryFactory;

		// Grab handle repository reference before we close
		Repository oldRepository = oldRepositoryFactory.getRepository();

		// Use old repository reference to perform rebuild
		oldRepository.rebuild();

		repositoryFactory = oldRepositoryFactory.reopen();
	}

	public static boolean isDeadlockRelated(Throwable e) {
		Throwable cause = e.getCause();

		return SQLException.class.isInstance(cause) && repositoryFactory.isDeadlockException((SQLException) cause);
	}

	public static boolean canArchiveOrPrune() {
		try (final Repository repository = getRepository()) {
			return repository.getATRepository().hasAtStatesHeightIndex();
		} catch (DataException e) {
			return false;
		}
	}

}
