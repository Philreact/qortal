package org.qortal.at;

/**
 * Thread-bound nanosecond timings for CIYAM AT pipeline profiling (typically during {@link org.qortal.block.Block#executeATs()}).
 */
public final class ATExecInstrumentation {

	public enum VmPhase {
		NONE,
		WILL_EXECUTE,
		POST_WILL,
		MACHINE_EXECUTE
	}

	private static final ThreadLocal<ATExecInstrumentation> CURRENT = new ThreadLocal<>();

	private VmPhase vmPhase = VmPhase.NONE;

	// --------- Block.executeATs ----------
	public long exec_preconditionNanoseconds;
	public long exec_allocateListsNanos;
	public long exec_fetch_executable_totalNanos;

	public long exec_loopWallNanos;

	public long exec_prepending_setApprovalNanos;
	public long exec_prepending_addAllNanos;
	public long exec_prepending_sortNanos;

	public long exec_wallTotalNanos;

	// --------- Summed AT.run(...) across loop ----------
	public int exec_loopIterations;
	public int exec_run_earlyExit_afterWillNanosCount;
	public int exec_run_earlyExit_vmNoOpCount;
	public int exec_run_finishedWithStateCount;
	/** Passed willExecute -- next step loads latest VM state */
	public int exec_run_passed_will_execute_count;

	public long exec_sum_constructAtNanos;

	public long exec_sum_run_qortalApiCtorNanos;
	public long exec_sum_run_loggerFactoryNanos;
	public long exec_sum_run_wallNanos;

	// willExecute
	public long exec_sum_willExecute_totalNanos;

	public int exec_sum_willExecute_sleepWakeByHeightOnlyCount;
	public long exec_sum_willExecute_sleepWakeByHeightOnlyNanos;

	public int exec_sum_willExecute_sleepFindNextWakeCount;
	public long exec_sum_willExecute_sleepFindNextPrepNanos;

	public int exec_sum_willExecute_returnFalse_sleepingCount;
	public long exec_sum_willExecute_returnFalse_sleepNanos;

	/** Branch: no sleep-until-message timestamp — willExecute returns true immediately */
	public int exec_sum_willExecute_noSleepTs_fastTrueCount;
	public long exec_sum_willExecute_noSleepTs_fastTrueNanos;

	// POST will (but before MachineState.execute): load machine + prep
	public long exec_sum_postWill_getLatestStateNanos;
	public long exec_sum_afterLatest_nullGuardNanos;

	public long exec_sum_afterLatest_fetchCodeBytesRefNanos;

	public long exec_sum_machine_fromBytesNanos;

	public long exec_sum_machine_preExecuteNanos;
	public long exec_sum_machine_executeNanos;

	public long exec_sum_postVm_toBytesNanos;
	public long exec_sum_postVm_digestStateNanos;

	public long exec_sum_postVm_noopCompareNanos;

	public long exec_sum_postVm_calcFinalFeesNanos;
	public long exec_sum_postVm_readSleepTsNanos;
	public long exec_sum_postVm_newAtStateNanos;
	public long exec_sum_postVm_getApiTransactionsNanos;

	public long exec_sum_loop_iterationBookkeepingNanos;

	// --------- Latest ATState DB (during execute AT loop, session active only) ----------
	public long exec_repo_getLatest_openResultSetNanos;
	public long exec_repo_getLatest_readRowNanos;

	// --------- getAllExecutableATs ----------
	public long exec_repo_fetch_executable_openResultSetNanos;
	public long exec_repo_fetch_executable_iterateRowsNanos;

	// --------- findNextTransaction (during session: split by VmPhase when called) ----------
	public long exec_repo_findNext_will_execute_phaseNanos;
	public long exec_repo_findNext_machine_execute_phaseNanos;
	public long exec_repo_findNext_other_phaseNanos;
	public int exec_repo_findNext_will_execute_calls;
	public int exec_repo_findNext_machine_execute_calls;
	public int exec_repo_findNext_other_calls;

