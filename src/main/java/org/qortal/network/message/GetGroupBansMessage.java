package org.qortal.network.message;

import java.nio.ByteBuffer;

public class GetGroupBansMessage extends Message {

	private final int groupId;


	public GetGroupBansMessage(int groupId) {
		super(MessageType.GET_GROUP_BANS);

		this.groupId = groupId;

		ByteBuffer buffer = ByteBuffer.allocate(4);
		buffer.putInt(groupId);

		this.dataBytes = buffer.array();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private GetGroupBansMessage(int id, int groupId) {
		super(id, MessageType.GET_GROUP_BANS);
		this.groupId = groupId;
	}

	public int getGroupId() {
		return this.groupId;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) {
		int groupId = bytes.getInt();
		return new GetGroupBansMessage(id, groupId);
	}
}
