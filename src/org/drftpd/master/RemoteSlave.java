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
package org.drftpd.master;

import java.beans.DefaultPersistenceDelegate;
import java.beans.ExceptionListener;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.beans.XMLEncoder;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EmptyStackException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Stack;
import java.util.StringTokenizer;

import net.sf.drftpd.DuplicateElementException;
import net.sf.drftpd.FatalException;
import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.event.SlaveEvent;

import org.apache.log4j.Logger;
import org.apache.oro.text.regex.MalformedPatternException;
import org.drftpd.GlobalContext;
import org.drftpd.LightSFVFile;
import org.drftpd.dynamicdata.Key;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.id3.ID3Tag;
import org.drftpd.io.SafeFileOutputStream;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.slave.ConnectInfo;
import org.drftpd.slave.DiskStatus;
import org.drftpd.slave.RemoteIOException;
import org.drftpd.slave.SlaveStatus;
import org.drftpd.slave.Transfer;
import org.drftpd.slave.TransferIndex;
import org.drftpd.slave.TransferStatus;
import org.drftpd.slave.async.AsyncCommand;
import org.drftpd.slave.async.AsyncCommandArgument;
import org.drftpd.slave.async.AsyncResponse;
import org.drftpd.slave.async.AsyncResponseChecksum;
import org.drftpd.slave.async.AsyncResponseDIZFile;
import org.drftpd.slave.async.AsyncResponseDiskStatus;
import org.drftpd.slave.async.AsyncResponseException;
import org.drftpd.slave.async.AsyncResponseID3Tag;
import org.drftpd.slave.async.AsyncResponseMaxPath;
import org.drftpd.slave.async.AsyncResponseRemerge;
import org.drftpd.slave.async.AsyncResponseSFVFile;
import org.drftpd.slave.async.AsyncResponseTransfer;
import org.drftpd.slave.async.AsyncResponseTransferStatus;
import org.drftpd.usermanager.Entity;
import org.drftpd.usermanager.HostMask;
import org.drftpd.usermanager.HostMaskCollection;

/**
 * @author mog
 * @author zubov
 * @version $Id: RemoteSlave.java 1765 2007-08-04 04:14:28Z tdsoul $
 */
public class RemoteSlave implements Runnable, Comparable<RemoteSlave>, Serializable, Entity {
	private static final long serialVersionUID = -6973935289361817125L;
	
	private final String[] transientFields = { "available", "lastDownloadSending",
	"lastUploadReceiving" };

	private static final Logger logger = Logger.getLogger(RemoteSlave.class);

	private transient boolean _isAvailable;

	protected transient int _errors;

	private transient GlobalContext _gctx;

	private transient long _lastDownloadSending = 0;

	protected transient long _lastNetworkError;

	private transient long _lastUploadReceiving = 0;
	
	private transient long _lastResponseReceived = System.currentTimeMillis();
	
	private transient long _lastCommandSent = System.currentTimeMillis();

	private transient int _maxPath;

	private transient String _name;

	private transient DiskStatus _status;

	private HostMaskCollection _ipMasks;

	private Properties _keysAndValues;

	private LinkedList<QueuedOperation> _renameQueue;

	private transient Stack<String> _indexPool;

	private transient HashMap<String, AsyncResponse> _indexWithCommands;

	private transient ObjectInputStream _sin;

	private transient Socket _socket;

	private transient ObjectOutputStream _sout;

	private transient HashMap<TransferIndex, RemoteTransfer> _transfers;

	private long _uptime;

	public RemoteSlave(String name) {
		_name = name;
		_keysAndValues = new Properties();
		_ipMasks = new HostMaskCollection();
		_renameQueue = new LinkedList<QueuedOperation>();
	}

	/**
	 * Used by everything including tests
	 */
	public RemoteSlave(String name, GlobalContext gctx) {
		this(name);
		_gctx = gctx;
		commit();
	}

	public final static Hashtable rslavesToHashtable(Collection rslaves) {
		Hashtable<String, RemoteSlave> map = new Hashtable<String, RemoteSlave>(
				rslaves.size());

		for (Iterator iter = rslaves.iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			map.put(rslave.getName(), rslave);
		}

		return map;
	}

	public void addMask(String mask) throws DuplicateElementException {
		_ipMasks.addMask(mask);
		commit();
	}

