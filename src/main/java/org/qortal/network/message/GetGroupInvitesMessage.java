package org.qortal.network.message;

import java.nio.ByteBuffer;

public class GetGroupInvitesMessage extends Message {

	private final int groupId;


	public GetGroupInvitesMessage(int groupId) {
		super(MessageType.GET_GROUP_INVITES);

		this.groupId = groupId;

		ByteBuffer buffer = ByteBuffer.allocate(4);
		buffer.putInt(groupId);

		this.dataBytes = buffer.array();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private GetGroupInvitesMessage(int id, int groupId) {
		super(id, MessageType.GET_GROUP_INVITES);
		this.groupId = groupId;
	}

	public int getGroupId() {
		return this.groupId;
	}

	public static Message fromByteBuffer(int id, ByteBuffer bytes) {
		int groupId = bytes.getInt();
		return new GetGroupInvitesMessage(id, groupId);
	}
}
