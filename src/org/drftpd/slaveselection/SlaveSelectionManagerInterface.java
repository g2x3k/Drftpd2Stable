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
package org.drftpd.slaveselection;

import java.io.IOException;
import java.util.Collection;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.mirroring.Job;

import org.drftpd.GlobalContext;
import org.drftpd.master.RemoteSlave;
import org.drftpd.remotefile.LinkedRemoteFileInterface;


/**
 * @author mog
 * @version $Id: SlaveSelectionManagerInterface.java 1765 2007-08-04 04:14:28Z tdsoul $
 */
public interface SlaveSelectionManagerInterface {
    public abstract void reload() throws IOException;

    /**
     * Checksums call us with null BaseFtpConnection.
     */
    public RemoteSlave getASlave(Collection<RemoteSlave> rslaves, char direction,
        BaseFtpConnection conn, LinkedRemoteFileInterface file)
        throws NoAvailableSlaveException;

    public GlobalContext getGlobalContext();

    public RemoteSlave getASlaveForJobDownload(Job job)
        throws NoAvailableSlaveException;

    public RemoteSlave getASlaveForJobUpload(Job job, RemoteSlave sourceSlave)
        throws NoAvailableSlaveException;
}
