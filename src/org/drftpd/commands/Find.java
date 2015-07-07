/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.commands;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.mirroring.Job;
import net.tmods.extra.CNuke;

import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.SFVFile;
import org.drftpd.SFVFile.SFVStatus;
import org.drftpd.master.RemoteSlave;
import org.drftpd.plugins.DIZFile;
import org.drftpd.plugins.DIZPlugin;
import org.drftpd.remotefile.FileStillTransferringException;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.remotefile.MLSTSerialize;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

/**
 * SITE FIND <options>-action <action>Options: -user <user>-group
 * <group>-nogroup -nouser Options: -mtime [-]n -type [f|d] -slave <slave>-size
 * [-]size Options: -name <name>(* for wildcard) -incomplete -offline Actions:
 * print, wipe, delete Multipe options and actions are allowed. If multiple
 * options are given a file must match all options for action to be taken.
 * 
 * @author pyrrhic
 * @author mog
 * @version $Id: Find.java 1847 2007-12-05 01:30:05Z tdsoul $
 */
public class Find implements CommandHandler, CommandHandlerFactory {

	private static class ActionDeleteFromSlaves implements Action {
		private HashSet<RemoteSlave> _deleteFromSlaves;

		public ActionDeleteFromSlaves(HashSet<RemoteSlave> deleteFromSlaves) {
			assert deleteFromSlaves != null;
			_deleteFromSlaves = deleteFromSlaves;
		}

		public String exec(BaseFtpConnection conn,
				LinkedRemoteFileInterface file) {
			HashSet<RemoteSlave> deleteFromSlaves = new HashSet<RemoteSlave>(
					_deleteFromSlaves);
			for (Iterator<RemoteSlave> iter = deleteFromSlaves.iterator(); iter
					.hasNext();) {
				RemoteSlave rslave = iter.next();
				try {
					file.deleteFromSlave(rslave);
				} catch (IllegalArgumentException ex) {
					iter.remove();
				}
			}
			if (deleteFromSlaves.isEmpty())
				return file.getPath()
						+ " was not present on any specified slaves";
			String ret = file.getPath() + " deleted from ";
			for (RemoteSlave rslave : deleteFromSlaves) {
				ret = ret + rslave.getName() + ",";
			}
			return ret.substring(0, ret.length() - 1);
		}
	}

	private static interface Action {
		public String exec(BaseFtpConnection conn,
				LinkedRemoteFileInterface file);
	}

	private static class ActionDelete implements Action {
		private String doDELE(BaseFtpConnection conn,
				LinkedRemoteFileInterface file) {
			//FtpRequest request = conn.getRequest();
			// argument check
			//if (!request.hasArgument()) {
			//out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
			//return FtpReply.RESPONSE_501_SYNTAX_ERROR;
			//}
			// get filenames
			//String fileName = file.getName();
			//try {
			//requestedFile = getVirtualDirectory().lookupFile(fileName);
			//requestedFile = conn.getCurrentDirectory().lookupFile(fileName);
			//} catch (FileNotFoundException ex) {
			//return new FtpReply(550, "File not found: " + ex.getMessage());
			//}
			// check permission
			if (file.getUsername().equals(conn.getUserNull().getName())) {
				if (!conn.getGlobalContext().getConnectionManager()
				.getGlobalContext().getConfig().checkPathPermission("deleteown", conn.getUserNull(), file)) {
					//return FtpReply.RESPONSE_530_ACCESS_DENIED;
					return "Access denied for " + file.getPath();
				}
			} else if (!conn.getGlobalContext().getConnectionManager()
			.getGlobalContext().getConfig().checkPathPermission("delete", conn.getUserNull(), file)) {
				//return FtpReply.RESPONSE_530_ACCESS_DENIED;
				return "Access denied for " + file.getPath();
			}

			//FtpReply reply = (FtpReply)
			// FtpReply.RESPONSE_250_ACTION_OKAY.clone();
			String reply = "Deleted " + file.getPath();
			User uploader;

			try {
				uploader = conn.getGlobalContext().getConnectionManager()
						.getGlobalContext().getUserManager().getUserByName(
								file.getUsername());
				uploader.updateCredits((long) -(file.length() * uploader.getKeyedMap().getObjectFloat(UserManagement.RATIO)));
			} catch (UserFileException e) {
				reply += ("Error removing credits: " + e.getMessage());
			} catch (NoSuchUserException e) {
				reply += ("Error removing credits: " + e.getMessage());
			}

			//conn.getConnectionManager()
			//.dispatchFtpEvent(
			//new DirectoryFtpEvent(conn.getUserNull(), "DELE",
			//requestedFile));
			file.delete();

			return reply;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see net.sf.drftpd.master.command.plugins.find.FindAction#exec(net.sf.drftpd.master.BaseFtpConnection,
		 *             net.sf.drftpd.remotefile.LinkedRemoteFileInterface)
		 */
		public String exec(BaseFtpConnection conn,
				LinkedRemoteFileInterface file) {
			return doDELE(conn, file);
		}
	}

