package net.schwarzbaer.java.tools.diskusage;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.BufferedReader;
import java.io.File;
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
import java.util.Vector;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.image.BumpMapping.Normal;

public class DiskUsage implements FileMap.GuiContext {

	private static final String CONFIG_FILE = "DiskUsage.cfg";

	public static void main(String[] args) {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		
		File file = new File("hdd.diskusage");
		new DiskUsage().readConfig().readFile(file).createGUI();
	}
	
	private DiskItem root;
	private DiskItemTreeNode rootTreeNode;
	private JTree treeView;
	private FileMap fileMap;
	private ContextMenu treeViewContextMenu;
	private DiskItem fileMapRoot;

	private void createGUI() {
		rootTreeNode = new DiskItemTreeNode(root);
		treeView = new JTree(rootTreeNode);
		treeView.setCellRenderer(new MyTreeCellRenderer());
		treeView.addMouseListener(new MyMouseListener());
		treeView.addTreeSelectionListener(e -> {
			TreePath treePath = treeView.getSelectionPath();
			if (treePath==null) return;
			Object object = treePath.getLastPathComponent();
			if (object instanceof DiskItemTreeNode) {
				DiskItemTreeNode treeNode = (DiskItemTreeNode) object;
				fileMap.setSelected(treeNode.diskItem);
			}
		});
		treeViewContextMenu = new ContextMenu();
		
		fileMapRoot = root;
		fileMap = new FileMap(fileMapRoot,1000,800,this);
		fileMap.setBorder(BorderFactory.createTitledBorder("File Map"));
		fileMap.setPreferredSize(new Dimension(1000,800));
		
		JScrollPane treeScrollPane = new JScrollPane(treeView);
		treeScrollPane.setBorder(BorderFactory.createTitledBorder("Folder Structure"));
		treeScrollPane.setPreferredSize(new Dimension(500,800));
		
		JPanel contentPane = new JPanel(new BorderLayout(3,3));
		contentPane.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		contentPane.add(treeScrollPane,BorderLayout.WEST);
		contentPane.add(fileMap,BorderLayout.CENTER);
		
		StandardMainWindow mainWindow = new StandardMainWindow("DiskUsage");
		mainWindow.startGUI(contentPane);
	}
	
	private class MyTreeCellRenderer extends DefaultTreeCellRenderer {
		private static final long serialVersionUID = -833719497285747612L;

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean isSelected, boolean isExpanded, boolean isLeaf, int row, boolean hasFocus) {
			Component component = super.getTreeCellRendererComponent(tree, value, isSelected, isExpanded, isLeaf, row, hasFocus);
			if (value instanceof DiskItemTreeNode && !isSelected) {
				DiskItemTreeNode treeNode = (DiskItemTreeNode) value;
				if (treeNode.diskItem==fileMapRoot)
					component.setForeground(Color.BLUE);
				else if (treeNode.diskItem.isChildOf(fileMapRoot)) {
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
		
		ContextMenu() {
			add(showInFileMap = createMenuItem("Show in File Map",e->{
				fileMap.setRoot(fileMapRoot = clickedTreeNode.diskItem);
				treeView.repaint();
			}));
//			createCheckBoxMenuItems(pst, Layouter.Type.values(), t->t.title, FileMap.this::setLayouter);
//			add(configureLayouter = createMenuItem("Configure Layouter ...",e->currentLayouter.showConfig(FileMap.this)));
//			addSeparator();
//			createCheckBoxMenuItems(pt , Painter .Type.values(), t->t.title, FileMap.this::setPainter );
//			add(configurePainter = createMenuItem("Configure Painter ...",e->currentPainter.showConfig(FileMap.this)));
		}
		
		public void show(Component invoker, int x, int y, DiskItemTreeNode clickedTreeNode) {
			this.clickedTreeNode = clickedTreeNode;
			showInFileMap.setEnabled(!this.clickedTreeNode.isLeaf());
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

	@Override
	public void expandPathInTree(DiskItem diskItems) {
		DiskItemTreeNode node = rootTreeNode.find(diskItems);
		if (node==null) return;
		TreePath treePath = node.getPath();
		//System.out.println(treePath);
		treeView.setSelectionPath(treePath);
		treeView.scrollPathToVisible(treePath);
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
				
				FileMap.Painter.CushionPainter.Config config = FileMap.Painter.CushionPainter.config;
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
							FileMap.Painter.CushionPainter.Config config = FileMap.Painter.CushionPainter.config;
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
		
		DiskItem.traverse(root,di->{
			if (di.children.isEmpty())
				for (DiskItemType dit:DiskItemType.values())
					for (String ext:dit.fileExtensions)
						if (di.name.toLowerCase().endsWith("."+ext)) {
							di.type = dit;
							return;
						}
		});
		
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
	
	enum DiskItemType {
		mp4   ("MP4 Video"          , new Color(0x7F0000), "mp4"   ),
		mpg   ("MPEG Video"         , new Color(0x7F0000), "mpg", "mpeg"),
		wmv   ("Windows Media Video", new Color(0x00A4AE), "wmv"   ),
		webm  ("WEBM WebVideo"      , new Color(0x005cae), "webm"  ),
		ts    ("Transport Stream"   , new Color(0x0000ff), "ts"    ),
		stream("Video Stream"       , new Color(0x89737b), "stream"),
		vob   ("DVD Video"          , new Color(0xc73c00), "vob"),
		eit   ("Record Description" , new Color(0x6868a3), "eit"   ),
		png   ("PNG Image"          , new Color(0x565600), "png"   ),
		jpg   ("JPEG Image"         , new Color(0x095600), "jpg", "jpeg"),
		;
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
		long size = 0;
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
		
		public static void traverse(DiskItem di, Consumer<DiskItem> consumer) {
			consumer.accept(di);
			for (DiskItem child:di.children)
				traverse(child, consumer);
		}
	}
}
