package org.qortal.network.message;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.qortal.api.model.PollVotes;
import org.qortal.data.voting.VoteOnPollData;
import org.qortal.transform.Transformer;

public class PollVotesMessage extends Message {

    private static final int PUBLIC_KEY_LENGTH = Transformer.PUBLIC_KEY_LENGTH;

    private final PollVotes pollVotes;

    public PollVotesMessage(PollVotes pollVotes) {
        super(MessageType.POLL_VOTES);

        this.pollVotes = pollVotes;

        ByteBuffer buffer = serializePollVotes(pollVotes);

        this.dataBytes = new byte[buffer.position()];
        buffer.rewind();
        buffer.get(this.dataBytes);

        this.checksumBytes = Message.generateChecksum(this.dataBytes);
    }

    public PollVotesMessage(int id, PollVotes pollVotes) {
        this(pollVotes);
        this.setId(id);
    }

    public PollVotes getPollVotes() {
        return this.pollVotes;
    }

    private static ByteBuffer serializePollVotes(PollVotes pollVotes) {
        ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024); // 1 MB buffer (adjust if needed)

        // Write whether votes list is present (1 = yes, 0 = no)
        buffer.putInt(pollVotes.votes != null ? 1 : 0);

        // Write votes list (if present)
        if (pollVotes.votes != null) {
            buffer.putInt(pollVotes.votes.size());
            for (VoteOnPollData vote : pollVotes.votes) {
                putSizedString(buffer, vote.getPollName());

                buffer.put(vote.getVoterPublicKey());

                buffer.putInt(vote.getOptionIndex());
            }
        }

        // Total votes and total weight
        buffer.putInt(pollVotes.totalVotes);
        buffer.putInt(pollVotes.totalWeight);

        // Vote counts list
        buffer.putInt(pollVotes.voteCounts.size());
        for (PollVotes.OptionCount optionCount : pollVotes.voteCounts) {
            putSizedString(buffer, optionCount.optionName);
            buffer.putInt(optionCount.voteCount);
        }

        // Vote weights list
        buffer.putInt(pollVotes.voteWeights.size());
        for (PollVotes.OptionWeight optionWeight : pollVotes.voteWeights) {
            putSizedString(buffer, optionWeight.optionName);
            buffer.putInt(optionWeight.voteWeight);
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

    public static PollVotesMessage fromByteBuffer(int id, ByteBuffer buffer) {
        int hasVotes = buffer.getInt();
        List<VoteOnPollData> votes = null;

        if (hasVotes == 1) {
            int votesCount = buffer.getInt();
            votes = new java.util.ArrayList<>(votesCount);
            for (int i = 0; i < votesCount; i++) {
                String pollName = readSizedString(buffer);

                byte[] voterPublicKey = new byte[PUBLIC_KEY_LENGTH];
                buffer.get(voterPublicKey);

                int optionIndex = buffer.getInt();

                votes.add(new VoteOnPollData(pollName, voterPublicKey, optionIndex));
            }
        }

        int totalVotes = buffer.getInt();
        int totalWeight = buffer.getInt();

        int voteCountsSize = buffer.getInt();
        List<PollVotes.OptionCount> voteCounts = new java.util.ArrayList<>(voteCountsSize);
        for (int i = 0; i < voteCountsSize; i++) {
            String optionName = readSizedString(buffer);
            int voteCount = buffer.getInt();
            voteCounts.add(new PollVotes.OptionCount(optionName, voteCount));
        }

        int voteWeightsSize = buffer.getInt();
        List<PollVotes.OptionWeight> voteWeights = new java.util.ArrayList<>(voteWeightsSize);
        for (int i = 0; i < voteWeightsSize; i++) {
            String optionName = readSizedString(buffer);
            int voteWeight = buffer.getInt();
            voteWeights.add(new PollVotes.OptionWeight(optionName, voteWeight));
        }

        PollVotes pollVotes = new PollVotes(votes, totalVotes, totalWeight, voteCounts, voteWeights);
        return new PollVotesMessage(id, pollVotes);
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

    public PollVotesMessage cloneWithNewId(int newId) {
        PollVotesMessage clone = new PollVotesMessage(this.pollVotes);
        clone.setId(newId);
        return clone;
    }
}
