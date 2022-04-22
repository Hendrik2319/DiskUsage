package net.schwarzbaer.java.tools.diskusage;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Vector;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.gui.Tables;
import net.schwarzbaer.gui.Tables.SimplifiedColumnConfig;
import net.schwarzbaer.java.tools.diskusage.DiskUsage.DiskItem;
import net.schwarzbaer.java.tools.diskusage.DiskUsage.ImportedFileData;

class DiskUsageCompare {
	
	private final StandardMainWindow mainWindow;
	private final SourceFieldRow oldDataField;
	private final SourceFieldRow newDataField;
	private final CompareDiskItem root;
	private final JTree tree;
	private final FileTableModel fileTableModel;
	private final JTable fileTable;

	DiskUsageCompare() {
		mainWindow = new StandardMainWindow("DiskUsageCompare");
		
		root = new CompareDiskItem(null);
		
		tree = new JTree((TreeNode)null);
		JScrollPane treePanel = new JScrollPane(tree);
		
		fileTableModel = new FileTableModel();
		fileTable = new JTable(fileTableModel);
		fileTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		JScrollPane fileTablePanel = new JScrollPane(fileTable);
		
		tree.getSelectionModel().addTreeSelectionListener(new CDITreeSelectionListener());
		
		JSplitPane mainPanel = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, treePanel, fileTablePanel);
		mainPanel.setResizeWeight(1);
		mainPanel.setDividerLocation(400);
		
		
		JPanel sourceFieldPanel = new JPanel(new GridBagLayout());
		oldDataField = new SourceFieldRow(mainWindow, sourceFieldPanel, "Old Data", oldRoot->{
			root.setData(oldRoot, CompareDiskItem.DataType.OldData);
			tree.setModel(new DefaultTreeModel(root));
		});
		newDataField = new SourceFieldRow(mainWindow, sourceFieldPanel, "New Data", newRoot->{
			root.setData(newRoot, CompareDiskItem.DataType.NewData);
			tree.setModel(new DefaultTreeModel(root));
		});
		
		
		JPanel contentPane = new JPanel(new BorderLayout(3,3));
		contentPane.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		contentPane.add(sourceFieldPanel,BorderLayout.NORTH);
		contentPane.add(mainPanel,BorderLayout.CENTER);
		
