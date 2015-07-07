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

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;

import org.apache.log4j.Logger;
import org.drftpd.commands.CommandHandler;
import org.drftpd.commands.CommandHandlerFactory;
import org.drftpd.commands.Reply;
import org.drftpd.remotefile.LinkedRemoteFileInterface;


/**
 * @author mog
 * @version $Id: Search.java 1765 2007-08-04 04:14:28Z tdsoul $
 */
public class Search implements CommandHandler, CommandHandlerFactory {
    public void unload() {
    }

    public void load(CommandManagerFactory initializer) {
    }

    private static void findFile(BaseFtpConnection conn, Reply response,
        LinkedRemoteFileInterface dir, Collection searchstrings, boolean files,
        boolean dirs) {
        //TODO optimize me, checking using regexp for all dirs is possibly slow
        if (!conn.getGlobalContext().getConfig().checkPathPermission("privpath", conn.getUserNull(), dir, true)) {
            Logger.getLogger(Search.class).debug("privpath: " + dir.getPath());

            return;
        }

        for (Iterator iter = dir.getFiles().iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();

            if (file.isDirectory()) {
                findFile(conn, response, file, searchstrings, files, dirs);
            }

            boolean isFind = false;
            boolean allFind = true;

            if ((dirs && file.isDirectory()) || (files && file.isFile())) {
                for (Iterator iterator = searchstrings.iterator();
                        iterator.hasNext();) {
                    if (response.size() >= 100) {
                        return;
                    }

                    String searchstring = (String) iterator.next();

                    if (file.getName().toLowerCase().indexOf(searchstring) != -1) {
                        isFind = true;
                    } else {
                        allFind = false;
                    }
                }

                if (isFind && allFind) {
                    response.addComment(file.getPath());

                    if (response.size() >= 100) {
                        response.addComment("<snip>");

                        return;
                    }
                }
            }
        }
    }

    public Reply execute(BaseFtpConnection conn) {
        FtpRequest request = conn.getRequest();

        if (!request.hasArgument()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        String[] args = request.getArgument().toLowerCase().split(" ");

        if (args.length == 0) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        Collection searchstrings = Arrays.asList(args);
        Reply response = (Reply) Reply.RESPONSE_200_COMMAND_OK.clone();
        findFile(conn, response, conn.getCurrentDirectory(), searchstrings,
            "SITE DUPE".equals(request.getCommand()),
            "SITE SEARCH".equals(request.getCommand()));

        return response;
    }

    public CommandHandler initialize(BaseFtpConnection conn,
        CommandManager initializer) {
        return this;
    }

    public String[] getFeatReplies() {
        return null;
    }
}