	private static class ActionPrint implements Action {
		/*
		 * (non-Javadoc)
		 * 
		 * @see net.sf.drftpd.master.command.plugins.find.FindAction#exec(net.sf.drftpd.remotefile.LinkedRemoteFile)
		 */
		public String exec(BaseFtpConnection conn,
				LinkedRemoteFileInterface file) {
			return file.getPath();
		}
	}

	private static class ActionPrintf implements Action {
		private String _format;

		private String parent;

		public ActionPrintf(String f) {
			_format = f;

			if (_format == null) {
				throw new NullPointerException();
			}
		}

		public String exec(BaseFtpConnection conn,
				LinkedRemoteFileInterface file) {
			try {
				parent = file.getParent();
			} catch (FileNotFoundException e) {
				parent = "/";
			}

			String mlst = MLSTSerialize.toMLST(file);
			String retval = null;

			try {
				retval = formatMLST(mlst, file);
			} catch (NumberFormatException e) {
				return mlst;
			}

			return retval;
		}

		private String formatMLST(String mlst, LinkedRemoteFileInterface file)
				throws NumberFormatException {

			HashMap<String, String> formats = new HashMap<String, String>();
			formats.put("#f", file.getName());
			formats.put("#s", Bytes.formatBytes(file.length()));
			formats.put("#u", file.getUsername());
			formats.put("#g", file.getGroupname());
			formats.put("#t", new Date(file.lastModified()).toString());
			formats.put("#x", file.getSlaves().toString());
			formats.put("#h", parent);

			String temp = _format;

			for (Map.Entry<String, String> entry : formats.entrySet()) {
				temp = temp.replaceAll(entry.getKey(), entry.getValue());
			}

			return temp;
		}

		private String getValue(String main, String sub) {
			int index = main.indexOf(sub);
			int endIndex = main.indexOf(";", index + 1);
			String retval = main.substring(index + sub.length(), endIndex);

			return retval;
		}
	}

	private static class ActionSendToSlaves implements Action {
		private HashSet<RemoteSlave> _destSlaves;

		private int _priority;

		private int _transferNum;

		public ActionSendToSlaves(int transferNum,
				HashSet<RemoteSlave> destSlaves, int priority) {
			_transferNum = transferNum;
			_destSlaves = destSlaves;
			_priority = priority;
		}

		public String exec(BaseFtpConnection conn,
				LinkedRemoteFileInterface file) {
			if (!file.isFile()) {
				return file.getName() + " is not a file, cannot send it";
			}
			conn.getGlobalContext().getJobManager().addJobToQueue(
					new Job(file, _destSlaves, _priority, _transferNum));
			return file.getName() + " added to jobqueue";
		}

	}

