package org.qortal.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.qortal.data.chat.ActiveChats;
import org.qortal.data.chat.ActiveChats.DirectChat;
import org.qortal.data.chat.ActiveChats.GroupChat;
import org.qortal.data.chat.ChatMessage.Encoding;
import org.qortal.group.Group;
import org.qortal.naming.Name;
import org.qortal.transform.TransformationException;
import org.qortal.transform.Transformer;
import org.qortal.utils.Serialization;

public class ActiveChatMessage extends Message {

	private final ActiveChats activeChats;

	public ActiveChatMessage(ActiveChats activeChats,  Encoding encoding) {
		super(MessageType.ACTIVE_CHAT);
		this.activeChats = activeChats;

		try {
			this.dataBytes = toBytes(activeChats, encoding);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to serialize ActiveChats", e);
		}

		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private ActiveChatMessage(int id, ActiveChats activeChats) {
		super(id, MessageType.ACTIVE_CHAT);
		this.activeChats = activeChats;
	}

	public ActiveChats getActiveChats() {
		return this.activeChats;
	}

public static Message fromByteBuffer(int id, ByteBuffer byteBuffer) throws MessageException {
	try {
		ActiveChats activeChats = fromBytes(byteBuffer);
		return new ActiveChatMessage(id, activeChats);
	} catch (IOException | TransformationException e) {
		throw new MessageException("Failed to deserialize ActiveChats", e);
	}
}

	private static byte[] toBytes(ActiveChats activeChats, Encoding encoding) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		// GROUP CHATS
		List<GroupChat> groups = activeChats.getGroups();
		Serialization.serializeSizedInt(bytes, groups.size());

		for (GroupChat group : groups) {
			Serialization.serializeInt(bytes, group.getGroupId());
			Serialization.serializeNullableString(bytes, group.getGroupName());

			Serialization.serializeNullableTimestamp(bytes, group.getTimestamp());
			Serialization.serializeNullableString(bytes, group.getSender());
			Serialization.serializeNullableString(bytes, group.getSenderName());
			Serialization.serializeNullableData(bytes, group.getSignature());

			bytes.write((byte) encoding.ordinal());

			Serialization.serializeNullableString(bytes, group.getData());
		}

		// DIRECT CHATS
		List<DirectChat> direct = activeChats.getDirect();
		Serialization.serializeSizedInt(bytes, direct.size());

		for (DirectChat chat : direct) {
			Serialization.serializeNullableString(bytes, chat.getAddress());
			Serialization.serializeNullableString(bytes, chat.getName());
			Serialization.serializeTimestamp(bytes, chat.getTimestamp());
			Serialization.serializeNullableString(bytes, chat.getSender());
			Serialization.serializeNullableString(bytes, chat.getSenderName());
		}

		return bytes.toByteArray();
	}

	private static ActiveChats fromBytes(ByteBuffer buffer) throws IOException, TransformationException {
		int groupCount = Serialization.deserializeSizedInt(buffer);
		List<GroupChat> groups = new ArrayList<>();

		for (int i = 0; i < groupCount; i++) {
			int groupId = Serialization.deserializeInt(buffer);
			String groupName = Serialization.deserializeNullableString(buffer);


			Long timestamp = Serialization.deserializeNullableTimestamp(buffer);
			String sender = Serialization.deserializeNullableString(buffer);
			String senderName = Serialization.deserializeNullableString(buffer);
			byte[] signature = Serialization.deserializeNullableData(buffer);

			Encoding encoding = Encoding.values()[buffer.get()];

			String data = Serialization.deserializeNullableString(buffer);

			GroupChat group = new GroupChat(groupId, groupName, timestamp, sender, senderName, signature, encoding,
					data != null ? encoding == Encoding.BASE64 ? org.bouncycastle.util.encoders.Base64.decode(data)
							: org.qortal.utils.Base58.decode(data)
							: null);
			groups.add(group);
		}

		int directCount = Serialization.deserializeSizedInt(buffer);
		List<DirectChat> direct = new ArrayList<>();

		for (int i = 0; i < directCount; i++) {
			String address = Serialization.deserializeNullableString(buffer);
			String name = Serialization.deserializeNullableString(buffer);
			long timestamp = Serialization.deserializeTimestamp(buffer);
			String sender = Serialization.deserializeNullableString(buffer);
			String senderName = Serialization.deserializeNullableString(buffer);

			DirectChat chat = new DirectChat(address, name, timestamp, sender, senderName);
			direct.add(chat);
		}

		return new ActiveChats(groups, direct);
	}
}
