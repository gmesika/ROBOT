package com.geronimo.robot;

import java.awt.AWTEvent;
import java.awt.event.WindowEvent;

import javax.swing.tree.TreePath;

public interface MonitoredEventsListener 
{
	public void monitoredMouse(TreePath treePath, AWTEvent event, int delay);
    
    public void monitoredKeyboard(TreePath treePath, AWTEvent event, int delay);
    
    public void monitoredWindowRestored(WindowEvent e);
    
    public void monitoredWindowMaximized(WindowEvent e);
    
    public void monitoredWindowMinimized(WindowEvent e);
    
}
