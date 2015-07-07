/*
 * Created on 2004-sep-24
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package org.drftpd.remotefile;

import junit.framework.TestCase;


import java.io.FileNotFoundException;

import java.util.Stack;


/**
 * @author mog
 * @version $Id: FileUtilsTest.java 1765 2007-08-04 04:14:28Z tdsoul $
 */
public class FileUtilsTest extends TestCase {
    public FileUtilsTest(String fName) {
        super(fName);
    }

    public void testGetSubdirofDirectoryLevel() throws FileNotFoundException {
        LinkedRemoteFile root = new LinkedRemoteFile(null);
        Stack s = new Stack();
        s.push(root);
        s.push(((LinkedRemoteFile) s.peek()).addFile(
                new StaticRemoteFile("dir1", null)));
        s.push(((LinkedRemoteFile) s.peek()).addFile(
                new StaticRemoteFile("dir2", null)));
        s.push(((LinkedRemoteFile) s.peek()).addFile(
                new StaticRemoteFile("dir3", null)));
        s.push(((LinkedRemoteFile) s.peek()).addFile(
                new StaticRemoteFile("dir4", null)));

        assertEquals(root, FileUtils.getSubdirOfDirectory(root, root));
        assertEquals(s.get(1),
            FileUtils.getSubdirOfDirectory(root,
                (LinkedRemoteFileInterface) s.peek()));
    }
}
