package org.qortal.at;

import org.apache.logging.log4j.Logger;

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

	public static double ms(long ns) {
		return ns / 1_000_000.0;
	}

	private static String rateMicrosEach(long ns, long count) {
		if (count == 0)
			return "(n/a)";
		return String.format("%.4f µs each", ms(ns) * 1000.0 / count);
	}

	/** Structured INFO lines after executeATs completes — one logger line per sub-step bucket. */
	public void logExecuteATsDetailed(Logger log, String tag, int blockHeight, int unfinishedAtCount,
			int newAtStatesThisBlock) {

		long loopAccountedNanos = exec_sum_constructAtNanos + exec_sum_run_wallNanos + exec_sum_loop_iterationBookkeepingNanos;
		long unaccountedLoopNanos = Math.max(0L, exec_loopWallNanos - loopAccountedNanos);

		long runAccountedNanosPlusBookkeepingExecPath = aggregateRunTrackedNanos();

		long unaccountedRunWallNanosTotal = Math.max(0L, exec_sum_run_wallNanos - runAccountedNanosPlusBookkeepingExecPath);
		double unaccountedRunAvgMsEachRun = unfinishedAtCount == 0 ? 0.0 : ms(unaccountedRunWallNanosTotal) / unfinishedAtCount;

		log.info("{} blockHeight={} step=EXECUTE_ATS_OVERVIEW wall_ms={} fetch_ms={} queue_select_open_ms={} queue_materialize_ms={} "
						+ "latest_state_ms={} vm_execute_ms={} findNext_will_ms={} findNext_machine_ms={} run_loop_ms={} "
						+ "postpend_ms={} sort_ms={} iterations={} ran={} sleeping_skips={} emitted_states={}",
				tag,
				blockHeight,
				ms(exec_wallTotalNanos),
				ms(exec_fetch_executable_totalNanos),
				ms(exec_repo_fetch_executable_openResultSetNanos),
				ms(exec_repo_fetch_executable_iterateRowsNanos),
				ms(exec_sum_postWill_getLatestStateNanos),
				ms(exec_sum_machine_executeNanos),
				ms(exec_repo_findNext_will_execute_phaseNanos),
				ms(exec_repo_findNext_machine_execute_phaseNanos),
				ms(exec_loopWallNanos),
				ms(exec_prepending_addAllNanos),
				ms(exec_prepending_sortNanos),
				exec_loopIterations,
				unfinishedAtCount,
				exec_run_earlyExit_afterWillNanosCount,
				newAtStatesThisBlock);

		log.info("{} blockHeight={} step=EXECUTE_ATS_wall_total_ms={} unfinishedAtCount={} newAtStatesThisBlock={}",
				tag,
				blockHeight, ms(exec_wallTotalNanos), unfinishedAtCount, newAtStatesThisBlock);

		log.info("{} blockHeight={} step=EXEC_guard_ourAtStates_null_checks_ms={}", tag, blockHeight,
				ms(exec_preconditionNanoseconds));
		log.info("{} blockHeight={} step=EXEC_allocate_List_and_init_ourStates_fees_arrays_ms={}", tag, blockHeight,
				ms(exec_allocateListsNanos));

		log.info("{} blockHeight={} step=EXEC_fetch_getAllExecutableATs_wall_including_repository_ms={}", tag,
				blockHeight,
				ms(exec_fetch_executable_totalNanos));

		log.info("{} blockHeight={} step=REPO_getAllExecutable_open_result_and_stmt_ms={}", tag, blockHeight,
				ms(exec_repo_fetch_executable_openResultSetNanos));

		log.info("{} blockHeight={} step=REPO_getAllExecutable_iterate_rows_materialize_ATData_rows_ms={}", tag,
				blockHeight,
				ms(exec_repo_fetch_executable_iterateRowsNanos));

		log.info("{} blockHeight={} step=EXEC_run_loop_exclusive_wall_between_first_and_last_AT_run_ms={}", tag,
				blockHeight, ms(exec_loopWallNanos));

		log.info("{} blockHeight={} step=CROSSCHECK_loop_wall_minus_construct_run_bookkeeping_residual_ms={} " +
						"(construct_ms={} summed_run_wall_ms={} bookmarkkeeping_ms={})",
				tag,
				blockHeight, ms(unaccountedLoopNanos), ms(exec_sum_constructAtNanos),
				ms(exec_sum_run_wallNanos), ms(exec_sum_loop_iterationBookkeepingNanos));

		log.info("{} blockHeight={} step=LOOP_iterations_total={}", tag, blockHeight, exec_loopIterations);

		log.info("{} blockHeight={} step=COUNTS_ran_AT_run_iterations={}", tag, blockHeight,
				unfinishedAtCount);

		log.info("{} blockHeight={} step=COUNTS_will_early_return_sleeping_skip={} entered_post_will_path={}",
				tag, blockHeight, exec_run_earlyExit_afterWillNanosCount, exec_run_passed_will_execute_count);

		log.info("{} blockHeight={} step=COUNTS_execute_vm_but_no_emit_state_row={}", tag, blockHeight,
				exec_run_earlyExit_vmNoOpCount);

		log.info("{} blockHeight={} step=COUNTS_emitted_new_ATState_row={}", tag, blockHeight,
				exec_run_finishedWithStateCount);

		log.info("{} blockHeight={} step=AGG_sum_AT_constructor_calls_ms={} ({})", tag, blockHeight,
				ms(exec_sum_constructAtNanos), rateMicrosEach(exec_sum_constructAtNanos, unfinishedAtCount));

		log.info("{} blockHeight={} step=MICRO_iteration_bookkeeping_after_run_collect_state_ms={}", tag, blockHeight,
				ms(exec_sum_loop_iterationBookkeepingNanos));

		log.info("{} blockHeight={} step=AGG_sum_run_wall_inside_AT_run_accumulated_ms={} ({})", tag, blockHeight,
				ms(exec_sum_run_wallNanos), rateMicrosEach(exec_sum_run_wallNanos, unfinishedAtCount));

		log.info("{} blockHeight={} step=AGG_run_wall_less_all_instrumented_subphases_residual_ms={}", tag, blockHeight,
				ms(exec_sum_run_wallNanos - runAccountedNanosPlusBookkeepingExecPath));

		log.info("{} blockHeight={} step=SANITY_residual_run_wall_average_ms_per_loop_iteration_when_nonzero={}",
				tag, blockHeight, unaccountedRunAvgMsEachRun);

		log.info("{} blockHeight={} step=MICRO_inside_run_QortalATAPI_constructor_accumulated_ms={}", tag,
				blockHeight,
				ms(exec_sum_run_qortalApiCtorNanos));

		log.info("{} blockHeight={} step=MICRO_inside_run_QortalAtLoggerFactory_getInstance_accumulated_ms={}", tag,
				blockHeight,
				ms(exec_sum_run_loggerFactoryNanos));

		log.info("{} blockHeight={} step=WILL_EXECUTE_total_inside_Qortal_accumulated_wall_ms={} ({})", tag, blockHeight,
				ms(exec_sum_willExecute_totalNanos),
				rateMicrosEach(exec_sum_willExecute_totalNanos, unfinishedAtCount));

		log.info("{} blockHeight={} step=WILL_no_sleep_until_message_timestamp_fast_TRUE count={} sum_ms={}", tag,
				blockHeight,
				exec_sum_willExecute_noSleepTs_fastTrueCount,
				ms(exec_sum_willExecute_noSleepTs_fastTrueNanos));

		log.info("{} blockHeight={} step=WILL_branch_sleep_but_wake_via_height_WITHOUT_findNext count={} sum_ms={}", tag,
				blockHeight,
				exec_sum_willExecute_sleepWakeByHeightOnlyCount,
				ms(exec_sum_willExecute_sleepWakeByHeightOnlyNanos));

		log.info("{} blockHeight={} step=WILL_branch_sleep_wake_REQUIRED_find_next_message_PATH count={} javaAroundRepo_prep_sum_ms={} " +
						"(repo FIND_NEXT_for_will_is_attributed_below)",
				tag,
				blockHeight, exec_sum_willExecute_sleepFindNextWakeCount,
				ms(exec_sum_willExecute_sleepFindNextPrepNanos));

		log.info("{} blockHeight={} step=WILL_branch_return_FALSE_still_waiting count={} sum_ms={}", tag, blockHeight,
				exec_sum_willExecute_returnFalse_sleepingCount,
				ms(exec_sum_willExecute_returnFalse_sleepNanos));

		log.info("{} blockHeight={} step=REPO_under_session_getLatestATState_open_ResultSet_wall_ms={}", tag,
				blockHeight,
				ms(exec_repo_getLatest_openResultSetNanos));

		log.info("{} blockHeight={} step=REPO_under_session_getLatestATState_read_cells_and_blob_ms={}", tag,
				blockHeight,
				ms(exec_repo_getLatest_readRowNanos));

		log.info("{} blockHeight={} step=AGG_AT_run_java_sum_get_repository_getLatest_include_overhead_ms={} ({})", tag,
				blockHeight,
				ms(exec_sum_postWill_getLatestStateNanos),
				rateMicrosEach(exec_sum_postWill_getLatestStateNanos, Math.max(1L, exec_run_passed_will_execute_count)));

		log.info("{} blockHeight={} step=MICRO_JAVA_after_latest_latestAtState_null_guard_wall_ms={}", tag, blockHeight,
				ms(exec_sum_afterLatest_nullGuardNanos));

		log.info("{} blockHeight={} step=MICRO_JAVA_resolve_code_byte_array_reference_ms={}", tag, blockHeight,
				ms(exec_sum_afterLatest_fetchCodeBytesRefNanos));

		log.info("{} blockHeight={} step=RUN_CIYAM_MachineState_fromBytes_ms={} ({})", tag, blockHeight,
				ms(exec_sum_machine_fromBytesNanos),
				rateMicrosEach(exec_sum_machine_fromBytesNanos, Math.max(1L, exec_run_passed_will_execute_count)));

		log.info("{} blockHeight={} step=RUN_CIYAM_QortalATAPI_preExecute_ms={}", tag, blockHeight,
				ms(exec_sum_machine_preExecuteNanos));

		log.info("{} blockHeight={} step=RUN_CIYAM_MACHINE_MACHINEEXECUTE_EXECUTE_ms={} ({})", tag, blockHeight,
				ms(exec_sum_machine_executeNanos),
				rateMicrosEach(exec_sum_machine_executeNanos, Math.max(1L, exec_run_passed_will_execute_count)));

		log.info("{} blockHeight={} step=RUN_postMachineState_toBytes_for_persist_candidate_ms={}", tag, blockHeight,
				ms(exec_sum_postVm_toBytesNanos));

		log.info("{} blockHeight={} step=RUN_postMachineState_Crypto_digest_stateHash_ms={}", tag, blockHeight,
				ms(exec_sum_postVm_digestStateNanos));

		log.info("{} blockHeight={} step=RUN_postMachineState_compare_steps_hashes_frozen_earlyExit_ms={}", tag,
				blockHeight,
				ms(exec_sum_postVm_noopCompareNanos));

		log.info("{} blockHeight={} step=RUN_CIYAM_calc_final_fees_using_step_counter_ms={}", tag, blockHeight,
				ms(exec_sum_postVm_calcFinalFeesNanos));

		log.info("{} blockHeight={} step=RUN_read_remaining_sleep_until_message_timestamp_field_ms={}", tag,
				blockHeight,
				ms(exec_sum_postVm_readSleepTsNanos));

		log.info("{} blockHeight={} step=RUN_construct_new_ATState_Java_object_before_return_ms={}", tag, blockHeight,
				ms(exec_sum_postVm_newAtStateNanos));

		log.info("{} blockHeight={} step=RUN_return_generated_AT_transactions_list_clone_ms={}", tag, blockHeight,
				ms(exec_sum_postVm_getApiTransactionsNanos));

		log.info("{} blockHeight={} step=REPO_findNext_WHILE_WILL_EXECUTE_phase_wall_ms={} calls={}", tag, blockHeight,
				ms(exec_repo_findNext_will_execute_phaseNanos), exec_repo_findNext_will_execute_calls);

		log.info("{} blockHeight={} step=REPO_findNext_DURING_CIYAM_MACHINE_EXECUTE_phase_wall_ms={} calls={}", tag,
				blockHeight,
				ms(exec_repo_findNext_machine_execute_phaseNanos),
				exec_repo_findNext_machine_execute_calls);

		log.info("{} blockHeight={} step=REPO_findNext_OTHER_PHASES_NONE_POSTWILL_wall_ms={} calls={}", tag,
				blockHeight,
				ms(exec_repo_findNext_other_phaseNanos),
				exec_repo_findNext_other_calls);

		log.info("{} blockHeight={} step=EXEC_postpend_foreach_set_APPROVAL_NOT_REQUIRED_ON_AT_TX_accumulated_ms={}", tag,
				blockHeight,
				ms(exec_prepending_setApprovalNanos));

		log.info("{} blockHeight={} step=EXEC_prepend_generated_AT_transactions_into_block_list_addAll_accumulated_ms={}",
				tag,
				blockHeight, ms(exec_prepending_addAllNanos));

		log.info("{} blockHeight={} step=EXEC_sort_transactions_by_Comparator_accumulated_after_prepend_ms={}", tag,
				blockHeight, ms(exec_prepending_sortNanos));
	}

	private long aggregateRunTrackedNanos() {
		return exec_sum_run_qortalApiCtorNanos + exec_sum_run_loggerFactoryNanos + exec_sum_willExecute_totalNanos
				+ exec_sum_postWill_getLatestStateNanos + exec_sum_afterLatest_nullGuardNanos
				+ exec_sum_afterLatest_fetchCodeBytesRefNanos + exec_sum_machine_fromBytesNanos
				+ exec_sum_machine_preExecuteNanos + exec_sum_machine_executeNanos + exec_sum_postVm_toBytesNanos
				+ exec_sum_postVm_digestStateNanos + exec_sum_postVm_noopCompareNanos + exec_sum_postVm_calcFinalFeesNanos
				+ exec_sum_postVm_readSleepTsNanos + exec_sum_postVm_newAtStateNanos
				+ exec_sum_postVm_getApiTransactionsNanos;
	}

	public void logProcessAtFeesAndStates(Logger log, String tag, int blockHeight) {

		long sumSlices = apply_fees_sum_accountCtorNanos + apply_fees_sum_modifyBalanceNanos
				+ apply_fees_sum_fromAtAddressNanos + apply_fees_sum_atConstructNanos
				+ apply_fees_sum_update_wallNanos
				+ apply_fees_sum_update_saveAtStateNanos
				+ apply_fees_sum_update_batchRuntimeStateNanos
				+ apply_fees_sum_update_recomputeNextIncomingNanos
				+ apply_fees_sum_update_recomputeExecutionQueueNanos
				+ apply_fees_sum_update_currentStatePointerNanos;

		log.info("{} blockHeight={} step=AT_FEES_STATES_OVERVIEW wall_ms={} rows={} modify_balance_ms={} save_state_total_ms={} "
						+ "save_atstates_row_ms={} save_state_blobs_ms={} save_legacy_blob_ms={} save_runtime_ms={} "
						+ "save_runtime_full_ms={} save_runtime_current_height_only_ms={} runtime_full_count={} runtime_current_height_only_count={} "
						+ "recompute_next_incoming_ms={} recompute_execution_queue_ms={} save_definition_total_ms={} residual_ms={}",
				tag,
				blockHeight,
				ms(apply_fees_wallTotalNanos),
				apply_fees_rowCount,
				ms(apply_fees_sum_modifyBalanceNanos),
				ms(apply_fees_sum_update_saveAtStateNanos),
				ms(apply_fees_sum_update_saveAtStatesRowNanos),
				ms(apply_fees_sum_update_saveATStateBlobsNanos),
				ms(apply_fees_sum_update_saveAtStatesDataBlobNanos),
				ms(apply_fees_sum_update_batchRuntimeStateNanos),
				ms(apply_fees_sum_update_batchRuntimeFullNanos),
				ms(apply_fees_sum_update_batchRuntimeCurrentHeightNanos),
				apply_fees_runtimeFullUpdateCount,
				apply_fees_runtimeCurrentHeightOnlyUpdateCount,
				ms(apply_fees_sum_update_recomputeNextIncomingNanos),
				ms(apply_fees_sum_update_recomputeExecutionQueueNanos),
				ms(apply_fees_sum_update_saveAtRowNanos),
				ms(apply_fees_wallTotalNanos - sumSlices));

		log.info("{} blockHeight={} step=APPLY_processAtFeesAndStates_GUARD_and_loop_fence_wall_total_ms={} row_count={}",
				tag,
				blockHeight,
				ms(apply_fees_wallTotalNanos), apply_fees_rowCount);

		log.info("{} blockHeight={} step=APPLY_summed_new_Account_constructors_ms={}", tag, blockHeight,
				ms(apply_fees_sum_accountCtorNanos));

		log.info("{} blockHeight={} step=APPLY_summed_modify_account_QORT_negative_fee_ms={}", tag, blockHeight,
				ms(apply_fees_sum_modifyBalanceNanos));

		log.info("{} blockHeight={} step=APPLY_summed_ATRepository_lookup_fromATAddress_ms={}", tag, blockHeight,
				ms(apply_fees_sum_fromAtAddressNanos));

		log.info("{} blockHeight={} step=APPLY_summed_AT_instance_wrapped_constructor_calls_ms={}", tag, blockHeight,
				ms(apply_fees_sum_atConstructNanos));

		log.info("{} blockHeight={} step=APPLY_summed_AT_update_JAVA_ENTIRE_wall_per_row_aggregate_ms={}", tag,
				blockHeight, ms(apply_fees_sum_update_wallNanos));

		log.info("{} blockHeight={} step=BREAKDOWN_AT_update_CIYAM_flags_parse_only_ms={}", tag, blockHeight,
				ms(apply_fees_sum_update_flagsOnlyFromBytesNanos));

		log.info("{} blockHeight={} step=BREAKDOWN_AT_update_repository_SAVE_AT_StateData_row_ms={}", tag, blockHeight,
				ms(apply_fees_sum_update_saveAtStateNanos));

		log.info("{} blockHeight={} step=BREAKDOWN_AT_update_repository_FETCH_previous_AT_state_heights_ms={}", tag,
				blockHeight, ms(apply_fees_sum_update_fetchPreviousHeightNanos));

		log.info("{} blockHeight={} step=BREAKDOWN_AT_update_repository_SAVE_ATStates_row_only_ms={}", tag,
				blockHeight, ms(apply_fees_sum_update_saveAtStatesRowNanos));

		log.info("{} blockHeight={} step=BREAKDOWN_AT_update_repository_SAVE_ATStates_split lock_wait_ms={} mark_tx_ms={} prepare_ms={} bind_rows_ms={} execute_batch_ms={}",
				tag,
				blockHeight,
				ms(apply_fees_sum_update_checkpointReadLockWaitNanos),
				ms(apply_fees_sum_update_markTransactionStartedNanos),
				ms(apply_fees_sum_update_prepareAtStatesStatementNanos),
				ms(apply_fees_sum_update_bindAtStatesRowsNanos),
				ms(apply_fees_sum_update_executeAtStatesRowsNanos));

		log.info("{} blockHeight={} step=BREAKDOWN_AT_update_repository_SAVE_ATStateBlobs_content_addressed_ms={} rows={} bytes={} avg_bytes={}",
				tag,
				blockHeight,
				ms(apply_fees_sum_update_saveATStateBlobsNanos),
				apply_fees_saveATStateBlobCount,
				apply_fees_saveATStateBlobBytes,
				apply_fees_saveATStateBlobCount == 0 ? 0L : apply_fees_saveATStateBlobBytes / apply_fees_saveATStateBlobCount);

		log.info("{} blockHeight={} step=BREAKDOWN_AT_update_repository_SAVE_ATStateBlobs_split candidates={} existing={} missing={} lookup_ms={} prepare_ms={} bind_rows_ms={} execute_batch_ms={}",
				tag,
				blockHeight,
				apply_fees_candidateATStateBlobCount,
				apply_fees_existingATStateBlobCount,
				apply_fees_saveATStateBlobCount,
				ms(apply_fees_sum_update_lookupATStateBlobHashesNanos),
				ms(apply_fees_sum_update_prepareATStateBlobsStatementNanos),
				ms(apply_fees_sum_update_bindATStateBlobRowsNanos),
				ms(apply_fees_sum_update_executeATStateBlobRowsNanos));

		log.info("{} blockHeight={} step=BREAKDOWN_AT_update_repository_SAVE_ATStatesData_legacy_blob_only_ms={} rows={} bytes={} avg_bytes={}",
				tag,
				blockHeight,
				ms(apply_fees_sum_update_saveAtStatesDataBlobNanos),
				apply_fees_saveAtStatesDataBlobCount,
				apply_fees_saveAtStatesDataBlobBytes,
				apply_fees_saveAtStatesDataBlobCount == 0 ? 0L : apply_fees_saveAtStatesDataBlobBytes / apply_fees_saveAtStatesDataBlobCount);

		log.info("{} blockHeight={} step=BREAKDOWN_AT_update_repository_UPDATE_ATCurrentState_pointer_ms={}", tag,
				blockHeight, ms(apply_fees_sum_update_currentStatePointerNanos));

		log.info("{} blockHeight={} step=BREAKDOWN_AT_update_repository_DELETE_ATStatesData_blob_ms={}", tag,
				blockHeight, ms(apply_fees_sum_update_deleteAtStatesDataBlobNanos));

		log.info("{} blockHeight={} step=BREAKDOWN_AT_update_java_sync_fields_between_ATData_and_CIYAM_flags_ms={}", tag,
				blockHeight, ms(apply_fees_sum_update_populateMetaNanos));

		log.info("{} blockHeight={} step=BREAKDOWN_AT_update_repository_SAVE_updated_AT_definition_row_metadata_ms={}", tag,
				blockHeight,
				ms(apply_fees_sum_update_saveAtRowNanos));

		log.info("{} blockHeight={} step=BREAKDOWN_AT_update_repository_BATCH_UPDATE_AT_runtime_metadata_only_ms={}", tag,
				blockHeight, ms(apply_fees_sum_update_batchRuntimeStateNanos));

		log.info("{} blockHeight={} step=BREAKDOWN_AT_update_repository_RECOMPUTE_ATNextIncoming_after_runtime_update_ms={}", tag,
				blockHeight, ms(apply_fees_sum_update_recomputeNextIncomingNanos));

		log.info("{} blockHeight={} step=BREAKDOWN_AT_update_repository_RECOMPUTE_ATExecutionQueue_after_runtime_update_ms={}", tag,
				blockHeight, ms(apply_fees_sum_update_recomputeExecutionQueueNanos));

		log.info("{} blockHeight={} step=SANITY_fee_row_slice_sum_equals_ms={} wall_minus_slices_residual_ms={}", tag,
				blockHeight,
				ms(sumSlices), ms(apply_fees_wallTotalNanos - sumSlices));
	}

	public void logAtTransactionsApply(Logger log, String tag, int blockHeight) {
		log.info("{} blockHeight={} step=APPLY_saved_AT_TRANSACTION_rows_ms={}", tag, blockHeight,
				ms(apply_tx_saveNanos));

		log.info("{} blockHeight={} step=APPLY_processed_transaction_process_on_AT_ROWS_ms={}", tag, blockHeight,
				ms(apply_tx_processNanos));

		log.info("{} blockHeight={} step=APPLY_transaction_process_refs_and_fee_metadata_updates_ms={}", tag,
				blockHeight, ms(apply_tx_referenceFeesNanos));

		log.info("{} blockHeight={} step=APPLY_at_transaction_logical_rows_touched_Count={}", tag, blockHeight,
				apply_tx_atTxnCount);
	}
}
