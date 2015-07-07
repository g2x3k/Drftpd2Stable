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

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.StringTokenizer;

import net.sf.drftpd.DuplicateElementException;
import net.sf.drftpd.event.ConnectionEvent;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.helpers.OptionConverter;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.PropertyHelper;
import org.drftpd.commands.Nuke;
import org.drftpd.commands.UserManagement;
import org.drftpd.plugins.SiteBot;
import org.drftpd.plugins.Statistics;
import org.drftpd.thirdparty.plus.Default;
import org.drftpd.usermanager.HostMask;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.tanesha.replacer.ReplacerEnvironment;

import f00f.net.irc.martyr.commands.MessageCommand;
import f00f.net.irc.martyr.util.FullNick;

/**
 * @author fr0w
 */

public class IRCUserManagement extends IRCCommand {

	private static final Logger logger = Logger.getLogger(IRCUserManagement.class);

	public IRCUserManagement(GlobalContext gctx) {
		super(gctx);
	}

	private User getUser(FullNick fn) {
		String ident = fn.getNick() + "!" + fn.getUser() + "@" + fn.getHost();
		User user = null;
		try {
			user = getGlobalContext().getUserManager().getUserByIdent(ident);
		} catch (Exception e) {
			logger.warn("Could not identify " + ident);
		}
		return user;
	}

	private void kickAll(User user, String message) {
		Integer status = 0;

		ArrayList<BaseFtpConnection> conns = new ArrayList<BaseFtpConnection>(
				getGlobalContext().getConnectionManager().getConnections());

		for (Iterator iter = conns.iterator(); iter.hasNext();) {
			BaseFtpConnection conn2 = (BaseFtpConnection) iter.next();

			try {
				if (conn2.getUser().getName().equals(user.getName())) {
					conn2.stop(message);
					status++;
				}
			} catch (NoSuchUserException e) {
			}
		}
	}

	public ArrayList<String> doADDIP(String args, MessageCommand msgc) {

		ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		StringTokenizer st = new StringTokenizer(args);

		User adder = getUser(msgc.getSource());
		env.add("ircnick", msgc.getSource().getNick());

		if (adder == null) {
			out.add(ReplacerUtils.jprintf("ident.noident", env, IRCUserManagement.class));
			return out;
		}

		User myUser;

		if (st.countTokens() < 2) {
			out.add(ReplacerUtils.jprintf("ip.syntax", env, IRCUserManagement.class));
			return out;
		}

		String _user = st.nextToken();

		env.add("targetuser", _user);
		env.add("user", adder.getName());

		while (st.hasMoreTokens()) {

			String ip = st.nextToken().replace(",", ""); // strip commas from iplist, for easy copy+paste

			try {
				myUser = getGlobalContext().getUserManager().getUserByName(_user);
				env.add("ip", ip);
				if ((new HostMask(ip)).isAllowed()) {
					try {
						myUser.addIPMask(ip);
						out.add(ReplacerUtils.jprintf("ip.added", env, IRCUserManagement.class));
						logger.info("Added ip to " + _user);
					} catch (DuplicateElementException e) {
						out.add(ReplacerUtils.jprintf("ip.exists", env, IRCUserManagement.class));
					}
				} else {
					out.add(ReplacerUtils.jprintf("ip.invalid", env, IRCUserManagement.class));
					logger.info("'" + adder.getName() +
            				"' was denied the add of ip '" + ip + "' to '" +
            				myUser.getName() + "'");
				}
				myUser.commit();
			} catch (NoSuchUserException ex) {
				out.add(ReplacerUtils.jprintf("nosuch.user", env, IRCUserManagement.class));
			} catch (UserFileException ex) {
				out.add(ReplacerUtils.jprintf("error.user", env, IRCUserManagement.class));
				logger.error("", ex);
			}
		}
		return out;
	}

	public ArrayList<String> doDELIP(String args, MessageCommand msgc) {

		ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		StringTokenizer st = new StringTokenizer(args);

		User adder = getUser(msgc.getSource());
		env.add("ircnick", msgc.getSource().getNick());

		if (adder == null) {
			out.add(ReplacerUtils.jprintf("ident.noident", env, IRCUserManagement.class));
			return out;
		}

		User myUser;

		if (st.countTokens() < 2) {
			out.add(ReplacerUtils.jprintf("ip.syntax", env, IRCUserManagement.class));
			return out;
		}

		String _user = st.nextToken();

		env.add("targetuser", _user);
		env.add("user", adder.getName());

		while (st.hasMoreTokens()) {

			String ip = st.nextToken().replace(",", ""); // strip commas from iplist, for easy copy+paste

			try {

				myUser = getGlobalContext().getUserManager().getUserByName(_user);

				try {
					myUser.removeIpMask(ip);
					env.add("ip", ip);
					out.add(ReplacerUtils.jprintf("ip.removed", env, IRCUserManagement.class));
					logger.info("Removed ip from " + _user);
				} catch (NoSuchFieldException e) {
					env.add("ip", ip);
					out.add(ReplacerUtils.jprintf("ip.dontexists", env, IRCUserManagement.class));
				}
			} catch (NoSuchUserException ex) {
				out.add(ReplacerUtils.jprintf("nosuch.user", env, IRCUserManagement.class));
			} catch (UserFileException ex) {
				out.add(ReplacerUtils.jprintf("error.user", env, IRCUserManagement.class));
				logger.error("", ex);
			}
		}
		return out;
	}