	private static class ActionWipe implements Action {
		/*
		 * (non-Javadoc)
		 * 
		 * @see net.sf.drftpd.master.command.plugins.find.FindAction#exec(net.sf.drftpd.master.BaseFtpConnection,
		 *             net.sf.drftpd.remotefile.LinkedRemoteFileInterface)
		 */
		public String exec(BaseFtpConnection conn,
				LinkedRemoteFileInterface file) {
			/*
			 * tommie's FindNuke mod removes nukelog entries on wipe.  We don't want that.
			if (file.isDirectory() && file.getName().startsWith("[NUKED]-")) {
				StringBuffer modPath = new StringBuffer(file.getPath());
				int i = modPath.indexOf("[NUKED]-");
				modPath.delete(i, i + 8);
				try {
					Nuke.getNukeLog().remove(modPath.toString());
				} catch (ObjectNotFoundException e) {
				}
			}
			*/
			file.delete();

			return "Wiped " + file.getPath();
		}
	}

		
	private static class ActionNuke implements Action {
		private String nuke_reason;
		private int nuke_x;
		private CNuke cn;
		
		public ActionNuke(CNuke _cn, String args) {
			StringTokenizer st = new StringTokenizer(args);
			if (st.countTokens() >= 2) {
				nuke_x = Integer.parseInt(st.nextToken());
				String _reason = "";
				while (st.hasMoreTokens()) {
					_reason = _reason + st.nextToken() + " ";
				}
				nuke_reason = _reason;
			} else {
				nuke_x = 1;
				nuke_reason = "ActionNuke";
			}
			cn = _cn;
		}
		
		public String exec(BaseFtpConnection conn,
				LinkedRemoteFileInterface file) {
			cn.doNuke(file.getPath(), nuke_reason, nuke_x, conn.getUserNull().getName());
			return "Nuked: " + file.getPath();
		}
	}
	
	private static interface Option {
		public boolean isTrueFor(LinkedRemoteFileInterface file);
	}

	private static class OptionGroup implements Option {
		private String groupname;

		public OptionGroup(String g) {
			groupname = g;
		}

		public boolean isTrueFor(LinkedRemoteFileInterface file) {
			return file.getGroupname().equals(groupname);
		}
	}

	private static class OptionIncomplete implements Option {
		private int _minPercent;

		public OptionIncomplete() {
		}

		public OptionIncomplete(int minPercent) {
			_minPercent = minPercent;
		}

		public boolean isTrueFor(LinkedRemoteFileInterface file) {
			if (DIZPlugin.zipFilesOnline(file) > 0) {
				try {
					DIZFile diz = new DIZFile(DIZPlugin.getZipFile(file));
					if (diz.getDiz() != null && diz.getTotal() > 0) {

						if (_minPercent == 0) { return !(DIZPlugin.zipFilesPresent(file) == diz.getTotal()); }

						int totalPercent = DIZPlugin.zipFilesPresent(file) * 100 / diz.getTotal();
						if (totalPercent == 100) {
							return false;
						} else {
							return totalPercent < _minPercent;
						}
					}
				} catch (FileNotFoundException e) {
				} catch (NoAvailableSlaveException e) {
				}
			} else {
				try {
					SFVFile sfv = file.lookupSFVFile();
					SFVStatus status = sfv.getStatus();

					if (_minPercent == 0) { return !status.isFinished(); }

					int totalPercent = status.getPresent() * 100 / sfv.size();
					if (totalPercent == 100) {
						return false;
					} else {
						return totalPercent < _minPercent;					
					}
				} catch (IOException e) {
				} catch (NoAvailableSlaveException e) {
				} catch (FileStillTransferringException e) {
				}
			}
			return false;
		}
	}
	
	private static class OptionEmpty implements Option {
		public OptionEmpty() {
		}

		public boolean isTrueFor(LinkedRemoteFileInterface file) {
			try {
				if (file.getFiles().size() == 0) return true;
			} catch (Exception e) {
				return false;
			}
			return false;
		}
	}
	
	private static class OptionMissing implements Option {
		private String arg;
		private Boolean dirs;
		
		public OptionMissing(String _arg) {
			arg = _arg;
			dirs = false;
		}

		public OptionMissing(String _arg, Boolean _dirs) {
			arg = _arg;
			dirs = _dirs;
		}

		public boolean isTrueFor(LinkedRemoteFileInterface file) {
			try {
				if (!file.isDirectory()) return false;
				for (LinkedRemoteFileInterface f : file.getFiles2()) {
					if (dirs) {
						// if in dir mode, skip files
						if (f.isFile()) { continue; }
					} else {
						// if not in dir mode, skip dirs
						if (f.isDirectory()) { continue; }
					}

					// check for matching files/dirs
					if (f.getName().matches(arg)) {
						return false;
					}
				}
			} catch (Exception e) {
				return false;
			}
			return true;
		}
	}

