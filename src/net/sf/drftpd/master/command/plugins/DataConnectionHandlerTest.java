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
package net.sf.drftpd.master.command.plugins;

import java.io.ByteArrayOutputStream;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;

import junit.framework.TestCase;
import junit.framework.TestSuite;
import net.sf.drftpd.master.FtpRequest;

import org.apache.log4j.BasicConfigurator;
import org.drftpd.commands.Reply;
import org.drftpd.commands.ReplyException;
import org.drftpd.tests.DummyBaseFtpConnection;
import org.drftpd.tests.DummyConnectionManager;
import org.drftpd.tests.DummyFtpConfig;
import org.drftpd.tests.DummyGlobalContext;
import org.drftpd.tests.DummyServerSocketFactory;
import org.drftpd.tests.DummySocketFactory;


/**
 * @author mog
 * @version $Id: DataConnectionHandlerTest.java 1765 2007-08-04 04:14:28Z tdsoul $
 */
public class DataConnectionHandlerTest extends TestCase {
    private DummyGlobalContext gctx;
    private DummyConnectionManager cm;
    DummyBaseFtpConnection conn;
    DummyDataConnectionHandler dch;

    public DataConnectionHandlerTest(String fName) {
        super(fName);
    }

    public static TestSuite suite() {
        return new TestSuite(DataConnectionHandlerTest.class);
    }

    public void assertBetween(int val, int from, int to) {
        assertTrue(val + " is less than " + from, val >= from);
        assertTrue(val + " is more than " + from, val <= to);
    }

    private String list() {
        LIST list = (LIST) new LIST().initialize(null, null);

        ByteArrayOutputStream out = conn.getDummySSF().getDummySocket()
                                        .getByteArrayOutputStream();

        conn.setRequest(new FtpRequest("LIST"));

        Reply reply = list.execute(conn);
        String replystr = conn.getDummyOut().getBuffer().toString();
        assertEquals(150, Integer.parseInt(replystr.substring(0, 3)));
        assertEquals(226, reply.getCode());

        String ret = out.toString();
        out.reset();

        return ret;
    }

    private String pasvList() throws ReplyException {
        conn.setRequest(new FtpRequest("PRET LIST"));

        Reply reply;
        reply = dch.execute(conn);
        assertEquals(reply.toString(), 200, reply.getCode());

        conn.setRequest(new FtpRequest("PASV"));
        reply = dch.execute(conn);
        assertEquals(reply.toString(), 227, reply.getCode());

        return list();
    }

    private String portList() throws ReplyException {
        conn.setRequest(new FtpRequest("PORT 127,0,0,1,0,0"));
        dch.execute(conn);

        return list();
    }

    protected void setUp() throws Exception {
        BasicConfigurator.configure();
        dch = (DummyDataConnectionHandler) new DummyDataConnectionHandler().initialize(null, null);

        gctx = new DummyGlobalContext();
        gctx.setFtpConfig(new DummyFtpConfig());

        conn = new DummyBaseFtpConnection(dch);
        cm = new DummyConnectionManager();
        cm.setGlobalContext(gctx);
        conn.setGlobalConext(gctx);
        gctx.setConnectionManager(cm);
    }

    protected void tearDown() throws Exception {
        dch = null;
    }

    public void testMixedListEqual() throws ReplyException {
        String list = portList();
        assertEquals(list, pasvList());
        assertEquals(list, portList());
        assertEquals(list, pasvList());
        testPASVWithoutPRET();
        assertEquals(list, portList());
        testPASVWithoutPRET();
        assertEquals(list, portList());
    }

    public void testPasvList() throws ReplyException {
        pasvList();
    }

    public void testPASVWithoutPRET() throws ReplyException {
        conn.setRequest(new FtpRequest("PASV"));

        Reply reply = dch.execute(conn);
        assertBetween(reply.getCode(), 500, 599);
    }

    public void testPortList() throws ReplyException {
        portList();
    }

    public class DummyDataConnectionHandler extends DataConnectionHandler {
        public DummyDataConnectionHandler() {
            super();
        }

        public ServerSocketFactory getServerSocketFactory(boolean dataChannel) {
            return new DummyServerSocketFactory(new DummySocketFactory());
        }

        public SocketFactory getSocketFactory(boolean dataChannel) {
            return new DummySocketFactory();
        }
    }

}
