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
package net.sf.drftpd.master.usermanager.jsx;

import JSX.ObjIn;

import net.sf.drftpd.DuplicateElementException;
import net.sf.drftpd.FatalException;
import net.sf.drftpd.FileExistsException;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import org.drftpd.GlobalContext;
import org.drftpd.commands.UserManagement;
import org.drftpd.dynamicdata.KeyNotFoundException;

import org.drftpd.master.ConnectionManager;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserExistsException;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.usermanager.UserManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;


/**
 * @author mog
 * @version $Id: JSXUserManager.java 1765 2007-08-04 04:14:28Z tdsoul $
 */
public class JSXUserManager implements UserManager {
    private static final Logger logger = Logger.getLogger(JSXUserManager.class.getName());
    private GlobalContext _gctx;
    String userpath = "users/jsx/";
    File userpathFile = new File(userpath);
    Hashtable users = new Hashtable();

    public JSXUserManager() throws UserFileException {
        if (!userpathFile.exists() && !userpathFile.mkdirs()) {
            throw new UserFileException(new IOException(
                    "Error creating folders: " + userpathFile));
        }

        String[] userfilenames = userpathFile.list();
        int numUsers = 0;

        for (int i = 0; i < userfilenames.length; i++) {
            String string = userfilenames[i];

            if (string.endsWith(".xml")) {
                numUsers++;
            }
        }

        if (numUsers == 0) {
            User user = create("drftpd");
            user.setGroup("drftpd");
            user.setPassword("drftpd");
            user.getKeyedMap().setObject(UserManagement.RATIO, new Float(0));

            try {
                user.addIPMask("*@127.0.0.1");
                user.addIPMask("*@0:0:0:0:0:0:0:1");
            } catch (DuplicateElementException e) {
            }

            try {
                user.addSecondaryGroup("siteop");
            } catch (DuplicateElementException e1) {
            }

            user.commit();
        }
    }

    public User create(String username) throws UserFileException {
        try {
            getUserByName(username);

            //bad
            throw new FileExistsException("User already exists");
        } catch (IOException e) {
            //bad
            throw new UserFileException(e);
        } catch (NoSuchUserException e) {
            //good
        }

        JSXUser user = new JSXUser(this, username);
        users.put(user.getName(), user);

        return user;
    }

    public boolean exists(String username) {
        return getUserFile(username).exists();
    }

    public Collection getAllGroups() throws UserFileException {
        Collection users = this.getAllUsers();
        ArrayList ret = new ArrayList();

        for (Iterator iter = users.iterator(); iter.hasNext();) {
            User myUser = (User) iter.next();
            Collection myGroups = myUser.getGroups();

            for (Iterator iterator = myGroups.iterator(); iterator.hasNext();) {
                String myGroup = (String) iterator.next();

                if (!ret.contains(myGroup)) {
                    ret.add(myGroup);
                }
            }
        }

        return ret;
    }

    public Collection getAllUsers() throws UserFileException {
        ArrayList users = new ArrayList();

        String[] userpaths = userpathFile.list();

        for (int i = 0; i < userpaths.length; i++) {
            String userpath = userpaths[i];

            if (!userpath.endsWith(".xml")) {
                continue;
            }

            String username = userpath.substring(0,
                    userpath.length() - ".xml".length());

            try {
                users.add((JSXUser) getUserByNameUnchecked(username));

                // throws IOException
            } catch (NoSuchUserException e) {
            } // continue
        }

        return users;
    }

    public Collection getAllUsersByGroup(String group)
        throws UserFileException {
        Collection users = getAllUsers();

        for (Iterator iter = users.iterator(); iter.hasNext();) {
            JSXUser user = (JSXUser) iter.next();

            if (!user.isMemberOf(group)) {
                iter.remove();
            }
        }

        return users;
    }

    public User getUserByNameUnchecked(String username)
        throws NoSuchUserException, UserFileException {
        try {
            JSXUser user = (JSXUser) users.get(username);

            if (user != null) {
                return user;
            }

            ObjIn in;

            try {
                in = new ObjIn(new FileReader(getUserFile(username)));
            } catch (FileNotFoundException ex) {
                throw new NoSuchUserException("No such user");
            }

            try {
                user = (JSXUser) in.readObject();

                //throws RuntimeException
                user.setUserManager(this);
                users.put(user.getName(), user);
                //user.reset(getGlobalContext());

                return user;
            } catch (ClassNotFoundException e) {
                throw new FatalException(e);
            } finally {
                in.close();
            }
        } catch (Throwable ex) {
            if (ex instanceof NoSuchUserException) {
                throw (NoSuchUserException) ex;
            }

            throw new UserFileException("Error loading " + username, ex);
        }
    }

	private GlobalContext getGlobalContext() {
		return _gctx;
	}

	public User getUserByName(String username)
        throws NoSuchUserException, UserFileException {
        JSXUser user = (JSXUser) getUserByNameUnchecked(username);

        if (user.isDeleted()) {
            throw new NoSuchUserException(user.getName() + " is deleted");
        }

        user.reset(getGlobalContext());

        return user;
    }

    public File getUserFile(String username) {
        return new File(userpath + username + ".xml");
    }

    void remove(JSXUser user) {
        this.users.remove(user.getName());
    }

    void rename(JSXUser oldUser, String newUsername) throws UserExistsException {
        if (users.contains(newUsername)) {
            throw new UserExistsException("user " + newUsername + " exists");
        }

        users.remove(oldUser.getName());
        users.put(newUsername, oldUser);
    }

    public void saveAll() throws UserFileException {
        logger.log(Level.INFO, "Saving userfiles");

        for (Iterator iter = users.values().iterator(); iter.hasNext();) {
            Object obj = iter.next();

            if (!(obj instanceof JSXUser)) {
                throw new ClassCastException("Only accepts JSXUser objects");
            }

            JSXUser user = (JSXUser) obj;
            user.commit();
        }
    }

    public void init(GlobalContext gctx) {
        _gctx = gctx;
    }

	public User getUserByNameIncludeDeleted(String argument) throws NoSuchUserException, UserFileException {
		return getUserByName(argument);
	}

    public User getUserByIdent(String ident) throws NoSuchUserException, UserFileException {
        for (Iterator iter = getAllUsers().iterator(); iter.hasNext();) {
		    User user = (User) iter.next();
	        try {
                String uident = (String) user.getKeyedMap().getObject(UserManagement.IRCIDENT);
                if (uident.equals(ident)) {
                    return user;
                }
            } catch (KeyNotFoundException e1) {
            }	       
		}
        throw new NoSuchUserException("No user found with ident = " + ident);
    }
}
