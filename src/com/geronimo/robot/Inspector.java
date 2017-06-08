/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.geronimo.robot;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.MouseInfo;
import java.awt.Toolkit;
import java.awt.TrayIcon.MessageType;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.List;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/**
 *
 * @author GUYMES
 */
public class Inspector extends javax.swing.JFrame
implements MonitoredWindowListener,TreeSelectionListener
{

	/**
	 * 
	 */
	private static final long serialVersionUID = 9115828458408165896L;

	Timer timer;
	private boolean useRefreshButton;
	private int windowsSyncIntervalms;
	private int windowsSyncInitialDelayms;
	private boolean executeSyncOnNextTimer;
	private boolean syncPausedByKey;
	private boolean stopSync;
	private boolean currentlySyncing;

	private List<MonitoredEventsListener> monitoredEventsListener;

	private static Inspector inspector;

	private DefaultTreeModel treeModel;
	private ComponentTreeNode root;

	private boolean listenToInputEvents;

	private long lastInputClickTimestamp;

	private boolean lastMouseEventWasPRESEED;
	private boolean lastMouseEventWasRELEASED;
	private boolean lastMouseEventWasCLICKED;

	private boolean inspectHiddenWindows;

	private boolean logInputEvents;

	private ConcurrentHashMap<Integer, WeakReference<ComponentTreeNode>> allComponentTreeNodes;
	private ConcurrentHashMap<String, WeakReference<ComponentTreeNode>> allComponentByNameTreeNodes;

	private Queue<AWTEvent> inputEventsQueue;
	//private Queue<AWTEvent> mouseEventsQueue;
	//private Queue<AWTEvent> keyabordEventsQueue;

	boolean highlightSelectedNode;
	
	Boolean useComponentNameMapping;
	
	/**
	 * Creates new form NewJFrame
	 * @throws InterruptedException 
	 * @throws InvocationTargetException 
	 */
	public Inspector()  {

		inspector = this;		

		initComponents();
		setInspectionProperties();

		Container contentPane = this.getContentPane();
		contentPane.setLayout(new GridLayout());

		this.setVisible(true);

		SwingUtils.invoke(new Runnable() {
			public void run() {
				new RobotController();
			}
		});		

		setInspectionTreeProperties();				

		MonitoredWindows.getInstance().registerForWindowsNotifictions(this);	

		attachToInputs();						

		this.setTitle("Inspector");				
	}

	private void setInspectionProperties()
	{
		timer = new Timer();
		
		inspectHiddenWindows = Boolean.parseBoolean(Utils.getInstance().getPropValue("inspectHiddenWindows"));
		InspectHiddenWindowsCB.setSelected(inspectHiddenWindows);
		
		listenToInputEvents = false;
		monitoredEventsListener = new ArrayList<MonitoredEventsListener>();

		String showButton = Utils.getInstance().getPropValue("useRefreshButton");
		if (Boolean.parseBoolean(showButton))
			useRefreshButton = true;
		else
			useRefreshButton = false;
		
		
		windowsSyncIntervalms = 500;
		windowsSyncInitialDelayms = 0;
		executeSyncOnNextTimer = true;
		syncPausedByKey = false;
		stopSync = false;
		currentlySyncing = false;

		logInputEvents = false;
		enableInputLoggingMenuItem.setSelected(logInputEvents);

		lastMouseEventWasRELEASED = true;

		allComponentTreeNodes = new ConcurrentHashMap<>();

		useComponentNameMapping = Boolean.parseBoolean(Utils.getInstance().getPropValue("useComponentNameMapping"));
		if (useComponentNameMapping)
			allComponentByNameTreeNodes = new ConcurrentHashMap<>();	
		
		//mouseEventsQueue = new ConcurrentLinkedQueue<AWTEvent>();
		//keyabordEventsQueue = new ConcurrentLinkedQueue<AWTEvent>();
		inputEventsQueue  = new ConcurrentLinkedQueue<AWTEvent>();
	}

	private void setInspectionTreeProperties()
	{
		root = new ComponentTreeNode(Constants.WINDOWS);
		this.treeModel = new DefaultTreeModel(root);
		treeModel.addTreeModelListener(new InspectionTreeModelListener());

		SwingUtils.invoke(new Runnable()
		{
			@Override
			public void run()
			{
				getInspectionTree().setModel(treeModel);
				getInspectionTree().getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
				getInspectionTree().addTreeSelectionListener(Inspector.getInstance());
			}
		});		
		
		highlightSelectedNode = true;
	}

	public static Inspector getInstance()
	{
		return inspector;
	}

	public void attachToInputs()
	{
		handleMouseEvents();
		handleKeyboardEvents();
	}

	public void resetLastInputClickTimestamp()
	{
		lastInputClickTimestamp = 0;
	}

	private void handleMouseEvents()
	{
		Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener()
		{
			@Override
			public void eventDispatched(AWTEvent event) 
			{
				MouseEvent mouseEvent = (MouseEvent)event;

				if (mouseEvent.getID() != MouseEvent.MOUSE_ENTERED 
						&& mouseEvent.getID() != MouseEvent.MOUSE_EXITED)					
				{					
					//mouseEventsQueue.add(event);
					inputEventsQueue.add(event);
				}										
			}        	
		},AWTEvent.MOUSE_EVENT_MASK);		
	}

	private void runFromInputQueue()
	{		
		while (inputEventsQueue.peek() !=null)
		{
			AWTEvent event = inputEventsQueue.peek();
			if (event instanceof MouseEvent)
				runFromMouseQueue();
			else
				runFromKeyaboardQueue();
		}		
	}
	
	private void runFromMouseQueue()
	{
		AWTEvent event = inputEventsQueue.poll();
		if (event == null)
		{
			return;
		}

		//while(!(event==null))
		//{
			MouseEvent mouseEvent = (MouseEvent) event; 

			if (listenToInputEvents)
			{								

				if (isInRestrictedList(mouseEvent.getSource().hashCode()))
				{
					// maybe it is not a Component class
					return;			
				}

				Component component = (Component) mouseEvent.getSource();
				Component componentTopParent = SwingUtilities.getWindowAncestor(component);
				if (componentTopParent == null)
					componentTopParent = component;

				if (isInRestrictedList(componentTopParent.hashCode()))
				{
					component = null;
					componentTopParent = null;
					System.gc();
					// maybe window was not loaded yet						
					return;			
				}

				if (logInputEvents)
				{
					RobotFactory.writeInfo("From Inspector: " + event.toString());
				}
                                
                                RobotFactory.writeInfo("getPointer  x: " + MouseInfo.getPointerInfo().getLocation().x + "  y : " + MouseInfo.getPointerInfo().getLocation().y);
                                                                                                
				if (mouseEvent.getID() == MouseEvent.MOUSE_RELEASED && !lastMouseEventWasPRESEED)
				{			
					//System.out.println("@!@!@!@!@ mouse release and previous was not pressed @!@@!@");
					RobotFactory.writeInfo("@!@!@!@!@ mouse release and previous was not pressed @!@@!@");
				}
				if (mouseEvent.getID() == MouseEvent.MOUSE_CLICKED && !lastMouseEventWasRELEASED)
				{			
					//System.out.println("@!@!@!@!@ mouse clicked and previous was not released @!@@!@");
					RobotFactory.writeInfo("@!@!@!@!@ mouse clicked and previous was not released @!@@!@");
				}

				if (lastInputClickTimestamp == 0)
				{
					lastInputClickTimestamp = Calendar.getInstance().getTimeInMillis();									
				}

				if (mouseEvent.getID() == MouseEvent.MOUSE_PRESSED)
				{
					lastMouseEventWasPRESEED = true;
				}
				else
				{
					lastMouseEventWasPRESEED = false;
				}
				if (mouseEvent.getID() == MouseEvent.MOUSE_RELEASED)
				{
					lastMouseEventWasRELEASED = true;
				}
				else
				{
					lastMouseEventWasRELEASED = false;
				}
				if (mouseEvent.getID() == MouseEvent.MOUSE_CLICKED)
				{
					lastMouseEventWasCLICKED = true;
				}
				else
				{
					lastMouseEventWasCLICKED = false;
				}

				WeakReference<ComponentTreeNode> weakComponentTreeNode = allComponentTreeNodes.get(component.hashCode());
				if (weakComponentTreeNode != null && weakComponentTreeNode.get() != null)
				{
					final TreePath treePath = new TreePath(weakComponentTreeNode.get().getPath());
					SwingUtils.invoke(new Runnable() {

						@Override
						public void run() {																		
							if (highlightSelectedNode == true)
							{
								getInspectionTree().setSelectionPath(treePath);
							}
						}
					});

					monitoredMouseEvent(treePath, event, (int) (Calendar.getInstance().getTimeInMillis() - lastInputClickTimestamp));							
					lastInputClickTimestamp = Calendar.getInstance().getTimeInMillis();
				}	
				else
				{
					RobotController.getInstance().displayTrayIconMessage("Say what?!", "Can you repeat that?", MessageType.WARNING);
				}																		

				component = null;
				componentTopParent = null;
				System.gc();
			}
			else
			{
				RobotFactory.writeInfo("NOT listening to events. Event: " + event.toString());
			}
			//event = inputEventsQueue.poll();
		//}


	}

	public boolean isInRestrictedList(int hashCode)
	{
		if (this.hashCode() == hashCode || RobotController.getInstance().hashCode() == hashCode || RobotController.getInstance().getTrayIcon().hashCode() == hashCode
				|| RobotController.getInstance().getRobotRecordPropertiesForm().hashCode() == hashCode)
			return true;

		return false;
	}

	public ComponentTreeNode getComponentTreeNodeByNodeDescription(ComponentTreeNode parentComponentTreeNode, String description, boolean aggresivly)
	{
		if (parentComponentTreeNode == null)
		{
			RobotFactory.writeError("Failed to find: " + description);
			return null;
		}

		Enumeration e =  parentComponentTreeNode.children();
		ComponentTreeNode node = null;
		e =  parentComponentTreeNode.children();
		while(e.hasMoreElements())
		{	
			node = (ComponentTreeNode) e.nextElement();
			if (node.getNodeDescription().equals(description))
			{
				return node;
			}
		}

		node = null;

		if (aggresivly && (node == null))
		{
			e =  parentComponentTreeNode.children();
			while(e.hasMoreElements())
			{	
				node = (ComponentTreeNode) e.nextElement();
				if (description.indexOf("[") > -1)
				{
					String newDescription = description.substring(0, description.indexOf("["));					
					if (node.getComponentName().equals(newDescription))
					{
						return node;
					}
				}				
			}
		}

		return null;
	}	
			
	private void runFromKeyaboardQueue()
	{
		AWTEvent event = inputEventsQueue.poll();
		if (event == null)
		{
			return;
		}

		//while(!(event==null))
		//{
			KeyEvent keyEvent =(KeyEvent) event; 

			if (listenToInputEvents)
			{						
				Component component = (Component) keyEvent.getSource();
				Component componentTopParent = SwingUtilities.getWindowAncestor(component);
				if (componentTopParent == null)
					componentTopParent = component;

				if (isInRestrictedList(componentTopParent.hashCode()))
				{			
					component = null;
					componentTopParent = null;
					System.gc();
					stopSync = false;
					return;			
				}

				if(logInputEvents)
				{
					RobotFactory.writeInfo("From Inspector: " + event.toString());
				}

				if (lastInputClickTimestamp == 0)
				{
					lastInputClickTimestamp = Calendar.getInstance().getTimeInMillis();
				}

				// Only KEY_PRESSED is valid because it records the actual keycode
				if (event.getSource() instanceof JDialog)
				{
					if (((JDialog)event.getSource()).getName().equals("ErrorDialog"))
					{
						component = null;
						componentTopParent = null;
						
						listenToInputEvents = false;
						stopSync = true;
						System.gc();
						return;
					}
				}

				WeakReference<ComponentTreeNode> weakComponentTreeNode = allComponentTreeNodes.get(component.hashCode());
				if (weakComponentTreeNode != null && weakComponentTreeNode.get() != null) // could be null incase window is WindowsFileChooserUI$7
				{
					final ComponentTreeNode componentTreeNode = weakComponentTreeNode.get();
					
						final TreePath treePath = new TreePath(componentTreeNode.getPath());
						SwingUtils.invoke(new Runnable() {

							@Override
							public void run() {																		
								if (highlightSelectedNode == true)
								{
									getInspectionTree().setSelectionPath(treePath);
								}
							}
						});

						monitoreKeyboardEvent(treePath, event, (int) (Calendar.getInstance().getTimeInMillis() - lastInputClickTimestamp));						
						lastInputClickTimestamp = Calendar.getInstance().getTimeInMillis();
										
				}
				else
				{
					RobotController.getInstance().displayTrayIconMessage("Say what?!", "Can you repeat that?", MessageType.WARNING);
				}
				
				component = null;
				componentTopParent = null;
				System.gc();
			}
			//event = keyabordEventsQueue.poll();
		//}
	}

	private void handleKeyboardEvents()
	{
		Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener()
		{
			public void eventDispatched(AWTEvent event)
			{
				KeyEvent keyEvent = (KeyEvent) event;

				// CTRL+A code is here so it will execute on any window opened - including inspector
				if (keyEvent.isControlDown() && keyEvent.getKeyCode() == KeyEvent.VK_A)
				{
					if (executeSyncOnNextTimer == true)
					{
						//currentOperationLabel.setText("Timer refresh are OFF!");
						executeSyncOnNextTimer = false;
						syncPausedByKey = true;
					}						
					else
					{
						if (! useRefreshButton && ! executeSyncOnNextTimer)
						{
							//currentOperationLabel.setText("Timer refresh are ON.");
							executeSyncOnNextTimer = true;
							syncPausedByKey = false;
						}
					}
					setupTimer();
					return;
				}

				if (event.getID() != KeyEvent.KEY_TYPED 
						&& event.getID() != KeyEvent.KEY_RELEASED)
				{
					inputEventsQueue.add(event);						
				}
			}
		}
		,KeyEvent.KEY_EVENT_MASK);               
	}

	public void setupTimer()
	{
		if (useRefreshButton)
		{
			//Refresh.setVisible(true);
			SyncWindows.setVisible(true);
		}
		else
		{
			SyncWindows.setVisible(false);
			//Refresh.setVisible(false);
			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					if (executeSyncOnNextTimer)
					{
						sync(false);						
					}
				}
			}, windowsSyncInitialDelayms, windowsSyncIntervalms);
		}

	}

	public void sync(boolean forceSync)
	{		
		if (!currentlySyncing || forceSync)
			if ((! stopSync && ! syncPausedByKey) || forceSync)
			{
				currentlySyncing = true;
				MonitoredWindows.getInstance().syncWindows();
				currentlySyncing = false;
			}

	}

	public void valueChanged(TreeSelectionEvent e) {

		if(!(getInspectionTree().getLastSelectedPathComponent() instanceof ComponentTreeNode))
		{
			return;
		}

		ComponentTreeNode selectednode;
		//Returns the last path element of the selection.
		//This method is useful only when the selection model allows a single selection.
		selectednode = (ComponentTreeNode) getInspectionTree().getLastSelectedPathComponent();

		if (selectednode == null)
		{
			//Nothing is selected.     
			return;
		}
		
		Object nodeInfo = selectednode.getUserObject();  
		String nodeText = nodeInfo.toString();
		if (nodeText.equals(Constants.WINDOWS))
			return;

		RobotFactory.writeInfo(selectednode.getNodeDetails());

		circleComponent(selectednode);    		
	}       

	private void listProperties(Object obj)
	{
		DefaultTableModel model = (DefaultTableModel) componentPropertiesTable.getModel();

		while (model.getRowCount() > 0)
		{
			model.removeRow(0);
		}

		Field[] fields = obj.getClass().getDeclaredFields();
		for (Field field : fields) 
		{
			String fieldValue;
			field.setAccessible(true);
			try {
				fieldValue = String.valueOf(field.get(obj));
			} catch (IllegalArgumentException e) {
				fieldValue = "unable to obtain value!";
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				fieldValue = "unable to obtain value!";
				e.printStackTrace();
			}
			model.addRow(new Object[]{Modifier.toString(field.getModifiers()), field.getType().getSimpleName(), field.getName(), fieldValue});			
		}
	}
	
	private void circleComponent(ComponentTreeNode componentTreeNode)
	{
		Component component = componentTreeNode.getWeakReferenceToComponent().get();

		if (component instanceof JComponent)
		{
			circleJComponent((JComponent) component);							  	
			listProperties(component);				
		}    		
		component = null;
		System.gc();
	}

	private void circleJComponent(final JComponent component)
	{
		SwingWorker worker = new SwingWorker<Void, Void>() {
			@Override
			public Void doInBackground() {
				component.setBorder(BorderFactory.createMatteBorder(2, 2, 2, 2, Color.RED));
				try {
					Thread.sleep(200);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
				component.setBorder(null);     
				try {
					Thread.sleep(200);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
				component.setBorder(BorderFactory.createMatteBorder(2, 2, 2, 2, Color.RED));
				try {
					Thread.sleep(200);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
				component.setBorder(null);
				return null;
			}

			@Override
			public void done() {
			}
		};
		worker.execute();
	}

	/**
	 * This method is called from within the constructor to initialize the form.
	 * WARNING: Do NOT modify this code. The content of this method is always
	 * regenerated by the Form Editor.
	 */
	@SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel2 = new javax.swing.JPanel();
        jSplitPane1 = new javax.swing.JSplitPane();
        jScrollPane2 = new javax.swing.JScrollPane();
        inspectionTree = new javax.swing.JTree();
        jScrollPane1 = new javax.swing.JScrollPane();
        componentPropertiesTable = new javax.swing.JTable();
        mainMenuBar = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        exitMenuItem = new javax.swing.JMenuItem();
        optionsMenu = new javax.swing.JMenu();
        enableInputLoggingMenuItem = new javax.swing.JCheckBoxMenuItem();
        highlightSelected = new javax.swing.JCheckBoxMenuItem();
        InspectHiddenWindowsCB = new javax.swing.JCheckBoxMenuItem();
        SyncWindows = new javax.swing.JMenu();
        syncWindowsOp = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setFocusCycleRoot(false);
        setName("swingInspector"); // NOI18N

        jSplitPane1.setDividerLocation(200);

        javax.swing.tree.DefaultMutableTreeNode treeNode1 = new javax.swing.tree.DefaultMutableTreeNode("root");
        inspectionTree.setModel(new javax.swing.tree.DefaultTreeModel(treeNode1));
        jScrollPane2.setViewportView(inspectionTree);

        jSplitPane1.setLeftComponent(jScrollPane2);

        componentPropertiesTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Modifier", "Type", "Name", "Value"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, false, false, false
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jScrollPane1.setViewportView(componentPropertiesTable);

        jSplitPane1.setRightComponent(jScrollPane1);

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(jSplitPane1)
                .addGap(0, 0, 0))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(jSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 593, Short.MAX_VALUE)
                .addContainerGap())
        );

        fileMenu.setText("File");
        fileMenu.addMenuKeyListener(new javax.swing.event.MenuKeyListener() {
            public void menuKeyPressed(javax.swing.event.MenuKeyEvent evt) {
                //fileMenuMenuKeyPressed(evt);
            }
            public void menuKeyReleased(javax.swing.event.MenuKeyEvent evt) {
            }
            public void menuKeyTyped(javax.swing.event.MenuKeyEvent evt) {
            }
        });

        exitMenuItem.setText("Exit");
        exitMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(exitMenuItem);

        mainMenuBar.add(fileMenu);

        optionsMenu.setLabel("Options");

        enableInputLoggingMenuItem.setSelected(true);
        enableInputLoggingMenuItem.setText("Enable inspector input logging");
        enableInputLoggingMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enableInputLoggingMenuItemActionPerformed(evt);
            }
        });
        optionsMenu.add(enableInputLoggingMenuItem);

        highlightSelected.setSelected(true);
        highlightSelected.setText("Highlight selected node");
        highlightSelected.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                highlightSelectedActionPerformed(evt);
            }
        });
        optionsMenu.add(highlightSelected);

        InspectHiddenWindowsCB.setText("Inspect hidden windows");
        InspectHiddenWindowsCB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                InspectHiddenWindowsCBActionPerformed(evt);
            }
        });
        optionsMenu.add(InspectHiddenWindowsCB);

        mainMenuBar.add(optionsMenu);

        SyncWindows.setText("Refresh");

        syncWindowsOp.setText("Refresh Tree");
        syncWindowsOp.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                syncWindowsOpActionPerformed(evt);
            }
        });
        SyncWindows.add(syncWindowsOp);

        mainMenuBar.add(SyncWindows);

        setJMenuBar(mainMenuBar);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

	private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitMenuItemActionPerformed
		System.exit(0);
	}//GEN-LAST:event_exitMenuItemActionPerformed

	private void enableInputLoggingMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enableInputLoggingMenuItemActionPerformed
		logInputEvents = enableInputLoggingMenuItem.isSelected();
	}//GEN-LAST:event_enableInputLoggingMenuItemActionPerformed

    private void highlightSelectedActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_highlightSelectedActionPerformed
    	highlightSelectedNode = highlightSelected.isSelected();
    }//GEN-LAST:event_highlightSelectedActionPerformed

    private void InspectHiddenWindowsCBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_InspectHiddenWindowsCBActionPerformed
    	inspectHiddenWindows = InspectHiddenWindowsCB.isSelected();
    }//GEN-LAST:event_InspectHiddenWindowsCBActionPerformed

    private void syncWindowsOpActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_syncWindowsOpActionPerformed
       sync(false);
    }//GEN-LAST:event_syncWindowsOpActionPerformed

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
			java.util.logging.Logger.getLogger(Inspector.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
		} catch (InstantiationException ex) {
			java.util.logging.Logger.getLogger(Inspector.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
		} catch (IllegalAccessException ex) {
			java.util.logging.Logger.getLogger(Inspector.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
		} catch (javax.swing.UnsupportedLookAndFeelException ex) {
			java.util.logging.Logger.getLogger(Inspector.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
		}
		//</editor-fold>

		/* Create and display the form */
		java.awt.EventQueue.invokeLater(new Runnable() {
			public void run() {
				new Inspector().setVisible(true);
			}
		});

	}	

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBoxMenuItem InspectHiddenWindowsCB;
    private javax.swing.JMenu SyncWindows;
    private javax.swing.JTable componentPropertiesTable;
    private javax.swing.JCheckBoxMenuItem enableInputLoggingMenuItem;
    private javax.swing.JMenuItem exitMenuItem;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JCheckBoxMenuItem highlightSelected;
    private javax.swing.JTree inspectionTree;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JSplitPane jSplitPane1;
    private javax.swing.JMenuBar mainMenuBar;
    private javax.swing.JMenu optionsMenu;
    private javax.swing.JMenuItem syncWindowsOp;
    // End of variables declaration//GEN-END:variables

	@Override
	public void monitoredWindowAdded(final String hash) 
	{
		if (isInRestrictedList(Integer.valueOf(hash)))
			return;

		StringBuffer windowIndex = new StringBuffer();
		// We don't use windowIndex - leave it here for now....
		Window window = MonitoredWindows.getInstance().getNativeWindow(hash, windowIndex);			

		updateCurrentOperationsLabel("New window opened. Hash: " + hash);

		final ComponentTreeNode addedWindowTreeNode = new ComponentTreeNode(window, Integer.parseInt(windowIndex.toString()), true);
		allComponentTreeNodes.put(window.hashCode(), new WeakReference<ComponentTreeNode>(addedWindowTreeNode));
			
		SwingUtils.invoke(new Runnable() {

			@Override
			public void run() {
				treeModel.insertNodeInto(addedWindowTreeNode, root, root.getChildCount());		
				TreePath treePath = new TreePath(root);
				if (!inspectionTree.isExpanded(treePath))
				{
					inspectionTree.expandPath(treePath);
				}	
			}
		});			

		addUpdateRemoveComponentTreeNodes(addedWindowTreeNode, window.getComponents());
		updateCurrentOperationsLabel("New window node added.");				
	}

	@Override
	public void monitoredWindowUpdate(final String hash) 
	{
		if (isInRestrictedList(Integer.valueOf(hash)))
			return;

		// TODO here the check should be different , not be hashCode but with index only
		StringBuffer windowIndex = new StringBuffer();
		// We don't use windowIndex - leave it here for now....
		Window window = MonitoredWindows.getInstance().getNativeWindow(hash, windowIndex);

		updateCurrentOperationsLabel("Updating window. Hash: " + hash);

		WeakReference<ComponentTreeNode> weakComponentTreeNode = allComponentTreeNodes.get(window.hashCode());
		final ComponentTreeNode componentTreeNode = weakComponentTreeNode.get();
		if (componentTreeNode == null)
		{
			// could be in cases such parent is : javax.swing.SwingUtilities$SharedOwnerFrame
			// not suppose to get to here in case that 'doNotInspectHiddenWindows' if true
			return;
		}
		componentTreeNode.updateWindowName();
		updateCurrentOperationsLabel("Updating window. description: " + componentTreeNode.getUserObject());
		
		addUpdateRemoveComponentTreeNodes(componentTreeNode, window.getComponents());
		updateCurrentOperationsLabel("Updating window is done. Hash: " + hash);				
	}

	private void updateCurrentOperationsLabel(final String line)
	{
//		EventQueue.invokeLater(new Runnable() {
//
//			@Override
//			public void run() {
//				currentOperationLabel.setText(line);
//			}
//		});	
	}

	private void addUpdateRemoveComponentTreeNodes(ComponentTreeNode parentNode, Component[] components)
	{
		List thisNodeChilds = new ArrayList();
		updateCurrentOperationsLabel("Updating " + components.length + " components.");

		int numOfChildrens = parentNode.getNumOfChildrens();
		int componentIndex = numOfChildrens - 1;
		for (Component component : components)
		{						
			ComponentTreeNode node;
			if (allComponentTreeNodes.get(component.hashCode()) == null || allComponentTreeNodes.get(component.hashCode()).get() == null)
			{
				componentIndex = componentIndex + 1; 
				node = addComponentTreeNode(parentNode, component, componentIndex);
				updateCurrentOperationsLabel("Node created. description: " + node.getUserObject());
			}
			else
			{
				node = allComponentTreeNodes.get(component.hashCode()).get();
			}

			thisNodeChilds.add(node);

			if (component instanceof Container)  {
				Container subContainer = (Container)component;
				addUpdateRemoveComponentTreeNodes(node, subContainer.getComponents());
			}else{
				//RobotFactory.writeToLog("not updating sub component of : " + component.getClass().getName().toString());
			}	

		}    			

		removeComponentTreeNode(parentNode, thisNodeChilds);								
	}

	private void removeComponentTreeNode(final ComponentTreeNode parentNode, List thisNodeChilds)
	{
		updateCurrentOperationsLabel("Searching for old nodes to remove...");

		Enumeration e = parentNode.children();
		while(e.hasMoreElements())
		{							
			boolean found = false;
			final ComponentTreeNode node = (ComponentTreeNode) e.nextElement();
			for (Object nodeChild : thisNodeChilds)
			{
				ComponentTreeNode componentTreeNode = (ComponentTreeNode) nodeChild;
				if (node == componentTreeNode)
				{
					found = true;
					break;
				}				
			}		
			if (! found)
			{
				updateCurrentOperationsLabel("Removing node. Description: " + node.getUserObject());

				SwingUtils.invoke(new Runnable() {

					@Override
					public void run() {
						if (node.getParent() != null)
						{
							treeModel.removeNodeFromParent(node);
							parentNode.setNumOfChildrens(parentNode.getNumOfChildrens() - 1);
						}

					}
				});				
			}
		}			
	}

	private ComponentTreeNode addComponentTreeNode(final ComponentTreeNode parentNode, Component component, int componentIndex)
	{
		final ComponentTreeNode addedComponentTreeNode = new ComponentTreeNode(component, componentIndex, false);
		allComponentTreeNodes.put(component.hashCode(), new WeakReference<ComponentTreeNode>(addedComponentTreeNode));
		
		if (useComponentNameMapping)
		{
			if (getAllComponentByNameTreeNodes().get(addedComponentTreeNode.getComponentName()) == null)
			{
				getAllComponentByNameTreeNodes().put(addedComponentTreeNode.getComponentName(), new WeakReference<ComponentTreeNode>(addedComponentTreeNode));
			}
			else
			{
				RobotFactory.writeInfo("Duplicate component found: " + addedComponentTreeNode.getComponentName());
			}
		}
			
		SwingUtils.invoke(new Runnable() {

			@Override
			public void run() {
				treeModel.insertNodeInto(addedComponentTreeNode, parentNode, parentNode.getChildCount());
				parentNode.setNumOfChildrens(parentNode.getNumOfChildrens() + 1);
			}
		});

		return addedComponentTreeNode;
	}

	@Override
	public void monitoredWindowRemoved(String hash)
	{        		
		if (isInRestrictedList(Integer.valueOf(hash)))
			return;

		updateCurrentOperationsLabel("Window closed. hash: " + hash);

		WeakReference<ComponentTreeNode> weakComponentTreeNode = allComponentTreeNodes.get(Integer.valueOf(hash));
		final ComponentTreeNode node = weakComponentTreeNode.get();

		updateCurrentOperationsLabel("Removing node with description: " + node.getUserObject());

		SwingUtils.invoke(new Runnable() {

			@Override
			public void run() {
				treeModel.removeNodeFromParent(node);				
			}
		});

		updateCurrentOperationsLabel("Node removed. hash : " + hash);		    
	}

	@Override
	public void monitoredWindowCycleStarted() 
	{
		listenToInputEvents = false;
		executeSyncOnNextTimer = false;
	}

	@Override
	public void monitoredWindowCycleFinished() 
	{				
		if (!RobotController.getInstance().isPlaying()) listenToInputEvents = true;
		
		runFromInputQueue();
		//runFromMouseQueue();
		//runFromKeyaboardQueue();
		
		executeSyncOnNextTimer = true;	
	}       

	public void setInspectionTree(javax.swing.JTree inspectionTree) {
		this.inspectionTree = inspectionTree;
	}

	public javax.swing.JTree getInspectionTree() {
		return inspectionTree;
	}

	public void registerForMonitoredEventsNotifictions(MonitoredEventsListener listener)
	{
		monitoredEventsListener.add(listener);
	}

	public void monitoredMouseEvent(TreePath treePath, AWTEvent event, int delay)
	{    	
		for (MonitoredEventsListener listener : monitoredEventsListener)
		{
			listener.monitoredMouse(treePath, event, delay);
		}
	}

	public void monitoreKeyboardEvent(TreePath treePath, AWTEvent event, int delay)
	{    	
		for (MonitoredEventsListener listener : monitoredEventsListener)
		{
			listener.monitoredKeyboard(treePath, event, delay);
		}
	}


	class InspectionTreeModelListener implements TreeModelListener 
	{		
		@Override
		public void treeNodesChanged(TreeModelEvent e) {
			DefaultMutableTreeNode node;
			node = (DefaultMutableTreeNode)(e.getTreePath().getLastPathComponent());

			/*
			 * If the event lists children, then the changed
			 * node is the child of the node we've already
			 * gotten.  Otherwise, the changed node and the
			 * specified node are the same.
			 */

			int index = e.getChildIndices()[0];
			node = (DefaultMutableTreeNode)(node.getChildAt(index));

			//System.out.println("The user has finished editing the node.");
			//System.out.println("New value: " + node.getUserObject());
		}

		@Override
		public void treeNodesInserted(TreeModelEvent e) 
		{
			for (Object child : e.getChildren())
			{
				final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) child;
				final DefaultMutableTreeNode treeParentNode = (DefaultMutableTreeNode) e.getPath()[e.getPath().length-1];

				SwingUtils.invoke(new Runnable() {

					@Override
					public void run() {
						//model.addElement("Child: " + treeNode.getUserObject() + " , Parent: " + treeParentNode.getUserObject() + " added");
						RobotFactory.writeInfo("Child: " + treeNode.getUserObject() + " , Parent: " + treeParentNode.getUserObject() + " added");
					}
				});

			}
		}

		@Override
		public void treeNodesRemoved(TreeModelEvent e) 
		{
			for (Object child : e.getChildren())
			{
				final DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) child;
				final DefaultMutableTreeNode treeParentNode = (DefaultMutableTreeNode) e.getPath()[e.getPath().length-1];

				SwingUtils.invoke(new Runnable() {

					@Override
					public void run() {
						//model.addElement("Child: " + treeNode.getUserObject() + " , Parent: " + treeParentNode.getUserObject() + " removed");
						RobotFactory.writeInfo("Child: " + treeNode.getUserObject() + " , Parent: " + treeParentNode.getUserObject() + " removed");
					}
				});				
			}
		}

		@Override
		public void treeStructureChanged(TreeModelEvent e) 
		{
		}
	}

	public void hardStop()
	{		
		listenToInputEvents = false;
		stopSync = true;
	}

	public void hardStart()
	{
		listenToInputEvents = true;
		stopSync = false;
	}

	@Override
	public void monitoredWindowRestored(WindowEvent e) {		
		for (MonitoredEventsListener listener : monitoredEventsListener)
		{
			listener.monitoredWindowRestored(e);
		}		
	}

	@Override
	public void monitoredWindowMinimized(WindowEvent e) {	
		for (MonitoredEventsListener listener : monitoredEventsListener)
		{
			listener.monitoredWindowMinimized(e);
		}
	}

	@Override
	public void monitoredWindowMaximized(WindowEvent e) {		
		for (MonitoredEventsListener listener : monitoredEventsListener)
		{
			listener.monitoredWindowMaximized(e);
		}
	}

	public ComponentTreeNode getRoot() {
		return root;
	}

	public boolean isListenToInputEvents() {
		return listenToInputEvents;
	}

	public void setListenToInputEvents(boolean listenToInputEvents) {
		this.listenToInputEvents = listenToInputEvents;

		if (! isListenToInputEvents())
			SwingUtils.invoke(new Runnable()
			{
				@Override
				public void run()
				{
					updateCurrentOperationsLabel("Not listening to input events. e.g.: Keyboard, Mouse...");
					highlightSelected.setSelected(false);
					highlightSelected.setEnabled(false);
				}
			});
		else
			SwingUtils.invoke(new Runnable()
			{
				@Override
				public void run()
				{
					updateCurrentOperationsLabel("listening to input events. e.g.: Keyboard, Mouse...");
					highlightSelected.setEnabled(true);
				}
			});
	}

	public boolean isInspectHiddenWindows() {
		return inspectHiddenWindows;
	}

	public TreeNode[] findPathToNode (ComponentTreeNode root, String[] path, int pathIndex)
	{
		Enumeration e = root.children();
		while(e.hasMoreElements())
		{	
			ComponentTreeNode node = (ComponentTreeNode) e.nextElement();
			if (node.getNodeDescription().equals(path[pathIndex]))
			{
				if (pathIndex == path.length - 1)
				{
					return node.getPath();
				}
				return findPathToNode (node, path, pathIndex + 1);
			}
		}
		return root.getPath();
	}

	public boolean isLastMouseEventWasPRESEED() {
		return lastMouseEventWasPRESEED;
	}

	public boolean isLastMouseEventWasRELEASED() {
		return lastMouseEventWasRELEASED;
	}

	public ConcurrentHashMap<Integer, WeakReference<ComponentTreeNode>> getAllComponentTreeNodes(){
		return allComponentTreeNodes;
	}

	public ConcurrentHashMap<String, WeakReference<ComponentTreeNode>> getAllComponentByNameTreeNodes() {
		return allComponentByNameTreeNodes;
	}

}
