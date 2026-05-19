package org.qortal.test.at;

import org.ciyam.at.MachineState;
import org.ciyam.at.Timestamp;
import org.junit.Before;
import org.junit.Test;
import org.qortal.account.PrivateKeyAccount;
import org.qortal.data.at.ATData;
import org.qortal.data.at.ATStateData;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.MessageTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.group.Group;
import org.qortal.repository.ATRepository.NextTransactionInfo;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.test.common.AtUtils;
import org.qortal.test.common.BlockUtils;
import org.qortal.test.common.Common;
import org.qortal.test.common.TransactionUtils;
import org.qortal.transaction.DeployAtTransaction;
import org.qortal.transaction.MessageTransaction;

import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class AtRepositoryTests extends Common {

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings();
	}

	@Test
	public void testGetATStateAtHeightWithData() throws DataException {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			// Mint a few blocks
			for (int i = 0; i < 10; ++i)
				BlockUtils.mintBlock(repository);

			Integer testHeight = 8;
			ATStateData atStateData = repository.getATRepository().getATStateAtHeight(atAddress, testHeight);

			assertEquals(testHeight, atStateData.getHeight());
			assertNotNull(atStateData.getStateData());
		}
	}

	@Test
	public void testGetATStateAtHeightWithoutData() throws DataException {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			// Mint a few blocks
			for (int i = 0; i < 10; ++i)
				BlockUtils.mintBlock(repository);

			int maxHeight = 8;
			Integer testHeight = maxHeight - 2;

			// Trim AT state data
			repository.getATRepository().rebuildLatestAtStates(maxHeight);
			repository.getATRepository().trimAtStates(2, maxHeight, 1000);

			ATStateData atStateData = repository.getATRepository().getATStateAtHeight(atAddress, testHeight);

			assertEquals(testHeight, atStateData.getHeight());
			assertNull(atStateData.getStateData());
		}
	}

	@Test
	public void testGetLatestATStateWithData() throws DataException {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			// Mint a few blocks
			for (int i = 0; i < 10; ++i)
				BlockUtils.mintBlock(repository);
			int blockchainHeight = repository.getBlockRepository().getBlockchainHeight();

			Integer testHeight = blockchainHeight;
			ATStateData atStateData = repository.getATRepository().getLatestATState(atAddress);

			assertEquals(testHeight, atStateData.getHeight());
			assertNotNull(atStateData.getStateData());
		}
	}

	@Test
	public void testGetLatestATStatesWithData() throws DataException {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			for (int i = 0; i < 10; ++i)
				BlockUtils.mintBlock(repository);
			int blockchainHeight = repository.getBlockRepository().getBlockchainHeight();

			List<ATStateData> atStatesData = repository.getATRepository().getLatestATStates(Arrays.asList(atAddress));

			assertEquals(1, atStatesData.size());
			assertEquals(atAddress, atStatesData.get(0).getATAddress());
			assertEquals(Integer.valueOf(blockchainHeight), atStatesData.get(0).getHeight());
			assertNotNull(atStatesData.get(0).getStateData());
		}
	}

	@Test
	public void testATCurrentStatePointerRebuildAdvanceAndRollback() throws Exception {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			for (int i = 0; i < 5; ++i)
				BlockUtils.mintBlock(repository);

			repository.getATRepository().rebuildATCurrentStates();

			ATStateData latestAtState = repository.getATRepository().getLatestATState(atAddress);
			List<ATStateData> currentAtStates = repository.getATRepository().getCurrentATStates(Arrays.asList(atAddress));

			assertEquals(1, currentAtStates.size());
			assertEquals(latestAtState.getATAddress(), currentAtStates.get(0).getATAddress());
			assertEquals(latestAtState.getHeight(), currentAtStates.get(0).getHeight());
			assertArrayEquals(latestAtState.getStateHash(), currentAtStates.get(0).getStateHash());
			assertNotNull(currentAtStates.get(0).getStateData());
			assertEquals(latestAtState.getHeight(), getATsCurrentStateHeight(repository, atAddress));
			assertEquals(latestAtState.getHeight(), getATRuntimeCurrentStateHeight(repository, atAddress));

			int syntheticHeight = latestAtState.getHeight() + 1;
			ATStateData syntheticAtState = new ATStateData(atAddress, syntheticHeight, latestAtState.getStateData(),
					latestAtState.getStateHash(), latestAtState.getFees(), false, latestAtState.getSleepUntilMessageTimestamp());
			repository.getATRepository().save(syntheticAtState);

			currentAtStates = repository.getATRepository().getCurrentATStates(Arrays.asList(atAddress));
			assertEquals(1, currentAtStates.size());
			assertEquals(Integer.valueOf(syntheticHeight), currentAtStates.get(0).getHeight());
			assertEquals(Integer.valueOf(syntheticHeight), getATsCurrentStateHeight(repository, atAddress));
			assertEquals(Integer.valueOf(syntheticHeight), getATRuntimeCurrentStateHeight(repository, atAddress));

			// Current-state reads must follow ATRuntime even if the old wide ATs mirror is stale.
			((HSQLDBRepository) repository).executeCheckedUpdate("UPDATE ATs SET current_state_height = ? WHERE AT_address = ?",
					latestAtState.getHeight(), atAddress);
			currentAtStates = repository.getATRepository().getCurrentATStates(Arrays.asList(atAddress));
			assertEquals(Integer.valueOf(syntheticHeight), currentAtStates.get(0).getHeight());
			assertEquals(Integer.valueOf(syntheticHeight), getATRuntimeCurrentStateHeight(repository, atAddress));

			// If one pointer is stale but another pointer is correct, previous_height must still use the greatest valid pointer.
			int nextSyntheticHeight = syntheticHeight + 1;
			ATStateData nextSyntheticAtState = new ATStateData(atAddress, nextSyntheticHeight, latestAtState.getStateData(),
					latestAtState.getStateHash(), latestAtState.getFees(), false, latestAtState.getSleepUntilMessageTimestamp());
			repository.getATRepository().save(nextSyntheticAtState);
			assertEquals(Integer.valueOf(syntheticHeight), getPreviousATStateHeight(repository, atAddress, nextSyntheticHeight));

			repository.getATRepository().delete(atAddress, nextSyntheticHeight);

			repository.getATRepository().delete(atAddress, syntheticHeight);

			latestAtState = repository.getATRepository().getLatestATState(atAddress);
			currentAtStates = repository.getATRepository().getCurrentATStates(Arrays.asList(atAddress));

			assertEquals(1, currentAtStates.size());
			assertEquals(latestAtState.getHeight(), currentAtStates.get(0).getHeight());
			assertArrayEquals(latestAtState.getStateHash(), currentAtStates.get(0).getStateHash());
			assertEquals(latestAtState.getHeight(), getATsCurrentStateHeight(repository, atAddress));
			assertEquals(latestAtState.getHeight(), getATRuntimeCurrentStateHeight(repository, atAddress));
		}
	}

	@Test
	public void testATRuntimeNullFieldsDoNotFallbackToStaleATsMirror() throws Exception {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			((HSQLDBRepository) repository).executeCheckedUpdate("UPDATE ATs SET sleep_until_height = ?, "
					+ "frozen_balance = ?, sleep_until_message_timestamp = ? WHERE AT_address = ?",
					123, 456L, 789L, atAddress);
			((HSQLDBRepository) repository).executeCheckedUpdate("UPDATE ATRuntime SET sleep_until_height = NULL, "
					+ "frozen_balance = NULL, sleep_until_message_timestamp = NULL WHERE AT_address = ?",
					atAddress);

			ATData atData = repository.getATRepository().fromATAddress(atAddress);
			assertNull(atData.getSleepUntilHeight());
			assertNull(atData.getFrozenBalance());
			assertNull(atData.getSleepUntilMessageTimestamp());
		}
	}

	@Test
	public void testATStateBlobDeduplicatesByStateHash() throws DataException {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			for (int i = 0; i < 5; ++i)
				BlockUtils.mintBlock(repository);

			ATStateData latestAtState = repository.getATRepository().getLatestATState(atAddress);
			int blobCount = repository.getATRepository().getATStateBlobCount();

			ATStateData duplicateAtState = new ATStateData(atAddress, latestAtState.getHeight() + 1,
					latestAtState.getStateData(), latestAtState.getStateHash(), latestAtState.getFees(), false,
					latestAtState.getSleepUntilMessageTimestamp());
			repository.getATRepository().save(duplicateAtState);

			assertEquals(blobCount, repository.getATRepository().getATStateBlobCount());
		}
	}

	@Test
	public void testLatestATStateCanReadFromStateBlobWithoutLegacyRow() throws Exception {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			for (int i = 0; i < 5; ++i)
				BlockUtils.mintBlock(repository);

			ATStateData latestAtState = repository.getATRepository().getLatestATState(atAddress);
			byte[] expectedStateData = latestAtState.getStateData();

			((HSQLDBRepository) repository).executeCheckedUpdate("DELETE FROM ATStatesData WHERE AT_address = ? AND height = ?",
					atAddress, latestAtState.getHeight());

			ATStateData resolvedAtState = repository.getATRepository().getLatestATState(atAddress);
			assertArrayEquals(expectedStateData, resolvedAtState.getStateData());

			repository.getATRepository().rebuildATCurrentStates();
			List<ATStateData> currentAtStates = repository.getATRepository().getCurrentATStates(Arrays.asList(atAddress));
			assertEquals(1, currentAtStates.size());
			assertArrayEquals(expectedStateData, currentAtStates.get(0).getStateData());
		}
	}

	@Test
	public void testBlockATStateHotPathSkipsLegacyBytesButKeepsStateReadable() throws Exception {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			BlockUtils.mintBlock(repository);

			ATStateData latestAtState = repository.getATRepository().getLatestATState(atAddress);
			assertNotNull(latestAtState.getStateData());

			try (ResultSet resultSet = ((HSQLDBRepository) repository).checkedExecute(
					"SELECT COUNT(*) FROM ATStatesData WHERE AT_address = ? AND height = ?",
					atAddress, latestAtState.getHeight())) {
				assertNotNull(resultSet);
				assertEquals(0, resultSet.getInt(1));
			}

			ATStateData atStateAtHeight = repository.getATRepository().getATStateAtHeight(atAddress, latestAtState.getHeight());
			assertArrayEquals(latestAtState.getStateData(), atStateAtHeight.getStateData());
		}
	}

	@Test
	public void testATIncomingCursorRebuildAndStrictOrdering() throws DataException {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");
			PrivateKeyAccount bob = Common.getTestAccount(repository, "bob");
			PrivateKeyAccount chloe = Common.getTestAccount(repository, "chloe");
			PrivateKeyAccount dilbert = Common.getTestAccount(repository, "dilbert");

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			MessageTransactionData normalRecipientMessage = sendMessage(repository, bob, deployer.getAddress());
			MessageTransactionData firstATMessage = sendMessage(repository, chloe, atAddress);
			MessageTransactionData secondATMessage = sendMessage(repository, dilbert, atAddress);
			BlockUtils.mintBlock(repository);

			TransactionData normalRecipientTransaction = repository.getTransactionRepository().fromSignature(normalRecipientMessage.getSignature());
			TransactionData firstATTransaction = repository.getTransactionRepository().fromSignature(firstATMessage.getSignature());
			TransactionData secondATTransaction = repository.getTransactionRepository().fromSignature(secondATMessage.getSignature());

			repository.getATRepository().rebuildATIncomingCaches();

			NextTransactionInfo firstNext = repository.getATRepository().findNextTransaction(atAddress, 0, 0);
			assertNotNull(firstNext);
			assertArrayEquals(firstATTransaction.getSignature(), firstNext.signature);
			assertFalse(Arrays.equals(normalRecipientTransaction.getSignature(), firstNext.signature));

			NextTransactionInfo secondNext = repository.getATRepository().findNextTransaction(atAddress,
					firstATTransaction.getBlockHeight(), firstATTransaction.getBlockSequence());
			assertNotNull(secondNext);
			assertArrayEquals(secondATTransaction.getSignature(), secondNext.signature);

			ATData atData = repository.getATRepository().fromATAddress(atAddress);
			atData.setSleepUntilMessageTimestamp(new Timestamp(firstATTransaction.getBlockHeight(), firstATTransaction.getBlockSequence()).longValue());
			repository.getATRepository().updateRuntimeState(atData);
			repository.getATRepository().recomputeNextIncomingForAT(atAddress);

			Map<String, NextTransactionInfo> cursorByAT = repository.getATRepository().getNextIncomingForATs(Arrays.asList(atData),
					repository.getBlockRepository().getBlockchainHeight() + 1);
			assertTrue(cursorByAT.containsKey(atAddress));
			assertArrayEquals(secondATTransaction.getSignature(), cursorByAT.get(atAddress).signature);

			atData.setSleepUntilMessageTimestamp(new Timestamp(secondATTransaction.getBlockHeight(), secondATTransaction.getBlockSequence()).longValue());
			repository.getATRepository().updateRuntimeState(atData);
			repository.getATRepository().recomputeNextIncomingForAT(atAddress);

			cursorByAT = repository.getATRepository().getNextIncomingForATs(Arrays.asList(atData),
					repository.getBlockRepository().getBlockchainHeight() + 1);
			assertFalse(cursorByAT.containsKey(atAddress));
		}
	}

	@Test
	public void testATExecutionQueueRebuildAndWakeScheduling() throws DataException {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");
			PrivateKeyAccount bob = Common.getTestAccount(repository, "bob");

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			int nextHeight = repository.getBlockRepository().getBlockchainHeight() + 1;
			repository.getATRepository().rebuildATIncomingCaches();
			assertTrue(containsAT(repository.getATRepository().getExecutableATs(nextHeight), atAddress));

			ATData atData = repository.getATRepository().fromATAddress(atAddress);
			atData.setSleepUntilMessageTimestamp(new Timestamp(nextHeight, 0).longValue());
			atData.setSleepUntilHeight(null);
			repository.getATRepository().updateRuntimeState(atData);
			repository.getATRepository().recomputeNextIncomingForAT(atAddress);
			repository.getATRepository().recomputeATExecutionQueueForAT(atAddress);

			assertFalse(containsAT(repository.getATRepository().getExecutableATs(nextHeight + 100), atAddress));

			MessageTransactionData atMessage = sendMessage(repository, bob, atAddress);
			BlockUtils.mintBlock(repository);
			TransactionData atMessageTransaction = repository.getTransactionRepository().fromSignature(atMessage.getSignature());

			assertFalse(containsAT(repository.getATRepository().getExecutableATs(atMessageTransaction.getBlockHeight()), atAddress));
			assertTrue(containsAT(repository.getATRepository().getExecutableATs(atMessageTransaction.getBlockHeight() + 1), atAddress));

			atData = repository.getATRepository().fromATAddress(atAddress);
			int heightWake = atMessageTransaction.getBlockHeight() + 3;
			atData.setSleepUntilMessageTimestamp(new Timestamp(atMessageTransaction.getBlockHeight(),
					atMessageTransaction.getBlockSequence()).longValue());
			atData.setSleepUntilHeight(heightWake);
			repository.getATRepository().updateRuntimeState(atData);
			repository.getATRepository().recomputeNextIncomingForAT(atAddress);
			repository.getATRepository().recomputeATExecutionQueueForAT(atAddress);

			assertFalse(containsAT(repository.getATRepository().getExecutableATs(heightWake - 1), atAddress));
			assertTrue(containsAT(repository.getATRepository().getExecutableATs(heightWake), atAddress));
		}
	}

	@Test
	public void testATExecutionQueueRepairsMissingDueRow() throws Exception {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			int nextHeight = repository.getBlockRepository().getBlockchainHeight() + 1;
			repository.getATRepository().rebuildATIncomingCaches();
			assertTrue(containsAT(repository.getATRepository().getExecutableATs(nextHeight), atAddress));

			((HSQLDBRepository) repository).executeCheckedUpdate("DELETE FROM ATExecutionQueue WHERE AT_address = ?", atAddress);

			assertTrue(containsAT(repository.getATRepository().getExecutableATs(nextHeight), atAddress));
		}
	}

	@Test
	public void testGetLatestATStatePostTrimming() throws DataException {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			// Mint a few blocks
			for (int i = 0; i < 10; ++i)
				BlockUtils.mintBlock(repository);
			int blockchainHeight = repository.getBlockRepository().getBlockchainHeight();

			int maxHeight = blockchainHeight + 100; // more than latest block height
			Integer testHeight = blockchainHeight;

			// Trim AT state data
			repository.getATRepository().rebuildLatestAtStates(maxHeight);
			// COMMIT to check latest AT states persist / TEMPORARY table interaction
			repository.saveChanges();

			repository.getATRepository().trimAtStates(2, maxHeight, 1000);

			ATStateData atStateData = repository.getATRepository().getLatestATState(atAddress);

			assertEquals(testHeight, atStateData.getHeight());
			// We should always have the latest AT state data available
			assertNotNull(atStateData.getStateData());
		}
	}

	private MessageTransactionData sendMessage(Repository repository, PrivateKeyAccount sender, String recipient) throws DataException {
		long txTimestamp = System.currentTimeMillis();
		byte[] lastReference = sender.getLastReference();

		Long fee = null;
		int version = 4;
		int nonce = 0;
		long amount = 0;
		Long assetId = null;
		byte[] data = new byte[] { 0x41 };

		BaseTransactionData baseTransactionData = new BaseTransactionData(txTimestamp, Group.NO_GROUP, lastReference, sender.getPublicKey(), fee, null);
		MessageTransactionData messageTransactionData = new MessageTransactionData(baseTransactionData, version, nonce, recipient, amount, assetId, data, false, false);

		MessageTransaction messageTransaction = new MessageTransaction(repository, messageTransactionData);
		fee = messageTransaction.calcRecommendedFee();
		messageTransactionData.setFee(fee);

		TransactionUtils.signAndImportValid(repository, messageTransactionData, sender);

		return messageTransactionData;
	}

	private Integer getATsCurrentStateHeight(Repository repository, String atAddress) throws Exception {
		try (ResultSet resultSet = ((HSQLDBRepository) repository).checkedExecute(
				"SELECT current_state_height FROM ATs WHERE AT_address = ?", atAddress)) {
			assertNotNull(resultSet);

			Integer height = resultSet.getInt(1);
			return height == 0 && resultSet.wasNull() ? null : height;
		}
	}

	private boolean containsAT(List<ATData> atDataList, String atAddress) {
		return atDataList.stream().anyMatch(atData -> atData.getATAddress().equals(atAddress));
	}

	private Integer getATRuntimeCurrentStateHeight(Repository repository, String atAddress) throws Exception {
		try (ResultSet resultSet = ((HSQLDBRepository) repository).checkedExecute(
				"SELECT current_state_height FROM ATRuntime WHERE AT_address = ?", atAddress)) {
			assertNotNull(resultSet);

			Integer height = resultSet.getInt(1);
			return height == 0 && resultSet.wasNull() ? null : height;
		}
	}

	private Integer getPreviousATStateHeight(Repository repository, String atAddress, int height) throws Exception {
		try (ResultSet resultSet = ((HSQLDBRepository) repository).checkedExecute(
				"SELECT previous_height FROM ATStates WHERE AT_address = ? AND height = ?", atAddress, height)) {
			assertNotNull(resultSet);

			Integer previousHeight = resultSet.getInt(1);
			return previousHeight == 0 && resultSet.wasNull() ? null : previousHeight;
		}
	}

	@Test
	public void testOrphanTrimmedATStates() throws DataException {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			// Mint a few blocks
			for (int i = 0; i < 10; ++i)
				BlockUtils.mintBlock(repository);

			int blockchainHeight = repository.getBlockRepository().getBlockchainHeight();
			int maxTrimHeight = blockchainHeight - 4;
			Integer testHeight = maxTrimHeight + 1;

			// Trim AT state data (using a max height of maxTrimHeight + 1, so it is beyond the trimmed range)
			repository.getATRepository().rebuildLatestAtStates(maxTrimHeight + 1);
			repository.saveChanges();
			repository.getATRepository().trimAtStates(2, maxTrimHeight, 1000);

			// Orphan 3 blocks
			// This leaves one more untrimmed block, so the latest AT state should be available
			BlockUtils.orphanBlocks(repository, 3);

			ATStateData atStateData = repository.getATRepository().getLatestATState(atAddress);
			assertEquals(testHeight, atStateData.getHeight());

			// We should always have the latest AT state data available
			assertNotNull(atStateData.getStateData());

			// Orphan 1 more block
			Exception exception = null;
			try {
				BlockUtils.orphanBlocks(repository, 1);
			} catch (DataException e) {
				exception = e;
			}

			// Ensure that a DataException is thrown because there is no more AT states data available
			assertNotNull(exception);
			assertEquals(DataException.class, exception.getClass());
			assertEquals(String.format("Can't find previous AT state data for %s", atAddress), exception.getMessage());

			// FUTURE: we may be able to retain unique AT states when trimming, to avoid this exception
			// and allow orphaning back through blocks with trimmed AT states.
		}
	}

	@Test
	public void testGetMatchingFinalATStatesWithoutDataValue() throws DataException {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			// Mint a few blocks
			for (int i = 0; i < 10; ++i)
				BlockUtils.mintBlock(repository);
			int blockchainHeight = repository.getBlockRepository().getBlockchainHeight();

			Integer testHeight = blockchainHeight;

			ATData atData = repository.getATRepository().fromATAddress(atAddress);

			byte[] codeHash = atData.getCodeHash();
			Boolean isFinished = Boolean.FALSE;
			Integer dataByteOffset = null;
			Long expectedValue = null;
			Integer minimumFinalHeight = null;
			Integer limit = null;
			Integer offset = null;
			Boolean reverse = null;

			List<ATStateData> atStates = repository.getATRepository().getMatchingFinalATStates(
					codeHash,
					null,
					null,
					isFinished,
					dataByteOffset,
					expectedValue,
					minimumFinalHeight,
					limit, offset, reverse);

			assertEquals(false, atStates.isEmpty());
			assertEquals(1, atStates.size());

			ATStateData atStateData = atStates.get(0);
			assertEquals(testHeight, atStateData.getHeight());
			assertNotNull(atStateData.getStateData());
		}
	}

	@Test
	public void testGetMatchingFinalATStatesWithDataValue() throws DataException {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			// Mint a few blocks
			for (int i = 0; i < 10; ++i)
				BlockUtils.mintBlock(repository);
			int blockchainHeight = repository.getBlockRepository().getBlockchainHeight();

			Integer testHeight = blockchainHeight;

			ATData atData = repository.getATRepository().fromATAddress(atAddress);

			byte[] codeHash = atData.getCodeHash();
			Boolean isFinished = Boolean.FALSE;
			Integer dataByteOffset = MachineState.HEADER_LENGTH + 0;
			Long expectedValue = 0L;
			Integer minimumFinalHeight = null;
			Integer limit = null;
			Integer offset = null;
			Boolean reverse = null;

			List<ATStateData> atStates = repository.getATRepository().getMatchingFinalATStates(
					codeHash,
					null,
					null,
					isFinished,
					dataByteOffset,
					expectedValue,
					minimumFinalHeight,
					limit, offset, reverse);

			assertEquals(false, atStates.isEmpty());
			assertEquals(1, atStates.size());

			ATStateData atStateData = atStates.get(0);
			assertEquals(testHeight, atStateData.getHeight());
			assertNotNull(atStateData.getStateData());
		}
	}

	@Test
	public void testGetBlockATStatesAtHeightWithData() throws DataException {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);

			// Mint a few blocks
			for (int i = 0; i < 10; ++i)
				BlockUtils.mintBlock(repository);

			Integer testHeight = 8;
			List<ATStateData> atStates = repository.getATRepository().getBlockATStatesAtHeight(testHeight);

			assertEquals(false, atStates.isEmpty());
			assertEquals(1, atStates.size());

			ATStateData atStateData = atStates.get(0);
			assertEquals(testHeight, atStateData.getHeight());
			// getBlockATStatesAtHeight never returns actual AT state data anyway
			assertNull(atStateData.getStateData());
		}
	}

	@Test
	public void testGetBlockATStatesAtHeightWithoutData() throws DataException {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);

			// Mint a few blocks
			for (int i = 0; i < 10; ++i)
				BlockUtils.mintBlock(repository);

			int maxHeight = 8;
			Integer testHeight = maxHeight - 2;

			// Trim AT state data
			repository.getATRepository().rebuildLatestAtStates(maxHeight);
			repository.getATRepository().trimAtStates(2, maxHeight, 1000);

			List<ATStateData> atStates = repository.getATRepository().getBlockATStatesAtHeight(testHeight);

			assertEquals(false, atStates.isEmpty());
			assertEquals(1, atStates.size());

			ATStateData atStateData = atStates.get(0);
			assertEquals(testHeight, atStateData.getHeight());
			// getBlockATStatesAtHeight never returns actual AT state data anyway
			assertNull(atStateData.getStateData());
		}
	}

	@Test
	public void testSaveATStateWithData() throws DataException {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			// Mint a few blocks
			for (int i = 0; i < 10; ++i)
				BlockUtils.mintBlock(repository);
			int blockchainHeight = repository.getBlockRepository().getBlockchainHeight();

			Integer testHeight = blockchainHeight - 2;
			ATStateData atStateData = repository.getATRepository().getATStateAtHeight(atAddress, testHeight);

			assertEquals(testHeight, atStateData.getHeight());
			assertNotNull(atStateData.getStateData());

			repository.getATRepository().save(atStateData);

			atStateData = repository.getATRepository().getATStateAtHeight(atAddress, testHeight);

			assertEquals(testHeight, atStateData.getHeight());
			assertNotNull(atStateData.getStateData());
		}
	}

	@Test
	public void testSaveATStateWithoutData() throws DataException {
		byte[] creationBytes = AtUtils.buildSimpleAT();

		try (final Repository repository = RepositoryManager.getRepository()) {
			PrivateKeyAccount deployer = Common.getTestAccount(repository, "alice");

			long fundingAmount = 1_00000000L;
			DeployAtTransaction deployAtTransaction = AtUtils.doDeployAT(repository, deployer, creationBytes, fundingAmount);
			String atAddress = deployAtTransaction.getATAccount().getAddress();

			// Mint a few blocks
			for (int i = 0; i < 10; ++i)
				BlockUtils.mintBlock(repository);
			int blockchainHeight = repository.getBlockRepository().getBlockchainHeight();

			Integer testHeight = blockchainHeight - 2;
			ATStateData atStateData = repository.getATRepository().getATStateAtHeight(atAddress, testHeight);

			assertEquals(testHeight, atStateData.getHeight());
			assertNotNull(atStateData.getStateData());

			// Clear data
			ATStateData newAtStateData = new ATStateData(atStateData.getATAddress(),
					atStateData.getHeight(),
					/*StateData*/ null,
					atStateData.getStateHash(),
					atStateData.getFees(),
					atStateData.isInitial(),
					atStateData.getSleepUntilMessageTimestamp());
			repository.getATRepository().save(newAtStateData);

			atStateData = repository.getATRepository().getATStateAtHeight(atAddress, testHeight);

			assertEquals(testHeight, atStateData.getHeight());
			assertNull(atStateData.getStateData());
		}
	}
}
