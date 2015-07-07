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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.StringTokenizer;

import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.command.plugins.Textoutput;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.util.UserComparator;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.permissions.Permission;
import org.drftpd.plugins.Trial;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.usermanager.UserManager;
import org.tanesha.replacer.ReplacerEnvironment;


/**
 * @version $Id: TransferStatistics.java 1765 2007-08-04 04:14:28Z tdsoul $
 */
public class TransferStatistics implements CommandHandler, CommandHandlerFactory  {
    private static final Logger logger = Logger.getLogger(TransferStatistics.class);

    public static long getStats(String command, User user) {
        // AL MONTH WK DAY
        String period = command.substring(0, command.length() - 2).toUpperCase();

        // UP DN
        String updn = command.substring(command.length() - 2).toUpperCase();

        if (updn.equals("UP")) {
            if (period.equals("AL")) {
                return user.getUploadedBytes();
            }

            if (period.equals("DAY")) {
                return user.getUploadedBytesDay();
            }

            if (period.equals("WK")) {
                return user.getUploadedBytesWeek();
            }

            if (period.equals("MONTH")) {
                return user.getUploadedBytesMonth();
            }
        } else if (updn.equals("DN")) {
            if (period.equals("AL")) {
                return user.getDownloadedBytes();
            }

            if (period.equals("DAY")) {
                return user.getDownloadedBytesDay();
            }

            if (period.equals("WK")) {
                return user.getDownloadedBytesWeek();
            }

            if (period.equals("MONTH")) {
                return user.getDownloadedBytesMonth();
            }
        }

        throw new RuntimeException(UnhandledCommandException.create(
                TransferStatistics.class, command));
    }

    public static long getFiles(String command, User user) {
        // AL MONTH WK DAY
        String period = command.substring(0, command.length() - 2);

        // UP DN
        String updn = command.substring(command.length() - 2);

        if (updn.equals("UP")) {
            if (period.equals("AL")) {
                return user.getUploadedFiles();
            }

            if (period.equals("DAY")) {
                return user.getUploadedFilesDay();
            }

            if (period.equals("WK")) {
                return user.getUploadedFilesWeek();
            }

            if (period.equals("MONTH")) {
                return user.getUploadedFilesMonth();
            }
        } else if (updn.equals("DN")) {
            if (period.equals("AL")) {
                return user.getDownloadedFiles();
            }

            if (period.equals("DAY")) {
                return user.getDownloadedFilesDay();
            }

            if (period.equals("WK")) {
                return user.getDownloadedFilesWeek();
            }

            if (period.equals("MONTH")) {
                return user.getDownloadedFilesMonth();
            }
        }

        throw new RuntimeException(UnhandledCommandException.create(
                TransferStatistics.class, command));
    }

    public static int getStatsPlace(String command, User user,
        UserManager userman) {
        // AL MONTH WK DAY
        int place = 1;
        long bytes = getStats(command, user);
        Collection users;

        try {
            users = userman.getAllUsers();
        } catch (UserFileException e) {
            logger.error("IO error:", e);

            return 0;
        }

        for (Iterator iter = users.iterator(); iter.hasNext();) {
            User tempUser = (User) iter.next();
            long tempBytes = getStats(command, tempUser);

            if (tempBytes > bytes) {
                place++;
            }
        }

        return place;
    }