	private static class OptionMTime implements Option {
		boolean after;

		private Date date;

		public OptionMTime(int h) {
			after = true;

			if (h < 0) {
				after = false;
				h = Math.abs(h);
			}

			long t = (long) h * 24 * 60 * 60 * 1000;
			Date currentDate = new Date();
			date = new Date(currentDate.getTime() - t);
		}

		public boolean isTrueFor(LinkedRemoteFileInterface file) {
			Date fileDate = new Date(file.lastModified());

			return after ? fileDate.after(date) : fileDate.before(date);
		}
	}

	private static class OptionName implements Option {
		Pattern pattern;

		public OptionName(String str) {
		    str = str.replaceAll("\\[", "\\\\[");
		    str = str.replaceAll("\\]", "\\\\]");
		    str = str.replaceAll("\\(", "\\\\(");
		    str = str.replaceAll("\\)", "\\\\)");
		    str = str.replaceAll("[*]", ".*");
			pattern = Pattern.compile(str);
		}

		public boolean isTrueFor(LinkedRemoteFileInterface file) {
			Matcher m = pattern.matcher(file.getName());

			return m.matches();
		}
	}

	private static class OptionRName implements Option {
		String pattern;

		public OptionRName(String str) {
			pattern = str;
		}

		public boolean isTrueFor(LinkedRemoteFileInterface file) {
			return file.getName().matches(pattern);
		}
	}

	private static class OptionOffline implements Option {
		public boolean isTrueFor(LinkedRemoteFileInterface file) {
			try {
				return file.lookupSFVFile().getStatus().getOffline() != 0;
			} catch (Exception e) {
				return false;
			}
		}
	}

	private static class OptionSize implements Option {
		boolean bigger;

		long size;

		public OptionSize(long s, boolean b) {
			bigger = b;
			size = s;
		}

		public boolean isTrueFor(LinkedRemoteFileInterface file) {
			return bigger ? (file.length() >= size) : (file.length() <= size);
		}
	}

	private static class OptionSlave implements Option {
		RemoteSlave slave;

		public OptionSlave(RemoteSlave s) {
			slave = s;
		}

		public boolean isTrueFor(LinkedRemoteFileInterface file) {
			return file.hasSlave(slave);
		}
	}

	private static class OptionType implements Option {
		boolean dirs;

		boolean files;

		public OptionType(boolean f, boolean d) {
			files = f;
			dirs = d;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see net.sf.drftpd.master.command.plugins.find.FindOption#isTrueFor(net.sf.drftpd.remotefile.LinkedRemoteFileInterface)
		 */
		public boolean isTrueFor(LinkedRemoteFileInterface file) {
			if (files && dirs) {
				return true;
			} else if (files && !dirs) {
				return file.isFile();
			} else if (!files && dirs) {
				return file.isDirectory();
			}

			return true;
		}
	}

	private static class OptionUser implements Option {
		private String username;

		public OptionUser(String u) {
			username = u;
		}

		public boolean isTrueFor(LinkedRemoteFileInterface file) {
			return file.getUsername().equals(username);
		}
	}

	private static class PeekingIterator<E> implements Iterator {
		private Iterator<E> _i;

		private E _peek = null;

		public PeekingIterator(Iterator<E> i) {
			_i = i;
		}

		public boolean hasNext() {
			return _peek != null || _i.hasNext();
		}

		public E next() {
			if (_peek != null) {
				E peek = _peek;
				_peek = null;
				return peek;
			}
			return _i.next();
		}

		public E peek() {
			if (_peek != null)
				return _peek;
			return _peek = next();
		}

		public void remove() {
			_i.remove();
		}
	}

