package org.qortal.network.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.qortal.data.voting.PollData;
import org.qortal.data.voting.PollOptionData;
import org.qortal.transform.TransformationException;
import org.qortal.transform.Transformer;
import org.qortal.utils.Serialization;
import org.qortal.voting.Poll;

import com.google.common.primitives.Ints;

public class PollsMessage extends Message {

    private static final int PUBLIC_KEY_LENGTH = Transformer.PUBLIC_KEY_LENGTH;

    private List<PollData> polls;

    public PollsMessage(List<PollData> polls) {
        super(MessageType.POLLS);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try {
            bytes.write(Ints.toByteArray(polls.size()));

            for (PollData poll : polls) {
                // Creator public key (32 bytes)
                bytes.write(poll.getCreatorPublicKey());

                // Owner (address)
                Serialization.serializeAddress(bytes, poll.getOwner());

                // Poll name (sized string)
                Serialization.serializeSizedStringV2(bytes, poll.getPollName());

                // Description (sized string)
                Serialization.serializeSizedStringV2(bytes, poll.getDescription());

                // Published timestamp (int64)
                bytes.write(longToBytes(poll.getPublished()));

                // Poll options count
                List<PollOptionData> options = poll.getPollOptions();
                bytes.write(Ints.toByteArray(options.size()));

                // Serialize each option (sized string)
                for (PollOptionData option : options) {
                    Serialization.serializeSizedStringV2(bytes, option.getOptionName());
                }
            }

        } catch (IOException e) {
            throw new AssertionError("IOException shouldn't occur with ByteArrayOutputStream");
        }

        this.dataBytes = bytes.toByteArray();
        this.checksumBytes = Message.generateChecksum(this.dataBytes);
    }

    public PollsMessage(int id, List<PollData> polls) {
       	super(id, MessageType.POLLS);

		this.polls = polls;
    }

    public List<PollData> getPolls() {
        return this.polls;
    }

    public static Message fromByteBuffer(int id, ByteBuffer bytes) throws MessageException {
        	try {
        int pollCount = bytes.getInt();
        List<PollData> polls = new ArrayList<>(pollCount);
        for (int i = 0; i < pollCount; i++) {
            byte[] creatorPublicKey = new byte[PUBLIC_KEY_LENGTH];
            bytes.get(creatorPublicKey);
            
            String owner = Serialization.deserializeAddress(bytes);
            
            String pollName = Serialization.deserializeSizedStringV2(bytes, Poll.MAX_NAME_SIZE);
            
            String description = Serialization.deserializeSizedStringV2(bytes, Poll.MAX_DESCRIPTION_SIZE);
            
            long published = bytes.getLong();
            
            int optionCount = bytes.getInt();
            List<PollOptionData> options = new ArrayList<>(optionCount);
            
            for (int j = 0; j < optionCount; j++) {
                String optionName = Serialization.deserializeSizedStringV2(bytes, Poll.MAX_NAME_SIZE);
                options.add(new PollOptionData(optionName));
            }
            
            PollData pollData = new PollData(creatorPublicKey, owner, pollName, description, options, published);
            polls.add(pollData);
        }
     
        return new PollsMessage(id, polls);
        	} catch (TransformationException e) {
			throw new MessageException(e.getMessage(), e);
		}
    }

    public PollsMessage cloneWithNewId(int newId) {
        PollsMessage clone = new PollsMessage(this.polls);
        clone.setId(newId);
        return clone;
    }

    private static byte[] longToBytes(long value) {
        return ByteBuffer.allocate(8).putLong(value).array();
    }
}