	/**
	 * If X # of errors occur in Y amount of time, kick slave offline
	 */
	public final void addNetworkError(SocketException e) {
		// set slave offline if too many network errors
		long errortimeout = Long
				.parseLong(getProperty("errortimeout", "60000")); // one
		// minute

		if (errortimeout <= 0) {
			errortimeout = 60000;
		}

		int maxerrors = Integer.parseInt(getProperty("maxerrors", "5"));

		if (maxerrors < 0) {
			maxerrors = 5;
		}

		_errors -= ((System.currentTimeMillis() - _lastNetworkError) / errortimeout);

		if (_errors < 0) {
			_errors = 0;
		}

		_errors++;
		_lastNetworkError = System.currentTimeMillis();

		if (_errors > maxerrors) {
			setOffline("Too many network errors - " + e.getMessage());
			logger.error("Too many network errors - " + e);
		}
	}

	protected void addQueueDelete(String fileName) {
		addQueueRename(fileName, null);
	}

	protected void addQueueRename(String fileName, String destName) {
		if (isOnline()) {
			throw new IllegalStateException(
					"Slave is online, you cannot queue an operation");
		}
		_renameQueue.add(new QueuedOperation(fileName, destName));
		commit();
	}

	public void setProperty(String name, String value) {
		synchronized (_keysAndValues) {
			_keysAndValues.setProperty(name, value);
			commit();
		}
	}

	public String getProperty(String name, String def) {
		synchronized (_keysAndValues) {
			return _keysAndValues.getProperty(name, def);
		}
	}

	public Properties getProperties() {
		synchronized (_keysAndValues) {
			return (Properties) _keysAndValues.clone();
		}
	}
	
	/** 
	 * Needed in order for this class to be a Bean
	 */
	public void setProperties(Properties keysAndValues) {
		_keysAndValues = keysAndValues;
	}

	public void commit() {
		try {

			XMLEncoder out = new XMLEncoder(new SafeFileOutputStream(
					(getGlobalContext().getSlaveManager().getSlaveFile(this
							.getName()))));
			out.setExceptionListener(new ExceptionListener() {
				public void exceptionThrown(Exception e) {
					logger.warn("", e);
				}
			});
			out.setPersistenceDelegate(Key.class,
					new DefaultPersistenceDelegate(new String[] { "owner",
							"key", "type" }));
			out.setPersistenceDelegate(HostMask.class,
					new DefaultPersistenceDelegate(new String[] { "mask" }));
			out.setPersistenceDelegate(RemoteSlave.class,
					new DefaultPersistenceDelegate(new String[] { "name" }));
			out.setPersistenceDelegate(QueuedOperation.class,
					new DefaultPersistenceDelegate(new String[] { "source", "destination" }));
			try {
				PropertyDescriptor[] pdArr = Introspector.getBeanInfo(
						RemoteSlave.class).getPropertyDescriptors();
				ArrayList<String> transientList = new ArrayList<String>();
				for (int x = 0; x < transientFields.length; x++) {
					transientList.add(transientFields[x]);
				}
				for (int x = 0; x < pdArr.length; x++) {
					if (transientList.contains(pdArr[x].getName())) {
						pdArr[x].setValue("transient", Boolean.TRUE);
					}
				}
			} catch (IntrospectionException e1) {
				logger.error("I don't know what to do here", e1);
				throw new RuntimeException(e1);
			}
			try {
				out.writeObject(this);
			} finally {
				out.close();
			}

			Logger.getLogger(RemoteSlave.class).debug("wrote " + getName());
		} catch (IOException ex) {
			throw new RuntimeException("Error writing slavefile for "
					+ this.getName() + ": " + ex.getMessage(), ex);
		}
	}

	public final int compareTo(RemoteSlave o) {
		return getName().compareTo(o.getName());
	}

	public final boolean equals(Object obj) {
		try {
			return ((RemoteSlave) obj).getName().equals(getName());
		} catch (NullPointerException e) {
			return false;
		}
	}

	public GlobalContext getGlobalContext() {
		return _gctx;
	}

	public final long getLastDownloadSending() {
		return _lastDownloadSending;
	}

	public final long getLastTransfer() {
		return Math.max(getLastDownloadSending(), getLastUploadReceiving());
	}

	public long getLastTransferForDirection(char dir) {
		if (dir == Transfer.TRANSFER_RECEIVING_UPLOAD) {
			return getLastUploadReceiving();
		} else if (dir == Transfer.TRANSFER_SENDING_DOWNLOAD) {
			return getLastDownloadSending();
		} else if (dir == Transfer.TRANSFER_THROUGHPUT) {
			return getLastTransfer();
		} else {
			throw new IllegalArgumentException();
		}
	}

