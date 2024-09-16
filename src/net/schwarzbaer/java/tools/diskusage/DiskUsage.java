package net.schwarzbaer.java.tools.diskusage;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.AbstractButton;
import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import net.schwarzbaer.java.lib.gui.FileChooser;
import net.schwarzbaer.java.lib.gui.HSColorChooser;
import net.schwarzbaer.java.lib.gui.IconSource;
import net.schwarzbaer.java.lib.gui.ProgressDialog;
import net.schwarzbaer.java.lib.gui.StandardDialog;
import net.schwarzbaer.java.lib.gui.StandardMainWindow;
import net.schwarzbaer.java.lib.gui.Tables;
import net.schwarzbaer.java.lib.gui.HSColorChooser.ColorDialog;
import net.schwarzbaer.java.lib.image.bumpmapping.BumpMapping.Normal;
import net.schwarzbaer.java.lib.system.ClipboardTools;
import net.schwarzbaer.java.lib.system.DateTimeFormatter;
import net.schwarzbaer.java.tools.diskusagecompare.DiskUsageCompare;

public class DiskUsage implements FileMap.GuiContext {

	private static final String PATH_CHAR = "/";
	private static final String CONFIG_FILE = "DiskUsage.cfg";
	
	public enum Icons32 {
		OpenFolder, OpenStoredTree, SaveStoredTree, EditTypes;
		public Icon getCacheIcon() { return icons32.getCachedIcon(this); }
	}
	public enum Icons16 {
		Folder, OpenStoredTree, EmptyFile, SaveStoredTree;
		public Icon getCacheIcon() { return icons16.getCachedIcon(this); }
	}
	
	private static IconSource.CachedIcons<Icons32> icons32;
	private static IconSource.CachedIcons<Icons16> icons16;
	private static FileChooser storedTreeChooser;
	private static JFileChooser folderChooser;
	
	public static void main(String[] args) {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		
		IconSource<Icons32> icons32source = new IconSource<>(32,32);
		icons32source.readIconsFromResource("/icons32.png");
		icons32 = icons32source.cacheIcons(Icons32.values());
		
		IconSource<Icons16> icons16source = new IconSource<>(16,16);
		icons16source.readIconsFromResource("/icons16.png");
		icons16 = icons16source.cacheIcons(Icons16.values());
		
		storedTreeChooser = StoredTreeIO.createFileChooser();
		folderChooser = new JFileChooser("./");
		folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		folderChooser.setMultiSelectionEnabled(false);
		
		boolean compareFolders = false;
		for (String arg : args) {
			if (arg.equalsIgnoreCase("-compare")) compareFolders = true;
			if (arg.equalsIgnoreCase("compare")) compareFolders = true;
		}
		
		Config.readConfig();
		
		if (compareFolders) {
			DiskUsageCompare diskUsageCompare = new DiskUsageCompare();
			diskUsageCompare.initialize();
		} else {
			DiskUsage diskUsage = new DiskUsage();
			diskUsage.initialize();
		}
	}
	
	private DiskItem root;
	private final StandardMainWindow mainWindow;
	private final TreePanel treePanel;
	private final FileMapPanel fileMapPanel;
	
	DiskUsage() {
		mainWindow = new StandardMainWindow("DiskUsage");
		
		treePanel = new TreePanel();
		fileMapPanel = new FileMapPanel();
		
		
		JPanel contentPane = new JPanel(new BorderLayout(3,3));
		contentPane.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		contentPane.add(treePanel,BorderLayout.WEST);
		contentPane.add(fileMapPanel,BorderLayout.CENTER);
		
		mainWindow.startGUI(contentPane);
	}
	
	private void initialize() {
		treePanel.rootChanged(null);
		fileMapPanel.rootChanged();
		OpenDialog.show(mainWindow, "Load File Tree", this::scanFolder, this::openStoredTree);
	}
	
	public static JTextField createOutputTextField(String initialValue) {
        JTextField comp = new JTextField(initialValue);
        comp.setEditable(false);
        return comp;
    }

	public static JToggleButton createToggleButton(Icons16 icon, String title, boolean isSelected, ButtonGroup bg, ActionListener al) {
		JToggleButton comp = new JToggleButton();
		comp.setSelected(isSelected);
		if (bg!=null) bg.add(comp);
		return setAbstractButton(comp, null, icon, title, null, al);
    }
	
	public static JButton createButton(String title, ActionListener al) {
		return setAbstractButton(new JButton(), null, null, title, null, al);
    }
	public static JButton createButton(Icons16 icon, String title, ActionListener al) {
		return setAbstractButton(new JButton(), null, icon, title, null, al);
    }
	public static JButton createButton(Icons32 icon, String title, ActionListener al) {
		return setAbstractButton(new JButton(), icon, null, title, null, al);
    }
	public static JButton createButton(Icons16 icon, String title, String toolTip, ActionListener al) {
		return setAbstractButton(new JButton(), null, icon, title, toolTip, al);
    }
	public static JButton createButton(Icons32 icon, String title, String toolTip, ActionListener al) {
		return setAbstractButton(new JButton(), icon, null, title, toolTip, al);
    }
	public static JButton createButton(Icons32 icon32, Icons16 icon16, String title, String toolTip, ActionListener al) {
		return setAbstractButton(new JButton(), icon32, icon16, title, toolTip, al);
	}

	private static <Btn extends AbstractButton> Btn setAbstractButton(Btn comp, Icons32 icon32, Icons16 icon16, String title, String toolTip, ActionListener al) {
		if (icon32 !=null) comp.setIcon(icons32.getCachedIcon(icon32));
		if (icon16 !=null) comp.setIcon(icons16.getCachedIcon(icon16));
		if (title  !=null) comp.setText(title);
		if (toolTip!=null) comp.setToolTipText(toolTip);
		if (al     !=null) comp.addActionListener(al);
		comp.setHorizontalAlignment(JButton.LEFT);
		return comp;
	}

	public static JCheckBox createCheckBox(String title, String toolTip, boolean isChecked, boolean isEnabled, Consumer<Boolean> setValue) {
		JCheckBox comp = new JCheckBox();
		comp.setSelected(isChecked);
		comp.setEnabled(isEnabled);
		if (title   !=null) comp.setText(title);
		if (toolTip !=null) comp.setToolTipText(toolTip);
		if (setValue!=null) comp.addActionListener(e->setValue.accept(comp.isSelected()));
		return comp;
	}

	public static JMenuItem createMenuItem(String title, ActionListener al) {
		JMenuItem comp = new JMenuItem(title);
		if (al!=null) comp.addActionListener(al);
		return comp;
	}

	public static class OpenDialog extends StandardDialog {
		private static final long serialVersionUID = 2425769711741725154L;

		public static boolean show(Window parent, String title, Supplier<Boolean> scanFolderFcn, Supplier<Boolean> openStoredTreeFcn) {
			OpenDialog dlg = new OpenDialog(parent, title, scanFolderFcn, openStoredTreeFcn);
			dlg.showDialog();
			return !dlg.wasAborted; 
		}

		private boolean wasAborted;

