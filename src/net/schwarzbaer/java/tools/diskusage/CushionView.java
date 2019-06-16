package net.schwarzbaer.java.tools.diskusage;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import net.schwarzbaer.gui.Canvas;
import net.schwarzbaer.java.tools.diskusage.DiskUsage.DiskItem;

public class CushionView extends Canvas {
	private static final long serialVersionUID = -41169096975888890L;
	
	interface GuiContext {
		void expandPathInTree(DiskItem diskItem);
	}
	
	private final GuiContext guiContext;
	private Cushion root;
	private Layouter currentLayouter;
	private Painter currentPainter;
	private Cushion selectedCushion = null;
	private ContextMenu contextMenu;

	public CushionView(DiskItem root, int width, int height, GuiContext guiContext) {
		this.guiContext = guiContext;
		this.root = new Cushion(root);
		Painter .Type pt = Painter .Type.RectanglePainter;
		Layouter.Type lt = Layouter.Type.GroupLayouter;
		currentPainter  = pt.createPainter .get();
		currentLayouter = lt.createLayouter.get();
		currentLayouter.setPainter(currentPainter);
		contextMenu = new ContextMenu(pt,lt);
		addMouseListener(new MyMouseListener());
	}

	public void setRoot(DiskItem root) {
		this.root = new Cushion(root);
		repaint();
	}

	public void setSelected(DiskItem diskItem) {
		setSelected(root.find(diskItem));
	}
	private void setSelected(Cushion cushion) {
		if (selectedCushion!=null) selectedCushion.setSelected(false);
		selectedCushion = cushion;
		if (selectedCushion!=null) selectedCushion.setSelected(true);
		repaint();
	}

	private void setLayouter(Layouter.Type t) {
		currentLayouter = t.createLayouter.get();
		currentLayouter.setPainter(currentPainter);
		repaint();
	}

	private void setPainter(Painter.Type t) {
		currentPainter = t.createPainter.get();
		currentLayouter.setPainter(currentPainter);
		repaint();
	}

	@Override
	protected void paintCanvas(Graphics g, int x, int y, int width, int height) {
		currentPainter.paintAll(root,g,x,y,width,height);
	}

	private class MyMouseListener implements MouseListener {

		@Override public void mousePressed (MouseEvent e) {}
		@Override public void mouseReleased(MouseEvent e) {}
		@Override public void mouseEntered (MouseEvent e) {}
		@Override public void mouseExited  (MouseEvent e) {}
		@Override public void mouseClicked (MouseEvent e) {
			
			if (e.getButton()==MouseEvent.BUTTON3)
				contextMenu.show(CushionView.this, e.getX(), e.getY());
			
			if (e.getButton()==MouseEvent.BUTTON1) {
				setSelected(root.getCushion(e.getX(), e.getY()));
				if (selectedCushion!=null) guiContext.expandPathInTree(selectedCushion.diskItem);
			}
		}
	}
	
	private class ContextMenu extends JPopupMenu {
		private static final long serialVersionUID = 5839108151130675728L;
		
		ContextMenu(Painter.Type pt, Layouter.Type pst) {
			createMenuItems(pst, Layouter.Type.values(), t->t.title, CushionView.this::setLayouter);
			addSeparator();
			createMenuItems(pt , Painter .Type.values(), t->t.title, CushionView.this::setPainter );
		}
		private <E extends Enum<E>> void createMenuItems(E selected, E[] values, Function<E,String> getTitle, Consumer<E> set) {
			ButtonGroup bg = new ButtonGroup();
			for (E t:values)
				add(createMenuItem(getTitle.apply(t),t==selected,bg,e->set.accept(t)));
		}
		private JMenuItem createMenuItem(String title, boolean isSelected, ButtonGroup bg, ActionListener al) {
			JCheckBoxMenuItem comp = new JCheckBoxMenuItem(title,isSelected);
			comp.addActionListener(al);
			bg.add(comp);
			return comp;
		}
	}

	private static class Cushion {
		
		private final Cushion parent;
		private final DiskItem diskItem;
		private final Cushion[] children;
		private final int level;

		private double relativeSize = 1.0;
		private long sizeOfChildren = 0;

		public Rectangle screenBox = new Rectangle();
		private boolean isSelected = false;