	public final long getLastUploadReceiving() {
		return _lastUploadReceiving;
	}

	public HostMaskCollection getMasks() {
		return _ipMasks;
	}

	public void setMasks(HostMaskCollection masks) {
		_ipMasks = masks;
	}

	/**
	 * Returns the name.
	 */
	public String getName() {
		return _name;
	}

	/**
	 * Returns the RemoteSlave's saved SlaveStatus, can return a status before
	 * remerge() is completed
	 */
	public synchronized SlaveStatus getSlaveStatus()
			throws SlaveUnavailableException {
		if ((_status == null) || !isOnline()) {
			throw new SlaveUnavailableException();
		}
		int throughputUp = 0;
		int throughputDown = 0;
		int transfersUp = 0;
		int transfersDown = 0;
		long bytesReceived;
		long bytesSent;

		synchronized (_transfers) {
			bytesReceived = getReceivedBytes();
			bytesSent = getSentBytes();

			for (Iterator i = _transfers.values().iterator(); i.hasNext();) {
				RemoteTransfer transfer = (RemoteTransfer) i.next();
				switch (transfer.getState()) {
				case Transfer.TRANSFER_RECEIVING_UPLOAD:
					throughputUp += transfer.getXferSpeed();
					bytesReceived += transfer.getTransfered();
					transfersUp += 1;
					break;

				case Transfer.TRANSFER_SENDING_DOWNLOAD:
					throughputDown += transfer.getXferSpeed();
					transfersDown += 1;
					bytesSent += transfer.getTransfered();
					break;

				case Transfer.TRANSFER_UNKNOWN:
				case Transfer.TRANSFER_THROUGHPUT:
					break;

				default:
					throw new FatalException("unrecognized direction - "
							+ transfer.getState() + " for " + transfer);
				}
			}
		}

		return new SlaveStatus(_status, bytesSent, bytesReceived, throughputUp,
				transfersUp, throughputDown, transfersDown);
	}

	public long getSentBytes() {
		return Long.parseLong(getProperty("bytesSent", "0"));
	}

	public long getReceivedBytes() {
		return Long.parseLong(getProperty("bytesReceived", "0"));
	}

	/**
	 * Returns the RemoteSlave's stored SlaveStatus, will not return a status
	 * before remerge() is completed
	 */
	public synchronized SlaveStatus getSlaveStatusAvailable()
			throws SlaveUnavailableException {
		if (isAvailable()) {
			return getSlaveStatus();
		}

		throw new SlaveUnavailableException("Slave is not online");
	}

	public final int hashCode() {
		return getName().hashCode();
	}

	/**
	 * Called when the slave connects
	 */
	private void initializeSlaveAfterThreadIsRunning() throws IOException,
			SlaveUnavailableException {
		commit();
		processQueue();

		_uptime = System.currentTimeMillis();

		String maxPathIndex = issueMaxPathToSlave();
		_maxPath = fetchMaxPathFromIndex(maxPathIndex);
		logger.debug("maxpath was received");

		String remergeIndex = issueRemergeToSlave("/");
		fetchRemergeResponseFromIndex(remergeIndex);
		getGlobalContext().getSlaveManager().putRemergeQueue(
				new RemergeMessage(this));
		setAvailable(true);
		logger.info("Slave added: '" + getName() + "' status: " + _status);
		getGlobalContext().dispatchFtpEvent(
				new SlaveEvent("ADDSLAVE", this));
	}

	/**
	 * @return true if the slave has synchronized its filelist since last
	 *                 connect
	 */
	public synchronized boolean isAvailable() {
		return _isAvailable;
	}

    public long getUptime() {
		return System.currentTimeMillis() - _uptime;
	} 

    public boolean isAvailablePing() {
		if (!isAvailable()) {
			return false;
		}

		try {
			String index = issuePingToSlave();
			fetchResponse(index);
		} catch (SlaveUnavailableException e) {
			setOffline(e);
			return false;
		} catch (RemoteIOException e) {
			setOffline("The slave encountered an IOException while running ping...this is almost not possible");
			return false;
		}

		return isAvailable();
	}

