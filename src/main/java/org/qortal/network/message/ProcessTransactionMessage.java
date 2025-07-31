package org.qortal.network.message;

import java.nio.ByteBuffer;

public class ProcessTransactionMessage extends Message {

	private final byte[] rawBytes;

	public ProcessTransactionMessage(byte[] rawBytes) {
		super(MessageType.PROCESS_TRANSACTION);

		this.rawBytes = rawBytes;
		this.dataBytes = rawBytes;
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private ProcessTransactionMessage(int id, byte[] rawBytes) {
		super(id, MessageType.PROCESS_TRANSACTION);
		this.rawBytes = rawBytes;
	}

	public byte[] getRawBytes() {
		return this.rawBytes;
	}

	public static Message fromByteBuffer(int id, ByteBuffer byteBuffer) {
		byte[] bytes = new byte[byteBuffer.remaining()];
		byteBuffer.get(bytes);

		return new ProcessTransactionMessage(id, bytes);
	}
}
