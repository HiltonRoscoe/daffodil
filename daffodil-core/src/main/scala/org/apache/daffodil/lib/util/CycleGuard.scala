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

package org.apache.daffodil.lib.util

/**
 * Runs `body` unless this thread is already inside `body` for the same host
 * and name, in which case `onCycle` is returned instead.
 *
 * Scala lazy vals deadlock when the initializing thread re-enters them. Schema
 * compilation walks recursive element references, so those walks have to notice
 * the cycle before they touch the lazy val again.
 */
object CycleGuard {
  private val stack =
    ThreadLocal.withInitial(() =>
      new java.util.IdentityHashMap[AnyRef, java.util.HashSet[String]]()
    )

  def apply[T](host: AnyRef, name: String, onCycle: => T)(body: => T): T = {
    val map = stack.get()
    val existing = map.get(host)
    if (existing != null && existing.contains(name)) onCycle
    else {
      val names =
        if (existing != null) existing
        else {
          val created = new java.util.HashSet[String]()
          map.put(host, created)
          created
        }
      names.add(name)
      try body
      finally {
        names.remove(name)
        if (names.isEmpty) map.remove(host)
      }
    }
  }
}
