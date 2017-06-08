package com.geronimo.robot;

import java.awt.Component;
import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Vector;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JWindow;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;

public class ComponentTreeNode extends DefaultMutableTreeNode
{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 4889325456371607730L;

	private int componentHashCode;
	private int componentIndex;
	private String nodeDescription; 
	private WeakReference<Component> weakReferenceToComponent;
	private long detectedTime;
	private String componentName;
	private int numOfChildrens;
	private String fullPath;
	private boolean isWindow;
	
	private boolean removed;
			
	public ComponentTreeNode(Component component, int componentIndex, boolean isWindow) {
		super();
		this.componentIndex = componentIndex;
		this.componentHashCode = component.hashCode();		
		weakReferenceToComponent = new WeakReference<Component>(component);
		detectedTime = Calendar.getInstance().getTimeInMillis();
		this.isWindow = isWindow;
		
		updateWindowName();
	}
	
	public ComponentTreeNode(String description) {
		super(description);
		nodeDescription = description;
	}

	public int getComponentHashCode() {
		return componentHashCode;
	}
	public void setComponentHashCode(int hashCode) {
		this.componentHashCode = hashCode;
	}
	public int getIndex() {
		return componentIndex;
	}
	public void setIndex(int index) {
		this.componentIndex = index;
	}
	public String getNodeDescription() {
		return nodeDescription;
	}
	public void setNodeDescription(String nodeDescription) {
		this.nodeDescription = nodeDescription;
		this.setUserObject(nodeDescription);
	}
	
	private String getNodeDescription(int index)
	{	
		boolean includeIndex = true;
		String title = Constants.EMPTY_STRING;
		
		if (isWindow)
		{
			includeIndex = false;
		}
		
		Component component = getWeakReferenceToComponent().get();
		if (component instanceof JFrame)
		{			
			title = ((JFrame)component).getTitle();
		}
		if (component instanceof JWindow)
		{			
			title = ((JWindow)component).getName();
		}
		if (component instanceof JDialog)
		{			
			title = ((JDialog)component).getTitle();
		}
		
		String simpleClassName = component.getClass().getSimpleName();
		if (simpleClassName.equals(Constants.EMPTY_STRING))
		{
			simpleClassName = component.getClass().getName().toString();
		}
		
		String _componentName;
		
		if (title.equals(Constants.EMPTY_STRING))
			_componentName = component.getName();
		else
			_componentName = title;
		
		componentName = simpleClassName + "(" + _componentName + ")";
		if (includeIndex)
		{
			return simpleClassName + "(" + _componentName + ")[" + index + "]";			
		}
		else
		{
			return simpleClassName + "(" + _componentName + ")";
		}
	}
	
	public String getNodeDetails()
	{
		StringBuffer nodeDetails = new StringBuffer();
		nodeDetails.append("componentHashCode: ");
		nodeDetails.append(componentHashCode);
		nodeDetails.append("\n");
		nodeDetails.append("componentIndex: ");
		nodeDetails.append(componentIndex);
		nodeDetails.append("\n");
		nodeDetails.append("nodeDescription: ");
		nodeDetails.append(nodeDescription);
		nodeDetails.append("\n");
		nodeDetails.append("has Weak Reference: ");
		nodeDetails.append(weakReferenceToComponent.get()!=null);
		nodeDetails.append("\n");
		
		return nodeDetails.toString();
	}

	public long getDetectedTime() {
		return detectedTime;
	}

	public WeakReference<Component> getWeakReferenceToComponent() {
		return weakReferenceToComponent;
	}

	public String getComponentName() {
		return componentName;
	}

	public int getNumOfChildrens() {
		return numOfChildrens;
	}

	public void setNumOfChildrens(int numOfChildrens) {
		this.numOfChildrens = numOfChildrens;
	}

	private String convertTreePathToString(TreeNode[] treeNodes)
	{
		String fullPath = Constants.EMPTY_STRING;
		
		for (TreeNode treeNode : treeNodes)
		{			
			fullPath = fullPath + Constants.COMPONENTS_SEPERATOR + ((ComponentTreeNode)treeNode).getNodeDescription();
		}
					
		return fullPath;		
	}
	
	public String getFullPath()
	{	
		if (fullPath.startsWith(Constants.WINDOWS_FQDN_PLUS_END))
			fullPath = fullPath.replace(Constants.WINDOWS_FQDN_PLUS_END, Constants.COMPONENTS_SEPERATOR);
		
		return fullPath;
	}

	@Override
	public void setParent(MutableTreeNode newParent) 
	{		
		super.setParent(newParent);
		
		if (newParent == null)
		{				
			//removeAllFromTree(this);						
			//System.gc();
		}
		else
			fullPath = convertTreePathToString(this.getPath());
	}
	
	private void removeAllFromTree(ComponentTreeNode componentTreeNodeToRemove)
	{
		if (componentTreeNodeToRemove.removed) return;
		
		if (componentTreeNodeToRemove.numOfChildrens > 0){
		Vector ComponentTreeNodes = componentTreeNodeToRemove.children;
		if (ComponentTreeNodes != null)
		{
			for (Object oComponentTreeNode : ComponentTreeNodes)
			{
				ComponentTreeNode componentTreeNode = (ComponentTreeNode) oComponentTreeNode;				
				removeAllFromTree(componentTreeNode);						
			}
		}}
				
		if (weakReferenceToComponent != null)
			componentTreeNodeToRemove.weakReferenceToComponent.clear();
		
		componentTreeNodeToRemove.weakReferenceToComponent = null;
		Inspector.getInstance().getAllComponentTreeNodes().remove(componentTreeNodeToRemove.componentHashCode);
		
//		if (Boolean.parseBoolean(Utils.getInstance().getPropValue("useComponentNameMapping")))
//		{
//			WeakReference<ComponentTreeNode> weakComponentTreeNode = Inspector.getInstance().getAllComponentByNameTreeNodes().get(this.getComponentName());
//			if (weakComponentTreeNode != null && weakComponentTreeNode.get() != null)
//			{
//				if (weakComponentTreeNode.get() == this)
//					 Inspector.getInstance().getAllComponentByNameTreeNodes().remove(this.getComponentName());
//			}
//		}
		
		System.gc();
		componentTreeNodeToRemove.removed = true;
	}
		
	public void updateWindowName()
	{
		this.nodeDescription = getNodeDescription(this.componentIndex);
		this.setUserObject(nodeDescription);
	}
	
}
