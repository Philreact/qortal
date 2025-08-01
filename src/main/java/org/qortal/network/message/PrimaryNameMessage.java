package org.qortal.network.message;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.qortal.utils.Base58;

public class PrimaryNameMessage extends Message {

	private String name;
	private String owner;

	public PrimaryNameMessage(String name, String owner) {
		super(MessageType.PRIMARY_NAME);

		if (owner == null)
			throw new IllegalArgumentException("Owner cannot be null");

		this.name = name;
		this.owner = owner;

		byte[] nameBytes = name == null ? new byte[0] : name.getBytes(StandardCharsets.UTF_8);
		byte[] ownerBytes = Base58.decode(owner);

		ByteBuffer buffer = ByteBuffer.allocate(4 + nameBytes.length + ownerBytes.length);
		buffer.putInt(nameBytes.length);
		if (name != null)
			buffer.put(nameBytes);
		buffer.put(ownerBytes);

		this.dataBytes = buffer.array();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	public PrimaryNameMessage(int id, String name, String owner) {
		this(name, owner);
		this.setId(id);
	}

	public String getName() {
		return this.name;
	}

	public String getOwner() {
		return this.owner;
	}

	public static Message fromByteBuffer(int id, ByteBuffer byteBuffer) {
		int nameLength = byteBuffer.getInt();
		String name = null;
		if (nameLength > 0) {
			byte[] nameBytes = new byte[nameLength];
			byteBuffer.get(nameBytes);
			name = new String(nameBytes, StandardCharsets.UTF_8);
		}

		byte[] ownerBytes = new byte[25];
		byteBuffer.get(ownerBytes);
		String owner = Base58.encode(ownerBytes);

		return new PrimaryNameMessage(id, name, owner);
	}

	public PrimaryNameMessage cloneWithNewId(int newId) {
		PrimaryNameMessage clone = new PrimaryNameMessage(this.name, this.owner);
		clone.setId(newId);
		return clone;
	}
}
