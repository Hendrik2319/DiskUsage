package net.schwarzbaer.java.tools.diskusage;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Vector;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import net.schwarzbaer.gui.StandardMainWindow;

public class DiskUsage implements CushionView.GuiContext {

	public static void main(String[] args) {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		
		File file = new File("hdd.diskusage");
		new DiskUsage().readFile(file).createGUI();
	}
	
	private DiskItem root;
	private DiskItemTreeNode rootTreeNode;
	private JTree treeView;
	private CushionView cushionView;

	private void createGUI() {
		rootTreeNode = new DiskItemTreeNode(root);
		treeView = new JTree(rootTreeNode);
		treeView.addTreeSelectionListener(e -> {
			TreePath treePath = treeView.getSelectionPath();
			if (treePath==null) return;
			Object object = treePath.getLastPathComponent();
			if (object instanceof DiskItemTreeNode) {
				DiskItemTreeNode treeNode = (DiskItemTreeNode) object;
				cushionView.setSelected(treeNode.diskItem);
			}
		});
		
		cushionView = new CushionView(root,1000,800,this);
		cushionView.setBorder(BorderFactory.createTitledBorder("Cushion View"));
		cushionView.setPreferredSize(new Dimension(1000,800));
		
		JScrollPane treeScrollPane = new JScrollPane(treeView);
		treeScrollPane.setBorder(BorderFactory.createTitledBorder("Folder Structure"));
		treeScrollPane.setPreferredSize(new Dimension(500,800));
		
		JPanel contentPane = new JPanel(new BorderLayout(3,3));
		contentPane.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		contentPane.add(treeScrollPane,BorderLayout.WEST);
		contentPane.add(cushionView,BorderLayout.CENTER);
		
		StandardMainWindow mainWindow = new StandardMainWindow("DiskUsage");
		mainWindow.startGUI(contentPane);
	}

	@Override
	public void expandPathInTree(DiskItem diskItems) {
		DiskItemTreeNode node = rootTreeNode.find(diskItems);
		if (node==null) return;
		TreePath treePath = node.getPath();
		//System.out.println(treePath);
		treeView.setSelectionPath(treePath);
		treeView.scrollPathToVisible(treePath);
	}

	private DiskUsage readFile(File file) {
		root = null;
		try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
			root = new DiskItem();
			String line;
			while ( (line=in.readLine())!=null ) {
				int pos = line.indexOf(0x9);
				long size = Long.parseLong(line.substring(0,pos));
				String[] path = line.substring(pos+1).split("/");
				DiskItem item = root.get(path);
				item.size = size;
			}
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return this;
	}

	private static class DiskItemTreeNode implements TreeNode {

		private DiskItemTreeNode parent = null;
		private DiskItem diskItem = null;
		private DiskItemTreeNode[] children = null;

		public DiskItemTreeNode(DiskItem root) { this(null,root); }
		public DiskItemTreeNode(DiskItemTreeNode parent, DiskItem diskItem) {
			this.parent = parent; 
			this.diskItem = diskItem;
		}

		public DiskItemTreeNode find(DiskItem diskItem) {
			if (this.diskItem == diskItem)
				return this;
			if (children==null) createChildren();
			for (DiskItemTreeNode child:children) {
				DiskItemTreeNode hit = child.find(diskItem);
				if (hit!=null) return hit;
			}
			return null;
		}
		public TreePath getPath() {
			Vector<DiskItemTreeNode> path = new Vector<>();
			path.add(this);
			DiskItemTreeNode node = this;
			while (node.parent!=null) {
				path.insertElementAt(node.parent, 0);
				node = node.parent;
			}
			return new TreePath( path.toArray(new DiskItemTreeNode[path.size()]) );
		}
		
		private void createChildren() {
			children = diskItem.children.stream()
					.map(di->new DiskItemTreeNode(this,di))
					.toArray(n->new DiskItemTreeNode[n]);
			Arrays.sort(
					children,
					Comparator.<DiskItemTreeNode,DiskItem>comparing(ditn->ditn.diskItem,Comparator.<DiskItem,Long>comparing(di->di.size,Comparator.reverseOrder()).thenComparing(di->di.name))
			);
		}

		@Override public String toString() { return diskItem.toString(); }

		@SuppressWarnings("rawtypes")
		@Override
		public Enumeration children() {
			if (children==null) createChildren();
			return new Enumeration<DiskItemTreeNode>() {
				int index = 0;
				@Override public boolean hasMoreElements() { return index < children.length; }
				@Override public DiskItemTreeNode nextElement() { return children[index++]; }
			};
		}

		@Override public TreeNode getParent() { return parent; }
		@Override public boolean  getAllowsChildren() { return true; }
		@Override public boolean  isLeaf() { return getChildCount()==0; }
		@Override public int      getChildCount()            { if (children==null) createChildren(); return children.length; }
		@Override public TreeNode getChildAt(int childIndex) { if (children==null) createChildren(); return children[childIndex]; }
		@Override public int      getIndex(TreeNode node)    { if (children==null) createChildren(); return Arrays.asList(children).indexOf(node); }
	}

	static class DiskItem {

		final DiskItem parent;
		final String name; 
		long size;
		Vector<DiskItem> children;

		public DiskItem() { this(null,"<root>"); }
		private DiskItem(DiskItem parent, String name) {
			this.parent = parent;
			this.name = name;
			size = 0;
			children = new Vector<>();
		}

		@Override
		public String toString() {
			//return name + " (" + size + "kB)";
			//return String.format(Locale.GERMAN, "[ %,d kB ]   %s", toString(size), name);
			return String.format("[ %s ]   %s", getSizeStr(), name);
		}
		
		public String getSizeStr() {
			if (size<1024) return size+" kB";
			double sizeD;
			sizeD = size/1024.0; if (sizeD<1024) return String.format(Locale.ENGLISH, "%1.2f MB", sizeD);
			sizeD = sizeD/1024;  if (sizeD<1024) return String.format(Locale.ENGLISH, "%1.2f GB", sizeD);
			sizeD = sizeD/1024;                  return String.format(Locale.ENGLISH, "%1.2f TB", sizeD);
		}
		
		public DiskItem get(String[] path) {
			return getChild(path,0);
		}

		private DiskItem getChild(String[] path, int i) {
			if (i>=path.length) return this;
			
			DiskItem child = null;
			for (DiskItem ch:children)
				if (ch.name.equals(path[i])) {
					child = ch;
					break;
				}
			if (child == null)
				children.add(child = new DiskItem(this,path[i]));
			return child.getChild(path, i+1);
		}
	}
}
