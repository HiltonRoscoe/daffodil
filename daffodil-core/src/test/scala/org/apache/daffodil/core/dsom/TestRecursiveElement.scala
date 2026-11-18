/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.daffodil.core.dsom

import scala.xml.Elem
import scala.xml.Node

import org.apache.daffodil.core.util.TestUtils
import org.apache.daffodil.lib.util.SchemaUtils
import org.apache.daffodil.lib.xml.NoNamespace
import org.apache.daffodil.lib.xml.XMLUtils

import org.junit.Test

/**
 * A global element that refers to itself used to deadlock schema compilation.
 * PDF's nested dictionaries and arrays need that shape.
 */
class TestRecursiveElement {

  private val includeGeneralFormat =
    <xs:include schemaLocation="/org/apache/daffodil/xsd/DFDLGeneralFormat.dfdl.xsd"/>

  private def choiceOfStopOrDown(ref: String): Elem =
    <xs:element name="pdf">
      <xs:complexType>
        <xs:choice>
          <xs:element name="stop" type="xs:string" dfdl:initiator="S"
                      dfdl:lengthKind="explicit" dfdl:length="1"/>
          <xs:sequence dfdl:initiator="D">
            <xs:element name="down" type="xs:string"
                        dfdl:lengthKind="explicit" dfdl:length="1"/>
            <xs:element ref={ref}/>
          </xs:sequence>
        </xs:choice>
      </xs:complexType>
    </xs:element>

  private val namespacedSchema = SchemaUtils.dfdlTestSchema(
    includeGeneralFormat,
    <dfdl:format ref="tns:GeneralFormat" encoding="ASCII"/>,
    choiceOfStopOrDown("tns:pdf")
  )

  private val noNamespaceSchema = SchemaUtils.dfdlTestSchema(
    includeGeneralFormat,
    <dfdl:format ref="GeneralFormat" encoding="ASCII"/>,
    choiceOfStopOrDown("pdf"),
    targetNamespace = NoNamespace,
    defaultNamespace = NoNamespace,
    elementFormDefault = "unqualified",
    useDefaultNamespace = false,
    useTNS = false
  )

  @Test def testRecursiveElementParsesAndUnparses(): Unit = {
    val (_, actual) = TestUtils.testString(namespacedSchema, "DADBSQ")
    val expected: Node =
      <pdf><down>A</down><pdf><down>B</down><pdf><stop>Q</stop></pdf></pdf></pdf>
    XMLUtils.compareAndReport(expected, actual)
    TestUtils.testUnparsing(namespacedSchema, actual, "DADBSQ")
  }

  @Test def testRecursiveElementNoNamespace(): Unit = {
    val (_, actual) = TestUtils.testString(noNamespaceSchema, "SQ")
    val expected: Node = <pdf><stop>Q</stop></pdf>
    XMLUtils.compareAndReport(expected, actual)
  }

  @Test def testOptionalElementStopsAtMaxOccurs(): Unit = {
    val schema = SchemaUtils.dfdlTestSchema(
      includeGeneralFormat,
      <dfdl:format ref="tns:GeneralFormat" encoding="ASCII" occursCountKind="implicit"/>,
      <xs:element name="pdf">
        <xs:complexType>
          <xs:sequence>
            <xs:element name="a" type="xs:string" minOccurs="0" maxOccurs="1"
                        dfdl:lengthKind="explicit" dfdl:length="1"/>
            <xs:element name="b" type="xs:string"
                        dfdl:lengthKind="explicit" dfdl:length="1"/>
          </xs:sequence>
        </xs:complexType>
      </xs:element>
    )
    val (_, actual) = TestUtils.testString(schema, "AB")
    val expected: Node = <pdf><a>A</a><b>B</b></pdf>
    XMLUtils.compareAndReport(expected, actual)
  }
}
