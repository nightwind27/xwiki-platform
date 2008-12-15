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
package com.xpn.xwiki.plugin.webdav.resources.partial;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.apache.jackrabbit.webdav.DavConstants;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.DavMethods;
import org.apache.jackrabbit.webdav.DavResource;
import org.apache.jackrabbit.webdav.DavResourceFactory;
import org.apache.jackrabbit.webdav.DavResourceLocator;
import org.apache.jackrabbit.webdav.DavServletResponse;
import org.apache.jackrabbit.webdav.DavSession;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.io.InputContext;
import org.apache.jackrabbit.webdav.lock.ActiveLock;
import org.apache.jackrabbit.webdav.lock.LockDiscovery;
import org.apache.jackrabbit.webdav.lock.LockInfo;
import org.apache.jackrabbit.webdav.lock.LockManager;
import org.apache.jackrabbit.webdav.lock.Scope;
import org.apache.jackrabbit.webdav.lock.SupportedLock;
import org.apache.jackrabbit.webdav.lock.Type;
import org.apache.jackrabbit.webdav.property.DavProperty;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameIterator;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.apache.jackrabbit.webdav.property.DavPropertySet;
import org.apache.jackrabbit.webdav.property.DefaultDavProperty;
import org.apache.jackrabbit.webdav.property.ResourceType;

import com.xpn.xwiki.plugin.webdav.resources.XWikiDavResource;
import com.xpn.xwiki.plugin.webdav.resources.domain.DavTempFile;
import com.xpn.xwiki.plugin.webdav.utils.XWikiDavContext;

/**
 * The superclass for all XWiki WebDAV resources.
 * 
 * @version $Id$
 */
public abstract class AbstractDavResource implements XWikiDavResource
{
    /**
     * Name of this resource.
     */
    protected String name;

    /**
     * Resource locator for this resource. {@link DavResourceLocator}.
     */
    protected DavResourceLocator locator;

    /**
     * Parent resource (collection).
     */
    protected XWikiDavResource parentResource;

    /**
     * XWiki WebDAV Context. {@link XWikiDavContext}
     */
    private XWikiDavContext context;

    /**
     * {@inheritDoc}
     */
    public void init(XWikiDavResource parent, String name, String relativePath)
        throws DavException
    {
        DavResourceLocator locator =
            parent.getLocator().getFactory().createResourceLocator(
                parent.getLocator().getPrefix(), parent.getLocator().getWorkspacePath(),
                parent.getLocator().getResourcePath() + relativePath);
        init(name, locator, parent.getContext());
        this.parentResource = parent;

    }

    /**
     * {@inheritDoc}
     */
    public void init(String name, DavResourceLocator locator, XWikiDavContext context)
        throws DavException
    {
        this.name = name;
        this.locator = locator;
        this.context = context;
        // set fundamental properties (Will be overridden as necessary)
        // Some properties are cached and should not be overwritten.
        DavPropertySet propertySet = getVirtualProperties();
        if (propertySet.get(DavPropertyName.CREATIONDATE) == null) {
            String timeStamp = DavConstants.creationDateFormat.format(new Date());
            propertySet.add(new DefaultDavProperty(DavPropertyName.CREATIONDATE, timeStamp));
        }
        propertySet.add(new DefaultDavProperty(DavPropertyName.DISPLAYNAME, getDisplayName()));
        if (isCollection()) {
            propertySet.add(new ResourceType(ResourceType.COLLECTION));
            // Windows XP support
            propertySet.add(new DefaultDavProperty(DavPropertyName.ISCOLLECTION, "1"));
        } else {
            propertySet.add(new ResourceType(ResourceType.DEFAULT_RESOURCE));
            // Windows XP support
            propertySet.add(new DefaultDavProperty(DavPropertyName.ISCOLLECTION, "0"));
        }
        /*
         * set current lock information. If no lock is set to this resource, an empty lockdiscovery
         * will be returned in the response.
         */
        propertySet.add(new LockDiscovery(getLock(Type.WRITE, Scope.EXCLUSIVE)));
        /*
         * lock support information: all locks are lockable.
         */
        SupportedLock supportedLock = new SupportedLock();
        supportedLock.addEntry(Type.WRITE, Scope.EXCLUSIVE);
        propertySet.add(supportedLock);
    }

