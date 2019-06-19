package net.schwarzbaer.java.tools.diskusage;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Properties;
import java.util.Vector;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import net.schwarzbaer.gui.FileChooser;
import net.schwarzbaer.gui.GUI;
import net.schwarzbaer.gui.IconSource;
import net.schwarzbaer.gui.IconSource.CachedIcons;
import net.schwarzbaer.gui.ProgressDialog;
import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.image.BumpMapping.Normal;

public class DiskUsage implements FileMap.GuiContext {

	private static final String CONFIG_FILE = "DiskUsage.cfg";

	public static void main(String[] args) {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		
		File file = new File("hdd.diskusage");
		new DiskUsage().readConfig().createGUI().openStoredTree(file);
	}
	
	private DiskItem root;
	private CachedIcons<Icons32> icons32;
	private FileChooser storedTreeChooser;
	private JFileChooser folderChooser;
	private StandardMainWindow mainWindow;
	private TreePanel treePanel;
	private FileMapPanel fileMapPanel;
	
	enum Icons32 { OpenFolder, OpenStoredTree, SaveStoredTree }
	
	private DiskUsage createGUI() {
		IconSource<Icons32> icons32source = new IconSource<Icons32>(32,32);
		icons32source.readIconsFromResource("/icons32.png");
		icons32 = icons32source.cacheIcons(Icons32.values());
		
		storedTreeChooser = new FileChooser("Stored Tree", "diskusage");
		folderChooser = new JFileChooser("./");
		folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		folderChooser.setMultiSelectionEnabled(false);
		
		treePanel = new TreePanel();
		fileMapPanel = new FileMapPanel();
		
		
		JPanel contentPane = new JPanel(new BorderLayout(3,3));
		contentPane.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		contentPane.add(treePanel,BorderLayout.WEST);
		contentPane.add(fileMapPanel,BorderLayout.CENTER);
		
		mainWindow = new StandardMainWindow("DiskUsage");
		mainWindow.startGUI(contentPane);
		
		treePanel.rootChanged(null);
		fileMapPanel.rootChanged();
		
		return this;
	}

	private class TreePanel extends JPanel {
		private static final long serialVersionUID = 4456876243723688978L;
		
		private File treeSource;
		private DiskItemTreeNode rootTreeNode;
		
		private JTextField treeSourceField;
		private JTree treeView;
		private DefaultTreeModel treeViewModel;
		private ContextMenu treeViewContextMenu;
		
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
			add(treeSourceField = GUI.createOutputTextField(getTreeSourceLabel()),GBC.setWeights(c,1,0));
			add(createButton(Icons32.OpenFolder    ,"Select Folder"      ,new Insets(0,0,0,0),e->selectFolder  ()),GBC.setWeights(c,0,0));
			add(createButton(Icons32.OpenStoredTree,"Open Stored Tree"   ,new Insets(0,0,0,0),e->openStoredTree()),c);
			add(createButton(Icons32.SaveStoredTree,"Save as Stored Tree",new Insets(0,0,0,0),e->saveStoredTree()),GBC.setLineEnd(c));
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
	}

	private class FileMapPanel extends JPanel {
		private static final long serialVersionUID = 4060339037904604642L;
		
		private FileMap fileMap;
		private DiskItem fileMapRoot;
		private JTextField fileMapRootPathField;
		
		FileMapPanel() {
			super(new GridBagLayout());
			GridBagConstraints c = new GridBagConstraints();
			
			fileMapRoot = null;
			fileMap = new FileMap(fileMapRoot,1000,800,DiskUsage.this);
			fileMap.setPreferredSize(new Dimension(1000,800));
			
			GBC.reset(c);
			GBC.setLineEnd(c);
			GBC.setFill(c, GBC.GridFill.BOTH);
			setBorder(BorderFactory.createTitledBorder("File Map"));
			add(fileMapRootPathField = GUI.createOutputTextField(getFileMapRootLabel()),GBC.setWeights(c,1,0));
			add(fileMap,GBC.setWeights(c,1,1));
			fileMapRootPathField.setPreferredSize(new Dimension(30,30));
			fileMapRootPathField.setMinimumSize(new Dimension(30,30));
		}
		
		String getFileMapRootLabel() {
			return fileMapRoot==null?null:fileMapRoot.getPathStr("  >  ");
		}

		void setFileMapRoot(DiskItem diskItem) {
			fileMap.setRoot(fileMapRoot = diskItem);
			fileMapRootPathField.setText(getFileMapRootLabel());
		}

		public void rootChanged() {
			if (root!=null && root.children.size()==1)
				setFileMapRoot(root.children.get(0));
			else
				setFileMapRoot(root);
		}
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
		Toolkit toolkit = Toolkit.getDefaultToolkit();
		if (toolkit==null) { return false; }
		Clipboard clipboard = toolkit.getSystemClipboard();
		if (clipboard==null) { return false; }
		
		StringSelection content = new StringSelection(str);
		clipboard.setContents(content,content);
		return true;
	}

