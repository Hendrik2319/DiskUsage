package net.schwarzbaer.java.tools.diskusage;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSlider;
import javax.swing.JTextField;

import net.schwarzbaer.gui.BumpmappingSunControl;
import net.schwarzbaer.gui.Canvas;
import net.schwarzbaer.gui.HSColorChooser;
import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.image.BumpMapping;
import net.schwarzbaer.image.BumpMapping.Normal;
import net.schwarzbaer.image.BumpMapping.Shading.MaterialShading;
import net.schwarzbaer.image.ImageCache;
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
		Painter .Type pt = Painter .Type.CushionPainter;
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
		if (currentPainter.isConfigurable()) currentPainter.hideConfig();
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
		private JMenuItem configureLayouter;
		private JMenuItem configurePainter;
		
		ContextMenu(Painter.Type pt, Layouter.Type pst) {
			createCheckBoxMenuItems(pst, Layouter.Type.values(), t->t.title, CushionView.this::setLayouter);
			add(configureLayouter = createMenuItem("Configure Layouter ...",e->currentLayouter.showConfig(CushionView.this)));
			addSeparator();
			createCheckBoxMenuItems(pt , Painter .Type.values(), t->t.title, CushionView.this::setPainter );
			add(configurePainter = createMenuItem("Configure Painter ...",e->currentPainter.showConfig(CushionView.this)));
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
		@Override
		public void show(Component invoker, int x, int y) {
			configureLayouter.setEnabled(currentLayouter!=null && currentLayouter.isConfigurable());
			configurePainter .setEnabled(currentPainter !=null && currentPainter .isConfigurable());
			super.show(invoker, x, y);
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
		
		protected Layouter layouter = null;
		public void setLayouter(Layouter layouter) {
			this.layouter = layouter;
		}
		public boolean isConfigurable() { return false; }
		public void showConfig(CushionView cushionView) {}
		public void hideConfig() {}
		
		public abstract void paintAll(Cushion root, Graphics g, int x, int y, int width, int height);
		public abstract void paintCushion(Cushion cushion, Graphics g, int x, int y, int width, int height);
		
		enum Type {
			RectanglePainter ("Rectangle Painter"  ,Painter.RectanglePainter ::new),
			RectanglePainter2("Rectangle Painter 2",Painter.RectanglePainter2::new),
			CushionPainter   ("Cushion Painter"    ,Painter.CushionPainter   ::new),
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
				layouter.layoutCushion(root,g,x,y,width,height);
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
		
		
		private static class CushionPainter extends Painter {
			
			final static CushionPainterConfig config = new CushionPainterConfig();
			
			private final ImageCache<BufferedImage> imageCache;
			private final BumpMapping bumpMapping;
			private final MaterialShading bumpMappingShading;
			
			private float[][] tempHeightMap;
			private int xOffset;
			private int yOffset;
			private boolean updateNormalMap = true;

			private CushionPainterConfigGUI configGUI = null;

			CushionPainter() {
				bumpMappingShading = new MaterialShading(new Normal(1,-1,2).normalize(), config.material, 0, config.materialPhongExp);
				bumpMapping = new BumpMapping(false);
				bumpMapping.setShading(bumpMappingShading);
				imageCache = new ImageCache<>(bumpMapping::renderImageUncached);
			}
			
			@Override
			public void setLayouter(Layouter layouter) {
				updateNormalMap = true;
				imageCache.resetImage();
				super.setLayouter(layouter);
			}

			@Override public boolean isConfigurable() { return true; }			
			@Override public void hideConfig() {
				if (configGUI!=null)
					configGUI.setVisible(false);
			}
			@Override public void showConfig(CushionView cushionView) {
				if (configGUI==null)
					configGUI = new CushionPainterConfigGUI(cushionView,this);
				else
					configGUI.setVisible(true);
			}
			
			@Override
			public void paintAll(Cushion root, Graphics g, int x, int y, int width, int height) {
				if (imageCache.getWidth()!=width || imageCache.getHeight()!=height || updateNormalMap) {
					updateNormalMap = false;
					tempHeightMap = new float[width][height];
					for (float[] column:tempHeightMap) Arrays.fill(column,0);
					xOffset = x;
					yOffset = y;
					layouter.layoutCushion(root,null,x,y,width,height);
					Normal[][] normalMap = new Normal[width][height];
					for (int x1=0; x1<tempHeightMap.length; ++x1)
						for (int y1=0; y1<tempHeightMap[x1].length; ++y1) {
							Normal n = new Normal();
							addNormal(n,computeNormal(x1,y1,+1, 0)); 
							addNormal(n,computeNormal(x1,y1,-1, 0)); 
							addNormal(n,computeNormal(x1,y1, 0,+1)); 
							addNormal(n,computeNormal(x1,y1, 0,-1));
							normalMap[x1][y1] = n.normalize();
						}
					bumpMapping.setNormalMap(normalMap);
					tempHeightMap = null;
				}
				g.drawImage(imageCache.getImage(width,height), x, y, null);
				drawSelected(root,g);
			}

			private void drawSelected(Cushion cushion, Graphics g) {
				if (cushion.isSelected) {
					if (cushion.children.length==0) g.setColor(config.selectedLeaf);
					else g.setColor(config.selectedFolder);
					Rectangle b = cushion.screenBox;
					g.drawRect(b.x, b.y, b.width-1, b.height-1);
					for (Cushion child:cushion.children)
						drawSelected(child,g);
				}
			}
			private void addNormal(Normal base, Normal n) {
				if (n != null) {
					base.x += n.x;
					base.y += n.y;
					base.z += n.z;
				}
			}

			private Normal computeNormal(int x, int y, int dx, int dy) {
				if (x+dx<0 || x+dx>=tempHeightMap   .length) return null;
				if (y+dy<0 || y+dy>=tempHeightMap[0].length) return null;
				float dh = tempHeightMap[x][y]-tempHeightMap[x+dx][y+dy];
				if (dx!=0) return new Normal(dh*dx,0,Math.abs(dx)).normalize();
				if (dy!=0) return new Normal(0,dh*dy,Math.abs(dy)).normalize();
				return null;
			}

			@Override
			public void paintCushion(Cushion cushion, Graphics g, int x, int y, int width, int height) {
				// TODO: cushion color 
				if (width<2 || height<2) return;
				float cushionHeight = Math.min(width,height)*config.cushionHeightScale;
				double xm = (width -1)*0.5;
				double ym = (height-1)*0.5;
				double cosA2 = Math.cos(config.alpha/2);
				double sin2A2 = Math.sin(config.alpha/2)*Math.sin(config.alpha/2);
				for (int x1=0; x1<width; ++x1) {
					float hX = (float) ((Math.sqrt(1-(x1/xm-1)*(x1/xm-1)*sin2A2)-cosA2)/(1-cosA2));
					for (int y1=0; y1<height; ++y1) {
						float hY = (float) ((Math.sqrt(1-(y1/ym-1)*(y1/ym-1)*sin2A2)-cosA2)/(1-cosA2));
						tempHeightMap[x+x1-xOffset][y+y1-yOffset] += cushionHeight * hX * hY;
					}
				}
			}

			private static class CushionPainterConfig {
				private Color selectedLeaf;
				private Color selectedFolder;
				private Color material;
				private double materialPhongExp;
				private double alpha;
				private float cushionHeightScale;
				
				CushionPainterConfig() {
					this(Color.YELLOW, Color.ORANGE, new Color(0xafafaf));
				}
				CushionPainterConfig(Color selectedLeaf, Color selectedFolder, Color material) {
					this.selectedLeaf = selectedLeaf;
					this.selectedFolder = selectedFolder;
					this.material = material;
					this.materialPhongExp = 40;
					this.alpha = 90*Math.PI/180;
					this.cushionHeightScale = 1/16f;
				}
				
			}

			private static class CushionPainterConfigGUI extends StandardMainWindow {
				private static final long serialVersionUID = 7270004938108015260L;
				private CushionView cushionView;
				private CushionPainter cushionPainter;
			
				public CushionPainterConfigGUI(CushionView cushionView, CushionPainter cushionPainter) {
					super("Config for Cushion Painter",StandardMainWindow.DefaultCloseOperation.HIDE_ON_CLOSE);
					this.cushionView = cushionView;
					this.cushionPainter = cushionPainter;
					
					Normal sun = new Normal();
					cushionPainter.bumpMapping.getSun(sun);
					BumpmappingSunControl sunControl = new BumpmappingSunControl(sun.x, sun.y, sun.z);
					sunControl.setPreferredSize(new Dimension(300,300));
					sunControl.addValueChangeListener((x,y,z)->{
						sun.set(x,y,z);
						cushionPainter.bumpMapping.setSun(x,y,z);
						cushionPainter.imageCache.resetImage();
						cushionView.repaint();
					});
					
					GridBagConstraints c = new GridBagConstraints();
					JPanel contentPane = new JPanel(new GridBagLayout());
					contentPane.setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
					
					int y;
					GBC.setFill(c, GBC.GridFill.HORIZONTAL);
					y=0; GBC.setWeights(c,0,0);
					contentPane.add(new JLabel("Selected File Color: "  ),GBC.setGridPos(c,0,y++));
					contentPane.add(new JLabel("Selected Folder Color: "),GBC.setGridPos(c,0,y++));
					contentPane.add(new JLabel("Cushion Base Color: "   ),GBC.setGridPos(c,0,y++));
					contentPane.add(new JLabel("Phong Exponent: "       ),GBC.setGridPos(c,0,y++));
					contentPane.add(new JLabel("Cushioness: "           ),GBC.setGridPos(c,0,y++));
					contentPane.add(new JLabel("Cushion Height: "       ),GBC.setGridPos(c,0,y++));
					y=0; GBC.setWeights(c,1,0);
					contentPane.add(createColorbutton(config.selectedLeaf  , "Set Color of Selected File"  , color->config.selectedLeaf  =color), GBC.setGridPos(c,1,y++));
					contentPane.add(createColorbutton(config.selectedFolder, "Set Color of Selected Folder", color->config.selectedFolder=color), GBC.setGridPos(c,1,y++));
					contentPane.add(createColorbutton(config.material      , "Select Cushion Base Color"   , this::setMaterialColor), GBC.setGridPos(c,1,y++));
					contentPane.add(createDoubleTextField(config.materialPhongExp, this::setPhongExp), GBC.setGridPos(c,1,y++));
					contentPane.add(createSlider(10*Math.PI/180,config.alpha,Math.PI,value->config.alpha=value), GBC.setGridPos(c,1,y++));
					contentPane.add(createSlider(-5,Math.log(config.cushionHeightScale)/Math.log(2),1,value->config.cushionHeightScale=(float) Math.exp(value*Math.log(2))), GBC.setGridPos(c,1,y++));
					
					GBC.setGridPos(c,0,y++);
					GBC.setFill(c, GBC.GridFill.BOTH);
					GBC.setGridWidth(c,2);
					GBC.setWeights(c,1,1);
					contentPane.add(sunControl, c);
					
					startGUI(contentPane);
				}
				
				private void setMaterialColor(Color color    ) { config.material = color; cushionPainter.bumpMappingShading.setMaterialColor(color); }
				private void setPhongExp     (double phongExp) { config.materialPhongExp = phongExp; cushionPainter.bumpMappingShading.setPhongExp(phongExp); }
				
				private JSlider createSlider(double min, double value, double max, Consumer<Double> setValue) {
					int minInt = 0;
					int maxInt = 100;
					int valueInt = (int) ((value-min) * (maxInt-minInt)/(max-min) + minInt);
					JSlider comp = new JSlider(JSlider.HORIZONTAL,minInt,maxInt,valueInt);
					comp.addChangeListener(e->{
						if (comp.getValueIsAdjusting()) return;
						setValue.accept( (comp.getValue()-minInt) * (max-min)/(maxInt-minInt) + min );
						cushionPainter.updateNormalMap = true;
						cushionPainter.imageCache.resetImage();
						cushionView.repaint();
					});
					return comp;
				}
				
				private JButton createColorbutton(Color initColor, String dialogTitle, Consumer<Color> setcolor) {
					JButton colorbutton = HSColorChooser.createColorbutton(
							initColor, this, dialogTitle, HSColorChooser.PARENT_CENTER,
							color->{
								setcolor.accept(color);
								cushionPainter.imageCache.resetImage();
								cushionView.repaint();
							}
					);
					colorbutton.setPreferredSize(new Dimension(30,30));
					return colorbutton;
				}
				
				private JTextField createDoubleTextField(double value, Consumer<Double> setValue) {
					JTextField comp = new JTextField(Double.toString(value));
					Consumer<Double> modifiedSetValue = d->{
						setValue.accept(d);
						cushionPainter.imageCache.resetImage();
						cushionView.repaint();
					};
					Color defaultBG = comp.getBackground();
					comp.addActionListener(e->{ readTextField(comp,modifiedSetValue,defaultBG); });
					comp.addFocusListener(new FocusListener() {
						@Override public void focusLost(FocusEvent e) { readTextField(comp,modifiedSetValue,defaultBG); }
						@Override public void focusGained(FocusEvent e) {}
					});
					return comp;
				}
			
				private void readTextField(JTextField comp, Consumer<Double> setValue, Color defaultBG) {
					double d = parseDouble(comp.getText());
					if (Double.isNaN(d)) {
						comp.setBackground(Color.RED);
					} else {
						comp.setBackground(defaultBG);
						setValue.accept(d);
					}
				}
			
				private double parseDouble(String str) {
					try {
						return Double.parseDouble(str);
					} catch (NumberFormatException e) {
						return Double.NaN;
					}
				}
			}
		}
	}
	
	private static abstract class Layouter {
		private Painter painter = null;

		public void setPainter(Painter painter) {
			this.painter = painter;
			this.painter.setLayouter(this);
		}
		
		public void showConfig(CushionView cushionView) {}
		public boolean isConfigurable() { return false; }

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
	private static class GBC {
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

		@SuppressWarnings("unused")
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

		@SuppressWarnings("unused")
		static GridBagConstraints setInsets(GridBagConstraints c, int top, int left, int bottom, int right) {
			c.insets = new Insets(top, left, bottom, right);
			return c;
		}

		@SuppressWarnings("unused")
		static GridBagConstraints setLineEnd(GridBagConstraints c) {
			c.gridwidth = GridBagConstraints.REMAINDER;
			return c;
		}

		@SuppressWarnings("unused")
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
