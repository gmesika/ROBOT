package com.geronimo.robot;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.*;


@XmlRootElement
public class RobotRecord
{
	public RobotRecord() {		
	}

	@XmlElement
	String recordName = "Unnamed";
	
	@XmlElement
	String recordDescription = "";

	@XmlElement
	boolean gracePeriodOn = false;
	
	@XmlElement
	int gracePeriodms = 1000;
	
	@XmlElement
	int numOfGraceAttempts = 10;
	
	@XmlElement
	boolean useAggressive = true;
	
	@XmlElement
	boolean closeWhenPlayingIsDone = false;
	
	@XmlElement
	boolean logIfPlayingSuccessfullyOrError = true;
		
	@XmlElement
	boolean supressMessagesDuringPlay = false;
	
	private List<RobotStep> steps = new ArrayList<RobotStep>();

	public void addRobotStep(RobotStep robotStep)
	{
		steps.add(robotStep);
	}

	@XmlElement(name = "RobotStep")
	public void setRobotSteps(List<RobotStep> listOfStates) {
		this.steps = listOfStates;
	}

	public List<RobotStep> getRobotSteps() {
		return this.steps;
	}
}
