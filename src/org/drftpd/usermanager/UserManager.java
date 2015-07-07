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
package org.drftpd.usermanager;


import java.util.Collection;

import org.drftpd.GlobalContext;


/**
 * @author mog
 * @version $Id: UserManager.java 1765 2007-08-04 04:14:28Z tdsoul $
 */
public interface UserManager {
    public abstract User create(String username) throws UserFileException;

    public abstract Collection getAllGroups() throws UserFileException;

    /**
     * Get all user names in the system.
     */
    public abstract Collection<User> getAllUsers() throws UserFileException;

    public abstract Collection getAllUsersByGroup(String group)
        throws UserFileException;

    /**
     * Get user by name.
     */
    public abstract User getUserByName(String username)
        throws NoSuchUserException, UserFileException;

    public abstract User getUserByIdent(String ident)
    	throws NoSuchUserException, UserFileException;

    public abstract User getUserByNameUnchecked(String username)
        throws NoSuchUserException, UserFileException;

    /**
     * A kind of constuctor defined in the interface for allowing the
     * usermanager to get a hold of the ConnectionManager object for dispatching
     * events etc.
     */
    public abstract void init(GlobalContext mgr);

    public abstract void saveAll() throws UserFileException;

	public abstract User getUserByNameIncludeDeleted(String argument) throws NoSuchUserException, UserFileException;
}