	public void processQueue() throws IOException, SlaveUnavailableException {
		//no for-each loop, needs iter.remove()
		for (Iterator<QueuedOperation> iter = _renameQueue.iterator(); iter
				.hasNext();) {
			QueuedOperation item = iter.next();
			String sourceFile = item.getSource();
			String destFile = item.getDestination();

			if (destFile == null) { // delete
				try {
					fetchResponse(issueDeleteToSlave(sourceFile), 300000);
				} catch (RemoteIOException e) {
					if (!(e.getCause() instanceof FileNotFoundException)) {
						throw (IOException) e.getCause();
					}
				} finally {
					iter.remove();
					commit();
				}
			} else { // rename
				String fileName = destFile
						.substring(destFile.lastIndexOf("/") + 1);
				String destDir = destFile.substring(0, destFile
						.lastIndexOf("/"));
				try {
					fetchResponse(issueRenameToSlave(sourceFile, destDir,
							fileName));
				} catch (RemoteIOException e) {
					if (!(e.getCause() instanceof FileNotFoundException)) {
						throw (IOException) e.getCause();
					}
				} finally {
					iter.remove();
					commit();
				}
			}
		}
	}

	/**
	 * @return true if the mask was removed successfully
	 */
	public final boolean removeMask(String mask) {
		boolean ret = _ipMasks.removeMask(mask);

		if (ret) {
			commit();
		}

		return ret;
	}

	public synchronized void setAvailable(boolean available) {
		_isAvailable = available;
	}

	public final void setLastDirection(char direction, long l) {
		switch (direction) {
		case Transfer.TRANSFER_RECEIVING_UPLOAD:
			setLastUploadReceiving(l);

			return;

		case Transfer.TRANSFER_SENDING_DOWNLOAD:
			setLastDownloadSending(l);

			return;

		default:
			throw new IllegalArgumentException();
		}
	}

	public final void setLastDownloadSending(long lastDownloadSending) {
		_lastDownloadSending = lastDownloadSending;
	}

	public final void setLastUploadReceiving(long lastUploadReceiving) {
		_lastUploadReceiving = lastUploadReceiving;
	}

	/**
	 * Deletes files/directories and waits for the response
	 * Meant to be used if you don't want to utilize asynchronization
	 */
	public void simpleDelete(String path) {
		try {
			fetchResponse(issueDeleteToSlave(path), 300000);
		} catch (RemoteIOException e) {
			if (e.getCause() instanceof FileNotFoundException) {
				return;
			}

			setOffline("IOException deleting file, check logs for specific error");
			addQueueDelete(path);
			logger
					.error(
							"IOException deleting file, file will be deleted when slave comes online",
							e);
		} catch (SlaveUnavailableException e) {
			// Already offline and we ARE successful in deleting the file
			addQueueDelete(path);
		}
	}

	/**
	 * Renames files/directories and waits for the response
	 */
	public void simpleRename(String from, String toDirPath, String toName) {
		String simplePath = null;
		if (toDirPath.endsWith("/")) {
			simplePath = toDirPath + toName;
		} else {
			simplePath = toDirPath + "/" + toName;
		}
		try {
			fetchResponse(issueRenameToSlave(from, toDirPath, toName));
		} catch (RemoteIOException e) {
			setOffline(e);
			addQueueRename(from, simplePath);
		} catch (SlaveUnavailableException e) {
			addQueueRename(from, simplePath);
		}
	}

	public String toString() {
		return moreInfo();
	}

