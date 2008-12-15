/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xpn.xwiki.plugin.webdav.resources.domain;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.server.io.IOUtil;
import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavResource;
import org.apache.jackrabbit.webdav.DavResourceIterator;
import org.apache.jackrabbit.webdav.DavResourceIteratorImpl;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.io.InputContext;
import org.apache.jackrabbit.webdav.io.OutputContext;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.plugin.webdav.resources.XWikiDavResource;
import com.xpn.xwiki.plugin.webdav.resources.partial.AbstractDavResource;

/**
 * The collection resource which represents a page {@link XWikiDocument} of XWiki.
 * 
 * @version $Id$
 */
public class DavPage extends AbstractDavResource
{
    /**
     * Logger instance.
     */
    private static final Logger logger = LoggerFactory.getLogger(DavPage.class);

    /**
     * The name of the space to which this page belongs to.
     */
    private String spaceName;

    /**
     * The {@link XWikiDocument} represented by this resource.
     */
    private XWikiDocument doc;

    /**
     * {@inheritDoc}
     */
    public void init(XWikiDavResource parent, String name, String relativePath)
        throws DavException
    {
        super.init(parent, name, relativePath);
        int dot = name.lastIndexOf('.');
        if (dot != -1) {
            this.spaceName = name.substring(0, dot);
        } else {
            throw new DavException(DavServletResponse.SC_BAD_REQUEST);
        }
        this.doc = getContext().getDocument(this.name);
        String timeStamp = DavConstants.creationDateFormat.format(doc.getCreationDate());
        getProperties().add(new DefaultDavProperty(DavPropertyName.CREATIONDATE, timeStamp));
        timeStamp = DavConstants.modificationDateFormat.format(doc.getContentUpdateDate());
        getProperties().add(new DefaultDavProperty(DavPropertyName.GETLASTMODIFIED, timeStamp));
        getProperties().add(new DefaultDavProperty(DavPropertyName.GETETAG, timeStamp));
        getProperties().add(
            new DefaultDavProperty(DavPropertyName.GETCONTENTTYPE, "text/directory"));
        getProperties().add(
            new DefaultDavProperty(DavPropertyName.GETCONTENTLANGUAGE, doc.getLanguage()));
        getProperties().add(new DefaultDavProperty(DavPropertyName.GETCONTENTLENGTH, 0));
    }

