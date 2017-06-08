package com.geronimo.robot;

public class RobotRecordPlay {

	private boolean played;
	
	private boolean playEndedSuccesfully = true;

	private int numberOfStepsPlayed = 0;
	
	public boolean isPlayed() {
		return played;
	}

	public void setPlayed(boolean played) {
		this.played = played;
	}

	public boolean isPlayEndedSuccesfully() {
		return playEndedSuccesfully;
	}

	public void setPlayEndedSuccesfully(boolean playEndedSuccesfully) {
		this.playEndedSuccesfully = playEndedSuccesfully;
	}

	public int getNumberOfStepsPlayed() {
		return numberOfStepsPlayed;
	}

	public void setNumberOfStepsPlayed(int numberOfStepsPlayed) {
		this.numberOfStepsPlayed = numberOfStepsPlayed;
	}
	
}
