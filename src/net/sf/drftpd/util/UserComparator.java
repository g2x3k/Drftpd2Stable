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

import org.drftpd.commands.TransferStatistics;

import org.drftpd.usermanager.User;

import java.util.Comparator;


/**
 * @author mog
 * @author zubov
 * @version $Id: UserComparator.java 1765 2007-08-04 04:14:28Z tdsoul $
 */
public class UserComparator implements Comparator {
    private String _type;

    public UserComparator(String type) {
        _type = type;
    }

    public int compare(Object o1, Object o2) {
        User u1 = (User) o1;
        User u2 = (User) o2;

        long thisVal = TransferStatistics.getStats(_type, u1);
        long anotherVal = TransferStatistics.getStats(_type, u2);

        return ((thisVal > anotherVal) ? (-1) : ((thisVal == anotherVal) ? 0 : 1));
    }
}
