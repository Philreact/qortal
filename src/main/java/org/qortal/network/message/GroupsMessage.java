package org.qortal.network.message;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.qortal.data.group.GroupData;
import org.qortal.group.Group.ApprovalThreshold;
import org.qortal.transform.Transformer;
import org.qortal.utils.Base58;

public class GroupsMessage extends Message {

	private static final int ADDRESS_LENGTH = Transformer.ADDRESS_LENGTH;
	private static final int SIGNATURE_LENGTH = Transformer.SIGNATURE_LENGTH;

	private List<GroupData> groups;

	public GroupsMessage(List<GroupData> groups) {
		super(MessageType.GROUPS);

		this.groups = groups;

		ByteBuffer buffer = serializeGroups(groups);

		this.dataBytes = new byte[buffer.position()];
		buffer.rewind();
		buffer.get(this.dataBytes);

		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	public GroupsMessage(int id, List<GroupData> groups) {
		this(groups);
		this.setId(id);
	}

	public List<GroupData> getGroups() {
		return this.groups;
	}

	private static ByteBuffer serializeGroups(List<GroupData> groups) {
		ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024); // 1 MB buffer, adjust if needed

		buffer.putInt(groups.size());

		for (GroupData group : groups) {
			Integer groupId = group.getGroupId();
			buffer.putInt(groupId != null ? groupId : 0);

			buffer.put(Base58.decode(group.getOwner()));

			putSizedString(buffer, group.getGroupName());
			putSizedString(buffer, group.getDescription());

			buffer.putLong(group.getCreated());

			if (group.getUpdated() != null) {
				buffer.putInt(1);
				buffer.putLong(group.getUpdated());
			} else {
				buffer.putInt(0);
			}

			buffer.putInt(group.isOpen() ? 1 : 0);

			// approvalThreshold (value)
			buffer.putInt(group.getApprovalThreshold().value);

			buffer.putInt(group.getMinimumBlockDelay());
			buffer.putInt(group.getMaximumBlockDelay());

			buffer.put(group.getReference());

			buffer.putInt(group.getCreationGroupId());

			putSizedString(buffer, group.getReducedGroupName());

			if (group.isAdmin() == null) {
				buffer.putInt(0);
			} else {
				buffer.putInt(group.isAdmin() ? 2 : 1);
			}

			// memberCount
			buffer.putInt(group.memberCount);
		}

		return buffer;
	}

	private static void putSizedString(ByteBuffer buffer, String value) {
		if (value == null) {
			buffer.putInt(0);
		} else {
			byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
			buffer.putInt(bytes.length);
			buffer.put(bytes);
		}
	}

	public static Message fromByteBuffer(int id, ByteBuffer buffer) {
		int groupCount = buffer.getInt();
		List<GroupData> groups = new ArrayList<>(groupCount);

		for (int i = 0; i < groupCount; i++) {
			Integer groupId = buffer.getInt();
			if (groupId == 0) groupId = null;

			byte[] ownerBytes = new byte[ADDRESS_LENGTH];
			buffer.get(ownerBytes);
			String owner = Base58.encode(ownerBytes);

			String groupName = readSizedString(buffer);
			String description = readSizedString(buffer);

			long created = buffer.getLong();

			int wasUpdated = buffer.getInt();
			Long updated = null;
			if (wasUpdated == 1) {
				updated = buffer.getLong();
			}

			boolean isOpen = buffer.getInt() == 1;

			// Read ApprovalThreshold by value (not ordinal)
			int approvalThresholdValue = buffer.getInt();
			ApprovalThreshold approvalThreshold = ApprovalThreshold.NONE;
			for (ApprovalThreshold threshold : ApprovalThreshold.values()) {
				if (threshold.value == approvalThresholdValue) {
					approvalThreshold = threshold;
					break;
				}
			}

			int minimumBlockDelay = buffer.getInt();
			int maximumBlockDelay = buffer.getInt();

			byte[] reference = new byte[SIGNATURE_LENGTH];
			buffer.get(reference);

			int creationGroupId = buffer.getInt();

			String reducedGroupName = readSizedString(buffer);

			int isAdminFlag = buffer.getInt();
			Boolean isAdmin = null;
			if (isAdminFlag == 1) isAdmin = false;
			if (isAdminFlag == 2) isAdmin = true;

			int memberCount = buffer.getInt();

			GroupData group = new GroupData(groupId, owner, groupName, description, created, updated, isOpen,
					approvalThreshold, minimumBlockDelay, maximumBlockDelay, reference, creationGroupId, reducedGroupName);
			group.setIsAdmin(isAdmin);
			group.memberCount = memberCount;

			groups.add(group);
		}

		return new GroupsMessage(id, groups);
	}

	private static String readSizedString(ByteBuffer buffer) {
		int length = buffer.getInt();
		if (length == 0) {
			return null;
		}
		byte[] strBytes = new byte[length];
		buffer.get(strBytes);
		return new String(strBytes, StandardCharsets.UTF_8);
	}

	public GroupsMessage cloneWithNewId(int newId) {
		GroupsMessage clone = new GroupsMessage(this.groups);
		clone.setId(newId);
		return clone;
	}
}
