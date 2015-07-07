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

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.master.config.FtpConfig;

import org.apache.log4j.Logger;

import org.drftpd.Bytes;
import org.drftpd.PropertyHelper;
import org.drftpd.master.RemoteSlave;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.slave.SlaveStatus;
import org.drftpd.usermanager.User;

import java.net.InetAddress;

import java.util.Iterator;
import java.util.Properties;


/**
 * @author zubov
 * @version $Id: MaxbandwidthFilter.java 880 2004-12-29 04:24:27Z mog $
 */
public class MaxbandwidthFilter extends Filter {
    private static final Logger logger = Logger.getLogger(MaxbandwidthFilter.class);
    private long _maxBandwidth;

    public MaxbandwidthFilter(FilterChain ssm, int i, Properties p) {
        _maxBandwidth = Bytes.parseBytes(PropertyHelper.getProperty(p,
                    i + ".maxbandwidth"));
    }

    public void process(ScoreChart scorechart, User user, InetAddress peer,
        char direction, LinkedRemoteFileInterface dir, RemoteSlave sourceSlave)
        throws NoAvailableSlaveException {
        for (Iterator iter = scorechart.getSlaveScores().iterator();
                iter.hasNext();) {
            ScoreChart.SlaveScore slavescore = (ScoreChart.SlaveScore) iter.next();
            SlaveStatus status;

            try {
                status = slavescore.getRSlave().getSlaveStatusAvailable();
            } catch (Exception e) {
                iter.remove();
                logger.debug("removed " + slavescore.getRSlave().getName() +
                    " because of exception", e);

                continue;
            }

            if (status.getThroughputDirection(direction) > _maxBandwidth) {
                iter.remove();
            }
        }
    }
}
