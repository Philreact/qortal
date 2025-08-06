package org.qortal.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.qortal.transform.TransformationException;
import org.qortal.utils.Serialization;
import org.qortal.voting.Poll;

public class GetPollVotesMessage extends Message {

	private final String pollName;
	private final boolean onlyCounts;

	// Constructor for creating message to SEND
	public GetPollVotesMessage(String pollName, boolean onlyCounts) {
		super(MessageType.GET_POLL_VOTES);

		this.pollName = pollName;
		this.onlyCounts = onlyCounts;

		try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
			// Serialize pollName using V2 sized string encoding
			Serialization.serializeSizedStringV2(bytes, pollName);

			// Serialize onlyCounts flag as 4-byte integer (0 = false, 1 = true)
			bytes.write(intToBytes(onlyCounts ? 1 : 0));

			this.dataBytes = bytes.toByteArray();
			this.checksumBytes = Message.generateChecksum(this.dataBytes);

		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream", e);
		}
	}

	// Constructor for receiving a parsed message
	private GetPollVotesMessage(int id, String pollName, boolean onlyCounts) {
		super(id, MessageType.GET_POLL_VOTES);
		this.pollName = pollName;
		this.onlyCounts = onlyCounts;
	}

	public String getPollName() {
		return this.pollName;
	}

	public boolean isOnlyCounts() {
		return this.onlyCounts;
	}

	// Deserialization from ByteBuffer when RECEIVING message
	public static Message fromByteBuffer(int id, ByteBuffer bytes) throws MessageException {
		try {
			// Deserialize pollName using V2 method
			String pollName = Serialization.deserializeSizedStringV2(bytes, Poll.MAX_NAME_SIZE);

			// Deserialize onlyCounts flag
			int onlyCountsInt = bytes.getInt();
			boolean onlyCounts = (onlyCountsInt == 1);

			return new GetPollVotesMessage(id, pollName, onlyCounts);

		} catch (TransformationException e) {
			throw new MessageException(e.getMessage(), e);
		}
	}

	private static byte[] intToBytes(int value) {
		return ByteBuffer.allocate(4).putInt(value).array();
	}

	public GetPollVotesMessage cloneWithNewId(int newId) {
		GetPollVotesMessage clone = new GetPollVotesMessage(this.pollName, this.onlyCounts);
		clone.setId(newId);
		return clone;
	}
}
