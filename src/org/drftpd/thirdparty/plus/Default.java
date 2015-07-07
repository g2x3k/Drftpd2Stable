/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */

package org.drftpd.thirdparty.plus;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.drftpd.Bytes;

import org.drftpd.thirdparty.plus.config.PlusConfig;

/**
 * @author fr0w
 */

public class Default {

	private static final Logger logger = Logger.getLogger(Default.class);	
	private static final String confFile = "conf/defaultuser.conf";	
	private static Default _default;
	
	public Float RATIO= 3F;
	public Float MINRATIO = 3F;
	public Float MAXRATIO = 3F;
	public Integer MAXLOGINS = 0;
	public Integer MAXLOGINSIP = 0;            
	public Integer MAXSIMUP = 0;
	public Integer MAXSIMDN = 0;
	public Long WKLYALLOT = 0L;
	public Long CREDITS = 0L;
	public Integer IDLETIME = 300;
	public String TAGLINE = "No tagline set!";
	public String GROUP = "nogroup";
	
	public static Default getInstance() {
		if (_default == null)
			_default = new Default(PlusConfig.getPlusConfig().DEFAULTUSER);
		return _default;
	}
	
	/**
	 * Reload like
	 */
	public static void newInstance() {
		_default = new Default(PlusConfig.getPlusConfig().DEFAULTUSER);
	}
	
	private Default(boolean enable) {
		if (!enable)
			// dont load the conf
			return;
		
		try {
			readConf();
		} catch (FileNotFoundException e1) {
			logger.error("Ignoring settings. [Reason: File not found: " + confFile + "]");
		} catch (NullPointerException e2) {
			logger.error("Ignoring settings. [Reason: " + e2.getMessage() + "]");
		} catch (IOException e3) {
			logger.error("Ignoring settings. [Reason: Error reading: " + confFile + "]");
		} catch (NumberFormatException e4) {
			logger.error("Ignoring settings. [Reason: NumberFormatException " + e4.getMessage().toLowerCase() + "]");
		}
	}
	
	private void readConf() throws NullPointerException, IOException,
		FileNotFoundException, NumberFormatException {
		
		Properties cfg = new Properties();
		FileInputStream file = null;
		
		String ratio, minratio, maxratio, maxlogins, maxloginsip, 
		maxsimup, maxsimdn,	idletime, wklyallot, credits, tagline, group;
		
		try {
			file = new FileInputStream(confFile);
			cfg.load(file);
			
			ratio = cfg.getProperty("ratio");
			minratio = cfg.getProperty("minratio");
			maxratio = cfg.getProperty("maxratio");
			maxlogins = cfg.getProperty("max_logins");
			maxloginsip = cfg.getProperty("max_logins_ip");
			maxsimup = cfg.getProperty("max_uploads");
			maxsimdn = cfg.getProperty("max_downloads");
			idletime = cfg.getProperty("idle_time");  
			wklyallot= cfg.getProperty("wkly_allotment");
			credits = cfg.getProperty("credits");
			tagline = cfg.getProperty("tagline");
			group = cfg.getProperty("group");                
			
			if (ratio == null) { throw new NullPointerException("Unspecified value 'ratio' in " + confFile); }                   
			if (minratio == null) { minratio = ratio; }                   
			if (maxratio == null) { maxratio = minratio; }                   
			if (maxlogins == null) { throw new NullPointerException("Unspecified value 'max_logins' in " + confFile); }
			if (maxloginsip == null) { throw new NullPointerException("Unspecified value 'max_logins_ip' in " + confFile); }
			if (maxsimup == null) { throw new NullPointerException("Unspecified value 'max_uploads' in " + confFile); }
			if (maxsimdn == null) { throw new NullPointerException("Unspecified value 'max_downloads' in " + confFile); }
			if (wklyallot == null) { throw new NullPointerException("Unspecified value 'wkly_allotment' in " + confFile); }
			if (credits == null) { throw new NullPointerException("Unspecified value 'credits' in " + confFile); }
			if (idletime == null) { throw new NullPointerException("Unspecified value 'idle_time' in " + confFile); }
			if (tagline == null) { throw new NullPointerException("Unspecified value 'tagline' in " + confFile); }
			if (group == null) { throw new NullPointerException("Unspecified value 'group' in " + confFile); }
			
			RATIO = Float.parseFloat(ratio);
			MINRATIO = Float.parseFloat(minratio);
			MAXRATIO = Float.parseFloat(maxratio);
			MAXLOGINS = Integer.parseInt(maxlogins);
			MAXLOGINSIP = Integer.parseInt(maxloginsip);
			MAXSIMUP = Integer.parseInt(maxsimup);
			MAXSIMDN = Integer.parseInt(maxsimdn);
			IDLETIME = Integer.parseInt(idletime);  
			WKLYALLOT = Bytes.parseBytes(wklyallot);
			CREDITS = Bytes.parseBytes(credits);
			TAGLINE = tagline;
			GROUP = group;
			
		} catch (FileNotFoundException e1) {
			throw new FileNotFoundException(e1.getMessage());
		} catch (IOException e2) {
			throw new IOException(e2.getMessage());
		} catch (NumberFormatException e3) {
			throw new NumberFormatException(e3.getMessage());
		} finally {
        	if (file != null) {
        		try {
        			file.close();
        		} catch (IOException e) {
        		}
        	}
		}
	}
}
