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

import java.util.Properties;

import net.sf.drftpd.master.config.FtpConfig;

import org.drftpd.GlobalContext;
import org.drftpd.master.ConnectionManager;
import org.drftpd.master.SlaveManager;
import org.drftpd.remotefile.LinkedRemoteFile;
import org.drftpd.sections.SectionManagerInterface;


/**
 * @author mog
 * @version $Id: DummyGlobalContext.java 1765 2007-08-04 04:14:28Z tdsoul $
 */
public class DummyGlobalContext extends GlobalContext {
    public DummyGlobalContext() {
    }

    public void loadPlugins(Properties cfg) {
        super.loadPlugins(cfg);
    }

    public void loadUserManager(Properties cfg, String cfgFileName) {
        super.loadUserManager(cfg, cfgFileName);
    }

    public void setConnectionManager(ConnectionManager cm) {
        _cm = cm;
    }

    public void setFtpConfig(FtpConfig config) {
        _config = config;
    }

    public void setSectionManager(SectionManagerInterface manager) {
        _sections = manager;
    }

    public void setSlaveManager(SlaveManager slavem) {
        _slaveManager = slavem;
    }

    public void setUserManager(DummyUserManager um) {
        _usermanager = um;
    }

    public void setRoot(LinkedRemoteFile root) {
        _root = root;
    }

    public void setSlaveSelectionManager(DummySlaveSelectionManager dssm) {
        _slaveSelectionManager = dssm;
    }
}
