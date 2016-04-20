/*******************************************************************************
 * Copyright (c) 2013, 2016 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.rtc;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;

import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.scm.client.IWorkspaceConnection;
import com.ibm.team.scm.client.IWorkspaceManager;
import com.ibm.team.scm.client.SCMPlatform;
import com.ibm.team.scm.client.internal.RepositoryItemProvider;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.IWorkspaceHandle;
import com.ibm.team.scm.common.dto.IWorkspaceSearchCriteria;
import com.ibm.team.scm.common.internal.ChangeHistoryHandle;
import com.ibm.team.scm.common.internal.ComponentEntry;

/**
 * Provides utility methods to retrieve information on a RTC SCM Streams and Workspaces
 */
@SuppressWarnings("restriction")
public class RTCWorkspaceUtils {
	private static final String [] hexToStringMap = {"0","1","2","3","4", "5", "6", "7","8", "9","a", "b", "c", "d", "e" ,"f"};
	private static RTCWorkspaceUtils instance = null;
    private static final Logger LOGGER = Logger.getLogger(RTCWorkspaceUtils.class.getName());
    private static final String MD5_ALG = "MD5"; //$NON-NLS1$

	static {
		instance = new RTCWorkspaceUtils();
	}
	
	private RTCWorkspaceUtils() {
		
	}
	
	/**
	 * The singleton
	 */
	public static RTCWorkspaceUtils getInstance() {
		return instance;
	}
	
