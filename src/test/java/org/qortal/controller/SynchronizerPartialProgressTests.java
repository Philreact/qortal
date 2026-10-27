package org.qortal.controller;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.qortal.block.Block;
import org.qortal.block.BlockChain;
import org.qortal.data.block.BlockData;
import org.qortal.data.block.BlockSummaryData;
import org.qortal.data.network.PeerData;
import org.qortal.network.Peer;
import org.qortal.network.PeerAddress;
import org.qortal.network.message.BlockV2Message;
import org.qortal.network.message.BlocksMessage;
import org.qortal.network.message.Message;
import org.qortal.network.message.MessageType;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.settings.Settings;
import org.qortal.test.common.AtUtils;
import org.qortal.test.common.BlockUtils;
import org.qortal.test.common.Common;
import org.qortal.utils.NTP;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.qortal.controller.Synchronizer.SynchronizationResult.*;

public class SynchronizerPartialProgressTests extends Common {
    @Before
    public void beforeTest() throws DataException {
        Common.useDefaultSettings();
    }

    @After
    public void restoreClock() {
        NTP.setFixedOffset(Settings.getInstance().getTestNtpOffset());
    }

    @Test
    public void blockTimeoutCommitsPrefixAndNextPeerContinuesFromIt() throws Exception {
        try (Repository repository = RepositoryManager.getRepository()) {
            Fixture fixture = new Fixture(repository);
            ScriptedPeer peer = new ScriptedPeer(fixture, 2);

            assertEquals(NO_REPLY, append(repository, peer, fixture.base, fixture.summaries));
            assertEquals(1, peer.batchRequests);
            assertEquals(3, peer.blockRequests);
            assertCommittedTip(repository, fixture.blocks.get(1));
            assertArrayEquals(fixture.stateHashes.get(1),
                    repository.getATRepository().getLatestATState(fixture.atAddress).getStateHash());

            // A fresh session starts after the committed prefix, not after the original tip.
            ScriptedPeer nextPeer = new ScriptedPeer(fixture, 3);
            assertEquals(OK, append(repository, nextPeer, fixture.blocks.get(1), fixture.summaries.subList(2, 3)));
            assertEquals(1, nextPeer.blockRequests);
            assertCommittedTip(repository, fixture.blocks.get(2));
            assertArrayEquals(fixture.stateHashes.get(2),
                    repository.getATRepository().getLatestATState(fixture.atAddress).getStateHash());
            repository.discardChanges();
            Common.orphanCheck();
        }
    }

    @Test
    public void signatureTimeoutAlsoCommitsPrefix() throws Exception {
        try (Repository repository = RepositoryManager.getRepository()) {
            Fixture fixture = new Fixture(repository);
            ScriptedPeer peer = new ScriptedPeer(fixture, 3);
            assertEquals(NO_REPLY, append(repository, peer, fixture.base, fixture.summaries.subList(0, 1)));
            assertEquals(1, peer.blockRequests);
            assertEquals(1, peer.signatureRequests);
            assertCommittedTip(repository, fixture.blocks.get(0));
        }
    }

    @Test
    public void individualOnlyModeAlsoCommitsPrefix() throws Exception {
        FieldUtils.writeField(Settings.getInstance(), "fastSyncEnabled", false, true);
        try (Repository repository = RepositoryManager.getRepository()) {
            Fixture fixture = new Fixture(repository);
            ScriptedPeer peer = new ScriptedPeer(fixture, 2);
            assertEquals(NO_REPLY, append(repository, peer, fixture.base, fixture.summaries));
            assertEquals(0, peer.batchRequests);
            assertCommittedTip(repository, fixture.blocks.get(1));
        }
    }

    @Test
    public void timeoutBeforeAnyBlockLeavesTipUnchanged() throws Exception {
        try (Repository repository = RepositoryManager.getRepository()) {
            Fixture fixture = new Fixture(repository);
            ScriptedPeer peer = new ScriptedPeer(fixture, 0);
            assertEquals(NO_REPLY, append(repository, peer, fixture.base, fixture.summaries));
            assertCommittedTip(repository, fixture.base);
        }
    }

