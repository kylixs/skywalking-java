package org.apache.skywalking.apm.agent.bytebuddy.case1;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import org.apache.skywalking.apm.agent.bytebuddy.SWClassFileLocator;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;

import static org.apache.skywalking.apm.agent.bytebuddy.ClassUtil.createAdaptedAdviceClass;

public class AdaptedAdviceClassTest extends AbstractInterceptTest {

    public static void main(String[] args) throws Exception {

        enableClassDump();

        Instrumentation instrumentation = ByteBuddyAgent.install();
        //newAgentBuilder("sw")
        new AgentBuilder.Default()
                .type(ElementMatchers.nameEndsWith("MyClass"))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
                        {
                            try {
                                Class<?> adviceClass = createAdaptedAdviceClass(MyAdvice.class, MyAdviceSupport.class, "MyClassInterceptor");
                                return builder.method(ElementMatchers.named("sayHello"))
                                        .intercept(Advice.to(adviceClass, new SWClassFileLocator(instrumentation, adviceClass.getClassLoader(), new String[]{"$"})));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                )
                .with(createListener())
                .installOn(instrumentation);

        //instrumentation.retransformClasses(MyClass.class);

        MyClass instance = new MyClass();
        String str = instance.sayHello("Tom");
        System.out.println("result: " + str);

        System.out.println();
        str = instance.sayHello("Joe");
        System.out.println("result: " + str);

        System.out.println();
        str = instance.sayHello("Cat");
        System.out.println("result: " + str);
    }

    public static class MyClass {
        public String sayHello(String message) {
            System.out.println("Execute origin method, arg: " + message);
            if (message.contains("Cat")) {
                throw new IllegalArgumentException("Invalid");
            }
            return "Hi, " + message;
        }
    }

    public static class MyAdvice {

        @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class, skipOnIndex = 1)
        public static Object[] enter(@Advice.Origin Method method,
                                     @Advice.This Object target,
                                     @Advice.AllArguments(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object[] allArguments,
                                     @Advice.Unused String interceptorClass) {
            return MyAdviceSupport.enter(method, target, allArguments, interceptorClass);
        }

        @Advice.OnMethodExit(onThrowable = Throwable.class)
        public static void exit(@Advice.Origin Method method,
                                @Advice.This Object target,
                                @Advice.AllArguments Object[] allArguments,
                                @Advice.Thrown Throwable throwable,
                                @Advice.Return(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object returnObj,
                                @Advice.Enter(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object[] contexts) {
            returnObj = MyAdviceSupport.exit(method, target, allArguments, throwable, returnObj, contexts);
        }

    }

    public static class MyAdviceSupport {
        public static Object[] enter(Method method, Object target, Object[] allArguments, String interceptorClass) {
            String message = (String) allArguments[0];
            System.out.println(String.format("Before method, arg: %s, interceptorClass: %s", message, interceptorClass));

            MyContext context = new MyContext();
            context.setInterceptorClass(interceptorClass);

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

        public static Object exit(Method method, Object target, Object[] allArguments, Throwable throwable, Object returnObj, Object[] contexts) {
            MyContext context = (MyContext) contexts[0];
            if (throwable != null) {
                System.out.println("Thrown on method: " + throwable);
            } else {
                if (context.isSkip()) {
                    returnObj = context.value;
                    System.out.println("Skip origin method and set return value: " + returnObj);
                } else {
                    System.out.println("Exit method with return value: " + returnObj);
                }
            }
            return returnObj;
        }
    }

    public static class MyContext {
        boolean skip;
        Object value;
        String interceptorClass;

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

        public String getInterceptorClass() {
            return interceptorClass;
        }

        public void setInterceptorClass(String interceptorClass) {
            this.interceptorClass = interceptorClass;
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