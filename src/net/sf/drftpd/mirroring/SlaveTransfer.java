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
package net.sf.drftpd.mirroring;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.SlaveUnavailableException;

import org.apache.log4j.Logger;

import org.drftpd.master.RemoteSlave;
import org.drftpd.master.RemoteTransfer;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.slave.ConnectInfo;
import org.drftpd.slave.RemoteIOException;
import org.drftpd.slave.TransferFailedException;

import java.io.IOException;

import java.net.InetSocketAddress;


/**
 * @author mog
 * @author zubov
 * @version $Id: SlaveTransfer.java 1765 2007-08-04 04:14:28Z tdsoul $
 */
public class SlaveTransfer {
    private static final Logger logger = Logger.getLogger(SlaveTransfer.class);
    private RemoteSlave _destSlave;
    private LinkedRemoteFileInterface _file;
    private RemoteSlave _srcSlave;
    private RemoteTransfer _destTransfer = null;
    private RemoteTransfer _srcTransfer = null;
    private boolean _secureTransfer = false;

    /**
     * Slave to Slave Transfers
     */
    public SlaveTransfer(LinkedRemoteFileInterface file,
        RemoteSlave sourceSlave, RemoteSlave destSlave, boolean secureTransfer) {
        _file = file;
        _srcSlave = sourceSlave;
        _destSlave = destSlave;
        _secureTransfer = secureTransfer;
    }

    long getTransfered() {
    	if(_srcTransfer == null || _destTransfer == null) return 0;
        return (_srcTransfer.getTransfered() + _destTransfer.getTransfered()) / 2;
    }
    
    public void abort(String reason) {
    	if (_srcTransfer != null) {
    		_srcTransfer.abort(reason);
    	}
    	if (_destTransfer != null) {
    		_destTransfer.abort(reason);
    	}
    }

    long getXferSpeed() {
    	if(_srcTransfer == null || _destTransfer == null) return 0;
        return (_srcTransfer.getXferSpeed() + _destTransfer.getXferSpeed()) / 2;
    }

    /**
     * Returns the crc checksum of the destination transfer
     * If CRC is disabled on the destination slave, checksum = 0
     */
    protected boolean transfer() throws SlaveException {
    	// can do encrypted slave2slave transfers by modifying the
    	// first argument in issueListenToSlave() and the third option
    	// in issueConnectToSlave(), maybe do an option later, is this wanted?
    	
        try {
            String destIndex = _destSlave.issueListenToSlave(_secureTransfer, false);
            ConnectInfo ci = _destSlave.fetchTransferResponseFromIndex(destIndex);
            _destTransfer = _destSlave.getTransfer(ci.getTransferIndex());
        } catch (SlaveUnavailableException e) {
            throw new DestinationSlaveException(e);
        } catch (RemoteIOException e) {
            throw new DestinationSlaveException(e);
        }

        try {
            String srcIndex = _srcSlave.issueConnectToSlave(_destTransfer
					.getAddress().getAddress().getHostAddress(), _destTransfer
					.getLocalPort(), _secureTransfer, true);
            ConnectInfo ci = _srcSlave.fetchTransferResponseFromIndex(srcIndex);
            _srcTransfer = _srcSlave.getTransfer(ci.getTransferIndex());
        } catch (SlaveUnavailableException e) {
            throw new SourceSlaveException(e);
        } catch (RemoteIOException e) {
            throw new SourceSlaveException(e);
        }

        try {
            _destTransfer.receiveFile(_file.getPath(), 'I', 0);
        } catch (IOException e1) {
            throw new DestinationSlaveException(e1);
        } catch (SlaveUnavailableException e1) {
            throw new DestinationSlaveException(e1);
        }

        try {
            _srcTransfer.sendFile(_file.getPath(), 'I', 0);
        } catch (IOException e2) {
            throw new SourceSlaveException(e2);
        } catch (SlaveUnavailableException e2) {
            throw new SourceSlaveException(e2);
        }

        boolean srcIsDone = false;
        boolean destIsDone = false;

        while (!(srcIsDone && destIsDone)) {
            try {
                if (_srcTransfer.getTransferStatus().isFinished()) {
                    srcIsDone = true;
                }
            } catch (TransferFailedException e7) {
                _destTransfer.abort("srcSlave had an error");
                throw new SourceSlaveException(e7.getCause());
            }

            try {
                if (_destTransfer.getTransferStatus().isFinished()) {
                    destIsDone = true;
                }
            } catch (TransferFailedException e6) {
                _srcTransfer.abort("destSlave had an error");
                throw new DestinationSlaveException(e6.getCause());
            }

            try {
                Thread.sleep(100);
            } catch (InterruptedException e5) {
            }
        }
        long srcChecksum = _srcTransfer.getChecksum();
        long destChecksum = _destTransfer.getChecksum();
        // may as well set the checksum, we know this one is right
        if (_srcTransfer.getChecksum() != 0) {
        	_file.setCheckSum(_srcTransfer.getChecksum());
        }
        
        if (_srcTransfer.getChecksum() == _destTransfer.getChecksum() || _destTransfer.getChecksum() == 0 || _srcTransfer.getChecksum() == 0 ) {
        	return true;
        } else {
        	return false;
        }
    }

	/**
	 * @return Returns the _destSlave.
	 */
	public RemoteSlave getDestinationSlave() {
		return _destSlave;
	}

	/**
	 * @return Returns the _srcSlave.
	 */
	public RemoteSlave getSourceSlave() {
		return _srcSlave;
	}
}
