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
package net.sf.drftpd.master.command.plugins;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.ResourceBundle;

import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.event.DirectoryFtpEvent;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.GroupPosition;
import net.sf.drftpd.master.UploaderPosition;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.queues.NukeLog;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.Checksum;
import org.drftpd.SFVFile;
import org.drftpd.commands.CommandHandler;
import org.drftpd.commands.CommandHandlerFactory;
import org.drftpd.commands.Nuke;
import org.drftpd.commands.Reply;
import org.drftpd.commands.UnhandledCommandException;
import org.drftpd.id3.ID3Tag;
import org.drftpd.plugins.DIZFile;
import org.drftpd.plugins.DIZPlugin;
import org.drftpd.plugins.SiteBot;
import org.drftpd.remotefile.FileStillTransferringException;
import org.drftpd.remotefile.LinkedRemoteFile;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.remotefile.ListUtils;
import org.drftpd.remotefile.StaticRemoteFile;
import org.drftpd.remotefile.LinkedRemoteFile.NonExistingFile;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerEnvironment;
import org.tanesha.replacer.ReplacerFormat;
import org.tanesha.replacer.SimplePrintf;

import com.Ostermiller.util.StringTokenizer;


/**
 * @author mog
 * @version $Id: Dir.java 1847 2007-12-05 01:30:05Z tdsoul $
 */
public class Dir implements CommandHandler, CommandHandlerFactory, Cloneable {
    private final static SimpleDateFormat DATE_FMT = new SimpleDateFormat(
            "yyyyMMddHHmmss.SSS");
    private static final Logger logger = Logger.getLogger(Dir.class);
    protected LinkedRemoteFileInterface _renameFrom = null;

    public Dir() {
        super();
    }

    /**
     * <code>CDUP &lt;CRLF&gt;</code><br>
     *
     * This command is a special case of CWD, and is included to
     * simplify the implementation of programs for transferring
     * directory trees between operating systems having different
     * syntaxes for naming the parent directory.  The reply codes
     * shall be identical to the reply codes of CWD.
     */
    private Reply doCDUP(BaseFtpConnection conn) {
        // change directory
        try {
            conn.setCurrentDirectory(conn.getCurrentDirectory().getParentFile());
        } catch (FileNotFoundException ex) {
        }

        return new Reply(200,
            "Directory changed to " + conn.getCurrentDirectory().getPath());
    }

