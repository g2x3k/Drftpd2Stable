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

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.util.ReplacerUtils;

import org.drftpd.GlobalContext;
import org.drftpd.master.RemoteSlave;
import org.drftpd.plugins.SiteBot;
import org.drftpd.slave.SlaveStatus;
import org.tanesha.replacer.ReplacerEnvironment;

import f00f.net.irc.martyr.commands.MessageCommand;

public class Uptime extends IRCCommand {

	public Uptime(GlobalContext gctx) {
		super(gctx);
	}

	public ArrayList<String> doMasterUptime(String args, MessageCommand msgc) {
		ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);

		RuntimeMXBean mxbean = ManagementFactory.getRuntimeMXBean();
		long uptime = mxbean.getUptime();

		env.add("uptime", formatTime(uptime));
		out.add(ReplacerUtils.jprintf("master.uptime", env, Uptime.class));
		return out;
	}

	public ArrayList<String> doSlaveUptime(String args, MessageCommand msgc) {
		ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);

		String slaveName = args;

		try {
            RemoteSlave rslave = getGlobalContext().getSlaveManager().getRemoteSlave(slaveName);
			out.add(makeOutput(rslave));
		} catch (ObjectNotFoundException e2) {
			env.add("slave", slaveName);
			out.add(ReplacerUtils.jprintf("slave.notfound", env, Uptime.class));
		}
		return out;
	}

	public ArrayList<String> doSlavesUptime(String args, MessageCommand msgc) {
		ArrayList<String> out = new ArrayList<String>();
	    List<RemoteSlave> rslaves = getGlobalContext().getSlaveManager().getSlaves();
		RemoteSlave rslave = null;

		for (Iterator iter = rslaves.iterator(); iter.hasNext();) {
			rslave = (RemoteSlave) iter.next();
			out.add(makeOutput(rslave));
		}
		return out;
	}

	private String makeOutput(RemoteSlave rslave) {
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);

		env.add("slave", rslave.getName());
		SlaveStatus status;

		try {
			status = rslave.getSlaveStatusAvailable();
		} catch (SlaveUnavailableException e1) {
			return ReplacerUtils.jprintf("slave.offline", env, Uptime.class);
		}

		env.add("uptime", formatTime(rslave.getUptime()));
		return ReplacerUtils.jprintf("slave.uptime", env, Uptime.class);
	}

	private String formatTime(Long longtime) {
		StringBuffer sb = new StringBuffer();

		Long day = 86400000L;
		Long hour = 3600000L;
		Long minute = 60000L;
		Long second = 1000L;
		Integer x = new Integer(0);

		// day
		if (longtime >= day) {
			x = new Long(longtime / day).intValue();
			longtime -= x * day;
			sb.append(x + "d ");
		}

		// hour
		if (longtime >= hour) {
			x = new Long(longtime / hour).intValue();
			longtime -= x * hour;
			sb.append(x + "h ");
		}

		// minute
		if (longtime >= minute) {
			x = new Long(longtime / minute).intValue();
			longtime -= x * minute;
			sb.append(x + "m ");
		}

		// second
		x = new Long(longtime / second).intValue();
		sb.append(x + "s");

		return sb.toString();
	}

}