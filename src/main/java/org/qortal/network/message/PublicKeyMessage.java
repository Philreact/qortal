package org.qortal.network.message;

import java.nio.ByteBuffer;

public class PublicKeyMessage extends Message {

	private byte[] publicKey;

	public PublicKeyMessage(byte[] publicKey) {
		super(MessageType.PUBLIC_KEY);

		if (publicKey == null || publicKey.length != 32)
			throw new IllegalArgumentException("publicKey must be 32 bytes");

		this.publicKey = publicKey;

		this.dataBytes = publicKey;
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	public PublicKeyMessage(int id, byte[] publicKey) {
		this(publicKey);
		this.setId(id);
	}

	public byte[] getPublicKey() {
		return this.publicKey;
	}

	public static Message fromByteBuffer(int id, ByteBuffer byteBuffer) {
		byte[] publicKey = new byte[32]; // public key is 32 bytes
		byteBuffer.get(publicKey);

		return new PublicKeyMessage(id, publicKey);
	}

	public PublicKeyMessage cloneWithNewId(int newId) {
		PublicKeyMessage clone = new PublicKeyMessage(this.publicKey);
		clone.setId(newId);
		return clone;
	}
}
