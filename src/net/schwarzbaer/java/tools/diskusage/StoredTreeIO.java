package net.schwarzbaer.java.tools.diskusage;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.swing.JFileChooser;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import net.schwarzbaer.java.lib.gui.FileChooser;

public class StoredTreeIO
{
	private static final String ZipEntry_CONTENT = "content";
	private static final String EXT_DISKUSAGE = "diskusage";
	
	public static void main(String[] args)
	{
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {}
		
		FileChooser fileChooser = createFileChooser(true);
		
		if (fileChooser.showOpenDialog(null)==JFileChooser.APPROVE_OPTION)
		{
			File[] files = fileChooser.getSelectedFiles();
			for (int i=0; i<files.length; i++)
			{
				File infile = files[i];
				System.out.printf("[%02d] \"%s\"", i+1, infile.getAbsolutePath());
				
				String name = infile.getName();
				if (name.toLowerCase().endsWith("."+EXT_DISKUSAGE))
					name = name.substring(0, name.length()-EXT_DISKUSAGE.length()-1);
				
				File outfile = new File(infile.getParentFile(), String.format("%s.zipped.%s", name, EXT_DISKUSAGE));
				for (int j=0; outfile.exists(); j++)
					outfile = new File(infile.getParentFile(), String.format("%s.zipped%03d.%s", name, (j+1), EXT_DISKUSAGE));
				
				System.out.printf(" -> \"%s\"%n", outfile.getName());
				
				List<String> lines = readFile(infile, ()->false);
				if (lines == null)
					System.err.printf("Can't read file \"%s\".%n", infile.getAbsolutePath());
				else
					writeFile(outfile, lines, ()->false);
			}
		}
	}

	static FileChooser createFileChooser()
	{
		return createFileChooser(false);
	}

	static FileChooser createFileChooser(boolean allowMultiSelection)
	{
		FileChooser fileChooser = new FileChooser("Stored Tree", EXT_DISKUSAGE);
		fileChooser.setMultiSelectionEnabled(allowMultiSelection);
		return fileChooser;
	}

	static List<String> readFile(File infile, Supplier<Boolean> shouldAbort)
	{
		Boolean isZipped = isZipped(infile);
		if (isZipped==null)
		{
			System.err.printf("Can't determine type of file \"%s\".%n", infile.getAbsolutePath());
			return null;
		}
		
		if (shouldAbort.get())
			return null;
		
		if (isZipped)
			return readZippedFile(infile);
		else
			return readPlainTextFile(infile);
	}

	private static Boolean isZipped(File file)
	{
		try (FileInputStream testIn = new FileInputStream(file);) {
			int ch;
			ch = testIn.read(); if (ch!=(int)'P') return false;
			ch = testIn.read(); if (ch!=(int)'K') return false;
			ch = testIn.read(); if (ch!=3       ) return false;
			ch = testIn.read(); if (ch!=4       ) return false;
			return true;
		}
		catch (FileNotFoundException e) { e.printStackTrace(); }
		catch (IOException e)
		{
			System.err.printf("IOException while reading file \"%s\" for format detection: %s.%n", file.getAbsolutePath(), e.getMessage());
			// e.printStackTrace();
		}
		return null;
	}



	private static List<String> readPlainTextFile(File file)
	{
		try {
			return Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.err.printf("IOException while reading file \"%s\": %s.%n", file.getAbsolutePath(), e.getMessage());
			//e.printStackTrace();
			return null;
		}
	}
	
	private static List<String> readZippedFile(File file)
	{
		try (ZipFile zipFile = new ZipFile(file))
		{
			InputStream stream = zipFile.getInputStream(new ZipEntry(ZipEntry_CONTENT));
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
			return reader.lines().toList();
		}
		catch (ZipException e)
		{
			System.err.printf("ZipException while reading file \"%s\": %s.%n", file.getAbsolutePath(), e.getMessage());
			//e.printStackTrace();
		}
		catch (IOException e)
		{
			System.err.printf("IOException while reading file \"%s\": %s.%n", file.getAbsolutePath(), e.getMessage());
			//e.printStackTrace();
		}
		return null;
	}
	
	static void writeFile(File file, List<String> lines, Supplier<Boolean> shouldAbort)
	{
		try(ZipOutputStream zipout = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file)), StandardCharsets.UTF_8)) {
			zipout.putNextEntry(new ZipEntry(ZipEntry_CONTENT));
			PrintWriter writer = new PrintWriter(zipout);
			for (String line : lines)
				if (!shouldAbort.get())
					writer.println(line);
			writer.flush();
			zipout.closeEntry();
		}
		catch (FileNotFoundException e) 
		{
			System.err.printf("FileNotFoundException while writing file \"%s\": %s.%n", file.getAbsolutePath(), e.getMessage());
			//e.printStackTrace();
		}
		catch (IOException e)
		{
			System.err.printf("IOException while writing file \"%s\": %s.%n", file.getAbsolutePath(), e.getMessage());
			//e.printStackTrace();
		}
	}
}
