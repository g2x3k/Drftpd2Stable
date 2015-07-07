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

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;


import net.sf.drftpd.master.SlaveFileException;

import org.apache.log4j.BasicConfigurator;

import org.drftpd.master.RemoteSlave;

import org.drftpd.tests.DummyFtpConfig;
import org.drftpd.tests.DummyGlobalContext;
import org.drftpd.tests.DummyRemoteSlave;
import org.drftpd.tests.DummySlaveManager;

import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 * @author mog
 * @version $Id: LinkedRemoteFileTest.java 1765 2007-08-04 04:14:28Z tdsoul $
 */
public class LinkedRemoteFileTest extends TestCase {
    private LinkedRemoteFile _root;
    private RemoteSlave _slave1;
    private RemoteSlave _slave2;

    public LinkedRemoteFileTest(String fName) {
        super(fName);
    }

    private static void buildRoot(LinkedRemoteFile root, List slaveBothList,
        List slave1List, List slave2List) throws FileNotFoundException {
        root.addFile(new StaticRemoteFile(slaveBothList, "ConflictTest", 1000));
        root.addFile(new StaticRemoteFile(slave1List, "AddSlaveTest", 1000));
        root.addFile(new StaticRemoteFile(slaveBothList, "RemoteSlaveTest", 1000));
        root.addFile(new StaticRemoteFile(slave2List, "RemoveFile", 1000));
        root.addFile(new StaticRemoteFile(null, "DirTest", 0));
        root.addFile(new StaticRemoteFile(null, "RemoveDir", 0));

        LinkedRemoteFileInterface masterdir = root.getFile("DirTest");
        masterdir.addFile(new StaticRemoteFile(slaveBothList, "TestFileInDir",
                1000));

        LinkedRemoteFileInterface removedir = root.getFile("RemoveDir");
        removedir.addFile(new StaticRemoteFile(slave1List, "TestForRemoval",
                1000));
    }

    private static void internalRemergeSlave1(LinkedRemoteFile root,
        RemoteSlave slave1) throws IOException {
        CaseInsensitiveHashtable slaveroot = new CaseInsensitiveHashtable();
        slaveroot.put("ConflictTest",
            new LightRemoteFile("ConflictTest", System.currentTimeMillis(), 1000));
        slaveroot.put("AddSlaveTest",
            new LightRemoteFile("AddSlaveTest", System.currentTimeMillis(), 1000));
        slaveroot.put("RemoteSlaveTest",
            new LightRemoteFile("RemoteSlaveTest", System.currentTimeMillis(),
                1000));
        slaveroot.put("DirTest",new LightRemoteFile("DirTest",System.currentTimeMillis()));
        root.remerge(slaveroot, slave1);
        slaveroot.clear();
        slaveroot.put("TestFileInDir",
            new LightRemoteFile("TestFileInDir", System.currentTimeMillis(),
                1000));
        root.getFile("DirTest").remerge(slaveroot, slave1);
    }

    private static void internalRemergeSlave2(LinkedRemoteFile root,
        RemoteSlave slave2) throws IOException {
        CaseInsensitiveHashtable slaveroot = new CaseInsensitiveHashtable();
        slaveroot.put("ConflictTest",
            new LightRemoteFile("ConflictTest", System.currentTimeMillis(), 1001));
        slaveroot.put("AddSlaveTest",
            new LightRemoteFile("AddSlaveTest", System.currentTimeMillis(), 1000));
        slaveroot.put("DirTest",new LightRemoteFile("DirTest",System.currentTimeMillis()));
        root.remerge(slaveroot, slave2);
        slaveroot.clear();
        
        slaveroot.put("TestFileInDir",
            new LightRemoteFile("TestFileInDir", System.currentTimeMillis(),
                1000));
        root.getFile("DirTest").remerge(slaveroot, slave2);
    }

    private void internalSetUp() throws SlaveFileException {
        _slave1 = new DummyRemoteSlave("slave1", null);
        _slave2 = new DummyRemoteSlave("slave2", null);

        DummyFtpConfig cfg = new DummyFtpConfig();
        DummyGlobalContext dgctx = new DummyGlobalContext();
        dgctx.setSlaveManager(new DummySlaveManager());
        cfg.setGlobalContext(dgctx);
        _root = new LinkedRemoteFile(cfg);
    }

    public void setUp() {
        BasicConfigurator.configure();
    }