		public Cushion(DiskItem diskItem) { this(null,diskItem,0); }
		public Cushion(Cushion parent, DiskItem diskItem, int level) {
			this.parent = parent;
			this.diskItem = diskItem;
			this.level = level;
			this.children = diskItem.children.stream()
					.filter(di->di.size>0)
					.map(di->new Cushion(this,di,level+1))
					.toArray(n->new Cushion[n]);
			Arrays.sort(
					children,
					Comparator.<Cushion,DiskItem>comparing(c->c.diskItem,Comparator.<DiskItem,Long>comparing(di->di.size,Comparator.reverseOrder()))
			);
			
			sizeOfChildren = 0;
			for (Cushion child:children) sizeOfChildren += child.diskItem.size;
			for (Cushion child:children) child.relativeSize = child.diskItem.size/(double)sizeOfChildren;
		}
		
		public void setSelected(boolean isSelected) {
			this.isSelected = isSelected;
			if (parent!=null) parent.setSelected(isSelected);
		}
		public Cushion getCushion(int x, int y) {
			if (!screenBox.contains(x,y)) return null;
			for (Cushion child:children) {
				Cushion hit = child.getCushion(x,y);
				if (hit!=null) return hit;
			}
			return this;
		}
		public Cushion find(DiskItem diskItem) {
			if (this.diskItem == diskItem) return this;
			for (Cushion child:children) {
				Cushion hit = child.find(diskItem);
				if (hit!=null) return hit;
			}
			return null;
		}
	}
	
	private static abstract class Painter {
		
		protected Layouter paintStrategy = null;
		public void setLayouter(Layouter paintStrategy) {
			this.paintStrategy = paintStrategy;
		}
		public abstract void paintAll(Cushion root, Graphics g, int x, int y, int width, int height);
		public abstract void paintCushion(Cushion cushion, Graphics g, int x, int y, int width, int height);
		
		enum Type {
			RectanglePainter ("Rectangle Painter"  ,Painter.RectanglePainter ::new),
			RectanglePainter2("Rectangle Painter 2",Painter.RectanglePainter2::new),
			;
			private String title;
			private Supplier<Painter> createPainter;
			private Type(String title, Supplier<Painter> createPainter) {
				this.title = title;
				this.createPainter = createPainter;
			}
		}
		
		
		private static class RectanglePainter extends Painter {
			
			private Color[] colors;
			private Color selectedLeaf;
			private Color selectedFolder;
			
			RectanglePainter() {
				this(Color.YELLOW, Color.ORANGE, new Color[] { Color.RED, Color.GREEN, Color.BLUE });
			}
			RectanglePainter(Color selectedLeaf, Color selectedFolder, Color[] colors) {
				this.selectedLeaf   = selectedLeaf;
				this.selectedFolder = selectedFolder;
				this.colors = colors;
			}
			
			@Override
			public void paintAll(Cushion root, Graphics g, int x, int y, int width, int height) {
				paintStrategy.layoutCushion(root,g,x,y,width,height);
			}
			@Override
			public void paintCushion(Cushion cushion, Graphics g, int x, int y, int width, int height) {
				g.setColor(getColor(cushion));
				g.drawRect(x, y, width-1, height-1);
			}
			protected Color getColor(Cushion cushion) {
				if (cushion.isSelected) {
					if (cushion.children.length==0) return selectedLeaf;
					return selectedFolder;
				}
				return colors[cushion.level%colors.length];
			}
		}
		
		private static class RectanglePainter2 extends RectanglePainter {
			RectanglePainter2() {
				super(Color.GREEN, Color.BLUE, new Color[] { Color.ORANGE, Color.DARK_GRAY, Color.PINK });
			}
		}
	}
	
	private static abstract class Layouter {
		private Painter painter = null;

		public void setPainter(Painter painter) {
			this.painter = painter;
			this.painter.setLayouter(this);
		}
		
		public void layoutCushion(Cushion cushion, Graphics g, int x, int y, int width, int height) {
			if (width<=0 || height<=0) { cushion.screenBox.setSize(0,0); return; }
			cushion.screenBox.setBounds(x,y,width,height);
			painter.paintCushion(cushion, g, x,y, width,height);
			layoutChildren(cushion.children, g, x+1, y+1, width-2, height-2);
		}
		protected abstract void layoutChildren(Cushion[] children, Graphics g, int x, int y, int width, int height);
		
