package org.qortal.api.model;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class AnalyticsSummary {

	private int activeUsers;
	private int highlyActiveUsers;
	private int nameRegistrations;

	public AnalyticsSummary() {
		// Needed for JAXB
	}

	public int getActiveUsers() {
		return this.activeUsers;
	}

	public void setActiveUsers(int activeUsers) {
		this.activeUsers = activeUsers;
	}

	public int getHighlyActiveUsers() {
		return this.highlyActiveUsers;
	}

	public void setHighlyActiveUsers(int highlyActiveUsers) {
		this.highlyActiveUsers = highlyActiveUsers;
	}

	public int getNameRegistrations() {
		return this.nameRegistrations;
	}

	public void setNameRegistrations(int nameRegistrations) {
		this.nameRegistrations = nameRegistrations;
	}
}
