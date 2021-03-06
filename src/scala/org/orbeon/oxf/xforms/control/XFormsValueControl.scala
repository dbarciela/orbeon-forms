/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.control

import org.dom4j.Element
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.xforms.XFormsProperties
import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xforms.analysis.XPathDependencies
import org.orbeon.oxf.xforms.event.XFormsEvent
import org.orbeon.oxf.xforms.event.XFormsEvents
import org.orbeon.oxf.xforms.event.events.XXFormsValue
import org.orbeon.oxf.xforms.model.DataModel
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.saxon.om.Item
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.saxon.value._
import collection.JavaConverters._
import java.util.{Collections ⇒ JCollections, Set ⇒ JSet}
import org.orbeon.oxf.xml.{ContentHandlerHelper, NamespaceMapping, XMLConstants}

/**
 * Base class for all controls that hold a value.
 */
abstract class XFormsValueControl(container: XBLContainer, parent: XFormsControl, element: Element, effectiveId: String)
    extends XFormsSingleNodeControl(container, parent, element, effectiveId) {

    // Value
    private var value: String = null // TODO: use ControlProperty<String>?

    // Previous value for refresh
    private var previousValue: String = null

    // External value (evaluated lazily)
    private var isExternalValueEvaluated: Boolean = false
    private var externalValue: String = null

    override def onCreate(): Unit = {
        super.onCreate()

        value = null
        previousValue = null

        markExternalValueDirty()
    }

    override def evaluateImpl(): Unit = {

        // Evaluate other aspects of the control if necessary
        super.evaluateImpl()

        // Evaluate control values
        if (isRelevant) {
            // Control is relevant
            if (value eq null)
                // Only evaluate if the value is not already available
                evaluateValue()
        } else {
            // Control is not relevant
            isExternalValueEvaluated = true
            value = null
        }

        // NOTE: We no longer evaluate the external value here, instead we do lazy evaluation. This is good in particular when there
        // are multiple refreshes during an Ajax request, and LHHA values are only needed in the end.
    }

    override def markDirtyImpl(xpathDependencies: XPathDependencies): Unit = {
        super.markDirtyImpl(xpathDependencies)

        // Handle value update
        if (xpathDependencies.requireValueUpdate(getPrefixedId)) {
            value = null
            // Always mark the external value dirty if the value is dirty
            markExternalValueDirty()
        }
    }

    def evaluateValue(): Unit =
        setValue(DataModel.getValue(getBoundItem))

    def evaluateExternalValue(): Unit =
        // By default, same as value
        setExternalValue(getValue)

    protected def markExternalValueDirty(): Unit = {
        isExternalValueEvaluated = false
        externalValue = null
    }

    protected def isExternalValueDirty: Boolean =
        ! isExternalValueEvaluated

    override def isValueChanged: Boolean = {
        val result = ! XFormsUtils.compareStrings(previousValue, value)
        previousValue = value
        result
    }

    /**
     * Notify the control that its value has changed due to external user interaction. The value passed is a value as
     * understood by the UI layer.
     *
     * @param value             the new external value
     */
    def storeExternalValue(value: String): Unit = {
        // Set value into the instance

        // NOTE: Standard value controls should be bound to simple content only. Is there anything we should / can do
        // about this? See: https://github.com/orbeon/orbeon-forms/issues/13


        val boundItem: Item = getBoundItem
        if (! boundItem.isInstanceOf[NodeInfo])// this should not happen
            throw new OXFException("Control is no longer bound to a node. Cannot set external value.")
        DataModel.jSetValueIfChanged(containingDocument, getIndentedLogger, this, getLocationData, boundItem.asInstanceOf[NodeInfo], value, "client", false)

        // NOTE: We do *not* call evaluate() here, as that will break the difference engine. doSetValue() above marks
        // the controls as dirty, and they will be evaluated when necessary later.
    }

    final protected def getValueUseFormat(format: String): String = {
        assert(isRelevant)
        assert(getValue ne null)

//                final String lang = XFormsUtils.resolveXMLang(getControlElement());// this could be done as part of the static analysis?
//                format = XFormsProperties.getTypeOutputFormat(containingDocument, typeName, lang);

        if (format eq null)
            // Try default format for known types
            Option(getBuiltinTypeName) flatMap
                (typeName ⇒ Option(XFormsProperties.getTypeOutputFormat(containingDocument, typeName))) map
                    (outputFormat ⇒ evaluateAsString(
                        StringValue.makeStringValue(getValue),
                        outputFormat,
                        XFormsValueControl.FORMAT_NAMESPACE_MAPPING,
                        getContextStack.getCurrentVariables)) orNull
        else
            // Format value according to format attribute
            evaluateAsString(format, JCollections.singletonList[Item](StringValue.makeStringValue(getValue)), 1)
    }

    /**
     * Return the control's internal value.
     */
    final def getValue = value

    /**
     * Return the control's external value is the value as exposed to the UI layer.
     */
    final def getExternalValue(): String = {
        if (! isExternalValueEvaluated) {
            if (isRelevant)
                evaluateExternalValue()
            else
                // NOTE: if the control is not relevant, nobody should ask about this in the first place
                setExternalValue(null)

            isExternalValueEvaluated = true
        }
        externalValue
    }

    /**
     * Return the external value ready to be inserted into the client after an Ajax response.
     */
    def getEscapedExternalValue = getExternalValue

    protected final def setValue(value: String): Unit =
        this.value = value

    protected final def setExternalValue(externalValue: String): Unit =
        this.externalValue = externalValue

    def getNonRelevantEscapedExternalValue: String =  ""

    override def getBackCopy: AnyRef = {
        // Evaluate lazy values
        getExternalValue()
        super.getBackCopy
    }

    override def equalsExternal(other: XFormsControl): Boolean = {
        if ((other eq null) || ! other.isInstanceOf[XFormsValueControl])
            return false

        if (this eq other)
            return true

        val otherValueControl = other.asInstanceOf[XFormsValueControl]

        // Compare on external value, not internal value
        if (getExternalValue != otherValueControl.getExternalValue)
            return false

        super.equalsExternal(other)
    }

    override def getAllowedExternalEvents: JSet[String] =
        XFormsValueControl.ALLOWED_EXTERNAL_EVENTS

    override def performDefaultAction(event: XFormsEvent): Unit = event match {
        case xxformsValue: XXFormsValue ⇒ storeExternalValue(xxformsValue.getNewValue)
        case _ ⇒ super.performDefaultAction(event)
    }

    override def toXML(helper: ContentHandlerHelper, attributes: List[String])(content: ⇒ Unit): Unit =
        super.toXML(helper, attributes) {
            helper.text(getExternalValue())
        }
}

object XFormsValueControl {
    // Assume xs: prefix for default formats
    val FORMAT_NAMESPACE_MAPPING = new NamespaceMapping(Map(XMLConstants.XSD_PREFIX → XMLConstants.XSD_URI).asJava)

    private final val ALLOWED_EXTERNAL_EVENTS = Set(
        XFormsEvents.XFORMS_FOCUS,
        XFormsEvents.XFORMS_HELP,
        XFormsEvents.DOM_ACTIVATE,
        XFormsEvents.XXFORMS_VALUE,
        XFormsEvents.XXFORMS_VALUE_OR_ACTIVATE).asJava// for noscript mode
}