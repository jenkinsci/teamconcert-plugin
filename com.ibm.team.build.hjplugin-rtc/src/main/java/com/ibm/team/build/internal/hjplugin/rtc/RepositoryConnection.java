/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package com.ibm.team.build.internal.hjplugin.rtc;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import com.ibm.team.build.common.TeamBuildException;
import com.ibm.team.build.internal.scm.AcceptReport;
import com.ibm.team.build.internal.scm.BuildWorkspaceDescriptor;
import com.ibm.team.build.internal.scm.RepositoryManager;
import com.ibm.team.build.internal.scm.SourceControlUtility;
import com.ibm.team.filesystem.client.FileSystemCore;
import com.ibm.team.filesystem.client.ISandbox;
import com.ibm.team.filesystem.client.ISharingManager;
import com.ibm.team.filesystem.client.internal.PathLocation;
import com.ibm.team.filesystem.client.internal.SharingManager;
import com.ibm.team.filesystem.client.internal.copyfileareas.ICorruptCopyFileAreaEvent;
import com.ibm.team.filesystem.client.internal.copyfileareas.ICorruptCopyFileAreaListener;
import com.ibm.team.filesystem.client.operations.ILoadRule2;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.ServerVersionCheckException;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.transport.client.AuthenticationException;
import com.ibm.team.scm.client.IWorkspaceConnection;
import com.ibm.team.scm.client.IWorkspaceManager;
import com.ibm.team.scm.client.SCMPlatform;
import com.ibm.team.scm.common.IComponentHandle;
import com.ibm.team.scm.common.IWorkspaceHandle;
import com.ibm.team.scm.common.dto.IWorkspaceSearchCriteria;

/**
 * A connection to the Jazz repository.
 */
@SuppressWarnings("restriction")
public class RepositoryConnection {

    private static final Logger LOGGER = Logger.getLogger(RepositoryConnection.class.getName());

	private final AbstractBuildClient fBuildClient;
	private final ConnectionDetails fConnectionDetails;
	private final RepositoryManager fRepositoryManager;
	private final ITeamRepository fRepository;
	
	public RepositoryConnection(AbstractBuildClient buildClient, ConnectionDetails connectionDetails, RepositoryManager repositoryManager, ITeamRepository repository) {
		this.fBuildClient = buildClient;
		this.fConnectionDetails = connectionDetails;
		this.fRepositoryManager = repositoryManager;
		this.fRepository = repository;
	}
	
	public AbstractBuildClient getBuildClient() {
		return fBuildClient;
	}
	
	public ConnectionDetails getConnectionDetails() {
		return fConnectionDetails;
	}
	
	public RepositoryManager getRepositoryManager() {
		return fRepositoryManager;
	}
	
	public ITeamRepository getTeamRepository() {
		return fRepository;
	}

	/**
	 * Tests the given connection by logging in.
	 * 
	 * @throw Exception if an error occurs
	 * @LongOp
	 */
	public void testConnection() throws Exception {
		ITeamRepository repo = (ITeamRepository) getTeamRepository();
		if (repo.loggedIn()) {
			repo.logout();
		}
		try {
			repo.login(null); // TODO: progress monitoring
		} catch (ServerVersionCheckException e) {
			throw new RTCValidationException(e.getMessage());
		} catch (AuthenticationException e) {
			fBuildClient.removeRepositoryConnection(getConnectionDetails());
			throw new RTCValidationException(e.getMessage());
		}
	}

	/**
	 * Tests that the specified workspace is a valid build workspace.
	 * 
	 * @throw Exception if an error occurs
	 * @LongOp
	 */
	public void testBuildWorkspace(String workspaceName) throws Exception {
		getWorkspace(workspaceName);
	}

