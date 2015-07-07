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
package org.drftpd.slave;

import java.io.Serializable;


/**
 * @author zubov
 * @version $Id: TransferIndex.java 1765 2007-08-04 04:14:28Z tdsoul $
 */
public final class TransferIndex implements Serializable {
    static Integer transfers = new Integer(0);
    private int _index;

    public TransferIndex(int index) {
        _index = index;
    }

    public TransferIndex() {
    	synchronized(transfers) {
    		transfers = new Integer(transfers.intValue() + 1);
    		_index = transfers.intValue();
    	}
    }

    public boolean equals(Object obj) {
    	if(obj == null) return false;
        return _index == ((TransferIndex) obj)._index;
    }

    public int hashCode() {
        return _index;
    }

    public String toString() {
        return Integer.toString(_index);
    }
}