		private OpenDialog(Window parent, String title, Supplier<Boolean> scanFolderFcn, Supplier<Boolean> openStoredTreeFcn) {
			super(parent, title);
			wasAborted = true;
			JPanel contentPane = new JPanel(new GridBagLayout());
			contentPane.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
			GridBagConstraints c = new GridBagConstraints();
			GBC.setFill(c, GBC.GridFill.BOTH);
			GBC.setLineEnd(c);
			GBC.setWeights(c,1,1);
			contentPane.add(createButton(Icons32.OpenFolder    ,"Select Folder"   ,e->closeIfSuccessful(scanFolderFcn    )),c);
			contentPane.add(createButton(Icons32.OpenStoredTree,"Open Stored Tree",e->closeIfSuccessful(openStoredTreeFcn)),c);
			createGUI(contentPane);
		}

		private void closeIfSuccessful(Supplier<Boolean> action) {
			boolean success = action.get();
			if (success) {
				wasAborted = false;
				closeDialog();
			}
		}
	}
	
	private class TreePanel extends JPanel {
		private static final long serialVersionUID = 4456876243723688978L;
		
		private File treeSource;
		private DiskItemTreeNode rootTreeNode;
		
		private JTextField treeSourceField;
		private JTree treeView;
		private DefaultTreeModel treeViewModel;
		private ContextMenu treeViewContextMenu;
		private DiskItemTypeDialog diskItemTypeDialog = null;
		
		TreePanel() {
			super(new GridBagLayout());
			GridBagConstraints c = new GridBagConstraints();
			
			treeSource = null;
			rootTreeNode = null;
			treeViewModel = new DefaultTreeModel(rootTreeNode);
			treeView = new JTree(treeViewModel);
			treeView.setRootVisible(false);
			treeView.setCellRenderer(new MyTreeCellRenderer());
			treeView.addMouseListener(new MyMouseListener());
			treeView.addTreeSelectionListener(e -> {
				TreePath treePath = treeView.getSelectionPath();
				if (treePath==null) return;
				Object object = treePath.getLastPathComponent();
				if (object instanceof DiskItemTreeNode) {
					DiskItemTreeNode treeNode = (DiskItemTreeNode) object;
					fileMapPanel.fileMap.setSelected(treeNode.diskItem);
				}
			});
			treeViewContextMenu = new ContextMenu();
			
			JScrollPane treeScrollPane = new JScrollPane(treeView);
			treeScrollPane.setPreferredSize(new Dimension(500,800));
			
			GBC.setFill(c, GBC.GridFill.BOTH);
			setBorder(BorderFactory.createTitledBorder("Folder Structure"));
			add(treeSourceField = createOutputTextField(getTreeSourceLabel()),GBC.setWeights(c,1,0));
			add(createButton(Icons32.OpenFolder    ,"Select Folder"      ,new Insets(0,0,0,0),e->scanFolder    ()),GBC.setWeights(c,0,0));
			add(createButton(Icons32.OpenStoredTree,"Open Stored Tree"   ,new Insets(0,0,0,0),e->openStoredTree()),c);
			add(createButton(Icons32.SaveStoredTree,"Save as Stored Tree",new Insets(0,0,0,0),e->saveStoredTree()),c);
			add(createButton(Icons32.EditTypes     ,"Edit File Types"    ,new Insets(0,0,0,0),e->editFileTypes ()),GBC.setLineEnd(c));
			add(treeScrollPane,GBC.setWeights(c,1,1));
			treeSourceField.setPreferredSize(new Dimension(30,30));
			treeSourceField.setMinimumSize(new Dimension(30,30));
			
		}
		
		public void rootChanged(File treeSource) {
			this.treeSource = treeSource;
			treeSourceField.setText(getTreeSourceLabel());
			rootTreeNode = root==null?null:new DiskItemTreeNode(root);
			treeViewModel.setRoot(rootTreeNode);
		}

		private String getTreeSourceLabel() {
			if (treeSource==null) return "";
			if (treeSource.isFile     ()) return "[StoredTree]  "+treeSource.getAbsolutePath();
			if (treeSource.isDirectory()) return "[Folder]  "+treeSource.getAbsolutePath();
			 return "[???]  "+treeSource.getAbsolutePath();
		}

		private JButton createButton(Icons32 icon, String toolTip, Insets margins, ActionListener al) {
			JButton btn = new JButton();
			if (margins!=null) btn.setMargin(margins);
			btn.setToolTipText(toolTip);
			btn.addActionListener(al);
			btn.setIcon(icons32.getCachedIcon(icon));
			return btn;
		}

		private class MyTreeCellRenderer extends DefaultTreeCellRenderer {
			private static final long serialVersionUID = -833719497285747612L;
		
			@Override
			public Component getTreeCellRendererComponent(JTree tree, Object value, boolean isSelected, boolean isExpanded, boolean isLeaf, int row, boolean hasFocus) {
				Component component = super.getTreeCellRendererComponent(tree, value, isSelected, isExpanded, isLeaf, row, hasFocus);
				if (value instanceof DiskItemTreeNode && !isSelected) {
					DiskItemTreeNode treeNode = (DiskItemTreeNode) value;
					if (treeNode.diskItem==fileMapPanel.fileMapRoot)
						component.setForeground(Color.BLUE);
					else if (treeNode.diskItem.isChildOf(fileMapPanel.fileMapRoot)) {
						Color color = treeNode.diskItem.getColor();
						component.setForeground(color==null?Color.BLACK:color);
					} else
						component.setForeground(Color.GRAY);
				}
				return component;
			}
		}

		private class MyMouseListener implements MouseListener {
		
			@Override public void mousePressed (MouseEvent e) {}
			@Override public void mouseReleased(MouseEvent e) {}
			@Override public void mouseEntered (MouseEvent e) {}
			@Override public void mouseExited  (MouseEvent e) {}
			@Override public void mouseClicked (MouseEvent e) {
				TreePath clickedTreePath = treeView.getPathForLocation(e.getX(),e.getY());
				DiskItemTreeNode clickedTreeNode = null;
				if (clickedTreePath!=null) {
					Object comp = clickedTreePath.getLastPathComponent();
					if (comp instanceof DiskItemTreeNode)
						clickedTreeNode = (DiskItemTreeNode) comp;
				}
				
				if (e.getButton()==MouseEvent.BUTTON3) {
					treeViewContextMenu.show(treeView, e.getX(), e.getY(), clickedTreeNode);
				}
				
				if (e.getButton()==MouseEvent.BUTTON1) {
					//setSelected(root.getMapItemAt(e.getX(), e.getY()));
					//if (selectedMapItem!=null) guiContext.expandPathInTree(selectedMapItem.diskItem);
				}
			}
		}

		private class ContextMenu extends JPopupMenu {
				private static final long serialVersionUID = 4192144302244498205L;
				private DiskItemTreeNode clickedTreeNode;
				private JMenuItem showInFileMap;
				private JMenuItem copyPath;
				
