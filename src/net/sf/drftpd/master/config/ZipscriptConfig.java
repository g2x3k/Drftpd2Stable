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
package net.sf.drftpd.master.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import net.sf.drftpd.master.config.FtpConfig;

import org.apache.log4j.Logger;
import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.MalformedPatternException;
import org.drftpd.GlobalContext;
import org.drftpd.org.apache.tools.ant.types.selectors.SelectorUtils;
import org.drftpd.permissions.GlobPathPermission;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.usermanager.User;

import com.Ostermiller.util.StringTokenizer;

/**
 * @author Teflon
 * @version $Id$
 */
public class ZipscriptConfig {
	private static final Logger logger = Logger
			.getLogger(ZipscriptConfig.class);

	protected GlobalContext _gctx;

	private String zsConf = "conf/zipscript.conf";

	private boolean _statusBarEnabled;

	private boolean _offlineFilesEnabled;

	private boolean _missingFilesEnabled;

	private boolean _id3Enabled;

	private boolean _dizEnabled;

	private boolean _raceStatsEnabled;

	private boolean _restrictSfvEnabled;

	private boolean _multiSfvAllowed;

	private boolean _SfvFirstRequired;

	private boolean _SfvFirstAllowNoExt;

	private String _AllowedExts;

	private String _SfvFirstUsers;

	private boolean _SfvDenySubdirEnabled;

	private String[] _SfvDenySubdirExempt; 

	private boolean _SfvDenyMKDEnabled;

	private String[] _SfvDenyMKDExempt;

	public ZipscriptConfig(GlobalContext gctx) throws IOException {
		_gctx = gctx;
		Properties cfg = new Properties();
		FileInputStream stream = null;
		try {
			stream = new FileInputStream(zsConf);
			cfg.load(stream);
			loadConfig(cfg);
		} finally {
			if(stream != null) {
				stream.close();
			}
		}
	}

