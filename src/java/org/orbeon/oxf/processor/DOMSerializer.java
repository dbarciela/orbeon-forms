/**
 *  Copyright (C) 2004 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.processor;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.DOMWriter;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xml.XMLUtils;

/**
 * Serializes the data input in a DOM. Once serialized, the content can
 * be retrieved with getNode() method.
 */
public class DOMSerializer extends ProcessorImpl {

    public DOMSerializer() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
    }

    public Document getNode(PipelineContext context) {
        return (Document) context.getAttribute(this);
    }

    public org.w3c.dom.Document getW3CDocument(PipelineContext context) {
        DOMWriter writer = new DOMWriter(XMLUtils.createDocument().getClass());
        try {
            return writer.write(getNode(context));
        } catch (DocumentException e) {
            throw new OXFException(e);
        }
    }

    public void start(org.orbeon.oxf.pipeline.api.PipelineContext context) {
        context.setAttribute(this, readCacheInputAsDOM4J(context, INPUT_DATA));
    }
}
