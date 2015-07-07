/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package net.sf.drftpd.master.command.plugins;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.StringTokenizer;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.NoSFVEntryException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.event.TransferEvent;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.config.ZipscriptConfig;

import org.apache.log4j.Logger;
import org.drftpd.ActiveConnection;
import org.drftpd.Bytes;
import org.drftpd.Checksum;
import org.drftpd.PassiveConnection;
import org.drftpd.SFVFile;
import org.drftpd.SSLGetContext;
import org.drftpd.commands.CommandHandler;
import org.drftpd.commands.CommandHandlerFactory;
import org.drftpd.commands.Reply;
import org.drftpd.commands.ReplyException;
import org.drftpd.commands.ReplySlaveUnavailableException;
import org.drftpd.commands.UnhandledCommandException;
import org.drftpd.commands.UserManagement;
import org.drftpd.master.RemoteSlave;
import org.drftpd.master.RemoteTransfer;
import org.drftpd.remotefile.FileStillTransferringException;
import org.drftpd.remotefile.LinkedRemoteFile;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.remotefile.ListUtils;
import org.drftpd.remotefile.StaticRemoteFile;
import org.drftpd.sections.SectionInterface;
import org.drftpd.slave.ConnectInfo;
import org.drftpd.slave.Connection;
import org.drftpd.slave.RemoteIOException;
import org.drftpd.slave.Transfer;
import org.drftpd.slave.TransferFailedException;
import org.drftpd.slave.TransferStatus;
import org.drftpd.usermanager.UserFileException;
import org.tanesha.replacer.ReplacerEnvironment;
import org.drftpd.SFVFile.SFVStatus;

/**
 * @author mog
 * @author zubov
 * @version $Id: DataConnectionHandler.java 1847 2007-12-05 01:30:05Z tdsoul $
 */