	public ArrayList<String> doADDUSER(String args, MessageCommand msgc) {

		ArrayList<String> out = new ArrayList<String>();
		ArrayList<String> error = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		StringTokenizer tok = new StringTokenizer(args);

		String text = msgc.getMessage();
		String[] cmd1 = text.split(" ");

		boolean isGAdduser = cmd1[0].contains("gadduser");

		if ((tok.countTokens() < 3) && (isGAdduser)) {
			out.add(ReplacerUtils.jprintf("adduser.syntax", env, IRCUserManagement.class));
			return out;
		}

		if ((tok.countTokens() < 2) && (!isGAdduser)) {
			out.add(ReplacerUtils.jprintf("adduser.syntax", env, IRCUserManagement.class));
			return out;
		}

		String newGroup = null;
		StringTokenizer st = new StringTokenizer(args);
		User newUser = null;

		User adder = getUser(msgc.getSource());
		env.add("ircnick", msgc.getSource().getNick());

		if (adder == null) {
			out.add(ReplacerUtils.jprintf("ident.noident", env, IRCUserManagement.class));
			return out;
		}

		if (isGAdduser) {
			newGroup = st.nextToken();
		}

		String newUsername = st.nextToken();
		env.add("targetuser", newUsername);
		env.add("user", adder.getName());
		String pass = st.nextToken();

		Default def;

		try {
			newUser = getGlobalContext().getUserManager().create(newUsername);

			def = Default.getInstance();

			newUser.setPasswordEnc(pass);
			newUser.getKeyedMap().setObject(UserManagement.CREATED, new Date());
			newUser.getKeyedMap().setObject(UserManagement.COMMENT, "Added by " + adder.getName());
			newUser.getKeyedMap().setObject(UserManagement.RATIO, def.RATIO);
			newUser.getKeyedMap().setObject(UserManagement.GROUPSLOTS, 0);
			newUser.getKeyedMap().setObject(UserManagement.LEECHSLOTS, 0);
			newUser.getKeyedMap().setObject(UserManagement.MAXLOGINS, def.MAXLOGINS);
			newUser.getKeyedMap().setObject(UserManagement.MAXLOGINSIP, def.MAXLOGINSIP);
			newUser.getKeyedMap().setObject(UserManagement.MINRATIO, def.MINRATIO);
			newUser.getKeyedMap().setObject(UserManagement.MAXRATIO, def.MAXRATIO);
			newUser.getKeyedMap().setObject(UserManagement.MAXSIMUP, def.MAXSIMUP);
			newUser.getKeyedMap().setObject(UserManagement.MAXSIMDN, def.MAXSIMDN);
			newUser.getKeyedMap().setObject(Statistics.LOGINS, 0);
			newUser.getKeyedMap().setObject(UserManagement.CREATED, new Date());
			newUser.getKeyedMap().setObject(UserManagement.LASTSEEN, new Date());
			newUser.getKeyedMap().setObject(UserManagement.WKLY_ALLOTMENT, def.WKLYALLOT);
			newUser.getKeyedMap().setObject(UserManagement.IRCIDENT, "N/A");
			newUser.getKeyedMap().setObject(UserManagement.TAGLINE, def.TAGLINE);
			newUser.getKeyedMap().setObject(UserManagement.BAN_TIME, new Date());
			newUser.getKeyedMap().setObject(Nuke.NUKED, 0);
			newUser.getKeyedMap().setObject(Nuke.NUKEDBYTES, new Long(0));
			newUser.setIdleTime(def.IDLETIME);
			newUser.setCredits(def.CREDITS);

		} catch (UserFileException ex) {
			logger.warn("", ex);
			error.add(ReplacerUtils.jprintf("adduser.dupe", env, IRCUserManagement.class));
			return error;
		}

		if (newGroup != null) {
			newUser.setGroup(newGroup);
			logger.info("'" + adder.getName() +
					"' added '" + newUser.getName() + "' with group " +
					newUser.getGroup() + "'");
			env.add("primgroup", newUser.getGroup());
			out.add(ReplacerUtils.jprintf("adduser.group", env, IRCUserManagement.class));
		} else {
			logger.info("'" + adder.getName() + "' added '" + newUser.getName() + "'");
			newUser.setGroup(def.GROUP);
		}
		out.add(ReplacerUtils.jprintf("adduser.success", env, IRCUserManagement.class));

		try {
			while (st.hasMoreTokens()) {
				String string = st.nextToken();
				env.add("ip", string);
				if ((new HostMask(string)).isAllowed()) { //validate a hostmask
					try {
						newUser.addIPMask(string);
						out.add(ReplacerUtils.jprintf("ip.added", env, IRCUserManagement.class));
						logger.info("'" + adder.getName() + "' added ip '"
								+ string + "' to '" + newUser.getName() + "'");
					} catch (DuplicateElementException e1) {
						out.add(ReplacerUtils.jprintf("ip.exists", env, IRCUserManagement.class));
					}
				} else {
					out.add(ReplacerUtils.jprintf("ip.invalid", env, IRCUserManagement.class));
					error.add(ReplacerUtils.jprintf("ip.invalid", env, IRCUserManagement.class));
					logger.info("'" + adder.getName() +
            				"' was denied the add of ip '" + string + "' to '" +
            				newUser.getName() + "'");
				}
			}
			newUser.commit();

			if (newUser.getHostMaskCollection().size() < 1) {
				newUser.purge();
            	error.add(ReplacerUtils.jprintf("adduser.nomasks", env, IRCUserManagement.class));
				return error;
			}
		} catch (UserFileException ex) {
			logger.warn("", ex);
			error.add(ReplacerUtils.jprintf("error.user", env, IRCUserManagement.class));
			return error;
		}

		return out;
	}

