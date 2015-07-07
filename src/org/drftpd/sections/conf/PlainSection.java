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
package org.drftpd.sections.conf;

import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;
import java.util.StringTokenizer;

import org.drftpd.Bytes;
import org.drftpd.PropertyHelper;
import org.drftpd.remotefile.FileUtils;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.sections.SectionInterface;


/**
 * @author mog
 * @version $Id: PlainSection.java 1765 2007-08-04 04:14:28Z tdsoul $
 */
public class PlainSection implements SectionInterface {
    private String _dir;
    private SectionManager _mgr;
    private String _name;
    private long _minSpeedUp;
    private long _minSpeedDn;

    public PlainSection(SectionManager mgr, int i, Properties p) {
        this(mgr, PropertyHelper.getProperty(p, i + ".name"),
            PropertyHelper.getProperty(p, i + ".path"), p.getProperty(i + ".minspeed", "0b/s 0b/s"));
    }

    public PlainSection(SectionManager mgr, String name, String path, String minSpeed) {
        _mgr = mgr;
        _name = name;
        _dir = path;

        StringTokenizer st = new StringTokenizer(minSpeed);

        String msu = st.nextToken();
        String msd = st.nextToken();

        _minSpeedUp = Bytes.parseBytes(msu.substring(0, msu.length() - 2));
        _minSpeedDn = Bytes.parseBytes(msd.substring(0, msd.length() - 2));

        if (!_dir.endsWith("/")) {
            _dir += "/";
        }

        //getFile();
    }

    public LinkedRemoteFileInterface getFile() {
        try {
            return _mgr.getConnectionManager().getGlobalContext().getRoot()
                       .lookupFile(_dir);
        } catch (FileNotFoundException e) {
            return _mgr.getConnectionManager().getGlobalContext().getRoot()
                       .createDirectories(_dir);
        }
    }

    public Collection getFiles() {
        return Collections.singletonList(getFile());
    }

    public LinkedRemoteFileInterface getFirstDirInSection(
        LinkedRemoteFileInterface dir) {
        try {
            return FileUtils.getSubdirOfDirectory(getFile(), dir);
        } catch (FileNotFoundException e) {
            return dir;
        }
    }

    public String getName() {
        return _name;
    }

    public String getPath() {
        return _dir;
    }

    public LinkedRemoteFileInterface getBaseFile() {
        return getFile();
    }

	public String getBasePath() {
		return getPath();
	}

	public Long getMinSpeedUp() {
		return _minSpeedUp;
	}

	public Long getMinSpeedDn() {
		return _minSpeedDn;
	}
}
