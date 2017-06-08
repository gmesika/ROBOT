package com.geronimo.robot;

import javax.xml.bind.annotation.*;


@XmlRootElement
public class RobotStep 
{	
	private String nodePath;
	private String type;
	private String value;
	private String coordinates;
	private String delay;
	private String skip;
	private String identifier;
	private String clazz;
	
	public RobotStep(){}
	
	public RobotStep(String nodePath, String type, String value, String coordinates, String delay,
			String skip, String identifier, String clazz) {

		super();
		
		this.nodePath = nodePath;
		this.type = type;
		this.value = value;
		this.coordinates = coordinates;
		this.delay = delay;
		this.skip = skip;
		this.identifier = identifier;
		this.clazz = clazz;
	}

	public String getNodePath() {
		return nodePath;
	}

	public void setNodePath(String nodePath) {
		this.nodePath = nodePath;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public String getCoordinates() {
		return coordinates;
	}

	public void setCoordinates(String coordinates) {
		this.coordinates = coordinates;
	}

	public String getDelay() {
		return delay;
	}

	public void setDelay(String delay) {
		this.delay = delay;
	}

	public String getSkip() {
		return skip;
	}

	public void setSkip(String skip) {
		this.skip = skip;
	}

	public String getIdentifier() {
		return identifier;
	}

	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}

	public String getClazz() {
		return clazz;
	}

	public void setClazz(String clazz) {
		this.clazz = clazz;
	}

}