	private static void findFile(BaseFtpConnection conn, Reply response,
			LinkedRemoteFileInterface dir, Collection<Option> options,
			ArrayList actions, boolean files, boolean dirs) {
		//TODO optimize me, checking using regexp for all dirs is possibly slow
		if (!conn.getGlobalContext().getConnectionManager().getGlobalContext()
		.getConfig().checkPathPermission("privpath", conn.getUserNull(), dir, true)) {
			//Logger.getLogger(Find.class).debug("privpath: " + dir.getPath());
			return;
		}

		for (Iterator<LinkedRemoteFileInterface> iter = new ArrayList<LinkedRemoteFileInterface>(
				dir.getFiles2()).iterator(); iter.hasNext();) {
			LinkedRemoteFileInterface file = iter.next();

			if (file.isDirectory()) {
				findFile(conn, response, file, options, actions, files, dirs);
			}

			if ((dirs && file.isDirectory()) || (files && file.isFile())) {
				boolean checkIt = true;

				for (Iterator<Option> iterator = options.iterator(); iterator
						.hasNext();) {
					if (response.size() >= 100) {
						return;
					}

					Option findOption = iterator.next();

					if (!findOption.isTrueFor(file)) {
						checkIt = false;

						break;
					}
				}

				if (!checkIt) {
					continue;
				}

				for (Iterator i = actions.iterator(); i.hasNext();) {
					Action findAction = (Action) i.next();
					response.addComment(findAction.exec(conn, file));

					if (response.size() == 100 && actions.size() == 1
							&& actions.get(0) instanceof ActionPrint) {
						response.addComment("<snip>");
						return;
					}
				}
			}
		}
	}

	private static String getArgs(String str) {
		int start = str.indexOf("(");
		int end = str.indexOf(")");

		if ((start == -1) || (end == -1)) {
			return null;
		}

		if (start > end) {
			return null;
		}

		return str.substring(start + 1, end);
	}

	private GlobalContext _gctx;