public class DataConnectionHandler implements CommandHandler, CommandHandlerFactory,
    Cloneable, HandshakeCompletedListener {
    private static final Logger logger = Logger.getLogger(DataConnectionHandler.class);
    private SSLContext _ctx;
    private boolean _encryptedDataChannel;
    private boolean _SSLHandshakeClientMode = false;
    protected boolean _isPasv = false;
    protected boolean _isPort = false;
    private short xdupe = 0;

    /**
     * Holds the address that getDataSocket() should connect to in PORT mode.
     */
    private InetSocketAddress _portAddress;
    protected boolean _preTransfer = false;
    private RemoteSlave _preTransferRSlave;
    private long _resumePosition = 0;
    private RemoteSlave _rslave;

    /**
     * ServerSocket for PASV mode.
     */
    private PassiveConnection _passiveConnection;
    private RemoteTransfer _transfer;
    private LinkedRemoteFileInterface _transferFile;
    private char _type = 'A';
	private boolean _handshakeCompleted;

	/**
	 * Holds values needed for Minimum Speed Limit checks.
	 */
    private long _lastCheck = 0;
    private long _delay = 0;

    public DataConnectionHandler() {
        super();
        _handshakeCompleted = false;
        try {
            _ctx = SSLGetContext.getSSLContext();
        } catch (FileNotFoundException e) {
        	_ctx = null;
        	logger.warn("Couldn't load SSLContext, SSL/TLS disabled");
        } catch (Exception e) {
            _ctx = null;
            logger.warn("Couldn't load SSLContext, SSL/TLS disabled", e);
        }
    }

    /**
     * This method is used to check the speed of the transfer
     * and check if the speed is ok.
     * @param minSpeed minimum required speed
     * @return true (continue transfering) or false (stop transfering)
     */
    private boolean checkSpeed(long minSpeed) {
        _lastCheck = System.currentTimeMillis();
        if (_transfer.getXferSpeed() < minSpeed) {
        	return false;
        }
        return true;
    }

    private Reply doAUTH(BaseFtpConnection conn) {
        // only AUTH SSL/TLS are currently supported, reject other types.
        // per RFC 2228, Reply 504 for unsupported security mechanism
        if (!conn.getRequest().getArgument().equals("TLS")
                && !conn.getRequest().getArgument().equals("SSL")) {
            return new Reply(504, conn.getRequest().getCommandLine() + " unsupported");
        }

        if (_ctx == null) {
            return new Reply(500, "TLS not configured");
        }

        Socket s = conn.getControlSocket();

        //reply success
        conn.getControlWriter().write(new Reply(234,
                conn.getRequest().getCommandLine() + " successful").toString());
        conn.getControlWriter().flush();
        SSLSocket s2 = null;
        try {
            s2 = (SSLSocket) _ctx.getSocketFactory().createSocket(s,
                    s.getInetAddress().getHostAddress(), s.getPort(), true);
            conn.setControlSocket(s2);
            s2.setUseClientMode(false);
            s2.addHandshakeCompletedListener(this);
            s2.startHandshake();
            while(!_handshakeCompleted) {
            	synchronized(this) {
            		try {
            			wait(10000);
            		} catch (InterruptedException e) {
            			s2.close();
            			conn.stop("Took too long to negotiate SSL");
            			return new Reply(400, "Took too long to negotiate SSL");
            		}
            	}
            }
            // reset for possible auth later
            _handshakeCompleted = false;
        } catch (IOException e) {
            logger.warn("", e);
            if (s2 != null) {
            	try {
            		s2.close();
            	} catch (IOException e2){
            		logger.debug("error closing SSLSocket connection");
            	}
            }
            conn.stop(e.getMessage());

            return null;
		}
        s2 = null;

        return null;
    }

    /**
     * <code>MODE &lt;SP&gt; <mode-code> &lt;CRLF&gt;</code><br>
     *
     * The argument is a single Telnet character code specifying the data
     * transfer modes described in the Section on Transmission Modes.
     */
    private Reply doMODE(BaseFtpConnection conn) {
        FtpRequest request = conn.getRequest();

        // argument check
        if (!request.hasArgument()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        if (request.getArgument().equalsIgnoreCase("S")) {
            return Reply.RESPONSE_200_COMMAND_OK;
        }

        return Reply.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM;
    }

    /**
     * <code>PASV &lt;CRLF&gt;</code><br>
     *
     * This command requests the server-DTP to "listen" on a data port (which is
     * not its default data port) and to wait for a connection rather than
     * initiate one upon receipt of a transfer command. The response to this
     * command includes the host and port address this server is listening on.
     */
    private Reply doPASVandCPSV(BaseFtpConnection conn) {
        if (!_preTransfer) {
            return new Reply(500,
                "You need to use a client supporting PRET (PRE Transfer) to use PASV");
        }

        //reset();
        _preTransfer = false;

        if (isPort() == true) {
            throw new RuntimeException();
        }
        
        if (conn.getRequest().getCommand().equals("CPSV")) {
        	_SSLHandshakeClientMode=true;
        }

        InetSocketAddress address = null;

        if (_preTransferRSlave == null) {
            try {
				_passiveConnection = new PassiveConnection(_encryptedDataChannel ? _ctx : null, conn.getGlobalContext().getPortRange(), false);
                try {
					address = new InetSocketAddress(conn.getGlobalContext()
							.getConfig().getPasvAddress(), _passiveConnection
							.getLocalPort());
				} catch (NullPointerException e) {
					address = new InetSocketAddress(conn.getControlSocket()
							.getLocalAddress(), _passiveConnection.getLocalPort());
				}
            _isPasv = true;
            } catch (Exception ex) {
                logger.warn("", ex);

                return new Reply(550, ex.getMessage());
            }
        } else {
            try {
                String index = _preTransferRSlave.issueListenToSlave(_encryptedDataChannel, _SSLHandshakeClientMode);
                ConnectInfo ci = _preTransferRSlave.fetchTransferResponseFromIndex(index);
                _transfer = _preTransferRSlave.getTransfer(ci.getTransferIndex());
                address = new InetSocketAddress(_preTransferRSlave.getPASVIP(),_transfer.getAddress().getPort());
                _isPasv = true;
            } catch (SlaveUnavailableException e) {
            	reset(conn);
                return Reply.RESPONSE_530_SLAVE_UNAVAILABLE;
            } catch (RemoteIOException e) {
            	reset(conn);
                _preTransferRSlave.setOffline(
                    "Slave could not listen for a connection");
                logger.error("Slave could not listen for a connection", e);

                return new Reply(500,
                    "Slave could not listen for a connection");
            }
        }
        
        if (conn.getRequest().getCommand().equals("CPSV")) {
        	// can only reset it if the transfer was setup with CPSV
        	_SSLHandshakeClientMode = false;
        }

        if (address.getAddress() == null || address.getAddress().getHostAddress() == null) {
        	return new Reply(500, "Address for is unresolvable, check pasv_addr setting");
        }
        
        String addrStr = address.getAddress().getHostAddress().replace('.', ',') +
            ',' + (address.getPort() >> 8) + ',' + (address.getPort() & 0xFF);

        return new Reply(227, "Entering Passive Mode (" + addrStr + ").");
    }

    private Reply doPBSZ(BaseFtpConnection conn)
        throws UnhandledCommandException {
        String cmd = conn.getRequest().getArgument();

        if ((cmd == null) || !cmd.equals("0")) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        return Reply.RESPONSE_200_COMMAND_OK;
    }

    /**
     * <code>PORT &lt;SP&gt; <host-port> &lt;CRLF&gt;</code><br>
     *
     * The argument is a HOST-PORT specification for the data port to be used in
     * data connection. There are defaults for both the user and server data
     * ports, and under normal circumstances this command and its reply are not
     * needed. If this command is used, the argument is the concatenation of a
     * 32-bit internet host address and a 16-bit TCP port address. This address
     * information is broken into 8-bit fields and the value of each field is
     * transmitted as a decimal number (in character string representation). The
     * fields are separated by commas. A port command would be:
     *
     * PORT h1,h2,h3,h4,p1,p2
     *
     * where h1 is the high order 8 bits of the internet host address.
     */
    private Reply doPORT(BaseFtpConnection conn) {
        FtpRequest request = conn.getRequest();
        reset(conn);

        InetAddress clientAddr = null;

        // argument check
        if (!request.hasArgument()) {
            //Syntax error in parameters or arguments
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        StringTokenizer st = new StringTokenizer(request.getArgument(), ",");

        if (st.countTokens() != 6) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        // get data server
        String dataSrvName = st.nextToken() + '.' + st.nextToken() + '.' +
            st.nextToken() + '.' + st.nextToken();

        try {
            clientAddr = InetAddress.getByName(dataSrvName);
        } catch (UnknownHostException ex) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        String portHostAddress = clientAddr.getHostAddress();
        String clientHostAddress = conn.getControlSocket().getInetAddress()
                                       .getHostAddress();

        if ((portHostAddress.startsWith("192.168.") &&
                !clientHostAddress.startsWith("192.168.")) ||
                (portHostAddress.startsWith("10.") &&
                !clientHostAddress.startsWith("10."))) {
            Reply response = new Reply(501);
            response.addComment("==YOU'RE BEHIND A NAT ROUTER==");
            response.addComment(
                "Configure the firewall settings of your FTP client");
            response.addComment("  to use your real IP: " +
                conn.getControlSocket().getInetAddress().getHostAddress());
            response.addComment("And set up port forwarding in your router.");
            response.addComment(
                "Or you can just use a PRET capable client, see");
            response.addComment("  http://drftpd.org/ for PRET capable clients");

            return response;
        }

        int clientPort;

        // get data server port
        try {
            int hi = Integer.parseInt(st.nextToken());
            int lo = Integer.parseInt(st.nextToken());
            clientPort = (hi << 8) | lo;
        } catch (NumberFormatException ex) {
        	reset(conn);
            return Reply.RESPONSE_501_SYNTAX_ERROR;

            //out.write(ftpStatus.getResponse(552, request, user, null));
        }

        _isPort = true;
        _portAddress = new InetSocketAddress(clientAddr, clientPort);

        if (portHostAddress.startsWith("127.")) {
            return new Reply(200,
                "Ok, but distributed transfers won't work with local addresses");
        }

        //Notify the user that this is not his IP.. Good for NAT users that
        // aren't aware that their IP has changed.
        if (!clientAddr.equals(conn.getControlSocket().getInetAddress())) {
            return new Reply(200,
                "FXP allowed. If you're not FXPing then set your IP to " +
                conn.getControlSocket().getInetAddress().getHostAddress() +
                " (usually in firewall settings)");
        }

        return Reply.RESPONSE_200_COMMAND_OK;
    }

    private Reply doPRET(BaseFtpConnection conn) {
        reset(conn);

        FtpRequest request = conn.getRequest();
        FtpRequest ghostRequest = new FtpRequest(request.getArgument());
        String cmd = ghostRequest.getCommand();

        if (cmd.equals("LIST") || cmd.equals("NLST") || cmd.equals("MLSD")) {
            _preTransferRSlave = null;
            _preTransfer = true;

            return new Reply(200, "OK, will use master for upcoming transfer");
        } else if (cmd.equals("RETR")) {
            try {
                LinkedRemoteFileInterface downFile = conn.getCurrentDirectory()
                                                         .lookupFile(ghostRequest.getArgument());
                _preTransferRSlave = conn.getGlobalContext().getSlaveSelectionManager().getASlave(downFile.getAvailableSlaves(),
                        Transfer.TRANSFER_SENDING_DOWNLOAD, conn, downFile);
                _preTransfer = true;

                return new Reply(200,
                    "OK, will use " + _preTransferRSlave.getName() +
                    " for upcoming transfer");
            } catch (NoAvailableSlaveException e) {
                return Reply.RESPONSE_530_SLAVE_UNAVAILABLE;
            } catch (FileNotFoundException e) {
                return Reply.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN;
            }
        } else if (cmd.equals("STOR")) {
            LinkedRemoteFile.NonExistingFile nef = conn.getCurrentDirectory()
                                                       .lookupNonExistingFile(ghostRequest.getArgument());

            if (nef.exists()) {
                //TODO X-DUPE:
                if (xdupe!=0)
                    return doXDUPE(conn);
                else
                return Reply.RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN_FILE_EXISTS;
            }

            if (!ListUtils.isLegalFileName(nef.getPath())) {
                return Reply.RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN;
            }

            try {
                _preTransferRSlave = conn.getGlobalContext().getSlaveSelectionManager().getASlave(conn.getGlobalContext()
                                                                                   .getSlaveManager()
                                                                                   .getAvailableSlaves(),
                        Transfer.TRANSFER_RECEIVING_UPLOAD, conn,
                        nef.getFile());
                _preTransfer = true;

                return new Reply(200,
                    "OK, will use " + _preTransferRSlave.getName() +
                    " for upcoming transfer");
            } catch (NoAvailableSlaveException e) {
                return Reply.RESPONSE_530_SLAVE_UNAVAILABLE;
            }
        } else {
            return Reply.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM;
        }
    }
    
    private Reply doSSCN(BaseFtpConnection conn) {
        if (_ctx == null) {
            return new Reply(500, "TLS not configured");
        }
        
        if (!(conn.getControlSocket() instanceof SSLSocket)) {
        	return new Reply(500, "You are not on a secure channel");
        }
        
        if (!_encryptedDataChannel) {
        	return new Reply(500, "SSCN only works for encrypted transfers");
        }
        
		if (conn.getRequest().hasArgument()) {
			if (conn.getRequest().getArgument().equalsIgnoreCase("ON")) {
				_SSLHandshakeClientMode = true;
			}
			if (conn.getRequest().getArgument().equalsIgnoreCase("OFF")) {
				_SSLHandshakeClientMode = false;
			}
		}
		return new Reply(220, "SSCN:"
				+ (_SSLHandshakeClientMode ? "CLIENT" : "SERVER") + " METHOD");
	}

    private Reply doPROT(BaseFtpConnection conn)
        throws UnhandledCommandException {
        if (_ctx == null) {
            return new Reply(500, "TLS not configured");
        }
        
        if (!(conn.getControlSocket() instanceof SSLSocket)) {
        	return new Reply(500, "You are not on a secure channel");
        }

        FtpRequest req = conn.getRequest();

        if (!req.hasArgument()) {
            //clear
            _encryptedDataChannel = false;

            return Reply.RESPONSE_200_COMMAND_OK;
        }

        if (!req.hasArgument() || (req.getArgument().length() != 1)) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        switch (Character.toUpperCase(req.getArgument().charAt(0))) {
        case 'C':

            //clear
            _encryptedDataChannel = false;

            return Reply.RESPONSE_200_COMMAND_OK;

        case 'P':

            //private
            _encryptedDataChannel = true;

            return Reply.RESPONSE_200_COMMAND_OK;

        default:
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }
    }

    /**
     * <code>REST &lt;SP&gt; <marker> &lt;CRLF&gt;</code><br>
     *
     * The argument field represents the server marker at which file transfer is
     * to be restarted. This command does not cause file transfer but skips over
     * the file to the specified data checkpoint. This command shall be
     * immediately followed by the appropriate FTP service command which shall
     * cause file transfer to resume.
     */
    private Reply doREST(BaseFtpConnection conn) {
        FtpRequest request = conn.getRequest();

        // argument check
        if (!request.hasArgument()) {
        	reset(conn);
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        String skipNum = request.getArgument();

        try {
            _resumePosition = Long.parseLong(skipNum);
        } catch (NumberFormatException ex) {
        	reset(conn);
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        if (_resumePosition < 0) {
            _resumePosition = 0;
            reset(conn);
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        return Reply.RESPONSE_350_PENDING_FURTHER_INFORMATION;
    }

    /**
	 * Use ZipScript features to manually scan a directory containing a sfv
	 * file.
	 * 
	 * @author Dom
	 * @author tdsoul
	 * @since plus-2.0.6
	 */
	private Reply doSITE_RESCAN(BaseFtpConnection conn) {
		PrintWriter out = conn.getControlWriter();

		FtpRequest request = conn.getRequest();
		boolean recursive = false;
		boolean forceRescan = false;
		boolean deleteBad = false;
		boolean quiet = false;
		boolean usecached = false;
		if (request.hasArgument()) {
			StringTokenizer args = new StringTokenizer(request.getArgument());
			while (args.hasMoreTokens()) {
				String arg = args.nextToken();
				if (arg.equalsIgnoreCase("-r")
						|| arg.equalsIgnoreCase("-recursive")) {
					recursive = true;
				} else if (arg.equalsIgnoreCase("force")) {
					forceRescan = true;
				} else if (arg.equalsIgnoreCase("-q")
						|| arg.equalsIgnoreCase("-quiet")) {
					quiet = true;
				} else if (arg.equalsIgnoreCase("-c")
						|| arg.equalsIgnoreCase("-cached")) {
					usecached = true;
				} else if (arg.equalsIgnoreCase("-delete")) {
						deleteBad = true;
				}
			}
		}

		LinkedList<LinkedRemoteFileInterface> dirs = new LinkedList<LinkedRemoteFileInterface>();
		dirs.add(conn.getCurrentDirectory());
		out.println("200- Initial dirs: " + dirs.size());
		while (dirs.size() > 0) {
			out.flush();
			LinkedRemoteFileInterface workingDir = dirs.poll();
			SFVFile workingSfv = null;
			if (recursive) {
				dirs.addAll(workingDir.getDirectories());
				// out.println("200- Size now: " + dirs.size());
				// out.flush();
			}

			try {
				workingSfv = workingDir.lookupSFVFile();
			} catch (FileNotFoundException e2) {
				/*
				 * No sfv in this dir, silently ignore so not to add useless
				 * output in recursive mode
				 */
				continue;
			} catch (NoAvailableSlaveException e2) {
				out.println("200- No available slave with sfv for: " + workingDir.getPath());
				continue;
			} catch (FileStillTransferringException e2) {
				// Silently skip
				continue;
			} catch (IOException e2) {
				out.println("200- IOError reading sfv for: " + workingDir.getPath());
				continue;
			}
			out.println("200- Rescanning: " + workingDir.getPath());
			for (Iterator<Map.Entry<String, Long>> i = workingSfv.getEntries().entrySet().iterator(); i.hasNext();) {
				out.flush();
				Map.Entry<String, Long> entry = i.next();
				String fileName = entry.getKey();
				Long checkSum = entry.getValue();
				LinkedRemoteFileInterface file;

				try {
					file = workingDir.lookupFile(fileName);
				} catch (FileNotFoundException ex) {
					out.write("200- SFV: "
							+ Checksum.formatChecksum(checkSum.longValue())
							+ " SLAVE: " + fileName + " MISSING"
							+ BaseFtpConnection.NEWLINE);
					continue;
				}

				String status;
				long fileCheckSum = 0;

				try {
					if (forceRescan) {
						fileCheckSum = file.getCheckSumFromSlave();
					} else {
						if (usecached)
							fileCheckSum = file.getCheckSumCached();
						else
							fileCheckSum = file.getCheckSum();
					}
				} catch (NoAvailableSlaveException e1) {
					out.println("200- " + fileName + "SFV: "
							+ Checksum.formatChecksum(checkSum.longValue())
							+ " SLAVE: OFFLINE");
					continue;
				} catch (IOException ex) {
					out.print("200- " + fileName + " SFV: "
							+ Checksum.formatChecksum(checkSum.longValue())
							+ " SLAVE: IO error: " + ex.getMessage());
					continue;
				}

				if (fileCheckSum == 0L) {
					if (forceRescan)
						status = "FAILED - failed to checksum file";
					else
						status = "UNKNOWN - This file has no checksum on file.  Use the FORCE option.";
				} else if (checkSum.longValue() == fileCheckSum) {
					status = quiet ? "" : "OK";
				} else {
					status = "FAILED - checksum mismatch";
					if (deleteBad) {
						// check permission
						if (file.getUsername().equals(conn.getUserNull().getName())) {
							if (!conn.getGlobalContext().getConfig().checkPathPermission("deleteown", conn.getUserNull(), file)) {
								status += " (delete failed, no permission)";
							}
						} else if (!conn.getGlobalContext().getConfig().checkPathPermission("delete", conn.getUserNull(), file)) {
							status += " (delete failed, no permission)";
						} else {
							file.delete();
							status += " (deleted)";
						}
					}
				}

				if (status != "") {
					out.println("200- " + fileName + " SFV: "
							+ Checksum.formatChecksum(checkSum.longValue())
							+ " SLAVE: "
							+ Checksum.formatChecksum(fileCheckSum) + " "
							+ status);
				}
				continue;
			}
		}
		out.flush();
		return Reply.RESPONSE_200_COMMAND_OK;
	}

    private Reply doXDUPE(BaseFtpConnection conn) {
        LinkedRemoteFileInterface dir = conn.getCurrentDirectory();
        Reply response = (Reply) Reply.RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN_FILE_EXISTS.clone();

        List files;
        if (dir.isFile()) {
            return response;
        } else {
            files = new ArrayList(dir.getMap().values());
        }
        Collections.sort(files);

        String mode1or4 = "";

        for (Iterator iter = files.iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();

            if (file.isDirectory())
                continue;

            switch (xdupe) {
                case 1 :
                    if (file.getName().length() > 66)
                        response.addComment("X-DUPE: "
                                + file.getName().substring(0, 65));
                    else if (file.getName().length() + mode1or4.length() > 66) {
                        response.addComment("X-DUPE: " + mode1or4);
                        mode1or4 = file.getName();
                    } else
                        mode1or4 = (mode1or4.length() > 0 ? mode1or4 + " " : "") + file.getName();
                    continue;
                case 2 :
                    response.addComment("X-DUPE: " + (file.getName().length() > 66 
                                    ? file.getName().substring(0, 65) 
                                            : file.getName()));
                    continue;
                case 3 :
                    response.addComment("X-DUPE: " + file.getName());
                    continue;
                case 4 :
                    if (mode1or4.length() + file.getName().length() <= 1010)
                        mode1or4 = (mode1or4.length() > 0 ? mode1or4 + " " : "") + file.getName();
                    continue;
            }
        }
        if (mode1or4.length() > 0)
            response.addComment("X-DUPE: " + mode1or4);

        return response;
    }

    private Reply doSITE_XDUPE(BaseFtpConnection conn) {
        FtpRequest request = conn.getRequest();

        // return Reply.RESPONSE_502_COMMAND_NOT_IMPLEMENTED;
        // resetState();
        //
        if (!request.hasArgument()) {
            if (this.xdupe == 0) {
                return new Reply(200, "Extended dupe mode is disabled.");
            } else {
                return new Reply(200, "Extended dupe mode " + this.xdupe
                        + " is enabled.");
            }
        } else {
            short myXdupe;
            try {
                myXdupe = Short.parseShort(request.getArgument());
            } catch (NumberFormatException ex) {
                return Reply.RESPONSE_501_SYNTAX_ERROR;
            }

            if (myXdupe < 0 || myXdupe > 4) {
                return Reply.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM;
            }

            this.xdupe = myXdupe;
            return new Reply(200, "Activated extended dupe mode " + myXdupe + ".");
        }
    }

    /**
     * <code>STRU &lt;SP&gt; &lt;structure-code&gt; &lt;CRLF&gt;</code><br>
     *
     * The argument is a single Telnet character code specifying file structure.
     */
    private Reply doSTRU(BaseFtpConnection conn) {
        FtpRequest request = conn.getRequest();

        // argument check
        if (!request.hasArgument()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        if (request.getArgument().equalsIgnoreCase("F")) {
            return Reply.RESPONSE_200_COMMAND_OK;
        }

        return Reply.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM;
    }

    /**
     * <code>SYST &lt;CRLF&gt;</code><br>
     *
     * This command is used to find out the type of operating system at the
     * server.
     */
    private Reply doSYST(BaseFtpConnection conn) {
        /*
         * String systemName = System.getProperty("os.name"); if(systemName ==
         * null) { systemName = "UNKNOWN"; } else { systemName =
         * systemName.toUpperCase(); systemName = systemName.replace(' ', '-'); }
         * String args[] = {systemName};
         */
        return Reply.RESPONSE_215_SYSTEM_TYPE;

        //String args[] = { "UNIX" };
        //out.write(ftpStatus.getResponse(215, request, user, args));
    }

    /**
     * <code>TYPE &lt;SP&gt; &lt;type-code&gt; &lt;CRLF&gt;</code><br>
     *
     * The argument specifies the representation type.
     */
    private Reply doTYPE(BaseFtpConnection conn) {
        FtpRequest request = conn.getRequest();

        // get type from argument
        if (!request.hasArgument()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        // set it
        if (setType(request.getArgument().charAt(0))) {
            return Reply.RESPONSE_200_COMMAND_OK;
        }

        return Reply.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM;
    }

    public Reply execute(BaseFtpConnection conn)
        throws ReplyException {
        String cmd = conn.getRequest().getCommand();
             
        if ("MODE".equals(cmd)) {
            return doMODE(conn);
        }

        if ("PASV".equals(cmd) || "CPSV".equals(cmd)) {
            return doPASVandCPSV(conn);
        }

        if ("PORT".equals(cmd)) {
            return doPORT(conn);
        }

        if ("PRET".equals(cmd)) {
            return doPRET(conn);
        }

        if ("REST".equals(cmd)) {
            return doREST(conn);
        }

        if ("RETR".equals(cmd) || "STOR".equals(cmd) || "APPE".equals(cmd)) {
            return transfer(conn);
        }

        if ("SITE RESCAN".equals(cmd)) {
            return doSITE_RESCAN(conn);
        }

        if ("SITE XDUPE".equals(cmd)) {
            return doSITE_XDUPE(conn);
        }

        if ("STRU".equals(cmd)) {
            return doSTRU(conn);
        }

        if ("SYST".equals(cmd)) {
            return doSYST(conn);
        }

        if ("TYPE".equals(cmd)) {
            return doTYPE(conn);
        }

        if ("AUTH".equals(cmd)) {
            return doAUTH(conn);
        }

        if ("PROT".equals(cmd)) {
            return doPROT(conn);
        }

        if ("PBSZ".equals(cmd)) {
            return doPBSZ(conn);
        }
        
        if ("SSCN".equals(cmd)) {
        	return doSSCN(conn);
        }


        throw UnhandledCommandException.create(DataConnectionHandler.class,
            conn.getRequest());
    }

    /**
     * Get the data socket.
     *
     * Used by LIST and NLST and MLST.
     */
    public Socket getDataSocket() throws IOException {
        Socket dataSocket;

        // get socket depending on the selection
        if (isPort()) {
            try {
				ActiveConnection ac = new ActiveConnection(_encryptedDataChannel ? _ctx : null, _portAddress, _SSLHandshakeClientMode);
                dataSocket = ac.connect(0);
            } catch (IOException ex) {
                logger.warn("Error opening data socket", ex);
                dataSocket = null;
                throw ex;
            }
        } else if (isPasv()) {
            try {
                dataSocket = _passiveConnection.connect(0);
            } finally {
				if (_passiveConnection != null) {
					_passiveConnection.abort();
	                _passiveConnection = null;
				}
            }
        } else {
            throw new IllegalStateException("Neither PASV nor PORT");
        }
		// Already done since we are using ActiveConnection and PasvConnection
/*        dataSocket.setSoTimeout(Connection.TIMEOUT); // 15 seconds timeout

        if (dataSocket instanceof SSLSocket) {
            SSLSocket ssldatasocket = (SSLSocket) dataSocket;
            ssldatasocket.setUseClientMode(false);
            ssldatasocket.startHandshake();
        }*/

        return dataSocket;
    }

    public String[] getFeatReplies() {
        if (_ctx != null) {
            return new String[] { "PRET", "AUTH SSL", "PBSZ", "CPSV" , "SSCN"};
        }

        return new String[] { "PRET" };
    }
    
    public String getHelp(String cmd) {
        ResourceBundle bundle = ResourceBundle.getBundle(DataConnectionHandler.class.getName());
        if ("".equals(cmd))
            return bundle.getString("help.general")+"\n";
        else if("rescan".equals(cmd))
            return bundle.getString("help.rescan")+"\n";
        else
            return "";
    }

    public RemoteSlave getTranferSlave() {
        return _rslave;
    }

    public synchronized RemoteTransfer getTransfer() throws ObjectNotFoundException {
        if (_transfer == null)
            throw new ObjectNotFoundException();
        return _transfer;
    }

    public LinkedRemoteFileInterface getTransferFile() {
        return _transferFile;
    }

    /**
     * Get the user data type.
     */
    public char getType() {
        return _type;
    }

    public CommandHandler initialize(BaseFtpConnection conn,
        CommandManager initializer) {
        try {
            return (DataConnectionHandler) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isEncryptedDataChannel() {
        return _encryptedDataChannel;
    }

    /**
     * Guarantes pre transfer is set up correctly.
     */
    public boolean isPasv() {
        return _isPasv;
    }

    public boolean isPort() {
        return _isPort;
    }

    public boolean isPreTransfer() {
        return _preTransfer || isPasv();
    }

    public synchronized boolean isTransfering() {
		return _transfer != null && _rslave != null && _transferFile != null;
	}

    public void load(CommandManagerFactory initializer) {
    }

    protected synchronized void reset(BaseFtpConnection conn) {
        _rslave = null;
        if (_transfer != null) {
        	try {
        		_transfer.abort("reset");
        	} catch (Throwable t) {
        		logger.debug("reset failed to abort transfer on the slave", t);
        	}
        }
        _transfer = null;
        if (_transferFile != null) {
        	if ((conn.getRequest().getCommand().equals("STOR") == true) &&
                    (_transferFile.getXfertime() == -1)) {

                        // Transfer failed on STOR

        		_transferFile.setXfertime(0);
        	}
        	_transferFile = null;
        }
        _preTransfer = false;
        _preTransferRSlave = null;

        if (_passiveConnection != null) { //isPasv() && _preTransferRSlave == null
            _passiveConnection.abort();
        }

        _isPasv = false;
        _passiveConnection = null;
        _isPort = false;
        _resumePosition = 0;
        
        _lastCheck = 0;
        _delay = 0;
    }

    /**
     * Set the data type. Supported types are A (ascii) and I (binary).
     *
     * @return true if success
     */
    private boolean setType(char type) {
        type = Character.toUpperCase(type);

        if ((type != 'A') && (type != 'I')) {
            return false;
        }

        _type = type;

        return true;
    }

    /**
     * <code>STOU &lt;CRLF&gt;</code><br>
     *
     * This command behaves like STOR except that the resultant file is to be
     * created in the current directory under a name unique to that directory.
     * The 250 Transfer Started response must include the name generated.
     */

    //TODO STOU

    /*
     * public void doSTOU(FtpRequest request, PrintWriter out) {
     *  // reset state variables resetState();
     *  // get filenames String fileName =
     * user.getVirtualDirectory().getAbsoluteName("ftp.dat"); String
     * physicalName = user.getVirtualDirectory().getPhysicalName(fileName); File
     * requestedFile = new File(physicalName); requestedFile =
     * IoUtils.getUniqueFile(requestedFile); fileName =
     * user.getVirtualDirectory().getVirtualName(requestedFile.getAbsolutePath());
     * String args[] = {fileName};
     *  // check permission
     * if(!user.getVirtualDirectory().hasCreatePermission(fileName, false)) {
     * out.write(ftpStatus.getResponse(550, request, user, null)); return; }
     *  // now transfer file data out.print(FtpResponse.RESPONSE_150_OK);
     * InputStream is = null; OutputStream os = null; try { Socket dataSoc =
     * mDataConnection.getDataSocket(); if (dataSoc == null) {
     * out.write(ftpStatus.getResponse(550, request, user, args)); return; }
     *
     *
     * is = dataSoc.getInputStream(); os = user.getOutputStream( new
     * FileOutputStream(requestedFile) );
     *
     * StreamConnector msc = new StreamConnector(is, os);
     * msc.setMaxTransferRate(user.getMaxUploadRate()); msc.setObserver(this);
     * msc.connect();
     *
     * if(msc.hasException()) { out.write(ftpStatus.getResponse(451, request,
     * user, null)); return; } else {
     * mConfig.getStatistics().setUpload(requestedFile, user,
     * msc.getTransferredSize()); }
     *
     * out.write(ftpStatus.getResponse(226, request, user, null));
     * mDataConnection.reset(); out.write(ftpStatus.getResponse(250, request,
     * user, args)); } catch(IOException ex) {
     * out.write(ftpStatus.getResponse(425, request, user, null)); } finally {
     * IoUtils.close(is); IoUtils.close(os); mDataConnection.reset(); } }
     */

    /**
     * <code>RETR &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
     *
     * This command causes the server-DTP to transfer a copy of the file,
     * specified in the pathname, to the server- or user-DTP at the other end of
     * the data connection. The status and contents of the file at the server
     * site shall be unaffected.
     *
     * RETR 125, 150 (110) 226, 250 425, 426, 451 450, 550 500, 501, 421, 530
     * <p>
     * <code>STOR &lt;SP&gt; &lt;pathname&gt; &lt;CRLF&gt;</code><br>
     *
     * This command causes the server-DTP to accept the data transferred via the
     * data connection and to store the data as a file at the server site. If
     * the file specified in the pathname exists at the server site, then its
     * contents shall be replaced by the data being transferred. A new file is
     * created at the server site if the file specified in the pathname does not
     * already exist.
     *
     * STOR 125, 150 (110) 226, 250 425, 426, 451, 551, 552 532, 450, 452, 553
     * 500, 501, 421, 530
     *
     * ''zipscript?? renames bad uploads to .bad, how do we handle this with
     * resumes?
     */
    //TODO add APPE support
    private Reply transfer(BaseFtpConnection conn)
        throws ReplyException {
        ReplacerEnvironment env = new ReplacerEnvironment();
        if (!_encryptedDataChannel &&
                conn.getGlobalContext().getConfig().checkPermission("denydatauncrypted", conn.getUserNull())) {
        	reset(conn);
            return new Reply(530, "USE SECURE DATA CONNECTION");
        }

        try {
            FtpRequest request = conn.getRequest();
            char direction = conn.getDirection();
            String cmd = conn.getRequest().getCommand();
            boolean isStor = cmd.equals("STOR");
            boolean isRetr = cmd.equals("RETR");
            boolean isAppe = cmd.equals("APPE");
            boolean isStou = cmd.equals("STOU");
            String eventType = isRetr ? "RETR" : "STOR";

            if (isAppe || isStou) {
                throw UnhandledCommandException.create(DataConnectionHandler.class,
                    conn.getRequest());
            }

            // argument check
            if (!request.hasArgument()) {
            	// reset(); already done in finally block
                return Reply.RESPONSE_501_SYNTAX_ERROR;
            }
            
//          Checks maxsim up/down
            // _simup OR _simdown = 0, exempt
            int comparison = 0;
            int count = conn.transferCounter(direction);
            env.add("maxsim", count);

            if (direction == Transfer.TRANSFER_RECEIVING_UPLOAD) {
            	comparison =  conn.getUserNull().getMaxSimUp();
                env.add("direction", "upload");
            } else {
            	comparison =  conn.getUserNull().getMaxSimDown();
                env.add("direction", "download");
            }

            if (comparison != 0 && count >= comparison)
            	return new Reply(550, conn.jprintf(DataConnectionHandler.class, "transfer.err.maxsim", env));
            
            // get filenames
            LinkedRemoteFileInterface targetDir;
            String targetFileName;

            if (isRetr) {
                try {
                    _transferFile = conn.getCurrentDirectory().lookupFile(request.getArgument());

                    if (!_transferFile.isFile()) {
                    	// reset(); already done in finally block
                        return new Reply(550, "Not a plain file");
                    }

                    targetDir = _transferFile.getParentFileNull();
                    targetFileName = _transferFile.getName();
                } catch (FileNotFoundException ex) {
                	// reset(); already done in finally block
                    return new Reply(550, ex.getMessage());
                }
            } else if (isStor) {
                LinkedRemoteFile.NonExistingFile ret = conn.getCurrentDirectory()
                                                           .lookupNonExistingFile(conn.getGlobalContext()
                                                                                      .getConfig()
                                                                                      .getFileName(request.getArgument()));
                targetDir = ret.getFile();
                targetFileName = ret.getPath();

                if (ret.exists()) {
                    // target exists, this could be overwrite or resume
                    //TODO overwrite & resume files.
                	// reset(); already done in finally block
                	if (xdupe!=0)
                		return doXDUPE(conn);
                	else
                		return Reply.RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN_FILE_EXISTS;

                    //_transferFile = targetDir;
                    //targetDir = _transferFile.getParent();
                    //if(_transfereFile.getOwner().equals(getUser().getUsername()))
                    // {
                    //	// allow overwrite/resume
                    //}
                    //if(directory.isDirectory()) {
                    //	return FtpReply.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN;
                    //}
                }

                if (!ListUtils.isLegalFileName(targetFileName) ||
                        !conn.getGlobalContext().getConfig().checkPathPermission("privpath", conn.getUserNull(), targetDir, true)) {
                	// reset(); already done in finally block
                    return new Reply(553,
                        "Requested action not taken. File name not allowed.");
                }

                //do our zipscript sfv checks
                String checkName = targetFileName.toLowerCase();
                ZipscriptConfig zsCfg = conn.getGlobalContext().getZsConfig();
                boolean SfvFirstEnforcedPath = zsCfg.checkSfvFirstEnforcedPath(targetDir, 
                		conn.getUserNull()); 
                try {
                    SFVFile sfv;
					try {
						sfv = conn.getCurrentDirectory().lookupSFVFile();
					} catch (FileStillTransferringException e) {
						// I have no idea how to handle this
						return new Reply(400, "SFVFile still transferring.");
					}
                    if (checkName.endsWith(".sfv") && 
                    	!zsCfg.multiSfvAllowed()) {
                        return new Reply(533,
                        	"Requested action not taken. Multiple SFV files not allowed.");
                    }
                    if (SfvFirstEnforcedPath && 
                    		!zsCfg.checkAllowedExtension(checkName)) {
                		// filename not explicitly permitted, check for sfv entry
                        boolean allow = false;
                        if (zsCfg.restrictSfvEnabled()) {
                            for (Iterator iter = sfv.getNames().iterator(); iter.hasNext();) {
                                String name = (String) iter.next();
                                if (name.toLowerCase().equals(checkName)) {
                                    allow = true;
                                    break;
                                }
                            }
                            if (!allow) {
                                return new Reply(533,
                                	"Requested action not taken. File not found in sfv.");
                            }
                        }
                    }
                } catch (FileNotFoundException e1) {
                    // no sfv found in dir
                	if (SfvFirstEnforcedPath) {
                		// check if .sfv, and if so should it be allowed.
                		String badsubdir = zsCfg.checkSfvDenyUL(targetDir, conn.getUserNull());
                		if (checkName.endsWith(".sfv")
                				&& badsubdir != "") {
                			return new Reply(533,
                					"Requested action not taken. You can not upload an SFV here due to subdir '" + badsubdir + "' (ZipScript+).");
                		}
                		if (!zsCfg.checkAllowedExtension(checkName)) {
                			// filename not explicitly permitted
                			// ForceSfvFirst is on, and file is in an enforced
                			// path.
                			return new Reply(533,
                			"Requested action not taken. You must upload sfv first.");
                		}
                	}
                } catch (IOException e1) {
                    //error reading sfv, do nothing
                } catch (NoAvailableSlaveException e1) {
                    //sfv not online, do nothing
                }
            } else {
            	// reset(); already done in finally block
            	throw UnhandledCommandException.create(
            			DataConnectionHandler.class, request);
            }

            // check access
            if (!conn.getGlobalContext().getConfig().checkPathPermission("privpath", conn.getUserNull(), targetDir, true)) {
            	// reset(); already done in finally block
                return new Reply(550,
                    request.getArgument() + ": No such file");
            }

            String DeniedReason = null;
            switch (direction) {
            case Transfer.TRANSFER_SENDING_DOWNLOAD:

                if (!conn.getGlobalContext().getConfig().checkPathPermission("download", conn.getUserNull(), targetDir)) {
                	// reset(); already done in finally block
                    return Reply.RESPONSE_530_ACCESS_DENIED;
                } 
                DeniedReason = conn.getGlobalContext().getConfig().checkRegexPermission("RETR", conn.getUserNull(),
                		targetDir.getPath() + "/" + targetFileName, "file");
                if (DeniedReason != null) {
                	// reset(); already done in finally block
                	return new Reply(530, "Access denied (" + DeniedReason + ")");
                    
                }

                break;

            case Transfer.TRANSFER_RECEIVING_UPLOAD:

                if (!conn.getGlobalContext().getConfig().checkPathPermission("upload", conn.getUserNull(), targetDir)) {
                	// reset(); already done in finally block
                    return Reply.RESPONSE_530_ACCESS_DENIED;
                } 
                DeniedReason = conn.getGlobalContext().getConfig().checkRegexPermission("STOR", conn.getUserNull(),
                		targetDir.getPath() + "/" + targetFileName, "file");
                if (DeniedReason != null) {
                	// reset(); already done in finally block
                	return new Reply(530, "Access denied (" + DeniedReason + ")");
                }

                break;

            default:
            	// reset(); already done in finally block
                throw UnhandledCommandException.create(DataConnectionHandler.class,
                    request);
            }

            //check credits
            if (isRetr) {
                if ((conn.getUserNull().getKeyedMap().getObjectFloat(
                        UserManagement.RATIO) != 0)
                        && (conn.getGlobalContext().getConfig()
                                .getCreditLossRatio(_transferFile,
                                        conn.getUserNull()) != 0)
                        && (conn.getUserNull().getCredits() < _transferFile
                                .length())) {
                	// reset(); already done in finally block
                    return new Reply(550, "Not enough credits.");
                }
            }

            //setup _rslave
            //if (isCpsv)
            if (isPasv()) {
                //				isPasv() means we're setup correctly
                //				if (!_preTransfer || _preTransferRSlave == null)
                //					return FtpReply.RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS;
                //check pretransfer
                if (isRetr &&
                        !_transferFile.getSlaves().contains(_preTransferRSlave)) {
                	// reset(); already done in finally block
                    return Reply.RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS;
                }

                _rslave = _preTransferRSlave;

                //_preTransferRSlave = null;
                //_preTransfer = false;
                //code above to be handled by reset()
            } else {
                try {
                    if (direction == Transfer.TRANSFER_SENDING_DOWNLOAD) {
                        _rslave = conn.getGlobalContext().getSlaveSelectionManager().getASlave(_transferFile.getAvailableSlaves(),
                                Transfer.TRANSFER_SENDING_DOWNLOAD, conn,
                                _transferFile);
                    } else if (direction == Transfer.TRANSFER_RECEIVING_UPLOAD) {
                        _rslave = conn.getGlobalContext().getSlaveSelectionManager().getASlave(conn.getGlobalContext()
                                                                                .getSlaveManager()
                                                                                .getAvailableSlaves(),
                                Transfer.TRANSFER_RECEIVING_UPLOAD, conn,
                                targetDir);
                    } else {
                    	// reset(); already done in finally block
                        throw new RuntimeException();
                    }
                } catch (NoAvailableSlaveException ex) {
                	//TODO Might not be good to 450 reply always
                	//from rfc: 450 Requested file action not taken. File unavailable (e.g., file busy).
                	// reset(); already done in finally block
                	throw new ReplySlaveUnavailableException(ex, 450);
                }
            }

            if (isStor) {
                //setup upload
                if (_rslave == null) {
                	// reset(); already done in finally block
                    throw new NullPointerException();
                }

                List rslaves = Collections.singletonList(_rslave);
                StaticRemoteFile uploadFile = new StaticRemoteFile(rslaves,
                        targetFileName, conn.getUserNull().getName(),
                        conn.getUserNull().getGroup(), 0L,
                        System.currentTimeMillis(), 0L);
                synchronized (this) {
                	uploadFile.setXfertime(-1); // used for new files to be
                								   // uploaded, see getXfertime()
                	_transferFile = targetDir.addFile(uploadFile);
                }
            }

            // setup _transfer

            if (isPort()) {
                try {
                    String index = _rslave.issueConnectToSlave(_portAddress
							.getAddress().getHostAddress(), _portAddress
							.getPort(), _encryptedDataChannel, _SSLHandshakeClientMode);
                    ConnectInfo ci = _rslave.fetchTransferResponseFromIndex(index);
                    synchronized (this) {
                    	_transfer = _rslave.getTransfer(ci.getTransferIndex());
                    }
                } catch (Exception ex) {
                    logger.fatal("rslave=" + _rslave, ex);
                	// reset(); already done in finally block
                    return new Reply(450,
                        ex.getClass().getName() + " from slave: " +
                        ex.getMessage());
                }
            } else if (isPasv()) {
                //_transfer is already set up by doPASV()
            } else {
            	// reset(); already done in finally block
            	if (isStor) {
            		_transferFile.delete();
            	}
                return Reply.RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS;
            }

            {
                PrintWriter out = conn.getControlWriter();
                out.write(new Reply(150,
                        "File status okay; about to open data connection " +
                        (isRetr ? "from " : "to ") + _rslave.getName() + ".").toString());
                out.flush();
            }

            TransferStatus status = null;

            SectionInterface section = conn.getGlobalContext().getSectionManager().lookup(_transferFile.getPath());
            long speedUp = 0, speedDn = 0, avg = 0;

            boolean check, first = true, slowTransfer = false;
            String slowReason = "Slow transfer."; // initialize with something, just in case.

            //transfer
            try {
                //TODO ABORtable transfers
                if (isRetr) {
                    _transfer.sendFile(_transferFile.getPath(), getType(),
                        _resumePosition);

                    speedDn = section.getMinSpeedDn();
                    check = (speedDn == 0 ? false : true);

                    while (true) {
                        status = _transfer.getTransferStatus();

                        if (status.isFinished()) {
                            break;
                        }

                        // Min speed per section
                        if (check) {
                        	_lastCheck = (_lastCheck == 0 ? System.currentTimeMillis() : _lastCheck);
                        	_delay = System.currentTimeMillis() - _lastCheck;
							// Min speed per section, check at most every 10 seconds.
                        	if (first ? _delay >= 30000 : _delay >= 10000) {
                        		boolean cont = checkSpeed(speedDn);
                        		first = false;
                        		if (!cont) {
									avg = status.getTransfered() / (status.getElapsed() / 1000);
									slowTransfer = true;
									slowReason = "Slow transfer: "
											+ Bytes.formatBytes(avg)
											+ "/s too slow for section "
											+ section.getName() + ", at least "
											+ Bytes.formatBytes(speedDn)
											+ "/s required.";
									_transfer.abort(slowReason);
								}
                        	}
                        }

                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e1) {
                        }
                    }
                } else if (isStor) {
                    _transfer.receiveFile(_transferFile.getPath(), getType(),
                        _resumePosition);

                    speedUp = section.getMinSpeedUp();
                    check = (speedUp == 0 ? false : true);

                    while (true) {
                        status = _transfer.getTransferStatus();
                        _transferFile.setLength(status.getTransfered());

                        if (status.isFinished()) {
                            break;
                        }

			// Min speed per section
                        if (check) {
                        	_lastCheck = (_lastCheck == 0 ? System.currentTimeMillis() : _lastCheck);
                        	_delay = System.currentTimeMillis() - _lastCheck;
                        	// Min speed per section, check at most every 10 seconds.
                        	if (first ? _delay >= 30000 : _delay >= 10000) {
                        		boolean cont = checkSpeed(speedUp);
                        		first = false;

                        		if (!cont) {
                        			avg = status.getTransfered() / (status.getElapsed() / 1000);
                        			slowTransfer = true;
									slowReason = "Slow transfer: "
											+ Bytes.formatBytes(avg)
											+ "/s too slow for section "
											+ section.getName() + ", at least "
											+ Bytes.formatBytes(speedUp)
											+ "/s required.";
									_transfer.abort(slowReason);
                        		}
                        	}
                        }

                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e1) {
                        }
                    }
                } else {
                    throw new RuntimeException();
                }
            } catch (IOException ex) {
            	logger.debug("", ex);
                if (ex instanceof TransferFailedException) {
                	// the below chunk makes no sense, we don't process it anywhere
/*                    status = ((TransferFailedException) ex).getStatus();
                    conn.getGlobalContext()
                        .dispatchFtpEvent(new TransferEvent(conn, eventType,
                            _transferFile, conn.getClientAddress(), _rslave,
                            _transfer.getAddress().getAddress(), _type, false));
*/
                    if (isRetr) {
                        conn.getUserNull().updateCredits(-status.getTransfered());
                    }
                }

                Reply reply = null;

                if (isStor) {
                    _transferFile.delete();
                    logger.error("IOException during transfer, deleting file",
                        ex);
                    reply = new Reply(426, "Transfer failed, deleting file");
                } else {
                    logger.error("IOException during transfer", ex);
                    reply = new Reply(426, ex.getMessage());
                }

                // Add reason if aborted for slow transfer
                if (slowTransfer)
                	reply.addComment(slowReason);

                reply.addComment(ex.getMessage());
            	// reset(); already done in finally block
                return reply;
            } catch (SlaveUnavailableException e) {
            	logger.debug("", e);
                Reply reply = null;

                if (isStor) {
                    _transferFile.delete();
                    logger.error("Slave went offline during transfer, deleting file",
                        e);
                    reply = new Reply(426,
                            "Slave went offline during transfer, deleting file");
                } else {
                    logger.error("Slave went offline during transfer", e);
                    reply = new Reply(426,
                            "Slave went offline during transfer");
                }

                reply.addComment(e.getLocalizedMessage());
            	// reset(); already done in finally block
                return reply;
            }

            // TransferThread transferThread = new TransferThread(rslave,
            // transfer);
            // System.err.println("Calling interruptibleSleepUntilFinished");
            //		try {
            //			transferThread.interruptibleSleepUntilFinished();
            //		} catch (Throwable e1) {
            //			e1.printStackTrace();
            //		}
            //		System.err.println("Finished");
            env = new ReplacerEnvironment();
            env.add("bytes", Bytes.formatBytes(status.getTransfered()));
            env.add("speed", Bytes.formatBytes(status.getXferSpeed()) + "/s");
            env.add("seconds", "" + ((float)status.getElapsed() / 1000F));
            env.add("checksum", Checksum.formatChecksum(status.getChecksum()));

            Reply response = new Reply(226,
                    conn.jprintf(DataConnectionHandler.class,
                        "transfer.complete", env));
            synchronized (conn.getGlobalContext()) { // need to synchronize
														// here so only one
				// TransferEvent can be sent at a time
				if (isStor) {
					if (_resumePosition == 0) {
						_transferFile.setCheckSum(status.getChecksum());
					} else {
						// try {
						// checksum = _transferFile.getCheckSumFromSlave();
						// } catch (NoAvailableSlaveException e) {
						// response.addComment(
						// "No available slaves when getting checksum from
						// slave: "
						// + e.getMessage());
						// logger.warn("", e);
						// checksum = 0;
						// } catch (IOException e) {
						// response.addComment(
						// "IO error getting checksum from slave: "
						// + e.getMessage());
						// logger.warn("", e);
						// checksum = 0;
						// }
					}

					_transferFile.setLastModified(System.currentTimeMillis());
					_transferFile.setLength(status.getTransfered());
					_transferFile.setXfertime(status.getElapsed());
				}

				boolean zipscript = zipscript(isRetr, isStor, status
						.getChecksum(), response, targetFileName, targetDir);

				if (zipscript) {
					// transferstatistics
					if (isRetr) {

						float ratio = conn.getGlobalContext().getConfig()
								.getCreditLossRatio(_transferFile,
										conn.getUserNull());

						// updating group stats.
						boolean nostats = conn.getGlobalContext().getConfig().checkPathPermission(
								"nostatsdn", conn.getUserNull(), conn.getCurrentDirectory());

						if (ratio != 0) {
							conn.getUserNull().updateCredits(
									(long) (-status.getTransfered() * ratio));
						}

						if (!nostats) {
							conn.getUserNull().updateDownloadedBytes(
									status.getTransfered());
							conn.getUserNull().updateDownloadedTime(
									status.getElapsed());
							conn.getUserNull().updateDownloadedFiles(1);
						}
					} else {
						// updating group stats.
						boolean nostats = conn.getGlobalContext().getConfig().checkPathPermission(
								"nostatsup", conn.getUserNull(), conn.getCurrentDirectory());

						conn.getUserNull().updateCredits(
								(long) (status.getTransfered() * conn
										.getGlobalContext().getConfig()
										.getCreditCheckRatio(_transferFile,
												conn.getUserNull())));
						if (!nostats) {
							conn.getUserNull().updateUploadedBytes(
									status.getTransfered());
							conn.getUserNull().updateUploadedTime(
									status.getElapsed());
							conn.getUserNull().updateUploadedFiles(1);
						}
					}

					try {
						conn.getUserNull().commit();
					} catch (UserFileException e) {
						logger.warn("", e);
					}
				}

				// Dispatch for both STOR and RETR
				conn.getGlobalContext().dispatchFtpEvent(
						new TransferEvent(conn, eventType, _transferFile, conn
								.getClientAddress(), _rslave, _transfer
								.getAddress().getAddress(), getType()));
				return response;
			}
        } finally {
            reset(conn);
        }
    }

    public void unload() {
    }

    /**
	 * @param isRetr
	 * @param isStor
	 * @param status
	 * @param response
	 * @param targetFileName
	 * @param targetDir
	 *            Returns true if crc check was okay, i.e, if credits should be
	 *            altered
	 */
    private boolean zipscript(boolean isRetr, boolean isStor, long checksum,
        Reply response, String targetFileName,
        LinkedRemoteFileInterface targetDir) {
        // zipscript
        logger.debug("Running zipscript on file " + targetFileName +
            " with CRC of " + checksum);

        if (isRetr) {
            //compare checksum from transfer to cached checksum
            logger.debug("checksum from transfer = " + checksum);

            if (checksum != 0) {
                response.addComment("Checksum from transfer: " +
                    Checksum.formatChecksum(checksum));

                long cachedChecksum;
                cachedChecksum = _transferFile.getCheckSumCached();

                if (cachedChecksum == 0) {
                    _transferFile.setCheckSum(checksum);
                } else if (cachedChecksum != checksum) {
                    response.addComment(
                        "WARNING: checksum from transfer didn't match cached checksum");
                    logger.info("checksum from transfer " +
                        Checksum.formatChecksum(checksum) +
                        "didn't match cached checksum" +
                        Checksum.formatChecksum(cachedChecksum) + " for " +
                        _transferFile.toString() + " from slave " +
                        _rslave.getName(), new Throwable());
                }

                //compare checksum from transfer to checksum from sfv
                try {
                    long sfvChecksum;
					try {
						sfvChecksum = _transferFile.getParentFileNull()
						                                .lookupSFVFile()
						                                .getChecksum(_transferFile.getName());
	                    if (sfvChecksum == checksum) {
	                        response.addComment(
	                            "checksum from transfer matched checksum in .sfv");
	                    } else {
	                        response.addComment(
	                            "WARNING: checksum from transfer didn't match checksum in .sfv");
	                    }
					} catch (FileStillTransferringException e) {
						response
								.addComment("checksum from sfv doesn't exist yet, SFVFile is being uploaded");
					}
                } catch (NoAvailableSlaveException e1) {
                    response.addComment(
                        "slave with .sfv offline, checksum not verified");
                } catch (FileNotFoundException e1) {
                    //continue without verification
                } catch (NoSFVEntryException e1) {
                    //file not found in .sfv, continue
                } catch (IOException e1) {
                    logger.info("", e1);
                    response.addComment("IO Error reading sfv file: " +
                        e1.getMessage());
                }
            } else { // slave has disabled download crc

                //response.addComment("Slave has disabled download checksum");
            }
        } else if (isStor) {
            if (!targetFileName.toLowerCase().endsWith(".sfv")) {
            	//TODO optionally grab checksum from the file on the hard disk on the slave to verify it was written and cen be read back in tact.
                try {
                    long sfvChecksum;
					try {
						sfvChecksum  = targetDir.lookupSFVFile().getChecksum(targetFileName);
                    /* If no exceptions are thrown means that the sfv is avaible and has a entry
                     * for that file.
                     * With this certain, we can assume that files that have CRC32 = 0 either is a
                     * 0byte file (bug!) or checksummed transfers are disabled(size is different
                     * from 0bytes though).                 
                     */

	                    if (checksum == sfvChecksum) {
                    	// Good! transfer checksum matches sfv checksum
	                        response.addComment("checksum match: SLAVE/SFV:" +
	                            Long.toHexString(checksum));
	                    } else if (checksum == 0) {
                    	// Here we have 2 conditions:
	                    	if (_transferFile.length() == 0) {
	                    		// The file has checksum = 0 and the size = 0 
	                    		// then it should be deleted.
	                    		response.addComment("0Byte File, Deleting...");
	                    		_transferFile.delete();
	                    		return false;
	                    	} else {
	                    		// The file has checksum = 0, although the size is != 0,
	                    		// meaning that we are not using checked transfers.
	                            response.addComment(
	                            "checksum match: SLAVE/SFV: DISABLED");
	                    	}
	                    } else {
	                        response.addComment("checksum mismatch: SLAVE: " +
	                            Long.toHexString(checksum) + " SFV: " +
	                            Long.toHexString(sfvChecksum));
	                        response.addComment(" deleting file");
	                        response.setMessage("Checksum mismatch, deleting file");
	                        _transferFile.delete();
	                        return false; // don't modify credits
	                    }
					} catch (FileStillTransferringException e) {
						// Should we really be doing this?
						// Maybe we need a configuration option to allow siteop to control if this condition causes a file to fail zipscript checking.
						logger.info("SFVERROR: checksum (" + Checksum.formatChecksum(checksum) + " ) can not be verified, SFVFile is still being uploaded.  Accepting file (" + _transferFile.toString() + ") anyway.");
						response.addComment("No sfv to compare to (SFVTransferring), file allowed");
					}
                } catch (NoAvailableSlaveException e) {
                	// Again seems kinda wierd, more control is needed.
					logger.info("SFVERROR: checksum (" + Checksum.formatChecksum(checksum) + " ) can not be verified, slave(s) with SFVFile offline.  Accepting file (" + _transferFile.toString() + ") anyway.");
                    response.addComment(
                        "zipscript - SFV unavailable, slave(s) with .sfv file is offline");
                } catch (NoSFVEntryException e) {
                	// This definately seems wrong, why on earth would anyone have zipscript on and allow this?
					logger.info("SFVERROR: checksum (" + Checksum.formatChecksum(checksum) + " ) can not be verified, no entry in SFVFile matching uploaded file.  Accepting file (" + _transferFile.toString() + ") anyway.");
                    response.addComment("zipscript - no entry in sfv for file");
                } catch (IOException e) {
                	// Again, if we can not verify the file, why allow it?  More control is needed.
					logger.info("SFVERROR: checksum (" + Checksum.formatChecksum(checksum) + " ) can not be verified, encountered an I/O error trying to access SFVFile (details: " + e.getMessage() + ").  Accepting file (" + _transferFile.toString() + ") anyway.");
                    response.addComment(
                        "zipscript - SFV unavailable, IO error: " +
                        e.getMessage());
                }
            }
        }

        return true; // modify credits, transfer was okay
    }

	public synchronized void handshakeCompleted(HandshakeCompletedEvent arg0) {
		_handshakeCompleted = true;
		notifyAll();
	}
}
