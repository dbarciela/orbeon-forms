<!--
    Copyright (C) 2004 Orbeon, Inc.
  
    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.
  
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.
  
    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<html xmlns="http://www.w3.org/1999/xhtml"
      xmlns:xforms="http://www.w3.org/2002/xforms"
      xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
      xsl:version="2.0">
    <head>
        <title>Hello World XForms</title>
    </head>
    <body>
        <xforms:group>
            <xsl:if test="/myform/first-name != ''">
                <p>Hello <xsl:value-of select="/myform/first-name"/>!</p>
            </xsl:if>
            <p>
                Please enter your first name:
                <xforms:input ref="/myform/first-name"/>
                <xforms:submit>
                    <xforms:label>Greet Me!</xforms:label>
                </xforms:submit>
            </p>
        </xforms:group>
    </body>
</html>