	public void loadConfig(Properties cfg) throws IOException {
		_statusBarEnabled = cfg.getProperty("statusbar.enabled") == null ? true
				: cfg.getProperty("statusbar.enabled").equalsIgnoreCase("true");
		_offlineFilesEnabled = cfg.getProperty("files.offline.enabled") == null ? true
				: cfg.getProperty("files.offline.enabled").equalsIgnoreCase(
						"true");
		_missingFilesEnabled = cfg.getProperty("files.missing.enabled") == null ? true
				: cfg.getProperty("files.missing.enabled").equalsIgnoreCase(
						"true");
		_id3Enabled = cfg.getProperty("cwd.id3info.enabled") == null ? true
				: cfg.getProperty("cwd.id3info.enabled").equalsIgnoreCase(
						"true");
		_dizEnabled = cfg.getProperty("cwd.dizinfo.enabled") == null ? true
				: cfg.getProperty("cwd.dizinfo.enabled").equalsIgnoreCase(
						"true");
		_raceStatsEnabled = cfg.getProperty("cwd.racestats.enabled") == null ? true
				: cfg.getProperty("cwd.racestats.enabled").equalsIgnoreCase(
						"true");
		_restrictSfvEnabled = cfg.getProperty("sfv.restrict.files") == null ? false
				: cfg.getProperty("sfv.restrict.files")
						.equalsIgnoreCase("true");
		_multiSfvAllowed = cfg.getProperty("allow.multi.sfv") == null ? true
				: cfg.getProperty("allow.multi.sfv").equalsIgnoreCase("true");
		_SfvFirstRequired = cfg.getProperty("sfvfirst.required") == null ? true
				: cfg.getProperty("sfvfirst.required").equalsIgnoreCase("true");
		_SfvFirstAllowNoExt = cfg.getProperty("sfvfirst.allownoext") == null ? true
				: cfg.getProperty("sfvfirst.allownoext").equalsIgnoreCase(
						"true");
		_AllowedExts = cfg.getProperty("allowedexts") == null ? "sfv" : cfg
				.getProperty("allowedexts").toLowerCase().trim()
				+ " sfv";
		_SfvFirstUsers = cfg.getProperty("sfvfirst.users") == null ? "*" : cfg
				.getProperty("sfvfirst.users");
		_SfvDenySubdirEnabled = cfg.getProperty("sfvdeny.subdir.enabled") == null ? false
				: cfg.getProperty("sfvdeny.subdir.enabled").trim().equalsIgnoreCase("true");
		_SfvDenyMKDEnabled = cfg.getProperty("sfvdeny.mkd.enabled") == null ? false
				: cfg.getProperty("sfvdeny.mkd.enabled").trim().equalsIgnoreCase("true");
		_SfvDenyMKDExempt = cfg.getProperty("sfvdeny.mkd.exempt") == null ? "".split("")
				: cfg.getProperty("sfvdeny.mkd.exempt").trim().split("\\s+");

		// Locals
		String SfvFirstPathIgnore = cfg.getProperty("sfvfirst.pathignore") == null ? "*"
				: cfg.getProperty("sfvfirst.pathignore").trim();
		String SfvFirstPathCheck = cfg.getProperty("sfvfirst.pathcheck") == null ? "*"
				: cfg.getProperty("sfvfirst.pathcheck").trim();
		String SfvDenySubdirExempt = cfg.getProperty("sfvdeny.subdir.exempt") == null ? ""
				: cfg.getProperty("sfvdeny.subdir.exempt").trim();

		// SFV First PathPermissions
		if (_SfvFirstRequired) {
			try {
				// this one gets perms defined in sfvfirst.users
				StringTokenizer st = new StringTokenizer(SfvFirstPathCheck, " ");
				while (st.hasMoreTokens()) {
					_gctx.getConfig().addPathPermission(
							"sfvfirst.pathcheck",
							new GlobPathPermission(new GlobCompiler()
									.compile(st.nextToken()), FtpConfig
									.makeUsers(new StringTokenizer(
											_SfvFirstUsers, " "))));
				}
				st = new StringTokenizer(SfvFirstPathIgnore, " ");
				while (st.hasMoreTokens()) {
					_gctx.getConfig().addPathPermission(
							"sfvfirst.pathignore",
							new GlobPathPermission(new GlobCompiler()
									.compile(st.nextToken()), FtpConfig
									.makeUsers(new StringTokenizer("*", " "))));
				}
                st = new StringTokenizer(SfvDenySubdirExempt, " ");
				while (st.hasMoreTokens()) {
					_gctx.getConfig().addPathPermission(
							"sfvdeny.subdir.exempt",
							new GlobPathPermission(new GlobCompiler()
									.compile("*/" + st.nextToken() + "/"), FtpConfig
									.makeUsers(new StringTokenizer("*", " "))));
				}
			} catch (MalformedPatternException e) {
				logger.warn("Exception when reading " + zsConf, e);
			}
		}
	}

	public GlobalContext getGlobalContext() {
		return _gctx;
	}

	public boolean id3Enabled() {
		return _id3Enabled;
	}

	public boolean dizEnabled() {
		return _dizEnabled;
	}

	public boolean missingFilesEnabled() {
		return _missingFilesEnabled;
	}

	public boolean multiSfvAllowed() {
		return _multiSfvAllowed;
	}

	public boolean offlineFilesEnabled() {
		return _offlineFilesEnabled;
	}

	public boolean raceStatsEnabled() {
		return _raceStatsEnabled;
	}

	public boolean restrictSfvEnabled() {
		return _restrictSfvEnabled;
	}

	public boolean statusBarEnabled() {
		return _statusBarEnabled;
	}

