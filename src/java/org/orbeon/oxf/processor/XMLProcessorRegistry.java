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

import org.apache.log4j.Logger;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.XPathUtils;

import java.util.Iterator;

public class XMLProcessorRegistry extends ProcessorImpl {

    static private Logger logger = LoggerFactory.createLogger(XMLProcessorRegistry.class);

    public XMLProcessorRegistry() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG));
    }

    public void start(PipelineContext context) {
        try {
            Node config = readInputAsDOM4J(context, INPUT_CONFIG);
            final Object configValidity = getInputValidity(context, getInputByName(INPUT_CONFIG));
            for (Iterator i = XPathUtils.selectIterator(config, "/processors/processor"); i.hasNext();) {
                Element processorElement = (Element) i.next();

                // Extract processor name
                final QName processorQName = extractProcessorQName(processorElement);
                final String processorURI = extractProcessorURI(processorElement);// [BACKWARD COMPATIBILITY]
                if (processorQName == null && processorURI == null)
                    throw new OXFException("Missing or empty processor name!");

                if (processorQName != null)
                    logger.debug("Binding name: " + XMLUtils.qNameToexplodedQName(processorQName));
                if (processorURI != null)
                    logger.debug("Binding name: " + processorURI);

                // Defined as a class
                Node classDef = XPathUtils.selectSingleNode(processorElement, "class");
                if (classDef != null) {
                    final String className = XPathUtils.selectStringValueNormalize(classDef, "@name");
                    if (logger.isDebugEnabled())
                        logger.debug("To class: " + className);

                    final String defaultName = (processorQName != null) ? XMLUtils.qNameToexplodedQName(processorQName) : processorURI;
                    final QName defaultQName = (processorQName != null) ? processorQName : new QName(processorURI);

                    ProcessorFactory processorFactory = new ProcessorFactory() {
                        public Processor createInstance(PipelineContext context) {
                            try {
                                Processor processor = (Processor) Class.forName(className).newInstance();
                                processor.setName(defaultQName);
                                return processor;
                            } catch (ClassNotFoundException e) {
                                throw new OXFException("Cannot load processor '" + defaultName
                                        + "' because the class implementing this processor ('"
                                        + className + "') cannot be found");
                            } catch (NoClassDefFoundError e) {
                                throw new OXFException("Cannot load processor '" + defaultName
                                        + "' because it needs a class that cannot be loaded: '"
                                        + e.getMessage() + "'");
                            } catch (Exception e) {
                                throw new OXFException(e);
                            }
                        }
                    };

                    if (processorQName != null)
                        ProcessorFactoryRegistry.bind(processorQName, processorFactory);
                    if (processorURI != null)
                        ProcessorFactoryRegistry.bind(processorURI, processorFactory);
                }

                // Defined based on an other processor (instantiation)
                final Element instantiationDef = (Element) XPathUtils.selectSingleNode(processorElement, "instantiation");
                if (instantiationDef != null) {

                    ProcessorFactory processorFactory = new ProcessorFactory() {
                         public Processor createInstance(PipelineContext context) {
                            try {
                                // Find base processor
                                final QName processorQName = extractProcessorQName(instantiationDef);
                                final String processorURI = extractProcessorURI(instantiationDef);// [BACKWARD COMPATIBILITY]
                                if (processorQName == null && processorURI == null)
                                    throw new OXFException("Missing or empty processor name!");

                                if (processorQName != null)
                                    logger.debug("Binding name: " + XMLUtils.qNameToexplodedQName(processorQName));
                                if (processorURI != null)
                                    logger.debug("Binding name: " + processorURI);

                                final QName defaultQName = (processorQName != null) ? processorQName : new QName(processorURI);

                                Processor baseProcessor = ((processorQName != null)
                                        ? ProcessorFactoryRegistry.lookup(processorQName)
                                        : ProcessorFactoryRegistry.lookup(processorURI)).createInstance(context);
                                // Override the name - can this have unexpected consequences?
                                baseProcessor.setName(defaultQName);

                                for (Iterator j = XPathUtils.selectIterator(instantiationDef, "input"); j.hasNext();) {
                                    Node config = (Node) j.next();
                                    String name = XPathUtils.selectStringValueNormalize(config, "@name");
                                    String src = XPathUtils.selectStringValueNormalize(config, "@src");

                                    if (src != null) {
                                        // Connect to resource generator
                                        Processor resourceGenerator = PipelineUtils.createURLGenerator(src);
                                        PipelineUtils.connect(resourceGenerator, OUTPUT_DATA, baseProcessor, name);
                                    } else {
                                        // We must have some XML in the <input> tag
                                        Processor domGenerator = PipelineUtils.createDOMGenerator
                                                (XPathUtils.selectSingleNode(config, "*"), configValidity);
                                        PipelineUtils.connect(domGenerator, OUTPUT_DATA, baseProcessor, name);
                                    }
                                }
                                return baseProcessor;
                            } catch (Exception e) {
                                throw new OXFException(e);
                            }
                        }
                    };

                    if (processorQName != null)
                        ProcessorFactoryRegistry.bind(processorQName, processorFactory);
                    if (processorURI != null)
                        ProcessorFactoryRegistry.bind(processorURI, processorFactory);
                }
            }
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public static QName extractProcessorQName(Element processorElement) {
        return XMLUtils.extractAttributeValueQName(processorElement, "name");
    }

    public static String extractProcessorURI(Element processorElement) {
        // [BACKWARD COMPATIBILITY] Extract URI
        String uri = XPathUtils.selectStringValueNormalize(processorElement, "@uri");
        if (uri == null || uri.trim().length() == 0)
            return null;
        else
            return uri.trim();
    }
}
