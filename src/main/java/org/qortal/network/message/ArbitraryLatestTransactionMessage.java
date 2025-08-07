package org.qortal.network.message;


import java.nio.ByteBuffer;

import org.qortal.transform.transaction.TransactionTransformer;


public class ArbitraryLatestTransactionMessage extends Message {

	private final byte[] signature;

	public ArbitraryLatestTransactionMessage(byte[] signature) {
		super(MessageType.ARBITRARY_LATEST_TRANSACTION);
		this.signature = signature;

		this.dataBytes = signature;
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	public ArbitraryLatestTransactionMessage(int id, byte[] signature) {
		this(signature);
		this.setId(id);
	}

	public byte[] getSignature() {
		return this.signature;
	}

	public static ArbitraryLatestTransactionMessage fromByteBuffer(int id, ByteBuffer buffer) {
		byte[] signature = new byte[TransactionTransformer.SIGNATURE_LENGTH];
		buffer.get(signature);
		return new ArbitraryLatestTransactionMessage(id, signature);
	}
}
