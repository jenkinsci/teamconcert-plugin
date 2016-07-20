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

package com.ibm.team.build.internal.hjplugin.rtc.tests;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import com.ibm.team.filesystem.client.FileSystemCore;
import com.ibm.team.filesystem.client.IFileContentManager;
import com.ibm.team.filesystem.client.workitems.IFileSystemWorkItemManager;
import com.ibm.team.filesystem.common.FileLineDelimiter;
import com.ibm.team.filesystem.common.IFileContent;
import com.ibm.team.filesystem.common.IFileItem;
import com.ibm.team.filesystem.common.internal.FileContent;
import com.ibm.team.filesystem.common.internal.FileItem;
import com.ibm.team.filesystem.common.internal.FilesystemFactory;
import com.ibm.team.process.common.IProcessAreaHandle;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IItemHandle;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.UUID;
import com.ibm.team.scm.client.IConfiguration;
import com.ibm.team.scm.client.IFlowNodeConnection.IComponentAdditionOp;
import com.ibm.team.scm.client.IWorkspaceConnection;
import com.ibm.team.scm.client.IWorkspaceConnection.IConfigurationOp;
import com.ibm.team.scm.client.IWorkspaceConnection.IConfigurationOpFactory;
import com.ibm.team.scm.client.IWorkspaceConnection.ISaveOp;
import com.ibm.team.scm.client.IWorkspaceManager;
import com.ibm.team.scm.client.SCMPlatform;
import com.ibm.team.scm.client.content.util.VersionedContentManagerByteArrayInputStreamPovider;
import com.ibm.team.scm.common.BaselineSetFlags;
import com.ibm.team.scm.common.ContentHash;
import com.ibm.team.scm.common.IBaselineSet;
import com.ibm.team.scm.common.IBaselineSetHandle;
import com.ibm.team.scm.common.IChangeSetHandle;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IComponentHandle;
import com.ibm.team.scm.common.IFolder;
import com.ibm.team.scm.common.IFolderHandle;
import com.ibm.team.scm.common.IVersionable;
import com.ibm.team.scm.common.IVersionableHandle;
import com.ibm.team.scm.common.IWorkspace;
import com.ibm.team.scm.common.IWorkspaceHandle;
import com.ibm.team.scm.common.dto.IComponentSearchCriteria;
import com.ibm.team.scm.common.internal.ScmFactory;
import com.ibm.team.scm.common.internal.Versionable;
import com.ibm.team.workitem.common.model.IWorkItemHandle;

@SuppressWarnings({"nls", "restriction"})
public class SCMUtil {

	public static IWorkspaceConnection createWorkspace(IWorkspaceManager workspaceManager, String streamName) throws TeamRepositoryException {
		return workspaceManager.createWorkspace(workspaceManager.teamRepository().loggedInContributor(), streamName , "The stream for the build", null);
	}
	
	public static IWorkspaceConnection createBuildWorkspace(IWorkspaceManager workspaceManager, IWorkspaceConnection buildStream, String workspaceName) throws TeamRepositoryException {
		return workspaceManager.createWorkspace(workspaceManager.teamRepository().loggedInContributor(), workspaceName, "Build workspace", buildStream, buildStream, null);
	}
	
	public static IWorkspaceConnection createWorkspace(IWorkspaceManager workspaceManager, String workspaceName, String description) throws TeamRepositoryException {
		return workspaceManager.createWorkspace(workspaceManager.teamRepository().loggedInContributor(), workspaceName , description, null);
	}
	
	public static IWorkspaceConnection createStream(IWorkspaceManager workspaceManager, IProcessAreaHandle processAreaHandle, String streamName)
			throws TeamRepositoryException {
		return workspaceManager.createStream(processAreaHandle, streamName, "The stream for the build", null);
	}
	
