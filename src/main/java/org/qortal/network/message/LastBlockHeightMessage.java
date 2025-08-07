package org.qortal.network.message;

import org.qortal.data.block.BlockData;
import org.qortal.utils.Serialization;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import com.google.common.primitives.Longs;

public class LastBlockHeightMessage extends Message {

	private final BlockData blockData;
	private final String minterAddress;
	private final int minterLevel;

	public LastBlockHeightMessage(BlockData blockData, String minterAddress, int minterLevel) {
		super(MessageType.LAST_BLOCK_HEIGHT);
		this.blockData = blockData;
		this.minterAddress = minterAddress;
		this.minterLevel = minterLevel;

		try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
			Serialization.serializeInt(bytes, blockData.getVersion());
			Serialization.serializeNullableData(bytes, blockData.getReference());
			Serialization.serializeInt(bytes, blockData.getTransactionCount());

			// Serialize totalFees as raw long
			bytes.write(Longs.toByteArray(blockData.getTotalFees()));

			Serialization.serializeNullableData(bytes, blockData.getTransactionsSignature());
			Serialization.serializeInt(bytes, blockData.getHeight());
			Serialization.serializeTimestamp(bytes, blockData.getTimestamp());
			Serialization.serializeNullableData(bytes, blockData.getMinterPublicKey());
			Serialization.serializeNullableData(bytes, blockData.getMinterSignature());
			Serialization.serializeInt(bytes, blockData.getATCount());

			// Serialize atFees as raw long
			bytes.write(Longs.toByteArray(blockData.getATFees()));

			Serialization.serializeNullableData(bytes, blockData.getEncodedOnlineAccounts());
			Serialization.serializeInt(bytes, blockData.getOnlineAccountsCount());
			Serialization.serializeNullableTimestamp(bytes, blockData.getOnlineAccountsTimestamp());
			Serialization.serializeNullableData(bytes, blockData.getOnlineAccountsSignatures());

			// Serialize block signature
			Serialization.serializeNullableData(bytes, blockData.getSignature());

			// ✅ Serialize new fields
			Serialization.serializeNullableString(bytes, minterAddress);
			Serialization.serializeInt(bytes, minterLevel);

			this.dataBytes = bytes.toByteArray();
			this.checksumBytes = Message.generateChecksum(this.dataBytes);

		} catch (IOException e) {
			throw new RuntimeException("Failed to serialize BlockData for LastBlockHeightMessage", e);
		}
	}

	public LastBlockHeightMessage(int id, BlockData blockData, String minterAddress, int minterLevel) {
		this(blockData, minterAddress, minterLevel);
		this.setId(id);
	}

	public BlockData getBlockData() {
		return this.blockData;
	}

	public String getMinterAddress() {
		return this.minterAddress;
	}

	public int getMinterLevel() {
		return this.minterLevel;
	}

	public static LastBlockHeightMessage fromByteBuffer(int id, ByteBuffer buffer) {
		int version = Serialization.deserializeInt(buffer);
		byte[] reference = Serialization.deserializeNullableData(buffer);
		int transactionCount = Serialization.deserializeInt(buffer);

		long totalFees = buffer.getLong();

		byte[] transactionsSignature = Serialization.deserializeNullableData(buffer);
		int height = Serialization.deserializeInt(buffer);
		long timestamp = Serialization.deserializeTimestamp(buffer);
		byte[] minterPublicKey = Serialization.deserializeNullableData(buffer);
		byte[] minterSignature = Serialization.deserializeNullableData(buffer);
		int atCount = Serialization.deserializeInt(buffer);

		long atFees = buffer.getLong();

		byte[] encodedOnlineAccounts = Serialization.deserializeNullableData(buffer);
		int onlineAccountsCount = Serialization.deserializeInt(buffer);
		Long onlineAccountsTimestamp = Serialization.deserializeNullableTimestamp(buffer);
		byte[] onlineAccountsSignatures = Serialization.deserializeNullableData(buffer);

		byte[] signature = Serialization.deserializeNullableData(buffer);

		String minterAddress = Serialization.deserializeNullableString(buffer);
		int minterLevel = Serialization.deserializeInt(buffer);

		BlockData blockData = new BlockData(
			version,
			reference,
			transactionCount,
			totalFees,
			transactionsSignature,
			height,
			timestamp,
			minterPublicKey,
			minterSignature,
			atCount,
			atFees,
			encodedOnlineAccounts,
			onlineAccountsCount,
			onlineAccountsTimestamp,
			onlineAccountsSignatures
		);

		blockData.setSignature(signature);

		return new LastBlockHeightMessage(id, blockData, minterAddress, minterLevel);
	}

	public LastBlockHeightMessage cloneWithNewId(int newId) {
		return new LastBlockHeightMessage(newId, this.blockData, this.minterAddress, this.minterLevel);
	}
}
