package net.schwarzbaer.java.tools.diskusage;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;

import net.schwarzbaer.gui.BumpmappingSunControl;
import net.schwarzbaer.gui.HSColorChooser;
import net.schwarzbaer.gui.StandardMainWindow;
import net.schwarzbaer.image.BumpMapping;
import net.schwarzbaer.image.BumpMapping.Normal;
import net.schwarzbaer.image.BumpMapping.Shading.MaterialShading;
import net.schwarzbaer.image.ImageCache;
import net.schwarzbaer.java.tools.diskusage.DiskUsage.GBC;
import net.schwarzbaer.java.tools.diskusage.FileMap.MapItem;
import net.schwarzbaer.java.tools.diskusage.Painter.CushionPainter.CushionContour.Variant;

abstract class Painter {
	
	protected Layouter layouter = null;
	public void setLayouter(Layouter layouter) {
		this.layouter = layouter;
	}
	public boolean isConfigurable() { return false; }
	public void showConfig(FileMap fileMap) {}
	public void hideConfig() {}
	public void forceUpdate() {}
	
	public abstract void paintAll(MapItem root, Graphics g, int x, int y, int width, int height);
	public abstract void paintMapItem(MapItem mapItem, Graphics g, int x, int y, int width, int height);
	
	enum Type {
		RectanglePainter ("Rectangle Painter"  ,Painter.RectanglePainter ::new),
		RectanglePainter2("Rectangle Painter 2",Painter.RectanglePainter2::new),
		CushionPainter   ("Cushion Painter"    ,Painter.CushionPainter   ::new),
		;
		String title;
		Supplier<Painter> createPainter;
		private Type(String title, Supplier<Painter> createPainter) {
			this.title = title;
			this.createPainter = createPainter;
		}
	}
	
	
	static class RectanglePainter extends Painter {
		
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
		public void paintAll(MapItem root, Graphics g, int x, int y, int width, int height) {
			layouter.layoutMapItem(root,g,x,y,width,height);
		}
		@Override
		public void paintMapItem(MapItem mapItem, Graphics g, int x, int y, int width, int height) {
			g.setColor(getColor(mapItem));
			g.drawRect(x, y, width-1, height-1);
		}
		protected Color getColor(MapItem mapItem) {
			if (mapItem.isHighlighted) {
				if (mapItem.children.length==0) return selectedLeaf;
				return selectedFolder;
			}
			return colors[mapItem.level%colors.length];
		}
	}
	
	static class RectanglePainter2 extends Painter.RectanglePainter {
		RectanglePainter2() {
			super(Color.GREEN, Color.BLUE, new Color[] { Color.ORANGE, Color.DARK_GRAY, Color.PINK });
		}
	}
	
	
	static class CushionPainter extends Painter {
		
		final static CushionPainter.Config config = new Config();
		
		private final ImageCache<BufferedImage> imageCache;
		private final BumpMapping bumpMapping;
		private final MaterialShading bumpMappingShading;
		
		private float[][] tempHeightMap;
		private CushionContour tempCushionContour;
		private int xOffset;
		private int yOffset;
		private boolean updateNormalMap = true;

		private ConfigGUI configGUI = null;

		private Color[][] tempColorMap;

