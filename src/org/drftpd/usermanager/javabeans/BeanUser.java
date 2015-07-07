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
package org.drftpd.usermanager.javabeans;

import java.beans.XMLEncoder;
import java.io.IOException;
import java.io.Serializable;

import org.apache.log4j.Logger;
import org.drftpd.commands.UserManagement;
import org.drftpd.io.SafeFileOutputStream;
import org.drftpd.usermanager.AbstractUser;
import org.drftpd.usermanager.AbstractUserManager;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.usermanager.UserManager;

/**
 * @author mog
 * @version $Id: BeanUser.java 1765 2007-08-04 04:14:28Z tdsoul $
 */
public class BeanUser extends AbstractUser implements Serializable {

	private BeanUserManager _um;
	private String _password = "";
	private boolean _purged;
	private static final Logger logger = Logger.getLogger(BeanUser.class);

	public BeanUser(String username) {
		super(username);
	}

	public BeanUser(BeanUserManager manager, String username) {
		super(username);
		_um = manager;
	}

	public AbstractUserManager getAbstractUserManager() {
		return _um;
	}

	public UserManager getUserManager() {
		return _um;
	}

	public void commit() throws UserFileException {
		if(_purged) return;
		XMLEncoder out = null;
		try {
			out = _um.getXMLEncoder(new SafeFileOutputStream(_um.getUserFile(getName())));
			out.writeObject(this);
		} catch (IOException ex) {
			throw new UserFileException(ex);
		} finally {
				if(out != null) out.close();
		}
	}

	public void purge() {
		_purged = true;
		_um.delete(getName());
	}

	public String getPassword() {
		return _password;
	}

	public void setPassword(String password) {
		_password = password;
	}

	public void setUserManager(BeanUserManager manager) {
		_um = manager;
	}

	/**
	 * Setter for userfile backwards comptibility.
	 * Should work but i had nothing to test with.
	 */
//    public void setGroupSlots(int s) {
//    	getKeyedMap().setObject(UserManagement.GROUPSLOTS, s);
//    }

    /**
	 * Setter for userfile backwards comptibility.
	 * Should work but i had nothing to test with.
	 */
	public void setGroupLeechSlots(short s) {
    	getKeyedMap().setObject(UserManagement.LEECHSLOTS, s);		
	}
}
