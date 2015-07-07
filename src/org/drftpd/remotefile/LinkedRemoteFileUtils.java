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
package org.drftpd.remotefile;

import java.util.ArrayList;
import java.util.Iterator;



/**
 * @author mog
 * @version $Id: LinkedRemoteFileUtils.java 1765 2007-08-04 04:14:28Z tdsoul $
 */
public class LinkedRemoteFileUtils {
    protected LinkedRemoteFileUtils() {
        super();
    }

//    public static void getAllDirectories(LinkedRemoteFileInterface dir,
//        Collection directories) {
//        for (Iterator iter = dir.getDirectories().iterator(); iter.hasNext();) {
//            LinkedRemoteFile subdir = (LinkedRemoteFile) iter.next();
//            getAllDirectories(subdir, directories);
//        }
//
//        directories.add(dir);
//    }

//    public static void getAllSFVFiles(LinkedRemoteFile dir, Collection sfvFiles) {
//        for (Iterator iter = dir.getDirectories().iterator(); iter.hasNext();) {
//            LinkedRemoteFile subdir = (LinkedRemoteFile) iter.next();
//            getAllSFVFiles(subdir, sfvFiles);
//        }
//        sfvFiles.add(dir);
//    }

    private static void getAllFilesInternal(LinkedRemoteFileInterface dir,
        ArrayList<LinkedRemoteFileInterface> files) {
        for (Iterator iter = dir.getFiles().iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();

            if (file.isDirectory()) {
                getAllFilesInternal(file, files);
            } else if (file.isFile()) {
                files.add(file);
            } else if (file.isLink()) {
            	// is a symlink, should we do anything?
            } else {
                throw new IllegalArgumentException();
            }
        }
    }

    public static ArrayList<LinkedRemoteFileInterface> getAllFiles(LinkedRemoteFileInterface dir) {
        ArrayList<LinkedRemoteFileInterface> files=
        	new ArrayList<LinkedRemoteFileInterface>();
        getAllFilesInternal(dir, files);
        return files;
    }
}
