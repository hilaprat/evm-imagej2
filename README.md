Eulerian Video Magnification Plugin
===========================================

This is EVM plugin for ImageJ2 platform

Installation
----

***Installing IIRJ package*** 

In order to work with EVM plugin, IIR package needs to be available in Fiji.app.
You can download iirj version 1.5 from here: https://search.maven.org/artifact/uk.me.berndporr/iirj/1.5/jar
Then, move the jar file to Fiji.app/jars directory.

***Installing EVM plugin***

Download EVM_\<version\>.jar from target directory of this repository. 
Move the file to Fiji.app/plugins directory.

Now you can run Fiji.app and use EVM plugin according to the Usage segment.


Usage
----

**Open video**
  
  Open Fiji app and open the video you want to magnify. For example, by using File > Open.

**Choose EVM Plugin**

  Click: Plugins > Video Magnification > EVM
  
  <img width="461" alt="image" src="https://user-images.githubusercontent.com/75682535/147559162-ba9a20e2-aede-42d3-9e7c-6a4bf4c90bbd.png">

**Insert Input Parameters**

  Enter the video sample frequency, the frequency you would like to magnify and the amplification factor.
  Click Should crop output values between 0-255 if you want the output to be cropped to normal video values. Otherwise, the algorithm output value rage will be shown.
  
  <img width="366" alt="image" src="https://user-images.githubusercontent.com/75682535/147559502-d2a03773-0ec3-4696-ba47-88353182de94.png">

  Click OK

**Plugin Output**
  
  You can see plugin progress in Window > Console.

  <img width="461" alt="image" src="https://user-images.githubusercontent.com/75682535/147559279-71ebccd7-b6aa-40f5-9522-3eb9a5168e71.png">
  
  The amplified video will apear as soon as the plugin is finished.


