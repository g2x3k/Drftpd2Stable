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

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;

import net.sf.drftpd.util.ReplacerUtils;
import org.apache.log4j.Logger;
import org.drftpd.plugins.SiteBot;
import org.tanesha.replacer.ReplacerEnvironment;

import f00f.net.irc.martyr.GenericAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.State;
import f00f.net.irc.martyr.clientstate.ClientState;
import f00f.net.irc.martyr.commands.JoinCommand;

/**
 * Listen to sysop.log and output to irc
 *
 * @author tdsoul
 * @version $Id: SysopTailService.java 1719 2007-05-14 06:41:14Z tdsoul $
 */
public class SysopTailService extends GenericAutoService implements Runnable {

	private static final Logger logger = Logger.getLogger(SysopTailService.class);

	private File _file;

	private long _filePointer;

	private SiteBot _sb;

	private ArrayList<String> sayList = new ArrayList<String>();

	private boolean _running = true;

	private int _updateInterval = 1000;

	private ReplacerEnvironment _env;

	private State _state;

	public SysopTailService(SiteBot sitebot) {
		super(sitebot.getIRCConnection());

		_sb = sitebot;
		_state = _sb.getIRCConnection().getState();

		_file = new File("logs/sysop.log");
		_env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		_env.add("tag", ReplacerUtils.jprintf("tag", _env, SysopTailService.class));

		this.tailStart();
	}

	private void tailStart() {
		sayLog("Starting up logfile monitoring.");
		Thread t = new Thread(this);
		t.start();
	}

	/*
	 * This is the method that contains all the actual log tailing stuff.
	 * Note: I'm not particularly happy about the use of the readLine()
	 * method call, as it may return a partial line if it reaches the
	 * end of the file.  It might be worth jibbling about with this at
	 * a later date so that a different approach is used.
	 *
	 * This method was derived from JLogTailer 
	 * Copyright Paul James Mutton, 2001-2004, http://www.jibble.org/
	 * 
	 * @author Paul James Mutton
	 * @author tdsoul
	 */
	public void run() {
		RandomAccessFile raf = null;
		try {
			_filePointer = _file.length();
			while (_running) {
				Thread.sleep(_updateInterval);
				long len = _file.length();
				if (len < _filePointer) {
					sayLog("Log file was reset. Restarting logging from start of file.");
					_filePointer = len;
				} else if (len > _filePointer) {
					// File must have had something added to it!
					raf = new RandomAccessFile(_file, "r");
					raf.seek(_filePointer);
					String line = null;
					while ((line = raf.readLine()) != null) {
						if (line.startsWith("INFO") && line.indexOf("'") > 5)
							line = line.substring(line.indexOf(" ") + 1,
									line.length()).replaceAll("'", "");
						else
							continue;

						sayLog(line);
					}
					_filePointer = raf.getFilePointer();
					raf.close();
				}
			}
		} catch (Exception e) {
                    sayLog("Fatal error reading log file, log tailing has stopped.");
		}

		// don't leak fd
		try {
                    if (raf != null)
                        raf.close();
                } catch (Throwable t) {
                    logger.debug(t);
                } finally {
                    raf = null;

                    // restart immediately, if stopped in error.
                    if (_running) this.tailStart();
                }
        }

    public void sayLog(String line) {
        sayLog(line, true);
    }

	public void sayLog(String line, boolean canSave) {
        if (line != null && isEventLogConfigured()) {
            if (_state == State.REGISTERED && inChannel()) {
                ReplacerEnvironment env = new ReplacerEnvironment(_env);
                env.add("logline", line);
                _sb.sayEvent("log", ReplacerUtils.jprintf("announce", env, SysopTailService.class));
            } else if (canSave) {
                // not in the channel, save the output for later
                sayList.add(line);
            }
        }
	}

	protected void updateState(State s) {
			_state = s;
	}

	protected void updateCommand(InCommand ic) {
		_state = ic.getState();

        if (isEventLogConfigured()) {
            HashMap<String, ArrayList<String>> ecmap = _sb.getEventChannelMap();
            if (ic instanceof JoinCommand) {
                JoinCommand jc = (JoinCommand) ic;
                if (ecmap.get("log").contains(jc.getChannel().toLowerCase())
                        && jc.getUser().equals(_sb.getIRCConnection().getClientState().getNick()))
                    // Bot just joined the channel, send any saved messages stored while absent
                    saySavedList();
            }
        }
	}

	private void saySavedList() {
		if (sayList.isEmpty()) return;

        if (isEventLogConfigured() && _state == State.REGISTERED && inChannel()) {
            ArrayList<String> tmpList;
            synchronized(sayList) {
                tmpList = new ArrayList<String>(sayList);
                sayList.clear();
            }

            if (tmpList.size() == 1 && tmpList.get(0).equalsIgnoreCase("Starting up logfile monitoring.")) {
                sayLog(tmpList.get(0));
                return;
            }

            String head = ReplacerUtils.jprintf("savedlog.header", _env, SysopTailService.class);
            String foot = ReplacerUtils.jprintf("savedlog.footer", _env, SysopTailService.class);

            // hardcoded alternates, if not defined in .properties
            if (head == null || head.equals("")) head = "While I was out, this stuff happened:";
            if (foot == null || foot.equals("")) foot = "-end-";

            // List all logs that were missed
            sayLog(head, false);
            for (String line : tmpList) {
                sayLog(line);
            }
            sayLog(foot, false);
        }
	}

    private boolean isEventLogConfigured() {
        return _sb.getEventChannelMap().containsKey("log");
    }

	private boolean inChannel() {
        boolean ret = false;
        if (isEventLogConfigured()) {
            ClientState cs = _sb.getIRCConnection().getClientState();
            HashMap<String, ArrayList<String>> ecmap = _sb.getEventChannelMap();
            for (String channel : ecmap.get("log")) {
                ret = cs.isOnChannel(channel);
            }
        }
        return ret;
	}
}
