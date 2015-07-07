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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;

import net.sf.drftpd.NoAvailableSlaveException;

import org.drftpd.master.RemoteSlave;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.slave.SlaveStatus;
import org.drftpd.slave.Transfer;
import org.drftpd.usermanager.User;

public class TransfercounterFilter extends Filter {

	public TransfercounterFilter(FilterChain ssm, int i, Properties p) {

	}

	@Override
	public void process(ScoreChart scorechart, User user, InetAddress peer,
			char direction, LinkedRemoteFileInterface dir,
			RemoteSlave sourceSlave) throws NoAvailableSlaveException {
		
		ArrayList<ScoreChart.SlaveScore> lowSlaves = new ArrayList<ScoreChart.SlaveScore>();
		int lownum = Integer.MAX_VALUE;
		for (Iterator iter = scorechart.getSlaveScores().iterator(); iter
				.hasNext();) {
			ScoreChart.SlaveScore slavescore = (ScoreChart.SlaveScore) iter
					.next();
			SlaveStatus status;

			try {
				status = slavescore.getRSlave().getSlaveStatusAvailable();
			} catch (Exception e) {
				iter.remove();
				continue;
			}

			int transfers = 0;

			if (direction == Transfer.TRANSFER_RECEIVING_UPLOAD) {
				transfers = status.getTransfersReceiving();
			} else if (direction == Transfer.TRANSFER_SENDING_DOWNLOAD) {
				transfers = status.getTransfersSending();
			} else {
				throw new IllegalArgumentException(
						"Direction was not one of download or upload");
			}
			if (transfers < lownum) {
				lownum = transfers;
				lowSlaves.clear();
				lowSlaves.add(slavescore);
			} else if (transfers == lownum) {
				lowSlaves.add(slavescore);
			} else { // transfers > lownum
				// nothing, you stink slave! :)
			}
		}
		if (lowSlaves.isEmpty()) {
			throw new NoAvailableSlaveException("No slaves are available");
		}
		for (ScoreChart.SlaveScore slavescore : lowSlaves) {
			slavescore.addScore(1);
		}
	}

}