				ContextMenu() {
					add(showInFileMap = createMenuItem("Show in File Map",e->{
						fileMapPanel.setFileMapRoot(clickedTreeNode.diskItem);
						treeView.repaint();
					}));
					add(copyPath = createMenuItem("Copy Path",e->{
						copyPathToClipBoard(clickedTreeNode.diskItem);
					}));
		//			createCheckBoxMenuItems(pst, Layouter.Type.values(), t->t.title, FileMap.this::setLayouter);
		//			add(configureLayouter = createMenuItem("Configure Layouter ...",e->currentLayouter.showConfig(FileMap.this)));
		//			addSeparator();
		//			createCheckBoxMenuItems(pt , Painter .Type.values(), t->t.title, FileMap.this::setPainter );
		//			add(configurePainter = createMenuItem("Configure Painter ...",e->currentPainter.showConfig(FileMap.this)));
				}
				
				public void show(Component invoker, int x, int y, DiskItemTreeNode clickedTreeNode) {
					this.clickedTreeNode = clickedTreeNode;
					showInFileMap.setEnabled(this.clickedTreeNode!=null && !this.clickedTreeNode.isLeaf());
					copyPath     .setEnabled(this.clickedTreeNode!=null);
					show(invoker, x, y);
				}
				
				@SuppressWarnings("unused")
				private <E extends Enum<E>> void createCheckBoxMenuItems(E selected, E[] values, Function<E,String> getTitle, Consumer<E> set) {
					ButtonGroup bg = new ButtonGroup();
					for (E t:values)
						add(createCheckBoxMenuItem(getTitle.apply(t),t==selected,bg,e->set.accept(t)));
				}
				private JCheckBoxMenuItem createCheckBoxMenuItem(String title, boolean isSelected, ButtonGroup bg, ActionListener al) {
					JCheckBoxMenuItem comp = new JCheckBoxMenuItem(title,isSelected);
					comp.addActionListener(al);
					bg.add(comp);
					return comp;
				}
				private JMenuItem createMenuItem(String title, ActionListener al) {
					JMenuItem comp = new JMenuItem(title);
					comp.addActionListener(al);
					return comp;
				}
			}

		public void expandPathInTree(DiskItem diskItems) {
			DiskItemTreeNode node = rootTreeNode.find(diskItems);
			if (node==null) return;
			TreePath treePath = node.getPath();
			//System.out.println(treePath);
			treeView.setSelectionPath(treePath);
			treeView.scrollPathToVisible(treePath);
		}

		public void editFileTypes() {
			if (diskItemTypeDialog==null)
				diskItemTypeDialog = new DiskItemTypeDialog(mainWindow, "Edit File Types", true);
			diskItemTypeDialog.showDialog();
			DiskItemType.setTypes(root);
			treeView.repaint();
			fileMapPanel.fileMap.update();
			saveConfig();
		}
	}

	private class FileMapPanel extends JPanel {
		private static final long serialVersionUID = 4060339037904604642L;
		
		private FileMap fileMap;
		private DiskItem fileMapRoot;
		private JTextField fileMapRootPathField;
		private JTextField highlightedPathField;
		
		FileMapPanel() {
			super(new GridBagLayout());
			setBorder(BorderFactory.createTitledBorder("File Map"));
			
			GridBagConstraints c = new GridBagConstraints();
			
			fileMapRoot = null;
			fileMap = new FileMap(fileMapRoot,1000,800,DiskUsage.this);
			fileMap.setPreferredSize(new Dimension(1000,800));
			
			GBC.reset(c);
			GBC.setFill(c, GBC.GridFill.BOTH);
			GBC.setLineMid(c);
			GBC.setWeights(c,0,0);
			add(new JLabel("Root Folder: "),GBC.setGridPos(c, 0,0));
			add(new JLabel("Highlighted File/Folder: "),GBC.setGridPos(c, 0,1));
			GBC.setWeights(c,1,0);
			add(fileMapRootPathField = createOutputTextField(getPathStr(fileMapRoot)),GBC.setGridPos(c, 1,0));
			add(highlightedPathField = createOutputTextField(""),GBC.setGridPos(c, 1,1));
			
			GBC.setGridPos(c, 0,2);
			GBC.setLineEnd(c);
			GBC.setWeights(c,1,1);
			add(fileMap,c);
			//fileMapRootPathField.setPreferredSize(new Dimension(30,30));
			//fileMapRootPathField.setMinimumSize(new Dimension(30,30));
		}
		
		String getPathStr(DiskItem diskItem) {
			return diskItem==null?null:diskItem.getPathStr("  >  ");
		}
		
		void setHighlightedPath(DiskItem diskItem) {
			highlightedPathField.setText(getPathStr(diskItem));
		}

		void setFileMapRoot(DiskItem diskItem) {
			fileMap.setRoot(fileMapRoot = diskItem);
			fileMapRootPathField.setText(getPathStr(fileMapRoot));
			highlightedPathField.setText(null);
		}

		public void rootChanged() {
			DiskItem fmr = root;
			while (fmr!=null && fmr.children.size()==1)
				fmr = fmr.children.get(0);
			setFileMapRoot(fmr);
		}
	}
	
	static class DiskItemTypeDialog extends StandardDialog {
		private static final long serialVersionUID = 3019855581662554862L;

		public DiskItemTypeDialog(Window parent, String title, boolean repeatedUseOfDialogObject) {
			super(parent, title, ModalityType.APPLICATION_MODAL, repeatedUseOfDialogObject );
			
			DiskItemTypeTableModel tableModel = new DiskItemTypeTableModel();
			JTable table = new JTable(tableModel);
			tableModel.setColumnWidths(table);
			
			ColorCellRendererEditor renderer = new ColorCellRendererEditor();
			table.setDefaultRenderer(Color.class, renderer);
			table.setDefaultEditor(Color.class, renderer);
			
			TableColumn column = table.getColumnModel().getColumn(table.convertColumnIndexToView(tableModel.getColumn(ColumnID.Label)));
			column.setCellRenderer(new LabelCellRenderer());
			
			JScrollPane tableScrollPane = new JScrollPane(table);
			tableScrollPane.setPreferredSize(new Dimension(tableModel.getSumOfColumnWidths()+20, (DiskItemType.types.size()+1)*table.getRowHeight()+60));
			
			JPanel contentPane = new JPanel(new BorderLayout(3,3));
			contentPane.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
			contentPane.add(tableScrollPane,BorderLayout.CENTER);
			createGUI(contentPane);
		}
		
		private static class LabelCellRenderer extends DefaultTableCellRenderer {
			private static final long serialVersionUID = 2954177515113317104L;

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
				Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				if (value==null) {
					if (!isSelected) setForeground(Color.GRAY);
					setText("Add New Type");
				} else if (!isSelected) 
					setForeground(Color.BLACK);
				return component;
			}
			
		}
		
		private class ColorCellRendererEditor extends AbstractCellEditor implements TableCellRenderer, TableCellEditor {
			private static final long serialVersionUID = 2734834827009184692L;
			
			private Tables.LabelRendererComponent renderComp;
			private ColorDialog colorDialog;
			private Color oldColor;
			private Color newColor;
			private boolean editingCanceled;

			ColorCellRendererEditor() {
				renderComp = new Tables.LabelRendererComponent();
				colorDialog = null;
				editingCanceled = false;
				newColor = null;
				oldColor = null;
			}

