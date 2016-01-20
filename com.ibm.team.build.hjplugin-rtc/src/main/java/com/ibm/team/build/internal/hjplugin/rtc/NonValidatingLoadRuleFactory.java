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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.SubMonitor;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

import com.ibm.team.filesystem.client.FileSystemCore;
import com.ibm.team.filesystem.client.FileSystemException;
import com.ibm.team.filesystem.client.ILoadRuleFactory;
import com.ibm.team.filesystem.client.internal.load.LoadRule;
import com.ibm.team.filesystem.client.internal.load.loadRules.LoadRuleHandler;
import com.ibm.team.filesystem.client.operations.ILoadRule2;
import com.ibm.team.filesystem.common.IFileContent;
import com.ibm.team.filesystem.common.IFileItem;
import com.ibm.team.filesystem.common.IFileItemHandle;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.scm.client.IWorkspaceConnection;
import com.ibm.team.scm.common.IComponentHandle;

public class NonValidatingLoadRuleFactory {
    
    private static final String SAX_PROPERTIES_SCHEMA_LOCATION = "http://apache.org/xml/properties/schema/external-schemaLocation"; //$NON-NLS-1$
    private static final String SAX_FEATURES_VALIDATION_SCHEMA = "http://apache.org/xml/features/validation/schema"; //$NON-NLS-1$
    private static final String SAX_FEATURES_VALIDATION = "http://xml.org/sax/features/validation"; //$NON-NLS-1$
    private static final String SAX_FEATURES_INCLUDE_XMLNS_ATTRIBUTE = "http://xml.org/sax/features/namespace-prefixes"; //$NON-NLS-1$
    
    
    public final static String LOAD_RULE_SCHEMA_LOCATION = "http://com.ibm.team.scm platform:/plugin/com.ibm.team.filesystem.client/schema/LoadRule.xsd"; //$NON-NLS-1$
    //public final static String LOAD_RULE_SCHEMA_LOCATION = "http://com.ibm.team.scm file:///C:/selfhosting3.next/com.ibm.team.filesystem.client/schema/LoadRule.xsd"; //$NON-NLS-1$

    /**
     * XML text declaration string that typically is in the first few bytes of an xml file
     */
    private final static String XML_START = "<?xml"; //$NON-NLS-1$

    
    private static ILoadRule2 getLoadRule(IWorkspaceConnection connection, IComponentHandle potentialComponentContext, Reader input, IProgressMonitor progress) throws TeamRepositoryException {
        
        BufferedReader bufferedInput = new BufferedReader(input);
        try {
            // if known to be xml, just parse out the load rules
            if (potentialComponentContext == null || isXMLInput(bufferedInput)) {
                return getLoadRule(connection, bufferedInput, progress);
            } else {
                // if not xml assume oldstyle xml
                LoadRule oldRule = new LoadRule(connection, potentialComponentContext);
                oldRule.addLoadRules(bufferedInput, progress);
                
                // translate to the new format
                ILoadRule2 rule = oldRule.convertToNewFormat(potentialComponentContext, progress);
                return rule;
            }
        } catch (IOException e) {
            throw new FileSystemException(Messages.getDefault().NonValidatingLoadRuleFactory_load_rule_type_failure(e.getMessage()), e);
        } finally {
            try {
                bufferedInput.close();
            } catch (IOException e) {
                // we tried
            }
        }
    }
    
