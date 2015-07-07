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
package net.sf.drftpd.event;

import java.util.ArrayList;

import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.usermanager.User;

/**
 * Event routing in sitebot/plugins breaks old functionality in some cases, this band-aid 
 * should enable sitebot plugins which utilize events-for-output to sufficiently emulate
 * the old behavior.
 *
 * @author tdsoul
 * @since plus-2.0.6
 * @version $Id: DirectorySiteBotEvent.java 1777 2007-08-27 23:09:19Z tdsoul $
 */
public class DirectorySiteBotEvent extends DirectoryFtpEvent {
	private ArrayList<String> _forceToChannels;

	/**
	 * @param user
	 * @param command
	 * @param directory
	 * @param forceToChannel List of additional IRC channels/users any applicable messages should be sent to.
	 */
	public DirectorySiteBotEvent(User user, String command,
			LinkedRemoteFileInterface directory, ArrayList<String> forceToChannels) {
		this(user, command, directory, forceToChannels, System.currentTimeMillis());
	}

	/**
	 * @param user
	 * @param command
	 * @param directory
	 * @param forceToChannel List of additional IRC channels/users any applicable messages should be sent to.
	 */
	public DirectorySiteBotEvent(User user, String command,
			LinkedRemoteFileInterface directory, ArrayList<String> forceToChannels, long time) {
		super(user, command, directory, time);
		
		/* Convert list to lower case for later comparison. */
		// Since IRC RFC defines it to be case insensitive, this should be compatible with all IRC servers.
		ArrayList<String> forceToChannels2 = new ArrayList<String>();
		for (String c : forceToChannels) {
			forceToChannels2.add(c.toLowerCase());
		}
		this._forceToChannels = forceToChannels2;
	}

    public ArrayList<String> getForceToChannels() {
        return _forceToChannels;
    }

    public String toString() {
        return getClass().getName() + "[user=" + getUser() + ",cmd=" +
        getCommand() + ",directory=" + getDirectory().getPath() + 
        ",forcechannels=" + _forceToChannels.toString() + "]";
    }
}
