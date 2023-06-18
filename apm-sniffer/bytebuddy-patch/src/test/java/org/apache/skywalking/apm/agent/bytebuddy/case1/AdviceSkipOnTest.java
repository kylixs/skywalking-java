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
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.apache.skywalking.apm.agent.bytebuddy.Log;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;

public class AdviceSkipOnTest {

    public static void main(String[] args) throws Exception {

        Instrumentation instrumentation = ByteBuddyAgent.install();
        new AgentBuilder.Default()
                .type(ElementMatchers.nameEndsWith("MyClass"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                        builder.method(ElementMatchers.named("sayHello"))
                                .intercept(Advice.to(MyAdvice.class))
                )
                .with(createListener())
                .installOn(instrumentation);

        //instrumentation.retransformClasses(MyClass.class);

        MyClass instance = new MyClass();
        String str = instance.sayHello("Tom");
        Log.info("result: " + str);

        Log.info("");
        str = instance.sayHello("Joe");
        Log.info("result: " + str);

        Log.info("");
        str = instance.sayHello("Cat");
        Log.info("result: " + str);
    }

    public static class MyClass {
        public String sayHello(String message) {
            Log.info("Execute origin method, arg: " + message);
            if (message.contains("Cat")) {
                throw new IllegalArgumentException("Invalid");
            }
            return "Hi, " + message;
        }
    }

    public static class MyAdvice {

        @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class, skipOnIndex = 1)
        public static Object[] enter(@Advice.This Object target,
                                     @Advice.AllArguments(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object[] allArguments,
                                     @Advice.Origin Method method) {
            return onEnter(allArguments);
        }

        public static Object[] onEnter(Object[] allArguments) {
            String message = (String) allArguments[0];
            Log.info("Before method, arg:" + message);

            MyContext context = new MyContext();
            // Do something special ..
            if (message.contains("Tom")) {
                context.setSkip(true);
                context.setValue("Reject");
            }

            if (context.isSkip()) {
                return new Object[]{context, null};
            } else {
                return new Object[]{context, true};
            }
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void exit(@Advice.This Object target,
                                @Advice.AllArguments Object[] allArguments,
                                @Advice.Thrown Throwable throwable,
                                @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object returnObj,
                                @Advice.Enter(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object[] contexts) {
            returnObj = onExit(target, allArguments, returnObj, throwable, (MyContext) contexts[0]);
        }

        public static Object onExit(Object target, Object[] allArguments, Object returnObj, Throwable throwable, MyContext context) {
            if (throwable != null) {
                Log.info("Thrown on method: " + throwable);
            } else {
                if (context.isSkip()) {
                    returnObj = context.value;
                    Log.info("Skip origin method and set return value: " + returnObj);
                } else {
                    Log.info("Exit method with return value: " + returnObj);
                }
            }
            return returnObj;
        }

    }

    public static class MyContext {
        boolean skip;
        Object value;

        public Object getValue() {
            return value;
        }

        public void setValue(Object value) {
            this.value = value;
        }

        public boolean isSkip() {
            return skip;
        }

        public void setSkip(boolean skip) {
            this.skip = skip;
        }
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

}