    private static ILoadRule2 getLoadRule(IWorkspaceConnection connection, Reader input, IProgressMonitor progress) throws TeamRepositoryException {
        
        SAXParserFactory nsParserFactory = SAXParserFactory.newInstance();
        nsParserFactory.setNamespaceAware(true);
        nsParserFactory.setValidating(true);

        XMLReader parser;
        InputSource inputSource;
        LoadRuleHandler handler;
        
        try {
            parser = nsParserFactory.newSAXParser().getXMLReader();

            handler = new LoadRuleHandler();
            parser.setContentHandler(handler);
            parser.setEntityResolver(handler);
            parser.setErrorHandler(handler);
            parser.setDTDHandler(handler);
            parser.setFeature(SAX_FEATURES_INCLUDE_XMLNS_ATTRIBUTE, true);

            parser.setFeature(SAX_FEATURES_VALIDATION, true);
            parser.setFeature(SAX_FEATURES_VALIDATION_SCHEMA, true);
            URL schema = NonValidatingLoadRuleFactory.class.getResource("/schema/LoadRule.xsd"); //$NON-NLS-1$
            if (schema == null) {
            	// try again with a class known to be in the jar
            	schema = ILoadRuleFactory.class.getResource("/schema/LoadRule.xsd"); //$NON-NLS-1$
            }
            if (schema == null) {
            	throw new FileSystemException(Messages.getDefault().NonValidatingLoadRuleFactory_missing_schema());
            }
            String schemaLocation = "http://com.ibm.team.scm " + schema.toString(); //$NON-NLS-1$
            parser.setProperty(SAX_PROPERTIES_SCHEMA_LOCATION, schemaLocation);        
            
            inputSource = new InputSource(input);
        } catch (SAXNotRecognizedException e) {
            // Happens if the parser does not support JAXP 1.2
            throw new FileSystemException(e);
        } catch (ParserConfigurationException e) {
            throw new FileSystemException(e);
        } catch (SAXException e) {
            throw new FileSystemException(e);
        }
        
        try {
            parser.parse(inputSource);
        } catch (SAXParseException spe) {
            // Error generated by the parser
            Exception cause = spe.getException();
            if (cause == null) {
                cause = spe;
            }
            if (spe.getSystemId() != null) {
                throw new FileSystemException(Messages.getDefault().NonValidatingLoadRuleFactory_parsing_failed_at_line(spe.getSystemId(), spe.getLineNumber()), cause);
            } else {
                throw new FileSystemException(Messages.getDefault().NonValidatingLoadRuleFactory_parsing_failure(spe.getLocalizedMessage()), cause);
            }
        } catch (IOException e) {
            throw new FileSystemException(Messages.getDefault().NonValidatingLoadRuleFactory_read_failure(), e);
        } catch (SAXException e) {
            if (e.getException() instanceof TeamRepositoryException) {
                throw (TeamRepositoryException) e.getException();
            } else {
                throw new FileSystemException(Messages.getDefault().NonValidatingLoadRuleFactory_parsing_failure(e.getLocalizedMessage()), e);
            }
        }
        
        List<IStatus> warnings = handler.getWarnings();
        if (!warnings.isEmpty()) {
            // TODO figure out a way to give this back to caller as well as the load rules
            // preference is an operation
        }
        
        return handler.getLoadRule(connection, progress);
    }

    public static ILoadRule2 getLoadRule(IWorkspaceConnection connection, String filePath, IProgressMonitor progress) throws TeamRepositoryException {
        try {
			File lFile = new File(filePath);
			if (lFile != null && lFile.exists() && lFile.isFile()) {
			    // assume UTF-8 because we are assuming we generated it.
			    CharsetDecoder decoder = Charset.forName(IFileContent.ENCODING_UTF_8).newDecoder()
			            .onMalformedInput(CodingErrorAction.REPORT)
			            .onUnmappableCharacter(CodingErrorAction.REPORT);
			    Reader reader = new InputStreamReader(new FileInputStream(lFile), decoder);
			    return getLoadRule(connection, null, reader, null);
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        return null;
    }
    
    public static ILoadRule2 getLoadRule(IWorkspaceConnection connection, IComponentHandle componentHandle, IFileItemHandle fileVersionable,
            IProgressMonitor progress) throws TeamRepositoryException {

        SubMonitor monitor = SubMonitor.convert(progress, 100);
        IFileItem file = (IFileItem) connection.configuration(componentHandle).fetchCompleteItem(fileVersionable, monitor.newChild(1));
        InputStream stream = FileSystemCore.getContentManager(connection.teamRepository()).retrieveContentStream(file, file.getContent(), monitor.newChild(1));

        Reader reader;
        if (file.getContent().getCharacterEncoding() == null || file.getContent().getCharacterEncoding() == "") { //$NON-NLS-1$
            // assume UTF-8 because we are assuming we generated it.
            CharsetDecoder decoder = Charset.forName(IFileContent.ENCODING_UTF_8).newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            reader = new InputStreamReader(stream, decoder);
        } else {
            try {
                reader = new InputStreamReader(stream, file.getContent().getCharacterEncoding());
            } catch (UnsupportedEncodingException e) {
                throw new FileSystemException(Messages.getDefault().NonValidatingLoadRuleFactory_bad_encoding(file.getName(), file.getContent().getCharacterEncoding()), e);
            }
        }
        
        return getLoadRule(connection, componentHandle, reader, monitor.newChild(98));
    }


    private static boolean isXMLInput(BufferedReader input) throws IOException {
        boolean result = false;
        input.mark(12);
        char[] buffer = new char[10];
        int len = 0;
        int offset = 0;
        int max = buffer.length;
        
        while (len != -1 && offset < buffer.length) {
            len = input.read(buffer, offset, max);
            if (len != -1) {
                offset += len;
                max -= len;
            }
        }
        String start = new String(buffer, 0, offset);
        if (start.contains(XML_START)) {
            result = true;
        }
        input.reset();
        return result;
    }

}
