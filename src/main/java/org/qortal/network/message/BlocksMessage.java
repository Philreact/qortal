package org.qortal.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.block.Block;
import org.qortal.data.block.BlockData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.transform.TransformationException;
import org.qortal.transform.block.BlockTransformation;
import org.qortal.transform.block.BlockTransformer;

import com.google.common.primitives.Ints;

public class BlocksMessage extends Message {

	private static final Logger LOGGER = LogManager.getLogger(BlocksMessage.class);

		public static final long MINIMUM_PEER_VERSION = 0x500070000L; // 5.0.7

	private final List<BlockData> blocks;
	private final List<List<TransactionData>> transactions;
	private final List<byte[]> atStatesHashes;

	public BlocksMessage(List<Block> blocks) throws MessageException {
		super(MessageType.BLOCKS);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			bytes.write(Ints.toByteArray(blocks.size()));

			for (Block block : blocks) {
				bytes.write(Ints.toByteArray(block.getBlockData().getHeight()));
				bytes.write(BlockTransformer.toBytesV2(block));
			}
		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
		} catch (TransformationException e) {
			throw new MessageException(e.getMessage(), e);
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);

		this.blocks = null;
		this.transactions = null;
		this.atStatesHashes = null;
	}

	private BlocksMessage(int id, List<BlockData> blocks, List<List<TransactionData>> transactions, List<byte[]> atStatesHashes) {
		super(id, MessageType.BLOCKS);

		this.blocks = blocks;
		this.transactions = transactions;
		this.atStatesHashes = atStatesHashes;
	}

	public List<BlockData> getBlockData() {
		return this.blocks;
	}

	public List<List<TransactionData>> getTransactions() {
		return this.transactions;
	}

	public List<byte[]> getAtStatesHashes() {
		return this.atStatesHashes;
	}

	public static Message fromByteBuffer(int id, ByteBuffer byteBuffer) throws MessageException {
		try {
			int blockCount = byteBuffer.getInt();

			List<BlockData> blocks = new ArrayList<>(blockCount);
			List<List<TransactionData>> transactions = new ArrayList<>(blockCount);
			List<byte[]> atStatesHashes = new ArrayList<>(blockCount);

			for (int i = 0; i < blockCount; ++i) {
				int height = byteBuffer.getInt();

				BlockTransformation blockTransformation = BlockTransformer.fromByteBufferV2(byteBuffer);

				BlockData blockData = blockTransformation.getBlockData();
				blockData.setHeight(height);

				blocks.add(blockData);
				transactions.add(blockTransformation.getTransactions());
				atStatesHashes.add(blockTransformation.getAtStatesHash());
			}

			return new BlocksMessage(id, blocks, transactions, atStatesHashes);
		} catch (TransformationException e) {
			LOGGER.info(String.format("Received garbled BLOCKS message: %s", e.getMessage()));
			throw new MessageException(e.getMessage(), e);
		}
	}

}
