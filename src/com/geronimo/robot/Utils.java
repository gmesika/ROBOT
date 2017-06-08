package com.geronimo.robot;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Utils {

	static Utils instance;
	
	public static Utils getInstance()
	{
		if (instance==null)
			instance= new Utils();
		
		return instance;
	}

	public String getPropValue(String propName) {
 		
		Properties prop = new Properties();
		String propFileName = "config.properties";
 
		InputStream inputStream = getClass().getClassLoader().getResourceAsStream(propFileName);
 
		if (inputStream != null) {
			try {
				prop.load(inputStream);
			} catch (IOException e) {
				RobotFactory.writeError(e.toString());
				e.printStackTrace();
				return null;
			}
		} else {
			RobotFactory.writeError("property file '" + propFileName + "' not found!");
			return null;
		}
 
		// get the property value and print it out
		String propVal = prop.getProperty(propName);
		
		return propVal;
	}
}
