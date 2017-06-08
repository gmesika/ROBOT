/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.geronimo.robot;

import java.awt.event.WindowEvent;

/**
 *
 * @author GUYMES
 */
public interface MonitoredWindowListener
{   
     public void monitoredWindowAdded(String hash);
     
     public void monitoredWindowRemoved(String hash);
     
     public void monitoredWindowUpdate(String hash);
     
     public void monitoredWindowCycleStarted();
     
     public void monitoredWindowCycleFinished();
     
     public void monitoredWindowRestored(WindowEvent e);
     
     public void monitoredWindowMinimized(WindowEvent e);
     
     public void monitoredWindowMaximized(WindowEvent e);
}
