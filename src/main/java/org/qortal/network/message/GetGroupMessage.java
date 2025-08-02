package org.qortal.network.message;

import java.nio.ByteBuffer;

public class GetGroupMessage extends Message {

	private final int groupId;


	public GetGroupMessage(int groupId) {
		super(MessageType.GET_GROUP);

		this.groupId = groupId;

		ByteBuffer buffer = ByteBuffer.allocate(4);
		buffer.putInt(groupId);

		this.dataBytes = buffer.array();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private GetGroupMessage(int id, int groupId) {
		super(id, MessageType.GET_GROUP);
		this.groupId = groupId;
	}

	public int getGroupId() {
		return this.groupId;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) {
		int groupId = bytes.getInt();
		return new GetGroupMessage(id, groupId);
	}
}