	public static Map<String, IItemHandle> addComponent(IWorkspaceManager workspaceManager, IWorkspaceConnection workspace,
			String componentName, String[] contents) throws TeamRepositoryException {
		List<IComponentHandle> searchResult = workspaceManager.findComponents(IComponentSearchCriteria.FACTORY.newInstance().setExactName(componentName), 1, null);
		IComponent component;
		if (searchResult.isEmpty()) {
			component = workspaceManager.createComponent(componentName, workspaceManager.teamRepository().loggedInContributor(), null);
		} else {
			component = (IComponent) workspaceManager.teamRepository().itemManager().fetchCompleteItem(searchResult.get(0), IItemManager.DEFAULT, null);
		}
		Map<String, IItemHandle> artifacts = new HashMap<String, IItemHandle>();
		artifacts.put(componentName, component);
		
		IComponentAdditionOp componentOp = workspace.componentOpFactory().addComponent(component, false);
		workspace.applyComponentOperations(Collections.singletonList(componentOp), null);
		
		addVersionables(workspace, component, null, artifacts, contents);
		return artifacts;
	}
	
	public static Map<String, IItemHandle> addComponent(IWorkspaceManager workspaceManager, IWorkspaceConnection workspace,
			String componentName) throws TeamRepositoryException {
		List<IComponentHandle> searchResult = workspaceManager.findComponents(IComponentSearchCriteria.FACTORY.newInstance().setExactName(componentName), 1, null);
		IComponent component;
		if (searchResult.isEmpty()) {
			component = workspaceManager.createComponent(componentName, workspaceManager.teamRepository().loggedInContributor(), null);
		} else {
			component = (IComponent) workspaceManager.teamRepository().itemManager().fetchCompleteItem(searchResult.get(0), IItemManager.DEFAULT, null);
		}
		Map<String, IItemHandle> artifacts = new HashMap<String, IItemHandle>();
		artifacts.put(componentName, component);
		
		IComponentAdditionOp componentOp = workspace.componentOpFactory().addComponent(component, false);
		workspace.applyComponentOperations(Collections.singletonList(componentOp), null);
		
		return artifacts;
	}
	
	public static IBaselineSetHandle createSnapshot(IWorkspaceConnection workspace, String snapshotName) throws TeamRepositoryException {
		return workspace.createBaselineSet(null, snapshotName, null, BaselineSetFlags.CREATE_NEW_BASELINES, null);
	}

	public static IChangeSetHandle addVersionables(IWorkspaceConnection workspace,
			IComponent component, IChangeSetHandle changeSet,
			Map<String, IItemHandle> artifacts,
			String[] hierarchy) throws TeamRepositoryException {
		return addVersionables(workspace, component, changeSet,
				artifacts, hierarchy, null);
	}
	
	public static IChangeSetHandle addVersionables(IWorkspaceConnection workspace,
			IComponent component, IChangeSetHandle changeSet,
			Map<String, IItemHandle> artifacts,
			String[] hierarchy, String[] fileContents) throws TeamRepositoryException {
		
		if (hierarchy == null || hierarchy.length == 0) {
			return changeSet;
		}
		
        IFileContentManager contentManager = FileSystemCore.getContentManager(workspace.teamRepository());

        List<IConfigurationOp> ops = new ArrayList<IWorkspaceConnection.IConfigurationOp>(hierarchy.length + 1);
        for (int i = 0; i < hierarchy.length; i++) {
            final String itemPath = hierarchy[i];
            IPath path = new Path(itemPath);
            IFolderHandle parentHandle;
            String parentPath = standardizePath(path.removeLastSegments(1).toString(), true);
            if (parentPath.equals("/")) {
                parentHandle = component.getRootFolder();
            } else {
                parentHandle = (IFolderHandle) artifacts.get(parentPath);
            }
            if (parentHandle == null) {
            	throw new IllegalArgumentException(parentPath + " has no handle");
            }
            switch (path.segmentCount()) {
            case 0:
                break;
            default:
                IVersionable item;
                if ((itemPath.charAt(itemPath.length() - 1) == IPath.SEPARATOR)) {
                    item = (IFolder) IFolder.ITEM_TYPE.createItem();
                } else {
                    // create a file
                    item = (IFileItem) IFileItem.ITEM_TYPE.createItem();
                    VersionedContentManagerByteArrayInputStreamPovider inStream;
                    FileLineDelimiter lineDelimiter = FileLineDelimiter.LINE_DELIMITER_NONE;
                    String mimeType = IFileItem.CONTENT_TYPE_TEXT;
                    String encoding = IFileContent.ENCODING_UTF_8;
                    
                    if (fileContents == null || fileContents[i] == null) {
                    	inStream = getRandomStreamProvider(itemPath);
                    } else {
                    	inStream = new VersionedContentManagerByteArrayInputStreamPovider(fileContents[i].getBytes());
                    }
                    
                    IFileContent content = contentManager.storeContent(encoding, lineDelimiter, inStream, null, null);
                    ((IFileItem) item).setContentType(mimeType);
                    ((IFileItem) item).setContent(content);
                    ((IFileItem) item).setFileTimestamp(new Date());
                    ((IFileItem) item).setExecutable(false);
                }
                item.setParent(parentHandle);
                item.setName(path.lastSegment());
                ops.add(workspace.configurationOpFactory().save(item));

                artifacts.put(itemPath, item.getItemHandle());
                break;
            }
        }
        boolean closeChangeSet = false;
        if (changeSet == null) {
            changeSet = workspace.createChangeSet(component, null);
            closeChangeSet = true;
        }
        workspace.commit(changeSet, ops, null);
        if (closeChangeSet) {
            workspace.closeChangeSets(Collections.singletonList(changeSet), null);
        }
        return changeSet;
	}
	
