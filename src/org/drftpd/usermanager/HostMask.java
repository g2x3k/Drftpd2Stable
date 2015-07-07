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
package org.drftpd.usermanager;

import org.apache.log4j.Logger;

import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Matcher;
import org.drftpd.thirdparty.plus.config.PlusConfig;

import java.net.InetAddress;


/**
 * @author mog
 * @version $Id: HostMask.java 1847 2007-12-05 01:30:05Z tdsoul $
 */
public class HostMask {

    private static final Logger logger = Logger.getLogger(HostMask.class);
    private String _hostMask;
    private String _identMask;

    public HostMask(String string) {
        int pos = string.indexOf('@');

        if (pos == -1) {
            _identMask = "*";
            _hostMask = string;
        } else {
            _identMask = string.substring(0, pos);
            _hostMask = string.substring(pos + 1);
        }
        if (_identMask.equals("")) {
        	_identMask = "*";
        }
        if (_hostMask.equals("")) {
        	_hostMask = "*";
        }
    }

	public boolean equals(Object obj) {
		if(obj == null) return false;
		HostMask h = (HostMask) obj;
		return h.getIdentMask().equals(getIdentMask()) && h.getHostMask().equals(getHostMask());
	}

    public String getHostMask() {
        return _hostMask;
    }

    public String getIdentMask() {
        return _identMask;
    }

    public String getMask() {
        return getIdentMask() + "@" + getHostMask();
    }

    /**
     * Is ident used?
     * @return false is ident mask equals "*"
     */
    public boolean isIdentMaskSignificant() {
        return !_identMask.equals("*");
    }

    public boolean matchesHost(InetAddress a) throws MalformedPatternException {
        Perl5Matcher m = new Perl5Matcher();
        GlobCompiler c = new GlobCompiler();
        Pattern p = c.compile(getHostMask());

        return (m.matches(a.getHostAddress(), p) ||
        m.matches(a.getHostName(), p));
    }

    public boolean matchesIdent(String ident) throws MalformedPatternException {
        Perl5Matcher m = new Perl5Matcher();
        GlobCompiler c = new GlobCompiler();

        if (ident == null) {
            ident = "";
        }

        return !isIdentMaskSignificant() ||
        m.matches(ident, c.compile(getIdentMask()));
    }

    public String toString() {
        return _identMask + "@" + _hostMask;
    }

    public boolean isAllowed() {
    	if (!PlusConfig.getPlusConfig().SECUREMASK) {
    		return true;
    	}

    	String patterns[] = {
    			// Covers: (*|ident)@(x.x.x.*|x.x.x.x)
    			"^(?:\\*|[-a-zA-Z0-9]+)@(?:\\d{1,3}\\.){3}(?:\\d{1,3}|\\*)$",
    			// Covers: ident@(x.x.*|x.x.*.*)
    			"^[-a-zA-Z0-9]+@(?:\\d{1,3}\\.){2}(?:\\.?\\*){1,2}$",
    			// Covers: ident@(*.someisp.com|*.someisp.com.uk)
    			"^[-a-zA-Z0-9]+@\\*(?:\\.[-a-zA-Z0-9]{4,})+(?:\\.[a-zA-Z]{2,3}|\\.[a-zA-Z]{2,3}\\.[a-zA-Z]{2})$"
    		};
    	for (int i = 0; i < patterns.length; i++) {
    		String pat = patterns[i];
    		if (toString().matches(pat)) {
    			logger.info("IP '" + toString() + "' matches ~" + pat + "~");
    			return true;
    		}
    	}
		return false;
    }
}
