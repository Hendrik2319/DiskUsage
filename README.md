# DiskUsage
`DiskUsage` is a tool to show content of a folder structure in a more graphical manner.  
It's inspired by a tool named [SequoiaView](https://sequoiaview.de.softonic.com/). And it's also the reason for my experimentation with bumpmapping, which resulted in the implementations included in [`JavaLib_Common_Imaging`](https://github.com/Hendrik2319/JavaLib_Common_Imaging).  
The tool can read the output of shell command `du -a {path} > {file}` and uses this format to store tree data in text files (-> "Stored Tree").

`DiskUsage` includes a subtool named [`DiskUsageCompare`](/src/net/schwarzbaer/java/tools/diskusagecompare/DiskUsageCompare.java), which is used to compare 2 folders or more precisely to compare 2 states of the same folder. These states can be read from stored folder tree data or can be scanned from HDD.

### Usage
You can download a release [here](https://github.com/Hendrik2319/DiskUsage/releases).  
You will need a JAVA 17 VM.

### Development
`DiskUsage` is configured as a Eclipse project.  
It depends on following libraries:
* [`JavaLib_Common_Essentials`](https://github.com/Hendrik2319/JavaLib_Common_Essentials)
* [`JavaLib_Common_Imaging`](https://github.com/Hendrik2319/JavaLib_Common_Imaging)
* [`JavaLib_Common_HSColorChooser`](https://github.com/Hendrik2319/JavaLib_Common_HSColorChooser)
* [`JavaLib_Common_Dialogs`](https://github.com/Hendrik2319/JavaLib_Common_Dialogs)

These libraries are imported as "project imports" in Eclipse. 
If you want to develop for your own and
* you use Eclipse as IDE,
	* then you should clone the projects above too and add them to the same workspace as the `DiskUsage` project.
* you use another IDE (e.q. VS Code)
	* then you should clone the said projects, build JAR files of them and add the JAR files as libraries.

### Screenshots
`DiskUsage`
![Screenshot 1 : DiskUsage](/github/screenshot1.png)

`DiskUsageCompare`
![Screenshot 2 : DiskUsageCompare](/github/screenshot2.png)
