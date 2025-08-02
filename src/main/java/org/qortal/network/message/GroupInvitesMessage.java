package org.qortal.network.message;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.qortal.data.group.GroupInviteData;
import org.qortal.transform.Transformer;
import org.qortal.utils.Base58;

public class GroupInvitesMessage extends Message {

	private static final int ADDRESS_LENGTH = Transformer.ADDRESS_LENGTH;
	private static final int SIGNATURE_LENGTH = Transformer.SIGNATURE_LENGTH;

	private List<GroupInviteData> invites;

	public GroupInvitesMessage(List<GroupInviteData> invites) {
		super(MessageType.GROUP_INVITES);
		this.invites = invites;

		ByteBuffer buffer = serializeInvites(invites);

		this.dataBytes = new byte[buffer.position()];
		buffer.rewind();
		buffer.get(this.dataBytes);

		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	public GroupInvitesMessage(int id, List<GroupInviteData> invites) {
		this(invites);
		this.setId(id);
	}

	public List<GroupInviteData> getInvites() {
		return this.invites;
	}

	private static ByteBuffer serializeInvites(List<GroupInviteData> invites) {
		ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024); // 1 MB buffer (adjust if needed)

		buffer.putInt(invites.size());

		for (GroupInviteData invite : invites) {
			buffer.putInt(invite.getGroupId());

			buffer.put(Base58.decode(invite.getInviter()));
			buffer.put(Base58.decode(invite.getInvitee()));

			// expiry (nullable long)
			if (invite.getExpiry() != null) {
				buffer.putInt(1);
				buffer.putLong(invite.getExpiry());
			} else {
				buffer.putInt(0);
			}

			// reference (64 bytes)
			buffer.put(invite.getReference());
		}

		return buffer;
	}

	public static Message fromByteBuffer(int id, ByteBuffer buffer) {
		int count = buffer.getInt();
		List<GroupInviteData> invites = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			int groupId = buffer.getInt();

			byte[] inviterBytes = new byte[ADDRESS_LENGTH];
			buffer.get(inviterBytes);
			String inviter = Base58.encode(inviterBytes);

			byte[] inviteeBytes = new byte[ADDRESS_LENGTH];
			buffer.get(inviteeBytes);
			String invitee = Base58.encode(inviteeBytes);

			int hasExpiry = buffer.getInt();
			Long expiry = null;
			if (hasExpiry == 1) {
				expiry = buffer.getLong();
			}

			byte[] reference = new byte[SIGNATURE_LENGTH];
			buffer.get(reference);

			GroupInviteData invite = new GroupInviteData(groupId, inviter, invitee, expiry, reference);
			invites.add(invite);
		}

		return new GroupInvitesMessage(id, invites);
	}

	public GroupInvitesMessage cloneWithNewId(int newId) {
		GroupInvitesMessage clone = new GroupInvitesMessage(this.invites);
		clone.setId(newId);
		return clone;
	}
}