		mainWindow.startGUI(contentPane, createMenuBar(), 800,600);
	}

	private JMenuBar createMenuBar() {
		JMenuBar menuBar = new JMenuBar();
		
		JMenu debugMenu = menuBar.add(new JMenu("Debug"));
		debugMenu.add(DiskUsage.createMenuItem("Show Table Column Widths", e->{
			System.out.printf("Table Column Widths: %s%n", Tables.SimplifiedTableModel.getColumnWidthsAsString(fileTable));
		}));
		
		return menuBar;
	}
	
	void initialize() {
		boolean success = true;
		if (success) success = DiskUsage.OpenDialog.show(mainWindow, "Load Old File Tree", oldDataField::scanFolder, oldDataField::openStoredTree);
		if (success) success = DiskUsage.OpenDialog.show(mainWindow, "Load New File Tree", newDataField::scanFolder, newDataField::openStoredTree);
	}
	
	private final class CDITreeSelectionListener implements TreeSelectionListener {
		
		private TreePath selectedPath;
		private CompareDiskItem selectedCDI;

		public CDITreeSelectionListener() {
			selectedPath = null;
			selectedCDI = null;
		}

		@Override
		public void valueChanged(TreeSelectionEvent e) {
			selectedPath = e.getPath();
			Object obj = selectedPath==null ? null : selectedPath.getLastPathComponent();
			if (obj instanceof CompareDiskItem) {
				selectedCDI = (CompareDiskItem) obj;
				fileTableModel.setData(selectedCDI);
//				System.out.printf("Selected Path: %s%n", selectedPath);
//				System.out.printf("Selected CDI: %s%n", selectedCDI);
			} else {
//				System.out.printf("Selected Path: %s%n", selectedPath);
//				System.out.printf("Selected CDI: %s%n", obj);
			}
		}
	}

	private static class CompareDiskItem implements TreeNode {
		
		private static final Comparator<String> NAME_ORDER =
				Comparator
				.<String,String>comparing(str->str.toLowerCase())
				.thenComparing(Comparator.naturalOrder());
		
		private static final Comparator<DiskItem> DISKITEM_ORDER =
				Comparator
				.<DiskItem,String>comparing(di->di.name,NAME_ORDER);
		
		enum DataType {
			OldData,
			NewData,
			;
			DataType() {
				
			}

			DataType getOther() {
				switch (this) {
				case NewData: return OldData;
				case OldData: return NewData;
				}
				throw new IllegalStateException();
			}
		}
		
		private final CompareDiskItem parent;
		private Vector<CompareDiskItem> children;
		private String name; 
		private DiskItem oldData;
		private DiskItem newData;

		CompareDiskItem(CompareDiskItem parent) {
			this(parent,null,null);
		}
		CompareDiskItem(CompareDiskItem parent, DiskItem oldData, DiskItem newData) {
			this.parent = parent;
			name = null;
			children = null;
			this.oldData = oldData;
			this.newData = newData;
			
			if (this.oldData!=null && this.newData!=null) {
				if (!this.oldData.name.equals(this.newData.name)) {
					String oldDataPath = this.oldData.getPathStr("/");
					String newDataPath = this.newData.getPathStr("/");
					String msg = String.format("Unequal names in CompareDiskItem:%nold data path: %s%nnew data path: %s", oldDataPath, newDataPath);
					throw new IllegalArgumentException(msg);
				}
			}
			
			if (this.oldData!=null) name = this.oldData.name;
			if (this.newData!=null) name = this.newData.name;
		}
		
		String getPathStr(String glue) {
			if (parent == null) return null;
			String pathStr = parent.getPathStr(glue);
			if (pathStr==null) return name;
			return pathStr+glue+name;
		}

		boolean isSet(DataType type) {
			return getData(type) != null;
		}
		
		DiskItem getData(DataType type) {
			if (type==null) throw new IllegalArgumentException();
			switch (type) {
			case NewData: return newData;
			case OldData: return oldData;
			}
			throw new IllegalStateException();
		}
		
		void removeData(DataType dataType) {
			switch (dataType) {
			case NewData: newData = null; break;
			case OldData: oldData = null; break;
			}
			
			if (children == null)
				return;
			
			for (int i=0; i<children.size();) {
				CompareDiskItem cdi = children.get(i);
				if (!cdi.isSet(dataType.getOther()))
					children.remove(i);
				else {
					cdi.removeData(dataType);
					i++;
				}
			}
		}
		
		void setData(DiskItem data, DataType dataType) {
			if (dataType==null)
				throw new IllegalArgumentException(String.format("No DataType given to CompareDiskItem.setData(). path=\"%s\"", getPathStr("/")));
			if (data==null)
				throw new IllegalArgumentException(String.format("Set null as %s: %s", dataType.toString(), getPathStr("/")));
			
			if (name == null)
				name = data.name;
			else if (!name.equals(data.name)) {
				String msg = String.format(
						"Set %s with data with different name:%npath: \"%s\"%nthis.name: \"%s\"%n%s.name: \"%s\"",
						dataType.toString(), getPathStr("/"), name, dataType.toString(), data.name);
				throw new IllegalArgumentException(msg);
			}
			
			switch (dataType) {
			case NewData: newData = data; break;
			case OldData: oldData = data; break;
			}
			
			if (children != null)
				setChildrenData(data, dataType);
		}

		private void setChildrenData(DiskItem data, DataType dataType) {
			Vector<DiskItem> diChildren = new Vector<>(data.children);
			diChildren.sort(DISKITEM_ORDER);
			
			for (int i_di=0, i_cdi=0; i_di<diChildren.size() || i_cdi<children.size();) {
				CompareDiskItem cdi = i_cdi<children  .size() ? children  .get(i_cdi) : null;
				DiskItem         di = i_di <diChildren.size() ? diChildren.get(i_di ) : null;
				
				if (cdi!=null && cdi.name==null) throw new IllegalStateException();
				if ( di!=null &&  di.name==null) throw new IllegalStateException();
				
				if (cdi == null && di == null)
					throw new IllegalStateException();
				
				if (cdi == null) {
					cdi = new CompareDiskItem(this);
					cdi.setData(di, dataType);
					children.add(cdi);
					i_cdi++;
					i_di++;
					
				} else if (di == null) {
					if (!cdi.isSet(dataType.getOther()))
						children.remove(i_cdi);
					else {
						cdi.removeData(dataType);
						i_cdi++;
					}
					
				} else {
					int n = NAME_ORDER.compare(cdi.name, di.name);
					if (n==0) {
						cdi.setData(di, dataType);
						i_cdi++;
						i_di++;
						
					} else if (n<0) {
						if (!cdi.isSet(dataType.getOther()))
							children.remove(i_cdi);
						else {
							cdi.removeData(dataType);
							i_cdi++;
						}
						
					} else if (n>0) {
						cdi = new CompareDiskItem(this);
						cdi.setData(di, dataType);
						children.insertElementAt(cdi, i_cdi);
						i_cdi++;
						i_di++;
						
					}
				}
			}
		}

		private void generateChildren() {
			if (children!=null) throw new IllegalStateException();
			children = new Vector<>();
			
			Vector<DiskItem> oldChildren = oldData==null ? new Vector<>() : new Vector<>(oldData.children);
			Vector<DiskItem> newChildren = newData==null ? new Vector<>() : new Vector<>(newData.children);
			oldChildren.sort(DISKITEM_ORDER);
			newChildren.sort(DISKITEM_ORDER);
			
			for (int i_old=0, i_new=0; i_old<oldChildren.size() || i_new<newChildren.size();) {
				DiskItem di_old = i_old<oldChildren.size() ? oldChildren.get(i_old) : null;
				DiskItem di_new = i_new<newChildren.size() ? newChildren.get(i_new) : null;
				
				if (di_old!=null && di_old.name==null) throw new IllegalStateException();
				if (di_new!=null && di_new.name==null) throw new IllegalStateException();
				
				if (di_old == null && di_new == null)
					throw new IllegalStateException();
				
				if (di_new == null) {
					children.add(new CompareDiskItem(this,di_old,null));
					i_old++;
					
				} else if (di_old == null) {
					children.add(new CompareDiskItem(this,null,di_new));
					i_new++;
					
				} else {
					int n = NAME_ORDER.compare(di_old.name, di_new.name);
					if (n==0) {
						children.add(new CompareDiskItem(this,di_old,di_new));
						i_old++;
						i_new++;
						
					} else if (n<0) {
						children.add(new CompareDiskItem(this,di_old,null));
						i_old++;
						
					} else if (n>0) {
						children.add(new CompareDiskItem(this,null,di_new));
						i_new++;
						
					}
				}
			}
		}

		@Override public String toString() {
			if (oldData==null && newData==null)
				return String.format("%s <EMPTY>", name);
			
			if (oldData==null) return String.format("%s <ADDED> %s"  , name, newData.getSizeStr());
			if (newData==null) return String.format("%s <REMOVED> %s", name, oldData.getSizeStr());
			
			if (oldData.size_kB == newData.size_kB)
				return String.format("%s [%s]", name, oldData.getSizeStr());
			
			if (oldData.size_kB==0 || newData.size_kB==0)
				return String.format("%s [%s >> %s]", name, oldData.getSizeStr(), newData.getSizeStr());
			
			double diff_percent = (newData.size_kB/(double)oldData.size_kB-1)*100;
			String diff_abs_Str = DiskItem.getSizeStr(Math.abs(newData.size_kB-oldData.size_kB));
			
			if (oldData.size_kB < newData.size_kB) {
				return String.format(Locale.ENGLISH, "%s [%s >> %s] +%s (+%1.2f%%)", name, oldData.getSizeStr(), newData.getSizeStr(), diff_abs_Str, diff_percent);
			} else
				return String.format(Locale.ENGLISH, "%s [%s >> %s] -%s (%1.2f%%)", name, oldData.getSizeStr(), newData.getSizeStr(), diff_abs_Str, diff_percent);
		}
		
		@Override public TreeNode getChildAt(int childIndex) {
			if (children==null) generateChildren();
			if (childIndex<0 || children.size()<=childIndex) return null;
			return children.get(childIndex);
		}
		@Override public TreeNode getParent    ()                { return parent; }
		@Override public int      getChildCount()                { if (children==null) generateChildren(); return children.size(); }
		@Override public int      getIndex(TreeNode node)        { if (children==null) generateChildren(); return children.indexOf(node); }
		@Override public boolean  getAllowsChildren()            { return true; }
		@Override public boolean  isLeaf           ()            { if (children==null) generateChildren(); return children.isEmpty(); }
		@Override public Enumeration<CompareDiskItem> children() { if (children==null) generateChildren(); return children.elements(); }
		Vector<CompareDiskItem>   getChildren()                  { if (children==null) generateChildren(); return children; }
	}
	
	private static class SourceFieldRow {
		
		private final Window parent;
		private final String title;
		private final Consumer<DiskUsage.DiskItem> setRoot;
		private final JTextField outputField;
		private final JButton btnSaveStoredTree;

		private ImportedFileData ifd;

		SourceFieldRow(Window parent, JPanel panel, String title, Consumer<DiskUsage.DiskItem> setRoot) {
			this.parent = parent;
			this.title = title;
			this.setRoot = setRoot;
			ifd = null;
			
			GridBagConstraints c = new GridBagConstraints();
			c.fill = GridBagConstraints.HORIZONTAL;
			
			c.gridwidth = 1;
			c.weightx = 0;
			panel.add(new JLabel(String.format("%s: ",title)),c);
			c.weightx = 1;
			panel.add(outputField = DiskUsage.createOutputTextField(""),c);
			c.weightx = 0;
			panel.add(DiskUsage.createButton(DiskUsage.Icons32.OpenFolder    , null, "Scan Folder"     , e->scanFolder    ()),c);
			panel.add(DiskUsage.createButton(DiskUsage.Icons32.OpenStoredTree, null, "Open Stored Tree", e->openStoredTree()),c);
			c.gridwidth = GridBagConstraints.REMAINDER;
			btnSaveStoredTree = DiskUsage.createButton(DiskUsage.Icons32.SaveStoredTree, null, "Save as Stored Tree", e->saveStoredTree());
			btnSaveStoredTree.setEnabled(false);
			panel.add(btnSaveStoredTree,c);
		}

		private void saveStoredTree() { DiskUsage.saveStoredTree(parent, String.format("Select Stored Tree File for %s", title), ifd.root()); }
		boolean scanFolder    () { return importData(DiskUsage::scanFolder    , String.format("Select Folder for %s"          , title)); }
		boolean openStoredTree() { return importData(DiskUsage::openStoredTree, String.format("Select Stored Tree File for %s", title)); }
		
		private boolean importData(BiFunction<Window,String,DiskUsage.ImportedFileData> importFcn, String dlgTitle) {
			DiskUsage.ImportedFileData ifd = importFcn.apply(parent, dlgTitle);
			if (ifd==null) return false;
			this.ifd = ifd;
			setRoot.accept(this.ifd.root());
			outputField.setText(this.ifd.source().getAbsolutePath());
			btnSaveStoredTree.setEnabled(true);
			return true;
		}
	}

	private static class FileTableModel extends Tables.SimplifiedTableModel<FileTableModel.ColumnID> {

		enum ColumnID implements Tables.SimplifiedColumnIDInterface {
			Name    ("Name"     ,  String.class, 500),
			SizeOld ("Size (Old)",   Long.class,  80),
			SizeNew ("Size (New)",   Long.class,  80),
			;
		
			private final SimplifiedColumnConfig config;
			ColumnID(String name, Class<?> columnClass, int width) {
				config = new SimplifiedColumnConfig(name, columnClass, 20, -1, width, width);
			}
			@Override public SimplifiedColumnConfig getColumnConfig() { return config; }
			
		}

		private Vector<CompareDiskItem> data;

		private FileTableModel() {
			super(ColumnID.values());
			data = null;
		}

		void setData(CompareDiskItem cdi) {
			data = cdi.getChildren();
			fireTableUpdate();
		}

		@Override public int getRowCount() { return data==null ? 0 : data.size(); }

		CompareDiskItem getRow(int rowIndex) {
			if (data==null || rowIndex<0 || data.size()<=rowIndex) return null;
			return data.get(rowIndex);
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex, ColumnID columnID) {
			CompareDiskItem cdi = getRow(rowIndex);
			if (cdi==null) return null;
			
			switch (columnID) {
			case Name   : return cdi.name;
			case SizeNew: return cdi.newData==null ? null : cdi.newData.size_kB;
			case SizeOld: return cdi.oldData==null ? null : cdi.oldData.size_kB;
			}
			return null;
		}
	
	}

}