	public ArrayList<String> doDELUSER(String args, MessageCommand msgc) {

		ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		StringTokenizer st = new StringTokenizer(args);

		User myUser;
		User adder = getUser(msgc.getSource());
		env.add("ircnick", msgc.getSource().getNick());

		if (adder == null) {
			out.add(ReplacerUtils.jprintf("ident.noident", env, IRCUserManagement.class));
			return out;
		}

		if (st.countTokens() < 1) {
			out.add(ReplacerUtils.jprintf("deluser.syntax", env, IRCUserManagement.class));
			return out;
		}

		String _user = st.nextToken();
		env.add("targetuser", _user);
		env.add("user", adder.getName());

		try {
			myUser = getGlobalContext().getUserManager().getUserByName(_user);
		} catch (NoSuchUserException e) {
			out.add(ReplacerUtils.jprintf("nosuch.user", env, IRCUserManagement.class));
			return out;
		} catch (UserFileException e) {
			out.add(ReplacerUtils.jprintf("error.user", env, IRCUserManagement.class));
			return out;
		}

		myUser.setDeleted(true);
		try {
			myUser.commit();
		} catch (UserFileException e1) {
			out.add(ReplacerUtils.jprintf("error.user", env, IRCUserManagement.class));
			return out;
		}
		logger.info("'" + adder.getName() + "' deleted user '"
				+ myUser.getName());

		kickAll(myUser, "User has been deleted");
		out.add(ReplacerUtils.jprintf("deluser.success", env, IRCUserManagement.class));

		return out;

	}

	public ArrayList<String> doREADD(String args, MessageCommand msgc) {
		ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		StringTokenizer st = new StringTokenizer(args);

		User myUser;
		User adder = getUser(msgc.getSource());
		env.add("ircnick", msgc.getSource().getNick());

		if (adder == null) {
			out.add(ReplacerUtils.jprintf("ident.noident", env, IRCUserManagement.class));
			return out;
		}

		if (st.countTokens() < 1) {
			out.add(ReplacerUtils.jprintf("readd.syntax", env, IRCUserManagement.class));
			return out;
		}

		String _user = st.nextToken();
		env.add("targetuser", _user);
		env.add("user", adder.getName());

		try {
			myUser = getGlobalContext().getUserManager().getUserByNameIncludeDeleted(_user);
		} catch (NoSuchUserException e) {
			out.add(ReplacerUtils.jprintf("nosuch.user", env, IRCUserManagement.class));
			return out;
		} catch (UserFileException e) {
			out.add(ReplacerUtils.jprintf("error.user", env, IRCUserManagement.class));
			return out;
		}

		if (!myUser.isDeleted()) {
        	out.add(ReplacerUtils.jprintf("readd.notdeleted", env, IRCUserManagement.class));
			return out;
		}

		myUser.setDeleted(false);
		myUser.getKeyedMap().remove(UserManagement.REASON);
		try {
			myUser.commit();
		} catch (UserFileException e1) {
			out.add(ReplacerUtils.jprintf("error.user", env, IRCUserManagement.class));
			return out;
		}

		logger.info("'" + adder.getName() + "' readded '" + myUser.getName() + "'");

		out.add(ReplacerUtils.jprintf("readd.success", env, IRCUserManagement.class));

		return out;
	}

	public ArrayList<String> doPURGE(String args, MessageCommand msgc) {

		ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		StringTokenizer st = new StringTokenizer(args);

		boolean isDelPurge = msgc.getMessage().startsWith("!delpurge");

		User myUser;
		User adder = getUser(msgc.getSource());
		env.add("ircnick", msgc.getSource().getNick());

		if (adder == null) {
			out.add(ReplacerUtils.jprintf("ident.noident", env, IRCUserManagement.class));
			return out;
		}

		if (st.countTokens() < 1) {
			out.add(ReplacerUtils.jprintf("purge.syntax", env, IRCUserManagement.class));
			return out;
		}

		String _user = st.nextToken();
		env.add("targetuser", _user);
		env.add("user", adder.getName());

		try {
			myUser = getGlobalContext().getUserManager()
					.getUserByNameUnchecked(_user);
		} catch (NoSuchUserException e) {
			out.add(ReplacerUtils.jprintf("nosuch.user", env, IRCUserManagement.class));
			return out;
		} catch (UserFileException e) {
			out.add(ReplacerUtils.jprintf("error.user", env, IRCUserManagement.class));
			return out;
		}

		if (!myUser.isDeleted()) {
			if (!isDelPurge) {
				out.add(ReplacerUtils.jprintf("purge.notdeleted", env, IRCUserManagement.class));
				return out;
			} else {
				myUser.setDeleted(true);
			}
		}

		kickAll(myUser, "User has been purged");

		myUser.purge();
		logger.info("'" + adder.getName() + "' purged '" + myUser.getName() + "'");
		out.add(ReplacerUtils.jprintf("purge.success", env, IRCUserManagement.class));
		return out;
	}

