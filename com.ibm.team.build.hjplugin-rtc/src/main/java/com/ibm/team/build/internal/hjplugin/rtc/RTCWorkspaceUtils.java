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
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubMonitor;

import com.ibm.team.process.client.IProcessClientService;
import com.ibm.team.process.common.IProcessArea;
import com.ibm.team.process.common.IProcessItem;
import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.process.common.ITeamArea;
import com.ibm.team.process.common.ProcessCommon;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.internal.ItemManager;
import com.ibm.team.repository.common.IItem;
import com.ibm.team.repository.common.IItemHandle;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.scm.client.IWorkspaceManager;
import com.ibm.team.scm.client.SCMPlatform;
import com.ibm.team.scm.client.internal.RepositoryItemProvider;
import com.ibm.team.scm.common.IBaselineSet;
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
	 * @param processAreaName - the name of the owning project or team area
	 * @param streamName - the name of the stream
	 * @param repository - the repository in which the stream is to be found
	 * @param progress
	 * @param clientLocale
	 * @return a {@link IWorkspaceHandle} to the corresponding build stream item, if it is found in the repository.
	 * @throws TeamRepositoryException - If there is some other error while fetching the build stream
	 * @throws RTCConfigurationException - If there is no stream with the given name or multiple streams with the same
	 *             name
	 * @throws URISyntaxException
	 * @throws UnsupportedEncodingException 
	 */
	public IWorkspaceHandle getStream(String processAreaName, String streamName, ITeamRepository repository, IProgressMonitor progress,
			Locale clientLocale) throws TeamRepositoryException, RTCConfigurationException, UnsupportedEncodingException, URISyntaxException {
		

		SubMonitor monitor = SubMonitor.convert(progress, 100);

		// first ensure that the owning process area exists, if given
		IProcessArea owningProjectOrTeamArea = null;
		if (Utils.fixEmptyAndTrim(processAreaName) != null) {
			owningProjectOrTeamArea = getProcessAreaByName(processAreaName, repository, monitor.newChild(50), clientLocale);
		}
		monitor.setWorkRemaining(50);
		return getStream(processAreaName, owningProjectOrTeamArea, streamName, repository, monitor.newChild(50), clientLocale);
	}
	
	/**
	 * Given a build stream name, give the {@link IWorkspaceHandle} to the corresponding stream item in repository.
	 * 
	 * @param processAreaPath - the name of the owning project area or path of the team area
	 * @param owningProjectOrTeamArea - reference to the owning project or team area. Though, having both the name of
	 *            the owning project or team area and the reference is redundant, it optimizes scenarios where the
	 *            caller has already resolved the owning process area. We need the name of the process area as it comes
	 *            handy in constructing error messages, especially for team area we need to show the path of the team
	 *            area; constructing it from the reference is not straight forward, so we use the value provided by the
	 *            user in the configuration.
	 * @param streamName - the name of the stream
	 * @param repository - the repository in which the stream is to be found
	 * @param progress
	 * @param clientLocale
	 * @return a {@link IWorkspaceHandle} to the corresponding build stream item, if it is found in the repository.
	 * @throws TeamRepositoryException - If there is some other error while fetching the build stream
	 * @throws RTCConfigurationException - If there is no stream with the given name or multiple streams with the same
	 *             name
	 * @throws URISyntaxException
	 * @throws UnsupportedEncodingException
	 */
	public IWorkspaceHandle getStream(String processAreaPath, IProcessArea owningProjectOrTeamArea, String streamName, ITeamRepository repository,
			IProgressMonitor progress, Locale clientLocale) throws TeamRepositoryException, RTCConfigurationException, UnsupportedEncodingException,
			URISyntaxException {

		SubMonitor monitor = SubMonitor.convert(progress, 100);

		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repository);
		IWorkspaceSearchCriteria searchCriteria = IWorkspaceSearchCriteria.FACTORY.newInstance().setExactName(streamName) // streamName
				.setKind(IWorkspaceSearchCriteria.STREAMS); // search for streams
		if (owningProjectOrTeamArea != null) {
			searchCriteria = searchCriteria.setExactOwnerName(owningProjectOrTeamArea.getName()); // process Area
		}
				
		List<IWorkspaceHandle> workspaceHandles = workspaceManager.findWorkspaces(searchCriteria, 2, monitor);
		if (workspaceHandles.size() > 1) {
			if (owningProjectOrTeamArea != null) {
				if (owningProjectOrTeamArea instanceof IProjectArea) {
					throw new RTCConfigurationException(Messages.get(clientLocale).RepositoryConnection_stream_name_not_unique_pa(streamName,
							processAreaPath));
				} else {
					throw new RTCConfigurationException(Messages.get(clientLocale).RepositoryConnection_stream_name_not_unique_ta(streamName,
							processAreaPath));
				}
			} else {
				throw new RTCConfigurationException(Messages.get(clientLocale).RepositoryConnection_stream_name_not_unique(streamName));
			}
		}
		if (workspaceHandles.size() == 0) {
			if (owningProjectOrTeamArea != null) {
				if (owningProjectOrTeamArea instanceof IProjectArea) {
					throw new RTCConfigurationException(Messages.get(clientLocale).RepositoryConnection_stream_not_found_pa(streamName,
							processAreaPath));
				} else {
					throw new RTCConfigurationException(Messages.get(clientLocale).RepositoryConnection_stream_not_found_ta(streamName,
							processAreaPath));
				}
			} else {
				throw new RTCConfigurationException(Messages.get(clientLocale).RepositoryConnection_stream_not_found(streamName));
			}
		}
		return workspaceHandles.get(0);
	}
	
	/**
	 * Given a build stream name, return its UUID
	 * 
	 * @param processAreaName - the name of the owning project or team area
	 * @param streamName - the name of the stream.
	 * @param repository - the repository in which the stream will be found
	 * @param progress
	 * @param clientLocale
	 * @return a {@link String} representation of the build stream's UUID
	 * @throws TeamRepositoryException - If there is some other error while fetching the build stream
	 * @throws RTCConfigurationException - If there is no stream with the given name or multiple streams with the same
	 *             name
	 * @throws URISyntaxException
	 * @throws UnsupportedEncodingException
	 */
	public String getStreamUUID(String processAreaName, String streamName, ITeamRepository repository, IProgressMonitor progress, 
			Locale clientLocale) throws TeamRepositoryException, RTCConfigurationException, UnsupportedEncodingException, URISyntaxException {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		IWorkspaceHandle workspaceHandle = getStream(processAreaName, streamName, repository, monitor.newChild(75), clientLocale);
		return workspaceHandle.getItemId().getUuidValue();
	}
	
	/**
	 * Returns the repository workspace with the given name. The workspace connection can
	 * be to a workspace or a stream. If there is more than 1 repository workspace with the name
	 * it is an error.  If there are no repository workspaces with the name it is an error.
	 * 
	 * @param workspaceName The name of the workspace. Never <code>null</code>
	 * @param progress A progress monitor to check for cancellation with (and mark progress).
	 * @param clientLocale The locale of the requesting client
	 * @return The workspace connection for the workspace. Never <code>null</code>
	 * @throws Exception if an error occurs
	 */
	public IWorkspaceHandle getWorkspace(String workspaceName, ITeamRepository repository, IProgressMonitor progress, Locale clientLocale)
			throws Exception {
		SubMonitor monitor = SubMonitor.convert(progress, 100);

		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repository);

		IWorkspaceSearchCriteria searchCriteria = IWorkspaceSearchCriteria.FACTORY.newInstance().setExactName(workspaceName)
				.setKind(IWorkspaceSearchCriteria.WORKSPACES);
		List<IWorkspaceHandle> workspaceHandles = workspaceManager.findWorkspaces(searchCriteria, 2, monitor);
		if (workspaceHandles.size() > 1) {
			throw new RTCConfigurationException(Messages.get(clientLocale).RepositoryConnection_name_not_unique(workspaceName));
		}
		if (workspaceHandles.size() == 0) {
			throw new RTCConfigurationException(Messages.get(clientLocale).RepositoryConnection_workspace_not_found(workspaceName));
		}
		return workspaceHandles.get(0);
	}
	
	/**
	 * Returns a {@link IWorkspace} for a given workspaceUUID
	 * 
	 * @param repository - the Jazz repository connection
	 * @param workspaceUUID - the UUID of the workspace
	 * @param progress - progress monitor
	 * @param clientLocale - locale of the client
	 * @return a {@link IWorkspace}
	 * @throws TeamRepositoryException
	 */
	public IWorkspace getWorkspace(UUID workspaceUUID, ITeamRepository repository, IProgressMonitor progress, Locale clientLocale)
			throws TeamRepositoryException {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		IItemHandle itemHandle = IBaselineSet.ITEM_TYPE.createItemHandle(workspaceUUID, null);
		IWorkspace workspace = (IWorkspace)repository.itemManager().fetchCompleteItem(itemHandle, ItemManager.REFRESH, monitor);
		return workspace;
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
	
	/**
	 * Fetch the project area/team area instance with the given name.
	 * 
	 * @param processAreaName Name of the project area or team area. For team area, specify the name of all team areas
	 *            in the hierarchy, starting with the name of the project area, with each of the names separated by "/".
	 *            For an instance 'JKE Banking/Development/User Interface' identifies the 'User Interface' team area
	 *            which is under the 'Development' team area in the 'JKE Banking' project area.
	 * 
	 * @param progress Progress monitor
	 * @param clientLocale The locale of the requesting client
	 * @return IProcessArea Project Area/Team Area instance
	 * @throws RTCConfigurationException
	 * @throws TeamRepositoryException
	 * @throws URISyntaxException
	 * @throws UnsupportedEncodingException
	 */
	public IProcessArea getProcessAreaByName(String processAreaName, ITeamRepository repository, IProgressMonitor progress, Locale clientLocale)
			throws RTCConfigurationException, TeamRepositoryException, URISyntaxException, UnsupportedEncodingException {
		SubMonitor monitor = SubMonitor.convert(progress, 100);
		boolean isTeamArea = false;
		IProcessClientService processClientService = (IProcessClientService)repository.getClientLibrary(IProcessClientService.class);
		// encode the individual name segments and reconstruct the string
		StringTokenizer tokenizer = new StringTokenizer(processAreaName, Constants.PROCESS_AREA_PATH_SEPARATOR);
		StringBuilder encodedProcessAreaName = new StringBuilder();
		while (tokenizer.hasMoreTokens()) {
			encodedProcessAreaName.append(URLEncoder.encode(tokenizer.nextToken(), Constants.DFLT_ENCODING).replace("+", "%20")); //$NON-NLS-1$ //$NON-NLS-2$
			if (tokenizer.hasMoreTokens()) {
				isTeamArea = true;
				encodedProcessAreaName.append(Constants.PROCESS_AREA_PATH_SEPARATOR);
			}
		}
		// fetch only the required properties - we are interested to know the existence and only very few properties
		Collection<String> processAreaProperties = Arrays.asList(new String[] { IItem.ITEM_ID_PROPERTY, IItem.STATE_ID_PROPERTY,
				IItem.MODIFIED_PROPERTY, IItem.CONTEXT_ID_PROPERTY, ProcessCommon.getPropertyName(IProcessArea.class, IProcessItem.NAME_PROPERTY_ID),
				ProcessCommon.getPropertyName(IProcessArea.class, IProcessArea.ARCHIVED_PROPERTY_ID),
				ProcessCommon.getPropertyName(IProcessArea.class, IProcessArea.PROJECT_AREA_PROPERTY_ID) });

		IProcessArea processArea = processClientService.findProcessArea(new URI(encodedProcessAreaName.toString()), processAreaProperties, monitor);
		if (processArea == null) {
			if (isTeamArea) {
				throw new RTCConfigurationException(Messages.get(clientLocale).RepositoryConnection_team_area_not_found(processAreaName));
			} else {
				throw new RTCConfigurationException(Messages.get(clientLocale).RepositoryConnection_project_area_not_found(processAreaName));
			}
		}
		if (processArea.isArchived()) {
			if (processArea instanceof ITeamArea) {
				throw new RTCConfigurationException(Messages.get(clientLocale).RepositoryConnection_team_area_archived(processAreaName));
			} else {
				throw new RTCConfigurationException(Messages.get(clientLocale).RepositoryConnection_project_area_archived(processAreaName));
			}
		}
		return processArea;
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
