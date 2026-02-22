package org.qortal.data.group;

import io.swagger.v3.oas.annotations.media.Schema;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

/**
 * Summary of a group's chat activity for API responses.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class GroupActivitySummary {

	@XmlElement(name = "groupId")
	@Schema(description = "Group ID", example = "1")
	private int groupId;

	@XmlElement(name = "groupName")
	@Schema(description = "Group name")
	private String groupName;

	@XmlElement(name = "owner")
	@Schema(description = "Group owner address")
	private String owner;

	@XmlElement(name = "participantCount")
	@Schema(description = "Number of unique accounts that have sent at least one message in this group")
	private long participantCount;

	@XmlElement(name = "description")
	@Schema(description = "Group description")
	private String description;

	@XmlElement(name = "isOpen")
	@Schema(description = "Whether the group is open for anyone to join")
	private boolean isOpen;

	protected GroupActivitySummary() {
		/* For JAXB */
	}

	public GroupActivitySummary(int groupId, String groupName, String owner, long participantCount,
			String description, boolean isOpen) {
		this.groupId = groupId;
		this.groupName = groupName;
		this.owner = owner;
		this.participantCount = participantCount;
		this.description = description;
		this.isOpen = isOpen;
	}

	public int getGroupId() {
		return groupId;
	}

	public String getGroupName() {
		return groupName;
	}

	public String getOwner() {
		return owner;
	}

	public long getParticipantCount() {
		return participantCount;
	}

	public String getDescription() {
		return description;
	}

	public boolean isOpen() {
		return isOpen;
	}

}