    public void testAddSlave() throws IOException, SlaveFileException {
        internalSetUp();

        List bothSlaves = Arrays.asList(new RemoteSlave[] { _slave1, _slave2 });
        System.out.println("bothSlaves = " + bothSlaves);
        System.out.println("slave1 = " + _slave1);
        System.out.println("slave2 = " + _slave2);
        CaseInsensitiveHashtable files = new CaseInsensitiveHashtable();
        files.put("AddSlaveTest",
            new LightRemoteFile("AddSlaveTest", System.currentTimeMillis(), 1000));

        CaseInsensitiveHashtable files2 = new CaseInsensitiveHashtable();
        files2.put("AddSlaveTest",
            new LightRemoteFile("AddSlaveTest", System.currentTimeMillis(), 1000));
        System.out.println("remerging slave1");
        _root.remerge(files, _slave1);
        System.out.println("remerging slave2");
        _root.remerge(files2, _slave2);
        System.out.println("slaves remerged");

        LinkedRemoteFileInterface file2 = _root.getFile(new StaticRemoteFile(
                    Collections.EMPTY_LIST, "AddSlaveTest", 1000).getName());
        assertEquals(file2, _root.getFile(file2.getName()));
        assertEquals(file2, _root.getFile(file2.getName().toUpperCase()));
        assertEquals(file2, _root.getFile(file2.getName().toLowerCase()));
        System.out.println("file2slaves = " + _root.getFile(file2.getName()).getSlaves());
        System.out.println("files = " + _root.getFiles());
        assertEquals(bothSlaves, _root.getFile(file2.getName()).getSlaves());
    }

    public void testEmptyRoot() throws FileNotFoundException {
        _root = new LinkedRemoteFile(null);
        assertEquals(0, _root.length());

        _root.addFile(new StaticRemoteFile(Collections.EMPTY_LIST, "Test1", 1000));

        assertEquals(1000, _root.length());

        _root.addFile(new StaticRemoteFile(Collections.EMPTY_LIST, "Test2",
                10000));

        assertEquals(11000, _root.length());

        _root.getFile("Test1").delete();

        assertEquals(10000, _root.length());
    }

    public void internalBuildLinks() {
        _root = new LinkedRemoteFile(null);
        _root.addFile(new StaticRemoteFile("dir", null));
        _root.addFile(new StaticRemoteFile("link", null, "/"));
        _root.addFile(new StaticRemoteFile("dirlinkrel", null, "dir"));
        _root.addFile(new StaticRemoteFile("dirlinkabs", null, "/dir"));
    }

    public void testLinkRecursive() throws FileNotFoundException {
        internalBuildLinks();

        LinkedRemoteFile lrf = _root.lookupFile("/link/link/link");
        assertEquals(lrf, _root);
    }

    public void testLinkRelativeRel() throws FileNotFoundException {
        internalBuildLinks();

        LinkedRemoteFile lrf = _root.lookupFile("dirlinkrel");
        assertEquals(lrf, _root.getFile("dir"));
    }

    public void testLinkRelativeAbs() throws FileNotFoundException {
        internalBuildLinks();

        LinkedRemoteFile lrf = _root.lookupFile("dirlinkabs");
        assertEquals(lrf, _root.getFile("dir"));
    }

    public void testLookup() throws FileNotFoundException {
        internalBuildLinks();
        assertNotSame(_root, _root.lookupFile("/dir"));
        assertNotSame(_root, _root.lookupFile("dir"));
    }
    
    public void testConflict() throws IOException, SlaveFileException {
    	internalSetUp();
    	_root.addFile(new StaticRemoteFile(Collections.singletonList(_slave1),"ConflictFile","drftpd","drftpd",1000));
    	CaseInsensitiveHashtable files = new CaseInsensitiveHashtable();
    	files.put("ConflictFile",new LightRemoteFile("ConflictFile",System.currentTimeMillis(),1001));
    	_root.remerge(files,_slave1);
    	LinkedRemoteFileInterface file = _root.getFile("ConflictFile");
    	assertNotNull(file);
    	assertEquals(1001,file.length());
    }

