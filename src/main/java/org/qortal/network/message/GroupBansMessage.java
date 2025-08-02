package org.qortal.network.message;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.qortal.data.group.GroupBanData;
import org.qortal.transform.Transformer;
import org.qortal.utils.Base58;

public class GroupBansMessage extends Message {

	private static final int ADDRESS_LENGTH = Transformer.ADDRESS_LENGTH;
	private static final int SIGNATURE_LENGTH = Transformer.SIGNATURE_LENGTH;

	private List<GroupBanData> bans;

	public GroupBansMessage(List<GroupBanData> bans) {
		super(MessageType.GROUP_BANS);
		this.bans = bans;

		ByteBuffer buffer = serializeBans(bans);

		this.dataBytes = new byte[buffer.position()];
		buffer.rewind();
		buffer.get(this.dataBytes);

		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	public GroupBansMessage(int id, List<GroupBanData> bans) {
		this(bans);
		this.setId(id);
	}

	public List<GroupBanData> getBans() {
		return this.bans;
	}

	private static ByteBuffer serializeBans(List<GroupBanData> bans) {
		ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024); // 1 MB buffer (adjust if needed)

		buffer.putInt(bans.size());

		for (GroupBanData ban : bans) {
			buffer.putInt(ban.getGroupId());

			buffer.put(Base58.decode(ban.getOffender()));
			buffer.put(Base58.decode(ban.getAdmin()));

			buffer.putLong(ban.getBanned());

			putSizedString(buffer, ban.getReason());

			// expiry (nullable long)
			if (ban.getExpiry() != null) {
				buffer.putInt(1);
				buffer.putLong(ban.getExpiry());
			} else {
				buffer.putInt(0);
			}

			// reference (64 bytes)
			buffer.put(ban.getReference());
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
		int count = buffer.getInt();
		List<GroupBanData> bans = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			int groupId = buffer.getInt();

			byte[] offenderBytes = new byte[ADDRESS_LENGTH];
			buffer.get(offenderBytes);
			String offender = Base58.encode(offenderBytes);

			byte[] adminBytes = new byte[ADDRESS_LENGTH];
			buffer.get(adminBytes);
			String admin = Base58.encode(adminBytes);

			long banned = buffer.getLong();

			String reason = readSizedString(buffer);

			int hasExpiry = buffer.getInt();
			Long expiry = null;
			if (hasExpiry == 1) {
				expiry = buffer.getLong();
			}

			byte[] reference = new byte[SIGNATURE_LENGTH];
			buffer.get(reference);

			GroupBanData ban = new GroupBanData(groupId, offender, admin, banned, reason, expiry, reference);
			bans.add(ban);
		}

		return new GroupBansMessage(id, bans);
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

	public GroupBansMessage cloneWithNewId(int newId) {
		GroupBansMessage clone = new GroupBansMessage(this.bans);
		clone.setId(newId);
		return clone;
	}
}