    /**
     * USAGE: site stats [<user>]
     *        Display a user's upload/download statistics.
     */
    public Reply doSITE_STATS(BaseFtpConnection conn) {
        FtpRequest request = conn.getRequest();

        if (!request.hasArgument()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        User user;

        if (!request.hasArgument()) {
            user = conn.getUserNull();
        } else {
            try {
                user = conn.getGlobalContext().getUserManager().getUserByName(request.getArgument());
            } catch (NoSuchUserException e) {
                return new Reply(200, "No such user: " + e.getMessage());
            } catch (UserFileException e) {
                logger.log(Level.WARN, "", e);

                return new Reply(200, e.getMessage());
            }
        }

        if (conn.getUserNull().isGroupAdmin() &&
                !conn.getUserNull().getGroup().equals(user.getGroup())) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        } else if (!conn.getUserNull().isAdmin() &&
                !user.equals(conn.getUserNull())) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        Reply response = (Reply) Reply.RESPONSE_200_COMMAND_OK.clone();
        UserManager userman = conn.getGlobalContext().getUserManager();
        response.addComment("created: " +
            user.getKeyedMap().getObject(UserManagement.CREATED, ""));
        response.addComment("rank alup: " +
            getStatsPlace("ALUP", user, userman));
        response.addComment("rank aldn: " +
            getStatsPlace("ALDN", user, userman));
        response.addComment("rank monthup: " +
            getStatsPlace("MONTHUP", user, userman));
        response.addComment("rank monthdn: " +
            getStatsPlace("MONTHDN", user, userman));
        response.addComment("rank wkup: " +
            getStatsPlace("WKUP", user, userman));
        response.addComment("rank wkdn: " +
            getStatsPlace("WKDN", user, userman));

        //        response.addComment("races won: " + user.getObject2());
        //        response.addComment("races lost: " + user.getRacesLost());
        //        response.addComment("races helped: " + user.getRacesParticipated());
        //        response.addComment("requests made: " + user.getRequests());
        //        response.addComment("requests filled: " + user.getRequestsFilled());
        //        response.addComment("nuked " + user.getTimesNuked() + " times for " +
        //            user.getNukedBytes() + " bytes");
        response.addComment("        FILES		BYTES");
        response.addComment("ALUP   " + user.getUploadedFiles() + "	" +
            Bytes.formatBytes(user.getUploadedBytes()));
        response.addComment("ALDN   " + user.getDownloadedFiles() + "	" +
            Bytes.formatBytes(user.getDownloadedBytes()));
        response.addComment("MNUP   " + user.getUploadedFilesMonth() + "	" +
            Bytes.formatBytes(user.getUploadedBytesMonth()));
        response.addComment("MNDN   " + user.getDownloadedFilesMonth() + "	" +
            Bytes.formatBytes(user.getDownloadedBytesMonth()));
        response.addComment("WKUP   " + user.getUploadedFilesWeek() + "	" +
            Bytes.formatBytes(user.getUploadedBytesWeek()));
        response.addComment("WKDN   " + user.getDownloadedFilesWeek() + "	" +
            Bytes.formatBytes(user.getDownloadedBytesWeek()));
        response.addComment("DAYUP   " + user.getUploadedFilesDay() + "     " +
            Bytes.formatBytes(user.getUploadedBytesDay()));
        response.addComment("DAYDN   " + user.getDownloadedFilesDay() +
            "       " + Bytes.formatBytes(user.getDownloadedBytesDay()));

        return response;
    }

