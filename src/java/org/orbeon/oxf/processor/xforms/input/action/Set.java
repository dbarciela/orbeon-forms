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
package org.orbeon.oxf.processor.xforms.input.action;

import org.dom4j.Document;
import org.jaxen.FunctionContext;

import java.util.HashMap;
import java.util.Map;

public class Set implements Action {

    private SetValue setValue = new SetValue();

    public void setParameters(Map parameters) {
        Map setValueParameters = new HashMap();
        setValueParameters.put("ref", parameters.get("ref"));
        setValueParameters.put("content", parameters.get("value"));
        setValue.setParameters(setValueParameters);
    }

    public void run(FunctionContext functionContext, Document instance) {
        setValue.run(functionContext, instance);
    }
}
