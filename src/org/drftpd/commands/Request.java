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
package org.drftpd.commands;

import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.event.DirectoryFtpEvent;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Logger;

import org.drftpd.dynamicdata.Key;
import org.drftpd.remotefile.LinkedRemoteFile;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.usermanager.NoSuchUserException;
import org.tanesha.replacer.ReplacerEnvironment;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.Iterator;
import java.util.Properties;
import java.util.StringTokenizer;


/**
 * @author mog
 * @version $Id: Request.java 1765 2007-08-04 04:14:28Z tdsoul $
 */
public class Request implements CommandHandler, CommandHandlerFactory {
	
    public static final Key REQUESTSFILLED = new Key(Request.class, "requestsFilled", Integer.class);
    public static final Key REQUESTS = new Key(Request.class, "requests", Integer.class);
    public static final Key WEEKREQS = new Key(Request.class, "weekreq",Integer.class);

    public static final String FILLEDPREFIX = "FILLED-for.";
    public static final String REQPREFIX = "REQUEST-by.";

    private static final Logger logger = Logger.getLogger(Request.class);

    private String _requestPath;
    private int _maxWeekReqs = 0;
    private String _weekExempt;

    private Reply doSITE_REQFILLED(BaseFtpConnection conn) {
        if (!conn.getRequest().hasArgument()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        LinkedRemoteFileInterface currdir = conn.getCurrentDirectory();
        String reqname = conn.getRequest().getArgument().trim();

        for (Iterator iter = currdir.getFiles().iterator(); iter.hasNext();) {
            LinkedRemoteFile file = (LinkedRemoteFile) iter.next();

            if (!file.getName().startsWith(REQPREFIX)) {
                continue;
            }

            String username = file.getName().substring(REQPREFIX.length());
            String myreqname = username.substring(username.indexOf('-') + 1);
            username = username.substring(0, username.indexOf('-'));

            if (myreqname.equals(reqname)) {
                String filledname = FILLEDPREFIX + username + "-" + myreqname;

                try {
                    file.renameTo(file.getParentFile().getPath(),
                            filledname);
                } catch (IOException e) {
                    logger.warn("", e);

                    return new Reply(200, e.getMessage());
                }

                //if (conn.getConfig().checkDirLog(conn.getUserNull(), file)) {
                conn.getGlobalContext().dispatchFtpEvent(new DirectoryFtpEvent(
                        conn.getUserNull(), "REQFILLED", file));

                //}
                try {
                    conn.getUser().getKeyedMap().incrementObjectLong(REQUESTSFILLED);

                    //conn.getUser().addRequestsFilled();
                } catch (NoSuchUserException e) {
                    e.printStackTrace();
                }

                return new Reply(200,
                    "OK, renamed " + myreqname + " to " + filledname);
            }
        }

        return new Reply(200, "Couldn't find a request named " + reqname);
    }

    private Reply doSITE_REQUEST(BaseFtpConnection conn) {
        if (!conn.getGlobalContext().getConfig().checkPathPermission("request",
                    conn.getUserNull(), conn.getCurrentDirectory())) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        if (!conn.getRequest().hasArgument()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        if (_maxWeekReqs != 0) {
	        boolean exempt = false;
	        StringTokenizer st = new StringTokenizer(_weekExempt);
	        while (st.hasMoreTokens()) {
	            if (conn.getUserNull().isMemberOf(st.nextToken())) {
	                exempt = true;
	                break;
	            }
	        }
	
	        if (!exempt) {
	            int reqsMade = conn.getUserNull().getKeyedMap().getObjectInt(org.drftpd.commands.Request.WEEKREQS);
	            if (reqsMade >= _maxWeekReqs) {
	                return new Reply(550, "Access Denied. Maximum weekly request limit reached.");
	            }
	        }  
        }

        String createdDirName = REQPREFIX + conn.getUserNull().getName() +
            "-" + conn.getRequest().getArgument().trim();

        try {
            LinkedRemoteFile createdDir = conn.getCurrentDirectory()
                                              .createDirectory(conn.getUserNull()
                                                                   .getName(),
                    conn.getUserNull().getGroup(), createdDirName);

            //if (conn.getConfig().checkDirLog(conn.getUserNull(), createdDir)) {
            conn.getGlobalContext().dispatchFtpEvent(new DirectoryFtpEvent(
                    conn.getUserNull(), "REQUEST", createdDir));

            conn.getUserNull().getKeyedMap().incrementObjectLong(REQUESTS);
            conn.getUserNull().getKeyedMap().incrementObjectInt(WEEKREQS, 1);

            //conn.getUser().addRequests();
            return new Reply(257, "\"" + createdDir.getPath() +
                "\" created.");
        } catch (FileExistsException ex) {
            return new Reply(550,
                "directory " + createdDirName + " already exists");
        }
    }

    private Reply doSITE_REQUESTS(BaseFtpConnection conn) {
        LinkedRemoteFileInterface requestDir;

        // use path permissions in order to keep support for multiple site request dirs.
        // site operator must configure the new 'requests' permission accordingly
        try {
            if (!conn.getGlobalContext().getConfig().checkPathPermission("requests",
                    conn.getUserNull(), conn.getCurrentDirectory())) {
                // if no permission to do requests in current dir, try the configured !request dir instead
                if (!conn.getGlobalContext().getConfig().checkPathPermission("requests",
                        conn.getUserNull(), conn.getGlobalContext().getRoot().lookupFile(_requestPath))) {
                    return Reply.RESPONSE_530_ACCESS_DENIED;
                } else {
                    // access granted here for !request dir
                    requestDir = conn.getGlobalContext().getRoot().lookupFile(_requestPath);
                }
            } else {
                // access granted for current dir
                requestDir = conn.getCurrentDirectory();
            }
        } catch (FileNotFoundException e) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        ReplacerEnvironment env = BaseFtpConnection.getReplacerEnvironment(null, conn.getUserNull());
        Reply response = new Reply(200, "Command Successful.");

        response.addComment(ReplacerUtils.jprintf("requests.header", env, Request.class));
        int i=1;
        for (Iterator iter = requestDir.getDirectories().iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();
            if (file.isDirectory()) {
                //  if (file.getName().startsWith("REQUEST")) {
                StringTokenizer st = new StringTokenizer(file.getName(), "-");
                if (st.nextToken().equals("REQUEST")) {
                    String byuser = st.nextToken();
                    String request = st.nextToken();
                    while (st.hasMoreTokens()) {
                        request = request+"-"+st.nextToken();
                    }
                    byuser = byuser.replace('.',' ');
                    String num = Integer.toString(i);
                    env.add("reqnum",num);
                    env.add("requser",byuser.replaceAll("by |for.*",""));
                    env.add("reqrequest",request);
                    i=i+1;
                    response.addComment(ReplacerUtils.jprintf("requests.list", env, Request.class));    
                }
            }
        }
        response.addComment(ReplacerUtils.jprintf("requests.footer", env, Request.class));
        return response;
    }

    public Reply execute(BaseFtpConnection conn)
        throws UnhandledCommandException {
        String cmd = conn.getRequest().getCommand();

        if ("SITE REQUEST".equals(cmd)) {
            return doSITE_REQUEST(conn);
        }

        if ("SITE REQFILLED".equals(cmd)) {
            return doSITE_REQFILLED(conn);
        }

        if ("SITE REQUESTS".equals(cmd)) {
            return doSITE_REQUESTS(conn);
        }

        throw UnhandledCommandException.create(Request.class, conn.getRequest());
    }

    public String[] getFeatReplies() {
        return null;
    }

    public CommandHandler initialize(BaseFtpConnection conn,
        CommandManager initializer) {
        return this;
    }

    public void load(CommandManagerFactory initializer) {
        Properties cfg = new Properties();
        FileInputStream file = null;
        try {
            file = new FileInputStream("conf/drmods.conf");
            cfg.load(file);
            _requestPath = cfg.getProperty("request.dirpath", "/requests/");
            String maxWeekReqs = cfg.getProperty("request.weekmax", "0");
            _weekExempt = cfg.getProperty("request.weekexempt", "siteop");
            file.close();
            _maxWeekReqs = Integer.parseInt(maxWeekReqs);
        } catch (Exception e) {
            logger.error("Error reading conf/drmods.conf",e);
            throw new RuntimeException(e.getMessage());
        } finally {
            try {
                file.close();
            } catch (Exception e) {
            }
        }
    }

    public void unload() {
    }
}
