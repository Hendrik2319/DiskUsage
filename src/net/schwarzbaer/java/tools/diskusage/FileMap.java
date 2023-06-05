package net.schwarzbaer.java.tools.diskusage;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import net.schwarzbaer.java.lib.gui.Canvas;
import net.schwarzbaer.java.tools.diskusage.DiskUsage.DiskItem;

public class FileMap extends Canvas {
	private static final long serialVersionUID = -41169096975888890L;
	
	interface GuiContext {
		void copyPathToClipBoard(DiskItem diskItem);
		boolean copyToClipBoard(String str);
		void expandPathInTree(DiskItem diskItem);
		void saveConfig();
		void showHighlightedMapItem(DiskItem diskItem);
	}
	
	final GuiContext guiContext;
	private MapItem root;
	private Layouter currentLayouter;
	private Painter currentPainter;
	private MapItem selectedMapItem = null;
	private ContextMenu contextMenu;
	private MapItem highlightedMapItem;

	public FileMap(DiskItem root, int width, int height, GuiContext guiContext) {
		this.guiContext = guiContext;
		this.root = root==null?null:new MapItem(root);
		Painter .Type pt = Painter .Type.CushionPainter;
		Layouter.Type lt = Layouter.Type.GroupLayouter;
		currentPainter  = pt.createPainter .get();
		currentLayouter = lt.createLayouter.get();
		currentLayouter.setPainter(currentPainter);
		contextMenu = new ContextMenu(pt,lt);
		MyMouseListener mouseListener = new MyMouseListener();
		addMouseListener(mouseListener);
		addMouseMotionListener(mouseListener);
	}

	public void setRoot(DiskItem root) {
		this.root = root==null?null:new MapItem(root);
		update();
	}

	public void update() {
		currentPainter.forceUpdate();
		repaint();
	}

	public void setSelected(DiskItem diskItem) {
		if (root!=null)
			setSelected(root.find(diskItem));
	}
	private void setSelected(MapItem mapItem) {
		selectedMapItem = mapItem;
		setHighlighted(selectedMapItem);
	}
	private void setHighlighted(MapItem mapItem) {
		if (highlightedMapItem!=null) highlightedMapItem.setHighlighted(false);
		highlightedMapItem = mapItem;
		if (highlightedMapItem!=null) highlightedMapItem.setHighlighted(true);
		guiContext.showHighlightedMapItem(highlightedMapItem==null?null:highlightedMapItem.diskItem);
		repaint();
	}

	private void setLayouter(Layouter.Type t) {
		currentLayouter = t.createLayouter.get();
		currentLayouter.setPainter(currentPainter);
		repaint();
	}

	private void setPainter(Painter.Type t) {
		if (currentPainter.isConfigurable()) currentPainter.hideConfig();
		currentPainter = t.createPainter.get();
		currentLayouter.setPainter(currentPainter);
		repaint();
	}

	@Override
	protected void paintCanvas(Graphics g, int x, int y, int width, int height) {
		if (root!=null)
			currentPainter.paintAll(root,g,x,y,width,height);
	}

	private class MyMouseListener implements MouseListener, MouseMotionListener {
		
		private MapItem getMapItemAt(MouseEvent e) {
			return root==null?null:root.getMapItemAt(e.getX(), e.getY());
		}
		
		@Override public void mouseDragged(MouseEvent e) {}
		@Override public void mousePressed (MouseEvent e) {}
		@Override public void mouseReleased(MouseEvent e) {}
		
		@Override public void mouseClicked (MouseEvent e) {
			if (e.getButton()==MouseEvent.BUTTON3)
				contextMenu.show(FileMap.this, getMapItemAt(e), e.getX(), e.getY());
			
			if (e.getButton()==MouseEvent.BUTTON1) {
				setSelected(getMapItemAt(e));
				if (selectedMapItem!=null) guiContext.expandPathInTree(selectedMapItem.diskItem);
			}
		}
		
