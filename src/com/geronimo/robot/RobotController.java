/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.geronimo.robot;

import java.awt.AWTEvent;
import java.awt.AWTException;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.MouseInfo;
import java.awt.Robot;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.TrayIcon.MessageType;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JSeparator;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

/**
 *
 * @author guymes
 */
public class RobotController extends javax.swing.JFrame
implements MonitoredEventsListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = 6425175818831790173L;

	private static RobotController robotController;

	private Robot robotic;

	private boolean monitorInputs = true;

	private String fileNameInContext = Constants.EMPTY_STRING;
	private boolean fileInContextDirty = false;

	private boolean stopCurrentActionFlag = false;

	private Image playImage = null;
	private Image pauseImage = null;
	private Image recordImage = null;

	private TrayIcon trayIcon = null;

	//private boolean gracePeriodOn = true;
	//private int gracePeriodms = 1000;
	//private int numberOfGracePeriodAttemps = 10;

	private boolean duringLoad = false;
	private boolean duringRecord = false;

	private boolean logRecorderInput = false;

	private RobotRecord loadedRobotRecord;
	private RobotRecordPlay robotRecordPlay;
	
	private RobotRecordPropertiesForm robotRecordPropertiesForm;
	
	private boolean playing;
	
	/**
	 * Creates new form RobotController
	 */
	public RobotController() {
		initComponents();

		Container contentPane = this.getContentPane();
		contentPane.setLayout(new GridLayout());

		this.setVisible(true);

		robotController = this;
		registerForEventNotifications();

		try {
			robotic = new Robot();
		} catch (AWTException e) {
			RobotFactory.writeError("Failed to init the ROBOT! " + e.toString());
			e.printStackTrace();
		}

		setupTrayIcon();

		newRecordingMenuItemActionPerformed(null);

		Inspector.getInstance().setupTimer();

		stepsTable.setRowSelectionAllowed(true);
		stepsTable.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);

		setupContextMenu();

		setupRobot();

	}

	private void setupRobot()
	{
		logRecorderInput = false;
		enableInputLoggingMenuItem.setSelected(logRecorderInput);	

		stepsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

			@Override
			public void valueChanged(ListSelectionEvent e) {				
				if (stepsTable.getSelectedRow() > -1)
				{
					String nodePath = (String) stepsTable.getValueAt(stepsTable.getSelectedRow(), 0);
					updateCurrentlySelectedNode(nodePath);
				}
				else
				{
					updateCurrentlySelectedNode(Constants.EMPTY_STRING);
				}
			}
		} );			
	    
		if (robotRecordPropertiesForm == null)
    		robotRecordPropertiesForm = new RobotRecordPropertiesForm();
		
		playing = false;
	}
	
	private void setupContextMenu()
	{		
		JMenuItem findInInpectorMenuItem = new JMenuItem("Find in inspector");
		findInInpectorMenuItem.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				int selectedRow = stepsTable.getSelectedRow();
				String nodePath = (String) stepsTable.getValueAt(selectedRow, 0);
				
				if (nodePath.equals("Robot"))
					return;
				
				String lastRecordedNode = nodePath.substring(nodePath.lastIndexOf(Constants.COMPONENTS_SEPERATOR) + 1);

				ComponentTreeNode root = (ComponentTreeNode) Inspector.getInstance().getInspectionTree().getModel().getRoot();
				TreeNode[] treeNodes = Inspector.getInstance().findPathToNode (root, nodePath.split(Constants.COMPONENTS_SEPERATOR), 1);

				if (treeNodes == null || treeNodes.length == 1)
				{
					JOptionPane.showMessageDialog(RobotController.getInstance(), "Can't find the path to it...\nMaybe window is not opened yet.", "Lost Path", MessageType.ERROR.ordinal());
					return;
				}

				ComponentTreeNode lastTreeNode = (ComponentTreeNode)treeNodes[treeNodes.length - 1];
				if (! lastTreeNode.getNodeDescription().equals(lastRecordedNode))
				{
					JOptionPane.showMessageDialog(RobotController.getInstance(), "Found only partial path to this node", "Lost Path", MessageType.ERROR.ordinal());
				}

				TreePath tp = new TreePath(treeNodes);
				Inspector.getInstance().getInspectionTree().setSelectionPath(tp);

			}
		});	
		
		JMenuItem convertToRandomNumber = new JMenuItem("Convert to random number");
		convertToRandomNumber.addActionListener(new ActionListener() {
			
			@Override
			public void actionPerformed(ActionEvent paramActionEvent) {
				int selectedRow = stepsTable.getSelectedRow();
				
				int minRow = stepsTable.getSelectedRows()[0];
				int maxRow = stepsTable.getSelectedRows()[stepsTable.getSelectedRows().length-1] + 1;
				for (int counter = minRow; counter < maxRow; counter++)
				{
					String type = (String) stepsTable.getValueAt(selectedRow, 1);
					if (type.equals("401"))
					{					
						stepsTable.setValueAt("random#", counter, 2);
						
						setDirtyFlag(true);
					}
					else
					{
						JOptionPane.showMessageDialog(RobotController.getInstance(), "Row:" + counter + ". This option is applicable for 401 events only.", "Error", JOptionPane.ERROR_MESSAGE);
					}
				}				
			}
		});

		JMenuItem moveUpMenuItem = new JMenuItem("Move Up");
		moveUpMenuItem.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {				
				int selectedRow = stepsTable.getSelectedRow();

				if (selectedRow > 0)
				{
					replaceRows(selectedRow, selectedRow - 1);
					stepsTable.changeSelection(selectedRow - 1, selectedRow - 1, false, false);			
					
					setDirtyFlag(true);
				}
				
			}
		});

		JMenuItem moveDownMenuItem = new JMenuItem("Move Down");
		moveDownMenuItem.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {				
				int selectedRow = stepsTable.getSelectedRow();

				if (selectedRow < stepsTable.getRowCount() - 1)
				{
					replaceRows(selectedRow, selectedRow + 1);
					stepsTable.changeSelection(selectedRow + 1, selectedRow + 1, false, false);
					
					setDirtyFlag(true);
				}							
			}
		});
		
		JMenuItem addCloseMenuItem = new JMenuItem("Add Delay");
		addCloseMenuItem.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {				
				updateMockingTable("Robot", "Delay", "", "", 0, false, "", "Robot");
			}
		});

		jPopupMenu1.add(findInInpectorMenuItem);
		jPopupMenu1.add(new JSeparator());
		jPopupMenu1.add(convertToRandomNumber);
		jPopupMenu1.add(new JSeparator());
		jPopupMenu1.add(moveUpMenuItem);
		jPopupMenu1.add(moveDownMenuItem);
		jPopupMenu1.add(new JSeparator());
		jPopupMenu1.add(addCloseMenuItem);
	}

	private void replaceRows(int source, int target)
	{
		int maxColumnIndex = 7;

		Object sourceVal = null;
		Object targetVal = null;
		for (int counter = 0; counter <= maxColumnIndex; counter++)
		{
			sourceVal = stepsTable.getValueAt(source, counter);
			targetVal = stepsTable.getValueAt(target, counter);
			stepsTable.setValueAt(sourceVal, target, counter);
			stepsTable.setValueAt(targetVal, source, counter);
		}						
	}

	private void setupTrayIcon()
	{
		if (SystemTray.isSupported()) 
		{
			SystemTray tray = SystemTray.getSystemTray();

			try 
			{
				playImage = ImageIO.read(getClass().getResource("/com/geronimo/robot/images/StepForwardNormalRed.png"));
				pauseImage = ImageIO.read(getClass().getResource("/com/geronimo/robot/images/PauseNormalRed.png"));
				recordImage = ImageIO.read(getClass().getResource("/com/geronimo/robot/images/RecordNormalRed.png"));
			} 
			catch (IOException e2) 
			{
				RobotFactory.writeInfo("Failed to create tray icon images! " + e2.toString());
				e2.printStackTrace();
			}

			trayIcon = new TrayIcon(pauseImage, "Robot is not initizalied...");

			trayIcon.setImageAutoSize(true);

			try 
			{
				tray.add(trayIcon);
			} 
			catch (AWTException e) 
			{
				RobotFactory.writeError("Failed to add tray icon! " + e.toString());
				e.printStackTrace();
			}
		}
	}

	public static RobotController getInstance()
	{
		return robotController;
	}

	/**
	 * This method is called from within the constructor to initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is always
	 * regenerated by the Form Editor.
	 */
	@SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPopupMenu1 = new javax.swing.JPopupMenu();
        jPanel1 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        currentlySelectedNodePath = new javax.swing.JTextArea();
        jScrollPane2 = new javax.swing.JScrollPane();
        stepsTable = new javax.swing.JTable();
        playAllButton = new javax.swing.JButton();
        playSelected = new javax.swing.JButton();
        deleteAllBTN = new javax.swing.JButton();
        deleteSelectedBTN = new javax.swing.JButton();
        recordTBTN = new javax.swing.JToggleButton();
        pauseTBTN = new javax.swing.JToggleButton();
        stopCurrentAction = new javax.swing.JButton();
        jMenuBar1 = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        newRecordingMenuItem = new javax.swing.JMenuItem();
        loadRecordingMenuItem = new javax.swing.JMenuItem();
        saveRecordingMenuItem = new javax.swing.JMenuItem();
        optionsMenu = new javax.swing.JMenu();
        recordingProperties = new javax.swing.JMenuItem();
        enableInputLoggingMenuItem = new javax.swing.JCheckBoxMenuItem();

        jPopupMenu1.setComponentPopupMenu(jPopupMenu1);

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Record & Play");
        setFocusCycleRoot(false);
        getContentPane().setLayout(new java.awt.GridLayout(1, 0));

        jLabel1.setText("Currently selected node:");

        currentlySelectedNodePath.setEditable(false);
        currentlySelectedNodePath.setColumns(20);
        currentlySelectedNodePath.setRows(1);
        currentlySelectedNodePath.setAutoscrolls(false);
        jScrollPane1.setViewportView(currentlySelectedNodePath);

        stepsTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Node Path", "Type", "Value", "Coordinates", "Delay", "Skip", "Identifier", "Class"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.Integer.class, java.lang.Boolean.class, java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                false, false, false, false, true, true, false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        stepsTable.setComponentPopupMenu(jPopupMenu1);
        stepsTable.getTableHeader().setReorderingAllowed(false);
        jScrollPane2.setViewportView(stepsTable);

        playAllButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/geronimo/robot/images/StepForwardNormalRed.png"))); // NOI18N
        playAllButton.setText("Play All");
        playAllButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                playAllButtonActionPerformed(evt);
            }
        });

        playSelected.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/geronimo/robot/images/StepForwardNormalOrange.png"))); // NOI18N
        playSelected.setText("Play Selected");
        playSelected.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                playSelectedActionPerformed(evt);
            }
        });

        deleteAllBTN.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/geronimo/robot/images/EjectNormalRed.png"))); // NOI18N
        deleteAllBTN.setText("Delete All");
        deleteAllBTN.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteAllBTNActionPerformed(evt);
            }
        });

        deleteSelectedBTN.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/geronimo/robot/images/EjectNormalRed - Copy.png"))); // NOI18N
        deleteSelectedBTN.setText("Delete Selected");
        deleteSelectedBTN.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteSelectedBTNActionPerformed(evt);
            }
        });

        recordTBTN.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/geronimo/robot/images/RecordNormalRed.png"))); // NOI18N
        recordTBTN.setText("Record");
        recordTBTN.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                recordTBTNActionPerformed(evt);
            }
        });

        pauseTBTN.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/geronimo/robot/images/PauseNormalRed.png"))); // NOI18N
        pauseTBTN.setText("Pause");
        pauseTBTN.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pauseTBTNActionPerformed(evt);
            }
        });

        stopCurrentAction.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/geronimo/robot/images/stop-normal-red.png"))); // NOI18N
        stopCurrentAction.setLabel("Stop current action");
        stopCurrentAction.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stopCurrentActionActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jScrollPane2)
                            .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel1Layout.createSequentialGroup()
                                .addComponent(jLabel1)
                                .addGap(0, 0, Short.MAX_VALUE)))
                        .addContainerGap())
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(recordTBTN)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(pauseTBTN))
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(playAllButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(playSelected, javax.swing.GroupLayout.PREFERRED_SIZE, 135, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(deleteAllBTN)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(deleteSelectedBTN)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(stopCurrentAction)))
                        .addGap(0, 9, Short.MAX_VALUE))))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(recordTBTN)
                    .addComponent(pauseTBTN))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 345, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(stopCurrentAction, javax.swing.GroupLayout.PREFERRED_SIZE, 41, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(playAllButton)
                        .addComponent(playSelected)
                        .addComponent(deleteAllBTN)
                        .addComponent(deleteSelectedBTN)))
                .addContainerGap(17, Short.MAX_VALUE))
        );

        getContentPane().add(jPanel1);

        fileMenu.setText("File");

        newRecordingMenuItem.setText("New Recording");
        newRecordingMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newRecordingMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(newRecordingMenuItem);

        loadRecordingMenuItem.setText("Load Recording");
        loadRecordingMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadRecordingMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(loadRecordingMenuItem);

        saveRecordingMenuItem.setText("Save Recording");
        saveRecordingMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveRecordingMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(saveRecordingMenuItem);

        jMenuBar1.add(fileMenu);

        optionsMenu.setLabel("Options");

        recordingProperties.setText("Recording Properties");
        recordingProperties.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                recordingPropertiesActionPerformed(evt);
            }
        });
        optionsMenu.add(recordingProperties);

        enableInputLoggingMenuItem.setSelected(true);
        enableInputLoggingMenuItem.setText("Enable recorder input logging");
        enableInputLoggingMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enableInputLoggingMenuItemActionPerformed(evt);
            }
        });
        optionsMenu.add(enableInputLoggingMenuItem);

        jMenuBar1.add(optionsMenu);

        setJMenuBar(jMenuBar1);

        pack();
    }// </editor-fold>//GEN-END:initComponents

	private void playAllButtonActionPerformed(java.awt.event.ActionEvent evt) { 
		playAll();
	}        
	
	protected void playAll()
	{
		stopCurrentActionFlag = false;
		stopCurrentAction.setEnabled(true);

		final int numOfRows = stepsTable.getRowCount();

		if (numOfRows == 0)
		{
			JOptionPane.showMessageDialog(this, "You need to record at least one step!", "Error", MessageType.ERROR.ordinal());
			return;
		}

		if (stepsTable.getSelectedRow() == -1)
		{
			stepsTable.setRowSelectionInterval(0, 0);
		}

		final int selectedRow = stepsTable.getSelectedRow();

		monitorInputs = false;
		Inspector.getInstance().setListenToInputEvents(false); // this dosen't really matter
		pauseRecording();          	

		new Thread(new Runnable() {
			@Override 
			public void run() {
				playing = true;
				PlaySteps(selectedRow, numOfRows);
				Inspector.getInstance().setListenToInputEvents(true); // this dosen't really matter
				playing = false;
				playingIsDone();
			} 
		}).start();

	}

	private void playSelectedActionPerformed(java.awt.event.ActionEvent evt) {        

		stopCurrentActionFlag = false;
		stopCurrentAction.setEnabled(true);

		final int selectedRow = stepsTable.getSelectedRow();
		if (selectedRow == -1)
		{
			JOptionPane.showMessageDialog(this, "You have to select a record to play!", "Error", MessageType.ERROR.ordinal());
			return;
		}

		monitorInputs = false;
		Inspector.getInstance().setListenToInputEvents(false); // this dosen't really matter
		pauseRecording();            

		new Thread(new Runnable() {
			@Override 
			public void run() {
				playing = true;				
				PlaySteps(selectedRow, stepsTable.getSelectedRows()[stepsTable.getSelectedRows().length-1] + 1);
				Inspector.getInstance().setListenToInputEvents(true); // this dosen't really matter
				playing = false;
				playingIsDone();
			} 
		}).start();

	}                                            

	private void playingIsDone()
	{
		int rv = 0;
		
		if (loadedRobotRecord.logIfPlayingSuccessfullyOrError)
		{
			if (robotRecordPlay.isPlayed() && robotRecordPlay.isPlayEndedSuccesfully())
			{
				RobotFactory.writePlay("Record Name: " + loadedRobotRecord.recordName + " (" + fileNameInContext + ") after " + 
						robotRecordPlay.getNumberOfStepsPlayed() + " steps ended successfully");
				
				rv = 0;
			}
			if (robotRecordPlay.isPlayed() && !robotRecordPlay.isPlayEndedSuccesfully())
			{
				RobotFactory.writePlay("Record Name: " + loadedRobotRecord.recordName + " (" + fileNameInContext + ") after " + 
						robotRecordPlay.getNumberOfStepsPlayed() + " steps ended with failure!");
				
				rv = 1;
			}
		}
		
		// reset default values
		robotRecordPlay.setNumberOfStepsPlayed(0);
		robotRecordPlay.setPlayed(false);
		robotRecordPlay.setPlayEndedSuccesfully(false);
		
		if (loadedRobotRecord.closeWhenPlayingIsDone)
			System.exit(rv);
	}
	
	private void deleteSelectedBTNActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteSelectedBTNActionPerformed

		final int selectedRow = stepsTable.getSelectedRow();
		if (selectedRow == -1)
		{
			JOptionPane.showMessageDialog(this, "You have to select a record to delete!", "Error", MessageType.ERROR.ordinal());
			return;
		}

		pauseRecording();
		SwingUtils.invoke(new Runnable() {
			public void run() {
				int endRow = stepsTable.getSelectedRows()[stepsTable.getSelectedRows().length-1] + 1;
				DefaultTableModel model = (DefaultTableModel) stepsTable.getModel();	
				for (int counter = endRow - 1; selectedRow <= counter; counter--)
				{					
					model.removeRow(counter);
				}				
			}
		});
	}//GEN-LAST:event_deleteSelectedBTNActionPerformed

	private void deleteAllBTNActionPerformed(java.awt.event.ActionEvent evt) {                 

		pauseRecording();

		SwingUtils.invoke(new Runnable() {
			public void run() {
				DefaultTableModel model = (DefaultTableModel) stepsTable.getModel();

				while (stepsTable.getRowCount() > 0)
					for (int counter = 0; counter < stepsTable.getRowCount(); counter++)
					{
						model.removeRow(counter);					
					}				
			}
		});
	}                                            

	private void recordTBTNActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_recordTBTNActionPerformed
		record();
	}//GEN-LAST:event_recordTBTNActionPerformed

	private void pauseTBTNActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pauseTBTNActionPerformed
		pauseRecording();
	}//GEN-LAST:event_pauseTBTNActionPerformed


	private void newRecordingMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newRecordingMenuItemActionPerformed

		pauseRecording();

		boolean askQuestion = false;
		if ((! fileNameInContext.equals(Constants.EMPTY_STRING)) && fileInContextDirty)
		{    		
			askQuestion = true;
		}
		else
		{
			if (fileNameInContext.equals(Constants.EMPTY_STRING) && stepsTable.getRowCount() > 0)
				askQuestion = true;
		}
		if (askQuestion)
		{
			int rv = JOptionPane.showConfirmDialog(this, "There is currently unsaved recording steps. Are you sure you want to discard these steps?", "Discard steps", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
			if (rv != 0)
			{
				return;
			}
		}

		createNewRobotRecording();
		fileNameInContext = Constants.EMPTY_STRING;
		fileInContextDirty = false;
		deleteAllBTNActionPerformed(null);

		setRobotTitle("new");

	}//GEN-LAST:event_newRecordingMenuItemActionPerformed

	private void createNewRobotRecording()
	{
		setLoadedRobotRecord(new RobotRecord());
		getLoadedRobotRecord().gracePeriodOn = true;
		robotRecordPlay = new RobotRecordPlay();
	}
	
	private void setRobotTitle(String type)
	{
		if (type.equals("new"))
		{			
			this.setTitle("Record & Play (Unnamed)");
			return;
		}
		if (type.equals("load") || type.equals("save"))
		{
			if (fileNameInContext != null && !fileNameInContext.equals(""))
				this.setTitle("Record & Play (" + fileNameInContext.substring(fileNameInContext.lastIndexOf("\\") + 1 , fileNameInContext.length()) + ")");
			return;
		}
		if(type.equals("dirty"))
		{
			if (fileNameInContext != null && !fileNameInContext.equals(""))
				this.setTitle("Record & Play (*" + fileNameInContext.substring(fileNameInContext.lastIndexOf("\\") + 1 , fileNameInContext.length()) + ")");
			return;
		}

	}

	protected void Load(String fileName)
	{
		pauseRecording();

		File fileNameToLoadFrom = getFileNameToLoadFrom(fileName);    	
		if (fileNameToLoadFrom == null)
		{
			return;
		}
		if (! fileNameToLoadFrom.exists())
		{
			JOptionPane.showMessageDialog(this, "File does not exists!", "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}

		if (! fileNameToLoadFrom.toString().equals(fileNameInContext))
		{
			if (stepsTable.getRowCount() > 0)
			{
				int rv = JOptionPane.showConfirmDialog(this, "Discard current recording?", "Question", MessageType.INFO.ordinal());
				if (rv == 0)
				{
					deleteAllBTNActionPerformed(null);
				}
				else
				{
					return;
				}
			}    		
		}
		else
		{
			deleteAllBTNActionPerformed(null);
		}

		setLoadedRobotRecord(readRobotRecordXmlFile(fileNameToLoadFrom.toString()));
		
		for (RobotStep roboStep : getLoadedRobotRecord().getRobotSteps())
		{
			updateMockingTable(roboStep.getNodePath(), roboStep.getType(), roboStep.getValue(), roboStep.getCoordinates(), Integer.parseInt(roboStep.getDelay()), 
					Boolean.parseBoolean(roboStep.getSkip()), roboStep.getIdentifier(), roboStep.getClazz());
		}
		
		robotRecordPlay = new RobotRecordPlay();
		fileNameInContext = fileNameToLoadFrom.toString();
		fileInContextDirty = false;
		setRobotTitle("load");				
	}

	private RobotRecord readRobotRecordXmlFile(String fileName)
	{
		try {		
			// create JAXB context and initializing Marshaller
			JAXBContext jaxbContext = JAXBContext.newInstance(RobotRecord.class);
			Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
			// specify the location and name of xml file to be read
			File XMLfile = new File(fileName);
			// this will create Java object - country from the XML file
			RobotRecord robotRec = (RobotRecord) jaxbUnmarshaller.unmarshal(XMLfile);
			return robotRec;

		} catch (JAXBException e) {
			e.printStackTrace();
		}
		return null;
	}
		
	private void loadRecordingMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadRecordingMenuItemActionPerformed

		duringLoad = true;
		Inspector.getInstance().hardStop();
		Load(null);
		Inspector.getInstance().hardStart();
		duringLoad = false;

	}//GEN-LAST:event_loadRecordingMenuItemActionPerformed
	
	private void save()
	{
		if (stepsTable.getRowCount() == 0)
		{
			JOptionPane.showMessageDialog(this, "There are no steps to save!", "Error", JOptionPane.OK_OPTION);
			return;
		}

		pauseRecording();
		//List<String> contents = new ArrayList<String>(); 

		if (getLoadedRobotRecord() == null)
		{
			setLoadedRobotRecord(new RobotRecord());
		}
		
		getLoadedRobotRecord().getRobotSteps().clear();
		
		for (int rowCounter = 0; rowCounter < stepsTable.getRowCount(); rowCounter++)
		{
			String nodePath = stepsTable.getValueAt(rowCounter, 0).toString();
			String type = stepsTable.getValueAt(rowCounter, 1).toString();
			String value = stepsTable.getValueAt(rowCounter, 2).toString();
			String coordinates = stepsTable.getValueAt(rowCounter, 3).toString();
			String delay = stepsTable.getValueAt(rowCounter, 4).toString();
			String skip = stepsTable.getValueAt(rowCounter, 5).toString();
			String identifier = Constants.EMPTY_STRING;
			if (stepsTable.getValueAt(rowCounter, 6) != null)
			{
				identifier = stepsTable.getValueAt(rowCounter, 6).toString();
			}
			String clazz = stepsTable.getValueAt(rowCounter, 7).toString();
			//contents.add(nodePath + "@" + type + "@" + value + "@" + coordinates + "@" + delay + "@" + skip + "@" + identifier + "@" + clazz );

			RobotStep robotStep = new RobotStep(nodePath, type, value, coordinates, delay, skip, identifier, clazz);
			getLoadedRobotRecord().addRobotStep(robotStep);
		} 

		File destinationFile = null;
		String fileName = null;
		//String shortFileName = Constants.EMPTY_STRING;
		if (fileNameInContext.equals(Constants.EMPTY_STRING))
		{
			destinationFile = selectFileNameToSave();
			if (destinationFile == null)
				return;

			fileName = destinationFile.toString();
			//shortFileName = destinationFile.getName();
		}
		else
		{
			fileName = fileNameInContext;
			//shortFileName = fileNameInContext.substring(fileNameInContext.lastIndexOf("\\") + 1 , fileNameInContext.length());
		}
		
		if (! fileName.toLowerCase().endsWith(".xml"))
		{
			fileName = fileName + ".xml";
		}

		writeXmlFile(fileName, getLoadedRobotRecord());
		//writeFile(fileName, contents);

		fileNameInContext = fileName;    	
		
		setDirtyFlag(false);		
	}

	public boolean writeXmlFile(String fileName, RobotRecord robotRec)
	{
		try {

			File file = new File(fileName);
			JAXBContext jaxbContext = JAXBContext.newInstance(RobotRecord.class);

			Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

			// output pretty printed
			jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);	 
			jaxbMarshaller.marshal(robotRec, file);
			jaxbMarshaller.marshal(robotRec, System.out);

		} catch (JAXBException e) {
			e.printStackTrace();
		}

		return true;
	}

	private void saveRecordingMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveRecordingMenuItemActionPerformed

		Inspector.getInstance().hardStop();
		save();
		Inspector.getInstance().hardStart();

	}//GEN-LAST:event_saveRecordingMenuItemActionPerformed

	private void stopCurrentActionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stopCurrentActionActionPerformed
		stopCurrentActionFlag = true;
	}//GEN-LAST:event_stopCurrentActionActionPerformed

	private void enableInputLoggingMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enableInputLoggingMenuItemActionPerformed
		logRecorderInput = enableInputLoggingMenuItem.isSelected();
	}//GEN-LAST:event_enableInputLoggingMenuItemActionPerformed

    private void recordingPropertiesActionPerformed(java.awt.event.ActionEvent evt) {                                                                
    	robotRecordPropertiesForm.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
    	
    	java.awt.EventQueue.invokeLater(new Runnable() {
			public void run() {
				robotRecordPropertiesForm.setVisible(true);
			}
		});        
    }                                                   

	private File selectFileNameToSave()
	{
		final JFileChooser fc = new JFileChooser();
		int returnVal = fc.showSaveDialog(null);

		if (returnVal == JFileChooser.APPROVE_OPTION) {
			return fc.getSelectedFile();            
		}
		return null;
	}

	private void writeTextFile(String fileName, List<String> lines)
	{
		PrintWriter writer;
		try {
			writer = new PrintWriter(fileName, "UTF-8");			
			for (String line : lines)
			{
				writer.println(line);
			}
			writer.close();
		} catch (FileNotFoundException e) {
			RobotFactory.writeError(e.toString());
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			RobotFactory.writeError(e.toString());
			e.printStackTrace();
		}
	}

	private File getFileNameToLoadFrom(String fileName)
	{
		if (fileName == null)
		{
			final JFileChooser fc = new JFileChooser();
			int returnVal = fc.showOpenDialog(null);

			if (returnVal == JFileChooser.APPROVE_OPTION) {
				return fc.getSelectedFile();                       
			}
		}
		else
		{
			return new File(fileName);
		}
		return null;                
	}

	private List<String> readFile(String filename)
	{
		List<String> records = new ArrayList<String>();
		try
		{
			BufferedReader reader = new BufferedReader(new FileReader(filename));
			String line;
			while ((line = reader.readLine()) != null)
			{
				records.add(line);
			}
			reader.close();
			return records;
		}
		catch (Exception e)
		{
			RobotFactory.writeError(e.toString());
			e.printStackTrace();
			return null;
		}
	}

	private void pauseRecording()
	{   
		RobotFactory.writeInfo("Recording paused at: " + Calendar.getInstance().getTime().toString());

		SwingUtils.invoke(new Runnable() {

			@Override
			public void run() {
				duringRecord = false;
				pauseTBTN.setSelected(true);
				recordTBTN.setSelected(false);
				monitorInputs = false;
				currentlySelectedNodePath.setText(Constants.EMPTY_STRING);
				stopCurrentAction.setEnabled(false);

				changeRobotIconToPause();

				Inspector.getInstance().resetLastInputClickTimestamp();
			}
		});

	}

	private void changeRobotIconToPause()
	{
		trayIcon.setImage(pauseImage);
		trayIcon.setToolTip("Robot is not doing anything...");
	}

	private void changeRobotIconToRecording()
	{
		trayIcon.setImage(recordImage);
		trayIcon.setToolTip("Robot is recording...");
	}

	private void record()
	{
		RobotFactory.writeInfo("Recording at: " + Calendar.getInstance().getTime().toString());

		SwingUtils.invoke(new Runnable() {

			@Override
			public void run() {
				duringRecord = true;
				recordTBTN.setSelected(true);
				pauseTBTN.setSelected(false);    	
				monitorInputs = true;
				Inspector.getInstance().resetLastInputClickTimestamp();
				stopCurrentAction.setEnabled(false);

				changeRobotIconToRecording();				
			}
		});

	}

	private void changeRobotIconToPlaying()
	{
		trayIcon.setImage(playImage);
		trayIcon.setToolTip("Robot is playing...");
	}

	public void displayTrayIconMessage(String caption, String text, TrayIcon.MessageType messageType)
	{
		//TODO leave this code??

		int xPosition = MouseInfo.getPointerInfo().getLocation().x;
		int yPosition = MouseInfo.getPointerInfo().getLocation().y;

		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		double width = screenSize.getWidth();
		double height = screenSize.getHeight();

		double maxHeightLocation = height * 4/5;
		double maxWidthLocation = width * 4/5;

		if (yPosition < maxHeightLocation && xPosition < maxWidthLocation)
			trayIcon.displayMessage(caption, text, messageType);
		else
			trayIcon.displayMessage(Constants.EMPTY_STRING, Constants.EMPTY_STRING, MessageType.NONE);
	}

	private void PlaySteps(int rowStart, int rowEnd)
	{      	
		changeRobotIconToPlaying();    	

		disableRnPControlsEnabledState();
		for (int rowCounter = rowStart; rowCounter < rowEnd; rowCounter++)
		{
			displayTrayIconMessage("Playing...", "Step " + rowCounter + " out of " + rowEnd, MessageType.INFO);
			final int rowCounterForRunnable = rowCounter;
			SwingUtils.invoke(new Runnable() {
				public void run() {
					stepsTable.changeSelection(rowCounterForRunnable, rowCounterForRunnable, false, false);
				}
			});

			String nodePath = stepsTable.getValueAt(rowCounter, 0).toString();
			String type = stepsTable.getValueAt(rowCounter, 1).toString();
			String value = stepsTable.getValueAt(rowCounter, 2).toString();
			String coordinates = stepsTable.getValueAt(rowCounter, 3).toString();
			int delay = Integer.parseInt(stepsTable.getValueAt(rowCounter, 4).toString());
			boolean skip = Boolean.parseBoolean(stepsTable.getValueAt(rowCounter, 5).toString());
			String identifier = Constants.EMPTY_STRING;
			if (stepsTable.getValueAt(rowCounter, 6) != null)
			{
				identifier = stepsTable.getValueAt(rowCounter, 6).toString();
			}
			String clazz = stepsTable.getValueAt(rowCounter, 7).toString();
			try {
				PlayStep(nodePath, type, value, coordinates, delay, skip, identifier, clazz);
			} catch (Exception e) {
				RobotFactory.writeError(e.toString());
				e.printStackTrace();
				break;
			}

			if (stopCurrentActionFlag)
				break;
		}           
		enableRnPControlsEnabledState();

		displayTrayIconMessage("Done!", "Done playing recording.", MessageType.INFO);

		changeRobotIconToPause();
	}

	private void disableRnPControlsEnabledState()
	{
		changeRnPControlsEnabledState(false);
	}

	private void enableRnPControlsEnabledState()
	{
		changeRnPControlsEnabledState(true);
	}

	private void changeRnPControlsEnabledState(boolean state)
	{
		recordTBTN.setEnabled(state);
		pauseTBTN.setEnabled(state);
		playAllButton.setEnabled(state);
		playSelected.setEnabled(state);
		deleteAllBTN.setEnabled(state);
		deleteSelectedBTN.setEnabled(state);
	}

	private ComponentTreeNode getComponentTreeNodeByNodeDescriptionRecursively(String[] path, int index, ComponentTreeNode currentComponent, boolean aggressive)
	{
		String windowTitle = path[index];
		ComponentTreeNode componentTreeNode = Inspector.getInstance().getComponentTreeNodeByNodeDescription(currentComponent, windowTitle, aggressive);
		if (index == (path.length - 1) || componentTreeNode == null)
		{
			return componentTreeNode;
		}
		return getComponentTreeNodeByNodeDescriptionRecursively(path, index + 1, componentTreeNode, aggressive);
	}

	private void handleRobotStep(String nodePath, String type, String value, String coordinates, int delay, boolean skip, String identifier, String clazz) throws Exception
	{
		switch (type)
		{		
		case "Delay":
			robotic.delay(getLoadedRobotRecord().gracePeriodms);
			break;
		}
	}
	
	private void PlayStep(String nodePath, String type, String value, String coordinates, int delay, boolean skip, String identifier, String clazz) throws Exception
	{	
		if (skip)
			return;

		stopCurrentAction.setEnabled(true);
		robotRecordPlay.setNumberOfStepsPlayed(robotRecordPlay.getNumberOfStepsPlayed() + 1);
		robotRecordPlay.setPlayed(true);
		
		if (nodePath.equals("Robot"))
		{
			handleRobotStep(nodePath, type, value, coordinates, delay, skip, identifier, clazz);
			return;
		}
				
		Component nativeFrame;
		Component component = null;

		try {					
			String[] path = nodePath.split(Constants.COMPONENTS_SEPERATOR);
			String windowTitle = path[1];
			ComponentTreeNode parentComponentTreeNode = Inspector.getInstance().getComponentTreeNodeByNodeDescription(Inspector.getInstance().getRoot(), windowTitle, false);

			if (parentComponentTreeNode == null && getLoadedRobotRecord().gracePeriodOn)
			{
				RobotFactory.writeInfo("checking grace period for window title: " + windowTitle);
				int graceCounter = 0;
				boolean graceFlagOn = true;
				while (parentComponentTreeNode == null && graceFlagOn && ! stopCurrentActionFlag)
				{
					robotic.delay(getLoadedRobotRecord().gracePeriodms);   
					RobotFactory.writeInfo("Counting with grace. Attempt " + graceCounter + " was delayed for: " + getLoadedRobotRecord().gracePeriodms);

					parentComponentTreeNode = Inspector.getInstance().getComponentTreeNodeByNodeDescription(Inspector.getInstance().getRoot(), windowTitle, true);
					graceCounter = graceCounter + 1;
					if (graceCounter == getLoadedRobotRecord().numOfGraceAttempts)
					{
						graceFlagOn = false;
					}
					displayTrayIconMessage("Working hard...", "Attempting to locate window.\n Attempt No.: " + graceCounter, MessageType.WARNING);
					RobotFactory.writeInfo("graceCounter is now: " + graceCounter);
				}
			}

			if (parentComponentTreeNode == null)
			{
				displayTrayIconMessage("Didn't find any!", "Attempting to locate window: " + windowTitle + " failed!", MessageType.ERROR);
				throw new Exception("Frame: " + windowTitle + "\n is not visible! \nIt was either closed, hidden or did not open at all.");
			}
			else{
				nativeFrame = parentComponentTreeNode.getWeakReferenceToComponent().get();
			}

			// Window was found, let's get to the component......

			if (path.length > 2)
			{
				ComponentTreeNode componentTreeNode ;
				componentTreeNode = getComponentTreeNodeByNodeDescriptionRecursively(path, 2, parentComponentTreeNode, false);

				String LeafName = path[path.length - 1];

				if (componentTreeNode == null && getLoadedRobotRecord().useAggressive)
				{
					RobotFactory.writeInfo("aggresivly checking for component: " + path[path.length-1]);
					displayTrayIconMessage("Working hard...", "Attempting to aggresivly locate compoent.", MessageType.WARNING);

					robotic.delay(getLoadedRobotRecord().gracePeriodms);					
					componentTreeNode = getComponentTreeNodeByNodeDescriptionRecursively(path, 2, parentComponentTreeNode, true);                											
				}

				if (componentTreeNode == null && getLoadedRobotRecord().gracePeriodOn)
				{
					int graceCounter = 0;
					boolean graceFlagOn = true;
					RobotFactory.writeInfo("checking grace period for component: " + path[path.length-1]);
					while (componentTreeNode == null && graceFlagOn && ! stopCurrentActionFlag)
					{
						robotic.delay(getLoadedRobotRecord().gracePeriodms);   
						RobotFactory.writeInfo("Counting with grace. Attempt " + graceCounter + " was delayed for: " + getLoadedRobotRecord().gracePeriodms);

						componentTreeNode = getComponentTreeNodeByNodeDescriptionRecursively(path, 2, parentComponentTreeNode, false);                	
						graceCounter = graceCounter + 1;
						if (graceCounter == getLoadedRobotRecord().numOfGraceAttempts)
						{
							graceFlagOn = false;
						}
						displayTrayIconMessage("Working hard...", "Attempting to locate compoent.\n Attempt No.: " + graceCounter, MessageType.WARNING);
						RobotFactory.writeInfo("graceCounter is now: " + graceCounter + " miliseconds: " + Calendar.getInstance().getTimeInMillis());
					}
				}

				if (componentTreeNode==null && Inspector.getInstance().useComponentNameMapping)
				{
                                    // should we try to locate the component by name mapping
                                    // only when it is not found or not ?
                                    
					String leafName = path[path.length-1];
					
					if (leafName.indexOf("[") > -1)
					{
						leafName = leafName.substring(0, leafName.indexOf("["));					
					}
					
					if (Inspector.getInstance().getAllComponentByNameTreeNodes().get(leafName) != null)						
						componentTreeNode = Inspector.getInstance().getAllComponentByNameTreeNodes().get(leafName).get();					
				}
				
				if (componentTreeNode==null)
				{
					displayTrayIconMessage("Didn't find any!", "Attempting to locate component: " + LeafName + " failed!", MessageType.ERROR);
					RobotFactory.writeInfo("Component not found at: "  + Calendar.getInstance().getTimeInMillis());
					throw new Exception("Component: " + LeafName + " was not found!");
				}

				component = componentTreeNode.getWeakReferenceToComponent().get();
			}

			// Component was found, let's robot the action...........

			// TODO create a gui option for this
			if (this.isFocused() || Inspector.getInstance().isFocused()){
				nativeFrame.requestFocus();        		
			}

			if (component != null && !component.isFocusOwner())
			{
				component.requestFocus();
			}

			int numOfDelayes = Math.round(delay / 60000);
			switch (Integer.parseInt(type))
			{
			case 401:
				
				int keyCode;
				if (value.equals("random#"))
				{
					keyCode = (int) ((Math.random()*(57 - 48 + 1)) + 48);
					//keyCode = new Random().nextInt(57) + 48;
				}
				else
					keyCode = Integer.parseInt(value);
				
				robotic.keyPress(keyCode);
				robotic.keyRelease(keyCode);

				if (delay > 1000)
					displayTrayIconMessage("ZzZzZzZ...", "Sleeping for " + delay/1000 + " seconds.", MessageType.INFO);

				if (numOfDelayes > 0)
				{        			
					for (int delayCounter = 0; delayCounter < numOfDelayes-1; delayCounter++)
					{
						robotic.delay(60000);
					}
				}

				robotic.delay(delay - (60000 * numOfDelayes));
				break;    	
			case 500:
			case 501:
			case 502:
				int x = 0;
				int y = 0;

				x = Integer.parseInt(coordinates.split(",")[0]);
				y = Integer.parseInt(coordinates.split(",")[1]);

				robotMouseMouse(component, x, y, Integer.parseInt(type), Integer.parseInt(value));

				if (delay > 1000)
					displayTrayIconMessage("ZzZzZzZ...", "Sleeping for " + delay/1000 + " seconds.", MessageType.INFO);

				if (numOfDelayes > 0)
				{        			
					for (int delayCounter = 0; delayCounter < numOfDelayes-1; delayCounter++)
					{
						robotic.delay(60000);
					}
				}

				robotic.delay(delay - (60000 * numOfDelayes));

				break;
			case 209:
				int stateVal = Integer.parseInt(value);
				switch (stateVal)
				{
				case 0:
					((JFrame)nativeFrame).setExtendedState(java.awt.Frame.NORMAL);
					break;
				case 1:
					((JFrame)nativeFrame).setExtendedState(java.awt.Frame.ICONIFIED);
					break;
				case 6:
					((JFrame)nativeFrame).setExtendedState(java.awt.Frame.MAXIMIZED_BOTH);        			
					break;
				}

				break;
			}    	  
		} catch (Exception e) {
			robotRecordPlay.setPlayEndedSuccesfully(false);
			e.printStackTrace();
			if (! loadedRobotRecord.supressMessagesDuringPlay)
				JOptionPane.showMessageDialog(this, e.toString());
			
			throw e;
		}
		finally
		{
			// Extremely important to GC these objects
			component = null;
			nativeFrame = null;
			System.gc();
			stopCurrentAction.setEnabled(false);
		}

	}

	private void robotMouseMouse(Component container, int x, int y, int type, int value)
	{
		if (x < 0 || y < 0)
		{
			RobotFactory.writeInfo("x or y are negative coordinates! returning.......");
			return;
		}

		int xOnScreenWithIndentation = -1;
		int yOnScreenWithIndentation = -1;

		try 
		{
			xOnScreenWithIndentation = container.getLocationOnScreen().x + x;
			yOnScreenWithIndentation = container.getLocationOnScreen().y + y;
		} catch (Exception e) 
		{
			RobotFactory.writeError(e.toString());
			e.printStackTrace();
		}
		if 	(xOnScreenWithIndentation == -1)
		{
			RobotFactory.writeInfo("component is not visible on screen, the x is -1");
			return;
		}

		robotic.mouseMove(xOnScreenWithIndentation, yOnScreenWithIndentation);

		int buttons = 0;
		switch (value)
		{
		case 1:
			buttons = InputEvent.BUTTON1_MASK;
			break;
		case 2:
			buttons = InputEvent.BUTTON2_MASK;
			break;
		case 3:
			buttons = InputEvent.BUTTON3_MASK;
			break;
		}

		switch (type)
		{
		case 500:						
			robotic.mousePress(buttons);	
			robotic.mouseRelease(buttons);		
			break;
		case 501:
			robotic.mousePress(buttons);
			break;
		case 502:
			robotic.mouseRelease(buttons);
			break;
		}	
	}

	/**
	 * @param args the command line arguments
	 */
	public static void main(String args[]) {
		/* Set the Nimbus look and feel */
		//<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
		/* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
		 * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
		 */
		try {
			for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
				if ("Nimbus".equals(info.getName())) {
					javax.swing.UIManager.setLookAndFeel(info.getClassName());
					break;
				}
			}
		} catch (ClassNotFoundException ex) {
			java.util.logging.Logger.getLogger(RobotController.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
		} catch (InstantiationException ex) {
			java.util.logging.Logger.getLogger(RobotController.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
		} catch (IllegalAccessException ex) {
			java.util.logging.Logger.getLogger(RobotController.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
		} catch (javax.swing.UnsupportedLookAndFeelException ex) {
			java.util.logging.Logger.getLogger(RobotController.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
		}
		//</editor-fold>

		/* Create and display the form */
		java.awt.EventQueue.invokeLater(new Runnable() {
			public void run() {
				new RobotController().setVisible(true);
			}
		});
	}

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextArea currentlySelectedNodePath;
    private javax.swing.JButton deleteAllBTN;
    private javax.swing.JButton deleteSelectedBTN;
    private javax.swing.JCheckBoxMenuItem enableInputLoggingMenuItem;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPopupMenu jPopupMenu1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JMenuItem loadRecordingMenuItem;
    private javax.swing.JMenuItem newRecordingMenuItem;
    private javax.swing.JMenu optionsMenu;
    private javax.swing.JToggleButton pauseTBTN;
    private javax.swing.JButton playAllButton;
    private javax.swing.JButton playSelected;
    private javax.swing.JToggleButton recordTBTN;
    private javax.swing.JMenuItem recordingProperties;
    private javax.swing.JMenuItem saveRecordingMenuItem;
    private javax.swing.JTable stepsTable;
    private javax.swing.JButton stopCurrentAction;
    // End of variables declaration//GEN-END:variables

	private void registerForEventNotifications()
	{
		Inspector.getInstance().registerForMonitoredEventsNotifictions(this);
	}

	private void updateCurrentlySelectedNode(String freeText)
	{
		currentlySelectedNodePath.setText(freeText);
	}

	@Override
	public void monitoredMouse(TreePath treePath, AWTEvent event, int delay) 
	{
		if (! monitorInputs)
		{
			return;
		}

		MouseEvent mouseEvent = (MouseEvent)event;

		if (logRecorderInput)
		{
			RobotFactory.writeInfo("From Recorder: " + mouseEvent.toString());
		}
                                             
		String FullPath = ((ComponentTreeNode)treePath.getLastPathComponent()).getFullPath();
		updateCurrentlySelectedNode(FullPath);
		String coordinates = String.valueOf(mouseEvent.getX()) + "," + String.valueOf(mouseEvent.getY());
                //String coordinates = String.valueOf(mouseEvent.getXOnScreen()) + "," + String.valueOf(mouseEvent.getYOnScreen());
		boolean skip = false;
		String identifier = getSpecialIdentifier(event.getSource());
		String clazz = event.getSource().getClass().getName();

                Component component = (Component) mouseEvent.getSource();
                
                RobotFactory.writeInfo ("component x: " + component.getX() +  " " + component.getY());
                
                //coordinates = String.valueOf(component.getX()+mouseEvent.getX()) + "," + String.valueOf(component.getY() + mouseEvent.getY());
		updateMockingTable(FullPath, String.valueOf(event.getID()), String.valueOf(mouseEvent.getButton()), coordinates, 
				delay, skip, identifier, clazz);
	}

	private String getSpecialIdentifier(Object obj)
	{
		//		if (obj instanceof JScrollBar)
		//		{
		//			return String.valueOf(((JScrollBar)obj).getOrientation());			
		//		}

		return null;
	}

	private boolean needSpecialIdentifier(Object obj)
	{
		//		if (obj instanceof JScrollBar)
		//		{
		//			return true;			
		//		}

		return false;
	}

	private boolean hasSpecialIdentifier(Object obj, String identifier)
	{
		//		if (obj instanceof JScrollBar)
		//		{
		//			return String.valueOf(((JScrollBar)obj).getOrientation()).equals(identifier);			
		//		}

		return false;
	}

	@Override
	public void monitoredKeyboard(TreePath treePath, AWTEvent event, int delay) 
	{
		if (! monitorInputs)
		{
			return;
		}

		KeyEvent keyEvent = (java.awt.event.KeyEvent)event;	

		if (logRecorderInput)
		{
			RobotFactory.writeInfo("From Recorder: " + keyEvent.toString());
		}

		String FullPath = ((ComponentTreeNode)treePath.getLastPathComponent()).getFullPath();
		updateCurrentlySelectedNode(FullPath);		
		String coordinates = Constants.EMPTY_STRING;
		boolean skip = false;
		String identifier = Constants.EMPTY_STRING;
		String clazz = event.getSource().getClass().getName();
		updateMockingTable(FullPath, String.valueOf(event.getID()), String.valueOf(keyEvent.getKeyCode()), coordinates, 
				delay, skip, identifier, clazz);		
	}

	private void addEvent(int eventID)
	{

		int selectedRow = stepsTable.getSelectedRow();
		String nodePath = (String) stepsTable.getValueAt(selectedRow, 0);
		String event = null;
		String coordinates = null;
		int delay = 0;
		boolean skip = false;
		String identifier = Constants.EMPTY_STRING;
		String clazz = (String) stepsTable.getValueAt(selectedRow, 7);
		switch (eventID)
		{
		case 502:
			event = (String) stepsTable.getValueAt(selectedRow, 2);
			coordinates = (String) stepsTable.getValueAt(selectedRow, 3);
			break;
		}			

		updateMockingTable(nodePath, String.valueOf(eventID), event, coordinates, 
				delay, skip, identifier, clazz);	
	}

	private void setDirtyFlag(boolean isDirty)	
	{
		fileInContextDirty = isDirty;
		if (isDirty)
		{
			setRobotTitle("dirty");	
		}
		else
		{
			setRobotTitle("save");
		}
	}
	
	private void updateMockingTable(final String nodePath, final String type, final String value, final String coordinates, final int delay, final boolean skip, 
			final String identifier, final String clazz)
	{
		if (duringRecord && ! fileNameInContext.equals(Constants.EMPTY_STRING))
		{
			setDirtyFlag(true);
		}

		final DefaultTableModel model = (DefaultTableModel) stepsTable.getModel();

		SwingUtils.invoke(new Runnable() {
			public void run() {

				boolean newSkip = skip;
				int newDelay = delay; 		
				String newType = type;

				if(duringRecord)
					if(type.equals(String.valueOf(MouseEvent.MOUSE_RELEASED)))
					{
						int numOfRows = model.getRowCount();
						if (numOfRows > 0)
						{							
							int previousRow = numOfRows - 1;
							String previousType = (String) model.getValueAt(previousRow, 1);

							if (! previousType.equals(String.valueOf(MouseEvent.MOUSE_PRESSED)))
							{
								//newType = String.valueOf(MouseEvent.MOUSE_CLICKED);
								model.setValueAt(String.valueOf(MouseEvent.MOUSE_CLICKED), previousRow, 1);
								//System.out.println("~!~!~!~!~!~! vMODIFIED TO MOUSE_CLIECKED ~!~!~!~!");
							}
						}
						else							
						{
							// the next action in table is relased
							newType = String.valueOf(MouseEvent.MOUSE_CLICKED);
							//System.out.println("~!~!~!~!~!~! vMODIFIED TO MOUSE_CLIECKED ~!~!~!~!");
						}
					}

				if(duringRecord)
					if(type.equals(String.valueOf(MouseEvent.MOUSE_PRESSED)))
					{
						int numOfRows = model.getRowCount();
						if (numOfRows > 0)
						{							
							int previousRow = numOfRows - 1;
							String previousType = (String) model.getValueAt(previousRow, 1);

							if (previousType.equals(String.valueOf(MouseEvent.MOUSE_PRESSED)))
							{
								model.setValueAt(String.valueOf(MouseEvent.MOUSE_CLICKED), previousRow, 1);
								//System.out.println("~!~!~!~!~!~! vMODIFIED TO MOUSE_CLIECKED ~!~!~!~!");
							}
						}
					}

				if(duringRecord)
					if(type.equals(String.valueOf(MouseEvent.MOUSE_CLICKED)))
					{
						int numOfRows = model.getRowCount();
						if (numOfRows > 1)
						{
							int previousRow = numOfRows - 1;
							String previousType = (String) model.getValueAt(previousRow, 1);
							boolean skipped = (boolean) model.getValueAt(previousRow, 5);
							String previousNodePath = (String) model.getValueAt(previousRow, 0);
							if (nodePath.equals(previousNodePath) && previousType.equals(String.valueOf(MouseEvent.MOUSE_RELEASED)) && (! skipped))
							{
								newSkip = true;							
							}

						}
					}

				int futureNumOfRows = model.getRowCount() + 1;

				if(duringRecord)					
				{
					if (futureNumOfRows > 1)
					{						
						int lineIndexToSetNewDelay = futureNumOfRows - 1;
						boolean lastLineSkipped = false;
						do
						{
							lineIndexToSetNewDelay = lineIndexToSetNewDelay - 1;
							lastLineSkipped = (boolean) model.getValueAt(lineIndexToSetNewDelay, 5);								
						}while(lineIndexToSetNewDelay > 0 && lastLineSkipped);

						model.setValueAt(newDelay, lineIndexToSetNewDelay, 4);
					}						
				}

				int selectedRow = stepsTable.getSelectedRow();

				if (duringRecord)
				{
					if (selectedRow != -1)
					{
						selectedRow = selectedRow+1;
						model.insertRow(selectedRow, new Object[]{nodePath, newType, value, coordinates, 0, newSkip, identifier, clazz});
					}
					else
					{
						model.addRow(new Object[]{nodePath, newType, value, coordinates, 0, newSkip, identifier, clazz});
					}
				}
				else
				{
					if (selectedRow != -1)
					{
						selectedRow = selectedRow+1;
						model.insertRow(selectedRow, new Object[]{nodePath, newType, value, coordinates, delay, newSkip, identifier, clazz});
					}					
					else
					{
						model.addRow(new Object[]{nodePath, newType, value, coordinates, delay, newSkip, identifier, clazz});
					}
				}

				if (duringRecord)
				{
					if (selectedRow == model.getRowCount() - 1)
						stepsTable.changeSelection(model.getRowCount() - 1, model.getRowCount() - 1, false, false);
					else
						stepsTable.changeSelection(selectedRow, selectedRow, false, false);
				}
			}
		});

	}

	public boolean currentlyListeningToEvents()
	{
		return monitorInputs;
	}

	@Override
	public void monitoredWindowRestored(WindowEvent e) {
		handleWindowStateEvents(e);

	}

	@Override
	public void monitoredWindowMaximized(WindowEvent e) {
		handleWindowStateEvents(e);

	}

	@Override
	public void monitoredWindowMinimized(WindowEvent e) {
		handleWindowStateEvents(e);

	}

	private void handleWindowStateEvents(WindowEvent e){

		if (! monitorInputs)
		{
			return;
		}

		Window window = e.getWindow();

		if (Inspector.getInstance().isInRestrictedList(window.hashCode()))
		{
			return;			
		}

		String coordinates = Constants.EMPTY_STRING;
		boolean skip = false;
		String identifier = getSpecialIdentifier(e.getSource());
		String clazz = e.getSource().getClass().getName();
		ConcurrentHashMap<Integer, WeakReference<ComponentTreeNode>> allComponentTreeNodes = Inspector.getInstance().getAllComponentTreeNodes();
		WeakReference<ComponentTreeNode> weakComponentTreeNode = allComponentTreeNodes.get(window.hashCode());
		ComponentTreeNode componentTreeNode = weakComponentTreeNode.get();

		updateMockingTable(componentTreeNode.getFullPath(), String.valueOf(e.getID()), String.valueOf(e.getNewState()), coordinates, 
				1000, skip, identifier, clazz);
	}

	public TrayIcon getTrayIcon() {
		return trayIcon;
	}

	public RobotRecordPropertiesForm getRobotRecordPropertiesForm() {
		return robotRecordPropertiesForm;
	}

	public RobotRecord getLoadedRobotRecord() {
		return loadedRobotRecord;
	}

	public void setLoadedRobotRecord(RobotRecord loadedRobotRecord) {
		this.loadedRobotRecord = loadedRobotRecord;
	}

	public boolean isPlaying() {
		return playing;
	}

	public void setPlaying(boolean playing) {
		this.playing = playing;
	}
	
}
