package net.schwarzbaer.java.tools.diskusage;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.Arrays;
import java.util.Comparator;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import net.schwarzbaer.gui.Canvas;
import net.schwarzbaer.java.tools.diskusage.DiskUsage.DiskItem;

public class CushionView extends Canvas {
	private static final long serialVersionUID = -41169096975888890L;
	
	private Cushion root;
	private AbstractDrawStrategy currentStrategy;

	public CushionView(DiskItem root, int width, int height) {
		this.root = new Cushion(root);
		currentStrategy = new SimpleStripsStrategy();
		addMouseListener(new ContextMenu());
	}

	private void setStrategy(AbstractDrawStrategy strategy) {
		currentStrategy = strategy;
		repaint();
	}
	
	private class ContextMenu extends JPopupMenu implements MouseListener {
		private static final long serialVersionUID = 5839108151130675728L;
		
		ContextMenu() {
			ButtonGroup bg = new ButtonGroup();
			add(createMenuItem("Simple Strips Strategy",true ,bg,e->setStrategy(new SimpleStripsStrategy())));
			add(createMenuItem("Group Strategy"        ,false,bg,e->setStrategy(new GroupStrategy       ())));
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
		}
	}

	@Override
	protected void paintCanvas(Graphics g, int x, int y, int width, int height) {
		currentStrategy.draw(root,g,x,y,width,height);
	}

	private static class Cushion {
		private static final Color[] defaultColors = new Color[] {
				Color.BLUE, Color.GREEN, Color.RED
		};
		
		private final DiskItem diskItem;
		private final Cushion[] children;
		private final Color color;

		private double partOfParent = 1.0;
		private long sizeOfChildren = 0;

		public Cushion(DiskItem diskItem) { this(diskItem,0); }
		public Cushion(DiskItem diskItem, int level) {
			this.diskItem = diskItem;
			this.color = defaultColors[level%defaultColors.length];
			this.children = diskItem.children.stream()
					.filter(di->di.size>0)
					.map(di->new Cushion(di,level+1))
					.toArray(n->new Cushion[n]);
			Arrays.sort(
					children,
					Comparator.<Cushion,DiskItem>comparing(c->c.diskItem,Comparator.<DiskItem,Long>comparing(di->di.size,Comparator.reverseOrder()))
			);
			
			sizeOfChildren = 0;
			for (Cushion child:children) sizeOfChildren += child.diskItem.size;
			for (Cushion child:children) child.partOfParent = child.diskItem.size/(double)sizeOfChildren;
		}
	}
	
	private static abstract class AbstractDrawStrategy {
		public void draw(Cushion cushion, Graphics g, int x, int y, int width, int height) {
			// x,y,w,h = my absolute position defined by parent
			if (width<=0 || height<=0) return;
			g.setColor(cushion.color);
			g.drawRect(x, y, width-1, height-1);
			drawChildren(cushion.children, g, x+1, y+1, width-2, height-2);
		}
		protected abstract void drawChildren(Cushion[] children, Graphics g, int x, int y, int width, int height);
	}
	
	private static class GroupStrategy extends AbstractDrawStrategy {
		private static final double MAX_RATIO = 3.0; // 1/X < w/h < X

		@Override
		protected void drawChildren(Cushion[] children, Graphics g, int x, int y, int width, int height) {
			if (width<=0 || height<=0) return;
			drawChildrenGroup(children, 0, 1.0, g, x,y, width,height);
		}

		private void drawChildrenGroup(Cushion[] children, int offset, double remaining, Graphics g, int x, int y, int width, int height) {
			if (width<=0 || height<=0) return;
			if (offset>=children.length) return;
			
			boolean horizontal = width>height;
			int length = horizontal?width:height;
			int breadth = horizontal?height:width;
			
			double firstBlock = breadth / (children[offset].partOfParent/remaining*length);
			if (firstBlock<MAX_RATIO) {
				// firstBlock uses full width
				int blockWidth = (int)Math.round( children[offset].partOfParent/remaining*length );
				if (horizontal) {
					draw(children[offset], g, x,y, blockWidth,height);
					drawChildrenGroup(children, offset+1, remaining-children[offset].partOfParent, g, x+blockWidth,y, width-blockWidth, height);
				} else {
					draw(children[offset], g, x,y, width,blockWidth);
					drawChildrenGroup(children, offset+1, remaining-children[offset].partOfParent, g, x,y+blockWidth, width, height-blockWidth);
				}
				return;
			}
			
			// TODO Auto-generated method stub
			
			// simple stripes
			int screenPos = horizontal?x:y;
			double pos = screenPos;
			for (int i=offset; i<children.length; i++) {
				Cushion child = children[i];
				pos += child.partOfParent/remaining*length;
				int w = (int)Math.round(pos-screenPos);
				if (horizontal)
					draw(child, g, screenPos,y, w,height);
				else
					draw(child, g, x,screenPos, width,w);
				screenPos += w;
			}
		}
	}
	
	private static class SimpleStripsStrategy extends AbstractDrawStrategy {
		@Override
		protected void drawChildren(Cushion[] children, Graphics g, int x, int y, int width, int height) {
			if (width<=0 || height<=0) return;
			boolean horizontal = width>height;
			int length = horizontal?width:height;
			int screenPos = horizontal?x:y;
			double pos = screenPos;
			for (Cushion child:children) {
				pos += child.partOfParent*length;
				int w = (int)Math.round(pos-screenPos);
				if (horizontal)
					draw(child, g, screenPos,y, w,height);
				else
					draw(child, g, x,screenPos, width,w);
				screenPos += w;
			}
		}
	}
}
