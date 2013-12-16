package com.drwp.process.police;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class RunningApplication {

	private String name;
	private int nbrRestarts=0;
	private List<String> restartedAt = new ArrayList<String>();
	private String pidFile;
	private String version;

	public String getVersion() {
		return version;
	}
	public void setVersion(String version) {
		this.version = version;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public int getNbrRestarts() {
		return nbrRestarts;
	}
	public void setNbrRestarts(int nbrRestarts) {
		this.nbrRestarts = nbrRestarts;
	}
	public List<String> getRestartedAt() {
		return restartedAt;
	}
	public void setRestartedAt(List<String> restartedAt) {
		this.restartedAt = restartedAt;
	}
	public String getPidFile() {
		return pidFile;
	}
	public void setPidFile(String pidFile) {
		this.pidFile = pidFile;
	}
}
