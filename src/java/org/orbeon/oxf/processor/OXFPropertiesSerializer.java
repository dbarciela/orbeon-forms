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

import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.Node;
import org.dom4j.QName;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class OXFPropertiesSerializer extends ProcessorImpl {

    public static final String PROPERTIES_SCHEMA_URI = "http://www.orbeon.com/oxf/properties";

    private static final String SUPPORTED_TYPES_URI = XMLConstants.XSD_URI;
    private static final Map supportedTypes = new HashMap();
    static {
        supportedTypes.put("string", "");
        supportedTypes.put("integer", "");
        supportedTypes.put("boolean", "");
        supportedTypes.put("date", "");
        supportedTypes.put("dateTime", "");
        supportedTypes.put("QName", "");
        supportedTypes.put("anyURI", "");
    }

    public OXFPropertiesSerializer() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA, PROPERTIES_SCHEMA_URI));
    }

    public PropertyStore getPropertyStore(PipelineContext context) {
        return (PropertyStore) context.getAttribute(this);
    }

    public void start(PipelineContext context) {
        PropertyStore propertyStore = (PropertyStore) readCacheInputAsObject(context, getInputByName(INPUT_DATA),
                new CacheableInputReader() {
                    public Object read(PipelineContext context, ProcessorInput input) {
                        PropertyStore propertyStore = new PropertyStore();

                        Node propertiesNode = readInputAsDOM4J(context, input);
                        for (Iterator i = XPathUtils.selectIterator(propertiesNode, "/properties/property"); i.hasNext();) {
                            Element propertyElement = (Element) i.next();

                            // Extract attributes
                            String processorName = propertyElement.attributeValue("processor-name");
                            String as = propertyElement.attributeValue("as");
                            String name = propertyElement.attributeValue("name");
                            String value = propertyElement.attributeValue("value");

                            if (as != null) {
                                // We only support one namespace for types for now
                                QName typeQName = XMLUtils.extractAttributeValueQName(propertyElement, "as");
                                if (!typeQName.getNamespaceURI().equals(SUPPORTED_TYPES_URI))
                                    throw new ValidationException("Invalid as attribute: " + typeQName.getQualifiedName(), (LocationData) propertyElement.getData());

                                if (supportedTypes.get(typeQName.getName()) == null)
                                    throw new ValidationException("Invalid as attribute: " + typeQName.getQualifiedName(), (LocationData) propertyElement.getData());

                                if (processorName != null) {
                                    // Processor-specific property
                                    QName processorQName = XMLUtils.extractAttributeValueQName(propertyElement, "processor-name");
                                    propertyStore.setProperty(propertyElement, processorQName, name, typeQName, value);
                                } else {
                                    // Global property
                                    propertyStore.setProperty(propertyElement, name, typeQName, value);
                                }
                            } else {
                                // [BACKWARD COMPATIBILITY] get type attribute
                                String type = propertyElement.attributeValue("type");
                                if (supportedTypes.get(type) == null)
                                    throw new ValidationException("Invalid type attribute: " + type, (LocationData) propertyElement.getData());
                                propertyStore.setProperty(propertyElement, name, new QName(type, new Namespace("xs", SUPPORTED_TYPES_URI)), value);
                            }
                        }
                        return propertyStore;
                    }
                });
        context.setAttribute(this, propertyStore);
    }

    public static class PropertyStore {

        private OXFProperties.PropertySet globalPropertySet = new OXFProperties.PropertySet();
        private Map processorPropertySets = new HashMap();

        public void setProperty(Element element, String name, QName type, String value) {
            globalPropertySet.setProperty(element, name, type, value);
        }

        public void setProperty(Element element, QName processorName, String name, QName type, String value) {
            getProcessorPropertySet(processorName).setProperty(element, name, type, value);
        }


        public OXFProperties.PropertySet getGlobalPropertySet() {
            return globalPropertySet;
        }

        public OXFProperties.PropertySet getProcessorPropertySet(QName processorName) {
            OXFProperties.PropertySet propertySet = (OXFProperties.PropertySet) processorPropertySets.get(processorName);
            if (propertySet == null) {
                propertySet = new OXFProperties.PropertySet();
                processorPropertySets.put(processorName, propertySet);
            }
            return propertySet;
        }
    }
}
