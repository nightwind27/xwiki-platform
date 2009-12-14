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
package org.xwiki.officeimporter.internal.splitter;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;

import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;
import org.xwiki.model.DocumentName;
import org.xwiki.officeimporter.document.XDOMOfficeDocument;
import org.xwiki.officeimporter.internal.AbstractOfficeImporterTest;
import org.xwiki.officeimporter.internal.splitter.TargetPageDescriptor;
import org.xwiki.officeimporter.splitter.XDOMOfficeDocumentSplitter;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.parser.Parser;

/**
 * Test case for {@link DefaultXDOMOfficeDocumentSplitter}.
 * 
 * @version $Id$
 * @since 2.1M1
 */
public class DefaultXDOMOfficeDocumentSplitterTest extends AbstractOfficeImporterTest
{
    /**
     * Parser for building xdom instances.
     */
    private Parser xwikiSyntaxParser;

    /**
     * Document splitter for testing.
     */
    private XDOMOfficeDocumentSplitter officeDocumentSplitter;
    
    /**
     * {@inheritDoc}
     */
    @Before
    public void setUp() throws Exception
    {
        super.setUp();
        this.xwikiSyntaxParser = getComponentManager().lookup(Parser.class, "xwiki/2.0");
        this.officeDocumentSplitter = getComponentManager().lookup(XDOMOfficeDocumentSplitter.class);
    }

    /**
     * Test basic document splitting.
     */
    @Test
    public void testDocumentSplitting() throws Exception
    {
        // Create xwiki/2.0 document.
        StringBuffer buffer = new StringBuffer();
        buffer.append("=Heading1=").append("\n");
        buffer.append("Content").append("\n");
        buffer.append("==Heading11==").append("\n");
        buffer.append("Content").append("\n");
        buffer.append("==Heading12==").append("\n");
        buffer.append("Content").append("\n");
        buffer.append("=Heading2=").append("\n");
        buffer.append("Content").append("\n");
        XDOM xdom = xwikiSyntaxParser.parse(new StringReader(buffer.toString()));

        // Create xdom office document.
        XDOMOfficeDocument officeDocument =
            new XDOMOfficeDocument(xdom, new HashMap<String, byte[]>(), getComponentManager());
        final DocumentName baseDocument = new DocumentName("xwiki", "Test", "Test");

        // Add expectations to mock document name serializer. 
        this.context.checking(new Expectations() {{
                allowing(mockDocumentNameSerializer).serialize(baseDocument);
                will(returnValue("xwiki:Test.Test"));
                allowing(mockDocumentNameSerializer).serialize(new DocumentName("xwiki", "Test", "Heading1"));
                will(returnValue("xwiki:Test.Heading1"));
                allowing(mockDocumentNameSerializer).serialize(new DocumentName("xwiki", "Test", "Heading11"));
                will(returnValue("xwiki:Test.Heading11"));                
                allowing(mockDocumentNameSerializer).serialize(new DocumentName("xwiki", "Test", "Heading12"));
                will(returnValue("xwiki:Test.Heading12"));                
                allowing(mockDocumentNameSerializer).serialize(new DocumentName("xwiki", "Test", "Heading2"));
                will(returnValue("xwiki:Test.Heading2"));                
        }});
        
        // Add expectations to mock document name factory.         
        this.context.checking(new Expectations() {{
                allowing(mockDocumentNameFactory).createDocumentName("xwiki:Test.Test");
                will(returnValue(new DocumentName("xwiki", "Test", "Test")));
                allowing(mockDocumentNameFactory).createDocumentName("xwiki:Test.Heading1");
                will(returnValue(new DocumentName("xwiki", "Test", "Heading1")));
                allowing(mockDocumentNameFactory).createDocumentName("xwiki:Test.Heading11");
                will(returnValue(new DocumentName("xwiki", "Test", "Heading11")));
                allowing(mockDocumentNameFactory).createDocumentName("xwiki:Test.Heading12");
                will(returnValue(new DocumentName("xwiki", "Test", "Heading12")));
                allowing(mockDocumentNameFactory).createDocumentName("xwiki:Test.Heading2");
                will(returnValue(new DocumentName("xwiki", "Test", "Heading2")));
        }});
        
        // Add expectations to mock document access bridge.
        this.context.checking(new Expectations() {{
            allowing(mockDocumentAccessBridge).exists("xwiki:Test.Heading1");
            will(returnValue(false));
            allowing(mockDocumentAccessBridge).exists("xwiki:Test.Heading11");
            will(returnValue(false));
            allowing(mockDocumentAccessBridge).exists("xwiki:Test.Heading12");
            will(returnValue(false));
            allowing(mockDocumentAccessBridge).exists("xwiki:Test.Heading2");
            will(returnValue(false));
        }});

        // Perform the split operation.
        Map<TargetPageDescriptor, XDOMOfficeDocument> result =
            officeDocumentSplitter.split(officeDocument, new int[] {1, 2, 3, 4, 5, 6}, "headingNames", baseDocument);
        
        // There should be five xdom office documents.
        Assert.assertEquals(5, result.size());
    }
}