	public ArrayList<String> doCHANGE(String args, MessageCommand msgc) {

	    Collection<User> users = new ArrayList<User>();
	    ArrayList<String> out = new ArrayList<String>();
	    ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
	    StringTokenizer st = new StringTokenizer(args);

	    User thisUser;

	    env.add("ircnick", msgc.getSource().getNick());

	    User adder = getUser(msgc.getSource());
	    if (adder == null) {
	        out.add(ReplacerUtils.jprintf("ident.noident", env, IRCUserManagement.class));
	        return out;
	    }

	    if (st.countTokens() < 3) {
	        out.add(ReplacerUtils.jprintf("change.syntax", env, IRCUserManagement.class));
	        return out;
	    }

	    String _user = st.nextToken();
	    String func = st.nextToken();
	    String parm = ""; 
        String parm1 = st.nextToken();
        String parmrest = (st.hasMoreTokens() ? st.nextToken("") : "");

	    String old = null;

	    env.add("targetuser", _user);
	    env.add("user", adder.getName());
	    env.add("func", func.toLowerCase());

	    try {
	        if (_user.startsWith("=")) {
	            String group = _user.replace("=", "");
	            users = getGlobalContext().getUserManager().getAllUsersByGroup(group);
	        } else if (_user.equals("*")) {
	            users = getGlobalContext().getUserManager().getAllUsers();
	        } else
	            users.add(getGlobalContext().getUserManager().getUserByNameUnchecked(_user));
	    } catch (NoSuchUserException e) {
	        out.add(ReplacerUtils.jprintf("nosuch.user", env, IRCUserManagement.class));
	        return out;
	    } catch (UserFileException e) {
	        out.add(ReplacerUtils.jprintf("error.user", env, IRCUserManagement.class));
	        return out;
	    }

	    Iterator<User> userIterator = users.iterator();

	    while (userIterator.hasNext()) {
	        thisUser = userIterator.next();
            // reset tokenizer and parm for each user.
            st = new StringTokenizer(parmrest);
            parm = parm1;

	        if (func.equalsIgnoreCase("group")) {

	            if (thisUser.getGroup().equals(parm)) {
	                out.add(ReplacerUtils.jprintf("change.grpdupe", env, IRCUserManagement.class));
	                return out;
	            }
	            old = thisUser.getGroup();
	            env.add("old", old);
	            env.add("new", parm);
	            thisUser.setGroup(parm);

	        } else if (func.equalsIgnoreCase("ratio")) {

	            float ratio;
	            env.add("new", parm);
	            try {
	                ratio = Float.parseFloat(parm);
	            } catch (NumberFormatException e) {
	                out.add(ReplacerUtils.jprintf("change.numex", env, IRCUserManagement.class));
	                return out;
	            }
	            old = String.valueOf(thisUser.getKeyedMap().getObjectFloat(UserManagement.RATIO));
	            thisUser.getKeyedMap().setObject(UserManagement.RATIO, ratio);

	        } else if (func.equalsIgnoreCase("num_logins")) {
	            try {
	                int numLogins;
	                int numLoginsIP;

	                numLogins = Integer.parseInt(parm);

	                if (st.hasMoreTokens()) {
	                    numLoginsIP = Integer.parseInt(st.nextToken());
	                } else {
	                    numLoginsIP = thisUser.getKeyedMap().getObjectInt(UserManagement.MAXLOGINSIP);
	                }
	                parm = parm + " " + String.valueOf(numLoginsIP);
	                old = thisUser.getKeyedMap().getObjectInt(UserManagement.MAXLOGINS) 
	                + " " + thisUser.getKeyedMap().getObjectInt(UserManagement.MAXLOGINSIP);

	                thisUser.getKeyedMap().setObject(UserManagement.MAXLOGINS,numLogins);
	                thisUser.getKeyedMap().setObject(UserManagement.MAXLOGINSIP,numLoginsIP);
	                env.add("numlogins", "" + numLogins);
	                env.add("numloginsip", "" + numLoginsIP);
	                env.add("new", parm);
	            } catch (NumberFormatException ex) {
	                out.add(ReplacerUtils.jprintf("change.numex", env, IRCUserManagement.class));
	                return out;
	            }
	        } else if (func.equalsIgnoreCase("max_sim")) {
	            try {
	                int maxup;
	                int maxdn;

	                if (!st.hasMoreTokens()) {
	                    out.add(ReplacerUtils.jprintf("change.syntax", env, IRCUserManagement.class));
	                    return out;
	                }

	                maxdn = Integer.parseInt(parm);
	                maxup = Integer.parseInt(st.nextToken());
	                parm = parm + " " + String.valueOf(maxup);

	                old = thisUser.getMaxSimDown() + " " + thisUser.getMaxSimUp();

	                thisUser.setMaxSimUp(maxup);
	                thisUser.setMaxSimDown(maxdn);

	                env.add("new", parm);

	            } catch (NumberFormatException ex) {
	                out.add(ReplacerUtils.jprintf("change.numex", env, IRCUserManagement.class));
	                return out;
	            }
	        } else if (func.equalsIgnoreCase("wkly_allotment")) {
	            try {
	                env.add("new", parm);
	                long bytes = Bytes.parseBytes(parm);
	                old = Bytes.formatBytes(thisUser.getKeyedMap().getObjectLong(UserManagement.WKLY_ALLOTMENT));
	                thisUser.getKeyedMap().setObject(UserManagement.WKLY_ALLOTMENT, bytes);
	            } catch (NumberFormatException ex) {
	                out.add(ReplacerUtils.jprintf("change.numex", env, IRCUserManagement.class));
	                return out;
	            } catch (IllegalArgumentException ex) {

	            }
	        } else {
	            out.add(ReplacerUtils.jprintf("change.syntax", env, IRCUserManagement.class));
	            return out;
	        }

	        try {
	            thisUser.commit();
	        } catch (UserFileException e) {
	            logger.warn("", e);
	            out.add(ReplacerUtils.jprintf("error.user", env, IRCUserManagement.class));
	            return out;
	        }

	        out.add(ReplacerUtils.jprintf("change.success", env, IRCUserManagement.class));

	        logger.info("'" + adder.getName() + "' changed " + func + " of '"
	                + thisUser.getName() + "' from '" + old + "' to '" + parm + "'");
	    }
	    return out;
	}