    /**
     * <p>
     * The default decode implementation assumes the next resource in chain to be a temporary
     * resource. Sub classes should override this method to provide their own implementation.
     * </p>
     */
    public void decode(Stack<XWikiDavResource> stack, String[] tokens, int next)
        throws DavException
    {
        if (next < tokens.length) {
            String nextToken = tokens[next];
            DavTempFile resource = new DavTempFile();
            String method = getContext().getMethod();
            if (method != null && DavMethods.getMethodCode(method) == DavMethods.DAV_MKCOL) {
                resource.setCollection();
            }
            resource.init(this, nextToken, "/" + nextToken);
            // Search inside session resources to see if we already have this resource stored.
            int index = getVirtualMembers().indexOf(resource);
            if (index != -1) {
                // Use the old resource instead.
                resource = (DavTempFile) getVirtualMembers().get(index);
                // Re-init the old resource.
                resource.init(this, nextToken, "/" + nextToken);
            }
            stack.push(resource);
            if (resource.isCollection()) {
                resource.decode(stack, tokens, next + 1);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean isLockable(Type type, Scope scope)
    {
        return Type.WRITE.equals(type) && Scope.EXCLUSIVE.equals(scope);
    }

    /**
     * {@inheritDoc}
     */
    public ActiveLock getLock(Type type, Scope scope)
    {
        return getContext().getLock(type, scope, this);
    }

    /**
     * {@inheritDoc}
     */
    public ActiveLock[] getLocks()
    {
        ActiveLock writeLock = getLock(Type.WRITE, Scope.EXCLUSIVE);
        return (writeLock != null) ? new ActiveLock[] {writeLock} : new ActiveLock[0];
    }

    /**
     * {@inheritDoc}
     */
    public boolean hasLock(Type type, Scope scope)
    {
        return getLock(type, scope) != null;
    }

    /**
     * {@inheritDoc}
     */
    public ActiveLock lock(LockInfo reqLockInfo) throws DavException
    {
        ActiveLock lock = null;
        if (isLockable(reqLockInfo.getType(), reqLockInfo.getScope())) {
            lock = getContext().createLock(reqLockInfo, this);
        } else {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED);
        }
        return lock;
    }

    /**
     * {@inheritDoc}
     */
    public ActiveLock refreshLock(LockInfo reqLockInfo, String lockToken) throws DavException
    {
        if (!exists()) {
            throw new DavException(DavServletResponse.SC_NOT_FOUND);
        }
        ActiveLock lock = getLock(reqLockInfo.getType(), reqLockInfo.getScope());
        if (lock == null) {
            throw new DavException(DavServletResponse.SC_PRECONDITION_FAILED);
        }
        return getContext().refreshLock(reqLockInfo, lockToken, this);
    }

    /**
     * {@inheritDoc}
     */
    public void unlock(String lockToken) throws DavException
    {
        ActiveLock lock = getLock(Type.WRITE, Scope.EXCLUSIVE);
        if (lock != null && lock.isLockedByToken(lockToken)) {
            getContext().releaseLock(lockToken, this);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void copy(DavResource destination, boolean shallow) throws DavException
    {
        throw new DavException(DavServletResponse.SC_NOT_IMPLEMENTED);
    }

    /**
     * Default implementation simply returns all the cached properties.
     * 
     * @return The set of properties associated with this resource.
     */
    public DavPropertySet getProperties()
    {
        return getVirtualProperties();
    }

    /**
     * {@inheritDoc}
     */
    public DavProperty getProperty(DavPropertyName name)
    {
        return getProperties().get(name);
    }

    /**
     * {@inheritDoc}
     */
    public DavPropertyName[] getPropertyNames()
    {
        return getProperties().getPropertyNames();
    }

    /**
     * {@inheritDoc}
     */
    public MultiStatusResponse alterProperties(DavPropertySet setProperties,
        DavPropertyNameSet removePropertyNames) throws DavException
    {
        getProperties().addAll(setProperties);
        DavPropertyNameIterator it = removePropertyNames.iterator();
        while (it.hasNext()) {
            removeProperty(it.nextPropertyName());
        }
        return createPropStat();
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public MultiStatusResponse alterProperties(List changeList) throws DavException
    {
        for (Object next : changeList) {
            if (next instanceof DavProperty) {
                DavProperty property = (DavProperty) next;
                setProperty(property);
            } else {
                DavPropertyName propertyName = (DavPropertyName) next;
                removeProperty(propertyName);
            }
        }
        return createPropStat();
    }

    /**
     * @return A {@link MultiStatusResponse} with all property statuses.
     */
    private MultiStatusResponse createPropStat()
    {
        DavPropertyNameSet propertyNameSet = new DavPropertyNameSet();
        for (DavPropertyName propertyName : getPropertyNames()) {
            propertyNameSet.add(propertyName);
        }
        return new MultiStatusResponse(this, propertyNameSet);
    }

    /**
     * {@inheritDoc}
     */
    public void removeProperty(DavPropertyName propertyName) throws DavException
    {
        getProperties().remove(propertyName);
    }

    /**
     * {@inheritDoc}
     */
    public void setProperty(DavProperty property) throws DavException
    {
        getProperties().add(property);
    }

    /**
     * {@inheritDoc}
     */
    public void addLockManager(LockManager lockmgr)
    {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public String getDisplayName()
    {
        return this.name;
    }

    /**
     * {@inheritDoc}
     */
    public String getComplianceClass()
    {
        return COMPLIANCE_CLASS;
    }

    /**
     * {@inheritDoc}
     */
    public String getSupportedMethods()
    {
        return METHODS;
    }

    /**
     * {@inheritDoc}
     */
    public DavResourceFactory getFactory()
    {
        return getContext().getResourceFactory();
    }

    /**
     * {@inheritDoc}
     */
    public DavResourceLocator getLocator()
    {
        return this.locator;
    }

    /**
     * {@inheritDoc}
     */
    public String getResourcePath()
    {
        return this.locator.getResourcePath();
    }

    /**
     * {@inheritDoc}
     */
    public String getHref()
    {
        return this.locator.getHref(isCollection());
    }

    /**
     * {@inheritDoc}
     */
    public DavSession getSession()
    {
        return getContext().getDavSession();
    }

    /**
     * {@inheritDoc}
     */
    public DavResource getCollection()
    {
        return this.parentResource;
    }

    /**
     * {@inheritDoc}
     */
    public XWikiDavContext getContext()
    {
        return context;
    }

    /**
     * {@inheritDoc}
     */
    public List<XWikiDavResource> getVirtualMembers()
    {
        Map<String, List<XWikiDavResource>> vResourcesMap =
            getContext().getUserStorage().getResourcesMap();
        if (vResourcesMap.get(getResourcePath()) == null) {
            vResourcesMap.put(getResourcePath(), new ArrayList<XWikiDavResource>());
        }
        return vResourcesMap.get(getResourcePath());
    }

    /**
     * {@inheritDoc}
     */
    public DavPropertySet getVirtualProperties()
    {
        Map<String, DavPropertySet> vPropertiesMap =
            getContext().getUserStorage().getPropertiesMap();
        if (vPropertiesMap.get(getResourcePath()) == null) {
            vPropertiesMap.put(getResourcePath(), new DavPropertySet());
        }
        return vPropertiesMap.get(getResourcePath());
    }

    /**
     * Utility method for adding temporary resources to current user's cache.
     * 
     * @param tempResource {@link DavTempFile} instance.
     * @param inputContext {@link InputContext}
     */
    public void addTempResource(DavTempFile tempResource, InputContext inputContext)
        throws DavException
    {
        boolean isFile = (inputContext.getInputStream() != null);
        long modificationTime = inputContext.getModificationTime();
        if (isFile) {
            byte[] data = null;
            data = getContext().getFileContentAsBytes(inputContext.getInputStream());
            tempResource.update(data, new Date(modificationTime));
        } else {
            tempResource.setModified(new Date(modificationTime));
        }
        // It's possible that we are updating an existing resource.
        if (!getVirtualMembers().contains(tempResource)) {
            getVirtualMembers().add(tempResource);
        }
    }

    /**
     * Utility method for removing a temporary resource from session resources.
     * 
     * @param tempResource {@link DavTempFile} to be removed.
     */
    public void removeTempResource(DavTempFile tempResource) throws DavException
    {
        if (getVirtualMembers().contains(tempResource)) {
            getVirtualMembers().remove(tempResource);
        } else {
            throw new DavException(DavServletResponse.SC_NOT_FOUND);
        }
    }

    /**
     * Checks if the given resource name corresponds to a temporary resource.
     * 
     * @param resourceName Name of the resource.
     * @return True if the resourceName corresponds to a temporary file / directory. False
     *         otherwise.
     */
    public boolean isTempResource(String resourceName)
    {
        return resourceName.startsWith(".") || resourceName.endsWith("~");
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object obj)
    {
        if (obj instanceof DavResource) {
            DavResource other = (DavResource) obj;
            return getResourcePath().equals(other.getResourcePath());
        }
        return false;
    }
}
