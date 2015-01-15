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
package org.xwiki.lesscss.internal;

import javax.inject.Inject;

import org.xwiki.component.annotation.Component;
import org.xwiki.context.Execution;
import org.xwiki.context.ExecutionContext;

import groovy.lang.Singleton;

/**
 * Store and get LESS configuration variables from the execution context of the request.
 *
 * @version $Id$
 * @since 6.2.5
 */
@Component(roles = LESSContext.class)
@Singleton
public class LESSContext
{
    private static final String CACHE_PROPERTY = "less.cache.disable";

    @Inject
    private Execution execution;

    /**
     * Disable the LESS cache.
     */
    public void disableCache()
    {
        getContext().newProperty(CACHE_PROPERTY).inherited().initial(true).declare();
    }

    /**
     * Stop disabling the LESS cache.
     */
    public void stopDisablingCache()
    {
        getContext().setProperty(CACHE_PROPERTY, false);
    }

    /**
     * @return if the cache is disabled
     */
    public boolean isCacheDisabled()
    {
        return Boolean.TRUE.equals(getContext().getProperty(CACHE_PROPERTY));
    }

    private ExecutionContext getContext()
    {
        return execution.getContext();
    }
}
