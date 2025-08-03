package org.qortal.network.message;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.qortal.api.model.GroupMembers;
import org.qortal.api.model.GroupMembers.MemberInfo;
import org.qortal.transform.Transformer;
import org.qortal.utils.Base58;

public class GroupMembersMessage extends Message {

	private static final int ADDRESS_LENGTH = Transformer.ADDRESS_LENGTH;

	private GroupMembers groupMembers;

	public GroupMembersMessage(GroupMembers groupMembers) {
		super(MessageType.GROUP_MEMBERS);

		this.groupMembers = groupMembers;

		ByteBuffer buffer = serializeGroupMembers(groupMembers);

		this.dataBytes = new byte[buffer.position()];
		buffer.rewind();
		buffer.get(this.dataBytes);

		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	public GroupMembersMessage(int id, GroupMembers groupMembers) {
		this(groupMembers);
		this.setId(id);
	}

	public GroupMembers getGroupMembers() {
		return this.groupMembers;
	}

	private static ByteBuffer serializeGroupMembers(GroupMembers groupMembers) {
		ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024); // 1 MB buffer, adjust if needed

		// memberCount and adminCount (use getters)
		buffer.putInt(groupMembers.getMemberCount() != null ? groupMembers.getMemberCount() : 0);
		buffer.putInt(groupMembers.getAdminCount() != null ? groupMembers.getAdminCount() : 0);

		// members list
		List<MemberInfo> members = groupMembers.getGroupMembers();
		buffer.putInt(members.size());

		for (MemberInfo memberInfo : members) {
			// member (25 bytes base58-decoded)
			buffer.put(Base58.decode(memberInfo.member));

			// joined (nullable long)
			if (memberInfo.joined != null) {
				buffer.putInt(1); // hasJoined = true
				buffer.putLong(memberInfo.joined);
			} else {
				buffer.putInt(0); // hasJoined = false
			}

			// isAdmin flag (nullable boolean: 0=null, 1=false, 2=true)
			if (memberInfo.isAdmin == null) {
				buffer.putInt(0);
			} else {
				buffer.putInt(memberInfo.isAdmin ? 2 : 1);
			}
		}

		return buffer;
	}

	public static Message fromByteBuffer(int id, ByteBuffer buffer) {
		int memberCount = buffer.getInt();
		int adminCount = buffer.getInt();

		int membersSize = buffer.getInt();
		List<MemberInfo> members = new ArrayList<>(membersSize);

		for (int i = 0; i < membersSize; i++) {
			// member address (25 bytes)
			byte[] memberBytes = new byte[ADDRESS_LENGTH];
			buffer.get(memberBytes);
			String member = Base58.encode(memberBytes);

			// joined timestamp (nullable)
			int hasJoined = buffer.getInt();
			Long joined = null;
			if (hasJoined == 1) {
				joined = buffer.getLong();
			}

			// isAdmin flag (nullable boolean)
			int isAdminFlag = buffer.getInt();
		 Boolean isAdmin = null;
			if (isAdminFlag == 1) isAdmin = false;
			if (isAdminFlag == 2) isAdmin = true;

			members.add(new MemberInfo(member, joined, isAdmin != null && isAdmin));
		}

		GroupMembers groupMembers = new GroupMembers(members, memberCount, adminCount);
		return new GroupMembersMessage(id, groupMembers);
	}

	public GroupMembersMessage cloneWithNewId(int newId) {
		GroupMembersMessage clone = new GroupMembersMessage(this.groupMembers);
		clone.setId(newId);
		return clone;
	}
}
