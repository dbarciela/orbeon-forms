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
import org.orbeon.oxf.common.ValidationException;
import org.xml.sax.ContentHandler;

public class ErrorProcessor extends ProcessorImpl {

    public ErrorProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, INPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(org.orbeon.oxf.pipeline.api.PipelineContext context, ContentHandler contentHandler) {
                Document config = readCacheInputAsDOM4J(context, INPUT_CONFIG);
                throw new ValidationException((String) config.selectObject("string(/)"), getLocationData());
            }
        };
        addOutput(name, output);
        return output;
    };
}
