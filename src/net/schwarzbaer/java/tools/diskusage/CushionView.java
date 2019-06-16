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
	private AbstractPaintStrategy currentStrategy;
	private AbstractPainter currentPainter;

	public CushionView(DiskItem root, int width, int height, GuiContext guiContext) {
		this.guiContext = guiContext;
		this.root = new Cushion(root);
		currentPainter = new RectanglePainter();
		currentStrategy = new GroupStrategy();
		currentStrategy.setPainter(currentPainter);
		addMouseListener(new ContextMenu());
	}

	@Override
	protected void paintCanvas(Graphics g, int x, int y, int width, int height) {
		currentPainter.paintAll(root,g,x,y,width,height);
	}

	private void setStrategy(AbstractPaintStrategy strategy) {
		currentStrategy = strategy;
		currentStrategy.setPainter(currentPainter);
		repaint();
	}

	private void setPainter(AbstractPainter painter) {
		currentPainter = painter;
		currentStrategy.setPainter(currentPainter);
		repaint();
	}
	
	private class ContextMenu extends JPopupMenu implements MouseListener {
		private static final long serialVersionUID = 5839108151130675728L;
		
		ContextMenu() {
			ButtonGroup bg = new ButtonGroup();
			add(createMenuItem("Simple Strips Strategy",false,bg,e->setStrategy(new SimpleStripsStrategy())));
			add(createMenuItem("Group Strategy"        ,true ,bg,e->setStrategy(new GroupStrategy())));
			addSeparator();
			add(createMenuItem("Rectangle Painter"  ,false,bg,e->setPainter(new RectanglePainter ())));
			add(createMenuItem("Rectangle Painter 2",true ,bg,e->setPainter(new RectanglePainter2())));
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
		private static final Color[] defaultColors = new Color[] {
				Color.BLUE, Color.GREEN, Color.RED
		};
		
		@SuppressWarnings("unused")
		private final Cushion parent;
		private final DiskItem diskItem;
		private final Cushion[] children;
		private final Color color;

		private double relativeSize = 1.0;
		private long sizeOfChildren = 0;

		public Rectangle screenBox = new Rectangle();

		public Cushion(DiskItem diskItem) { this(null,diskItem,0); }
		public Cushion(Cushion parent, DiskItem diskItem, int level) {
			this.parent = parent;
			this.diskItem = diskItem;
			this.color = defaultColors[level%defaultColors.length];
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
	
	private static abstract class AbstractPainter {
		
		protected AbstractPaintStrategy paintStrategy = null;
		public void setPaintStrategy(AbstractPaintStrategy paintStrategy) {
			this.paintStrategy = paintStrategy;
		}
		public abstract void paintAll(Cushion root, Graphics g, int x, int y, int width, int height);
		public abstract void paintCushion(Cushion cushion, Graphics g, int x, int y, int width, int height);
	}
	
	private static class RectanglePainter extends AbstractPainter {
		@Override
		public void paintCushion(Cushion cushion, Graphics g, int x, int y, int width, int height) {
			g.setColor(cushion.color);
			g.drawRect(x, y, width-1, height-1);
		}
		@Override
		public void paintAll(Cushion root, Graphics g, int x, int y, int width, int height) {
			paintStrategy.paintCushion(root,g,x,y,width,height);
		}
	}
	
	private static class RectanglePainter2 extends RectanglePainter {
	}
	
	private static abstract class AbstractPaintStrategy {
		private AbstractPainter painter = null;

		public void setPainter(AbstractPainter painter) {
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
	}
	
	private static class GroupStrategy extends AbstractPaintStrategy {
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
						//paintChildrenGroup(children, beginIndex, endIndex, relGroupSize, g, x, y, width, height)
						//paintChildrenGroup(children, beginIndex+1, endIndex, relGroupSize-children[beginIndex].relativeSize, g, x+blockWidth,y, length-blockWidth, breadth);
					} else {
						paintCushion(children[beginIndex], g, x,y, width,blockWidth);
						y += blockWidth;
						height -= blockWidth;
						//paintChildrenGroup(children, beginIndex, endIndex, relGroupSize, g, x, y, width, height)
						//paintChildrenGroup(children, beginIndex+1, endIndex, relGroupSize-children[beginIndex].relativeSize, g, x,y+blockWidth, breadth, length-blockWidth);
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
					if ((blockWidth>0 && breadth/blockWidth < (i+1-beginIndex))) {
						//paintChildrenGroupAsSimpleStrips(children, beginIndex, i+1, relRowSize, g, x, y, !horizontal, breadth, blockWidth);
						if (horizontal) {
							paintChildrenGroup(children, beginIndex,i+1, relRowSize, g, x,y, blockWidth,height);
							//paintChildrenGroup(children, i+1, endIndex, relGroupSize-relRowSize, g, x+blockWidth,y, length-blockWidth, breadth);
							x += blockWidth;
							width  -= blockWidth;
							//paintChildrenGroup(children, beginIndex, endIndex, relGroupSize, g, x, y, width, height)
						} else {
							paintChildrenGroup(children, beginIndex,i+1, relRowSize, g, x,y, width,blockWidth);
							//paintChildrenGroup(children, i+1, endIndex, relGroupSize-relRowSize, g, x,y+blockWidth, breadth, length-blockWidth);
							y += blockWidth;
							height -= blockWidth;
							//paintChildrenGroup(children, beginIndex, endIndex, relGroupSize, g, x, y, width, height)
						}
						//return;
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
	
	private static class SimpleStripsStrategy extends AbstractPaintStrategy {

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
