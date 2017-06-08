/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.geronimo.robot;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

/**
 *
 * @author GUYMES
 */
public class RobotFactory {

	/**
	 * @param args the command line arguments
	 */

	public static void main(String[] args) 
	{       
		setupLoggers();
		launchInspectorForm();
		LoadAndPlay(args);
		launchInspectedProgram();		
	}

	static Logger generalLogger = Logger.getLogger("MyLog");  
	static FileHandler generalLoggerFileHandler;  
	
	static Logger playingLogger = Logger.getLogger("MyPlayingLog");  
	static FileHandler playingLoggerFileHandler;  
	
	static Inspector inspector;

	private static void setupLoggers()
	{
		try {
			String logPath = Utils.getInstance().getPropValue("logPath");
			generalLoggerFileHandler = new FileHandler(logPath);
		} 
		catch (SecurityException e) 
		{
			e.printStackTrace();
		} 
		catch (IOException e)
		{
			e.printStackTrace();
		}  
		LogFormatter formatter = new LogFormatter();         
		generalLoggerFileHandler.setFormatter(formatter);  
		generalLogger.addHandler(generalLoggerFileHandler);
		generalLogger.setUseParentHandlers(false); 	     
		
		try {
			String logPath = Utils.getInstance().getPropValue("playingLogPath");
			playingLoggerFileHandler = new FileHandler(logPath);
		} 
		catch (SecurityException e) 
		{
			e.printStackTrace();
		} 
		catch (IOException e)
		{
			e.printStackTrace();
		}          
		playingLoggerFileHandler.setFormatter(formatter);  
		playingLogger.addHandler(playingLoggerFileHandler);
		playingLogger.setUseParentHandlers(false);
	}

	public static void writePlay(String line)
	{
		try {  
			playingLogger.info(line + "\n");  
		} catch (Exception e) 
		{  
			e.printStackTrace();  
		}  
	}
	
	private static void launchInspectorForm()
	{
		inspector  = new Inspector();              
	}

	public static void writeInfo(String line)
	{
		try {  
			generalLogger.info(line + "\n");  
                        System.out.println(line + "\n");
		} catch (Exception e) 
		{  
			e.printStackTrace();  
		}  
	}

	public static void writeError(String line)
	{
		System.out.println(line);
		try {  
			generalLogger.severe(line + "\n");  
		} catch (Exception e) 
		{  
			e.printStackTrace();  
		}  
	}

	private static void launchInspectedProgram()
	{		
		try {

			String className = Utils.getInstance().getPropValue("classNameToLaunch");
			String methodName = Utils.getInstance().getPropValue("methodNameToLaunch");
			String argsVal = Utils.getInstance().getPropValue("argsToLaunch");

			if (className == null || methodName == null || argsVal == null)
			{
				RobotFactory.writeError("Mandtory properties are missing!");
				return;				
			}

			RobotFactory.writeInfo("class name: " + className);
			RobotFactory.writeInfo("method name: " + className);
			RobotFactory.writeInfo("args vals: " + className);

			String[] args;
			if (argsVal.equals(Constants.EMPTY_STRING))
			{
				RobotFactory.writeInfo("args vals: String[0]");
				args = new String[0];
			}
			else
			{
				RobotFactory.writeInfo("args vals: String[]{argsVal}");
				args = new String[]{argsVal};
			}

			RobotFactory.writeInfo("About to create the new class...: " + className);
			Class<?> createdClass = Class.forName(className);		
			RobotFactory.writeInfo("Created.");
			RobotFactory.writeInfo("About to invoke the method: " + methodName);
			createdClass.getMethod(methodName, args.getClass()).invoke(createdClass, new Object[]{args});
			RobotFactory.writeInfo("Invoked.");
		} catch (Exception e) {
			e.printStackTrace();
			RobotFactory.writeError(e.toString());
		}
	}
	
	private static void LoadAndPlay(String[] args)
	{
		if (args.length == 0)
		{
			writeInfo("Args are 0");
			return ;
		}
		
		String recordXmlFileName = args[0];
		writeInfo("Load: " + args[0]);
		RobotController.getInstance().Load(recordXmlFileName);
		RobotController.getInstance().playAll();
		writeInfo("Play All");
	}
	
}