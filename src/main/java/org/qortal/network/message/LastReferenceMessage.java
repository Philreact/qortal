package org.qortal.network.message;

import java.nio.ByteBuffer;

public class LastReferenceMessage extends Message {

	private byte[] lastReference;

	public LastReferenceMessage(byte[] lastReference) {
		super(MessageType.LAST_REFERENCE);

		if (lastReference == null)
			throw new IllegalArgumentException("lastReference cannot be null");

		this.lastReference = lastReference;

		this.dataBytes = lastReference;
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	public LastReferenceMessage(int id, byte[] lastReference) {
		super(id, MessageType.LAST_REFERENCE);

		if (lastReference == null)
			throw new IllegalArgumentException("lastReference cannot be null");

		this.lastReference = lastReference;
		this.dataBytes = lastReference;
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	public byte[] getLastReference() {
		return this.lastReference;
	}

	public static Message fromByteBuffer(int id, ByteBuffer byteBuffer) {
		byte[] lastReference = new byte[64]; // reference is 64 bytes
		byteBuffer.get(lastReference);

		return new LastReferenceMessage(id, lastReference);
	}

	public LastReferenceMessage cloneWithNewId(int newId) {
		LastReferenceMessage clone = new LastReferenceMessage(this.lastReference);
		clone.setId(newId);
		return clone;
	}
}
