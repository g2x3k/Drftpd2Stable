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
package net.sf.drftpd.util;

import org.apache.log4j.Logger;

import java.io.IOException;

import java.net.InetSocketAddress;
import java.net.ServerSocket;

import java.util.Random;

import javax.net.ServerSocketFactory;


/**
 * @author mog
 * @version $Id: PortRange.java 1765 2007-08-04 04:14:28Z tdsoul $
 */
public class PortRange {
    private static final Logger logger = Logger.getLogger(PortRange.class);
    private int _minPort;
    private int _maxPort;
    private int _bufferSize;
    Random rand = new Random();

    /**
     * Creates a default port range for port 49152 to 65535.
     */
    public PortRange(int bufferSize) {
        _minPort = 0;
        _maxPort = 0;
        _bufferSize = bufferSize;
    }

    public PortRange(int minPort, int maxPort, int bufferSize) {
        if (0 >= minPort || minPort > maxPort || maxPort > 65535) {
            throw new RuntimeException("0 < minPort <= maxPort <= 65535");
        }

        _maxPort = maxPort;
        _minPort = minPort;
        _bufferSize = bufferSize;
    }

    public ServerSocket getPort(ServerSocketFactory ssf) {
		if (_minPort == 0) {
			try {
				ServerSocket _serverSocket = ssf.createServerSocket();
 				if (_bufferSize > 0) {
 					_serverSocket.setReceiveBufferSize(_bufferSize);
 				}
 				_serverSocket.bind(new InetSocketAddress(0),1);
 				return _serverSocket;
			} catch (IOException e) {
				logger.error("Unable to bind anonymous port", e);
				throw new RuntimeException(e);
			}
		}

		int pos = rand.nextInt(_maxPort - _minPort + 1) + _minPort;
		int initPos = pos;
		boolean retry = true;
		while (true) {
			try {
				ServerSocket _serverSocket = ssf.createServerSocket();
        if (_bufferSize > 0) {
        	_serverSocket.setReceiveBufferSize(_bufferSize);
        }
        _serverSocket.bind(new InetSocketAddress(pos),1);
        return _serverSocket;
			} catch (IOException e) {
			}
			pos++;
			if (pos > _maxPort) {
				pos = _minPort;
			}
			if (pos == initPos) {
				if (retry == false) {
					throw new RuntimeException("PortRange exhausted");
				}
				System.runFinalization();
				retry = false;
			}
		}
	}
}
