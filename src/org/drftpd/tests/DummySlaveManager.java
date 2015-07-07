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
package org.drftpd.tests;

import java.util.Collection;
import java.util.List;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.master.SlaveFileException;

import org.drftpd.GlobalContext;
import org.drftpd.master.RemoteSlave;
import org.drftpd.master.SlaveManager;


/**
 * @author mog
 * @version $Id: DummySlaveManager.java 1765 2007-08-04 04:14:28Z tdsoul $
 */
public class DummySlaveManager extends SlaveManager {
    public DummySlaveManager() throws SlaveFileException {
        super();
    }

	public DummySlaveManager(GlobalContext gctx) {
		setGlobalContext(gctx);
	}

	public void setSlaves(List<RemoteSlave> rslaves) {
        _rslaves = rslaves;
    }

    public void setGlobalContext(GlobalContext gctx) {
        _gctx = gctx;
    }
    public Collection<RemoteSlave> getAvailableSlaves() throws NoAvailableSlaveException {
        return getSlaves();
    }
}
