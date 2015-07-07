# Drftpd2Stable
 Distributed FTP Daemon

## Installation steps
DrFTPD 2 installation requires a number of steps before you can utilize the software to its full extend.
To give an overview of the installation process the different steps are listed below in this section.


* On the master you will need to:
  * Install Sun JAVA 1.6 or higher (JDK)
  * Install ANT or Eclipse on the master
  * Add needed plugins that are not present
  * Compile the software using setup wizard
  * Rename .dist files to .conf
  * Configure .conf files
* On the slaves you will need to:
  * Install Sun JAVA 1.6 or higher (JRE)
  * Copy slave.zip to a slave from the master
  * Configure slave.conf


## Install java
* Download and install a java development kit 6 (JDK) on the master.
* Download and install a java runtime environment 6 (JRE) or java development kit 6 (JDK) on the slaves.

`
You can get sun's JDK here: http://www.oracle.com/technetwork/java/javase/downloads/index.html

If you want to utilize blowfish in your environment also download Java Cryptography Extension (JCE) Unlimited Strength Jurisdiction Policy Files 6.
You will need to manually replace the files local_policy.jar and US_export_policy.jar in your java/jre/lib/security folder.

## Problems
* Ensure that JAVA_HOME is configured. You can check this using
  * Windows:"echo %JAVA_HOME%"
  * NiX: "echo $JAVA_HOME"

*TiP: Don't use Sun JAVA versions between 1.6_02 and 1.6_12. They will cause an error after hours of usage: java.net.SocketException: Too many open files
These are issues with your Operating System/Java Install and not related to DrFTPD.

## Install ant
Compiling DrFTPD is required to use the software.
To allow you to compile java you will need to install ANT or ECLIPSE.
You can find the installation documentation here: http://ant.apache.org/manual/install.html

## Problems
* Ensure that ANT_HOME is configured. You can check this using with
  * Windows:"echo %ANT_HOME%"
  * NiX: "echo $ANT_HOME"
  
# Downloading

```
git clone git@github.com:g2x3k/Drftpd2Stable.git
cd Drftpd2Stable
./genkey.sh
./build.sh
```

edit conf/* accordingly and launch your ftpd ...