    @Test
    public void wrongBlockResponseDoesNotApplyPrefix() throws Exception {
        try (Repository repository = RepositoryManager.getRepository()) {
            Fixture fixture = new Fixture(repository);
            ScriptedPeer peer = new ScriptedPeer(fixture, 3);
            peer.wrongSecondBlock = true;
            assertEquals(INVALID_DATA, append(repository, peer, fixture.base, fixture.summaries));
            assertCommittedTip(repository, fixture.base);
        }
    }

    @Test
    public void noncontiguousSignedBlocksDoNotApplyPrefix() throws Exception {
        try (Repository repository = RepositoryManager.getRepository()) {
            Fixture fixture = new Fixture(repository);
            ScriptedPeer peer = new ScriptedPeer(fixture, 3);
            assertEquals(INVALID_DATA, append(repository, peer, fixture.base,
                    Arrays.asList(fixture.summaries.get(0), fixture.summaries.get(2))));
            assertCommittedTip(repository, fixture.base);
        }
    }

    @Test
    public void stalePeerTipDoesNotApplyPartialPrefix() throws Exception {
        try (Repository repository = RepositoryManager.getRepository()) {
            Fixture fixture = new Fixture(repository);
            ScriptedPeer peer = new ScriptedPeer(fixture, 1);
            BlockData lastBlock = fixture.blocks.get(2);
            assertNotNull(Controller.getMinimumLatestBlockTimestamp());
            peer.setChainTipData(new BlockSummaryData(lastBlock.getHeight(), lastBlock.getSignature(),
                    lastBlock.getMinterPublicKey(), 1L));
            assertEquals(CHAIN_TIP_TOO_OLD, append(repository, peer, fixture.base, fixture.summaries));
            assertCommittedTip(repository, fixture.base);
        }
    }

    @Test
    public void tipChangeWhileFetchingPreventsApplyingPartialPrefix() throws Exception {
        try (Repository repository = RepositoryManager.getRepository()) {
            Fixture fixture = new Fixture(repository);
            ScriptedPeer peer = new ScriptedPeer(fixture, 1);
            peer.onMissingBlock = () -> {
                try {
                    BlockUtils.mintBlock(repository);
                } catch (DataException e) {
                    throw new AssertionError(e);
                }
            };
            assertEquals(NOTHING_TO_DO, append(repository, peer, fixture.base, fixture.summaries));
            assertEquals(fixture.base.getHeight() + 1, repository.getBlockRepository().getBlockchainHeight());
            repository.discardChanges();
        }
    }

    @Test
    public void reorgFetchStillDiscardsIncompleteReplacement() throws Exception {
        try (Repository repository = RepositoryManager.getRepository()) {
            Fixture fixture = new Fixture(repository);
            ScriptedPeer peer = new ScriptedPeer(fixture, 1);
            Method method = Synchronizer.class.getDeclaredMethod("fetchReorgPlan", Repository.class,
                    Peer.class, BlockData.class, int.class, List.class);
            Object plan = invoke(method, repository, peer, fixture.base,
                    fixture.blocks.get(2).getHeight(), fixture.summaries);
            assertEquals(NO_REPLY, FieldUtils.readField(plan, "result", true));
            assertTrue(((List<?>) FieldUtils.readField(plan, "blocks", true)).isEmpty());
            assertCommittedTip(repository, fixture.base);
        }
    }

    private static Synchronizer.SynchronizationResult append(Repository repository, ScriptedPeer peer,
            BlockData tip, List<BlockSummaryData> summaries) throws Exception {
        Method method = Synchronizer.class.getDeclaredMethod("synchronizeAppendSession", Repository.class,
                java.util.concurrent.locks.ReentrantLock.class, Peer.class, BlockData.class,
                int.class, BlockData.class, List.class);
        return (Synchronizer.SynchronizationResult) invoke(method, repository,
                Controller.getInstance().getBlockchainLock(), peer, tip,
                peer.fixture.blocks.get(2).getHeight(), tip, summaries);
    }