	public boolean checkAllowedExtension(String file) {
		if (_SfvFirstAllowNoExt && !file.contains(".")) {
			return true;
		}
		StringTokenizer st = new StringTokenizer(_AllowedExts, " ");
		while (st.hasMoreElements()) {
			String ext = "." + st.nextElement().toString().toLowerCase();
			if (file.toLowerCase().endsWith(ext)) {
				return true;
			}
		}
		return false;
	}

	public boolean checkSfvFirstEnforcedPath(LinkedRemoteFileInterface dir,
			User user) {
		if (_SfvFirstRequired
				&& _gctx.getConfig().checkPathPermission("sfvfirst.pathcheck",
						user, dir)
				&& !_gctx.getConfig().checkPathPermission(
						"sfvfirst.pathignore", user, dir)) {
			return true;
		}
		return false;
	}

	/**
	 * Check to see if a .sfv upload should be denied in dir due to it having
	 * subdirectories as per the <code>sfvdeny.subdir.enabled</code> and
	 * <code>sfvdeny.subdir.exempt</code> properties in zipscript.conf.
	 * 
	 * <p>This feature is disabled if <code>_SfvFirstRequired</code>
	 * (zipscript.conf: <code>sfvfirst.required</code>) is not true.
	 * 
	 * @param dir	directory in which a .sfv file upload attempt is being made.
	 * @param user	user that is trying to send a .sfv file
	 * @return 		<code>name of subdir</code> if upload should be denied;
	 *         		<code>empty string</code> otherwise
	 * @since 		2.0.4+
	 * @author 		tdsoul
	 */
	public String checkSfvDenyUL(LinkedRemoteFileInterface dir, User user) {
		if (_SfvFirstRequired && _SfvDenySubdirEnabled && dir.isDirectory()
				&& dir.getDirectories().size() > 0) {
			for (LinkedRemoteFileInterface subdir : dir.getDirectories()) {
				if (!subdir.isDirectory()) continue;
				if (!_gctx.getConfig().checkPathPermission(
						"sfvdeny.subdir.exempt", user, subdir)) {
					logger.warn("SFV upload denied because subdirectory '" + subdir.getName() + "' exists.  Add it to sfvdeny.subdir.exempt in zipscript.conf if you wish to allow.");
					return subdir.getName();
				}
			}
			// all subdirs are exempt, do not deny.
		}
		return "";
	}

	/**
	 * Check to see if a MKD should be allowed.
	 * 
	 * <p>This feature is disabled if <code>_SfvFirstRequired</code>
	 * (zipscript.conf: <code>sfvfirst.required</code>) is not true.
	 * 
	 * @param dir 		directory in which a MKD is being attempted.
	 * @param subdir	the name of the subdir that	is being attempted to 
	 *                  be created in <code>dir</code>.
	 * @return 			<code>true</code> if MKD should be denied;
	 *         			<code>false</code> otherwise
	 * @since 			2.0.4+
	 * @author 			tdsoul
	 */
	public boolean checkSfvDenyMKD(LinkedRemoteFileInterface dir, String subdir) {
		if (_SfvFirstRequired && _SfvDenyMKDEnabled && dir.isDirectory()
				&& dir.getFiles().size() > 0) {
			for (LinkedRemoteFileInterface file : dir.getFiles2()) {
				if (file.isFile() && file.getName().endsWith(".sfv")) {
					// Found an sfv, check for permission.
					for (String _pat : _SfvDenyMKDExempt) {
						if (SelectorUtils.matchPath(_pat, subdir, false)) {
							// do not deny MKD, the subdir is in the exempt list.
							return false;
						}
					}
					// deny MKD because .sfv found, but subdir not in exempt list!
					logger.warn("Denied MKD of '" + subdir + "' because .sfv exists, add to sfvdeny.mkd.exempt in zipscript.conf if you wish to allow.");
					return true;
				}
			}
			// no .sfv was found.
		}
		/*
		 * either this is not a directory, or the SfvFirst or SfvDeny system
		 * is turned off, or there was no .sfv found, so do not deny MKD.
		 */
		return false;
	}
}
