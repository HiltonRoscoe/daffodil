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

package org.apache.daffodil.core.runtime1

import org.apache.daffodil.core.grammar.Gram
import org.apache.daffodil.lib.exceptions.Assert
import org.apache.daffodil.lib.util.Maybe
import org.apache.daffodil.runtime1.infoset.ForwardingInfosetBuilder
import org.apache.daffodil.runtime1.infoset.InfosetBuilder
import org.apache.daffodil.runtime1.infoset.NadaInfosetBuilder
import org.apache.daffodil.runtime1.processors.RuntimeData
import org.apache.daffodil.runtime1.processors.parsers.ForwardingParser
import org.apache.daffodil.runtime1.processors.parsers.Parser
import org.apache.daffodil.runtime1.processors.unparsers.ForwardingUnparser
import org.apache.daffodil.runtime1.processors.unparsers.Unparser

trait GramRuntime1Mixin { self: Gram =>

  /**
   * Provides parser.
   *
   * Required to examine child parsers, and optimize itself out by propagating NadaParser if there is no parser.
   *
   * Recursive schemas re-enter this while it is still running. A Scala lazy val
   * would deadlock on that thread, so the implementation is cached here and a
   * re-entrant call receives a forwarding parser that is tied to the real one
   * when construction finishes.
   */
  final def parser: Parser = parserSlot.get(
    new ForwardingParser(forwardRuntimeData),
    parserImpl,
    (forward, real) => forward.asInstanceOf[ForwardingParser].delegate = real
  )

  protected def parserImpl: Parser

  private val parserSlot = new ForwardSlot[Parser]

  final def maybeParser: Maybe[Parser] = {
    if (this.isEmpty) Maybe.Nope
    else {
      val p = this.parser
      if (p.isEmpty) Maybe.Nope
      else Maybe(p)
    }
  }

  protected final def hasNoParser: Parser = Assert.invariantFailed("Has no parser.")
  protected final def hasNoUnparser: Unparser = Assert.invariantFailed("Has no unparser.")

  /**
   * Provides unparser.
   *
   * Required to examine child unparsers, and optimize itself out by propagating NadaUnparser if there is no unparser.
   *
   * See `parser` for why this is not a lazy val.
   */
  final def unparser: Unparser = unparserSlot.get(
    new ForwardingUnparser(forwardRuntimeData),
    unparserImpl,
    (forward, real) => forward.asInstanceOf[ForwardingUnparser].delegate = real
  )

  protected def unparserImpl: Unparser

  private val unparserSlot = new ForwardSlot[Unparser]

  final lazy val maybeUnparser: Maybe[Unparser] = {
    if (this.isEmpty) Maybe.Nope
    else {
      val u = this.unparser
      if (u.isEmpty) Maybe.Nope
      else Maybe(u)
    }
  }

  /**
   * Provides this Gram's node in the dedicated InfosetBuilder tree that parallels
   * the Unparser tree. Most Grams create or select no infoset content and
   * inherit this Nada default; only those that do override it.
   */
  final def builder: InfosetBuilder = builderSlot.get(
    new ForwardingInfosetBuilder,
    builderImpl,
    (forward, real) => forward.asInstanceOf[ForwardingInfosetBuilder].delegate = real
  )

  protected def builderImpl: InfosetBuilder = NadaInfosetBuilder

  private val builderSlot = new ForwardSlot[InfosetBuilder]

  private def forwardRuntimeData: RuntimeData =
    if (context eq null) null
    else context.runtimeData
}

/**
 * One memoized grammar result that can be observed while it is still being
 * constructed. The forwarded object is the one already handed to the parent.
 */
private final class ForwardSlot[T >: Null <: AnyRef] {
  private var memo: T = null
  private var building = false
  private var forward: T = null

  def get(makeForward: => T, impl: => T, tie: (T, T) => Unit): T =
    this.synchronized {
      if (memo != null) memo
      else if (building) {
        if (forward == null) forward = makeForward
        forward
      } else {
        building = true
        try {
          memo = impl
        } finally {
          building = false
        }
        if (forward != null && memo != null) tie(forward, memo)
        memo
      }
    }
}