			@Override
			public Object getCellEditorValue() {
				return newColor;
			}

			@Override
			public boolean stopCellEditing() {
				if (colorDialog!=null) {
					editingCanceled = false;
					colorDialog.closeDialog();
				} else {
					fireEditingStopped();
				}
				return true;
			}

			@Override
			public void cancelCellEditing() {
				if (colorDialog!=null) {
					editingCanceled = true;
					colorDialog.closeDialog();
				} else {
					fireEditingCanceled();
				}
			}

			@Override
			protected void fireEditingStopped() {
				System.out.println("fireEditingStopped");
				super.fireEditingStopped();
			}

			@Override
			protected void fireEditingCanceled() {
				System.out.println("fireEditingCanceled");
				super.fireEditingCanceled();
			}

			@Override
			public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
				setColor(table, value, isSelected);
				if (value instanceof Color) {
					oldColor = (Color) value;
					newColor = oldColor;
					colorDialog = new HSColorChooser.ColorDialog(DiskItemTypeDialog.this, "Select Color", false, newColor);
					SwingUtilities.invokeLater(()->{
						editingCanceled = false;
						colorDialog.showDialog();
						newColor = colorDialog.getColor();
						colorDialog=null;
						if (editingCanceled || newColor==null) {
							newColor = oldColor;
							fireEditingCanceled();;
						} else {
							fireEditingStopped();
						}
					});
				}
				return renderComp;
			}

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
				setColor(table, value, isSelected);
				return renderComp;
			}

			private void setColor(JTable table, Object value, boolean isSelected) {
				if (value instanceof Color) {
					renderComp.setOpaque(true);
					renderComp.setBackground((Color) value);
				} else if (isSelected) {
					renderComp.setOpaque(true);
					renderComp.setBackground(table.getSelectionBackground());
				} else {
					renderComp.setOpaque(false);
					renderComp.setBackground(null);
				}
			}
			
		}
		
		enum ColumnID implements Tables.SimplifiedColumnIDInterface {
			Label("Label"                  ,String.class, 30, 200),
			Color("Color"                  , Color.class, 30,  50),
			Ext  ("List of File Extensions",String.class, 30, 500),
			;
			
			private Tables.SimplifiedColumnConfig cfg;
			ColumnID(String name, Class<?> columnClass, int minWidth, int width) {
				cfg = new Tables.SimplifiedColumnConfig(name, columnClass, minWidth, -1, width, width);
			}
			@Override public Tables.SimplifiedColumnConfig getColumnConfig() { return cfg; }
			
		}
		private class DiskItemTypeTableModel extends Tables.SimplifiedTableModel<ColumnID> {

			protected DiskItemTypeTableModel() {
				super(ColumnID.values());
			}

			public int getSumOfColumnWidths() {
				int sum = 0;
				for (ColumnID id:ColumnID.values())
					sum += id.cfg.currentWidth;
				return sum;
			}

			@Override
			public int getRowCount() {
				return DiskItemType.types.size()+1;
			}

			@Override
			protected boolean isCellEditable(int rowIndex, int columnIndex, ColumnID columnID) {
				if (rowIndex<DiskItemType.types.size()) return true;
				return columnID==ColumnID.Label;
			}

			@Override
			public Object getValueAt(int rowIndex, int columnIndex, ColumnID columnID) {
				if (rowIndex<DiskItemType.types.size()) {
					DiskItemType diskItemType = DiskItemType.types.get(rowIndex);
					switch (columnID) {
					case Label: return diskItemType.label;
					case Color: return diskItemType.color;
					case Ext  : return DiskItemType.toString(diskItemType.fileExtensions);
					}
				}
				return null;
			}

			@Override
			protected void setValueAt(Object aValue, int rowIndex, int columnIndex, ColumnID columnID) {
				DiskItemType diskItemType;
				if (rowIndex<DiskItemType.types.size()) {
					diskItemType = DiskItemType.types.get(rowIndex);
				} else {
					DiskItemType.types.add(diskItemType = new DiskItemType("", Color.GRAY));
					fireTableRowAdded(DiskItemType.types.size()-1);
				}
				switch (columnID) {
				case Label: diskItemType.label = (String)aValue; break;
				case Color: diskItemType.color = (Color)aValue; break;
				case Ext  : diskItemType.fileExtensions = DiskItemType.toStrArray((String)aValue); break;
				}
			}
		}
	}

	@Override
	public void showHighlightedMapItem(DiskItem diskItem) {
		fileMapPanel.setHighlightedPath(diskItem);
	}

	@Override
	public void copyPathToClipBoard(DiskItem diskItem) {
		Properties prop = System.getProperties();
		String file_separator = prop.get("file.separator").toString();
		String pathStr = diskItem.getPathStr(file_separator);
		if (pathStr!=null) copyToClipBoard(pathStr);
	}

	@Override
	public boolean copyToClipBoard(String str) {
		return ClipboardTools.copyToClipBoard(str);
	}

	@Override
	public void expandPathInTree(DiskItem diskItems) {
		treePanel.expandPathInTree(diskItems);
	}
	
	@Override public void saveConfig() { Config.saveConfig(); }
	
	private static class Config {
		private static final String HEADER_CUSHIONPAINTER = "[FileMap.Painter.CushionPainter]";
		private static final String VALUE_CUSHIONPAINTER_SUN = "sun";
		private static final String VALUE_CUSHIONPAINTER_SELECTED_FILE = "selectedFile";
		private static final String VALUE_CUSHIONPAINTER_SELECTED_FOLDER = "selectedFolder";
		private static final String VALUE_CUSHIONPAINTER_MATERIAL_COLOR = "materialColor";
		private static final String VALUE_CUSHIONPAINTER_MATERIAL_PHONG_EXP = "materialPhongExp";
		private static final String VALUE_CUSHIONPAINTER_CUSHIONCONTOUR = "cushionContour";
		private static final String VALUE_CUSHIONPAINTER_CUSHIONESS = "cushioness";
		private static final String VALUE_CUSHIONPAINTER_CUSHION_HEIGHT_SCALE = "cushionHeightScale";
		private static final String VALUE_CUSHIONPAINTER_AUTOMATIC_SAVING = "automaticSaving";
		
		private static final String HEADER_DISKITEMTYPE = "[FileType]";
		private static final String VALUE_DISKITEMTYPE_LABEL          = "label";
		private static final String VALUE_DISKITEMTYPE_COLOR          = "color";
		private static final String VALUE_DISKITEMTYPE_FILEEXTENSIONS = "extensions";
		
		enum ConfigBlock { CushionPainter, DiskItemType }
		
		public static void saveConfig() {
			System.out.print("Write Config ... ");
			try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(CONFIG_FILE), StandardCharsets.UTF_8))) {
				
				
				for (DiskItemType type:DiskItemType.types) {
					out.println(HEADER_DISKITEMTYPE);
					out.printf(VALUE_DISKITEMTYPE_LABEL         +"=%s%n",type.label);
					out.printf(VALUE_DISKITEMTYPE_COLOR         +"=%s%n",toString(type.color));
					out.printf(VALUE_DISKITEMTYPE_FILEEXTENSIONS+"=%s%n",DiskItemType.toString(type.fileExtensions));
					out.println();
				}
				
				Painter.CushionPainter.Config config = Painter.CushionPainter.config;
				out.println(HEADER_CUSHIONPAINTER);
				out.printf(VALUE_CUSHIONPAINTER_SUN                 +"=%s%n",toString(config.sun               ));
				out.printf(VALUE_CUSHIONPAINTER_SELECTED_FILE       +"=%s%n",toString(config.selectedFile      ));
				out.printf(VALUE_CUSHIONPAINTER_SELECTED_FOLDER     +"=%s%n",toString(config.selectedFolder    ));
				out.printf(VALUE_CUSHIONPAINTER_MATERIAL_COLOR      +"=%s%n",toString(config.materialColor     ));
				out.printf(VALUE_CUSHIONPAINTER_MATERIAL_PHONG_EXP  +"=%s%n",toString(config.materialPhongExp  ));
				out.printf(VALUE_CUSHIONPAINTER_CUSHIONCONTOUR      +"=%s%n",toString(config.cushionContour    ));
				out.printf(VALUE_CUSHIONPAINTER_CUSHIONESS          +"=%s%n",toString(config.cushioness        ));
				out.printf(VALUE_CUSHIONPAINTER_CUSHION_HEIGHT_SCALE+"=%s%n",toString(config.cushionHeightScale));
				out.printf(VALUE_CUSHIONPAINTER_AUTOMATIC_SAVING    +"=%s%n",toString(config.automaticSaving   ));
				out.println();
				
			}
			catch (FileNotFoundException e) { e.printStackTrace(); }
			System.out.println("done");
		}
		public static void readConfig() {
			System.out.print("Read Config ... ");
			ConfigBlock currentConfigBlock = null;
			DiskItemType type = null;
			boolean isFirstDiskItemType = true;
			try (BufferedReader in=new BufferedReader(new InputStreamReader(new FileInputStream(CONFIG_FILE), StandardCharsets.UTF_8))) {
				String str; int lineCount = 0;
				while ( (str=in.readLine())!=null ) {
					try {
						
						if (str.equals(HEADER_DISKITEMTYPE)) {
							currentConfigBlock = ConfigBlock.DiskItemType;
							if (isFirstDiskItemType) {
								DiskItemType.types.clear();
								isFirstDiskItemType = false;
							}
							type = new DiskItemType("", Color.GRAY);
							DiskItemType.types.add(type);
						}
						if (currentConfigBlock == ConfigBlock.DiskItemType && type != null) {
							if (str.startsWith(VALUE_DISKITEMTYPE_LABEL         +"=")) type.label          =                          str.substring(VALUE_DISKITEMTYPE_LABEL         .length()+1)  ;
							if (str.startsWith(VALUE_DISKITEMTYPE_COLOR         +"=")) type.color          = parseColor             ( str.substring(VALUE_DISKITEMTYPE_COLOR         .length()+1) );
							if (str.startsWith(VALUE_DISKITEMTYPE_FILEEXTENSIONS+"=")) type.fileExtensions = DiskItemType.toStrArray( str.substring(VALUE_DISKITEMTYPE_FILEEXTENSIONS.length()+1) );
							if (str.isEmpty()) { currentConfigBlock = null; type = null; }
						}
						
						if (str.equals(HEADER_CUSHIONPAINTER)) currentConfigBlock = ConfigBlock.CushionPainter;
						if (currentConfigBlock == ConfigBlock.CushionPainter) {
							Painter.CushionPainter.Config config = Painter.CushionPainter.config;
							if (str.startsWith(VALUE_CUSHIONPAINTER_SUN                 +"=")) config.sun                = parseNormal( str.substring(VALUE_CUSHIONPAINTER_SUN                 .length()+1) );
							if (str.startsWith(VALUE_CUSHIONPAINTER_SELECTED_FILE       +"=")) config.selectedFile       = parseColor ( str.substring(VALUE_CUSHIONPAINTER_SELECTED_FILE       .length()+1) );
							if (str.startsWith(VALUE_CUSHIONPAINTER_SELECTED_FOLDER     +"=")) config.selectedFolder     = parseColor ( str.substring(VALUE_CUSHIONPAINTER_SELECTED_FOLDER     .length()+1) );
							if (str.startsWith(VALUE_CUSHIONPAINTER_MATERIAL_COLOR      +"=")) config.materialColor      = parseColor ( str.substring(VALUE_CUSHIONPAINTER_MATERIAL_COLOR      .length()+1) );
							if (str.startsWith(VALUE_CUSHIONPAINTER_MATERIAL_PHONG_EXP  +"=")) config.materialPhongExp   = parseDouble( str.substring(VALUE_CUSHIONPAINTER_MATERIAL_PHONG_EXP  .length()+1) );
							if (str.startsWith(VALUE_CUSHIONPAINTER_CUSHIONCONTOUR      +"=")) config.cushionContour     = parseEnum  ( str.substring(VALUE_CUSHIONPAINTER_CUSHIONCONTOUR      .length()+1), Painter.CushionPainter.CushionContour.Variant::valueOf );
							if (str.startsWith(VALUE_CUSHIONPAINTER_CUSHIONESS          +"=")) config.cushioness         = parseDouble( str.substring(VALUE_CUSHIONPAINTER_CUSHIONESS          .length()+1) );
							if (str.startsWith(VALUE_CUSHIONPAINTER_CUSHION_HEIGHT_SCALE+"=")) config.cushionHeightScale = parseFloat ( str.substring(VALUE_CUSHIONPAINTER_CUSHION_HEIGHT_SCALE.length()+1) );
							if (str.startsWith(VALUE_CUSHIONPAINTER_AUTOMATIC_SAVING    +"=")) config.automaticSaving    = parseBool  ( str.substring(VALUE_CUSHIONPAINTER_AUTOMATIC_SAVING    .length()+1) );
							if (str.isEmpty()) currentConfigBlock = null;
						}
						
					} catch (ParseException e) {
						System.err.printf("%nParseException while parsing line %d: \"%s\"%n", lineCount, str);
						e.printStackTrace(System.err);
					}
					lineCount++;
				}
			}
			catch (FileNotFoundException e) {}
			catch (IOException e) { e.printStackTrace(); }
			System.out.println("done");
		}
		
		private static class ParseException extends Exception {
			private static final long serialVersionUID = -2041201413914094651L;
			
			ParseException(String format, Object...args) {
				super(String.format(Locale.ENGLISH, format, args));
			}
			@SuppressWarnings("unused")
			ParseException(Throwable t, String format, Object...args) {
				super(String.format(Locale.ENGLISH, format, args),t);
			}
		}
		private static boolean parseBool(String str) {
			return str.toLowerCase().equals("true");
		}
		private static float parseFloat(String str) throws ParseException {
			try { return Float.parseFloat(str); }
			catch (NumberFormatException e) { throw new ParseException("Can't parse float value: \"%s\"", str); }
		}
		private static double parseDouble(String str) throws ParseException {
			try { return Double.parseDouble(str); }
			catch (NumberFormatException e) { throw new ParseException("Can't parse double value: \"%s\"", str); }
		}
		private static Color parseColor(String str) throws ParseException {
			try { return new Color(Integer.parseInt(str,16));}
			catch (NumberFormatException e) { throw new ParseException("Can't parse Color value: \"%s\"", str); }
		}
		private static Normal parseNormal(String str) throws ParseException {
			String[] strs = str.split(",");
			if (strs.length!=3) return null;
			try {
				return new Normal(
						Double.parseDouble(strs[0]),
						Double.parseDouble(strs[1]),
						Double.parseDouble(strs[2])
						);
			} catch (NumberFormatException e) {
				 throw new ParseException("Can't parse Normal value: \"%s\"", str); 
			}
		}
		private static String toString(boolean b     ) { return b?"true":"false"; }
		private static String toString(double  d     ) { return String.format(Locale.ENGLISH, "%1.6f", d); }
		private static String toString(Color   color ) { return String.format("%06X", color.getRGB()&0xFFFFFF); }
		private static String toString(Normal  normal) { return String.format(Locale.ENGLISH, "%1.6f,%1.6f,%1.6f", normal.x, normal.y, normal.z); }
		
		private static <E extends Enum<E>> E parseEnum(String str, Function<String,E> valueOf) {
			try { return valueOf.apply(str); }
			catch (Exception e) { return null; }
		}
		private static <E extends Enum<E>> String toString(E e) {
			return e==null?"<null>":e.name();
		}
	}
	
	private static class ProgressAbortedException extends Exception {
		private static final long serialVersionUID = 7818542051945043168L;
	}

	public record ImportedFileData(DiskItem root, File source) {}
	
	private boolean importFileData(Function<Window,ImportedFileData> readFcn) {
		ImportedFileData rd = readFcn.apply(mainWindow);
		if (rd==null) return false;
		
		root = rd.root;
		treePanel.rootChanged(rd.source);
		fileMapPanel.rootChanged();
		return true;
	}

	private boolean scanFolder    () { return importFileData(window -> scanFolder    (window, "Select Folder"          , false, null)); }
	private boolean openStoredTree() { return importFileData(window -> openStoredTree(window, "Select Stored Tree File",        null)); }

	public static ImportedFileData scanFolder(Window window, String fchTitle, Boolean excludeRootFolder, String targetName) {
		File selectedFolder = selectFolder(window, fchTitle);
		if (selectedFolder == null) return null;
		
		boolean followSymbolicLinks;
		{
			int res = JOptionPane.showConfirmDialog(window, "Follow symbolic links?", "Symbolic Links", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
			if (res!=JOptionPane.YES_OPTION && res!=JOptionPane.NO_OPTION) return null;
			followSymbolicLinks = res==JOptionPane.YES_OPTION;
		}
		
		if (excludeRootFolder==null) {
			int res = JOptionPane.showConfirmDialog(window, "Exclude RootFolder from resulting tree?", "Exclude RootFolder", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
			if (res!=JOptionPane.YES_OPTION && res!=JOptionPane.NO_OPTION) return null;
			excludeRootFolder = res==JOptionPane.YES_OPTION;
		}
		
		return scanFolder(window, selectedFolder, followSymbolicLinks, excludeRootFolder, targetName);
	}

	public static ImportedFileData openStoredTree(Window window, String fchTitle, String targetName) {
		File selectedFile = selectStoredTreeFileToOpen(window, fchTitle);
		if (selectedFile == null) return null;
		
		return openStoredTree(window, selectedFile, targetName);
	}

	public static File selectFolder(Window window, String fchTitle) {
		return selectFileToOpen(window, fchTitle, folderChooser);
	}

	public static File selectStoredTreeFileToOpen(Window window, String fchTitle) {
		return selectFileToOpen(window, fchTitle, storedTreeChooser);
	}

	public static File selectStoredTreeFileToSave(Window window, String fchTitle) {
		return selectFileToSave(window, fchTitle, storedTreeChooser);
	}

	private static File selectFileToOpen(Window window, String fchTitle, JFileChooser fileChooser) {
		fileChooser.setDialogTitle(fchTitle);
		if (fileChooser.showOpenDialog(window) != FileChooser.APPROVE_OPTION) return null;
		return fileChooser.getSelectedFile();
	}

	private static File selectFileToSave(Window window, String fchTitle, JFileChooser fileChooser) {
		fileChooser.setDialogTitle(fchTitle);
		if (fileChooser.showSaveDialog(window) != FileChooser.APPROVE_OPTION) return null;
		return fileChooser.getSelectedFile();
	}

	public static ImportedFileData scanFolder(Window window, File selectedFolder, boolean followSymbolicLinks, boolean excludeRootFolder, String targetName) {
		String dlgTitle = "Scan Folder";
		if (targetName!=null) dlgTitle += " for "+targetName;
		
		DiskItem newRoot = ProgressDialog.runWithProgressDialogRV(window, dlgTitle, 500, pd->{
			long startTime = System.currentTimeMillis();
			
			DiskItem root = scanFolder_core(pd, selectedFolder, followSymbolicLinks, excludeRootFolder);
			
			String durationStr_ms = DateTimeFormatter.getDurationStr_ms(System.currentTimeMillis()-startTime);
			if (targetName!=null)
				System.out.printf("Folder \"%s\" scanned%n   for \"%s\"%n   in %s.%n", selectedFolder, targetName, durationStr_ms);
			else
				System.out.printf("Folder \"%s\" scanned%n   in %s.%n", selectedFolder, durationStr_ms);
			
			return root;
		});
		if (newRoot==null) return null;
		
		return new ImportedFileData(newRoot, selectedFolder);
	}

	public static ImportedFileData openStoredTree(Window window, File selectedFile, String targetName) {
		String dlgTitle = "Read Stored Tree";
		if (targetName!=null) dlgTitle += " for "+targetName;
		
		DiskItem newRoot = ProgressDialog.runWithProgressDialogRV(window, dlgTitle, 300, pd->{
			long startTime = System.currentTimeMillis();
			
			DiskItem root = openStoredTree_core(pd, selectedFile);
			
			String durationStr_ms = DateTimeFormatter.getDurationStr_ms(System.currentTimeMillis()-startTime);
			if (targetName!=null)
				System.out.printf("StoredTree read%n   from file \"%s\"%n   for \"%s\"%n   in %s.%n", selectedFile, targetName, durationStr_ms);
			else
				System.out.printf("StoredTree read%n   from file \"%s\"%n   in %s.%n", selectedFile, durationStr_ms);
			
			return root;
			
		});
		if (newRoot==null) return null;
		
		return new ImportedFileData(newRoot, selectedFile);
	}

	private static DiskItem scanFolder_core(ProgressDialog pd, File selectedfolder, boolean followSymbolicLinks, boolean excludeRootFolder) {
		SwingUtilities.invokeLater(()->{
			pd.setTaskTitle("Scan Folder");
			pd.setIndeterminate(true);
		});
		
		DiskItem newRoot = new DiskItem();
		
		DiskItem folderDI = excludeRootFolder ? newRoot : newRoot.addChild(selectedfolder,followSymbolicLinks);
		SwingUtilities.invokeLater(()->{
			pd.setValue(0, 10000);
		});
		try {
			folderDI.addChildren(pd,0,10000,selectedfolder,followSymbolicLinks);
		} catch (ProgressAbortedException e) {
			newRoot = null;
		}
		
		if (newRoot!=null) {
			SwingUtilities.invokeLater(()->{
				pd.setTaskTitle("Determine File Types");
				pd.setIndeterminate(true);
			});
			
			DiskItemType.setTypes(newRoot);
		}
		return newRoot;
	}

	private static DiskItem openStoredTree_core(ProgressDialog pd, File file) {
		SwingUtilities.invokeLater(()->{
			pd.setTaskTitle("Read Stored Tree File");
			pd.setIndeterminate(true);
		});
		
		List<String> lines = StoredTreeIO.readFile(file, ()->Thread.currentThread().isInterrupted());
		if (lines==null)
		{
			if (!Thread.currentThread().isInterrupted())
				System.err.printf("Can't read file \"%s\".%n", file.getAbsolutePath());
			return null;
		}
		
		DiskItem newRoot = parseLines(pd, lines, true);
		
		if (newRoot!=null) {
			SwingUtilities.invokeLater(()->{
				pd.setTaskTitle("Determine File Types");
				pd.setIndeterminate(true);
			});
			
			DiskItemType.setTypes(newRoot);
		}
		return newRoot;
	}

	static DiskItem parseLines(ProgressDialog pd, List<String> lines, boolean withDiskItemCache)
	{
		SwingUtilities.invokeLater(()->{
			pd.setTaskTitle("Parse Lines of Stored Tree File");
			pd.setValue(0, lines.size());
		});
		
		HashMap<String,DiskItem> cache = withDiskItemCache ? new HashMap<>() : null;
		int i=0;
		DiskItem newRoot = new DiskItem();
		for (String line : lines) {
			if (line.isBlank()) continue;
			if (Thread.currentThread().isInterrupted()) { newRoot = null; break; }
			int pos = line.indexOf(0x9);
			long size_kB = Long.parseLong(line.substring(0,pos));
			String pathStr = line.substring(pos+1);
			DiskItem item = getDiskItem(newRoot, pathStr, cache);
			item.size_kB = size_kB;
			final int index = ++i;
			SwingUtilities.invokeLater(() -> pd.setValue(index));
		}
		return newRoot;
	}

	private static DiskItem getDiskItem(DiskItem root, String pathStr, HashMap<String, DiskItem> cache)
	{
		if (cache==null)
			return root.get(pathStr.split(PATH_CHAR));
		
		return getDiskItemFromCache(root, pathStr, cache);
	}

	private static DiskItem getDiskItemFromCache(DiskItem root, final String pathStr, HashMap<String, DiskItem> cache)
	{
		DiskItem item;
		item = cache.get(pathStr);
		
		if (item == null)
		{
			String name;
			DiskItem parent;
			
			int pos = pathStr.lastIndexOf(PATH_CHAR);
			if (pos<0)
			{
				name = pathStr;
				parent = root;
			}
			else
			{
				name = pathStr.substring(pos+1);
				String parentPath = pathStr.substring(0, pos);
				parent = getDiskItemFromCache(root, parentPath, cache);
			}
			
			item = parent.createChild(name);
			cache.put(pathStr, item);
		}
		
		return item;
	}

	private void saveStoredTree() {
		saveStoredTree(mainWindow, "Select Stored Tree File", root);
	}

	public static void saveStoredTree(Window window, String dlgTitle, DiskItem root) {
		if (root == null) return;
		File selectedFile = selectStoredTreeFileToSave(window, dlgTitle);
		if (selectedFile == null) return;
		saveStoredTree(window, root, selectedFile);
	}

	public static void saveStoredTree(Window window, DiskItem root, File selectedFile)
	{
		ProgressDialog.runWithProgressDialog(window, "Write tree to file", 300, pd->{
			long startTime = System.currentTimeMillis();
			
			SwingUtilities.invokeLater(()->{
				pd.setTaskTitle("Traverse tree");
				pd.setIndeterminate(true);
			});
			
			Vector<String> lines = new Vector<>();
			boolean wasNotInterrupted = root.traverse(Thread.currentThread()::isInterrupted, true,(DiskItem di)->{
				if (di==root) return;
				lines.add(String.format("%d\t%s", di.size_kB, di.getPathStr(PATH_CHAR)));
			});
			if (!wasNotInterrupted) { System.out.printf("Writing of StoredTree aborted.%n"); return; }
			
			SwingUtilities.invokeLater(()->{
				pd.setTaskTitle("Write lines to file");
				pd.setIndeterminate(true);
			});
			
			StoredTreeIO.writeFile(selectedFile, lines, Thread.currentThread()::isInterrupted);
			if (Thread.currentThread().isInterrupted()) { System.out.printf("Writing of StoredTree aborted.%n"); return; }
			
			String durationStr_ms = DateTimeFormatter.getDurationStr_ms(System.currentTimeMillis()-startTime);
			System.out.printf("Tree written%n   into file \"%s\"%n   in %s.%n", selectedFile, durationStr_ms);
		});
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
					Comparator.<DiskItemTreeNode,DiskItem>comparing(ditn->ditn.diskItem,Comparator.<DiskItem,Long>comparing(di->di.size_kB,Comparator.reverseOrder()).thenComparing(di->di.name))
			);
		}

		@Override public String toString() { return diskItem.toString(); }

		@Override public Enumeration<DiskItemTreeNode> children() {
			if (children==null) createChildren();
			return new Enumeration<>() {
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
	
	static class DiskItemType {
		static Vector<DiskItemType> types;
		static {
			types = new Vector<>();
			types.add(new DiskItemType("Video"             , new Color(0x7F0000), "mp4","mpg","mpeg","wmv","webm","stream","vob"));
			types.add(new DiskItemType("Transport Stream"  , new Color(0xad3d00), "ts" ));
			types.add(new DiskItemType("Audio"             , new Color(0x237f00), "wav","mp3","ac3"));
			types.add(new DiskItemType("Archive"           , new Color(0xff6d00), "zip","7z","tar.gz","tar","gz","rar","iso","pak"));
			types.add(new DiskItemType("Text"              , new Color(0x6868a3), "eit", "txt"));
			types.add(new DiskItemType("Image"             , new Color(0x5656ff), "png","jpg", "jpeg"));
		}
		
		public static DiskItemType getType(String filename) {
			filename = filename.toLowerCase();
			for (DiskItemType dit:types)
				for (String ext:dit.fileExtensions)
					if (filename.endsWith("."+ext))
						return dit;
			return null;
		}
		public static void setTypes(DiskItem diskItem) {
			if (diskItem!=null)
				diskItem.traverse(null,false,di->{
					if (di.children.isEmpty())
						di.type = DiskItemType.getType(di.name);
				});
		}

		public static String toString(String[] strs) {
			String result = "";
			for (String str:strs) {
				if (!result.isEmpty()) result+=", ";
				result+=str;
			}
			return result;
		}

		public static String[] toStrArray(String strs) {
			if (strs.isEmpty()) return new String[0];
			return Arrays
					.asList(strs.split(","))
					.stream()
					.map(str->str.trim())
					.filter(str->!str.isEmpty())
					.toArray(n->new String[n]);
		}

		String label;
		Color color;
		String[] fileExtensions;
		DiskItemType(String label, Color color, String... fileExtensions) {
			this.label = label;
			this.color = color;
			this.fileExtensions = fileExtensions;
		}
	}

	public static class DiskItem {

		final DiskItem parent;
		public final Vector<DiskItem> children;
		public final String name; 
		public long size_kB = 0;
		DiskItemType type = null;

		public DiskItem() { this(null,"<root>"); }
		private DiskItem(DiskItem parent, String name) {
			this.parent = parent;
			this.name = name;
			children = new Vector<>();
		}
		public Color getColor() {
			return type==null?null:type.color;
		}
		
		public boolean isChildOf(DiskItem diskItem) {
			if (this==diskItem) return true;
			if (parent==null) return false;
			return parent.isChildOf(diskItem);
		}
		
		public String getPathStr(String glue) {
			if (parent == null) return null;
			String pathStr = parent.getPathStr(glue);
			if (pathStr==null) return name;
			return pathStr+glue+name;
		}
		
		@Override
		public String toString() {
			//return name + " (" + size + "kB)";
			//return String.format(Locale.GERMAN, "[ %,d kB ]   %s", toString(size), name);
			return String.format("[ %s ]   %s", getSizeStr(), name);
		}
		
		public String getSizeStr() {
			return getSizeStr(size_kB);
		}
		public static String getSizeStr(long size_kB) {
			String sign = "";
			if (size_kB<0) { sign = "-"; size_kB = -size_kB; }
			if (size_kB<1024) return String.format("%s%d kB", sign, size_kB);
			double sizeD;
			sizeD = size_kB/1024.0; if (sizeD<1024) return String.format(Locale.ENGLISH, "%s%1.2f MB", sign, sizeD);
			sizeD = sizeD/1024;     if (sizeD<1024) return String.format(Locale.ENGLISH, "%s%1.2f GB", sign, sizeD);
			sizeD = sizeD/1024;                     return String.format(Locale.ENGLISH, "%s%1.2f TB", sign, sizeD);
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
				child = createChild(path[i]);
			return child.getChild(path, i+1);
		}
		
		private DiskItem createChild(String name) {
			DiskItem child = new DiskItem(this,name);
			children.add(child);
			return child;
		}
		
		private boolean isSymbolicLink(File file) {
			Path path;
			try { path = file.toPath(); }
			catch (Exception e) {
				System.err.printf("Method \"isSymbolicLink\": Can't convert \"%s\" to Path. -> decide to \"no\"", file);
				return false;
			}
			return Files.isSymbolicLink(path);
		}
		public DiskItem addChild(File file, boolean followSymbolicLinks) {
			DiskItem child = createChild(file.getName());
			boolean isSymbolicLink = !followSymbolicLinks && isSymbolicLink(file);
			child.size_kB = isSymbolicLink ? 0 : (long) Math.ceil(file.length()/1024.0);
			return child;
		}
		public void addChildren(ProgressDialog pd, double pdMin, double pdMax, File folder, boolean followSymbolicLinks) throws ProgressAbortedException {
			if (Thread.currentThread().isInterrupted())
				throw new ProgressAbortedException();
			
			SwingUtilities.invokeLater(()->{
				pd.setTaskTitle(folder.getAbsolutePath());
			});
			if (!followSymbolicLinks && isSymbolicLink(folder))
				return;
			
			File[] files = folder.listFiles((FileFilter) file -> {
				if (file.isDirectory()) {
					if (file.getName().equals(".")) return false;
					if (file.getName().equals("..")) return false;
				}
				return true;
			});
			if (files!=null && files.length>0) {
				double pdStep = (pdMax-pdMin)/files.length;
				double pdPos = pdMin;
				for (File file:files) {
					DiskItem child = addChild(file,followSymbolicLinks);
					if (file.isDirectory()) {
						child.addChildren(pd,pdPos,pdPos+pdStep,file,followSymbolicLinks);
						SwingUtilities.invokeLater(()->{
							pd.setTaskTitle(folder.getAbsolutePath());
						});
					}
					pdPos+=pdStep;
					int pdPos_ = (int) Math.round(pdPos);
					SwingUtilities.invokeLater(()->{
						pd.setValue(pdPos_);
					});
				}
			}
			size_kB += sumSizeOfChildren();
		}
		public long sumSizeOfChildren() {
			long sum = 0;
			for (DiskItem child:children)
				sum += child.size_kB;
			return sum;
		}
		
		public boolean traverse(Supplier<Boolean> shouldAbort, boolean childrenFirst, Consumer<DiskItem> consumer) {
			if (shouldAbort!=null && shouldAbort.get()) return false;
			if (!childrenFirst) consumer.accept(this);
			for (DiskItem child:children) {
				if (shouldAbort!=null && shouldAbort.get()) return false;
				boolean wasNotAborted = child.traverse(shouldAbort,childrenFirst,consumer);
				if (!wasNotAborted) return false;
			}
			if ( childrenFirst) consumer.accept(this);
			return true;
		}
	}

	static class GBC {
		enum GridFill {
			BOTH(GridBagConstraints.BOTH),
			HORIZONTAL(GridBagConstraints.HORIZONTAL),
			H(GridBagConstraints.HORIZONTAL),
			VERTICAL(GridBagConstraints.VERTICAL),
			V(GridBagConstraints.VERTICAL),
			NONE(GridBagConstraints.NONE),
			;
			private int value;
			GridFill(int value) {
				this.value = value;
				
			}
		}
	
		static void reset(GridBagConstraints c) {
			c.gridx = GridBagConstraints.RELATIVE;
			c.gridy = GridBagConstraints.RELATIVE;
			c.weightx = 0;
			c.weighty = 0;
			c.fill = GridBagConstraints.NONE;
			c.gridwidth = 1;
			c.insets = new Insets(0,0,0,0);
		}
	
		static GridBagConstraints setWeights(GridBagConstraints c, double weightx, double weighty) {
			c.weightx = weightx;
			c.weighty = weighty;
			return c;
		}
	
		static GridBagConstraints setGridPos(GridBagConstraints c, int gridx, int gridy) {
			c.gridx = gridx;
			c.gridy = gridy;
			return c;
		}
	
		static GridBagConstraints setFill(GridBagConstraints c, GridFill fill) {
			c.fill = fill==null?GridBagConstraints.NONE:fill.value;
			return c;
		}
	
		static GridBagConstraints setInsets(GridBagConstraints c, int top, int left, int bottom, int right) {
			c.insets = new Insets(top, left, bottom, right);
			return c;
		}
	
		static GridBagConstraints setLineEnd(GridBagConstraints c) {
			c.gridwidth = GridBagConstraints.REMAINDER;
			return c;
		}
	
		static GridBagConstraints setLineMid(GridBagConstraints c) {
			c.gridwidth = 1;
			return c;
		}
	
		static GridBagConstraints setGridWidth(GridBagConstraints c, int gridwidth) {
			c.gridwidth = gridwidth;
			return c;
		}
	}
}