	public ArrayList<String> doCHGRP(String args, MessageCommand msgc) {

		ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		StringTokenizer st = new StringTokenizer(args);

		User myUser;

		env.add("ircnick", msgc.getSource().getNick());

		User adder = getUser(msgc.getSource());
		if (adder == null) {
			out.add(ReplacerUtils.jprintf("ident.noident", env, IRCUserManagement.class));
			return out;
		}

		if (st.countTokens() < 2) {
			out.add(ReplacerUtils.jprintf("chgrp.syntax", env, IRCUserManagement.class));
			return out;
		}

		String _user = st.nextToken();
		env.add("targetuser", _user);

		try {
			myUser = getGlobalContext().getUserManager().getUserByName(_user);
		} catch (NoSuchUserException e) {
			out.add(ReplacerUtils.jprintf("nosuch.user", env, IRCUserManagement.class));
			return out;
		} catch (UserFileException e) {
			out.add(ReplacerUtils.jprintf("error.user", env, IRCUserManagement.class));
			return out;
		}

		while (st.hasMoreTokens()) {

			String secgrp = st.nextToken();
			env.add("secgrp", secgrp);

			try {
				myUser.removeSecondaryGroup(secgrp);
				logger.info("'" + adder.getName() + "' removed '"
						+ myUser.getName() + "' from group '" + secgrp + "'");
				out.add(ReplacerUtils.jprintf("chgrp.remove", env, IRCUserManagement.class));
			} catch (NoSuchFieldException e1) {
				try {
					myUser.addSecondaryGroup(secgrp);
					logger.info("'" + adder.getName() + "' added '"
							+ myUser.getName() + "' to group '" + secgrp + "'");
					out.add(ReplacerUtils.jprintf("chgrp.add", env, IRCUserManagement.class));
				} catch (DuplicateElementException e2) {
					out.add(ReplacerUtils.jprintf("chgrp.error", env, IRCUserManagement.class));
				}
			}
		}
		try {
			myUser.commit();
		} catch (UserFileException e) {
			out.add(ReplacerUtils.jprintf("error.user", env, IRCUserManagement.class));
		}
		return out;
	}

	public ArrayList<String> doBAN(String args, MessageCommand msgc) {

		ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		StringTokenizer st = new StringTokenizer(args);

		User myUser;

		env.add("ircnick", msgc.getSource().getNick());

		User adder = getUser(msgc.getSource());
		if (adder == null) {
			out.add(ReplacerUtils.jprintf("ident.noident", env, IRCUserManagement.class));
			return out;
		}

		if (st.countTokens() < 2) {
			out.add(ReplacerUtils.jprintf("ban.syntax", env, IRCUserManagement.class));
			return out;
		}

		String _user = st.nextToken();
		env.add("targetuser", _user);

		try {
			myUser = getGlobalContext().getUserManager().getUserByName(_user);
		} catch (NoSuchUserException e) {
			out.add(ReplacerUtils.jprintf("nosuch.user", env, IRCUserManagement.class));
			return out;
		} catch (UserFileException e) {
			out.add(ReplacerUtils.jprintf("error.user", env, IRCUserManagement.class));
			return out;
		}

		long banTime;
		try {
			banTime = Long.parseLong(st.nextToken());
			env.add("bantime", banTime);
		} catch (NumberFormatException e) {
			out.add(ReplacerUtils.jprintf("ban.time", env, IRCUserManagement.class));
			return out;
		}

		String banMsg;

		if (st.hasMoreTokens()) {
			banMsg = "[" + adder.getName() + "]";
			while (st.hasMoreTokens())
				banMsg += " " + st.nextToken();
			env.add("banmsg", banMsg);
		} else {
			banMsg = "Banned by " + adder.getName() + " for " + banTime + "m";
			env.add("banmsg", banMsg);
		}

		myUser.getKeyedMap().setObject(UserManagement.BAN_TIME,
				new Date(System.currentTimeMillis() + (banTime * 60000)));
		myUser.getKeyedMap().setObject(UserManagement.BAN_REASON, banMsg);

		try {
			myUser.commit();
		} catch (UserFileException e) {
			out.add(ReplacerUtils.jprintf("error.user", env, IRCUserManagement.class));
			return out;
		}

		out.add(ReplacerUtils.jprintf("ban.success", env, IRCUserManagement.class));
		kickAll(myUser, banMsg);
		return out;
	}

