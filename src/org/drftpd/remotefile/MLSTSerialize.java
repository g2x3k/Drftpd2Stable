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

import net.sf.drftpd.master.config.ConfigInterface;
import org.apache.log4j.Logger;
import org.drftpd.Checksum;
import org.drftpd.master.ConnectionManager;
import org.drftpd.master.RemoteSlave;
import org.drftpd.remotefile.LinkedRemoteFile.NonExistingFile;

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.TimeZone;


/**
 * @author mog
 * @version $Id: MLSTSerialize.java 1777 2007-08-27 23:09:19Z tdsoul $
 */
public class MLSTSerialize {
    private static final Logger logger = Logger.getLogger(MLSTSerialize.class);
    // files.mlst format (local/configured time zone)
    public static final SimpleDateFormat timeval = getSDF(false);
    // MLST/MLSD ftp command format (always GMT time zone)
    public static final SimpleDateFormat timeval_gmt = getSDF(true);

    private static final SimpleDateFormat getSDF(boolean gmt) {
    	SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss.SSS");
    	if (gmt) sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
		return sdf;
    }

    public static void serialize(LinkedRemoteFileInterface dir, PrintWriter out) {
        out.println(dir.getPath() + ":");

        for (Iterator iter = dir.getMap().values().iterator(); iter.hasNext();) {
            LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
            out.println(toMLST(file, timeval));
        }

        out.println();

        //Iterator iter = dir.getFiles().iterator();
        for (Iterator iter = dir.getMap().values().iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();

            if (file.isDirectory() && !file.isLink()) {
                serialize(file, out);
            }
        }
    }

    public static String toMLST(RemoteFileInterface file) {
    	return toMLST(file, timeval_gmt);
    }

    public static String toMLST(RemoteFileInterface file, SimpleDateFormat sdf) {
        StringBuffer ret = new StringBuffer();

        if (file.isLink()) {
            ret.append("type=OS.unix=slink:" + file.getLinkPath() + ";");
        } else if (file.isFile()) {
            ret.append("type=file;");
        } else if (file.isDirectory()) {
            ret.append("type=dir;");
        } else {
            throw new RuntimeException("type");
        }

        if (file.getCheckSumCached() != 0) {
            ret.append("x.crc32=" +
                Checksum.formatChecksum(file.getCheckSumCached()) + ";");
        }

        ret.append("size=" + file.length() + ";");
        ret.append("modify=" + sdf.format(new Date(file.lastModified())) +
            ";");

        ret.append("unix.owner=" + file.getUsername() + ";");
        ret.append("unix.group=" + file.getGroupname() + ";");

        if (file.isFile()) {
            Iterator iter = file.getSlaves().iterator();
            ret.append("x.slaves=");

            if (iter.hasNext()) {
                ret.append(((RemoteSlave) iter.next()).getName());

                while (iter.hasNext()) {
                    ret.append("," + ((RemoteSlave) iter.next()).getName());
                }
            }

            ret.append(";");
        }

        if (file.getXfertime() != 0) {
            ret.append("x.xfertime=" + file.getXfertime() + ";");
        }

        ret.append(" " + file.getName());

        return ret.toString();
    }