	/**
	 * Returns the repository workspace with the given name. The workspace connection can
	 * be to a workspace or a stream. If there is more than 1 repository workspace with the name
	 * it is an error.  If there are no repository workspaces with the name it is an error.
	 * 
	 * @param repo The team repository containing that is to contain the workspace
	 * @param workspaceName The name of the workspace. Never <code>null</code>
	 * @return The workspace connection for the workspace. Never <code>null</code>
	 * @throws TeamRepositoryException if an error occurs
	 */
	protected IWorkspaceHandle getWorkspace(String workspaceName) throws Exception {
		IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(getTeamRepository());
		
		IWorkspaceSearchCriteria searchCriteria = IWorkspaceSearchCriteria.FACTORY
				.newInstance().setExactName(workspaceName)
				.setKind(IWorkspaceSearchCriteria.WORKSPACES);
		List<IWorkspaceHandle> workspaceHandles = workspaceManager.findWorkspaces(searchCriteria, 2, null);
		if (workspaceHandles.size() > 1) {
			throw new RTCValidationException(Messages.RepositoryConnection_name_not_unique(workspaceName));
		}
		if (workspaceHandles.size() == 0) {
			throw new RTCValidationException(Messages.RepositoryConnection_workspace_not_found(workspaceName));
		}
		return workspaceHandles.get(0);
	}

	/**
	 * Determines if there are changes for the build workspace which would result in a need for a build.
	 * We are interested in components to be added/removed, change sets to be added/removed.
	 * 
	 * @param buildWorkspaceName Name of the RTC build workspace
	 * @return <code>true</code> if there are changes for the build workspace
	 * <code>false</code> otherwise
	 * @throws Exception Thrown if anything goes wrong.
	 */
	public boolean incomingChanges(String buildWorkspaceName, IConsoleOutput listener) throws Exception {
		ensureLoggedIn();
		IWorkspaceHandle workspaceHandle = getWorkspace(buildWorkspaceName);
		BuildWorkspaceDescriptor workspace = new BuildWorkspaceDescriptor(fRepository, workspaceHandle.getItemId().getUuidValue(), buildWorkspaceName);
		AcceptReport report = SourceControlUtility.checkForIncoming(fRepositoryManager, workspace, null);
		return report.getChangesAcceptedCount() > 0;
	}

