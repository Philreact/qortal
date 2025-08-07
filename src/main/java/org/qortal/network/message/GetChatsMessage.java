package org.qortal.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.qortal.data.chat.ChatMessage.Encoding;
import org.qortal.transform.Transformer;
import org.qortal.utils.Base58;

public class GetChatsMessage extends Message {

	private Integer txGroupId;
	private List<String> involving;
	private Encoding encoding;
	private byte[] reference;
	private Long before;
	private Long after;
	private Boolean hasChatReference; // tri-state
	private byte[] chatReference;
	private String sender;
	private int offset;
	private int limit;
	private boolean reverse;

	private static final int ADDRESS_LENGTH = Transformer.ADDRESS_LENGTH;
	private static final int SIGNATURE_LENGTH = Transformer.SIGNATURE_LENGTH;

	public GetChatsMessage(Integer txGroupId, List<String> involving, Encoding encoding, byte[] reference, Long before, Long after,
	                       Boolean hasChatReference, byte[] chatReference, String sender, int offset, int limit, boolean reverse) {
		super(MessageType.GET_CHAT_MESSAGES);

		this.txGroupId = txGroupId;
		this.involving = involving;
		this.encoding = encoding;
		this.reference = reference;
		this.before = before;
		this.after = after;
		this.hasChatReference = hasChatReference;
		this.chatReference = chatReference;
		this.sender = sender;
		this.offset = offset;
		this.limit = limit;
		this.reverse = reverse;

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			// txGroupId (nullable int)
			bytes.write(intToBytes(txGroupId != null ? txGroupId : -1));

			// involving count
			bytes.write(intToBytes(involving.size()));
			for (String address : involving) {
				bytes.write(Base58.decode(address));
			}

			// encoding byte
			bytes.write((byte) encoding.ordinal());

			// reference (nullable)
			if (reference != null) {
				bytes.write(intToBytes(1));
				bytes.write(reference);
			} else {
				bytes.write(intToBytes(0));
			}

			// before (nullable long)
			if (before != null) {
				bytes.write(intToBytes(1));
				bytes.write(longToBytes(before));
			} else {
				bytes.write(intToBytes(0));
			}

			// after (nullable long)
			if (after != null) {
				bytes.write(intToBytes(1));
				bytes.write(longToBytes(after));
			} else {
				bytes.write(intToBytes(0));
			}

			// hasChatReference (tri-state)
			if (hasChatReference == Boolean.TRUE) {
				bytes.write(intToBytes(1));
				if (chatReference != null) {
					bytes.write(chatReference);
				} else {
					bytes.write(new byte[SIGNATURE_LENGTH]); // empty bytes
				}
			} else if (hasChatReference == Boolean.FALSE) {
				bytes.write(intToBytes(0));
			} else {
				bytes.write(intToBytes(-1));
			}

			// sender (nullable)
			if (sender != null) {
				bytes.write(intToBytes(1));
				bytes.write(Base58.decode(sender));
			} else {
				bytes.write(intToBytes(0));
			}

			// offset, limit, reverse
			bytes.write(intToBytes(offset));
			bytes.write(intToBytes(limit));
			bytes.write(intToBytes(reverse ? 1 : 0));
		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream", e);
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private GetChatsMessage(int id, Integer txGroupId, List<String> involving, Encoding encoding, byte[] reference, Long before, Long after,
	                        Boolean hasChatReference, byte[] chatReference, String sender, int offset, int limit, boolean reverse) {
		super(id, MessageType.GET_CHAT_MESSAGES);

		this.txGroupId = txGroupId;
		this.involving = involving;
		this.encoding = encoding;
		this.reference = reference;
		this.before = before;
		this.after = after;
		this.hasChatReference = hasChatReference;
		this.chatReference = chatReference;
		this.sender = sender;
		this.offset = offset;
		this.limit = limit;
		this.reverse = reverse;
	}

	public static Message fromByteBuffer(int id, ByteBuffer buffer) {
		// txGroupId (nullable int)
		int txGroupIdRaw = buffer.getInt();
		Integer txGroupId = (txGroupIdRaw == -1) ? null : txGroupIdRaw;

		// involving count
		int involvingCount = buffer.getInt();
		List<String> involving = new ArrayList<>(involvingCount);
		for (int i = 0; i < involvingCount; i++) {
			byte[] addressBytes = new byte[ADDRESS_LENGTH];
			buffer.get(addressBytes);
			involving.add(Base58.encode(addressBytes));
		}

		// encoding byte
		byte encodingByte = buffer.get();
		Encoding encoding = Encoding.values()[encodingByte];

		// reference (nullable)
		byte[] reference = null;
		if (buffer.getInt() == 1) {
			reference = new byte[SIGNATURE_LENGTH];
			buffer.get(reference);
		}

		// before (nullable long)
		Long before = null;
		if (buffer.getInt() == 1) {
			before = buffer.getLong();
		}

		// after (nullable long)
		Long after = null;
		if (buffer.getInt() == 1) {
			after = buffer.getLong();
		}

		// hasChatReference (tri-state)
		Boolean hasChatReference = null;
		byte[] chatReference = null;
		int hasChatRefFlag = buffer.getInt();
		if (hasChatRefFlag == 1) {
			hasChatReference = Boolean.TRUE;
			chatReference = new byte[SIGNATURE_LENGTH];
			buffer.get(chatReference);
		} else if (hasChatRefFlag == 0) {
			hasChatReference = Boolean.FALSE;
		} else {
			hasChatReference = null;
		}

		// sender (nullable)
		String sender = null;
		if (buffer.getInt() == 1) {
			byte[] senderBytes = new byte[ADDRESS_LENGTH];
			buffer.get(senderBytes);
			sender = Base58.encode(senderBytes);
		}

		// offset, limit, reverse
		int offset = buffer.getInt();
		int limit = buffer.getInt();
		boolean reverse = buffer.getInt() == 1;

		return new GetChatsMessage(id, txGroupId, involving, encoding, reference, before, after, hasChatReference, chatReference, sender, offset, limit, reverse);
	}

	private byte[] intToBytes(int value) {
		return ByteBuffer.allocate(4).putInt(value).array();
	}

	private byte[] longToBytes(long value) {
		return ByteBuffer.allocate(8).putLong(value).array();
	}

	// Getters
	public Integer getTxGroupId() { return txGroupId; }
	public List<String> getInvolving() { return involving; }
	public Encoding getEncoding() { return encoding; }
	public byte[] getReference() { return reference; }
	public Long getBefore() { return before; }
	public Long getAfter() { return after; }
	public Boolean getHasChatReference() { return hasChatReference; }
	public byte[] getChatReference() { return chatReference; }
	public String getSender() { return sender; }
	public int getOffset() { return offset; }
	public int getLimit() { return limit; }
	public boolean isReverse() { return reverse; }
}