	// --------- processAtFeesAndStates totals ----------
	public long apply_fees_wallTotalNanos;

	public long apply_fees_sum_accountCtorNanos;
	public long apply_fees_sum_modifyBalanceNanos;
	public long apply_fees_sum_fromAtAddressNanos;
	public long apply_fees_sum_atConstructNanos;

	public long apply_fees_sum_update_wallNanos;

	public long apply_fees_sum_update_flagsOnlyFromBytesNanos;
	public long apply_fees_sum_update_saveAtStateNanos;
	public long apply_fees_sum_update_fetchPreviousHeightNanos;
	public long apply_fees_sum_update_saveAtStatesRowNanos;
	public long apply_fees_sum_update_checkpointReadLockWaitNanos;
	public long apply_fees_sum_update_markTransactionStartedNanos;
	public long apply_fees_sum_update_prepareAtStatesStatementNanos;
	public long apply_fees_sum_update_bindAtStatesRowsNanos;
	public long apply_fees_sum_update_executeAtStatesRowsNanos;
	public long apply_fees_sum_update_saveATStateBlobsNanos;
	public long apply_fees_sum_update_lookupATStateBlobHashesNanos;
	public long apply_fees_sum_update_prepareATStateBlobsStatementNanos;
	public long apply_fees_sum_update_bindATStateBlobRowsNanos;
	public long apply_fees_sum_update_executeATStateBlobRowsNanos;
	public long apply_fees_sum_update_saveAtStatesDataBlobNanos;
	public long apply_fees_sum_update_currentStatePointerNanos;
	public long apply_fees_sum_update_deleteAtStatesDataBlobNanos;
	public int apply_fees_candidateATStateBlobCount;
	public int apply_fees_existingATStateBlobCount;
	public int apply_fees_saveATStateBlobCount;
	public long apply_fees_saveATStateBlobBytes;
	public int apply_fees_saveAtStatesDataBlobCount;
	public long apply_fees_saveAtStatesDataBlobBytes;
	public long apply_fees_sum_update_populateMetaNanos;
	public long apply_fees_sum_update_saveAtRowNanos;
	public long apply_fees_sum_update_batchRuntimeStateNanos;
	public long apply_fees_sum_update_batchRuntimeFullNanos;
	public long apply_fees_sum_update_batchRuntimeCurrentHeightNanos;
	public long apply_fees_sum_update_recomputeNextIncomingNanos;
	public long apply_fees_sum_update_recomputeExecutionQueueNanos;

	public int apply_fees_rowCount;
	public int apply_fees_runtimeFullUpdateCount;
	public int apply_fees_runtimeCurrentHeightOnlyUpdateCount;

	// --------- AT transactions in processTransactions ----------
	public long apply_tx_saveNanos;
	public long apply_tx_processNanos;
	public long apply_tx_referenceFeesNanos;
	public int apply_tx_atTxnCount;

	public static void bind(ATExecInstrumentation inst) {
		CURRENT.set(inst);
	}

	public static void unbind() {
		CURRENT.remove();
	}

	public static ATExecInstrumentation peek() {
		return CURRENT.get();
	}

	public void setVmPhase(VmPhase phase) {
		this.vmPhase = phase != null ? phase : VmPhase.NONE;
	}

	public VmPhase vmPhase() {
		return vmPhase;
	}

	public void noteRepoFindNext(long nanosElapsed) {
		switch (vmPhase) {
			case WILL_EXECUTE:
				exec_repo_findNext_will_execute_phaseNanos += nanosElapsed;
				exec_repo_findNext_will_execute_calls++;
				break;
			case MACHINE_EXECUTE:
				exec_repo_findNext_machine_execute_phaseNanos += nanosElapsed;
				exec_repo_findNext_machine_execute_calls++;
				break;
			default:
				exec_repo_findNext_other_phaseNanos += nanosElapsed;
				exec_repo_findNext_other_calls++;
				break;
		}
	}

}