		@Override public void mouseEntered (MouseEvent e) {
			setHighlighted(getMapItemAt(e));
		}
		@Override public void mouseMoved(MouseEvent e) {
			if (highlightedMapItem==null)
				setHighlighted(getMapItemAt(e));
			else {
				MapItem mouseOverMapItem = highlightedMapItem.getMapItemAt(e.getX(), e.getY());
				if (mouseOverMapItem==null)
					setHighlighted(getMapItemAt(e));
				else if (mouseOverMapItem!=highlightedMapItem)
					setHighlighted(mouseOverMapItem);
			}
		}
		@Override public void mouseExited  (MouseEvent e) {
			setHighlighted(selectedMapItem);
		}
	}
	
	private class ContextMenu extends JPopupMenu {
		private static final long serialVersionUID = 5839108151130675728L;
		
		private MapItem clickedMapItem;
		private JMenuItem configureLayouter;
		private JMenuItem configurePainter;
		private JMenuItem copyPath;
		
		ContextMenu(Painter.Type pt, Layouter.Type pst) {
			add(copyPath = createMenuItem("Copy Path",e->{
				if (clickedMapItem==null) return; 
				guiContext.copyPathToClipBoard(clickedMapItem.diskItem);
			}));
			addSeparator();
			createCheckBoxMenuItems(pst, Layouter.Type.values(), t->t.title, FileMap.this::setLayouter);
			add(configureLayouter = createMenuItem("Configure Layouter ...",e->currentLayouter.showConfig(FileMap.this)));
			addSeparator();
			createCheckBoxMenuItems(pt , Painter .Type.values(), t->t.title, FileMap.this::setPainter );
			add(configurePainter = createMenuItem("Configure Painter ...",e->currentPainter.showConfig(FileMap.this)));
		}
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
		public void show(Component invoker, MapItem clickedMapItem, int x, int y) {
			this.clickedMapItem = clickedMapItem;
			configureLayouter.setEnabled(currentLayouter!=null && currentLayouter.isConfigurable());
			configurePainter .setEnabled(currentPainter !=null && currentPainter .isConfigurable());
			copyPath.setEnabled(this.clickedMapItem!=null);
			super.show(invoker, x, y);
		}
		
	}

	static class MapItem {
		
		private final MapItem parent;
		final DiskItem diskItem;
		final MapItem[] children;
		final int level;

		double relativeSize = 1.0;

		public Rectangle screenBox = new Rectangle();
		boolean isHighlighted = false;

		public MapItem(DiskItem diskItem) { this(null,diskItem,0); }
		public MapItem(MapItem parent, DiskItem diskItem, int level) {
			this.parent = parent;
			this.diskItem = diskItem;
			this.level = level;
			this.children = diskItem.children.stream()
					//.filter(di->di.size_kB>0)
					.map(di->new MapItem(this,di,level+1))
					.toArray(n->new MapItem[n]);
			Arrays.sort(
					children,
					Comparator.<MapItem,DiskItem>comparing(c->c.diskItem,Comparator.<DiskItem,Long>comparing(di->di.size_kB,Comparator.reverseOrder()))
			);
			
			long sizeOfChildren = 0;
			for (MapItem child:children) sizeOfChildren += child.diskItem.size_kB;
			for (MapItem child:children) child.relativeSize = sizeOfChildren==0?1.0/children.length:child.diskItem.size_kB/(double)sizeOfChildren;
		}
		
		public void setHighlighted(boolean isHighlighted) {
			this.isHighlighted = isHighlighted;
			if (parent!=null) parent.setHighlighted(isHighlighted);
		}
		
		public MapItem getMapItemAt(int x, int y) {
			if (!isAt(x, y)) return null;
			for (MapItem child:children) {
				MapItem hit = child.getMapItemAt(x,y);
				if (hit!=null) return hit;
			}
			return this;
		}
		
		private boolean isAt(int x, int y) {
			return screenBox.contains(x,y);
		}
		
		public MapItem find(DiskItem diskItem) {
			if (this.diskItem == diskItem) return this;
			for (MapItem child:children) {
				MapItem hit = child.find(diskItem);
				if (hit!=null) return hit;
			}
			return null;
		}
	}
	
}