	public ArrayList<String> doUNBAN(String args, MessageCommand msgc) {

		ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		StringTokenizer st = new StringTokenizer(args);

		User myUser;

		env.add("ircnick", msgc.getSource().getNick());

		User adder = getUser(msgc.getSource());
		if (adder == null) {
			out.add(ReplacerUtils.jprintf("ident.noident", env, IRCUserManagement.class));
			return out;
		}

		if (st.countTokens() < 1) {
			out.add(ReplacerUtils.jprintf("unban.syntax", env, IRCUserManagement.class));
			return out;
		}

		String _user = st.nextToken();
		env.add("targetuser", _user);

		try {
			myUser = getGlobalContext().getUserManager().getUserByName(_user);
		} catch (NoSuchUserException e) {
			out.add(ReplacerUtils.jprintf("nosuch.user", env, IRCUserManagement.class));
			return out;
		} catch (UserFileException e) {
			out.add(ReplacerUtils.jprintf("error.user", env, IRCUserManagement.class));
			return out;
		}

		myUser.getKeyedMap().setObject(UserManagement.BAN_TIME, new Date());
		myUser.getKeyedMap().setObject(UserManagement.BAN_REASON, "");

		try {
			myUser.commit();
		} catch (UserFileException e) {
			out.add(ReplacerUtils.jprintf("error.user", env, IRCUserManagement.class));
			return out;
		}

		out.add(ReplacerUtils.jprintf("unban.success", env, IRCUserManagement.class));
		return out;
	}

	public ArrayList<String> doKICKUSER(String args, MessageCommand msgc) {

		ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		StringTokenizer st = new StringTokenizer(args);

		User myUser;

		env.add("ircnick", msgc.getSource().getNick());

		User adder = getUser(msgc.getSource());
		if (adder == null) {
			out.add(ReplacerUtils.jprintf("ident.noident", env, IRCUserManagement.class));
			return out;
		}

		if (st.countTokens() < 1) {
			out.add(ReplacerUtils.jprintf("kick.syntax", env, IRCUserManagement.class));
			return out;
		}

		String _user = st.nextToken();
		env.add("targetuser", _user);

		try {
			myUser = getGlobalContext().getUserManager().getUserByName(_user);
		} catch (NoSuchUserException e) {
			out.add(ReplacerUtils.jprintf("nosuch.user", env, IRCUserManagement.class));
			return out;
		} catch (UserFileException e) {
			out.add(ReplacerUtils.jprintf("error.user", env, IRCUserManagement.class));
			return out;
		}

		String msg;
		if (st.hasMoreTokens())
			msg = "Kicked by " + adder.getName() + " with reason: " + st.nextToken();
		else
			msg = "Kicked by " + adder.getName();

		env.add("kickmsg", msg);
		kickAll(myUser, msg);

		out.add(ReplacerUtils.jprintf("kick.user", env, IRCUserManagement.class));
		return out;
	}

	public ArrayList<String> doGIVETAKE(String args, MessageCommand msgc) {
		ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		StringTokenizer st = new StringTokenizer(args);

		User myUser;

		env.add("ircnick", msgc.getSource().getNick());

		User adder = getUser(msgc.getSource());
		if (adder == null) {
			out.add(ReplacerUtils.jprintf("ident.noident", env, IRCUserManagement.class));
			return out;
		}

		if (st.countTokens() < 2) {
			out.add(ReplacerUtils.jprintf("gt.syntax", env, IRCUserManagement.class));
			return out;
		}

		String _user = st.nextToken();
		env.add("targetuser", _user);

		try {
			myUser = getGlobalContext().getUserManager().getUserByName(_user);
		} catch (NoSuchUserException e) {
			out.add(ReplacerUtils.jprintf("nosuch.user", env, IRCUserManagement.class));
			return out;
		} catch (UserFileException e) {
			out.add(ReplacerUtils.jprintf("error.user", env, IRCUserManagement.class));
			return out;
		}

		long credits = Bytes.parseBytes(st.nextToken());

		if (0 > credits) {
        	out.add(ReplacerUtils.jprintf("gt.error", env, IRCUserManagement.class));
			return out;
		}

		boolean isGive = true;
		String[] text = msgc.getMessage().split(" ");
		String act, cmd, tofrom;

		if (text[0].contains("take"))
			isGive = false;

		if (isGive) {
			act = "Gave";
			cmd = "GIVE";
			tofrom = "to";
		} else {
			act = "Took";
			cmd = "TAKE";
			tofrom = "from";
		}

		logger.info("'" + adder.getName() + "' " + act.toLowerCase() + " "
				+ Bytes.formatBytes(credits) + " ('" + credits + "') from '"
				+ myUser.getName() + "'");
		env.add("cmd", cmd);
		env.add("act", act);
		env.add("to.from", tofrom);
		env.add("creds", Bytes.formatBytes(credits));
		myUser.updateCredits(isGive ? credits : -credits);
        out.add(ReplacerUtils.jprintf("gt.success", env, IRCUserManagement.class));

		return out;
	}

	public ArrayList<String> doRELOAD(String args, MessageCommand msgc) {

		ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);

		env.add("ircnick", msgc.getSource().getNick());

		User adder = getUser(msgc.getSource());
		if (adder == null) {
			out.add(ReplacerUtils.jprintf("ident.noident", env, IRCUserManagement.class));
			return out;
		}

		try {
			getGlobalContext().getSectionManager().reload();
			getGlobalContext().reloadFtpConfig();
			getGlobalContext().getSlaveSelectionManager().reload();

			try {
				getGlobalContext().getJobManager().reload();
			} catch (IllegalStateException e1) {
				// not loaded, don't reload
			}

			getGlobalContext().getConnectionManager()
					.getCommandManagerFactory().reload();

		} catch (IOException e) {
			logger.fatal("Error reloading config", e);
			out.add(ReplacerUtils.jprintf("reload.config", env, IRCUserManagement.class));
			return out;
		}