	public Reply execute(BaseFtpConnection conn) throws ReplyException, ImproperUsageException {
		FtpRequest request = conn.getRequest();

		//		if (!request.hasArgument()) {
		//			return getShortHelpMsg();
		//		}

		Collection<String> c = request.hasArgument() ? Arrays.asList(request
				.getArgument().split(" ")) : Collections.EMPTY_LIST;

		//		if (args.length == 0) {
		//			return getShortHelpMsg();
		//		}

		//Collection<String> c = Arrays.asList(args);
		ArrayList<Option> options = new ArrayList<Option>();
		ArrayList<Action> actions = new ArrayList<Action>();
		boolean files = true;
		boolean dirs = true;
		boolean forceFilesOnly = false;
		boolean forceDirsOnly = false;

		for (PeekingIterator<String> iter = new PeekingIterator<String>(c
				.iterator()); iter.hasNext();) {
			String arg = iter.next();

			if (arg.toLowerCase().equals("-user")) {
				if (!iter.hasNext()) {
					throw new ImproperUsageException();
				}

				options.add(new OptionUser(iter.next()));
			} else if (arg.toLowerCase().equals("-group")) {
				if (!iter.hasNext()) {
					throw new ImproperUsageException();
				}

				options.add(new OptionGroup(iter.next()));
			} else if (arg.toLowerCase().equals("-name")) {
				if (!iter.hasNext()) {
					throw new ImproperUsageException();
				}

				options.add(new OptionName(iter.next()));
			} else if (arg.toLowerCase().equals("-rname")) {
				if (!iter.hasNext()) {
					throw new ImproperUsageException();
				}

				options.add(new OptionRName(iter.next()));
			} else if (arg.toLowerCase().equals("-slave")) {
				if (!iter.hasNext()) {
					throw new ImproperUsageException();
				}

				RemoteSlave rs = null;
				String slaveName = iter.next();

				try {
					rs = conn.getGlobalContext().getSlaveManager()
							.getRemoteSlave(slaveName);
				} catch (ObjectNotFoundException e) {
					return new Reply(500, "Slave " + slaveName
							+ " was not found.");
				}

				forceFilesOnly = true;
				options.add(new OptionSlave(rs));
			} else if (arg.toLowerCase().equals("-mtime")) {
				if (!iter.hasNext()) {
					throw new ImproperUsageException();
				}

				int offset = 0;

				try {
					offset = Integer.parseInt(iter.next());
				} catch (NumberFormatException e) {
					throw new ImproperUsageException();
				}

				options.add(new OptionMTime(offset));
			} else if (arg.toLowerCase().equals("-size")) {
				if (!iter.hasNext()) {
					throw new ImproperUsageException();
				}

				long size = 0;
				boolean bigger = true;
				String bytes = iter.next();

				if (bytes.startsWith("-")) {
					bigger = false;
					bytes = bytes.substring(1);
				}

				try {
					size = Bytes.parseBytes(bytes);
				} catch (NumberFormatException e) {
					throw new ImproperUsageException();
				}

				options.add(new OptionSize(size, bigger));
			} else if (arg.toLowerCase().equals("-type")) {
				if (!iter.hasNext()) {
					throw new ImproperUsageException();
				}

				String type = iter.next().toLowerCase();

				if (type.equals("f")) {
					dirs = false;
				} else if (type.equals("d")) {
					files = false;
				} else {
					return Reply.RESPONSE_501_SYNTAX_ERROR;
				}
			} else if (arg.toLowerCase().equals("-help")) {
				throw new ImproperUsageException();
			} else if (arg.toLowerCase().equals("-nouser")) {
				options.add(new OptionUser("nobody"));
			} else if (arg.toLowerCase().equals("-empty")) {
				forceDirsOnly = true;
				options.add(new OptionEmpty());
			} else if (arg.toLowerCase().equals("-missing")) {
				String cmd = "";
				if (!iter.peek().startsWith("-")) {
					cmd = iter.next();
				}
				options.add(new OptionMissing(cmd));
			} else if (arg.toLowerCase().equals("-missingdir")) {
				String cmd = "";
				if (!iter.peek().startsWith("-")) {
					cmd = iter.next();
				}
				options.add(new OptionMissing(cmd, true));
			} else if (arg.toLowerCase().equals("-incomplete")) {
				forceDirsOnly = true;
				String peek = null;
				try {
					peek = iter.peek();
				} catch (NoSuchElementException fail) {
				}
				if (peek != null && peek.charAt(0) != '-') {
					options.add(new OptionIncomplete(Integer.parseInt(iter
							.next())));
				} else {
					options.add(new OptionIncomplete());
				}
			} else if (arg.toLowerCase().equals("-offline")) {
				forceDirsOnly = true;
				options.add(new OptionOffline());
			} else if (arg.toLowerCase().equals("-nogroup")) {
				options.add(new OptionGroup("drftpd"));
			} else if (arg.toLowerCase().equals("-action")) {
				if (!iter.hasNext()) {
					throw new ImproperUsageException();
				}

				String action = iter.next();

				if (action.indexOf("(") != -1) {
					String cmd = action.substring(0, action.indexOf("("));
					boolean go = true;

					while (go) {
						if (action.endsWith(")")) {
							Action findAction = getActionWithArgs(cmd,
									getArgs(action));
							actions.add(findAction);
							go = false;

							continue;
						} else if (!iter.hasNext()) {
							return Reply.RESPONSE_501_SYNTAX_ERROR;
						} else {
							action += (" " + iter.next());
						}
					}
				} else if (action.equals("sendtoslaves")) {
					if (!conn.getUserNull().isAdmin()) {
						throw new ReplyPermissionDeniedException();
					}
					// -action sendtoslaves
					// <numtransfers[:slave[,slave,..][:priority]]>
					List<String> actionArgs = Arrays.asList(iter.next().split(
							":"));
					int numOfSlaves = Integer.parseInt(actionArgs.get(0));
					int priority = 0;
					if (actionArgs.size() >= 3) {
						priority = Integer.parseInt(actionArgs.get(2));
					}
					HashSet<String> destSlavenames = new HashSet<String>(Arrays
							.asList(actionArgs.get(1).split(",")));
					HashSet<RemoteSlave> destSlaves = new HashSet<RemoteSlave>();
					for (RemoteSlave rslave : getGlobalContext()
							.getSlaveManager().getSlaves()) {
						if (destSlavenames.contains(rslave.getName()))
							destSlaves.add(rslave);
					}
					actions.add(new ActionSendToSlaves(numOfSlaves, destSlaves,
							priority));
				} else if (action.equals("deletefromslaves")) {
					if (!conn.getUserNull().isAdmin()) {
						throw new ReplyPermissionDeniedException();
					}
					// -action deletefromslaves <slave[,slave[,...]]>
					HashSet<String> destSlaveNames = new HashSet<String>(Arrays
							.asList(iter.next().split(",")));
					HashSet<RemoteSlave> destSlaves = new HashSet<RemoteSlave>();
					for (RemoteSlave rslave : getGlobalContext()
							.getSlaveManager().getSlaves()) {
						if (destSlaveNames.contains(rslave.getName()))
							destSlaves.add(rslave);
					}
					actions.add(new ActionDeleteFromSlaves(destSlaves));
				} else if (action.equals("nuke")) {
					forceDirsOnly = true;
					String cmd = "";
					while (iter.hasNext()) {
						if (iter.peek().startsWith("-")) break;
						cmd = cmd + iter.next() + " ";
					}
					Action findAction = getActionWithArgs("nuke", cmd);
					if (findAction instanceof ActionNuke) {
						if (!conn.getUserNull().isNuker()) {
							return Reply.RESPONSE_530_ACCESS_DENIED;
						}
					}
					actions.add(findAction);
				} else {
					Action findAction = getAction(action.toLowerCase());

					if (findAction == null) {
						throw new ImproperUsageException();
					}

					if (findAction instanceof ActionWipe) {
						if (!conn.getUserNull().isAdmin()) {
							return Reply.RESPONSE_530_ACCESS_DENIED;
						}
					}

					actions.add(findAction);
				}
			} else {
				return Reply.RESPONSE_501_SYNTAX_ERROR;
			}
		}

		Reply response = (Reply) Reply.RESPONSE_200_COMMAND_OK.clone();

		//if (actions.size() == 0 || options.size() == 0)
		//return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		if (actions.size() == 0) {
			actions.add(new ActionPrint());
		}

