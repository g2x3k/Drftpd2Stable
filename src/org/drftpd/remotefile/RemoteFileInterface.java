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
package org.drftpd.remotefile;

import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.List;

import org.drftpd.master.RemoteSlave;


/**
 * @author mog
 * @version $Id: RemoteFileInterface.java 1765 2007-08-04 04:14:28Z tdsoul $
 */
public interface RemoteFileInterface extends LightRemoteFileInterface {

    /**
     * Returns the cached checksum or 0 if no checksum was cached.
     * <p>
     * Use {getCheckSum()} to automatically calculate checksum if no cached checksum is available.
     */
    public long getCheckSumCached();

    /**
     * Returns a Collection of RemoteFileInterface objects.
     */
    public Collection<RemoteFileInterface> getFiles();

    /**
     * Get the group owner of the file as a String.
     * <p>
     * getUser().getGroupname() if the implementing class uses a User object.
     * @return primary group of the owner of this file
     */
    public String getGroupname();

    /**
     * Returns the target of the link.
     * @return target of the link.
     */
    public String getLinkPath();

    public abstract String getParent() throws FileNotFoundException;

    public abstract String getPath();
    public Collection<RemoteSlave> getSlaves();

    /**
     * Returns string representation of the owner of this file.
     * <p>
     * getUser().getUsername() if the implementing class uses a User object.
     * @return username of the owner of this file.
     */
    public String getUsername();

    public long getXfertime();

    /**
     * boolean flag whether this file is a 'link', it can be linked to another file.
     * This is for the moment used for "ghost files".
     */
    public boolean isLink();
}