    private static void unserialize(LineNumberReader in,
        LinkedRemoteFileInterface dir, Hashtable allRslaves, String path)
        throws IOException {
        for (String line = in.readLine();; line = in.readLine()) {
            boolean isFile = false;
            boolean isDir = false;

            if (line == null) {
                throw new CorruptFileListException("Unexpected EOF");
            }

            if (line.equals("")) {
                return;
            }

            int pos = line.indexOf(' ');

            if (pos == -1) {
                throw new CorruptFileListException("\"" + line +
                    "\" is invalid");
            }

            String filename = line.substring(pos + 1);
            StaticRemoteFile file = new StaticRemoteFile(filename);
            StringTokenizer st = new StringTokenizer(line.substring(0, pos), ";");

            while (st.hasMoreElements()) {
                String entry = st.nextToken();
                pos = entry.indexOf('=');

                if (pos == -1) {
                    throw new CorruptFileListException("\"" + entry +
                        " is corrupt, line " + in.getLineNumber());
                }

                String k = entry.substring(0, pos);
                String v = entry.substring(pos + 1);

                if ("type".equals(k)) {
                    //assert v.equals("file") || v.equals("dir") : v;
                	if (v.startsWith("unix.slink:")) {
						// kept here for conversion of old files.mlst
						file.setLink(v.substring("unix.slink:".length()));
						isDir = true;
					} else if (v.startsWith("OS.unix=slink:")) {
						file.setLink(v.substring("OS.unix=slink:".length()));
						isDir = true;
					} else {
                        isFile = "file".equals(v);
                        isDir = "dir".equals(v);

                        if (!(isFile || isDir)) {
                            throw new RuntimeException("!(isFile || isDir)");
                        }
                    }
                } else if ("modify".equals(k)) {
                    try {
                        file.setLastModified(timeval.parse(v).getTime());
                    } catch (ParseException e) {
                        throw new CorruptFileListException(e);
                    }
                } else if ("x.crc32".equals(k)) {
                    file.setCheckSum(Long.parseLong(v, 16));
                } else if ("unix.owner".equals(k)) {
                    file.setUsername(v);
                } else if ("unix.group".equals(k)) {
                    file.setGroupname(v);

                    //                } else if ("x.deleted".equals(k)) {
                    //                    if (file.isLink()) {
                    //                        isFile = true;
                    //                    }
                    //
                    //                    file.setDeleted(true);
                } else if ("size".equals(k)) {
                    file.setLength(Long.parseLong(v));
                } else if ("x.slaves".equals(k)) {
                    if (file.isLink() && isFile) {
                        isFile = true;
                        isDir = false;
                    }

                    ArrayList<RemoteSlave> rslaves = new ArrayList<RemoteSlave>();
                    StringTokenizer st2 = new StringTokenizer(v, ",");

                    while (st2.hasMoreTokens()) {
                        String slavename = st2.nextToken();
                        RemoteSlave rslave = (RemoteSlave) allRslaves.get(slavename);

                        if (rslave == null) {
                            continue;
                        }
                        rslaves.add(rslave);
                    }

                    file.setRSlaves(rslaves);
                } else if ("x.xfertime".equals(k)) {
                    file.setXfertime(Long.parseLong(v));
                }
            }

            //if(isFile && !file.isFile()) file.setRSlaves(Collections.EMPTY_LIST);
            if ((isFile != file.isFile()) && (isDir != file.isDirectory())) {
                throw new CorruptFileListException(
                    "entry is a file but had no x.slaves entry: " + line);
            }

            try {
                dir.putFile(file);
            } catch (IllegalStateException e) {
                logger.warn("", e);
            }
        }
    }

    public static LinkedRemoteFile unserialize(ConfigInterface conf, Reader in,
        List rslaves) throws IOException, CorruptFileListException {
        LinkedRemoteFile root = new LinkedRemoteFile(conf);

        LineNumberReader in2 = new LineNumberReader(in);

        for (String line = in2.readLine(); line != null;
                line = in2.readLine()) {
            if (!line.endsWith(":")) {
                throw new CorruptFileListException("expecting path, not \"" +
                    line + "\" line " + in2.getLineNumber());
            }

            String path = line.substring(0, line.length() - 1);
            NonExistingFile ret = root.lookupNonExistingFile(path);
            LinkedRemoteFileInterface dir;
            dir = ret.getFile();

            if (!ret.exists()) {
                throw new CorruptFileListException(path + " doesn't exist");
            }

            unserialize(in2, dir, RemoteSlave.rslavesToHashtable(rslaves), path);
        }

        return root;
    }

    public static LinkedRemoteFile loadMLSTFileDatabase(List rslaves,
        ConnectionManager cm) throws IOException {
		FileReader fr = null;
		try {
			fr = new FileReader("files.mlst");
			return MLSTSerialize.unserialize((cm != null) ? cm
					.getGlobalContext().getConfig() : null, fr, rslaves);
		} finally {
			if (fr != null) {
				fr.close();
			}
		}
    }
}
