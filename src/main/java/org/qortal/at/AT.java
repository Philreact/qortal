package org.qortal.at;

import org.ciyam.at.MachineState;
import org.ciyam.at.Timestamp;
import org.qortal.crypto.Crypto;
import org.qortal.data.at.ATData;
import org.qortal.data.at.ATStateData;
import org.qortal.data.transaction.DeployAtTransactionData;
import org.qortal.repository.ATRepository;
import org.qortal.repository.ATRepository.NextTransactionInfo;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.transaction.AtTransaction;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AT {

	// Properties
	private Repository repository;
	private ATData atData;
	private ATStateData atStateData;
	/*
	 * Block.executeATs() now runs ATs in two phases: first a cheap wake check, then a batched latest-state load.
	 * Keep the API object created during the wake check so the second phase does not rebuild API state or change
	 * sleep/wake semantics compared with the original single-call run() path.
	 */
	private QortalATAPI preparedApi;

	// Constructors

	public AT(Repository repository, ATData atData, ATStateData atStateData) {
		this.repository = repository;
		this.atData = atData;
		this.atStateData = atStateData;
	}

	public AT(Repository repository, ATData atData) {
		this(repository, atData, null);
	}

	/** Constructs AT-handling object when deploying AT */
	public AT(Repository repository, DeployAtTransactionData deployATTransactionData) throws DataException {
		this.repository = repository;

		String atAddress = deployATTransactionData.getAtAddress();
		int height = this.repository.getBlockRepository().getBlockchainHeight() + 1;
		byte[] creatorPublicKey = deployATTransactionData.getCreatorPublicKey();
		long creation = deployATTransactionData.getTimestamp();
		long assetId = deployATTransactionData.getAssetId();

		// Just enough AT data to allow API to query initial balances, etc.
		ATData skeletonAtData = new ATData(atAddress, creatorPublicKey, creation, assetId);

		long blockTimestamp = Timestamp.toLong(height, 0);
		QortalATAPI api = new QortalATAPI(repository, skeletonAtData, blockTimestamp);
		QortalAtLoggerFactory loggerFactory = QortalAtLoggerFactory.getInstance();

		MachineState machineState = new MachineState(api, loggerFactory, deployATTransactionData.getCreationBytes());

		byte[] codeBytes = machineState.getCodeBytes();
		byte[] codeHash = Crypto.digest(codeBytes);

		this.atData = new ATData(atAddress, creatorPublicKey, creation, machineState.version, assetId, codeBytes, codeHash,
				machineState.isSleeping(), machineState.getSleepUntilHeight(), machineState.isFinished(), machineState.hadFatalError(),
				machineState.isFrozen(), machineState.getFrozenBalance(), null);

		byte[] stateData = machineState.toBytes();
		byte[] stateHash = Crypto.digest(stateData);

		this.atStateData = new ATStateData(atAddress, height, stateData, stateHash, 0L, true, null);
	}

	// Getters / setters

	public ATStateData getATStateData() {
		return this.atStateData;
	}

	public ATData getATData() {
		return this.atData;
	}

	// Processing

	public void deploy() throws DataException {
		ATRepository atRepository = this.repository.getATRepository();
		atRepository.save(this.atData);

		atRepository.save(this.atStateData);
	}

	public void undeploy() throws DataException {
		// AT states deleted implicitly by repository
		this.repository.getATRepository().delete(this.atData.getATAddress());
	}

	/**
	 * Potentially execute AT.
	 * <p>
	 * Note that sleep-until-message support might set/reset
	 * sleep-related flags/values.
	 * <p>
	 * {@link #getATStateData()} will return null if nothing happened.
	 * <p>
	 * @param blockHeight
	 * @param blockTimestamp
	 * @return AT-generated transactions, possibly empty
	 * @throws DataException
	 */
	public List<AtTransaction> run(int blockHeight, long blockTimestamp) throws DataException {
		ATExecInstrumentation inst = ATExecInstrumentation.peek();

		String atAddress = this.atData.getATAddress();

		long t = System.nanoTime();
		QortalATAPI api = new QortalATAPI(repository, this.atData, blockTimestamp);
		if (inst != null)
			inst.exec_sum_run_qortalApiCtorNanos += System.nanoTime() - t;

		t = System.nanoTime();
		QortalAtLoggerFactory loggerFactory = QortalAtLoggerFactory.getInstance();
		if (inst != null)
			inst.exec_sum_run_loggerFactoryNanos += System.nanoTime() - t;

		if (inst != null)
			inst.setVmPhase(ATExecInstrumentation.VmPhase.WILL_EXECUTE);
		if (!api.willExecute(blockHeight)) {
			if (inst != null)
				inst.exec_run_earlyExit_afterWillNanosCount++;
			if (inst != null)
				inst.setVmPhase(ATExecInstrumentation.VmPhase.NONE);
			// this.atStateData will be null
			return Collections.emptyList();
		}
		if (inst != null)
			inst.exec_run_passed_will_execute_count++;
		if (inst != null)
			inst.setVmPhase(ATExecInstrumentation.VmPhase.POST_WILL);

		t = System.nanoTime();
		ATStateData latestAtStateData = this.repository.getATRepository().getLatestATState(atAddress);
		if (inst != null)
			inst.exec_sum_postWill_getLatestStateNanos += System.nanoTime() - t;

		return runAfterWill(blockHeight, blockTimestamp, api, loggerFactory, latestAtStateData);
	}

	public boolean willExecute(int blockHeight, long blockTimestamp) throws DataException {
		return willExecute(blockHeight, blockTimestamp, null, false);
	}

	public boolean willExecute(int blockHeight, long blockTimestamp, NextTransactionInfo precomputedWake) throws DataException {
		return willExecute(blockHeight, blockTimestamp, precomputedWake, precomputedWake != null);
	}

	public boolean willExecute(int blockHeight, long blockTimestamp, NextTransactionInfo precomputedWake, boolean precomputedWakeAvailable) throws DataException {
		ATExecInstrumentation inst = ATExecInstrumentation.peek();

		long t = System.nanoTime();
		QortalATAPI api = new QortalATAPI(repository, this.atData, blockTimestamp);
		if (inst != null)
			inst.exec_sum_run_qortalApiCtorNanos += System.nanoTime() - t;

		if (inst != null)
			inst.setVmPhase(ATExecInstrumentation.VmPhase.WILL_EXECUTE);
		if (!api.willExecute(blockHeight, precomputedWake, precomputedWakeAvailable)) {
			if (inst != null) {
				inst.exec_run_earlyExit_afterWillNanosCount++;
				inst.setVmPhase(ATExecInstrumentation.VmPhase.NONE);
			}
			this.preparedApi = null;
			return false;
		}

		if (inst != null) {
			inst.exec_run_passed_will_execute_count++;
			inst.setVmPhase(ATExecInstrumentation.VmPhase.POST_WILL);
		}

		this.preparedApi = api;
		return true;
	}

	/*
	 * Hot-path entry used after Block.executeATs() batch-loads current AT states. The caller already performed the
	 * willExecute() decision, so this method continues with the exact same API object when available and only falls
	 * back to constructing one for legacy/direct callers.
	 */
	public List<AtTransaction> runWithLatestState(int blockHeight, long blockTimestamp, ATStateData latestAtStateData) throws DataException {
		ATExecInstrumentation inst = ATExecInstrumentation.peek();

		QortalATAPI api = this.preparedApi;
		this.preparedApi = null;
		if (api == null) {
			long t = System.nanoTime();
			api = new QortalATAPI(repository, this.atData, blockTimestamp);
			if (inst != null)
				inst.exec_sum_run_qortalApiCtorNanos += System.nanoTime() - t;
		}

		long t = System.nanoTime();
		QortalAtLoggerFactory loggerFactory = QortalAtLoggerFactory.getInstance();
		if (inst != null)
			inst.exec_sum_run_loggerFactoryNanos += System.nanoTime() - t;

		return runAfterWill(blockHeight, blockTimestamp, api, loggerFactory, latestAtStateData);
	}

	private List<AtTransaction> runAfterWill(int blockHeight, long blockTimestamp, QortalATAPI api,
			QortalAtLoggerFactory loggerFactory, ATStateData latestAtStateData) throws DataException {
		ATExecInstrumentation inst = ATExecInstrumentation.peek();

		String atAddress = this.atData.getATAddress();
		long t;

		t = System.nanoTime();
		if (latestAtStateData == null)
			throw new IllegalStateException("No previous AT state data found");
		if (inst != null)
			inst.exec_sum_afterLatest_nullGuardNanos += System.nanoTime() - t;

		t = System.nanoTime();
		byte[] codeBytes = this.atData.getCodeBytes();
		if (inst != null)
			inst.exec_sum_afterLatest_fetchCodeBytesRefNanos += System.nanoTime() - t;

		t = System.nanoTime();
		MachineState state = MachineState.fromBytes(api, loggerFactory, latestAtStateData.getStateData(), codeBytes);
		if (inst != null)
			inst.exec_sum_machine_fromBytesNanos += System.nanoTime() - t;

		try {
			t = System.nanoTime();
			api.preExecute(state);
			if (inst != null)
				inst.exec_sum_machine_preExecuteNanos += System.nanoTime() - t;

			if (inst != null)
				inst.setVmPhase(ATExecInstrumentation.VmPhase.MACHINE_EXECUTE);
			t = System.nanoTime();
			state.execute();
			if (inst != null) {
				inst.exec_sum_machine_executeNanos += System.nanoTime() - t;
				inst.setVmPhase(ATExecInstrumentation.VmPhase.NONE);
			}
		} catch (Exception e) {
			if (inst != null)
				inst.setVmPhase(ATExecInstrumentation.VmPhase.NONE);
			throw new DataException(String.format("Uncaught exception while running AT '%s'", atAddress), e);
		}

		t = System.nanoTime();
		byte[] stateData = state.toBytes();
		if (inst != null)
			inst.exec_sum_postVm_toBytesNanos += System.nanoTime() - t;

		t = System.nanoTime();
		byte[] stateHash = Crypto.digest(stateData);
		if (inst != null)
			inst.exec_sum_postVm_digestStateNanos += System.nanoTime() - t;

		t = System.nanoTime();
		if (state.getSteps() == 0 && Arrays.equals(stateHash, latestAtStateData.getStateHash())) {
			// We currently want to execute frozen ATs, to maintain backwards support.
			if (!state.isFrozen()) {
				if (inst != null) {
					inst.exec_sum_postVm_noopCompareNanos += System.nanoTime() - t;
					inst.exec_run_earlyExit_vmNoOpCount++;
				}
				// this.atStateData will be null
				return Collections.emptyList();
			}
		}
		if (inst != null)
			inst.exec_sum_postVm_noopCompareNanos += System.nanoTime() - t;

		t = System.nanoTime();
		long atFees = api.calcFinalFees(state);
		if (inst != null)
			inst.exec_sum_postVm_calcFinalFeesNanos += System.nanoTime() - t;

		t = System.nanoTime();
		Long sleepUntilMessageTimestamp = this.atData.getSleepUntilMessageTimestamp();
		if (inst != null)
			inst.exec_sum_postVm_readSleepTsNanos += System.nanoTime() - t;

		t = System.nanoTime();
		this.atStateData = new ATStateData(atAddress, blockHeight, stateData, stateHash, atFees, false, sleepUntilMessageTimestamp);
		if (inst != null) {
			inst.exec_sum_postVm_newAtStateNanos += System.nanoTime() - t;
			inst.exec_run_finishedWithStateCount++;
		}

		t = System.nanoTime();
		List<AtTransaction> out = api.getTransactions();
		if (inst != null)
			inst.exec_sum_postVm_getApiTransactionsNanos += System.nanoTime() - t;

		return out;
	}

	public void update(int blockHeight, long blockTimestamp) throws DataException {
		update(blockHeight, blockTimestamp, true);
	}

	public void update(int blockHeight, long blockTimestamp, boolean updateRuntimeState) throws DataException {
		update(blockHeight, blockTimestamp, updateRuntimeState, true);
	}

	public void update(int blockHeight, long blockTimestamp, boolean updateRuntimeState, boolean saveStateData) throws DataException {
		ATExecInstrumentation inst = ATExecInstrumentation.peek();
		long tRow = System.nanoTime();

		MachineState state = MachineState.flagsOnlyfromBytes(this.atStateData.getStateData());
		if (inst != null)
			inst.apply_fees_sum_update_flagsOnlyFromBytesNanos += System.nanoTime() - tRow;

		long tSav = System.nanoTime();
		if (saveStateData) {
			this.repository.getATRepository().save(this.atStateData, false);
			if (inst != null)
				inst.apply_fees_sum_update_saveAtStateNanos += System.nanoTime() - tSav;
		}

		long tMeta = System.nanoTime();
		this.atData.setIsSleeping(state.isSleeping());
		this.atData.setSleepUntilHeight(state.getSleepUntilHeight());
		this.atData.setIsFinished(state.isFinished());
		this.atData.setHadFatalError(state.hadFatalError());
		this.atData.setIsFrozen(state.isFrozen());
		this.atData.setFrozenBalance(state.getFrozenBalance());
		this.atData.setSleepUntilMessageTimestamp(this.atStateData.getSleepUntilMessageTimestamp());
		if (inst != null)
			inst.apply_fees_sum_update_populateMetaNanos += System.nanoTime() - tMeta;

		long tRowSave = System.nanoTime();
		if (updateRuntimeState) {
			this.repository.getATRepository().updateRuntimeState(this.atData);
			this.repository.getATRepository().recomputeNextIncomingForAT(this.atData.getATAddress());
			this.repository.getATRepository().recomputeATExecutionQueueForAT(this.atData.getATAddress());
		}
		if (inst != null) {
			inst.apply_fees_sum_update_saveAtRowNanos += System.nanoTime() - tRowSave;
			inst.apply_fees_sum_update_wallNanos += System.nanoTime() - tRow;
		}
	}

	public void revert(int blockHeight, long blockTimestamp) throws DataException {
		String atAddress = this.atData.getATAddress();

		// Delete old AT state data from repository
		this.repository.getATRepository().delete(atAddress, blockHeight);

		if (this.atStateData.isInitial())
			return;

		// Load previous state data
		ATStateData previousStateData = this.repository.getATRepository().getLatestATState(atAddress);
		if (previousStateData == null)
			throw new DataException("Can't find previous AT state data for " + atAddress);

		// Extract minimal/flags-only AT machine state using AT state data
		MachineState state = MachineState.flagsOnlyfromBytes(previousStateData.getStateData());

		// Update AT info in repository
		this.atData.setIsSleeping(state.isSleeping());
		this.atData.setSleepUntilHeight(state.getSleepUntilHeight());
		this.atData.setIsFinished(state.isFinished());
		this.atData.setHadFatalError(state.hadFatalError());
		this.atData.setIsFrozen(state.isFrozen());
		this.atData.setFrozenBalance(state.getFrozenBalance());

		// Special sleep-until-message support
		this.atData.setSleepUntilMessageTimestamp(previousStateData.getSleepUntilMessageTimestamp());

		this.repository.getATRepository().updateRuntimeState(this.atData);
		this.repository.getATRepository().recomputeNextIncomingForAT(atAddress);
		this.repository.getATRepository().recomputeATExecutionQueueForAT(atAddress);
	}

}
