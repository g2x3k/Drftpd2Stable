/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.thirdparty.plus.config;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Properties;

import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.master.config.FtpConfig;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.permissions.Permission;
import org.drftpd.thirdparty.plus.Default;
import org.drftpd.usermanager.User;

import com.Ostermiller.util.StringTokenizer;

/**
 * @author fr0w
 */

public class PlusConfig extends FtpListener {

	private static final String confFile = "conf/plus.conf";
	private static final Logger logger = Logger.getLogger(PlusConfig.class);
	private static PlusConfig _plus;

	private Properties _cfg;

	public boolean SECUREMASK = false;
	public String SECUREEXEMPT = "";
	public boolean DEFAULTUSER = false;

	private Hashtable<String, ArrayList<Permission>> _perms = new Hashtable<String, ArrayList<Permission>>();

	/**
	 * This is the unique way you can fetch the PlusConfig object.
	 * @return PlusConfig
	 */
	public static PlusConfig getPlusConfig() {
		if (_plus == null)
			// should never happen, since we are loading the object when the daemon is started (FtpListener)
			_plus = new PlusConfig();
		return _plus;
	}

	/**
	 * This come necessary in order to force the object being loaded only once.
	 * @param plus
	 */
	private static void setInstance(PlusConfig plus) {
		_plus = plus;
	}

	/**
	 * This constructor is needed in order to make PlusConfig implements FtpListener
	 * in the right way. Although it is pretty useless it just pass some information to
	 * PlusConfig(dummyValue), that's where the real fun begins.
	 */
	public PlusConfig() {
		this(true);
	}

	/**
	 * Default Singleton Constructor, it only called by getPlusConfig()
	 * @param dummyValue
	 */
	private PlusConfig(boolean dummyValue) {
		try {
			readConf();
		} catch (Exception e) {
			logger.error("Ignoring settings." + e.getMessage(), e);
		}
		setInstance(this);

	}

	/**
	 * Another FtpListener thing.
	 */
	public void init(GlobalContext gctx) {
		super.init(gctx);
	}

	/**
	 * Reads plus.conf.
	 * @throws NullPointerException
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	private void readConf() throws NullPointerException, IOException,
	FileNotFoundException {

		_cfg = new Properties();
		FileInputStream file = null;

		String securemask, defaultuser, secureexempt, listGroupShow, listMaskedGroups;

		// reset when conf is reloaded
		_perms = new Hashtable<String, ArrayList<Permission>>();


		try {
			file = new FileInputStream(confFile);
			_cfg.load(file);

			securemask = _cfg.getProperty("securemask");
			secureexempt = _cfg.getProperty("securemask.bypass");
			defaultuser = _cfg.getProperty("defaultuser");

			if (securemask == null) { throw new NullPointerException("Unspecified value 'securemask' in " + confFile); }
			if (secureexempt == null) { throw new NullPointerException("Unspecified value 'securemask.bypass' in " + confFile);}
			if (defaultuser == null) { throw new NullPointerException("Unspecified value 'defaultuser' in " + confFile); }

			SECUREMASK = Boolean.parseBoolean(securemask);
			SECUREEXEMPT = secureexempt;
			DEFAULTUSER = Boolean.parseBoolean(defaultuser);

		} catch (FileNotFoundException e1) {
			throw new FileNotFoundException(e1.getMessage());
		} catch (IOException e2) {
			throw new IOException(e2.getMessage());
		} finally {
			if (file != null) {
				try {
					file.close();
				} catch (IOException e) {
				}
			}
		}
	}

	/**
	 * When 'site reload' is trigered, an event is dispatched, PlusConfig handles this event
	 * and then reloads the configuration, here and only here the configuration should be read.
	 */
	public void actionPerformed(Event event) {
		if (event.getCommand().equals("RELOAD")) {
			resetSettings();
			try {
				readConf();
			} catch (Exception e) {
				logger.error("Ignoring settings." + e.getMessage(), e);
			}

			// reload the plugins.
			Default.newInstance();
		}
	}

	/**
	 * Reset all setting to default.
	 */
	public void resetSettings() {
		SECUREMASK = false;
		SECUREEXEMPT = "";
		DEFAULTUSER = false;
	}

	/**
	 * @return The plus.conf properties fields.
	 */
	public Properties getProperties() {
		return _cfg;
	}

	public boolean checkPermission(String key, User fromUser) {
		return checkPermission(key, fromUser, false);
	}

	public boolean checkPermission(String key, User fromUser, boolean defaults) {
		Collection coll = ((Collection) _perms.get(key));

		if (coll == null) {
			return defaults;
		}

		Iterator iter = coll.iterator();

		while (iter.hasNext()) {
			Permission perm = (Permission) iter.next();
			return perm.check(fromUser);
		}

		return defaults;
	}

    public void addPermission(String key, Permission permission) {
        ArrayList<Permission> perms = _perms.get(key);

        if (perms == null) {
            perms = new ArrayList<Permission>();
            _perms.put(key, perms);
        }
        perms.add(permission);
	}
}
