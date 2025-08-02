package org.qortal.network.message;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.qortal.data.group.GroupJoinRequestData;
import org.qortal.transform.Transformer;
import org.qortal.utils.Base58;

public class GroupJoinRequestsMessage extends Message {

	private static final int ADDRESS_LENGTH = Transformer.ADDRESS_LENGTH;
	private static final int SIGNATURE_LENGTH = Transformer.SIGNATURE_LENGTH;

	private List<GroupJoinRequestData> joinRequests;

	public GroupJoinRequestsMessage(List<GroupJoinRequestData> joinRequests) {
		super(MessageType.GROUP_JOIN_REQUESTS);
		this.joinRequests = joinRequests;

		ByteBuffer buffer = serializeJoinRequests(joinRequests);

		this.dataBytes = new byte[buffer.position()];
		buffer.rewind();
		buffer.get(this.dataBytes);

		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	public GroupJoinRequestsMessage(int id, List<GroupJoinRequestData> joinRequests) {
		this(joinRequests);
		this.setId(id);
	}

	public List<GroupJoinRequestData> getJoinRequests() {
		return this.joinRequests;
	}

	private static ByteBuffer serializeJoinRequests(List<GroupJoinRequestData> joinRequests) {
		ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024); // 1 MB buffer (adjust if needed)

		buffer.putInt(joinRequests.size());

		for (GroupJoinRequestData request : joinRequests) {
			buffer.putInt(request.getGroupId());
			buffer.put(Base58.decode(request.getJoiner()));
			buffer.put(request.getReference());
		}

		return buffer;
	}

	public static Message fromByteBuffer(int id, ByteBuffer buffer) {
		int count = buffer.getInt();
		List<GroupJoinRequestData> joinRequests = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			int groupId = buffer.getInt();

			byte[] joinerBytes = new byte[ADDRESS_LENGTH];
			buffer.get(joinerBytes);
			String joiner = Base58.encode(joinerBytes);

			byte[] reference = new byte[SIGNATURE_LENGTH];
			buffer.get(reference);

			GroupJoinRequestData request = new GroupJoinRequestData(groupId, joiner, reference);
			joinRequests.add(request);
		}

		return new GroupJoinRequestsMessage(id, joinRequests);
	}

	public GroupJoinRequestsMessage cloneWithNewId(int newId) {
		GroupJoinRequestsMessage clone = new GroupJoinRequestsMessage(this.joinRequests);
		clone.setId(newId);
		return clone;
	}
}