    /**
     * <code>CWD  &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
     *
     * This command allows the user to work with a different
     * directory for file storage or retrieval without
     * altering his login or accounting information.  Transfer
     * parameters are similarly unchanged.  The argument is a
     * pathname specifying a directory.
     */
    private Reply doCWD(BaseFtpConnection conn) {
        FtpRequest request = conn.getRequest();

        if (!request.hasArgument()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        LinkedRemoteFile newCurrentDirectory;

        try {
            newCurrentDirectory = conn.getCurrentDirectory().lookupFile(request.getArgument());
        } catch (FileNotFoundException ex) {
            return new Reply(550, ex.getMessage());
        }

        if (!conn.getGlobalContext().getConfig().checkPathPermission("privpath", conn.getUserNull(), newCurrentDirectory, true)) {
            return new Reply(550, request.getArgument() + ": Not found");

            // reply identical to FileNotFoundException.getMessage() above
        }

        if (!newCurrentDirectory.isDirectory()) {
            return new Reply(550, request.getArgument() +
                ": Not a directory");
        }

        conn.setCurrentDirectory(newCurrentDirectory);

        Reply response = new Reply(250,
                "Directory changed to " + newCurrentDirectory.getPath());
        conn.getGlobalContext().getConfig().directoryMessage(response,
            conn.getUserNull(), newCurrentDirectory);

        // show cwd_mp3.txt if this is an mp3 release
        ResourceBundle bundle = ResourceBundle.getBundle(Dir.class.getName());
        if (conn.getGlobalContext().getZsConfig().id3Enabled()) {
            try {
                ID3Tag id3tag = newCurrentDirectory.lookupFile(newCurrentDirectory.lookupMP3File())
                                                   .getID3v1Tag();
                String mp3text = bundle.getString("cwd.id3info.text");
                ReplacerEnvironment env = BaseFtpConnection.getReplacerEnvironment(null,
                        conn.getUserNull());
                ReplacerFormat id3format = null;

                try {
                    id3format = ReplacerFormat.createFormat(mp3text);
                } catch (FormatterException e1) {
                    logger.warn(e1);
                }

                env.add("artist", id3tag.getArtist().trim());
                env.add("album", id3tag.getAlbum().trim());
                env.add("genre", id3tag.getGenre());
                env.add("year", id3tag.getYear());

                try {
                    if (id3format == null) {
                        response.addComment("broken 1");
                    } else {
                        response.addComment(SimplePrintf.jprintf(id3format, env));
                    }
                } catch (FormatterException e) {
                    response.addComment("broken 2");
                    logger.warn("", e);
                }
            } catch (FileNotFoundException e) {
                // no mp3 found
                //logger.warn("",e);
            } catch (IOException e) {
                logger.warn("", e);
            } catch (NoAvailableSlaveException e) {
                logger.warn("", e);
            }
        }
        // diz files
		if (conn.getGlobalContext().getZsConfig().dizEnabled()) {
			if (DIZPlugin.zipFilesOnline(newCurrentDirectory) > 0) {
				try {
					DIZFile diz = new DIZFile(DIZPlugin
							.getZipFile(newCurrentDirectory));

					ReplacerFormat format = null;
					ReplacerEnvironment env = BaseFtpConnection
							.getReplacerEnvironment(null, conn.getUserNull());

					if (diz.getDiz() != null) {
						try {
							format = ReplacerFormat.createFormat(diz.getDiz());
							response.addComment(SimplePrintf.jprintf(format,
									env));
						} catch (FormatterException e) {
							logger.warn(e);
						}
					}
				} catch (FileNotFoundException e) {
					// do nothing, continue on
				} catch (NoAvailableSlaveException e) {
					// do nothing, continue on
				}
			}
		}

        // show race stats
        if (conn.getGlobalContext().getZsConfig().raceStatsEnabled()) {
            try {
                SFVFile sfvfile = newCurrentDirectory.lookupSFVFile();
                Collection racers = SiteBot.userSort(sfvfile.getFiles(),
                        "bytes", "high");
                Collection groups = SiteBot.topFileGroup(sfvfile.getFiles());

                String racerline = bundle.getString("cwd.racers.body");
                //logger.debug("racerline = " + racerline);
                String groupline = bundle.getString("cwd.groups.body");

                ReplacerEnvironment env = BaseFtpConnection.getReplacerEnvironment(null,
                        conn.getUserNull());

                //Start building race message
                String racetext = bundle.getString("cwd.racestats.header") + "\n";
                racetext += bundle.getString("cwd.racers.header") + "\n";

                ReplacerFormat raceformat = null;

                //Add racer stats
                int position = 1;

                for (Iterator iter = racers.iterator(); iter.hasNext();) {
                    UploaderPosition stat = (UploaderPosition) iter.next();
                    User raceuser;

                    try {
                        raceuser = conn.getGlobalContext().getUserManager()
                                       .getUserByName(stat.getUsername());
                    } catch (NoSuchUserException e2) {
                        continue;
                    } catch (UserFileException e2) {
                        logger.log(Level.FATAL, "Error reading userfile", e2);

                        continue;
                    }

                    ReplacerEnvironment raceenv = new ReplacerEnvironment();

                    raceenv.add("speed",
                        Bytes.formatBytes(stat.getXferspeed()) + "/s");
                    raceenv.add("user", stat.getUsername());
                    raceenv.add("group", raceuser.getGroup());
                    raceenv.add("files", "" + stat.getFiles());
                    raceenv.add("bytes", Bytes.formatBytes(stat.getBytes()));
                    raceenv.add("position", String.valueOf(position));
                    raceenv.add("percent",
                        Integer.toString(
                            (stat.getFiles() * 100) / sfvfile.size()) + "%");

                    try {
                        racetext += (SimplePrintf.jprintf(racerline,
                            raceenv) + "\n");
                        position++;
                    } catch (FormatterException e) {
                        logger.warn(e);
                    }
                }

                racetext += bundle.getString("cwd.racers.footer") + "\n";
                racetext += bundle.getString("cwd.groups.header") + "\n";

                //add groups stats
                position = 1;

                for (Iterator iter = groups.iterator(); iter.hasNext();) {
                    GroupPosition stat = (GroupPosition) iter.next();

                    ReplacerEnvironment raceenv = new ReplacerEnvironment();

                    raceenv.add("group", stat.getGroupname());
                    raceenv.add("position", String.valueOf(position));
                    raceenv.add("bytes", Bytes.formatBytes(stat.getBytes()));
                    raceenv.add("files", Integer.toString(stat.getFiles()));
                    raceenv.add("percent",
                        Integer.toString(
                            (stat.getFiles() * 100) / sfvfile.size()) + "%");
                    raceenv.add("speed",
                        Bytes.formatBytes(stat.getXferspeed()) + "/s");

                    try {
                        racetext += (SimplePrintf.jprintf(groupline,
                            raceenv) + "\n");
                        position++;
                    } catch (FormatterException e) {
                        logger.warn(e);
                    }
                }

                racetext += bundle.getString("cwd.groups.footer") + "\n";

                env.add("totalfiles", Integer.toString(sfvfile.size()));
                env.add("totalbytes", Bytes.formatBytes(sfvfile.getTotalBytes()));
                env.add("totalspeed",
                    Bytes.formatBytes(sfvfile.getXferspeed()) + "/s");
                env.add("totalpercent",
                    Integer.toString(
                        (sfvfile.getStatus().getPresent() * 100) / sfvfile.size()) +
                    "%");

                racetext += bundle.getString("cwd.totals.body") + "\n";
                racetext += bundle.getString("cwd.racestats.footer") + "\n";

                try {
                    raceformat = ReplacerFormat.createFormat(racetext);
                } catch (FormatterException e1) {
                    logger.warn(e1);
                }

                try {
                    if (raceformat == null) {
                        response.addComment("cwd.uploaders");
                    } else {
                        response.addComment(SimplePrintf.jprintf(raceformat, env));
                    }
                } catch (FormatterException e) {
                    response.addComment("cwd.uploaders");
                    logger.warn("", e);
                }
            } catch (RuntimeException ex) {
                logger.error("", ex);
            } catch (IOException e) {
                //Error fetching SFV, ignore
            } catch (NoAvailableSlaveException e) {
                //Error fetching SFV, ignore
            } catch (FileStillTransferringException e) {
            	response.addComment("SFVFile still being transferred, no info available");
			}
        }

        return response;
    }

    /**
     * <code>DELE &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
     *
     * This command causes the file specified in the pathname to be
     * deleted at the server site.
     */
    private Reply doDELE(BaseFtpConnection conn) {
        FtpRequest request = conn.getRequest();

        // argument check
        if (!request.hasArgument()) {
            //out.print(FtpResponse.RESPONSE_501_SYNTAX_ERROR);
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        // get filenames
        String fileName = request.getArgument();
        LinkedRemoteFile requestedFile;

        try {
            //requestedFile = getVirtualDirectory().lookupFile(fileName);
            requestedFile = conn.getCurrentDirectory().lookupFile(fileName, false);
        } catch (FileNotFoundException ex) {
            return new Reply(550, "File not found: " + ex.getMessage());
        }

        // check permission
        if (requestedFile.getUsername().equals(conn.getUserNull().getName())) {
            if (!conn.getGlobalContext().getConfig().checkPathPermission("deleteown", conn.getUserNull(), requestedFile)) {
                return Reply.RESPONSE_530_ACCESS_DENIED;
            }
        } else if (!conn.getGlobalContext().getConfig().checkPathPermission("delete", conn.getUserNull(), requestedFile)) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        if (requestedFile.isDirectory() && requestedFile.getMap().size() != 0) {
			return new Reply(550, requestedFile.getPath()
					+ ": Directory not empty");
		}

        Reply reply = (Reply) Reply.RESPONSE_250_ACTION_OKAY.clone();

        User uploader;

        try {
			uploader = conn.getGlobalContext().getUserManager().getUserByName(
                    requestedFile.getUsername());
            uploader.updateCredits((long) -(requestedFile.length() * conn
                    .getGlobalContext().getConfig().getCreditCheckRatio(
                            requestedFile, uploader)));
            if (!conn.getGlobalContext().getConfig().checkPathPermission(
                    "nostatsup", uploader, conn.getCurrentDirectory())) {
                uploader.updateUploadedBytes(-requestedFile.length());
            }
		} catch (UserFileException e) {
			reply.addComment("Error removing credits & stats: "
					+ e.getMessage());
		} catch (NoSuchUserException e) {
			reply.addComment("User " + requestedFile.getUsername()
					+ " does not exist, cannot remove credits on deletion");
		}

        conn.getGlobalContext().dispatchFtpEvent(new DirectoryFtpEvent(
                conn.getUserNull(), "DELE", requestedFile));
        requestedFile.delete();

        return reply;
    }

    /**
     * <code>MDTM &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
     *
     * Returns the date and time of when a file was modified.
     */
    private Reply doMDTM(BaseFtpConnection conn) {
        FtpRequest request = conn.getRequest();

        // argument check
        if (!request.hasArgument()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        // get filenames
        String fileName = request.getArgument();
        LinkedRemoteFile reqFile;

        try {
            reqFile = conn.getCurrentDirectory().lookupFile(fileName);
        } catch (FileNotFoundException ex) {
            return Reply.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN;
        }

        //fileName = user.getVirtualDirectory().getAbsoluteName(fileName);
        //String physicalName =
        //	user.getVirtualDirectory().getPhysicalName(fileName);
        //File reqFile = new File(physicalName);
        // now print date
        //if (reqFile.exists()) {
        return new Reply(213,
            DATE_FMT.format(new Date(reqFile.lastModified())));

        //out.print(ftpStatus.getResponse(213, request, user, args));
        //} else {
        //	out.write(ftpStatus.getResponse(550, request, user, null));
        //}
    }

    /**
     * <code>MKD  &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
     *
     * This command causes the directory specified in the pathname
     * to be created as a directory (if the pathname is absolute)
     * or as a subdirectory of the current working directory (if
     * the pathname is relative).
     *
     *
     *                MKD
     *                   257
     *                   500, 501, 502, 421, 530, 550
     */
    private Reply doMKD(BaseFtpConnection conn) {
        FtpRequest request = conn.getRequest();

        // argument check
        if (!request.hasArgument()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        if (!conn.getGlobalContext().getSlaveManager().hasAvailableSlaves()) {
            return Reply.RESPONSE_450_SLAVE_UNAVAILABLE;
        }

        // arg cleanup extra /'s, take any / off the end
        String arg = request.getArgument();
        arg = arg.replaceAll("/{2,}", "/");
        if (arg.endsWith("/")) {
        	arg = arg.substring(0, arg.length() - 1);
        }

        // are we making one for this directory?
        String currentPath = conn.getCurrentDirectory().getPath();
        String toPath = null;
        if (!arg.startsWith("/")) {
        	if (currentPath.length() == 1)	/*		// isn't /		*/
        		toPath = currentPath + arg;
        	else
        		toPath = currentPath + "/" + arg;
        } else {
        	toPath = arg;
        }

        // get absolute path
        toPath = conn.getGlobalContext().getRoot().lookupPath(toPath);

        // lookup
        LinkedRemoteFile.NonExistingFile ret = conn.getCurrentDirectory()
                                                   .lookupNonExistingFile(arg);
        LinkedRemoteFile dir = ret.getFile();

        // does it already exist?
        if (ret.exists()) {
        	return new Reply(550,
        			"Requested action not taken. " + arg +
        			" already exists");
        }

        // is this a legal name?
        String createdDirName = conn.getGlobalContext().getConfig().getDirName(ret.getPath());
        if (!ListUtils.isLegalFileName(createdDirName)) {
        	return Reply.RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN;
        }
        if (!conn.getGlobalContext().getConfig().checkPathPermission("makedir", conn.getUserNull(), dir)) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        // check nukelog
        NukeLog _nukelog = Nuke.getNukeLog();
		if (_nukelog != null && _nukelog.find_fullpath(toPath)) {
			try {
				String reason = _nukelog.get(toPath).getReason();
				return new Reply(530,
						"Access denied - Directory already nuked for '"
								+ reason + "'");
			} catch (ObjectNotFoundException e) {
				return new Reply(530,
						"Access denied - Directory already nuked, reason unavailable - "
								+ e.getMessage());
			}
		}

		if (conn.getGlobalContext().getZsConfig().checkSfvDenyMKD(
				ret.getFile(), ret.getPath())) {
			return new Reply(530,
					"Access denied - Directory '" + ret.getPath() + "' not permitted when .sfv exists in '" + ret.getFile().getPath() + "' (ZipScript+)");
		}

	// Consult command filter
        String DeniedReason = conn.getGlobalContext().getConfig().checkRegexPermission("MKD", conn.getUserNull(), toPath, "directory");
        if (DeniedReason != null) {
        	return new Reply(530, "Access denied (" + DeniedReason + ")");
        }

        // ok, create it
        try {
            LinkedRemoteFile createdDir = dir.createDirectory(conn.getUserNull()
                                                                  .getName(),
                    conn.getUserNull().getGroup(), createdDirName);

            conn.getGlobalContext().dispatchFtpEvent(new DirectoryFtpEvent(
                    conn.getUserNull(), "MKD", createdDir));

            return new Reply(257, "\"" + createdDir.getPath() +
                "\" created.");
        } catch (FileExistsException ex) {
            return new Reply(550,
                "directory " + createdDirName + " already exists");
        }
    }

    /**
     * <code>PWD  &lt;CRLF&gt;</code><br>
     *
     * This command causes the name of the current working
     * directory to be returned in the reply.
     */
    private Reply doPWD(BaseFtpConnection conn) {
        return new Reply(257,
            "\"" + conn.getCurrentDirectory().getPath() +
            "\" is current directory");
    }

    /**
     * <code>RMD  &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
     *
     * This command causes the directory specified in the pathname
     * to be removed as a directory (if the pathname is absolute)
     * or as a subdirectory of the current working directory (if
     * the pathname is relative).
     */
    private Reply doRMD(BaseFtpConnection conn) {
        FtpRequest request = conn.getRequest();

        // argument check
        if (!request.hasArgument()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        // get file names
        String fileName = request.getArgument();
        LinkedRemoteFile requestedFile;

        try {
            requestedFile = conn.getCurrentDirectory().lookupFile(fileName);
        } catch (FileNotFoundException e) {
            return new Reply(550, fileName + ": " + e.getMessage());
        }

        if (requestedFile.getUsername().equals(conn.getUserNull().getName())) {
            if (!conn.getGlobalContext().getConfig().checkPathPermission("deleteown", conn.getUserNull(), requestedFile)) {
                return Reply.RESPONSE_530_ACCESS_DENIED;
            }
        } else if (!conn.getGlobalContext().getConfig().checkPathPermission("delete", conn.getUserNull(), requestedFile)) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        if (!requestedFile.isDirectory()) {
            return new Reply(550, fileName + ": Not a directory");
        }

        if (requestedFile.dirSize() != 0) {
            return new Reply(550, fileName + ": Directory not empty");
        }

        // now delete
        //if (conn.getConfig().checkDirLog(conn.getUserNull(), requestedFile)) {
        conn.getGlobalContext().dispatchFtpEvent(new DirectoryFtpEvent(
                conn.getUserNull(), "RMD", requestedFile));

        //}
        requestedFile.delete();

        return Reply.RESPONSE_250_ACTION_OKAY;
    }

    /**
     * <code>RNFR &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
     *
     * This command specifies the old pathname of the file which is
     * to be renamed.  This command must be immediately followed by
     * a "rename to" command specifying the new file pathname.
     *
     *                RNFR
                              450, 550
                              500, 501, 502, 421, 530
                              350

     */
    private Reply doRNFR(BaseFtpConnection conn) {
        FtpRequest request = conn.getRequest();

        // argument check
        if (!request.hasArgument()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        // set state variable
        // get filenames
        //String fileName = request.getArgument();
        //fileName = user.getVirtualDirectory().getAbsoluteName(fileName);
        //mstRenFr = user.getVirtualDirectory().getPhysicalName(fileName);
        try {
            _renameFrom = conn.getCurrentDirectory().lookupFile(request.getArgument());
        } catch (FileNotFoundException e) {
            return Reply.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN;
        }

        //check permission
        if (_renameFrom.getUsername().equals(conn.getUserNull().getName())) {
            if (!conn.getGlobalContext().getConfig().checkPathPermission("renameown", conn.getUserNull(), _renameFrom)) {
                return Reply.RESPONSE_530_ACCESS_DENIED;
            }
        } else if (!conn.getGlobalContext().getConfig().checkPathPermission("rename", conn.getUserNull(), _renameFrom)) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        String DeniedReason = conn.getGlobalContext().getConfig().checkRegexPermission("RNFR", conn.getUserNull(),
        		_renameFrom.getPath(), _renameFrom.isFile() ? "file" : "directory");
        if (DeniedReason != null) {
        	return new Reply(530, "Access denied (" + DeniedReason + ")");
        }

        return new Reply(350, "File exists, ready for destination name");
    }

    /**
     * <code>RNTO &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
     *
     * This command specifies the new pathname of the file
     * specified in the immediately preceding "rename from"
     * command.  Together the two commands cause a file to be
     * renamed.
     */
    private Reply doRNTO(BaseFtpConnection conn) {
        FtpRequest request = conn.getRequest();

        // argument check
        if (!request.hasArgument()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        // set state variables
        if (_renameFrom == null) {
            return Reply.RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS;
        }

        NonExistingFile ret = conn.getCurrentDirectory().lookupNonExistingFile(request.getArgument());
        LinkedRemoteFileInterface toDir = ret.getFile();
        String name = ret.getPath();
        LinkedRemoteFileInterface fromFile = _renameFrom;

        if (name == null) {
            name = fromFile.getName();
        }

        // check permission
        if (_renameFrom.getUsername().equals(conn.getUserNull().getName())) {
            if (!conn.getGlobalContext().getConfig().checkPathPermission("renameown", conn.getUserNull(), toDir)) {
                return Reply.RESPONSE_530_ACCESS_DENIED;
            }
        } else if (!conn.getGlobalContext().getConfig().checkPathPermission("rename", conn.getUserNull(), toDir)) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        String DeniedReason = conn.getGlobalContext().getConfig().checkRegexPermission("RNTO", conn.getUserNull(),
        		toDir.getPath(), toDir.isFile() ? "file" : "directory");
        if (DeniedReason != null) {
        	return new Reply(530, "Access denied (" + DeniedReason + ")");
        }

        try {
            fromFile.renameTo(toDir.getPath(), name);
        } catch (FileNotFoundException e) {
            logger.info("FileNotFoundException on renameTo()", e);

            return new Reply(500, "FileNotFound - " + e.getMessage());
        } catch (IOException e) {
            logger.info("IOException on renameTo()", e);

            return new Reply(500, "IOException - " + e.getMessage());
        }

        //out.write(FtpResponse.RESPONSE_250_ACTION_OKAY.toString());
        return new Reply(250, request.getCommand() +
            " command successful.");
    }

    private Reply doSITE_CHOWN(BaseFtpConnection conn)
        throws UnhandledCommandException {
        FtpRequest req = conn.getRequest();
        StringTokenizer st = new StringTokenizer(conn.getRequest().getArgument());
        String owner = st.nextToken();
        String group = null;
        int pos = owner.indexOf('.');

        if (pos != -1) {
            group = owner.substring(pos + 1);
            owner = owner.substring(0, pos);
        } else if ("SITE CHGRP".equals(req.getCommand())) {
            group = owner;
            owner = null;
        } else if (!"SITE CHOWN".equals(req.getCommand())) {
            throw UnhandledCommandException.create(Dir.class, req);
        }

        Reply reply = new Reply(200);

        while (st.hasMoreTokens()) {
            try {
                LinkedRemoteFileInterface file = conn.getCurrentDirectory()
                                                     .lookupFile(st.nextToken());

                if (owner != null) {
                    file.setOwner(owner);
                }

                if (group != null) {
                    file.setGroup(group);
                }
            } catch (FileNotFoundException e) {
                reply.addComment(e.getMessage());
            }
        }

        return Reply.RESPONSE_200_COMMAND_OK;
    }

    private Reply doSITE_LINK(BaseFtpConnection conn) {
        if (!conn.getRequest().hasArgument()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        StringTokenizer st = new StringTokenizer(conn.getRequest().getArgument(),
                " ");

        if (st.countTokens() != 2) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        String targetName = st.nextToken();
        String linkName = st.nextToken();
        LinkedRemoteFile target;

        try {
            target = conn.getCurrentDirectory().lookupFile(targetName);
        } catch (FileNotFoundException e) {
            return Reply.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN;
        }

        if (!target.isDirectory()) {
            return new Reply(501, "Only link to directories for now.");
        }

        StaticRemoteFile link = new StaticRemoteFile(linkName, null, targetName);
        conn.getCurrentDirectory().addFile(link);

        return Reply.RESPONSE_200_COMMAND_OK;
    }

    /**
     * USAGE: site wipe [-r] <file/directory>
     *
     *         This is similar to the UNIX rm command.
     *         In glftpd, if you just delete a file, the uploader loses credits and
     *         upload stats for it.  There are many people who didn't like that and
     *         were unable/too lazy to write a shell script to do it for them, so I
     *         wrote this command to get them off my back.
     *
     *         If the argument is a file, it will simply be deleted. If it's a
     *         directory, it and the files it contains will be deleted.  If the
     *         directory contains other directories, the deletion will be aborted.
     *
     *         To remove a directory containing subdirectories, you need to use
     *         "site wipe -r dirname". BE CAREFUL WHO YOU GIVE ACCESS TO THIS COMMAND.
     *         Glftpd will check if the parent directory of the file/directory you're
     *         trying to delete is writable by its owner. If not, wipe will not
     *         execute, so to protect directories from being wiped, make their parent
     *         555.
     *
     *         Also, wipe will only work where you have the right to delete (in
     *         glftpd.conf). Delete right and parent directory's mode of 755/777/etc
     *         will cause glftpd to SWITCH TO ROOT UID and wipe the file/directory.
     *         "site wipe -r /" will not work, but "site wipe -r /incoming" WILL, SO
     *         BE CAREFUL.
     *
     *         This command will remove the deleted files/directories from the dirlog
     *         and dupefile databases.
     *
     *         To give access to this command, add "-wipe -user flag =group" to the
     *         config file (similar to other site commands).
     *
     * @param request
     * @param out
     */
    private Reply doSITE_WIPE(BaseFtpConnection conn) {
        if (!conn.getRequest().hasArgument()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        String arg = conn.getRequest().getArgument();

        boolean recursive;

        if (arg.startsWith("-r ")) {
            arg = arg.substring(3);
            recursive = true;
        } else {
            recursive = false;
        }

        LinkedRemoteFile wipeFile;

        try {
            wipeFile = conn.getCurrentDirectory().lookupFile(arg);
        } catch (FileNotFoundException e) {
            return new Reply(200,
                "Can't wipe: " + arg +
                " does not exist or it's not a plain file/directory");
        }

        if (wipeFile.isDirectory() && (wipeFile.dirSize() != 0) && !recursive) {
            return new Reply(200, "Can't wipe, directory not empty");
        }

        String DeniedReason = conn.getGlobalContext().getConfig().checkRegexPermission("WIPE",
        		conn.getUserNull(), wipeFile.getPath(), (wipeFile.isDirectory() ? "directory" : "file"));
        if (DeniedReason != null) {
        	return new Reply(530, "Access denied (" + DeniedReason + ")");
        }

        //if (conn.getConfig().checkDirLog(conn.getUserNull(), wipeFile)) {
        conn.getGlobalContext().dispatchFtpEvent(new DirectoryFtpEvent(
                conn.getUserNull(), "WIPE", wipeFile));

        //}
        wipeFile.delete();

        return Reply.RESPONSE_200_COMMAND_OK;
    }

    /**
     * <code>SIZE &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
     *
     * Returns the size of the file in bytes.
     */
    private Reply doSIZE(BaseFtpConnection conn) {
        FtpRequest request = conn.getRequest();

        if (!request.hasArgument()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        LinkedRemoteFile file;

        try {
            file = conn.getCurrentDirectory().lookupFile(request.getArgument());
        } catch (FileNotFoundException ex) {
            return Reply.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN;
        }

        return new Reply(213, Long.toString(file.length()));
    }

    /**
     * http://www.southrivertech.com/support/titanftp/webhelp/xcrc.htm
     *
     * Originally implemented by CuteFTP Pro and Globalscape FTP Server
     */
    private Reply doXCRC(BaseFtpConnection conn) {
        FtpRequest request = conn.getRequest();

        if (!request.hasArgument()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        StringTokenizer st = new StringTokenizer(request.getArgument());
        LinkedRemoteFile myFile;

        try {
            myFile = conn.getCurrentDirectory().lookupFile(st.nextToken());
        } catch (FileNotFoundException e) {
            return Reply.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN;
        }

        if (st.hasMoreTokens()) {
            if (!st.nextToken().equals("0") ||
                    !st.nextToken().equals(Long.toString(myFile.length()))) {
                return Reply.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM;
            }
        }

        try {
            return new Reply(250,
                "XCRC Successful. " +
                Checksum.formatChecksum(myFile.getCheckSum()));
        } catch (NoAvailableSlaveException e1) {
            logger.warn("", e1);

            return new Reply(550,
                "NoAvailableSlaveException: " + e1.getMessage());
        }
    }

    public Reply execute(BaseFtpConnection conn)
        throws UnhandledCommandException {
        FtpRequest request = conn.getRequest();
        String cmd = request.getCommand();

        if ("CDUP".equals(cmd)) {
            return doCDUP(conn);
        }

        if ("CWD".equals(cmd)) {
            return doCWD(conn);
        }

        if ("MKD".equals(cmd)) {
            return doMKD(conn);
        }

        if ("PWD".equals(cmd)) {
            return doPWD(conn);
        }

        if ("RMD".equals(cmd)) {
            return doRMD(conn);
        }

        if ("RNFR".equals(cmd)) {
            return doRNFR(conn);
        }

        if ("RNTO".equals(cmd)) {
            return doRNTO(conn);
        }

        if ("SITE LINK".equals(cmd)) {
            return doSITE_LINK(conn);
        }

        if ("SITE WIPE".equals(cmd)) {
            return doSITE_WIPE(conn);
        }

        if ("XCRC".equals(cmd)) {
            return doXCRC(conn);
        }

        if ("MDTM".equals(cmd)) {
            return doMDTM(conn);
        }

        if ("SIZE".equals(cmd)) {
            return doSIZE(conn);
        }

        if ("DELE".equals(cmd)) {
            return doDELE(conn);
        }

        if ("SITE CHOWN".equals(cmd) || "SITE CHGRP".equals(cmd)) {
            return doSITE_CHOWN(conn);
        }

        throw UnhandledCommandException.create(Dir.class, request);
    }

//    public String getHelp(String cmd) {
//        ResourceBundle bundle = ResourceBundle.getBundle(Dir.class.getName());
//        if ("".equals(cmd))
//            return bundle.getString("help.general")+"\n";
//        else if("link".equals(cmd) || "link".equals(cmd) || "wipe".equals(cmd))
//            return bundle.getString("help."+cmd)+"\n";
//        else
//            return "";
//    }

    public String[] getFeatReplies() {
        return null;
    }

    public CommandHandler initialize(BaseFtpConnection conn,
        CommandManager initializer) {
        try {
            return (Dir) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public void load(CommandManagerFactory initializer) {
    }

    public void unload() {
    }
}