	@Override
	public void expandPathInTree(DiskItem diskItems) {
		treePanel.expandPathInTree(diskItems);
	}
	
	public DiskUsage readConfig() { Config.readConfig(); return this;}
	@Override public void saveConfig() { Config.saveConfig(); }
	
	private static class Config {
		private static final String HEADER_CUSHIONPAINTER = "[FileMap.Painter.CushionPainter]";
		private static final String VALUE_CUSHIONPAINTER_SUN = "sun";
		private static final String VALUE_CUSHIONPAINTER_SELECTED_FILE = "selectedFile";
		private static final String VALUE_CUSHIONPAINTER_SELECTED_FOLDER = "selectedFolder";
		private static final String VALUE_CUSHIONPAINTER_MATERIAL_COLOR = "materialColor";
		private static final String VALUE_CUSHIONPAINTER_MATERIAL_PHONG_EXP = "materialPhongExp";
		private static final String VALUE_CUSHIONPAINTER_CUSHIONESS = "cushioness";
		private static final String VALUE_CUSHIONPAINTER_CUSHION_HEIGHT_SCALE = "cushionHeightScale";
		private static final String VALUE_CUSHIONPAINTER_AUTOMATIC_SAVING = "automaticSaving";
		
		enum ConfigBlock { CushionPainter }
		
