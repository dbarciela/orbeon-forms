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
package org.orbeon.oxf.processor.generator;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.dom4j.Document;
import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.XLSUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.xml.sax.ContentHandler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.StringTokenizer;

/**
 * NOTE: This generator depends on the Servlet API.
 */
public class XLSGenerator extends org.orbeon.oxf.processor.ProcessorImpl {

    public XLSGenerator() {
        addOutputInfo(new org.orbeon.oxf.processor.ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public org.orbeon.oxf.processor.ProcessorOutput createOutput(String name) {
        org.orbeon.oxf.processor.ProcessorOutput output = new org.orbeon.oxf.processor.ProcessorImpl.ProcessorOutputImpl(getClass(), name) {
            public void readImpl(org.orbeon.oxf.pipeline.api.PipelineContext context, ContentHandler contentHandler) {

                try {
                    byte[] fileContent = (byte[]) context.getAttribute(PipelineContext.XLS_GENERATOR_LAST_FILE);
                    if (fileContent == null)
                        throw new OXFException("No file was uploaded");
                    DOMGenerator domGenerator = new DOMGenerator(extractFromXLS(new ByteArrayInputStream(fileContent)));
                    domGenerator.createOutput(OUTPUT_DATA).read(context, contentHandler);

                } catch (IOException e) {
                    throw new OXFException(e);
                }
            }

            private Document extractFromXLS(InputStream inputStream) throws IOException {

                // Create workbook
                HSSFWorkbook workbook = new HSSFWorkbook(new POIFSFileSystem(inputStream));

                // Create document
                DocumentFactory factory = DocumentFactory.getInstance();
                final Document resultDocument = factory.createDocument();
                resultDocument.setRootElement(factory.createElement("workbook"));

                // Add elements for each sheet
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    HSSFSheet sheet = workbook.getSheetAt(i);

                    final Element element = factory.createElement("sheet");
                    resultDocument.getRootElement().add(element);

                    // Go though each cell
                    XLSUtils.walk(workbook.createDataFormat(), sheet, new XLSUtils.Handler() {
                        public void cell(HSSFCell cell, String sourceXPath, String targetXPath) {
                            if (targetXPath != null) {
                                int cellType = cell.getCellType();
                                String value = null;
                                switch (cellType) {
                                    case HSSFCell.CELL_TYPE_STRING:
                                    case HSSFCell.CELL_TYPE_BLANK:
                                        value = cell.getStringCellValue();
                                        break;
                                    case HSSFCell.CELL_TYPE_NUMERIC:
                                        double doubleValue = cell.getNumericCellValue();
                                        if (((double) ((int) doubleValue)) == doubleValue) {
                                            // This is an integer
                                            value = Integer.toString((int) doubleValue);
                                        } else {
                                            // This is a floating point number
                                            value = XMLUtils.removeScientificNotation(doubleValue);
                                        }
                                        break;
                                }
                                if (value == null)
                                    throw new OXFException("Unkown cell type " + cellType
                                            + " for XPath expression '" + targetXPath + "'");
                                addToElement(element, targetXPath, value);
                            }
                        }
                    });
                }

                return resultDocument;
            }

            private void addToElement(Element element, String xpath, String value) {
                DocumentFactory factory = DocumentFactory.getInstance();
                StringTokenizer elements = new StringTokenizer(xpath, "/");

                while (elements.hasMoreTokens()) {
                    String name = elements.nextToken();
                    if (elements.hasMoreTokens()) {
                        // Not the last: try to find sub element, otherwise create
                        Element child = element.element(name);
                        if (child == null) {
                            child = factory.createElement(name);
                            element.add(child);
                        }
                        element = child;
                    } else {
                        // Last: add element, set content to value
                        Element child = factory.createElement(name);
                        child.add(factory.createText(value));
                        element.add(child);
                    }
                }
            }
        };
        addOutput(name, output);
        return output;
    }
}
