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

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;

import org.drftpd.master.RemoteSlave;
import org.drftpd.master.RemoteTransfer;

import org.drftpd.slave.Transfer;
import org.drftpd.tests.DummyRemoteSlave;

import java.util.Arrays;


/**
 * @author zubov
 * @version $Id: CycleFilterTest.java 823 2004-11-29 01:36:22Z mog $
 */
public class CycleFilterTest extends TestCase {
    /**
     * Constructor for CycleFilterTest.
     * @param arg0
     */
    public CycleFilterTest(String arg0) {
        super(arg0);
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(CycleFilterTest.class);
    }

    public void testProcess()
        throws NoAvailableSlaveException, ObjectNotFoundException {
        RemoteSlave[] rslaves = {
                new DummyRemoteSlave("slave1", null),
                new DummyRemoteSlave("slave2", null),
                new DummyRemoteSlave("slave3", null)
            };
        ScoreChart sc = new ScoreChart(Arrays.asList(rslaves));
        Filter f = new CycleFilter(null, 0, null);
        f.process(sc, null, null, Transfer.TRANSFER_SENDING_DOWNLOAD, null, null);
        assertEquals(1, sc.getSlaveScore(rslaves[0]).getScore());
        assertEquals(0, sc.getSlaveScore(rslaves[1]).getScore());
        assertEquals(0, sc.getSlaveScore(rslaves[2]).getScore());
    }
}