    private static VersionedContentManagerByteArrayInputStreamPovider getRandomStreamProvider(String someContent) {
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
    	String randomContents = someContent + " generated " + formatter.format(new Date(System.currentTimeMillis())) + " " + UUID.generate().getUuidValue();
        return new VersionedContentManagerByteArrayInputStreamPovider(randomContents.getBytes());
    }

    private static String standardizePath(String path, boolean isFolder) {
        if (path.equals("")) {
            return "/";
        }
        if (path.charAt(0) != '/') {
            path = "/" + path;
        }
        if (isFolder && path.charAt(path.length() - 1) != '/') {
            path = path + "/";
        }
        return path;
    }

	public static void modifyFiles(IWorkspaceConnection workspace,
			IComponent component, IChangeSetHandle changeSet,
			Map<String, IItemHandle> handles, String[] toModify) throws TeamRepositoryException {
		List<IVersionableHandle> versionables = new ArrayList<IVersionableHandle>(toModify.length);
		for (String path : toModify) {
			IVersionableHandle handle = (IVersionableHandle) handles.get(path);
			if (handle == null) {
				throw new IllegalStateException(path + " no handle found");
			}
			versionables.add(handle);
		}
		
		List<IVersionable> fullVersionables = workspace.configuration(component).fetchCompleteItems(versionables, null);
        List<IConfigurationOp> ops = new ArrayList<IConfigurationOp>();
        int i = 0;
        for (IVersionable next : fullVersionables) {
            if (next instanceof IFileItem) {
                IFileItem file = (IFileItem) next;
                file = (IFileItem) file.getWorkingCopy();
                IFileContent previousContent = file.getContent();
                
                String encoding = previousContent.getCharacterEncoding();
                VersionedContentManagerByteArrayInputStreamPovider inStream;
                inStream = getRandomStreamProvider(toModify[i]);
                
                ContentHash predecessor;
                if (file.getContent() != null) {
                    predecessor = file.getContent().getHash();
                } else {
                    predecessor = null;
                }
                IFileContent content = FileSystemCore.getContentManager(workspace.teamRepository()).storeContent(
                        encoding, previousContent.getLineDelimiter(), inStream, predecessor, null);
                // content type remains unchanged.
                file.setContent(content);
                file.setFileTimestamp(new Date());
                ops.add(workspace.configurationOpFactory().save(file));
            } else {
                throw new IllegalStateException("Unsupported versionable type " + next.getName() + " " + next.getClass().getName());
            }
        }
        workspace.commit(changeSet, ops, null);
	}