    private static Object invoke(Method method, Object... arguments) throws Exception {
        method.setAccessible(true);
        try {
            return method.invoke(Synchronizer.getInstance(), arguments);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception) throw (Exception) e.getCause();
            throw e;
        }
    }

    private static void assertCommittedTip(Repository repository, BlockData expected) throws DataException {
        repository.discardChanges();
        try (Repository reader = RepositoryManager.getRepository()) {
            BlockData actual = reader.getBlockRepository().getLastBlock();
            assertEquals(expected.getHeight(), actual.getHeight());
            assertArrayEquals(expected.getSignature(), actual.getSignature());
        }
    }

    private static final class Fixture {
        private final BlockData base;
        private final String atAddress;
        private final List<BlockData> blocks = new ArrayList<>();
        private final List<BlockSummaryData> summaries = new ArrayList<>();
        private final List<byte[]> messages = new ArrayList<>();
        private final List<byte[]> stateHashes = new ArrayList<>();

        private Fixture(Repository repository) throws Exception {
            atAddress = AtUtils.doDeployAT(repository, Common.getTestAccount(repository, "alice"),
                    AtUtils.buildSimpleAT(), 1_00000000L).getATAccount().getAddress();
            base = repository.getBlockRepository().getLastBlock();
            for (int i = 0; i < 3; ++i) {
                Block block = BlockUtils.mintBlock(repository);
                // Read a separate BlockData so orphaning the fixture cannot clear its height.
                BlockData data = repository.getBlockRepository().getLastBlock();
                blocks.add(data);
                summaries.add(new BlockSummaryData(data));
                messages.add((byte[]) FieldUtils.readField(new BlockV2Message(block), "dataBytes", true));
                stateHashes.add(repository.getATRepository().getLatestATState(atAddress).getStateHash());
            }
            BlockUtils.orphanBlocks(repository, 3);
            Controller.getInstance().refillLatestBlocksCache();
            // Exercise historical sync. Test minting uses cached synthetic online-account
            // nonces, which must be outside the signature-retention window when replayed.
            NTP.setFixedOffset(blocks.get(2).getTimestamp()
                    + BlockChain.getInstance().getOnlineAccountSignaturesMinLifetime() + 1_000L
                    - System.currentTimeMillis());
        }
    }

    private static final class ScriptedPeer extends Peer {
        private final Fixture fixture;
        private final int availableBlocks;
        private int batchRequests;
        private int blockRequests;
        private int signatureRequests;
        private boolean wrongSecondBlock;
        private Runnable onMissingBlock;

        private ScriptedPeer(Fixture fixture, int availableBlocks) {
            super(new PeerData(new PeerAddress("127.0.0.1:12392")), Peer.NETWORK);
            this.fixture = fixture;
            this.availableBlocks = availableBlocks;
            setPeersVersion("qortal-6.1.9", 0x0600010009L);
        }

        @Override
        public Message getResponseWithTimeout(Message message, int timeout) {
            assertFalse("Network requests must not hold the blockchain lock",
                    Controller.getInstance().getBlockchainLock().isHeldByCurrentThread());
            if (message.getType() == MessageType.GET_BLOCKS) {
                ++batchRequests;
                return new BlocksMessage(Collections.emptyList());
            }
            if (message.getType() == MessageType.GET_SIGNATURES_V2) {
                ++signatureRequests;
                return null;
            }
            assertEquals(MessageType.GET_BLOCK, message.getType());
            ++blockRequests;
            byte[] signature;
            try {
                signature = (byte[]) FieldUtils.readField(message, "dataBytes", true);
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
            for (int i = 0; i < fixture.blocks.size(); ++i) {
                if (!Arrays.equals(signature, fixture.blocks.get(i).getSignature())) continue;
                if (i >= availableBlocks) {
                    if (onMissingBlock != null) onMissingBlock.run();
                    return null;
                }
                try {
                    int responseIndex = wrongSecondBlock && blockRequests == 2 ? 0 : i;
                    return BlockV2Message.fromByteBuffer(1, ByteBuffer.wrap(fixture.messages.get(responseIndex)));
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
            }
            fail("Unexpected block signature");
            return null;
        }
    }
}
