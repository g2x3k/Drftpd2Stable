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
package org.drftpd.slaveselection.filter;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;

import org.drftpd.Bytes;
import org.drftpd.master.RemoteSlave;
import org.drftpd.master.RemoteTransfer;

import org.drftpd.slave.DiskStatus;
import org.drftpd.slave.SlaveStatus;
import org.drftpd.slave.Transfer;
import org.drftpd.tests.DummyRemoteSlave;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;


/**
 * @author mog
 * @version $Id: MinfreespaceFilterTest.java 823 2004-11-29 01:36:22Z mog $
 */
public class MinfreespaceFilterTest extends TestCase {
    public MinfreespaceFilterTest(String fName) {
        super(fName);
    }

    public static TestSuite suite() {
        return new TestSuite(MinfreespaceFilterTest.class);
    }

    public void testSimple()
        throws ObjectNotFoundException, NoAvailableSlaveException {
        Properties p = new Properties();
        p.put("1.multiplier", "1");
        p.put("1.minfreespace", "100MB");

        SlaveStatus s = new SlaveStatus(new DiskStatus(Bytes.parseBytes("50MB"),
                Bytes.parseBytes("100GB")), 0, 0, 0, 0, 0, 0);
        RemoteSlave[] rslaves = {
                new RemoteSlaveTesting("slave1", Collections.EMPTY_LIST, s)
            };
        ScoreChart sc = new ScoreChart(Arrays.asList(rslaves));

        Filter f = new MinfreespaceFilter(null, 1, p);
        f.process(sc, null, null, Transfer.TRANSFER_SENDING_DOWNLOAD, null, null);

        assertEquals(Bytes.parseBytes("-50MB"),
            sc.getSlaveScore(rslaves[0]).getScore());
    }

    public static class RemoteSlaveTesting extends DummyRemoteSlave {
        private SlaveStatus _status;

        public RemoteSlaveTesting(String name, Collection masks,
            SlaveStatus status) {
            super(name, null);
            _status = status;
        }

        public SlaveStatus getSlaveStatusAvailable() {
            return _status;
        }
    }
}
