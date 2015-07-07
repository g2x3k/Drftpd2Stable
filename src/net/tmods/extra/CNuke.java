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
package net.tmods.extra;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map.Entry;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.event.NukeEvent;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.commands.Nuke;
import org.drftpd.remotefile.LinkedRemoteFile;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

/**
 * CNuke v0.3
 * @author tommie
 */
public class CNuke {
	private static final Logger logger = Logger.getLogger(CNuke.class); 
	private GlobalContext gctx;
	private String nukePrefix, nukeReasonPrefix;
	
	public CNuke(GlobalContext _gctx, String _nukePrefix, String _nukeReasonPrefix) {
		gctx = _gctx;
		nukePrefix = _nukePrefix;
		nukeReasonPrefix = _nukeReasonPrefix;
	}

	public void doNuke(String dir, String reason, int nuke_x, String nuker) {
		// if it already contains nuke prefix or is excluded return
		if (dir.contains(nukePrefix)) return;

		// check nuker
		User _nuker = null;
		try {
			_nuker = gctx.getUserManager().getUserByName(nuker);
		} catch (NoSuchUserException e1) { logger.warn(e1); return;
		} catch (UserFileException e1) { logger.warn(e1); return; }

		LinkedRemoteFileInterface nukeDir;
		try {
			if (dir.contains("/")) {
				nukeDir = gctx.getRoot().lookupFile(dir);
			} else {
				nukeDir = LinkedRemoteFile.findLatestDir(gctx.getConnectionManager(),
						gctx.getRoot(), _nuker, dir);				
			}
		} catch (ObjectNotFoundException e) {
			return;
		} catch (FileNotFoundException e) {
			return;
		}
		String nukeDirPath = nukeDir.getPath();

		// permissions
		User xU = null;
		try {
			xU = gctx.getUserManager().getUserByName(nukeDir.getUsername());
		} catch (NoSuchUserException e) {
		} catch (UserFileException e) {
		}
		if (xU != null) {
			if (!gctx.getConfig().checkPathPermission("cnuke", xU, nukeDir)) {
				logger.debug("ABORT! Not permitted to nuke user: " + xU.toString());
				return;			
			}
		}

		// check nukelog, if it already exists return
		if (Nuke.getNukeLog().find_fullpath(nukeDirPath)) return;

		// disconnect anyone transferring in/out of this dirtree 
		gctx.getSlaveManager().cancelTransfersInDirectory(nukeDir); 

		// get nukees with string as key
		Hashtable<String, Long> nukees = new Hashtable<String, Long>();
		Nuke.nukeRemoveCredits(nukeDir, nukees);

		// // convert key from String to User ////
		HashMap<User, Long> nukees2 = new HashMap<User, Long>(nukees.size());
		for (String username : nukees.keySet()) {

			// String username = (String) iter.next();
			User user;
			try {
				user = gctx.getUserManager().getUserByName(username);
			} catch (NoSuchUserException e1) {
				user = null;
			} catch (UserFileException e1) {
				user = null;
			}
			// nukees contains credits as value
			//if (user == null) we don't do anything below anyway
			if (user != null) {
				nukees2.put(user, (Long) nukees.get(username));
			}
		}

		long nukeDirSize = 0;
		long nukedAmount = 0;

		// update credits, nukedbytes, timesNuked, lastNuked
		// for (Iterator iter = nukees2.keySet().iterator(); iter.hasNext();) {
		for (Entry<User, Long> nukeeEntry : nukees2.entrySet()) {
			// User nukee = (User) iter.next();
			User nukee = nukeeEntry.getKey();
			long size = nukeeEntry.getValue().longValue();

			long debt = Nuke.calculateNukedAmount(size, gctx
					.getConfig().getCreditCheckRatio(nukeDir, nukee), nuke_x);

			nukedAmount += debt;
			nukeDirSize += size;
			nukee.updateCredits(-debt);
			if (!gctx.getConfig().checkPathPermission(
					"nostatsup", nukee, nukeDir)) {
				nukee.updateUploadedBytes(-size);
				nukee.getKeyedMap().incrementObjectLong(Nuke.NUKEDBYTES, debt);
			}
			nukee.getKeyedMap().incrementObjectLong(Nuke.NUKED);
			nukee.getKeyedMap().setObject(Nuke.LASTNUKED,
					new Long(System.currentTimeMillis()));
			try {
				nukee.commit();
			} catch (UserFileException e1) {
			}
		}
		NukeEvent nuke = new NukeEvent(_nuker, "NUKE", nukeDirPath,
				nukeDirSize, nukedAmount, nuke_x, reason, nukees);

		Nuke.getNukeLog().add(nuke);

		// rename
		String toDirPath;
		String toName = nukePrefix + nukeDir.getName();
		try {
			toDirPath = nukeDir.getParentFile().getPath();
		} catch (FileNotFoundException ex) {
			return;
		}
		try {
			nukeDir.renameTo(toDirPath, toName);
			nukeDir.createDirectory(_nuker.getName(), _nuker.getGroup(),
					nukeReasonPrefix + reason);
		} catch (IOException ex) {
			return;
		}
		
		gctx.getConnectionManager().dispatchFtpEvent(nuke);		
	}
}
