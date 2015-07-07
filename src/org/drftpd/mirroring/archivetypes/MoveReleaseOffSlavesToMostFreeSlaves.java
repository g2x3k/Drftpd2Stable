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
package org.drftpd.mirroring.archivetypes;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

import net.sf.drftpd.NoAvailableSlaveException;

import org.apache.log4j.Logger;
import org.drftpd.master.RemoteSlave;
import org.drftpd.mirroring.ArchiveType;
import org.drftpd.plugins.Archive;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.sections.SectionInterface;

/**
 * @author zubov
 * @version $Id: MoveReleaseOffSlavesToMostFreeSlaves.java 1765 2007-08-04 04:14:28Z tdsoul $
 */
public class MoveReleaseOffSlavesToMostFreeSlaves extends ArchiveType {
    private static final Logger logger = Logger.getLogger(MoveReleaseOffSlavesToMostFreeSlaves.class);
    private Set<RemoteSlave> _offOfSlaves;

    public MoveReleaseOffSlavesToMostFreeSlaves(Archive archive,
        SectionInterface section, Properties props) {
        super(archive, section, props);
        _offOfSlaves = getOffOfSlaves(props);

        if (_offOfSlaves.isEmpty()) {
            throw new NullPointerException(
                "Cannot continue, 0 slaves found to move off MoveReleaseOffSlavesToMostFreeSlaves for for section " +
                getSection().getName());
        }

        if (_slaveList == null) _slaveList = new HashSet<RemoteSlave>();
        if (_slaveList.isEmpty()) {
        	logger.debug("Destination slaves chain is empty, adding all slaves to it but the source ones.");
        	for (RemoteSlave rs : _parent.getGlobalContext().getSlaveManager().getSlaves()) {
        		if (!_offOfSlaves.contains(rs)) {
        			_slaveList.add(rs);
        		}
        	}
        	logger.debug("Chain: " + _slaveList.toString());
        }

        if (_numOfSlaves < 1) {
            throw new IllegalArgumentException(
                "numOfSlaves has to be > 0 for section " + section.getName());
        }
    }

    public HashSet<RemoteSlave> findDestinationSlaves() {
        HashSet<RemoteSlave> set = _parent.getGlobalContext().getSlaveManager()
				.findSlavesBySpace(_numOfSlaves, _offOfSlaves, false);

        if (set.isEmpty()) {
            return null;
        }

        return set;
    }

    protected boolean isArchivedDir(LinkedRemoteFileInterface lrf)
        throws IncompleteDirectoryException, OfflineSlaveException {
        for (Iterator iter = lrf.getFiles().iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();

            if (file.isDirectory()) {
                if (!isArchivedDir(file)) {
                    return false;
                }
            } else {
                try {
                    for (Iterator iter2 = file.getAvailableSlaves().iterator();
                            iter2.hasNext();) {
                        RemoteSlave rslave = (RemoteSlave) iter2.next();

                        if (_offOfSlaves.contains(rslave)) {
                            return false;
                        }
                    }
                } catch (NoAvailableSlaveException e) {
                    throw new OfflineSlaveException(
                        "There were no available slaves for " + file.getPath());
                }
            }
        }

        return true;
    }

    public String toString() {
        return "MoveReleaseOffSlavesToMostFreeSlaves=[directory=[" +
        getDirectory().getPath() + "]dest=[" + outputSlaves(getRSlaves()) +
        "]numOfSlaves=[" + _numOfSlaves + "]]";
    }
}
