package org.qortal.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import org.qortal.transform.TransformationException;
import org.qortal.utils.Serialization;
import org.qortal.voting.Poll;

public class GetPollMessage extends Message {

	private String pollName;

	public GetPollMessage(String pollName) {
		super(MessageType.GET_POLL);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			Serialization.serializeSizedStringV2(bytes, pollName);

		} catch (IOException e) {
			throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
		}

		this.dataBytes = bytes.toByteArray();
		this.checksumBytes = Message.generateChecksum(this.dataBytes);
	}

	private GetPollMessage(int id, String pollName) {
		super(id, MessageType.GET_POLL);

		this.pollName = pollName;
	}

	public String getPollName() {
		return this.pollName;
	}



	public static Message fromByteBuffer(int id, ByteBuffer bytes) throws MessageException {
		try {
			String pollName = Serialization.deserializeSizedString(bytes, Poll.MAX_NAME_SIZE);

			return new GetPollMessage(id, pollName);
		} catch (TransformationException e) {
			throw new MessageException(e.getMessage(), e);
		}
	}


}
