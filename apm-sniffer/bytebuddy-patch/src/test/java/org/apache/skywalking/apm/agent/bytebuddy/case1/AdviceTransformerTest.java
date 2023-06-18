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

package org.apache.skywalking.apm.agent.bytebuddy.case1;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.apache.skywalking.apm.agent.bytebuddy.Log;

import java.lang.reflect.Method;

public class AdviceTransformerTest extends AbstractInterceptTest {
    public static void main(String[] args) {
        ByteBuddyAgent.install();

        enableClassDump();

        AgentBuilder.Transformer transformer = new AgentBuilder.Transformer.ForAdvice()
                .include(AdviceTransformerTest.class.getClassLoader())
                .advice(ElementMatchers.named("sayHello"), InstMethodAdvice.class.getName())
                .advice(ElementMatchers.named("staticMethod"), StaticMethodAdvice.class.getName());

        new AgentBuilder.Default()
                .type(ElementMatchers.any())
                .transform(transformer)
                .transform(transformer)
                .with(createListener())
                .installOnByteBuddyAgent();

        new MyClass().sayHello("John");
        MyClass.staticMethod("John");
    }

    private static AgentBuilder.Listener createListener() {
        return new AgentBuilder.Listener() {
            @Override
            public void onDiscovery(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {
            }

            @Override
            public void onTransformation(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, boolean loaded, DynamicType dynamicType) {
            }

            @Override
            public void onIgnored(TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, boolean loaded) {
            }

            @Override
            public void onError(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded, Throwable throwable) {
                System.err.println(String.format("transform class error: typeName: %s, ", typeName));
                throwable.printStackTrace();
            }

            @Override
            public void onComplete(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {

            }
        };
    }

    public static class MyClass {
        public String sayHello(String name) {
            return "HI " + name;
        }

        public static boolean staticMethod(String arg) {
            return false;
        }

    }

    public static class InstMethodAdvice {
        @Advice.OnMethodEnter
        public static void enter(
                @Advice.This Object target,
                @Advice.Origin Method method) {
            Log.info("enter method: " + method);
        }

        @Advice.OnMethodExit
        public static void exit(@Advice.Origin Method method) {
            Log.info("exit method: " + method);
        }
    }

    public static class StaticMethodAdvice {
        @Advice.OnMethodEnter(inline = false)
        public static void enter(
                @Advice.Origin Method method) {
            Log.info("enter method: " + method);
        }

        @Advice.OnMethodExit
        public static void exit(@Advice.Origin Method method) {
            Log.info("exit method: " + method);
        }
    }

}