	public void checkout(String buildWorkspaceName, String fetchDestination, ChangeReport changeReport,
			String snapshotName, final IConsoleOutput listener) throws Exception {
		ensureLoggedIn();
		IWorkspaceHandle workspaceHandle = getWorkspace(buildWorkspaceName);
		BuildWorkspaceDescriptor workspace = new BuildWorkspaceDescriptor(fRepository, workspaceHandle.getItemId().getUuidValue(), buildWorkspaceName);
		AcceptReport acceptReport = null;

        File fetchDestinationFile = new File(fetchDestination);
        final Path fetchDestinationPath = new Path(fetchDestinationFile.getCanonicalPath());
        
        listener.log(Messages.RepositoryConnection_checkout_setup());
        
        // Ensure we hang onto this between the accept and the load steps so that if 
        // we are synchronizing, we use the same cached sync times.
        IWorkspaceConnection workspaceConnection = workspace.getConnection(fRepositoryManager, false, null);

        boolean synchronizeLoad = false;
        boolean acceptBeforeFetch = true;
        boolean isPersonalBuild = false;
        boolean deleteNeeded = false;
        
        if (!isPersonalBuild && acceptBeforeFetch) {
            listener.log(Messages.RepositoryConnection_checkout_accept(
            		workspaceConnection.getName()));
            
            acceptReport = SourceControlUtility.acceptAllIncoming(
                    fRepositoryManager, workspace, snapshotName,
                    null);
            
            // build change report
            ChangeReportBuilder changeReportBuilder = new ChangeReportBuilder(fRepository);
            changeReportBuilder.populateChangeReport(changeReport,
            		workspaceConnection.getResolvedWorkspace(), acceptReport,
            		listener);
            
            synchronizeLoad = true;
        }

        ISharingManager manager = FileSystemCore.getSharingManager();
        ISandbox sandbox = manager.getSandbox(new PathLocation(fetchDestinationPath), false);

        // Create a listener to handle corrupt states which may happen during loading
        ICorruptCopyFileAreaListener corruptSandboxListener = new ICorruptCopyFileAreaListener () {
            public void corrupt(ICorruptCopyFileAreaEvent event) {
                if (event.isCorrupt() && event.getRoot().equals(fetchDestinationPath)) {
                    listener.log(Messages.RepositoryConnection_corrupt_metadata(
                    		fetchDestinationPath.toOSString()));
                }
            }
        };
        
        try {
            
            // we don't need to check for a corrupt sandbox if it doesn't exist or is marked as "Delete Before Fetch"
            if (fetchDestinationFile.exists() && !deleteNeeded) { 
               
                if (!sandbox.isRegistered()) {
                    // the sandbox must be registered in order to call .isCorrupted()
                    manager.register(sandbox, false, null);
                }
                
                if (sandbox.isCorrupted(null)) {
                    deleteNeeded = true;
                    listener.log(Messages.RepositoryConnection_corrupt_metadata_found(
                            fetchDestinationFile.getCanonicalPath()));
                    LOGGER.finer("Corrupt metadata for sandbox " +  //$NON-NLS-1$
                            fetchDestinationFile.getCanonicalPath());
                }
            }

            if (deleteNeeded) {
                listener.log(Messages.RepositoryConnection_checkout_clean_sandbox(
                        fetchDestinationFile.getCanonicalPath()));

                File toDelete = fetchDestinationFile;
                // the sandbox must be deregistered in order to delete
                manager.deregister(sandbox, null);
                
                boolean deleteSucceeded = delete(toDelete, listener);

                if (!deleteSucceeded || fetchDestinationFile.exists()) {
                    throw new TeamBuildException(Messages.RepositoryConnection_checkout_clean_failed(
                            fetchDestinationFile.getCanonicalPath()));
                }
            }

            // Add the listener just before the fetch stage
            ((SharingManager) FileSystemCore.getSharingManager()).addListener(corruptSandboxListener);

            listener.log(Messages.RepositoryConnection_checkout_fetch_start(
                    fetchDestinationFile.getCanonicalPath()));

            // figure out the handles from the load rules property
            Collection<ILoadRule2> loadRules = Collections.emptyList();
            Collection<IComponentHandle> components = Collections.emptyList();
            SourceControlUtility.updateFileCopyArea(workspaceConnection,
                    fetchDestinationFile.getCanonicalPath(), false, components,
                    synchronizeLoad, loadRules, false, null);

            listener.log(Messages.RepositoryConnection_checkout_fetch_complete());

        } finally {
            /*
             * Force a deregistration of the sandbox so that its Metadata is guaranteed to be flushed
             * And possibly so the next time we try to delete the file copy area, it will succeed.
             */
            ((SharingManager) FileSystemCore.getSharingManager()).removeListener(corruptSandboxListener);
            try {
                manager.deregister(sandbox, null);
            } catch (Exception e) {
            	listener.log(Messages.RepositoryConnection_checkout_termination_error(e.getMessage()), e);
            }
        }
	}
	public void ensureLoggedIn() throws TeamRepositoryException {
		if (!fRepository.loggedIn()) {
			try {
				fRepository.login(null);
			} catch (AuthenticationException e) {
				fBuildClient.removeRepositoryConnection(getConnectionDetails());
				throw e;
			}
		}
	}

    /**
     * Recursively delete starting at the given file.
     */
    private boolean delete(File file, IConsoleOutput listener) throws Exception {
    	// paranoia check... Don't delete root directory because somehow the path was empty
    	LOGGER.finer("Deleting " + file.getAbsolutePath()); //$NON-NLS-1$
    	IPath path = new Path(file.getCanonicalPath());
    	if (path.segmentCount() == 0) {
    		listener.log(Messages.RepositoryConnection_checkout_clean_root_disallowed(path.toOSString()));
    		LOGGER.finer("Tried to delete root directory " + path.toOSString()); //$NON-NLS-1$
    		return false;
    	}
    	return deleteUsingJavaIO(file, listener);
    }
    
    private boolean deleteUsingJavaIO(File file, IConsoleOutput listener) throws IOException {
        // take into account file or its children may be valid symbolic links.
        // we do not want to follow them.
        // The alternative to this is to use EFS to do the delete which does
        // essentially the same thing.
        // The other alternative is to use EFS to get the attributes and see
        // if it is a symbolic link and tailor the delete according to that.
        // This may have incorrect behaviour if the file is a link
        // and read-only.
        if (file.delete() || !file.exists()) {
            // Try to delete the file/link first without recursing to avoid
            // traversing into links
            return true;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                listener.log(Messages.RepositoryConnection_checkout_clean_error(file.getCanonicalPath()));
                LOGGER.finer("Unexpected null children for " + file.getCanonicalPath()); //$NON-NLS-1$
            } else {
                for (File child : children) {
                	deleteUsingJavaIO(child, listener);
                }
            }
        }
        return file.delete();
    }

}
