/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.geronimo.robot;

import java.awt.Window;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;


/**
 *
 * @author GUYMES
 */
public class MonitoredWindows
{    
	private static MonitoredWindows monitoredWindows;

	private final List<MonitoredWindowListener> listeners = new ArrayList<MonitoredWindowListener>();

	private long syncStartRunningTime = 0;
	private long syncEndRunningTime = 0;
	private boolean logSyncDuration = false;
	
	// currently leave useListeners as false because in case that
	// option to inspect hidden windows is TRUE then it will fail because
	// update is running faster then added windows method
	public boolean useListeners = false; 
	
	public static MonitoredWindows getInstance()
	{
		if (monitoredWindows == null)
		{
			monitoredWindows = new MonitoredWindows();
		}

		return monitoredWindows;
	}

	private List m_monitoredWindows;

	public synchronized List getWindows()
	{
		if (m_monitoredWindows == null)
		{
			m_monitoredWindows = new ArrayList();    
		}

		return m_monitoredWindows;
	}   

	public void registerForWindowsNotifictions(MonitoredWindowListener listener)
	{
		listeners.add (listener);
	}

	private void AddListenerToWindow(Window window)
	{    	    
		window.addWindowStateListener(new WindowStateListener() {

			@Override
			public void windowStateChanged(WindowEvent e) {

				RobotFactory.writeInfo(e.toString());

				switch (e.getID())
				{
				case WindowEvent.WINDOW_ACTIVATED:				
				{break;} //TODO
				case WindowEvent.WINDOW_CLOSED:			
				{break;}//TODO
				case WindowEvent.WINDOW_CLOSING:				
				{break;}//TODO
				case WindowEvent.WINDOW_DEACTIVATED:				
				{break;}//TODO
				case WindowEvent.WINDOW_DEICONIFIED:				
				{break;}//TODO
				case WindowEvent.WINDOW_GAINED_FOCUS:				
				{break;}//TODO
				case WindowEvent.WINDOW_ICONIFIED:				
				{break;}//TODO
				case WindowEvent.WINDOW_LOST_FOCUS:				
				{break;}//TODO
				case WindowEvent.WINDOW_OPENED:				
				{break;}//TODO
				case WindowEvent.WINDOW_STATE_CHANGED:				
				{
					if (e.getNewState() == 0)
					{
						monitoredWindowRestoredEvent(e);
					}
					if (e.getNewState() == 1)
					{
						monitoredWindowMinimazedEvent(e);
					}
					if (e.getNewState() == 6)
					{
						monitoredWindowMaximizedEvent(e);
					}
					break;
				}
				}
			}
		});
	}

	public synchronized void syncWindows()
	{
		monitoredWindowCycleStartedEvent();

		Window[] windows = Window.getWindows();
		List currentWindows = new ArrayList();
		List currentlyAddedWindows = new ArrayList();

		for (Window window : windows)
		{   
			if (window.isVisible() == true || Inspector.getInstance().isInspectHiddenWindows())
			{
				String hash = String.valueOf(window.hashCode());
				currentWindows.add(hash);
				if (! getWindows().contains(hash))
				{
					getWindows().add(hash);
					if (useListeners)
						monitoredWindowAddedEvent(hash);
					else
						Inspector.getInstance().monitoredWindowAdded(hash);

					currentlyAddedWindows.add(hash);

					AddListenerToWindow(window);
				}   
			}                    
		}

		List removedWindows = new ArrayList();

		for (Object hash : getWindows())
		{
			if (! currentWindows.contains(hash))
			{
				// window was closed
				if (useListeners)
					monitoredWindowRemovedEvent((String) hash);
				else
					Inspector.getInstance().monitoredWindowRemoved((String) hash);

				removedWindows.add(hash);
			}            

			if (currentWindows.contains(hash) && ! currentlyAddedWindows.contains(hash)
					&& ! removedWindows.contains(hash))
			{
				if (useListeners)            		
					monitoredWindowUpdateEvent((String) hash);
				else
					Inspector.getInstance().monitoredWindowUpdate((String) hash);
			}
		}

		for (Object hash : removedWindows)
		{
			MonitoredWindows.getInstance().getWindows().remove(hash);
		}         

		monitoredWindowCycleFinishedEvent();

	}

	public void monitoredWindowAddedEvent(String hash)
	{    	
		for (MonitoredWindowListener listener : listeners)
		{
			listener.monitoredWindowAdded(hash);
		}
	}

	public void monitoredWindowUpdateEvent(String hash)
	{    	
		for (MonitoredWindowListener listener : listeners)
		{
			listener.monitoredWindowUpdate(hash);
		}
	}

	public void monitoredWindowRemovedEvent(String hash)
	{
		for (MonitoredWindowListener listener : listeners)
		{
			listener.monitoredWindowRemoved(hash);
		}
	}

	public void monitoredWindowCycleStartedEvent()
	{
		syncStartRunningTime = Calendar.getInstance().getTimeInMillis(); 

		//JavaApplication1.writeToLog("Start Thread:" + Thread.currentThread().getId() + " " + Thread.currentThread().getName());
		for (MonitoredWindowListener listener : listeners)
		{
			listener.monitoredWindowCycleStarted();
		}
	}

	public void monitoredWindowCycleFinishedEvent()
	{
		//JavaApplication1.writeToLog("End Thread:" + Thread.currentThread().getId() + " " + Thread.currentThread().getName());
		for (MonitoredWindowListener listener : listeners)
		{
			listener.monitoredWindowCycleFinished();
		}

		syncEndRunningTime = Calendar.getInstance().getTimeInMillis(); 

		if (logSyncDuration)
		{
			RobotFactory.writeInfo("Sync took: " + String.valueOf(syncEndRunningTime - syncStartRunningTime) + " ms.");
		}
	}

	public void monitoredWindowRestoredEvent(WindowEvent e)
	{
		for (MonitoredWindowListener listener : listeners)
		{
			listener.monitoredWindowRestored(e);
		}
	}

	public void monitoredWindowMinimazedEvent(WindowEvent e)
	{
		for (MonitoredWindowListener listener : listeners)
		{
			listener.monitoredWindowMinimized(e);
		}
	}

	public void monitoredWindowMaximizedEvent(WindowEvent e)
	{
		for (MonitoredWindowListener listener : listeners)
		{
			listener.monitoredWindowMaximized(e);
		}
	}

	public Window getNativeWindow(String hash, StringBuffer windowIndex)
	{
		Window[] windows = Window.getWindows();
		int counter = 0;
		for (Window window : windows)
		{
			if (String.valueOf(window.hashCode()).equals(hash))
			{
				windowIndex.append(counter);
				return window;
			}
			counter = counter + 1;
		}

		return null;
	}    

}