		enum Type {
			GroupLayouter       ("Group Layouter"        ,Layouter.GroupLayouter       ::new),
			SimpleStripsLayouter("Simple Strips Layouter",Layouter.SimpleStripsLayouter::new),
			;
			private String title;
			private Supplier<Layouter> createLayouter;
			private Type(String title, Supplier<Layouter> createLayouter) {
				this.title = title;
				this.createLayouter = createLayouter;
			}
		}
		
		
		private static class GroupLayouter extends Layouter {
			private static final double MAX_RATIO = 3.0; // 1/X < w/h < X
		
			@Override
			protected void layoutChildren(Cushion[] children, Graphics g, int x, int y, int width, int height) {
				if (width<=0 || height<=0) return;
				layoutChildrenGroup(children, 0, children.length, 1.0, g, x,y, width,height);
			}
		
			private void layoutChildrenGroup(Cushion[] children, int beginIndex, int endIndex, double relGroupSize, Graphics g, int x, int y, int width, int height) {
				while (true) {
					if (relGroupSize<=0) return;
					if (width<=0 || height<=0) return;
					endIndex = Math.min(endIndex, children.length);
					if (beginIndex>=endIndex) return;
					
					boolean horizontal = width>height;
					int length = horizontal?width:height;
					int breadth = horizontal?height:width;
					
					double firstBlockAspectRatio = breadth / (children[beginIndex].relativeSize/relGroupSize*length);
					if (firstBlockAspectRatio<MAX_RATIO || beginIndex+1==endIndex) {
						// firstBlock uses full width
						int blockWidth = (int)Math.round( children[beginIndex].relativeSize/relGroupSize*length );
						if (horizontal) {
							layoutCushion(children[beginIndex], g, x,y, blockWidth,height);
							x += blockWidth;
							width -= blockWidth;
						} else {
							layoutCushion(children[beginIndex], g, x,y, width,blockWidth);
							y += blockWidth;
							height -= blockWidth;
						}
						relGroupSize -= children[beginIndex].relativeSize;
						beginIndex++;
						continue;
					}
					
					boolean writeCompleteRow = true;
					double relRowSize = 0.0;
					for (int i=beginIndex; i<endIndex-1; i++) {
						relRowSize += children[i].relativeSize;
						int blockWidth = (int)Math.round( relRowSize/relGroupSize*length );
						if (1 <= (i+1-beginIndex)*blockWidth / breadth) {
							if (horizontal) {
								layoutChildrenGroup(children, beginIndex,i+1, relRowSize, g, x,y, blockWidth,height);
								x += blockWidth;
								width -= blockWidth;
							} else {
								layoutChildrenGroup(children, beginIndex,i+1, relRowSize, g, x,y, width,blockWidth);
								y += blockWidth;
								height -= blockWidth;
							}
							relGroupSize -= relRowSize;
							beginIndex = i+1;
							writeCompleteRow = false;
							break;
						}
					}
					
					if (writeCompleteRow) {
						layoutChildrenGroupAsSimpleStrips(children, beginIndex, endIndex, relGroupSize, g, x, y, horizontal, length, breadth);
						return;
					}
				}
			}
		
			private void layoutChildrenGroupAsSimpleStrips(Cushion[] children, int beginIndex, int endIndex, double relGroupSize, Graphics g, int x, int y, boolean horizontal, int length, int breadth) {
				int screenPos = horizontal?x:y;
				double pos = screenPos;
				for (int i=beginIndex; i<endIndex; i++) {
					Cushion child = children[i];
					pos += child.relativeSize/relGroupSize*length;
					int w = (int)Math.round(pos-screenPos);
					if (horizontal)
						layoutCushion(child, g, screenPos,y, w,breadth);
					else
						layoutCushion(child, g, x,screenPos, breadth,w);
					screenPos += w;
				}
			}
		}
		
		private static class SimpleStripsLayouter extends Layouter {
		
			@Override
			protected void layoutChildren(Cushion[] children, Graphics g, int x, int y, int width, int height) {
				if (width<=0 || height<=0) return;
				boolean horizontal = width>height;
				int length = horizontal?width:height;
				int screenPos = horizontal?x:y;
				double pos = screenPos;
				for (Cushion child:children) {
					pos += child.relativeSize*length;
					int w = (int)Math.round(pos-screenPos);
					if (horizontal)
						layoutCushion(child, g, screenPos,y, w,height);
					else
						layoutCushion(child, g, x,screenPos, width,w);
					screenPos += w;
				}
			}
		}
	}
}