		if (!dirs && !files) {
			dirs = true;
			files = true;
		}

		//FtpReply response = (FtpReply)
		// FtpReply.RESPONSE_200_COMMAND_OK.clone();
		if (forceFilesOnly && forceDirsOnly) {
			return new Reply(500,
					"Option conflict.  Possibly -slave and -incomplete.");
		} else if (forceFilesOnly) {
			dirs = false;
			response
					.addComment("Forcing a file only search because of -slave option.");
		} else if (forceDirsOnly) {
			files = false;
			response.addComment("Forcing a dir only search.");
		}

		options.add(new OptionType(files, dirs));
		findFile(conn, response, conn.getCurrentDirectory(), options, actions,
				files, dirs);

		return response;
	}

	private Action getAction(String actionName) {
		if (actionName.equals("print")) {
			return new ActionPrint();
		} else if (actionName.equals("wipe")) {
			return new ActionWipe();
		} else if (actionName.equals("delete")) {
			return new ActionDelete();
		} else {
			return null;
		}
	}

	private Action getActionWithArgs(String actionName, String args) {
		if (actionName.equals("printf")) {
			return new ActionPrintf(args);
		} else if (actionName.equals("nuke")) {
			return new ActionNuke(cn, args);
		} else {
			return null;			
		}
	}

 	public String[] getFeatReplies() {
		return null;
	}

	private GlobalContext getGlobalContext() {
		return _gctx;
	}

	public CommandHandler initialize(BaseFtpConnection conn,
			CommandManager initializer) {
		return this;
	}

	public void load(CommandManagerFactory initializer) {
		_gctx = initializer.getConnectionManager().getGlobalContext();
		
		Properties props = new Properties();
		FileInputStream fis = null;
		try {
			fis = new FileInputStream("conf/drmods.conf");
			props.load(fis);
			fis.close();
		} catch (IOException e) { return; }
		
		String _nukePrefix = props.getProperty("find.nuke.prefix", "[NUKED]-");
		String _nukeReasonPrefix = props.getProperty("find.nuke.reason.prefix", "REASON-");
		
		cn = new CNuke(_gctx, _nukePrefix, _nukeReasonPrefix);
	}

	public void unload() {
	}
	
	private CNuke cn;
}
