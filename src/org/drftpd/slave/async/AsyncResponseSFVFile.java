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
package org.drftpd.slave.async;

import org.drftpd.LightSFVFile;


/**
 * @author zubov
 * @version $Id: AsyncResponseSFVFile.java 1765 2007-08-04 04:14:28Z tdsoul $
 */
public class AsyncResponseSFVFile extends AsyncResponse {
    private LightSFVFile _sfv;

    public AsyncResponseSFVFile(String index, LightSFVFile sfv) {
        super(index);

        if (sfv == null) {
            throw new IllegalArgumentException("sfv cannot be null");
        }

        _sfv = sfv;
    }

    public LightSFVFile getSFV() {
        return _sfv;
    }
    public String toString() {
    	return getClass().getName()+"[sfv.size="+getSFV().size()+"]";
    }
}
