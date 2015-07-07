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
package org.drftpd.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import org.apache.log4j.Logger;
import org.drftpd.usermanager.javabeans.BeanUser;

/**
 * @author mog
 * @version $Id: SafeFileOutputStream.java 1765 2007-08-04 04:14:28Z tdsoul $
 */
public class SafeFileOutputStream extends OutputStream {
    private File _actualFile;
    private OutputStreamWriter _out;
    private File _tempFile;
	private static final Logger logger = Logger.getLogger(SafeFileOutputStream.class);
	// failed until it works
    private boolean failed = true;

    public SafeFileOutputStream(File file) throws IOException {
        _actualFile = file;

        if (!_actualFile.getAbsoluteFile().getParentFile().canWrite()) {
            throw new IOException("Can't write to target dir");
        }

        File dir = _actualFile.getParentFile();

        if (dir == null) {
            dir = new File(".");
        }

        _tempFile = File.createTempFile(_actualFile.getName(), null, dir);
        _out = new OutputStreamWriter(new FileOutputStream(_tempFile), "UTF-8");
    }

    public SafeFileOutputStream(String fileName) throws IOException {
        this(new File(fileName));
    }

    public void close() throws IOException {
		if (_out == null) {
			return;
		}
		_out.flush();
    	_out.close();
		_out = null;
        if (!failed) {
            Logger.getLogger(SafeFileOutputStream.class).debug("Renaming " +
                _tempFile + " (" + _tempFile.length() + ") to " + _actualFile);

            if (_actualFile.exists() && !_actualFile.delete()) {
                throw new IOException("delete() failed");
            }

            if (!_tempFile.exists()) {
                throw new IOException("source doesn't exist");
            }

            if (!_tempFile.renameTo(_actualFile)) {
                throw new IOException("renameTo(" + _tempFile + ", " +
                    _actualFile + ") failed");
            }
        }
    }

    public void flush() throws IOException {
		_out.flush();
    }

	public void write(int b) throws IOException {
        _out.write(b);
		// ensures the file gets written to
		failed = false;
    }
}