    public Reply execute(BaseFtpConnection conn)
        throws UnhandledCommandException {
        FtpRequest request = conn.getRequest();

        if (request.getCommand().equals("SITE STATS")) {
            return doSITE_STATS(conn);
        }

        Collection users;

        try {
            users = conn.getGlobalContext().getUserManager().getAllUsers();
        } catch (UserFileException e) {
            logger.warn("", e);

            return new Reply(200, "IO error: " + e.getMessage());
        }

        int count = 10; // default # of users to list
        request = conn.getRequest();

        if (request.hasArgument()) {
            StringTokenizer st = new StringTokenizer(request.getArgument());

            try {
                count = Integer.parseInt(st.nextToken());
            } catch (NumberFormatException ex) {
                st = new StringTokenizer(request.getArgument());
            }

            if (st.hasMoreTokens()) {
                Permission perm = new Permission(FtpConfig.makeUsers(st));

                for (Iterator iter = users.iterator(); iter.hasNext();) {
                    User user = (User) iter.next();

                    if (!perm.check(user)) {
                        iter.remove();
                    }
                }
            }
        }

        final String command = request.getCommand();
        Reply response = new Reply(200);
        String type = command.substring("SITE ".length()).toLowerCase();
        ArrayList users2 = new ArrayList(users);
        Collections.sort(users2, new UserComparator(type));

        try {
            Textoutput.addTextToResponse(response, type + "_header");
        } catch (IOException ioe) {
            logger.warn("Error reading " + type + "_header", ioe);
        }

        int i = 0;

        for (Iterator iter = users2.iterator(); iter.hasNext();) {
            if (++i > count) {
                break;
            }

            User user = (User) iter.next();
            ReplacerEnvironment env = new ReplacerEnvironment();
            env.add("pos", "" + i);

            env.add("upbytesday", Bytes.formatBytes(user.getUploadedBytesDay()));
            env.add("upfilesday", "" + user.getUploadedFilesDay());
            env.add("uprateday", getUpRate(user, Trial.PERIOD_DAILY));
            env.add("upbytesweek",
                Bytes.formatBytes(user.getUploadedBytesWeek()));
            env.add("upfilesweek", "" + user.getUploadedFilesWeek());
            env.add("uprateweek", getUpRate(user, Trial.PERIOD_WEEKLY));
            env.add("upbytesmonth",
                Bytes.formatBytes(user.getUploadedBytesMonth()));
            env.add("upfilesmonth", "" + user.getUploadedFilesMonth());
            env.add("upratemonth", getUpRate(user, Trial.PERIOD_MONTHLY));
            env.add("upbytes", Bytes.formatBytes(user.getUploadedBytes()));
            env.add("upfiles", "" + user.getUploadedFiles());
            env.add("uprate", getUpRate(user, Trial.PERIOD_ALL));

            env.add("dnbytesday",
                Bytes.formatBytes(user.getDownloadedBytesDay()));
            env.add("dnfilesday", "" + user.getDownloadedFilesDay());
            env.add("dnrateday", getDownRate(user, Trial.PERIOD_DAILY));
            env.add("dnbytesweek",
                Bytes.formatBytes(user.getDownloadedBytesWeek()));
            env.add("dnfilesweek", "" + user.getDownloadedFilesWeek());
            env.add("dnrateweek", getDownRate(user, Trial.PERIOD_WEEKLY));
            env.add("dnbytesmonth",
                Bytes.formatBytes(user.getDownloadedBytesMonth()));
            env.add("dnfilesmonth", "" + user.getDownloadedFilesMonth());
            env.add("dnratemonth", getDownRate(user, Trial.PERIOD_MONTHLY));
            env.add("dnbytes", Bytes.formatBytes(user.getDownloadedBytes()));
            env.add("dnfiles", "" + user.getDownloadedFiles());
            env.add("dnrate", getDownRate(user, Trial.PERIOD_ALL));

            response.addComment(BaseFtpConnection.jprintf(
                    TransferStatistics.class, "transferstatistics" + type, env,
                    user));

            //			response.addComment(
            //	user.getUsername()
            //		+ " "
            //		+ Bytes.formatBytes(
            //			getStats(command.substring("SITE ".length()), user)));
        }

        try {
            Textoutput.addTextToResponse(response, type + "_footer");
        } catch (IOException ioe) {
            logger.warn("Error reading " + type + "_footer", ioe);
        }

        return response;
    }

    public static String getUpRate(User user, int period) {
        double s = user.getUploadedTimeForTrialPeriod(period) / (double) 1000.0;

        if (s <= 0) {
            return "- k/s";
        }

        double rate = user.getUploadedBytesForTrialPeriod(period) / s;

        return Bytes.formatBytes((long) rate) + "/s";
    }

    public static String getDownRate(User user, int period) {
        double s = user.getDownloadedTimeForTrialPeriod(period) / (double) 1000.0;

        if (s <= 0) {
            return "- k/s";
        }

        double rate = user.getDownloadedBytesForTrialPeriod(period) / s;

        return Bytes.formatBytes((long) rate) + "/s";
    }

    public String[] getFeatReplies() {
        return null;
    }

    public CommandHandler initialize(BaseFtpConnection conn,
        CommandManager initializer) {
        return this;
    }

    public void load(CommandManagerFactory initializer) {
    }

    public void unload() {
    }
}