    /**
     * {@inheritDoc}
     */
    public void decode(Stack<XWikiDavResource> stack, String[] tokens, int next)
        throws DavException
    {
        if (next < tokens.length) {
            boolean last = (next + 1 == tokens.length);
            String nextToken = tokens[next];
            String relativePath = "/" + nextToken;
            int dot = nextToken.indexOf('.');
            if (isTempResource(nextToken)) {
                super.decode(stack, tokens, next);
            } else if (dot != -1) {
                if (!last || getContext().exists(nextToken)
                    || getContext().isCreateCollectionRequest()) {
                    DavPage davPage = new DavPage();
                    davPage.init(this, nextToken, relativePath);
                    stack.push(davPage);
                    davPage.decode(stack, tokens, next + 1);
                } else if (nextToken.equals(DavWikiFile.WIKI_TXT)
                    || nextToken.equals(DavWikiFile.WIKI_XML)) {
                    DavWikiFile wikiFile = new DavWikiFile();
                    wikiFile.init(this, nextToken, relativePath);
                    stack.push(wikiFile);
                } else {
                    DavAttachment attachment = new DavAttachment();
                    attachment.init(this, nextToken, relativePath);
                    stack.push(attachment);
                }
            } else {
                if (!last || getContext().exists(this.spaceName + "." + nextToken)
                    || getContext().isCreateCollectionRequest()) {
                    DavPage davPage = new DavPage();
                    davPage.init(this, this.spaceName + "." + nextToken, relativePath);
                    stack.push(davPage);
                    davPage.decode(stack, tokens, next + 1);
                } else {
                    DavAttachment attachment = new DavAttachment();
                    attachment.init(this, nextToken, relativePath);
                    stack.push(attachment);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean exists()
    {
        return !doc.isNew();
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public DavResourceIterator getMembers()
    {
        // Protect against direct url referencing.
        List<DavResource> children = new ArrayList<DavResource>();
        if (!getContext().hasAccess("view", this.name)) {
            return new DavResourceIteratorImpl(children);
        }
        try {
            String sql = "where doc.parent='" + this.name + "'";
            List<String> docNames = getContext().searchDocumentsNames(sql);
            for (String docName : docNames) {
                if (getContext().hasAccess("view", docName)) {
                    XWikiDocument childDoc = getContext().getDocument(docName);
                    DavPage page = new DavPage();
                    if (childDoc.getSpace().equals(this.spaceName)) {
                        page.init(this, docName, "/" + childDoc.getName());
                    } else {
                        page.init(this, docName, "/" + docName);
                    }
                    children.add(page);
                }
            }
            DavWikiFile wikiText = new DavWikiFile();
            wikiText.init(this, DavWikiFile.WIKI_TXT, "/" + DavWikiFile.WIKI_TXT);
            children.add(wikiText);
            DavWikiFile wikiXml = new DavWikiFile();
            wikiXml.init(this, DavWikiFile.WIKI_XML, "/" + DavWikiFile.WIKI_XML);
            children.add(wikiXml);
            sql =
                "select attach.filename from XWikiAttachment as attach, "
                    + "XWikiDocument as doc where attach.docId=doc.id and doc.fullName='"
                    + this.name + "'";
            List attachments = getContext().search(sql);
            for (int i = 0; i < attachments.size(); i++) {
                String filename = (String) attachments.get(i);
                DavAttachment attachment = new DavAttachment();
                attachment.init(this, filename, "/" + filename);
                children.add(attachment);
            }
            // In-memory resources.
            for (DavResource sessionResource : getVirtualMembers()) {
                children.add(sessionResource);
            }
        } catch (DavException e) {
            logger.error("Unexpected Error : ", e);
        }
        return new DavResourceIteratorImpl(children);
    }

    /**
     * {@inheritDoc}
     */
    public void addMember(DavResource resource, InputContext inputContext) throws DavException
    {
        getContext().checkAccess("edit", this.name);
        boolean isFile = (inputContext.getInputStream() != null);
        if (resource instanceof DavTempFile) {
            addTempResource((DavTempFile) resource, inputContext);
        } else if (resource instanceof DavPage) {
            String pName = resource.getDisplayName();
            getContext().checkAccess("edit", pName);
            XWikiDocument childDoc = getContext().getDocument(pName);
            childDoc.setContent("This page was created thorugh xwiki-webdav interface.");
            childDoc.setParent(this.name);
            getContext().saveDocument(childDoc);
        } else if (isFile) {
            String fName = resource.getDisplayName();
            byte[] data = getContext().getFileContentAsBytes(inputContext.getInputStream());
            if (fName.equals(DavWikiFile.WIKI_TXT)) {
                doc.setContent(new String(data));
                getContext().saveDocument(doc);
            } else if (fName.equals(DavWikiFile.WIKI_XML)) {
                // These values should not be writable.
                String oldVersion = doc.getVersion();
                String oldContentAuthor = doc.getContentAuthor();
                // Keep for determining if the content was changed or not.
                String oldContent = doc.getContent();
                getContext().fromXML(doc, new String(data));
                // Ignore the version received in the XML. It will be automatically increased
                // when saving the doc.
                doc.setVersion(oldVersion);
                // Don't allow setting the contentAuthor, as it determines the programming
                // right.
                doc.setContentAuthor(oldContentAuthor);
                if (!StringUtils.equals(oldContent, doc.getContent())) {
                    // Force setting the contentUpdateDate and contentAuthor if the content was
                    // changed.
                    doc.setContentDirty(true);
                }
                // Force setting the current date and increasing the version
                doc.setMetaDataDirty(true);
                // Force setting the author.
                doc.setAuthor(getContext().getUser());
                getContext().saveDocument(doc);
            } else {
                getContext().addAttachment(doc, data, fName);
            }
        } else {
            throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void removeMember(DavResource member) throws DavException
    {
        getContext().checkAccess("edit", this.name);
        String mName = member.getDisplayName();
        if (member instanceof DavTempFile) {
            removeTempResource((DavTempFile) member);
        } else if (member instanceof DavWikiFile) {
            // Wiki files cannot be deleted, but don't do anything! let the client assume that the
            // delete was a success. This is required since some clients try to delete the file
            // before saving a new (edited) file or when deleting the parent. Still, problems might
            // arise if the client tries to verify the delete by re requesting the resource in which
            // case we'll need yet another (elegant) workaround.
        } else if (member instanceof DavAttachment) {
            getContext().deleteAttachment(doc.getAttachment(mName));
        } else if (member instanceof DavPage) {
            XWikiDocument childDoc = getContext().getDocument(mName);
            getContext().checkAccess("delete", childDoc.getFullName());
            if (!childDoc.isNew()) {
                getContext().deleteDocument(childDoc);
            }
        } else {
            throw new DavException(DavServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void move(DavResource destination) throws DavException
    {
        getContext().checkAccess("delete", this.name);
        XWikiDavResource dResource = (XWikiDavResource) destination;
        String dSpaceName = null;
        String dPageName = null;
        int dot = dResource.getDisplayName().lastIndexOf('.');
        if (dot != -1) {
            dSpaceName = dResource.getDisplayName().substring(0, dot);
            dPageName = dResource.getDisplayName().substring(dot + 1);
        } else {
            dSpaceName = this.spaceName;
            dPageName = dResource.getDisplayName();
        }
        List<String> spaces = getContext().getSpaces();
        if (spaces.contains(dSpaceName)) {
            String newDocName = dSpaceName + "." + dPageName;
            String sql = "where doc.parent='" + this.name + "'";
            List<String> childDocNames = getContext().searchDocumentsNames(sql);
            // Validate access rights for the destination page.
            getContext().checkAccess("edit", newDocName);
            // Validate access rights for all the renamed pages.
            for (String childDocName : childDocNames) {
                getContext().checkAccess("edit", childDocName);
            }
            getContext().renameDocument(doc, newDocName);
            for (String childDocName : childDocNames) {
                XWikiDocument childDoc = getContext().getDocument(childDocName);
                childDoc.setParent(newDocName);
                getContext().saveDocument(childDoc);
            }
        } else {
            throw new DavException(DavServletResponse.SC_BAD_REQUEST);
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean isCollection()
    {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public void spool(OutputContext outputContext) throws IOException
    {
        throw new IOException("Collection resources can't be spooled");
    }

    /**
     * {@inheritDoc}
     */
    public long getModificationTime()
    {
        if (exists()) {
            return doc.getContentUpdateDate().getTime();
        }
        return IOUtil.UNDEFINED_TIME;
    }

    /**
     * @return The document represented by this resource.
     */
    public XWikiDocument getDocument()
    {
        return this.doc;
    }
}
