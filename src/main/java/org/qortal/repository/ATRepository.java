package org.qortal.repository;

import org.qortal.data.at.ATData;
import org.qortal.data.at.ATStateData;
import org.qortal.utils.ByteArray;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ATRepository {

	// CIYAM AutomatedTransactions

	/** Returns ATData using AT's address or null if none found */
	public ATData fromATAddress(String atAddress) throws DataException;

	public List<ATData> fromATAddresses(List<String> atAddresses) throws DataException;

	/** Returns where AT with passed address exists in repository */
	public boolean exists(String atAddress) throws DataException;

	/** Returns AT creator's public key, or null if not found */
	public byte[] getCreatorPublicKey(String atAddress) throws DataException;

	/** Returns list of executable ATs, empty if none found */
	public List<ATData> getAllExecutableATs() throws DataException;

	/** Returns ATs scheduled to execute at the supplied block height, empty if none found. */
	public List<ATData> getExecutableATs(int blockHeight) throws DataException;

	/** Returns list of ATs with matching code hash, optionally executable only. */
	public List<ATData> getATsByFunctionality(byte[] codeHash, Boolean isExecutable, Integer limit, Integer offset, Boolean reverse) throws DataException;

	/** Returns list of all ATs matching one of passed code hashes, optionally executable only. */
	public List<ATData> getAllATsByFunctionality(Set<ByteArray> codeHashes, Boolean isExecutable) throws DataException;

	/** Returns creation block height given AT's address or null if not found */
	public Integer getATCreationBlockHeight(String atAddress) throws DataException;

	/** Saves ATData into repository */
	public void save(ATData atData) throws DataException;

	/** Updates mutable AT execution state without rewriting immutable deployment/code fields. */
	public void updateRuntimeState(ATData atData) throws DataException;

	/** Batch updates mutable AT execution state without rewriting immutable deployment/code fields. */
	public void updateRuntimeStates(Collection<ATData> atDataList) throws DataException;

	/** Batch updates mutable AT execution state and current state height without rewriting immutable deployment/code fields. */
	public void updateRuntimeStates(Collection<ATData> atDataList, Integer currentStateHeight) throws DataException;

	/** Removes an AT from repository, including associated ATStateData */
	public void delete(String atAddress) throws DataException;

	// AT States

	/**
	 * Returns ATStateData for an AT at given height.
	 * 
	 * @param atAddress
	 *            - AT's address
	 * @param height
	 *            - block height
	 * @return ATStateData for AT at given height or null if none found
	 */
	public ATStateData getATStateAtHeight(String atAddress, int height) throws DataException;

	/**
	 * Returns latest ATStateData for an AT.
	 * <p>
	 * As ATs don't necessarily run every block, this will return the <tt>ATStateData</tt> with the greatest height.
	 * 
	 * @param atAddress
	 *            - AT's address
	 * @return ATStateData for AT with greatest height or null if none found
	 */
	public ATStateData getLatestATState(String atAddress) throws DataException;

	public List<ATStateData> getLatestATStates(List<String> collect) throws DataException;

	/** Returns current executable ATStateData for ATs from canonical AT state history. */
	public List<ATStateData> getCurrentATStates(List<String> atAddresses) throws DataException;

	/** Rebuild current executable AT state pointer cache from canonical AT state data rows. */
	public void rebuildATCurrentStates() throws DataException;

	/** Rebuild content-addressed AT state blobs from legacy per-height AT state data rows. */
	public void rebuildATStateBlobs() throws DataException;

	/** Returns count of content-addressed AT state blobs, primarily for consistency tests. */
	public int getATStateBlobCount() throws DataException;

	/** Update current executable AT state pointer for AT. */
	public void updateCurrentATState(String atAddress, int height) throws DataException;

	/** Batch update current executable AT state pointers for ATs at the same height. */
	public void updateCurrentATStates(Collection<String> atAddresses, int height) throws DataException;

	/** Recompute current executable AT state pointer for AT after delete/orphan. */
	public void revertCurrentATState(String atAddress) throws DataException;

	/**
	 * Returns final ATStateData for ATs matching codeHash (required)
	 * and specific data segment value (optional).
	 * <p>
	 * If searching for specific data segment value, both <tt>dataByteOffset</tt>
	 * and <tt>expectedValue</tt> need to be non-null.
	 * <p>
	 * Note that <tt>dataByteOffset</tt> starts from 0 and will typically be
	 * a multiple of <tt>MachineState.VALUE_SIZE</tt>, which is usually 8:
	 * width of a long.
	 * <p>
	 * Although <tt>expectedValue</tt>, if provided, is natively an unsigned long,
	 * the data segment comparison is done via unsigned hex string.
	 */
	public List<ATStateData> getMatchingFinalATStates(byte[] codeHash, byte[] buyerPublicKey, byte[] sellerPublicKey, Boolean isFinished,
													  Integer dataByteOffset, Long expectedValue, Integer minimumFinalHeight,
													  Integer limit, Integer offset, Boolean reverse) throws DataException;

	/**
	 * Returns final ATStateData for ATs matching codeHash (required)
	 * and specific data segment value (optional), returning at least
	 * <tt>minimumCount</tt> entries over a span of at least
	 * <tt>minimumPeriod</tt> ms, given enough entries in repository.
	 * <p>
	 * If searching for specific data segment value, both <tt>dataByteOffset</tt>
	 * and <tt>expectedValue</tt> need to be non-null.
	 * <p>
	 * Note that <tt>dataByteOffset</tt> starts from 0 and will typically be
	 * a multiple of <tt>MachineState.VALUE_SIZE</tt>, which is usually 8:
	 * width of a long.
	 * <p>
	 * Although <tt>expectedValue</tt>, if provided, is natively an unsigned long,
	 * the data segment comparison is done via unsigned hex string.
	 */
	public List<ATStateData> getMatchingFinalATStatesQuorum(byte[] codeHash, Boolean isFinished,
			Integer dataByteOffset, Long expectedValue,
			int minimumCount, int maximumCount, long minimumPeriod) throws DataException;

	/**
	 * Returns all ATStateData for a given block height.
	 * <p>
	 * Unlike <tt>getATState</tt>, only returns <i>partial</i> ATStateData saved at the given height.
	 *
	 * @param height
	 *            - block height
	 * @return list of ATStateData for given height, empty list if none found
	 * @throws DataException
	 */
	public List<ATStateData> getBlockATStatesAtHeight(int height) throws DataException;


	/** Rebuild the latest AT states cache, necessary for AT state trimming/pruning.
	 * <p>
	 * NOTE: performs implicit <tt>repository.saveChanges()</tt>.
	 */
	public void rebuildLatestAtStates(int maxHeight) throws DataException;


	/** Returns height of first trimmable AT state. */
	public int getAtTrimHeight() throws DataException;

	/** Sets new base height for AT state trimming.
	 * <p>
	 * NOTE: performs implicit <tt>repository.saveChanges()</tt>.
	 */
	public void setAtTrimHeight(int trimHeight) throws DataException;

	/** Trims full AT state data between passed heights. Returns number of trimmed rows. */
	public int trimAtStates(int minHeight, int maxHeight, int limit) throws DataException;


	/** Returns height of first prunable AT state. */
	public int getAtPruneHeight() throws DataException;

	/** Sets new base height for AT state pruning.
	 * <p>
	 * NOTE: performs implicit <tt>repository.saveChanges()</tt>.
	 */
	public void setAtPruneHeight(int pruneHeight) throws DataException;

	/** Prunes full AT state data between passed heights. Returns number of pruned rows. */
	public int pruneAtStates(int minHeight, int maxHeight) throws DataException;


	/** Checks for the presence of the ATStatesHeightIndex in repository */
	public boolean hasAtStatesHeightIndex() throws DataException;


	/**
	 * Save ATStateData into repository.
	 * <p>
	 * Note: Requires at least these <tt>ATStateData</tt> properties to be filled, or an <tt>IllegalArgumentException</tt> will be thrown:
	 * <p>
	 * <ul>
	 * <li><tt>creation</tt></li>
	 * <li><tt>stateHash</tt></li>
	 * <li><tt>height</tt></li>
	 * </ul>
	 * 
	 * @param atStateData
	 * @throws IllegalArgumentException
	 */
	public void save(ATStateData atStateData) throws DataException;

	/**
	 * Save ATStateData into repository, optionally updating the derived current-state pointer.
	 * <p>
	 * Use {@code updateCurrentState = false} only when the caller advances the current pointer separately, e.g. as part
	 * of the batched AT runtime metadata update after a block's AT state rows have all been saved.
	 */
	public void save(ATStateData atStateData, boolean updateCurrentState) throws DataException;

	/**
	 * Save ATStateData into repository, optionally writing the legacy per-height state bytes row.
	 * <p>
	 * Content-addressed ATStateBlobs are always written when state bytes are present. Skipping legacy bytes is intended
	 * only for the block hot path after blob storage has been verified.
	 */
	public void save(ATStateData atStateData, boolean updateCurrentState, boolean writeLegacyStateData) throws DataException;

	/**
	 * Batch saves complete AT state rows.
	 * <p>
	 * Use {@code updateCurrentState = false} when the caller advances the current pointer separately, e.g. as part of
	 * the batched AT runtime metadata update for one block.
	 */
	public void save(Collection<ATStateData> atStateDataList, boolean updateCurrentState) throws DataException;

	/**
	 * Batch saves complete AT state rows, optionally skipping legacy ATStatesData bytes.
	 * <p>
	 * Content-addressed ATStateBlobs are always written when state bytes are present.
	 */
	public void save(Collection<ATStateData> atStateDataList, boolean updateCurrentState, boolean writeLegacyStateData) throws DataException;

	/** Delete AT's state data at this height */
	public void delete(String atAddress, int height) throws DataException;

	/** Delete state data for all ATs at this height */
	public void deleteATStates(int height) throws DataException;

	// Finding transactions for ATs to process

	static class NextTransactionInfo {
		public final int height;
		public final int sequence;
		public final byte[] signature;

		public NextTransactionInfo(int height, int sequence, byte[] signature) {
			this.height = height;
			this.sequence = sequence;
			this.signature = signature;
		}
	}

	static class IncomingTransactionInfo {
		public final String atAddress;
		public final int height;
		public final int sequence;
		public final byte[] signature;

		public IncomingTransactionInfo(String atAddress, int height, int sequence, byte[] signature) {
			this.atAddress = atAddress;
			this.height = height;
			this.sequence = sequence;
			this.signature = signature;
		}
	}

	/**
	 * Find next transaction for AT to process.
	 * <p>
	 * @param recipient AT address
	 * @param height starting height
	 * @param sequence starting sequence
	 * @return next transaction info, or null if none found
	 */
	public NextTransactionInfo findNextTransaction(String recipient, int height, int sequence) throws DataException;

	/**
	 * Rebuild derived AT incoming transaction cache from canonical transaction tables.
	 * <p>
	 * This cache is not consensus truth and can be safely rebuilt.
	 */
	public void rebuildATIncomingTransactions() throws DataException;

	/** Rebuild derived AT next-incoming cursor cache from current AT runtime state and AT incoming cache. */
	public void rebuildATNextIncoming() throws DataException;

	/** Rebuild all derived AT incoming/cursor tables. */
	public void rebuildATIncomingCaches() throws DataException;

	/** Rebuild derived AT execution queue from current runtime and incoming cursor state. */
	public void rebuildATExecutionQueue() throws DataException;

	/**
	 * Record AT-recipient transactions for a linked block and return affected AT addresses.
	 */
	public List<String> recordATIncomingTransactionsForBlock(int height) throws DataException;

	/**
	 * Record supplied AT-recipient transaction candidates for a linked block and return affected AT addresses.
	 */
	public List<String> recordATIncomingTransactionsForBlock(int height, Collection<IncomingTransactionInfo> incomingTransactions) throws DataException;

	/**
	 * Delete AT-recipient transactions for an orphaned block and return affected AT addresses.
	 */
	public List<String> deleteATIncomingTransactionsForBlock(int height) throws DataException;

	/** Recompute next incoming cursor for a single AT from canonical AT runtime state and derived inbox. */
	public void recomputeNextIncomingForAT(String atAddress) throws DataException;

	/** Recompute next incoming cursors for multiple ATs. */
	public void recomputeNextIncomingForATs(Collection<String> atAddresses) throws DataException;

	/** Recompute derived execution queue row for one AT from current runtime and incoming cursor state. */
	public void recomputeATExecutionQueueForAT(String atAddress) throws DataException;

	/** Recompute derived execution queue rows for multiple ATs. */
	public void recomputeATExecutionQueueForATs(Collection<String> atAddresses) throws DataException;

	/**
	 * Batch-load next incoming cursors for ATs that are sleeping until message.
	 * <p>
	 * Implementations must validate cursor sleep timestamp against supplied ATData and rebuild stale/missing rows.
	 */
	public Map<String, NextTransactionInfo> getNextIncomingForATs(Collection<ATData> atDataList, int blockHeight) throws DataException;

	// Other

	public void checkConsistency() throws DataException;

}