	public static String getSlaveNameFromObjectInput(ObjectInputStream in)
			throws IOException {
		try {
			return (String) in.readObject();
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	public synchronized void connect(Socket socket, ObjectInputStream in,
			ObjectOutputStream out) throws IOException {
		_socket = socket;
		_sout = out;
		_sin = in;
		_indexPool = new Stack<String>();

		for (int i = 0; i < 256; i++) {
			String key = Integer.toHexString(i);

			if (key.length() < 2) {
				key = "0" + key;
			}

			_indexPool.push(key);
		}

		_indexWithCommands = new HashMap<String, AsyncResponse>();
		_transfers = new HashMap<TransferIndex, RemoteTransfer>();
		_errors = 0;
		_lastNetworkError = System.currentTimeMillis();
		start();
		class RemergeThread implements Runnable {
			public void run() {
				try {
					initializeSlaveAfterThreadIsRunning();
				} catch (IOException e) {
					setOffline(e);
				} catch (SlaveUnavailableException e) {
					setOffline(e);
				}
			}
		}

		new Thread(new RemergeThread(), "RemoteSlaveRemerge - " + getName())
				.start();
	}

	private void start() {
		Thread t = new Thread(this);
		t.setName("RemoteSlave - " + getName());
		t.start();
	}

	public long fetchChecksumFromIndex(String index) throws RemoteIOException,
			SlaveUnavailableException {
		return ((AsyncResponseChecksum) fetchResponse(index)).getChecksum();
	}

	public ID3Tag fetchID3TagFromIndex(String index) throws RemoteIOException,
			SlaveUnavailableException {
		return ((AsyncResponseID3Tag) fetchResponse(index)).getTag();
	}

	private synchronized String fetchIndex() throws SlaveUnavailableException {
		while (isOnline()) {
			try {
				return _indexPool.pop();
			} catch (EmptyStackException e) {
				logger
						.error("Too many commands sent, need to wait for the slave to process commands");
			}

			try {
				wait();
			} catch (InterruptedException e1) {
			}
		}

		throw new SlaveUnavailableException("Slave was offline or went offline while fetching an index");
	}

	public int fetchMaxPathFromIndex(String maxPathIndex)
			throws SlaveUnavailableException {
		try {
			return ((AsyncResponseMaxPath) fetchResponse(maxPathIndex))
					.getMaxPath();
		} catch (RemoteIOException e) {
			throw new FatalException(
					"this is not possible, slave had an error processing maxpath...");
		}
	}

	/**
	 * @see fetchResponse(String index, int wait)
	 */
	public AsyncResponse fetchResponse(String index)
			throws SlaveUnavailableException, RemoteIOException {
		return fetchResponse(index, 60 * 1000);
	}

	/**
	 * returns an AsyncResponse for that index and throws any exceptions thrown
	 * on the Slave side
	 */
	public synchronized AsyncResponse fetchResponse(String index, int wait)
			throws SlaveUnavailableException, RemoteIOException {
		long total = System.currentTimeMillis();

		while (isOnline() && !_indexWithCommands.containsKey(index)) {
			try {
				wait(1000);

				// will wait a maximum of 1000 milliseconds before waking up
			} catch (InterruptedException e) {
			}

			if ((wait != 0) && ((System.currentTimeMillis() - total) >= wait)) {
				setOffline("Slave has taken too long while waiting for reply "
						+ index);
			}
		}

		if (!isOnline()) {
			throw new SlaveUnavailableException(
					"Slave went offline while processing command");
		}

		AsyncResponse rar = _indexWithCommands.remove(index);
		_indexPool.push(index);
		notifyAll();

		if (rar instanceof AsyncResponseException) {
			Throwable t = ((AsyncResponseException) rar).getThrowable();

			if (t instanceof IOException) {
				throw new RemoteIOException((IOException) t);
			}

			logger
					.error(
							"Exception on slave that is unable to be handled by the master",
							t);
			setOffline("Exception on slave that is unable to be handled by the master");
			throw new SlaveUnavailableException(
					"Exception on slave that is unable to be handled by the master");
		}
		return rar;
	}

   	public String fetchDIZFileFromIndex(String index)
            throws RemoteIOException, SlaveUnavailableException {
		return ((AsyncResponseDIZFile) fetchResponse(index)).getDIZ();
	}

	public LightSFVFile fetchSFVFileFromIndex(String index)
			throws RemoteIOException, SlaveUnavailableException {
		return ((AsyncResponseSFVFile) fetchResponse(index)).getSFV();
	}

	public synchronized String getPASVIP() throws SlaveUnavailableException {
		if(!isOnline()) throw new SlaveUnavailableException();
        return getProperty("pasv_addr", _socket.getInetAddress().getHostAddress());
	}

	public int getPort() {
		return _socket.getPort();
	}

	public synchronized boolean isOnline() {
		return ((_socket != null) && _socket.isConnected());
	}

	/**
	 * 
	 * @param string
	 * @return
	 * @throws SlaveUnavailableException
	 */
	public String issueChecksumToSlave(String string)
			throws SlaveUnavailableException {
		String index = fetchIndex();
		sendCommand(new AsyncCommandArgument(index, "checksum", string));

		return index;
	}

	public String issueConnectToSlave(String ip, int port, 
			boolean encryptedDataChannel, boolean useSSLClientHandshake) throws SlaveUnavailableException {
		String index = fetchIndex();
		sendCommand(new AsyncCommandArgument(index, "connect", ip + ":" + port
				+ "," + encryptedDataChannel + "," + useSSLClientHandshake));

		return index;
	}

	/**
	 * @return String index, needs to be used to fetch the response
	 */
	public String issueDeleteToSlave(String sourceFile)
			throws SlaveUnavailableException {
		String index = fetchIndex();
		sendCommand(new AsyncCommandArgument(index, "delete", sourceFile));

		return index;
	}

	public String issueID3TagToSlave(String path)
			throws SlaveUnavailableException {
		String index = fetchIndex();
		sendCommand(new AsyncCommandArgument(index, "id3tag", path));

		return index;
	}
	
	public String issueListenToSlave(boolean isSecureTransfer,boolean useSSLClientMode)
			throws SlaveUnavailableException {
		String index = fetchIndex();
		sendCommand(new AsyncCommandArgument(index, "listen", ""
				+ isSecureTransfer + ":" +useSSLClientMode));

		return index;
	}

	public String issueMaxPathToSlave() throws SlaveUnavailableException {
		String index = fetchIndex();
		sendCommand(new AsyncCommand(index, "maxpath"));

		return index;
	}

	private String issuePingToSlave() throws SlaveUnavailableException {
		String index = fetchIndex();
		sendCommand(new AsyncCommand(index, "ping"));

		return index;
	}

	public String issueReceiveToSlave(String name, char c, long position,
			TransferIndex tindex) throws SlaveUnavailableException {
		String index = fetchIndex();
		sendCommand(new AsyncCommandArgument(index, "receive", c + ","
				+ position + "," + tindex + "," + name));

		return index;
	}

	public String issueRenameToSlave(String from, String toDirPath,
			String toName) throws SlaveUnavailableException {
		if (toDirPath.length() == 0) { // needed for files in root
			toDirPath = "/";
		}
		String index = fetchIndex();
		sendCommand(new AsyncCommandArgument(index, "rename", from + ","
				+ toDirPath + "," + toName));

		return index;
	}

        public String issueDIZFileToSlave(LinkedRemoteFileInterface file)
	        throws SlaveUnavailableException {
	    String index    = fetchIndex();
	    AsyncCommand ac = new AsyncCommandArgument(index, "dizfile",
						       file.getPath());

	    sendCommand(ac);
	    return index;
	}

	public String issueSFVFileToSlave(String path)
			throws SlaveUnavailableException {
		String index = fetchIndex();
		AsyncCommand ac = new AsyncCommandArgument(index, "sfvfile", path);
		sendCommand(ac);

		return index;
	}

	public String issueStatusToSlave() throws SlaveUnavailableException {
		String index = fetchIndex();
		sendCommand(new AsyncCommand(index, "status"));

		return index;
	}

	public String moreInfo() {
		try {
			return getName() + ":address=[" + getPASVIP() + "]port=["
					+ Integer.toString(getPort()) + "]";
		} catch (SlaveUnavailableException e) {
			return getName()+":offline";
		}
	}

	public void run() {
		logger.debug("Starting RemoteSlave for " + getName());

		try {
			String pingIndex = null;
			while (isOnline()) {
				AsyncResponse ar = null;

				try {
					ar = readAsyncResponse();
					_lastResponseReceived = System.currentTimeMillis();
				} catch (SlaveUnavailableException e3) {
					// no reason for slave thread to be running if the slave is
					// not online
					return;
				} catch (SocketTimeoutException e) {
					// handled below
				}
				
				if (pingIndex == null
						&& ((getActualTimeout() / 2 < (System
								.currentTimeMillis() - _lastResponseReceived)) || (getActualTimeout() / 2 < (System
								.currentTimeMillis() - _lastCommandSent)))) {
					pingIndex = issuePingToSlave();
				} else if (getActualTimeout() < (System.currentTimeMillis() - _lastResponseReceived)) {
					setOffline("Slave seems to have gone offline, have not received a response in "
							+ (System.currentTimeMillis() - _lastResponseReceived)
							+ " milliseconds");
					throw new SlaveUnavailableException();
				}
				
				if (ar == null) {
					continue;
				}

				synchronized (this) {
					if (!(ar instanceof AsyncResponseRemerge) && !(ar instanceof AsyncResponseTransferStatus)) {
						logger.debug("Received: " + ar);
					}

					if (ar instanceof AsyncResponseTransfer) {
						AsyncResponseTransfer art = (AsyncResponseTransfer) ar;
						addTransfer((art.getConnectInfo().getTransferIndex()),
								new RemoteTransfer(art.getConnectInfo(), this));
					}

					if (ar.getIndex().equals("Remerge")) {
						getGlobalContext().getSlaveManager().putRemergeQueue(
								new RemergeMessage((AsyncResponseRemerge) ar,
										this));
					} else if (ar.getIndex().equals("DiskStatus")) {
						_status = ((AsyncResponseDiskStatus) ar)
								.getDiskStatus();
					} else if (ar.getIndex().equals("TransferStatus")) {
						TransferStatus ats = ((AsyncResponseTransferStatus) ar)
								.getTransferStatus();
						RemoteTransfer rt = null;

						try {
							rt = getTransfer(ats.getTransferIndex());
						} catch (SlaveUnavailableException e1) {
							
							// no reason for slave thread to be running if the
							// slave is not online
							return;
						}

						rt.updateTransferStatus(ats);

						if (ats.isFinished()) {
							removeTransfer(ats.getTransferIndex());
						}
					} else {
						_indexWithCommands.put(ar.getIndex(), ar);
						if (pingIndex != null && pingIndex.equals(ar.getIndex())) {
							fetchResponse(pingIndex);
							pingIndex = null;
						} else {
							notifyAll();
						}
					}
				}
			}
		} catch (Throwable e) {
			setOffline("error: " + e.getMessage());
			logger.error("", e);
		}
	}

	private int getActualTimeout() {
		return Integer.parseInt(getProperty("timeout", Integer.toString(SlaveManager.actualTimeout)));
	}

	private synchronized void removeTransfer(TransferIndex transferIndex) {
		RemoteTransfer transfer = null;
		synchronized (_transfers) {
			transfer = _transfers.remove(transferIndex);
		}
		if (transfer == null) {
			throw new IllegalStateException("there is a bug in code");
		}
		if (transfer.getState() == Transfer.TRANSFER_RECEIVING_UPLOAD) {
			addReceivedBytes(transfer.getTransfered());
		} else if (transfer.getState() == Transfer.TRANSFER_SENDING_DOWNLOAD) {
			addSentBytes(transfer.getTransfered());
		} // else, we don't care
	}

	private void addSentBytes(long transfered) {
		addBytes("bytesSent", transfered);
	}

	private void addBytes(String field, long transfered) {
		setProperty(field, Long.toString(Long.parseLong(getProperty(field,"0"))+transfered));
	}

	private void addReceivedBytes(long transfered) {
		addBytes("bytesReceived", transfered);
	}

	public void setOffline(String reason) {
		logger.debug("setOffline() " + reason);
		setOfflineReal(reason);
	}

	private final synchronized void setOfflineReal(String reason) {

		if (_socket != null) {
			setProperty("lastOnline", Long.toString(System.currentTimeMillis()));
			try {
				_socket.close();
			} catch (IOException e) {
			}
			_socket = null;
		}
		_sin = null;
		_sout = null;
		_indexPool = null;
		_indexWithCommands = null;
		_transfers = null;
		_maxPath = 0;
		_status = null;
		_uptime = 0;

		if (_isAvailable) {
			getGlobalContext().dispatchFtpEvent(
					new SlaveEvent("DELSLAVE", reason, this));
		}

		setAvailable(false);
	}

	public void setOffline(Throwable t) {
		logger.info("setOffline()", t);

		if (t.getMessage() == null) {
			setOfflineReal("No Message");
		} else {
			setOfflineReal(t.getMessage());
		}
	}

	/**
	 * fetches the next AsyncResponse, if IOException is encountered, the slave
	 * is setOffline() and the Exception is thrown
	 * 
	 * @throws SlaveUnavailableException
	 * @throws SocketTimeoutException 
	 */
	private AsyncResponse readAsyncResponse() throws SlaveUnavailableException, SocketTimeoutException {
		Object obj = null;
		while (true) {
			try {
				obj = _sin.readObject();
			} catch (ClassNotFoundException e) {
				logger.error("ClassNotFound reading AsyncResponse", e);
				setOffline("ClassNotFound reading AsyncResponse");
				throw new SlaveUnavailableException(
						"Slave is unavailable - Class Not Found");
			} catch (SocketTimeoutException e) {
				// don't want this to be caught by IOException below
				throw e;
			} catch (IOException e) {
				logger.error("IOException reading AsyncResponse", e);
				setOffline("IOException reading AsyncResponse");
				throw new SlaveUnavailableException(
						"Slave is unavailable - IOException");
			}
			if (obj != null) {
				if (obj instanceof AsyncResponse) {
					return (AsyncResponse) obj;
				}
				logger.error("Throwing away an unexpected class - "
						+ obj.getClass().getName() + " - " + obj);
			}
		}
	}

	public void issueAbortToSlave(TransferIndex transferIndex, String reason)
			throws SlaveUnavailableException {
		if (reason == null) {
			reason = "null";
		}
		sendCommand(new AsyncCommandArgument("abort", "abort", transferIndex
				.toString() + "," + reason));
	}

	public ConnectInfo fetchTransferResponseFromIndex(String index)
			throws RemoteIOException, SlaveUnavailableException {
		AsyncResponseTransfer art = (AsyncResponseTransfer) fetchResponse(index);

		return art.getConnectInfo();
	}

	/**
	 * Will not set a slave offline, it is the job of the calling thread to decide to do this
	 */
	private synchronized void sendCommand(AsyncCommand rac)
			throws SlaveUnavailableException {
		if (rac == null) {
			throw new NullPointerException();
		}

		if (!isOnline()) {
			throw new SlaveUnavailableException();
		}

		try {
			_sout.writeObject(rac);
			_sout.flush();
			_sout.reset();
		} catch (IOException e) {
			logger.error("error in sendCommand()", e);
			throw new SlaveUnavailableException(
					"error sending command (exception already handled)", e);
		}
		_lastCommandSent = System.currentTimeMillis();
	}

	public String issueSendToSlave(String name, char c, long position,
			TransferIndex tindex) throws SlaveUnavailableException {
		String index = fetchIndex();
		sendCommand(new AsyncCommandArgument(index, "send", c + "," + position
				+ "," + tindex + "," + name));

		return index;
	}

	public String issueRemergeToSlave(String path)
			throws SlaveUnavailableException {
		String index = fetchIndex();
		sendCommand(new AsyncCommandArgument(index, "remerge", path));

		return index;
	}

	public void fetchRemergeResponseFromIndex(String index) throws IOException,
			SlaveUnavailableException {
		try {
			fetchResponse(index, 0);
		} catch (RemoteIOException e) {
			throw (IOException) e.getCause();
		}
	}

	public boolean checkConnect(Socket socket) throws MalformedPatternException {
		return getMasks().check(socket);
	}

	public String getProperty(String key) {
		synchronized (_keysAndValues) {
			return _keysAndValues.getProperty(key);
		}
	}

	public synchronized void addTransfer(TransferIndex transferIndex,
			RemoteTransfer transfer) {
		if (!isOnline()) {
			return;
		}

		synchronized (_transfers) {
			_transfers.put(transferIndex, transfer);
		}
	}

	public synchronized RemoteTransfer getTransfer(TransferIndex transferIndex)
			throws SlaveUnavailableException {
		if (!isOnline()) {
			throw new SlaveUnavailableException("Slave is not online");
		}

		synchronized (_transfers) {
			RemoteTransfer ret = _transfers.get(transferIndex);
			if (ret == null)
				throw new FatalException("there is a bug somewhere in code, tried to fetch a transfer index that doesn't exist - " + transferIndex);
			return ret;
		}
	}
	
	public synchronized Collection<RemoteTransfer> getTransfers() throws SlaveUnavailableException {
		if (!isOnline()) {
			throw new SlaveUnavailableException("Slave is not online");
		}
		synchronized (_transfers) {
			return Collections.unmodifiableCollection(new ArrayList<RemoteTransfer>(_transfers.values()));
		}
	}

	public boolean isMemberOf(String string) {
		StringTokenizer st = new StringTokenizer(getProperty("keywords", ""),
				" ");

		while (st.hasMoreElements()) {
			if (st.nextToken().equals(string)) {
				return true;
			}
		}

		return false;
	}

	public void init(GlobalContext globalContext) {
		_gctx = globalContext;
	}

	public LinkedList<QueuedOperation> getRenameQueue() {
		return _renameQueue;
	}
	public void setRenameQueue(LinkedList<QueuedOperation> renameQueue) {
		_renameQueue = renameQueue;
	}
	
	public void shutdown() {
		try {
			sendCommand(new AsyncCommand("shutdown", "shutdown gracefully"));
			setOfflineReal("shutdown gracefully");
		} catch (SlaveUnavailableException e) {
		}
	}

	public long getLastTimeOnline() {
		if (isOnline()) {
			return System.currentTimeMillis();
		}
		String value = getProperty("lastOnline");
		// if (value == null) Slave has never been online
		return Long.parseLong(value == null ? "0" : value);
	}

	public String removeProperty(String key) throws KeyNotFoundException {
		synchronized (_keysAndValues) {
			if (getProperty(key) == null) throw new KeyNotFoundException();
			String value = (String) _keysAndValues.remove(key);
			commit();
			return value;
		}
	}
}
