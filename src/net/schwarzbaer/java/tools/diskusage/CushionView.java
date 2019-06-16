package net.schwarzbaer.java.tools.diskusage;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Vector;
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
		void expandPathInTree(DiskItem[] diskItems);
	}
	
	private final GuiContext guiContext;
	private final Cushion root;
	private PaintStrategy currentStrategy;
	private Painter currentPainter;

	public CushionView(DiskItem root, int width, int height, GuiContext guiContext) {
		this.guiContext = guiContext;
		this.root = new Cushion(root);
		Painter      .Type pt  = Painter      .Type.RectanglePainter;
		PaintStrategy.Type pst = PaintStrategy.Type.GroupStrategy;
		currentPainter  = pt .createPainter      .get();
		currentStrategy = pst.createPaintStrategy.get();
		currentStrategy.setPainter(currentPainter);
		addMouseListener(new ContextMenu(pt,pst));
	}

	@Override
	protected void paintCanvas(Graphics g, int x, int y, int width, int height) {
		currentPainter.paintAll(root,g,x,y,width,height);
	}

	private void setStrategy(PaintStrategy.Type t) {
		currentStrategy = t.createPaintStrategy.get();
		currentStrategy.setPainter(currentPainter);
		repaint();
	}

	private void setPainter(Painter.Type t) {
		currentPainter = t.createPainter.get();
		currentStrategy.setPainter(currentPainter);
		repaint();
	}
	
	private class ContextMenu extends JPopupMenu implements MouseListener {
		private static final long serialVersionUID = 5839108151130675728L;
		
		ContextMenu(Painter.Type pt, PaintStrategy.Type pst) {
			ButtonGroup bgStrategy = new ButtonGroup();
			for (PaintStrategy.Type t:PaintStrategy.Type.values())
				add(createMenuItem(t.title,t==pst,bgStrategy,e->setStrategy(t)));
//			add(createMenuItem("Group Strategy"        ,true ,bgStrategy,e->setStrategy(new PaintStrategy.GroupStrategy())));
//			add(createMenuItem("Simple Strips Strategy",false,bgStrategy,e->setStrategy(new PaintStrategy.SimpleStripsStrategy())));
			addSeparator();
			ButtonGroup bgPainter = new ButtonGroup();
			for (Painter.Type t:Painter.Type.values())
				add(createMenuItem(t.title,t==pt,bgPainter,e->setPainter(t)));
//			add(createMenuItem("Rectangle Painter"  ,true ,bgPainter,e->setPainter(new Painter.RectanglePainter ())));
//			add(createMenuItem("Rectangle Painter 2",false,bgPainter,e->setPainter(new Painter.RectanglePainter2())));
		}

		private JMenuItem createMenuItem(String title, boolean isSelected, ButtonGroup bg, ActionListener al) {
			JCheckBoxMenuItem comp = new JCheckBoxMenuItem(title,isSelected);
			comp.addActionListener(al);
			bg.add(comp);
			return comp;
		}

		@Override public void mousePressed (MouseEvent e) {}
		@Override public void mouseReleased(MouseEvent e) {}
		@Override public void mouseEntered (MouseEvent e) {}
		@Override public void mouseExited  (MouseEvent e) {}
		@Override public void mouseClicked (MouseEvent e) {
			if (e.getButton()==MouseEvent.BUTTON3)
				show(CushionView.this, e.getX(), e.getY());
			if (e.getButton()==MouseEvent.BUTTON1) {
				Cushion[] path = root.getPath(e.getX(), e.getY());
				//showPath(path);
				guiContext.expandPathInTree(getDiskItems(path));
			}
		}

		@SuppressWarnings("unused")
		private void showPath(Cushion[] path) {
			System.out.print("path: ");
			for (int i=0; i<path.length; i++) {
				Cushion c = path[i];
				System.out.print(c.diskItem.name);
				if (i+1<path.length)
					System.out.print(" | ");
				else
					System.out.println("  [ "+c.diskItem.getSizeStr()+" ]");
			}
		}

		private DiskItem[] getDiskItems(Cushion[] path) {
			return Arrays.asList(path).stream().map(c->c.diskItem).toArray(n->new DiskItem[n]);
		}
	}

	private static class Cushion {
		
		@SuppressWarnings("unused")
		private final Cushion parent;
		private final DiskItem diskItem;
		private final Cushion[] children;
		private final int level;

		private double relativeSize = 1.0;
		private long sizeOfChildren = 0;

		public Rectangle screenBox = new Rectangle();

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
		
		public Cushion[] getPath(int x, int y) {
			Vector<Cushion> path = new Vector<>();
			getPath(path,x,y);
			return path.toArray(new Cushion[path.size()]);
		}
		
		private boolean getPath(Vector<Cushion> path, int x, int y) {
			if (screenBox.contains(x,y)) {
				path.add(this);
				for (Cushion child:children) {
					boolean hit = child.getPath(path,x,y);
					if (hit) break;
				}
				return true;
			}
			return false;
		}
	}
	
	private static abstract class Painter {
		
		protected PaintStrategy paintStrategy = null;
		public void setPaintStrategy(PaintStrategy paintStrategy) {
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
			private static final Color[] defaultColors = new Color[] {
					Color.RED, Color.GREEN, Color.BLUE
			};
			
			@Override
			public void paintCushion(Cushion cushion, Graphics g, int x, int y, int width, int height) {
				Color color = defaultColors[cushion.level%defaultColors.length];
				g.setColor(color);
				g.drawRect(x, y, width-1, height-1);
			}
			@Override
			public void paintAll(Cushion root, Graphics g, int x, int y, int width, int height) {
				paintStrategy.paintCushion(root,g,x,y,width,height);
			}
		}
		
		private static class RectanglePainter2 extends RectanglePainter {
			private static final Color[] defaultColors = new Color[] {
					Color.ORANGE, Color.DARK_GRAY, Color.PINK
			};
		
			@Override
			public void paintCushion(Cushion cushion, Graphics g, int x, int y, int width, int height) {
				Color color = defaultColors[cushion.level%defaultColors.length];
				g.setColor(color);
				g.drawRect(x, y, width-1, height-1);
			}
			
		}
	}
	
	private static abstract class PaintStrategy {
		private Painter painter = null;

		public void setPainter(Painter painter) {
			this.painter = painter;
			this.painter.setPaintStrategy(this);
		}
		
		public void paintCushion(Cushion cushion, Graphics g, int x, int y, int width, int height) {
			if (width<=0 || height<=0) { cushion.screenBox.setSize(0,0); return; }
			cushion.screenBox.setBounds(x,y,width,height);
			painter.paintCushion(cushion, g, x,y, width,height);
			paintChildren(cushion.children, g, x+1, y+1, width-2, height-2);
		}
		protected abstract void paintChildren(Cushion[] children, Graphics g, int x, int y, int width, int height);
		
		enum Type {
			GroupStrategy       ("Group Strategy"        ,PaintStrategy.GroupStrategy       ::new),
			SimpleStripsStrategy("Simple Strips Strategy",PaintStrategy.SimpleStripsStrategy::new),
			;
			private String title;
			private Supplier<PaintStrategy> createPaintStrategy;
			private Type(String title, Supplier<PaintStrategy> createPaintStrategy) {
				this.title = title;
				this.createPaintStrategy = createPaintStrategy;
			}
		}
		
		
		private static class GroupStrategy extends PaintStrategy {
			private static final double MAX_RATIO = 3.0; // 1/X < w/h < X
		
			@Override
			protected void paintChildren(Cushion[] children, Graphics g, int x, int y, int width, int height) {
				if (width<=0 || height<=0) return;
				paintChildrenGroup(children, 0, children.length, 1.0, g, x,y, width,height);
			}
		
			private void paintChildrenGroup(Cushion[] children, int beginIndex, int endIndex, double relGroupSize, Graphics g, int x, int y, int width, int height) {
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
							paintCushion(children[beginIndex], g, x,y, blockWidth,height);
							x += blockWidth;
							width -= blockWidth;
						} else {
							paintCushion(children[beginIndex], g, x,y, width,blockWidth);
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
						// TODO: take remaining children into account
						if ((blockWidth>0 && breadth/blockWidth < (i+1-beginIndex))) {
							if (horizontal) {
								paintChildrenGroup(children, beginIndex,i+1, relRowSize, g, x,y, blockWidth,height);
								x += blockWidth;
								width -= blockWidth;
							} else {
								paintChildrenGroup(children, beginIndex,i+1, relRowSize, g, x,y, width,blockWidth);
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
						paintChildrenGroupAsSimpleStrips(children, beginIndex, endIndex, relGroupSize, g, x, y, horizontal, length, breadth);
						return;
					}
				}
			}
		
			private void paintChildrenGroupAsSimpleStrips(Cushion[] children, int beginIndex, int endIndex, double relGroupSize, Graphics g, int x, int y, boolean horizontal, int length, int breadth) {
				int screenPos = horizontal?x:y;
				double pos = screenPos;
				for (int i=beginIndex; i<endIndex; i++) {
					Cushion child = children[i];
					pos += child.relativeSize/relGroupSize*length;
					int w = (int)Math.round(pos-screenPos);
					if (horizontal)
						paintCushion(child, g, screenPos,y, w,breadth);
					else
						paintCushion(child, g, x,screenPos, breadth,w);
					screenPos += w;
				}
			}
		}
		private static class SimpleStripsStrategy extends PaintStrategy {
		
			@Override
			protected void paintChildren(Cushion[] children, Graphics g, int x, int y, int width, int height) {
				if (width<=0 || height<=0) return;
				boolean horizontal = width>height;
				int length = horizontal?width:height;
				int screenPos = horizontal?x:y;
				double pos = screenPos;
				for (Cushion child:children) {
					pos += child.relativeSize*length;
					int w = (int)Math.round(pos-screenPos);
					if (horizontal)
						paintCushion(child, g, screenPos,y, w,height);
					else
						paintCushion(child, g, x,screenPos, width,w);
					screenPos += w;
				}
			}
		}
	}
}