	/**
	 * Deletes a given {@link IWorkspace}. 
	 * <br><br>
	 * <b>Note:</b> Doesn't throw any exception if deletion fails.
	 * 
	 * @param workspace - the workspace to delete
	 * @param repository
	 * @param progress
	 * @param clientLocale
	 */
	public void delete(IWorkspace workspace, ITeamRepository repository, IProgressMonitor progress, IConsoleOutput listener, Locale clientLocale) {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		LOGGER.finest("RTCWorkspaceUtils.delete : Enter");
		try {
			if (workspace == null) {
				return;
			}
			IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repository);
			workspaceManager.deleteWorkspace(workspace, monitor.newChild(100));
		} catch (TeamRepositoryException exp) {
			String logMessage = Messages.get(clientLocale).RTCWorkspaceUtils_cannot_delete_workspace(workspace.getName(), exp.getMessage()); 
			listener.log(logMessage, exp);
			if (LOGGER.isLoggable(Level.WARNING)) {
				LOGGER.warning("RTCWorkspaceUtils.deleteWorkspace : Unable to delete temporary workspace '" + workspace.getName() + "'. Log message is " + logMessage);
			}
		}
		finally {
			monitor.done();
		}
	}

	/**
	 * Given a build stream name, give the {@link IWorkspaceHandle} to the corresponding stream item in repository.
	 *
	 * @param buildStream - the name of the stream
	 * @param repository - the repository in which the stream is to be found
	 * @param progress
	 * @param clientLocale
	 * @return a {@link IWorkspaceHandle} to the corresponding build stream item, if it is found in the repository.
	 * @throws TeamRepositoryException - If there is some other error while fetching the build stream 
	 * @throws RTCConfigurationException - If there is no stream with the given name or multiple streams with the same name 
	 */
	public IWorkspaceHandle getBuildStream (String buildStream, ITeamRepository repository, IProgressMonitor progress, 
											Locale clientLocale) throws TeamRepositoryException, RTCConfigurationException {
		
		SubMonitor monitor = SubMonitor.convert(progress, 100);

		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repository);
		IWorkspaceSearchCriteria searchCriteria = IWorkspaceSearchCriteria.FACTORY
				.newInstance().setExactName(buildStream)
				.setKind(IWorkspaceSearchCriteria.STREAMS);
		List<IWorkspaceHandle> workspaceHandles = workspaceManager.findWorkspaces(searchCriteria, 2, monitor.newChild(75));
		if (workspaceHandles.size() > 1) {
			throw new RTCConfigurationException(Messages.get(clientLocale).RepositoryConnection_stream_name_not_unique(buildStream));
		}
		if (workspaceHandles.size() == 0) {
			throw new RTCConfigurationException(Messages.get(clientLocale).RepositoryConnection_stream_not_found(buildStream));
		}
		return workspaceHandles.get(0);
	}
	
	/**
	 * Given a build stream name, return its UUID
	 * 
	 * @param buildStream - the name of the build stream.
	 * @param repository - the repository in which the stream will be found
	 * @param progress
	 * @param clientLocale
	 * @return a {@link String} representation of the build stream's UUID
	 * @throws TeamRepositoryException - If there is some other error while fetching the build stream 
	 * @throws RTCConfigurationException - If there is no stream with the given name or multiple streams with the same name 
	 */
	public String getBuildStreamUUID (String buildStream, ITeamRepository repository, IProgressMonitor progress,
										Locale clientLocale) throws TeamRepositoryException, RTCConfigurationException {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		IWorkspaceHandle workspaceHandle = getBuildStream(buildStream, repository, monitor.newChild(75), clientLocale);
        return workspaceHandle.getItemId().getUuidValue();
	}

	/**
	 * Given a workspace handle, returns a {@link BigInteger} representing the overall state of the workspace.
	 * The overall state of the workspace is a combination of the states of its components.
	 * This number can be used to compare the overall states of a workspace.
	 * @param repository
	 * @param workspaceHandle
	 * @param progress
	 * @return
	 * @throws TeamRepositoryException
	 * @throws NoSuchAlgorithmException
	 * @throws IOException
	 */
	public BigInteger getDigestNumber(ITeamRepository repository, IWorkspaceHandle workspaceHandle, IProgressMonitor progress) throws TeamRepositoryException, NoSuchAlgorithmException, IOException {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		String digest = getDigest(repository, workspaceHandle, monitor);
		return getDigestNumber(digest);
	}

	/**
	 * Given a workspace handle, returns a {@link String} representing the overall state of the workspace.
	 * The string is a hexadecimal number. The overall state of the workspace is a combination of the states 
	 * of each component. This number can be used to compare the overall states of a workspace.
	 * 
	 * @param repository
	 * @param workspaceHandle
	 * @param progress
	 * @return a {@link String} that represents the state of the workspace or stream 
	 * @throws TeamRepositoryException
	 * @throws NoSuchAlgorithmException
	 * @throws IOException
	 */
	public String getDigest(ITeamRepository repository, IWorkspaceHandle workspaceHandle, IProgressMonitor progress) throws TeamRepositoryException, NoSuchAlgorithmException, IOException {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		long timeBegin = System.currentTimeMillis();
		RepositoryItemProvider provider = new RepositoryItemProvider(repository);
		final ArrayList<ComponentEntry> compEntries = new ArrayList<ComponentEntry>();
		compEntries.addAll(provider.fetchComponentEntriesFor(workspaceHandle, monitor.newChild(80)));
		sort(compEntries);
		String digest = getDigest(compEntries);
		long diff = System.currentTimeMillis() - timeBegin;
		if (LOGGER.isLoggable(Level.FINEST)) {
			LOGGER.finest("GetDigest took " + ((double)diff/1000.00) + " seconds");
		}
		return digest;
	}
	
	/**
	 * Given a string digest in hexadecimal representation, returns a {@link BigInteger}
	 * @param hexDigest - the digest in hexadecimal form
	 * @return a {@link BigInteger} which is a numerical value of string digest
	 * @throws NumberFormatException if the given string is not in hexadecimal format
	 */
	public BigInteger getDigestNumber(String hexDigest) throws NumberFormatException {
		return new BigInteger(hexDigest, 16);
	}
	
	private String getDigest(final ArrayList<ComponentEntry> compEntries) throws IOException, NoSuchAlgorithmException {
		MessageDigest d = MessageDigest.getInstance(MD5_ALG);
		InputStream s = new InputStream() {
			Stack<Byte> currentItem = new Stack<Byte>();
			
			@Override
			public int read() throws IOException {
				if (currentItem.empty()) {
					try {
						ChangeHistoryHandle changeHistory = compEntries.remove(0).getChangehistory();
						while (changeHistory == null) {
							changeHistory = compEntries.remove(0).getChangehistory();
						}
						byte[] itemBytes = changeHistory.getItemId().getUuidValue().getBytes(Charset.forName("UTF-8"));
						for (int i = itemBytes.length-1 ; i >= 0; i--) {
							currentItem.push(itemBytes[i]);
						}
					}
					catch (IndexOutOfBoundsException exp) {
						return -1;
					}
				}
				return currentItem.pop();
			}
		};
		byte [] buf = new byte[4096];
		int numRead = s.read(buf);
		while (numRead != -1) {
			d.update(buf, 0, numRead);
			numRead = s.read(buf);
		}
		s.close();
		byte[] digest = d.digest();
		StringBuffer streamDataHashS = new StringBuffer(2048);
		for (byte dC : digest) {
			// Convert to unsigned integer
			int val = dC & 0xFF;
			int upperByte = (val & 0xF0) >> 4;
			int lowerByte = val & 0x0F;
			streamDataHashS.append(hexToStringMap[upperByte]);
			streamDataHashS.append(hexToStringMap[lowerByte]);
	    }
		LOGGER.finer("Stream's data hash in polling is " +  streamDataHashS.toString());
		return streamDataHashS.toString();
	}

	private void sort(ArrayList<ComponentEntry> compEntries) {
		Collections.sort(compEntries, new Comparator<ComponentEntry>() {
			@Override
			public int compare(ComponentEntry o1, ComponentEntry o2) {
				if (o1 == null && o2 == null) {
					return 0;
				}
				if (o2 == null) {
					return 1;
				}
				if (o1 == null) {
					return -1;
				}
				// Sort based on component item ids which are guarenteed to be unique inside a given stream or workspace
				return o1.getComponent().getItemId().compareTo(o2.getComponent().getItemId());
			}
		});
	}
}
