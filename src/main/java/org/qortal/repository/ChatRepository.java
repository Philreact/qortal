package org.qortal.repository;

import org.qortal.data.chat.ActiveChats;
import org.qortal.data.chat.ChatMessage;
import org.qortal.data.group.GroupActivitySummary;
import org.qortal.data.transaction.ChatTransactionData;

import java.util.List;

import static org.qortal.data.chat.ChatMessage.Encoding;

public interface ChatRepository {

	/**
	 * Returns CHAT messages matching criteria.
	 * <p>
	 * Expects EITHER non-null txGroupID OR non-null sender and recipient addresses.
	 */
	public List<ChatMessage> getMessagesMatchingCriteria(Long before, Long after,
			Integer txGroupId, byte[] reference, byte[] chatReferenceBytes, Boolean hasChatReference,
			List<String> involving, String senderAddress, Encoding encoding,
			Integer limit, Integer offset, Boolean reverse) throws DataException;

	public ChatMessage toChatMessage(ChatTransactionData chatTransactionData, Encoding encoding) throws DataException;

	public ActiveChats getActiveChats(String address, Encoding encoding, Boolean hasChatReference) throws DataException;

	/**
	 * Returns the groups with the most participating accounts, ordered by unique participant count descending.
	 * Counts distinct senders per group (group chats only: recipient IS NULL, tx_group_id &gt; 0;
	 * messages with a chat_reference are excluded).
	 *
	 * @param limit maximum number of groups to return (e.g. 10)
	 * @return list of group activity summaries, ordered by participant count descending
	 */
	public List<GroupActivitySummary> getTopGroupsByParticipantCount(int limit) throws DataException;

}