		getGlobalContext().dispatchFtpEvent(
				new ConnectionEvent(adder, "RELOAD"));

		// ugly hack to clear resourcebundle cache
		// see
		// http://developer.java.sun.com/developer/bugParade/bugs/4212439.html
		try {
			Field cacheList = ResourceBundle.class
					.getDeclaredField("cacheList");
			cacheList.setAccessible(true);
			((Map) cacheList.get(ResourceBundle.class)).clear();
			cacheList.setAccessible(false);
		} catch (Exception e) {
			logger.error("", e);
		}

		try {
			OptionConverter.selectAndConfigure(
					new URL(PropertyHelper.getProperty(System.getProperties(),
							"log4j.configuration")), null, LogManager
							.getLoggerRepository());
		} catch (MalformedURLException e) {
			env.add("error", e);
			out.add(ReplacerUtils.jprintf("reload.error", env, IRCUserManagement.class));
			return out;
		} finally {
		}
		out.add(ReplacerUtils.jprintf("reload.success", env, IRCUserManagement.class));
		return out;
	}

	public ArrayList<String> doLOOKUP(String args, MessageCommand msgc) {
		ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);

		env.add("ircnick", msgc.getSource().getNick());

		User adder = getUser(msgc.getSource());
		if (adder == null) {
			out.add(ReplacerUtils.jprintf("ident.noident", env, IRCUserManagement.class));
			return out;
		}

		StringTokenizer st = new StringTokenizer(args);
		if (!st.hasMoreTokens()) {
			out.add(ReplacerUtils.jprintf("lookup.syntax", env, IRCUserManagement.class));
			return out;
		}

		String lookup = st.nextToken();
		Collection<User> ret;
		env.add("lookup.user", lookup);

		try {
			ret = getGlobalContext().getUserManager().getAllUsers();
		} catch (UserFileException e) {
			out.add(ReplacerUtils.jprintf("error.user", env, IRCUserManagement.class));
			return out;
		}

		boolean found = false;
		User user = null;
		for (Iterator<User> iter = ret.iterator(); iter.hasNext();) {
			User u = iter.next();
			String ident = u.getKeyedMap().getObjectString(UserManagement.IRCIDENT);
			if (ident == null || ident.equals("") || ident.equalsIgnoreCase("N/A")) // seems to handle everything
				continue;

			String nick = ident.substring(0, ident.indexOf('!'));
			if (nick.equalsIgnoreCase(lookup)) {
				found = true;
				user = u;
				break;
			}
		}

		if (found && user != null) { // prevents NullPointerException (but they shouldn't happen anyway)
			env.add("ftpuser", user.getName());
			out.add(ReplacerUtils.jprintf("lookup.success", env, IRCUserManagement.class));
		} else
			out.add(ReplacerUtils.jprintf("lookup.failed", env, IRCUserManagement.class));

		return out;
	}

	public ArrayList<String> doNICK(String args, MessageCommand msgc) {
		ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);

		env.add("ircnick", msgc.getSource().getNick());

		User adder = getUser(msgc.getSource());
		if (adder == null) {
			out.add(ReplacerUtils.jprintf("ident.noident", env, IRCUserManagement.class));
			return out;
		}

		StringTokenizer st = new StringTokenizer(args);
		if (!st.hasMoreTokens()) {
			out.add(ReplacerUtils.jprintf("nick.syntax", env, IRCUserManagement.class));
			return out;
		}

		boolean found = false;
		User luser = null;
		String login = st.nextToken();
		env.add("ftpuser", login);

		try {
			luser = getGlobalContext().getUserManager().getUserByName(login);
			found = true;
		} catch (NoSuchUserException e) {
			env.add("targetuser", login);
			out.add(ReplacerUtils.jprintf("nosuch.user", env, IRCUserManagement.class));
			return out;
		} catch (UserFileException e) {
			out.add(ReplacerUtils.jprintf("error.user", env, IRCUserManagement.class));
			return out;
		}

		String ident;
		if (found) {
			 ident = luser.getKeyedMap().getObjectString(UserManagement.IRCIDENT);
			if (ident == null || ident.equals("") || ident.equalsIgnoreCase("N/A")) { 
				found = false;
			}
		}

		if (found) {
			ident = luser.getKeyedMap().getObjectString(UserManagement.IRCIDENT);
			env.add("nick.nickname", ident.substring(0, ident.lastIndexOf('!')));
			out.add(ReplacerUtils.jprintf("nick.success", env, IRCUserManagement.class));
		} else {
			out.add(ReplacerUtils.jprintf("nick.failed", env, IRCUserManagement.class));
		}
		return out;
	}

	public ArrayList<String> doFINDUSER(String args, MessageCommand msgc) {
		ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);

		env.add("ircnick", msgc.getSource().getNick());

		User adder = getUser(msgc.getSource());
		if (adder == null) {
			out.add(ReplacerUtils.jprintf("ident.noident", env, IRCUserManagement.class));
			return out;
		}

		if (args == null || args.length() == 0) {
			out.add(ReplacerUtils.jprintf("finduser.syntax", env, IRCUserManagement.class));
			return out;
		}

		String[] words = args.replaceAll(" ","").toUpperCase().split(","); // remove spaces
		Collection<User> users;
		
		ArrayList<String> matches = new ArrayList<String>();

		try {
			 users = getGlobalContext().getUserManager().getAllUsers();
		} catch (UserFileException e) {
			out.add(ReplacerUtils.jprintf("error.user", env, IRCUserManagement.class));
			return out;
		}

		for (User user : users) {
			StringBuffer sb = new StringBuffer();
			String comment = user.getKeyedMap().getObjectString(UserManagement.COMMENT).toUpperCase();
			String ircident = user.getKeyedMap().getObjectString(UserManagement.IRCIDENT).toUpperCase();
			for (String str : words) {
				//-user, =group, +ident, ~comment
				boolean found = false;
				if (user.getName().toUpperCase().indexOf(str) != -1) {
					sb.append('-');
					found = true;
				} if (ircident.indexOf(str) != -1) {
					sb.append('+');
					found = true;
				} if (comment.indexOf(str) != -1) {
					sb.append('~');
					found = true;
				} 
				
				if (found) {
					sb.append(user.getName());
					matches.add(sb.toString());
					break; // user already matched a string no need to continue;
				}
			}
		}

		if (matches.isEmpty()) {
			out.add(ReplacerUtils.jprintf("finduser.nomatches", env, IRCUserManagement.class));
			return out;
		}

		StringBuffer sb = new StringBuffer();
		for (Iterator<String> iter = matches.iterator(); iter.hasNext();) {
			sb.append(iter.next());
			if (iter.hasNext())
				sb.append(", ");
			else
				sb.append('.');
		}

		env.add("matches", sb.toString());
		env.add("num", matches.size());
		out.add(ReplacerUtils.jprintf("finduser.success", env, IRCUserManagement.class));
		return out;
	}
	
	public ArrayList<String> doUSER(String args, MessageCommand msgc) {
		ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);	
		StringTokenizer st = new StringTokenizer(args);
		
		env.add("ircnick", msgc.getSource().getNick());

		if (st.countTokens() < 1) {
			out.add(ReplacerUtils.jprintf("user.syntax", env, IRCUserManagement.class));
			return out;
		}

		String _user = st.nextToken();
		env.add("targetuser", _user);
		
		User myUser;
		try {
			myUser = getGlobalContext().getUserManager().getUserByNameIncludeDeleted(_user);
		} catch (NoSuchUserException e) {
			out.add(ReplacerUtils.jprintf("nosuch.user", env, IRCUserManagement.class));
			return out;
		} catch (UserFileException e) {
			out.add(ReplacerUtils.jprintf("error.user", env, IRCUserManagement.class));
			return out;
		}
		
		String[] output = BaseFtpConnection.jprintf(UserManagement.class, "user", null, myUser).split("\n");
		for (String t : output) {
			env.add("line", t);
			out.add(ReplacerUtils.jprintf("user.line", env, IRCUserManagement.class));
		}
		
        return out;
    }
	
	public ArrayList<String> doLASTSEEN(String args, MessageCommand msgc) {
		ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		
		env.add("ircnick", msgc.getSource().getNick());
		
		User adder = getUser(msgc.getSource());
		if (adder == null) {
			out.add(ReplacerUtils.jprintf("ident.noident", env, IRCUserManagement.class));
			return out;
		}
		
		StringTokenizer st = new StringTokenizer(args);
		if (!st.hasMoreTokens()) {
			out.add(ReplacerUtils.jprintf("lastseen.syntax", env, IRCUserManagement.class));
			return out;
		}
		
		User luser = null;
		String login = st.nextToken();
		env.add("ftpuser", login);
		
		try {
			luser = getGlobalContext().getUserManager().getUserByName(login);
			Date d = (Date) luser.getKeyedMap().getObject(UserManagement.LASTSEEN, new Date(0));
			env.add("date", d.toString());
			out.add(ReplacerUtils.jprintf("lastseen.success", env, IRCUserManagement.class));
		} catch (NoSuchUserException e) {
			out.add(ReplacerUtils.jprintf("nosuch.user", env, IRCUserManagement.class));
			return out;
		} catch (UserFileException e) {
			out.add(ReplacerUtils.jprintf("error.user", env, IRCUserManagement.class));
			return out;
		}
		return out;
	}
	
	public ArrayList<String> doUNIDENT(String args, MessageCommand msgc) {
		ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		
		env.add("ircnick", msgc.getSource().getNick());
		
		User adder = getUser(msgc.getSource());
		if (adder == null) {
			out.add(ReplacerUtils.jprintf("ident.noident", env, IRCUserManagement.class));
			return out;
		}
		
		StringTokenizer st = new StringTokenizer(args);
		if (!st.hasMoreTokens()) {
			out.add(ReplacerUtils.jprintf("unident.syntax", env, IRCUserManagement.class));
			return out;
		}
		
		User luser = null;
		String login = st.nextToken();
		env.add("ftpuser", login);
		
		try {
			luser = getGlobalContext().getUserManager().getUserByName(login);
			String ircident = luser.getKeyedMap().getObjectString(UserManagement.IRCIDENT);
			if (ircident == null || ircident.length() == 0) {
				out.add(ReplacerUtils.jprintf("unident.error", env, IRCUserManagement.class));
			} else {
				env.add("ircident", ircident);
				out.add(ReplacerUtils.jprintf("unident.success", env, IRCUserManagement.class));
			}
			luser.getKeyedMap().setObject(UserManagement.IRCIDENT, "");
		} catch (NoSuchUserException e) {
			out.add(ReplacerUtils.jprintf("nosuch.user", env, IRCUserManagement.class));
			return out;
		} catch (UserFileException e) {
			out.add(ReplacerUtils.jprintf("error.user", env, IRCUserManagement.class));
			return out;
		} 
		return out;
	}
}