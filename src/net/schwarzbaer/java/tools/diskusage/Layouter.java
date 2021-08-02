package net.schwarzbaer.java.tools.diskusage;

import java.awt.Graphics;
import java.util.function.Supplier;

import net.schwarzbaer.java.tools.diskusage.FileMap.MapItem;

abstract class Layouter {
	private Painter painter = null;

	public void setPainter(Painter painter) {
		this.painter = painter;
		this.painter.setLayouter(this);
	}
	
	public void showConfig(FileMap fileMap) {}
	public boolean isConfigurable() { return false; }

	public void layoutMapItem(MapItem mapItem, Graphics g, int x, int y, int width, int height) {
		if (width<=0 || height<=0) { mapItem.screenBox.setSize(0,0); return; }
		mapItem.screenBox.setBounds(x,y,width,height);
		painter.paintMapItem(mapItem, g, x,y, width,height);
		layoutChildren(mapItem.children, g, x+1, y+1, width-2, height-2);
	}
	protected abstract void layoutChildren(MapItem[] children, Graphics g, int x, int y, int width, int height);
	
	enum Type {
		GroupLayouter       ("Group Layouter"        ,Layouter.GroupLayouter       ::new),
		SimpleStripsLayouter("Simple Strips Layouter",Layouter.SimpleStripsLayouter::new),
		;
		String title;
		Supplier<Layouter> createLayouter;
		private Type(String title, Supplier<Layouter> createLayouter) {
			this.title = title;
			this.createLayouter = createLayouter;
		}
	}
	
	
	static class GroupLayouter extends Layouter {
	
		@Override
		protected void layoutChildren(MapItem[] children, Graphics g, int x, int y, int width, int height) {
			if (width<=0 || height<=0) return;
			layoutChildrenGroup(children, 0, children.length, 1.0, g, x,y, width,height);
		}
	
		private void layoutChildrenGroup(MapItem[] children, int beginIndex, int endIndex, double relGroupSize, Graphics g, int x, int y, int width, int height) {
			while (true) {
				if (relGroupSize<=0) return;
				if (width<=0 || height<=0) return;
				endIndex = Math.min(endIndex, children.length);
				if (beginIndex>=endIndex) return;
				
				boolean horizontal = width>height;
				int length = horizontal?width:height;
				int breadth = horizontal?height:width;
				
				boolean writeCompleteRow = true;
				double relRowSize = 0.0;
				for (int i=beginIndex; i<endIndex; i++) {
					relRowSize += children[i].relativeSize;
					int blockWidth = (int)Math.round( relRowSize/relGroupSize*length );
					if (1 <= (i+1-beginIndex)*blockWidth / breadth) {
						layoutChildrenGroupAsSimpleStrips(children, beginIndex,i+1, relRowSize, g, x,y, !horizontal, breadth, blockWidth);
						if (horizontal) {
							x += blockWidth;
							width -= blockWidth;
						} else {
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
	
		private void layoutChildrenGroupAsSimpleStrips(MapItem[] children, int beginIndex, int endIndex, double relGroupSize, Graphics g, int x, int y, boolean horizontal, int length, int breadth) {
			int screenPos = horizontal?x:y;
			double pos = screenPos;
			for (int i=beginIndex; i<endIndex; i++) {
				MapItem child = children[i];
				pos += child.relativeSize/relGroupSize*length;
				int w = (int)Math.round(pos-screenPos);
				if (horizontal)
					layoutMapItem(child, g, screenPos,y, w,breadth);
				else
					layoutMapItem(child, g, x,screenPos, breadth,w);
				screenPos += w;
			}
		}
	}
	
	static class SimpleStripsLayouter extends Layouter {
	
		@Override
		protected void layoutChildren(MapItem[] children, Graphics g, int x, int y, int width, int height) {
			if (width<=0 || height<=0) return;
			boolean horizontal = width>height;
			int length = horizontal?width:height;
			int screenPos = horizontal?x:y;
			double pos = screenPos;
			for (MapItem child:children) {
				pos += child.relativeSize*length;
				int w = (int)Math.round(pos-screenPos);
				if (horizontal)
					layoutMapItem(child, g, screenPos,y, w,height);
				else
					layoutMapItem(child, g, x,screenPos, width,w);
				screenPos += w;
			}
		}
	}
}