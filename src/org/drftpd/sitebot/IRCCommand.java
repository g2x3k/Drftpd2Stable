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

package org.drftpd.sitebot;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;

/**
 * @author zubov
 *
 * @version $Id$
 */
public class IRCCommand {
    private static final Logger logger = Logger.getLogger(IRCCommand.class); 
	private GlobalContext _gctx;

	public IRCCommand(GlobalContext gctx) {
	    logger.info("Loaded SiteBot plugin: " + getClass().getName());
		_gctx = gctx;
	}
	
	public GlobalContext getGlobalContext() {
		return _gctx;
	}
}