	public static void moveVersionable(
			IWorkspaceConnection workspace, IComponent component,
			IChangeSetHandle changeSet, Map<String, IItemHandle> pathToHandle,
			String source, String destination) throws TeamRepositoryException {
        IConfiguration configuration = workspace.configuration(component);
        
        source = standardizePath(source, source.charAt(source.length() - 1) == '/');
        destination = standardizePath(destination, destination.charAt(destination.length() - 1) == '/');

        IPath destinationPath = new Path(destination);
        IPath destinationParentPath = destinationPath.removeLastSegments(1);

        ArrayList ops = new ArrayList();
        IVersionableHandle move = (IVersionableHandle) pathToHandle.get(source);
        if (move == null) {
        	throw new IllegalStateException("Missing move element" + source);
        }
        Versionable previousItem = (Versionable) configuration.fetchCompleteItem(move, null);
        Versionable moveItem;
        if (previousItem instanceof IFolder) {
            moveItem = ScmFactory.eINSTANCE.createFolder();
            moveItem.initNew();
            moveItem.setItemId(previousItem.getItemId());
            
            // transfer properties
            Map<String, String> prevProperties = previousItem.getProperties();
            Map<String, String> moveProperties = moveItem.getProperties();
            for (Map.Entry<String, String> entry : prevProperties.entrySet()) {
                moveProperties.put(entry.getKey(), entry.getValue());
            }
            
        } else if (previousItem instanceof IFileItem){
            moveItem = FilesystemFactory.eINSTANCE.createFileItem();
            moveItem.initNew();
            moveItem.setItemId(previousItem.getItemId());
            
            FileItem moveFile = ((FileItem) moveItem);
            FileItem prevFile = ((FileItem) previousItem);
            
            moveFile.setExecutable(prevFile.isExecutable());
            // transfer properties
            Map<String, String> prevProperties = prevFile.getProperties();
            Map<String, String> moveProperties = moveItem.getProperties();
            for (Map.Entry<String, String> entry : prevProperties.entrySet()) {
                moveProperties.put(entry.getKey(), entry.getValue());
            }
            
            // set current timestamp like commit does.
            moveFile.setFileTimestamp(new Date());
            

            // est the content like commit does.
            FileContent prevContent = (FileContent) prevFile.getContent();
            FileContent content = FilesystemFactory.eINSTANCE.createFileContent();

            // transfer properties.
            prevProperties = prevContent.getProperties();
            moveProperties = content.getProperties();
            for (Map.Entry<String, String> entry : prevProperties.entrySet()) {
                moveProperties.put(entry.getKey(), entry.getValue());
            }

            content.setPredecessorHint(prevContent.getPredecessorHintHash());
            content.setLineDelimiter(prevContent.getLineDelimiter());
            content.setSize(prevContent.getSize());
            content.setCharacterEncoding(prevContent.getCharacterEncoding());
            content.setHash(prevContent.getHash());
            content.setLineDelimiterCount(prevContent.getLineDelimiterCount());
            content.setOriginalContainingState((IVersionableHandle) previousItem.getStateHandle());
            
            moveFile.setContent(content);
            moveFile.setContentType(prevFile.getContentType());
            
        } else {
            throw new IllegalStateException("Unsupported item type");
        }
        
        String destParent = standardizePath(destinationParentPath.toString(), true);
        IFolderHandle destinationParentHandle = (IFolderHandle) pathToHandle.get(destParent);
        if (destinationParentHandle == null) {
        	throw new IllegalStateException("Missing parent folder" + destParent);
        }
        moveItem.setParent(destinationParentHandle);
        moveItem.setName(destinationPath.lastSegment());
        if (moveItem instanceof IFileItem) {
            String name = moveItem.getName().toLowerCase();
            ((IFileItem) moveItem).setExecutable(name.endsWith(".exe") || name.endsWith(".bat"));
        }
        ops.add(workspace.configurationOpFactory().save(moveItem));
        
        pathToHandle.remove(source);
        pathToHandle.put(destination, move);

        if (move instanceof IFolderHandle) {
            // need to move the children too
            HashMap movedChildren = new HashMap();
            for (Iterator iPaths = pathToHandle.keySet().iterator(); iPaths.hasNext();) {
                String path = (String) iPaths.next();
                if (path.startsWith(source)) {
                    Object element = pathToHandle.get(path);
                    iPaths.remove();
                    path = destination + path.substring(source.length());
                    movedChildren.put(path, element);
                }
            }
            pathToHandle.putAll(movedChildren);
        }
        workspace.commit(changeSet, ops, null);
	}