		CushionPainter() {
			bumpMappingShading = new MaterialShading(config.sun, config.materialColor, 0, config.materialPhongExp);
			bumpMapping = new BumpMapping(false);
			bumpMapping.setShading(bumpMappingShading);
			imageCache = new ImageCache<>(bumpMapping::renderImage_uncached);
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
		@Override public void showConfig(FileMap fileMap) {
			if (configGUI==null)
				configGUI = new ConfigGUI(fileMap);
			else
				configGUI.setVisible(true);
		}
		
		@Override
		public void forceUpdate() {
			imageCache.resetImage();
			updateNormalMap = true;
		}

		@Override
		public void paintAll(MapItem root, Graphics g, int x, int y, int width, int height) {
			if (imageCache.getWidth()!=width || imageCache.getHeight()!=height || updateNormalMap) {
				updateNormalMap = false;
				tempCushionContour = config.cushionContour.create.apply(config.cushioness);
				tempHeightMap = new float[width][height];
				tempColorMap  = new Color[width][height];
				for (float[] column:tempHeightMap) Arrays.fill(column,0);
				for (Color[] column:tempColorMap ) Arrays.fill(column,null);
				xOffset = x;
				yOffset = y;
				layouter.layoutMapItem(root,null,x,y,width,height);
				bumpMapping.setHeightMap(tempHeightMap, tempColorMap, 0);
				tempCushionContour = null;
				tempHeightMap = null;
				tempColorMap  = null;
			}
			g.drawImage(imageCache.getImage(width,height), x, y, null);
			drawHighlighted(root,g);
		}

		private void drawHighlighted(MapItem mapItem, Graphics g) {
			if (mapItem.isHighlighted) {
				if (mapItem.children.length==0) g.setColor(config.selectedFile);
				else g.setColor(config.selectedFolder);
				Rectangle b = mapItem.screenBox;
				g.drawRect(b.x, b.y, b.width-1, b.height-1);
				for (MapItem child:mapItem.children)
					drawHighlighted(child,g);
			}
		}

		@Override
		public void paintMapItem(MapItem mapItem, Graphics g, int x, int y, int width, int height) {
			if (width<2 || height<2) return;
			float cushionHeight = Math.min(width,height)*config.cushionHeightScale;
			double xm = (width -1)*0.5;
			double ym = (height-1)*0.5;
			for (int x1=0; x1<width; ++x1) {
				float hX = tempCushionContour.getNormalizedHeight(x1/xm);
				for (int y1=0; y1<height; ++y1) {
					float hY = tempCushionContour.getNormalizedHeight(y1/ym);
					tempHeightMap[x+x1-xOffset][y+y1-yOffset] += cushionHeight * hX * hY;
					tempColorMap [x+x1-xOffset][y+y1-yOffset] = mapItem.diskItem.getColor();
				}
			}
		}
		
		static abstract class CushionContour {
			protected CushionContour(double cushioness) { setCushioness(cushioness); }
			public abstract void setCushioness(double cushioness); // cushioness: 0..1
			public abstract float getNormalizedHeight(double v); // v: 0..2
			
			public enum Variant {
				SphereSegment("Sphere Segment",CushionContour.SphereSegment::new),
				Polynomial   ("Polynomial"    ,CushionContour.Polynomial   ::new),
				;
				private String label;
				private Function<Double,CushionContour> create;
				Variant(String label, Function<Double,CushionContour> create) {
					this.label = label;
					this.create = create;
				}
				@Override public String toString() { return label; }
			}
			
			private static class Polynomial extends CushionContour {
				private double exponent;
				Polynomial(double cushioness) { super(cushioness); }
				
				@Override public void setCushioness(double cushioness) {
					exponent = cushioness*10+1;
				}
				@Override public float getNormalizedHeight(double v) {
					return (float) (1-Math.exp(Math.log(Math.abs(v-1))*exponent));
				}
			}
			
			private static class SphereSegment extends CushionContour {
				private double cosA2;
				private double sin2A2;
				SphereSegment(double cushioness) { super(cushioness); }
				
				@Override public void setCushioness(double cushioness) {
					double alpha = (cushioness*0.9+0.1)*Math.PI;
					cosA2 = Math.cos(alpha/2);
					sin2A2 = Math.sin(alpha/2)*Math.sin(alpha/2);
				}
				@Override public float getNormalizedHeight(double v) {
					return (float) ((Math.sqrt(1-(v-1)*(v-1)*sin2A2)-cosA2)/(1-cosA2));
				}
			}
		}
		
		static class Config {
			Normal sun;
			Color   selectedFile;
			Color   selectedFolder;
			Color   materialColor;
			double  materialPhongExp;
			Variant cushionContour;
			double  cushioness;
			float   cushionHeightScale;
			boolean automaticSaving;
			
			Config() {
				this(Color.YELLOW, Color.ORANGE, new Color(0xafafaf));
			}
			Config(Color selectedFile, Color selectedFolder, Color materialColor) {
				this.selectedFile = selectedFile;
				this.selectedFolder = selectedFolder;
				this.materialColor = materialColor;
				this.materialPhongExp = 40;
				this.cushioness = 0.5;
				this.cushionContour = Variant.SphereSegment;
				this.cushionHeightScale = 1/16f;
				this.sun = new Normal(1,-1,2).normalize();
				this.automaticSaving = false;
			}
			
		}

		private class ConfigGUI extends StandardMainWindow {
			private static final long serialVersionUID = 7270004938108015260L;
			private FileMap fileMap;
			private JButton saveChangesButton;
		
			public ConfigGUI(FileMap fileMap) {
				super("Config for Cushion Painter",StandardMainWindow.DefaultCloseOperation.HIDE_ON_CLOSE);
				this.fileMap = fileMap;
				
				BumpmappingSunControl sunControl = new BumpmappingSunControl(config.sun.x, config.sun.y, config.sun.z);
				sunControl.setPreferredSize(new Dimension(300,300));
				sunControl.addValueChangeListener((x,y,z)->{
					config.sun = new Normal(x,y,z);
					bumpMapping.setSun(x,y,z);
					valueChanged(sunControl.isAdjusting(),false);
				});
				
				GridBagConstraints c = new GridBagConstraints();
				JPanel valuePanel = new JPanel(new GridBagLayout());
				
				int y;
				GBC.setFill(c, GBC.GridFill.HORIZONTAL);
				y=0; GBC.setWeights(c,0,0);
				valuePanel.add(new JLabel("Selected File Color: "  ),GBC.setGridPos(c,0,y++));
				valuePanel.add(new JLabel("Selected Folder Color: "),GBC.setGridPos(c,0,y++));
				valuePanel.add(new JLabel("Cushion Base Color: "   ),GBC.setGridPos(c,0,y++));
				valuePanel.add(new JLabel("Phong Exponent: "       ),GBC.setGridPos(c,0,y++));
				valuePanel.add(new JLabel("Cushion Contour: "      ),GBC.setGridPos(c,0,y++));
				valuePanel.add(new JLabel("Cushioness: "           ),GBC.setGridPos(c,0,y++));
				valuePanel.add(new JLabel("Cushion Height: "       ),GBC.setGridPos(c,0,y++));
				y=0; GBC.setWeights(c,1,0);
				valuePanel.add(createColorbutton(config.selectedFile  , "Set Color of Selected File"  , color->{ config.selectedFile  =color; valueChanged(false,false); }), GBC.setGridPos(c,1,y++));
				valuePanel.add(createColorbutton(config.selectedFolder, "Set Color of Selected Folder", color->{ config.selectedFolder=color; valueChanged(false,false); }), GBC.setGridPos(c,1,y++));
				valuePanel.add(createColorbutton(config.materialColor , "Select Cushion Base Color"   , this::setMaterialColor), GBC.setGridPos(c,1,y++));
				valuePanel.add(createDoubleTextField(config.materialPhongExp, this::setPhongExp), GBC.setGridPos(c,1,y++));
				
				valuePanel.add(createComboBox(CushionContour.Variant.values(),config.cushionContour,value->{ config.cushionContour=value; valueChanged(false,true); }), GBC.setGridPos(c,1,y++));
				valuePanel.add(createSlider(0,config.cushioness,1,value->{ config.cushioness=value; valueChanged(false,true); }), GBC.setGridPos(c,1,y++));
				valuePanel.add(createSlider(-5,Math.log(config.cushionHeightScale)/Math.log(2),1,value->{ config.cushionHeightScale=(float) Math.exp(value*Math.log(2)); valueChanged(false,true); }), GBC.setGridPos(c,1,y++));
				
				GBC.setGridPos(c,0,y++);
				GBC.setFill(c, GBC.GridFill.BOTH);
				GBC.setGridWidth(c,2);
				GBC.setWeights(c,1,1);
				valuePanel.add(sunControl, c);
				
				JPanel buttonPanel = new JPanel(new GridBagLayout());
				GBC.reset(c); GBC.setWeights(c,1,0);
				buttonPanel.add(new JLabel(), c);
				GBC.setWeights(c,0,0);
				buttonPanel.add(createCheckBox("Automatic Saving",config.automaticSaving,b->{ config.automaticSaving=b; valueChanged(false,false); }), c);
				buttonPanel.add(saveChangesButton = createButton("Save Changes",e->saveConfig()), c);
				saveChangesButton.setEnabled(false);
				
				JPanel contentPane = new JPanel(new BorderLayout(3,3));
				contentPane.setBorder(BorderFactory.createEmptyBorder(3,3,3,3));
				contentPane.add(valuePanel,BorderLayout.CENTER);
				contentPane.add(buttonPanel,BorderLayout.SOUTH);
				
				startGUI(contentPane);
			}

			private void setMaterialColor(Color color    ) { config.materialColor    = color   ; bumpMappingShading.setMaterialColor(color); valueChanged(false,false); }
			private void setPhongExp     (double phongExp) { config.materialPhongExp = phongExp; bumpMappingShading.setPhongExp(phongExp);   valueChanged(false,false); }

			private void valueChanged(boolean isAdjusting, boolean updateNormalMap_) {
				if (!isAdjusting && config.automaticSaving) saveConfig();
				else saveChangesButton.setEnabled(true);
				updateNormalMap = updateNormalMap_;
				imageCache.resetImage();
				fileMap.repaint();
			}

			private void saveConfig() {
				fileMap.guiContext.saveConfig();
				saveChangesButton.setEnabled(false);
			}

			private <E> JComboBox<E> createComboBox(E[] items, E selectedItem, Consumer<E> setValue) {
				JComboBox<E> comp = new JComboBox<>(items);
				comp.setSelectedItem(selectedItem);
				comp.addActionListener(e->{
					int i = comp.getSelectedIndex();
					if (i<0) setValue.accept(null);
					else setValue.accept(items[i]);
				});
				return comp;
			}

			private JCheckBox createCheckBox(String title, boolean isSelected, Consumer<Boolean> setValue) {
				JCheckBox comp = new JCheckBox(title,isSelected);
				comp.addActionListener(e->setValue.accept(comp.isSelected()));
				return comp;
			}

			private JButton createButton(String title, ActionListener al) {
				JButton comp = new JButton(title);
				comp.addActionListener(al);
				return comp;
			}
			
			private JSlider createSlider(double min, double value, double max, Consumer<Double> setValue) {
				int minInt = 0;
				int maxInt = 100;
				int valueInt = (int) ((value-min) * (maxInt-minInt)/(max-min) + minInt);
				JSlider comp = new JSlider(JSlider.HORIZONTAL,minInt,maxInt,valueInt);
				comp.addChangeListener(e->{
					if (comp.getValueIsAdjusting()) return;
					setValue.accept( (comp.getValue()-minInt) * (max-min)/(maxInt-minInt) + min );
				});
				return comp;
			}
			
			private JButton createColorbutton(Color initColor, String dialogTitle, Consumer<Color> setcolor) {
				JButton colorbutton = HSColorChooser.createColorbutton(
						initColor, this, dialogTitle, HSColorChooser.PARENT_CENTER, setcolor::accept
				);
				colorbutton.setPreferredSize(new Dimension(30,30));
				return colorbutton;
			}
			
			private JTextField createDoubleTextField(double value, Consumer<Double> setValue) {
				JTextField comp = new JTextField(Double.toString(value));
				Color defaultBG = comp.getBackground();
				comp.addActionListener(e->{ readTextField(comp,setValue,defaultBG); });
				comp.addFocusListener(new FocusListener() {
					@Override public void focusLost(FocusEvent e) { readTextField(comp,setValue,defaultBG); }
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