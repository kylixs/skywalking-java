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
 *
 */

package net.bytebuddy.agent.builder;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.implementation.bytecode.assign.Assigner;

import java.util.Collections;
import java.util.List;

public class ExtForAdvice extends AgentBuilder.Transformer.ForAdvice {
    public ExtForAdvice() {
    }

    public ExtForAdvice(ClassFileLocator classFileLocator) {
        super(Advice.withCustomMapping(),
                Advice.ExceptionHandler.Default.SUPPRESSING,
                Assigner.DEFAULT,
                classFileLocator,
                AgentBuilder.PoolStrategy.Default.FAST,
                AgentBuilder.LocationStrategy.ForClassLoader.STRONG,
                Collections.<Entry>emptyList());
    }

    public ExtForAdvice(Advice.WithCustomMapping advice, Advice.ExceptionHandler exceptionHandler, Assigner assigner, ClassFileLocator classFileLocator, AgentBuilder.PoolStrategy poolStrategy, AgentBuilder.LocationStrategy locationStrategy, List<Entry> entries) {
        super(advice, exceptionHandler, assigner, classFileLocator, poolStrategy, locationStrategy, entries);
    }
}