	public static void deleteVersionables(
			IWorkspaceConnection workspace, IComponent component,
			IChangeSetHandle changeSet, Map<String, IItemHandle> pathToHandle,
			String[] toDelete) throws TeamRepositoryException {

        ArrayList ops = new ArrayList(toDelete.length + 1);

        IConfigurationOpFactory opFactory = workspace.configurationOpFactory();
        for (int i = 0; i < toDelete.length; i++) {
            IVersionableHandle itemHandle = (IVersionableHandle) pathToHandle.get(toDelete[i]);
            ops.add(opFactory.delete(itemHandle));
            pathToHandle.remove(toDelete[i]);
        }
        
        workspace.commit(changeSet, ops, null);
	}

	/**
	 * Create a link between the change set and the work items supplied
	 * @param teamRepository The team repository containing the change set & work items
	 * @param workItemHandles The work items to be associated with the change set
	 * @param changeSetHandle The change set for which the work items are to be associated.
	 * @throws TeamRepositoryException Thrown if anything goes wrong.
	 */
	public static void createWorkItemChangeSetLink(ITeamRepository teamRepository, IWorkItemHandle[] workItemHandles, IChangeSetHandle changeSetHandle) throws TeamRepositoryException {

        IFileSystemWorkItemManager workItemManager = (IFileSystemWorkItemManager) teamRepository.getClientLibrary(
                IFileSystemWorkItemManager.class);

        workItemManager.createLink(null, changeSetHandle, workItemHandles,
                null);
	}

	public static void makePropertyChanges(IWorkspaceConnection workspace,
			IComponent component, IChangeSetHandle cs, IFolderHandle rootFolder) throws TeamRepositoryException {
		IVersionable fullItem = workspace.configuration(component).fetchCompleteItem(rootFolder, null);
		fullItem = (IVersionable) fullItem.getWorkingCopy();
	    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		String value = formatter.format(System.currentTimeMillis());
		fullItem.setUserProperty("Hudson/Jenkins test", value);
		ISaveOp saveOp = workspace.configurationOpFactory().save(fullItem);
		workspace.commit(cs, Collections.singleton(saveOp), null);
	}
    
	public static void deleteWorkspace(ITeamRepository repo,
			String uuidValue) throws TeamRepositoryException {
		if (uuidValue != null) {
			IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
			IWorkspaceHandle toDelete = (IWorkspaceHandle) IWorkspace.ITEM_TYPE.createItemHandle(UUID.valueOf(uuidValue), null);
			workspaceManager.deleteWorkspace(toDelete, null);
		}
	}

	@SuppressWarnings("unchecked")
	public static void deleteWorkspaceAndAssociatedSnapshots(ITeamRepository repo, String uuidValue) throws TeamRepositoryException {
		if (uuidValue != null) {
			IWorkspaceManager workspaceManager = SCMPlatform.getWorkspaceManager(repo);
			IWorkspaceHandle toDelete = (IWorkspaceHandle)IWorkspace.ITEM_TYPE.createItemHandle(UUID.valueOf(uuidValue), null);
			IWorkspaceConnection wsConnection = workspaceManager.getWorkspaceConnection(toDelete, null);
			if (wsConnection != null) {
				List<IBaselineSetHandle> associatedSnapshots = wsConnection.getBaselineSets(null);
				for (IBaselineSetHandle snapshot : associatedSnapshots) {
					wsConnection.removeBaselineSet(snapshot, null);
				}
			}
			workspaceManager.deleteWorkspace(toDelete, null);
		}
	}
}
