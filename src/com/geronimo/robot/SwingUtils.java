package com.geronimo.robot;

import java.lang.reflect.InvocationTargetException;

import javax.swing.SwingUtilities;


public class SwingUtils 
{	
	
	public static void invoke(Runnable runnable)
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			runnable.run();
		}
		else
		{
			//invokeAndContiune(runnable);

			try {
				SwingUtilities.invokeAndWait(runnable);
			} catch (InvocationTargetException e) {				
				e.printStackTrace();
				RobotFactory.writeInfo(e.toString());
			} catch (InterruptedException e) {
				e.printStackTrace();
				RobotFactory.writeInfo(e.toString());
			}		
		}
		
	}
	
	public static void invokeAndContiune(Runnable runnable)
	{
		if (SwingUtilities.isEventDispatchThread())
		{
			runnable.run();
		}
		else
		{			
			SwingUtilities.invokeLater(runnable);		
		}
		
	}

}
