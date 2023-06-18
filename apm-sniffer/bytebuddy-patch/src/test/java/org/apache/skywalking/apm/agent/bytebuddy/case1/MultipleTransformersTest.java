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
import net.bytebuddy.agent.builder.SWAsmVisitorWrapper;
import net.bytebuddy.agent.builder.SWTransformThreadLocals;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.SuperMethodCall;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.apache.skywalking.apm.agent.bytebuddy.ConstructorInter;
import org.apache.skywalking.apm.agent.bytebuddy.InstMethodsInter;
import org.apache.skywalking.apm.agent.bytebuddy.biz.BizFoo;
import org.junit.Test;

import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;

public class MultipleTransformersTest extends AbstractRetransformTest {

    @Test
    public void test1() throws UnmodifiableClassException {
        String className = BIZ_FOO_CLASS_NAME;
        String nameTrait = getNameTrait(1);
        deleteDuplicatedFields = true;
        String methodName = SAY_HELLO_METHOD;

        //enableClassDump();

        AgentBuilder.Transformer transformer1 = new AgentBuilder.Transformer() {
            AgentBuilder.Transformer instance = this;

            @Override
            public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, ProtectionDomain protectionDomain) {
                int round = 1;
                String interceptorClassName = METHOD_INTERCEPTOR_CLASS + "$" + methodName + "$" + round;
                String fieldName = nameTrait + "_delegate$" + methodName + round;
                SWTransformThreadLocals.setTransformer(instance);

                if (deleteDuplicatedFields) {
                    builder = builder.visit(new SWAsmVisitorWrapper());
                }
                return builder
                        .constructor(ElementMatchers.any())
                        .intercept(SuperMethodCall.INSTANCE.andThen(
                                MethodDelegation.withDefaultConfiguration().to(
                                        new ConstructorInter(CONSTRUCTOR_INTERCEPTOR_CLASS + "$" + round, classLoader), nameTrait + "_delegate$constructor" + round)
                        ))
                        .method(ElementMatchers.nameContainsIgnoreCase(methodName))
                        .intercept(MethodDelegation.withDefaultConfiguration()
                                .to(new InstMethodsInter(interceptorClassName, classLoader), fieldName));
            }
        };

        AgentBuilder.Transformer transformer2 = new AgentBuilder.Transformer() {
            AgentBuilder.Transformer instance = this;

            @Override
            public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule module, ProtectionDomain protectionDomain) {
                int round = 2;
                String interceptorClassName = METHOD_INTERCEPTOR_CLASS + "$" + methodName + "$" + round;
                String fieldName = nameTrait + "_delegate$" + methodName + round;
                SWTransformThreadLocals.setTransformer(instance);

                if (deleteDuplicatedFields) {
                    builder = builder.visit(new SWAsmVisitorWrapper());
                }
                return builder
                        .constructor(ElementMatchers.any())
                        .intercept(SuperMethodCall.INSTANCE.andThen(
                                MethodDelegation.withDefaultConfiguration().to(
                                        new ConstructorInter(CONSTRUCTOR_INTERCEPTOR_CLASS + "$" + round, classLoader), nameTrait + "_delegate$constructor" + round)
                        ))
                        .method(ElementMatchers.nameContainsIgnoreCase(methodName))
                        .intercept(MethodDelegation.withDefaultConfiguration()
                                .to(new InstMethodsInter(interceptorClassName, classLoader), fieldName));
            }
        };

        Instrumentation instrumentation = ByteBuddyAgent.install();

//        new AgentBuilder.Default()
        newAgentBuilder(nameTrait)
                .type(ElementMatchers.named(className))
                .transform(transformer1)
                .type(ElementMatchers.named(className))
                .transform(transformer2)
                .with(new AgentBuilder.Listener.Adapter() {
                    @Override
                    public void onError(String typeName, ClassLoader classLoader, JavaModule module, boolean loaded, Throwable throwable) {
                        System.err.println(String.format("Transform Error: typeName: %s, classLoader: %s, module: %s, loaded: %s", typeName, classLoader, module, loaded));
                        throwable.printStackTrace();
                    }
                })
                .installOn(instrumentation);

        try {
            callBizFoo(2);
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            // check interceptors
            checkMethodInterceptor(SAY_HELLO_METHOD, 1);
            checkMethodInterceptor(SAY_HELLO_METHOD, 2);
            checkConstructorInterceptor(1);
            checkConstructorInterceptor(2);
        }

        reTransform(instrumentation, BizFoo.class);

        callBizFoo(2);

//        try {
//            TimeUnit.DAYS.sleep(1);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
    }

}