    public void testRemerge() throws IOException, SlaveFileException {
        System.out.println("testRemerge()");
        internalSetUp();

        List slaveBothList = Arrays.asList(new RemoteSlave[] { _slave1, _slave2 });

        // build like files.mlst does
        buildRoot(_root, slaveBothList, Collections.singletonList(_slave1),
            Collections.singletonList(_slave2));

        // remerge slave 1
        internalRemergeSlave1(_root, _slave1);
        assertEquals(Collections.singletonList(_slave1),
            _root.getFile("AddSlaveTest").getSlaves());
        assertTrue(_root.getFile("DirTest").getFile("TestFileInDir").getSlaves()
                        .contains(_slave1));

        try {
            LinkedRemoteFileInterface file = _root.getFile("RemoveDir");
            throw new AssertionFailedError(file.toString() +
                " should be deleted");
        } catch (FileNotFoundException success) {
        }

        // remerge slave 2
        internalRemergeSlave2(_root, _slave2);

        {
            assertNotNull(_root.getFile("ConflictTest"));
            assertNotNull(_root.getFile("ConflictTest.slave2.conflict"));
            assertEquals(slaveBothList,
                _root.getFile("AddSlaveTest").getSlaves());
            System.out.println(_root.getFile("RemoteSlaveTest"));
            assertFalse(_root.getFile("RemoteSlaveTest").getSlaves().contains(_slave2));

            try {
                LinkedRemoteFileInterface file = _root.getFile("RemoveFile");
                throw new AssertionFailedError(file.toString() +
                    " should be deleted");
            } catch (FileNotFoundException success) {
            }

            LinkedRemoteFileInterface masterdir = _root.getFile("DirTest");
            assertEquals(masterdir.getFile("TestFileInDir").getSlaves(),
                slaveBothList);
        }
    }
    public void testRename() throws IOException, SlaveFileException {
    	setUp();
    	internalSetUp();
    	DummyFtpConfig dfc = new DummyFtpConfig();
    	DummyGlobalContext dgc = new DummyGlobalContext();
    	dgc.setSlaveManager(new DummySlaveManager());
    	dfc.setGlobalContext(dgc);
    	_root = new LinkedRemoteFile(dfc);
    	_root.addFile(new StaticRemoteFile(null, "SourceDir", 0));
    	_root.addFile(new StaticRemoteFile(null, "DestDir", 0));
    	_root.getFile("SourceDir").addFile(new StaticRemoteFile(null, "test", 0));
    	_root.getFile("SourceDir").getFile("test").renameTo("/DestDir", "test2");
    	assertEquals("/DestDir/test2",_root.getFile("DestDir").getFile("test2").getPath());
    }
    
    public void testRemergeRemoveDir() throws IOException, SlaveFileException {
    	setUp();
    	internalSetUp();
    	DummyFtpConfig dfc = new DummyFtpConfig();
    	DummyGlobalContext dgc = new DummyGlobalContext();
    	dgc.setSlaveManager(new DummySlaveManager());
    	dfc.setGlobalContext(dgc);
    	RemoteSlave rslave = new DummyRemoteSlave("testslave",null);
    	ArrayList<RemoteSlave> slaveList = new ArrayList();
    	slaveList.add(rslave);
    	_root = new LinkedRemoteFile(dfc);
    	_root.addFile(new StaticRemoteFile(null, "DontRemoveMe", 0));
    	_root.getFile("DontRemoveMe").addFile(new StaticRemoteFile(null, "empty", 0));
    	_root.remerge(new CaseInsensitiveHashtable(), rslave);
    	assertNotNull(_root.getFile("DontRemoveMe"));
    	_root.getFile("DontRemoveMe").getFile("empty").addFile(new StaticRemoteFile(slaveList, "file", 100));
    	_root.getFile("DontRemoveMe").remerge(new CaseInsensitiveHashtable(), rslave);
    	assertEquals(0,_root.getFiles().size());
    }
    
    public void testRemergeRemoveLink() throws IOException, SlaveFileException {
    	setUp();
    	internalSetUp();
    	DummyFtpConfig dfc = new DummyFtpConfig();
    	DummyGlobalContext dgc = new DummyGlobalContext();
    	dgc.setSlaveManager(new DummySlaveManager());
    	dfc.setGlobalContext(dgc);
    	RemoteSlave rslave = new DummyRemoteSlave("testslave",null);
    	_root = new LinkedRemoteFile(dfc);
    	_root.addFile(new StaticRemoteFile(null, "TargetForLink", 0));
    	_root.addFile(new StaticRemoteFile("Link", null, "TargetForLink"));
    	_root.remerge(new CaseInsensitiveHashtable(), rslave);
    	_root.getFile("TargetForLink").remerge(new CaseInsensitiveHashtable(), rslave);
    	assertNotNull(_root.getFile("Link"));
    }
}