		public static void saveConfig() {
			System.out.print("Write Config ... ");
			try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(CONFIG_FILE), StandardCharsets.UTF_8))) {
				
				Painter.CushionPainter.Config config = Painter.CushionPainter.config;
				out.println(HEADER_CUSHIONPAINTER);
				out.printf(VALUE_CUSHIONPAINTER_SUN                 +"=%s%n",toString(config.sun               ));
				out.printf(VALUE_CUSHIONPAINTER_SELECTED_FILE       +"=%s%n",toString(config.selectedFile      ));
				out.printf(VALUE_CUSHIONPAINTER_SELECTED_FOLDER     +"=%s%n",toString(config.selectedFolder    ));
				out.printf(VALUE_CUSHIONPAINTER_MATERIAL_COLOR      +"=%s%n",toString(config.materialColor     ));
				out.printf(VALUE_CUSHIONPAINTER_MATERIAL_PHONG_EXP  +"=%s%n",toString(config.materialPhongExp  ));
				out.printf(VALUE_CUSHIONPAINTER_CUSHIONESS          +"=%s%n",toString(config.alpha             ));
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
			try (BufferedReader in=new BufferedReader(new InputStreamReader(new FileInputStream(CONFIG_FILE), StandardCharsets.UTF_8))) {
				String str; int lineCount = 0;
				while ( (str=in.readLine())!=null ) {
					try {
						
						if (str.equals(HEADER_CUSHIONPAINTER)) currentConfigBlock = ConfigBlock.CushionPainter;
						if (currentConfigBlock == ConfigBlock.CushionPainter) {
							Painter.CushionPainter.Config config = Painter.CushionPainter.config;
							if (str.startsWith(VALUE_CUSHIONPAINTER_SUN                 +"=")) config.sun                = parseNormal( str.substring(VALUE_CUSHIONPAINTER_SUN                 .length()+1) );
							if (str.startsWith(VALUE_CUSHIONPAINTER_SELECTED_FILE       +"=")) config.selectedFile       = parseColor ( str.substring(VALUE_CUSHIONPAINTER_SELECTED_FILE       .length()+1) );
							if (str.startsWith(VALUE_CUSHIONPAINTER_SELECTED_FOLDER     +"=")) config.selectedFolder     = parseColor ( str.substring(VALUE_CUSHIONPAINTER_SELECTED_FOLDER     .length()+1) );
							if (str.startsWith(VALUE_CUSHIONPAINTER_MATERIAL_COLOR      +"=")) config.materialColor      = parseColor ( str.substring(VALUE_CUSHIONPAINTER_MATERIAL_COLOR      .length()+1) );
							if (str.startsWith(VALUE_CUSHIONPAINTER_MATERIAL_PHONG_EXP  +"=")) config.materialPhongExp   = parseDouble( str.substring(VALUE_CUSHIONPAINTER_MATERIAL_PHONG_EXP  .length()+1) );
							if (str.startsWith(VALUE_CUSHIONPAINTER_CUSHIONESS          +"=")) config.alpha              = parseDouble( str.substring(VALUE_CUSHIONPAINTER_CUSHIONESS          .length()+1) );
							if (str.startsWith(VALUE_CUSHIONPAINTER_CUSHION_HEIGHT_SCALE+"=")) config.cushionHeightScale = parseFloat ( str.substring(VALUE_CUSHIONPAINTER_CUSHION_HEIGHT_SCALE.length()+1) );
							if (str.startsWith(VALUE_CUSHIONPAINTER_AUTOMATIC_SAVING    +"=")) config.automaticSaving    = parseBool  ( str.substring(VALUE_CUSHIONPAINTER_AUTOMATIC_SAVING    .length()+1) );
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
	}

	private void selectFolder() {
		if (folderChooser.showOpenDialog(mainWindow)==JFileChooser.APPROVE_OPTION) {
			File selectedfolder = folderChooser.getSelectedFile();
			ProgressDialog.runWithProgressDialog(mainWindow, "Read Folder", 500, pd->{
				pd.setTaskTitle("Read Folder");
				pd.setIndeterminate(true);
				
				root = new DiskItem();
				DiskItem folderDI = root.addChild(selectedfolder);
				pd.setValue(0, 10000);
				folderDI.addChildren(pd,0,10000,selectedfolder);
				
				pd.setTaskTitle("Determine File Types");
				pd.setIndeterminate(true);
				
				DiskItemType.setTypes(root);
			});
			treePanel.rootChanged(selectedfolder);
			fileMapPanel.rootChanged();
		}
	}

	private void openStoredTree() {
		if (storedTreeChooser.showOpenDialog(mainWindow)==FileChooser.APPROVE_OPTION) {
			File selectedFile = storedTreeChooser.getSelectedFile();
			openStoredTree(selectedFile);
		}
	}

	private DiskUsage openStoredTree(File selectedFile) {
		ProgressDialog.runWithProgressDialog(mainWindow, "Read Stored Tree", 300, pd->{
			
			pd.setTaskTitle("Read Stored Tree");
			pd.setIndeterminate(true);
			
			root = null;
			try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(selectedFile), StandardCharsets.UTF_8))) {
				root = new DiskItem();
				String line;
				while ( (line=in.readLine())!=null ) {
					int pos = line.indexOf(0x9);
					long size_kB = Long.parseLong(line.substring(0,pos));
					String[] path = line.substring(pos+1).split("/");
					DiskItem item = root.get(path);
					item.size_kB = size_kB;
				}
				
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			pd.setTaskTitle("Determine File Types");
			pd.setIndeterminate(true);
			
			DiskItemType.setTypes(root);
		});
		treePanel.rootChanged(selectedFile);
		fileMapPanel.rootChanged();
		return this;
	}

	private void saveStoredTree() {
		if (storedTreeChooser.showSaveDialog(mainWindow)==FileChooser.APPROVE_OPTION) {
			ProgressDialog.runWithProgressDialog(mainWindow, "Write Stored Tree", 300, pd->{
				
				pd.setTaskTitle("Write Stored Tree");
				pd.setIndeterminate(true);
				
				try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(storedTreeChooser.getSelectedFile()), StandardCharsets.UTF_8))) {
					if (root!=null)
						root.traverse((DiskItem di)->{
							if (di==root) return;
							out.printf("%d\t%s%n", di.size_kB, di.getPathStr("/"));
						});
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
				
			});
		}
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
				diskItem.traverse(di->{
					if (di.children.isEmpty())
						di.type = DiskItemType.getType(di.name);
				});
		}

		final Color color;
		final String[] fileExtensions;
		final String label;
		DiskItemType(String label, Color color, String... fileExtensions) {
			this.label = label;
			this.color = color;
			this.fileExtensions = fileExtensions;
		}
	}

	static class DiskItem {

		final DiskItem parent;
		final String name; 
		long size_kB = 0;
		Vector<DiskItem> children;
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
			if (size_kB<1024) return size_kB+" kB";
			double sizeD;
			sizeD = size_kB/1024.0; if (sizeD<1024) return String.format(Locale.ENGLISH, "%1.2f MB", sizeD);
			sizeD = sizeD/1024;     if (sizeD<1024) return String.format(Locale.ENGLISH, "%1.2f GB", sizeD);
			sizeD = sizeD/1024;                     return String.format(Locale.ENGLISH, "%1.2f TB", sizeD);
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
		
		public DiskItem addChild(File file) {
			DiskItem child = new DiskItem(this,file.getName());
			child.size_kB = (long) Math.ceil(file.length()/1024.0);
			children.add(child);
			return child;
		}
		public void addChildren(ProgressDialog pd, double pdMin, double pdMax, File folder) {
			pd.setTaskTitle(folder.getAbsolutePath());
			
			File[] files = folder.listFiles((FileFilter) file -> {
				if (file.isDirectory()) {
					if (file.getName().equals(".")) return false;
					if (file.getName().equals("..")) return false;
				}
				return true;
			});
			if (files.length>0) {
				double pdStep = (pdMax-pdMin)/files.length;
				double pdPos = pdMin;
				for (File file:files) {
					DiskItem child = addChild(file);
					if (file.isDirectory()) {
						child.addChildren(pd,pdPos,pdPos+pdStep,file);
						pd.setTaskTitle(folder.getAbsolutePath());
					}
					pdPos+=pdStep;
					pd.setValue((int)pdPos);
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
		
		public void traverse(Consumer<DiskItem> consumer) {
			consumer.accept(this);
			for (DiskItem child:children)
				child.traverse(consumer);
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
