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
package org.drftpd.slaveselection.def;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.config.ConfigInterface;
import net.sf.drftpd.mirroring.Job;

import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.PropertyHelper;
import org.drftpd.master.RemoteSlave;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.slave.SlaveStatus;
import org.drftpd.slave.Transfer;
import org.drftpd.slaveselection.SlaveSelectionManagerInterface;

/**
 * @author mog
 * @version $Id: DefaultSlaveSelectionManager.java 874 2004-12-23 17:43:28Z mog $
 */
public class DefaultSlaveSelectionManager
    implements SlaveSelectionManagerInterface {
    private GlobalContext _gctx;
    private long _minfreespace;
    private int _maxTransfers;
    private long _maxBandwidth;

    public DefaultSlaveSelectionManager(GlobalContext gctx)
        throws FileNotFoundException, IOException {
        super();
        _gctx = gctx;
    }

    public void reload() throws FileNotFoundException, IOException {
        Properties p = new Properties();
        FileInputStream fis = null;
        try {
        fis = new FileInputStream("conf/slaveselection-old.conf");
        p.load(fis);
        } finally {
        	if (fis != null) {
        		fis.close();
        		fis = null;
        	}
        }
        _minfreespace = Bytes.parseBytes(PropertyHelper.getProperty(p, "minfreespace"));

        _maxTransfers = Integer.parseInt(PropertyHelper.getProperty(p,
                            "maxTransfers"));
        _maxBandwidth = Bytes.parseBytes(PropertyHelper.getProperty(p,
                            "maxBandwidth"));
    }

    public RemoteSlave getASlave(Collection rslaves, char direction,
        BaseFtpConnection conn, LinkedRemoteFileInterface file)
        throws NoAvailableSlaveException {
        return getASlaveInternal(rslaves, direction);
    }

    public RemoteSlave getASlaveForMaster(LinkedRemoteFileInterface file,
        ConfigInterface cfg) throws NoAvailableSlaveException {
        return getASlaveInternal(file.getAvailableSlaves(),
            Transfer.TRANSFER_SENDING_DOWNLOAD);
    }

    public RemoteSlave getASlaveForJobDownload(Job job)
        throws NoAvailableSlaveException {
        return getASlaveInternal(job.getFile().getAvailableSlaves(),
            Transfer.TRANSFER_SENDING_DOWNLOAD);
    }

    private RemoteSlave getASlaveInternal(Collection slaves, char direction)
        throws NoAvailableSlaveException {
        RemoteSlave bestslave;
        SlaveStatus beststatus;

        {
            Iterator i = slaves.iterator();
            int bestthroughput;

            while (true) {
                if (!i.hasNext()) {
                    throw new NoAvailableSlaveException();
                }

                bestslave = (RemoteSlave) i.next();

                try {
                    beststatus = bestslave.getSlaveStatusAvailable();

                    // throws SlaveUnavailableException
                } catch (SlaveUnavailableException ex) {
                    continue;
                }

                bestthroughput = beststatus.getThroughputDirection(direction);

                break;
            }

            while (i.hasNext()) {
                RemoteSlave slave = (RemoteSlave) i.next();
                SlaveStatus status;

                try {
                    status = slave.getSlaveStatusAvailable();
                } catch (SlaveUnavailableException ex) {
                    continue;
                }

                int throughput = status.getThroughputDirection(direction);

                if ((beststatus.getDiskSpaceAvailable() < _minfreespace) &&
                        (beststatus.getDiskSpaceAvailable() < status.getDiskSpaceAvailable())) {
                    // best slave has less space than "freespace.min" &&
                    // best slave has less space available than current slave 
                    bestslave = slave;
                    bestthroughput = throughput;
                    beststatus = status;

                    continue;
                }

                if (status.getDiskSpaceAvailable() < _minfreespace) {
                    // current slave has less space available than "freespace.min"
                    // above check made sure bestslave has more space than us
                    continue;
                }

                if (throughput == bestthroughput) {
                    if (direction == Transfer.TRANSFER_RECEIVING_UPLOAD) {
                        if (bestslave.getLastUploadReceiving() > slave.getLastUploadReceiving()) {
                            bestslave = slave;
                            bestthroughput = throughput;
                            beststatus = status;
                        }
                    } else if (direction == Transfer.TRANSFER_SENDING_DOWNLOAD) {
                        if (bestslave.getLastDownloadSending() > slave.getLastDownloadSending()) {
                            bestslave = slave;
                            bestthroughput = throughput;
                            beststatus = status;
                        }
                    } else if (direction == Transfer.TRANSFER_THROUGHPUT) {
                        if (bestslave.getLastTransfer() > slave.getLastTransfer()) {
                            bestslave = slave;
                            bestthroughput = throughput;
                            beststatus = status;
                        }
                    }
                }

                if (throughput < bestthroughput) {
                    bestslave = slave;
                    bestthroughput = throughput;
                    beststatus = status;
                }
            }
        }

        if (direction == Transfer.TRANSFER_RECEIVING_UPLOAD) {
            bestslave.setLastUploadReceiving(System.currentTimeMillis());
        } else if (direction == Transfer.TRANSFER_SENDING_DOWNLOAD) {
            bestslave.setLastDownloadSending(System.currentTimeMillis());
        } else {
            bestslave.setLastUploadReceiving(System.currentTimeMillis());
            bestslave.setLastDownloadSending(System.currentTimeMillis());
        }

        return bestslave;
    }

    public RemoteSlave getASlaveForJobUpload(Job job, RemoteSlave sourceSlave)
        throws NoAvailableSlaveException {
        Collection<RemoteSlave> slaves = job.getDestinationSlaves();
        slaves.removeAll(job.getFile().getAvailableSlaves());

        return getASlaveForJob(slaves, Transfer.TRANSFER_RECEIVING_UPLOAD);
    }

    public RemoteSlave getASlaveForJob(Collection slaves, char direction)
        throws NoAvailableSlaveException {
        RemoteSlave rslave = this.getASlaveInternal(slaves, direction);
        SlaveStatus status = null;

        try {
            status = rslave.getSlaveStatusAvailable();
        } catch (SlaveUnavailableException e) {
            throw new NoAvailableSlaveException();
        }

        if (status.getThroughputDirection(direction) > _maxBandwidth) {
            throw new NoAvailableSlaveException();
        }

        if (direction == Transfer.TRANSFER_RECEIVING_UPLOAD) {
            if (status.getTransfersReceiving() > _maxTransfers) {
                throw new NoAvailableSlaveException();
            }
        }

        if (direction == Transfer.TRANSFER_SENDING_DOWNLOAD) {
            if (status.getTransfersSending() > _maxTransfers) {
                throw new NoAvailableSlaveException();
            }
        }

        return rslave;
    }

    public GlobalContext getGlobalContext() {
        return _gctx;
    }

}
