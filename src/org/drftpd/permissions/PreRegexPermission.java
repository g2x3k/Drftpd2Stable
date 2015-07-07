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
package org.drftpd.permissions;

import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @author fr0w
 * @version $Id: PreRegexPermission.java 1263 2004-09-10 18:00:00Z fr0w $
 */

public class PreRegexPermission extends RegexPermission {
    Pattern _pat;
    private String _lastmatch;

    public PreRegexPermission(Pattern pat, Collection<String> users) {
        super(users);
        _pat = pat;
    }

    public boolean checkPath(String path) {
        Matcher m;
        _lastmatch = null;

		m = _pat.matcher(path);

		if (m.find()) {
			_lastmatch = m.groupCount() > 0 ? m.group(1) : null;
			return true;
		}
		return false;
    }

    public String getLastMatch() {
    	return _lastmatch;
    }
}
