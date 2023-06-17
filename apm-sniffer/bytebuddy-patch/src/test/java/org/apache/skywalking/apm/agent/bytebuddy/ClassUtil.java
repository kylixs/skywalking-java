package org.apache.skywalking.apm.agent.bytebuddy;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.RandomString;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

public class ClassUtil {

    public static final String GET_INTERCEPTOR_CLASS_METHOD = "getInterceptorClass";

    public static <T> DynamicType.Builder<Object> copyAdviceClass(Class<T> originalClass, Class<?> adviceSupportClass, String interceptorClass, String suffix) throws Exception {
        // 获取原始类的信息
        TypeDescription originalType = TypePool.Default.ofSystemLoader().describe(originalClass.getName()).resolve();
        MethodList<MethodDescription.InDefinedShape> staticMethods = originalType.getDeclaredMethods().filter(MethodDescription::isStatic);
        Annotation[] classAnnotations = originalType.getDeclaredAnnotations().toArray(new Annotation[0]);

        ClassLoader classLoader = originalClass.getClassLoader();
        String newClassName = originalClass.getName() + "$" + suffix;
        // 创建新的类
        DynamicType.Builder<Object> builder = new ByteBuddy()
                .subclass(Object.class)
                .name(newClassName)
                .annotateType(classAnnotations)
                .modifiers(Modifier.STATIC);

        // 将静态方法复制到新的类中
        for (MethodDescription method : staticMethods) {
            if (method.getName().equals(GET_INTERCEPTOR_CLASS_METHOD)) {
                continue;
            }
            DynamicType.Builder.MethodDefinition.ParameterDefinition<Object> pdfn = builder
                    .defineMethod(method.getName(), method.getReturnType(), method.getModifiers());
            // 复制方法参数及参数注解
            List<Class<?>> parameterTypes = new ArrayList<>();
            for (ParameterDescription pd : method.getParameters()) {
                pdfn = pdfn.withParameter(pd.getType(), pd.getName(), pd.getModifiers())
                        .annotateParameter(pd.getDeclaredAnnotations());
                parameterTypes.add(getParameterClass(classLoader, pd));
            }

            MethodCall methodCall;
            if ("enter".equals(method.getName())) {
                methodCall = MethodCall.invoke(adviceSupportClass.getDeclaredMethod(method.getName(), parameterTypes.toArray(new Class[0])))
                        .withArgument(createArgumentIndexes(parameterTypes.size() - 1)) // fill n-1 args
                        .with(interceptorClass); // replace last argument with fixed value
            } else {
                methodCall = MethodCall.invoke(adviceSupportClass.getDeclaredMethod(method.getName(), parameterTypes.toArray(new Class[0])))
                        .withAllArguments();
            }
            builder = pdfn.intercept(methodCall)
                    .annotateMethod(method.getDeclaredAnnotations());
        }
        return builder;
    }

    private static int[] createArgumentIndexes(int size) {
        int[] indexArray = new int[size];
        for (int i = 0; i < size; i++) {
            indexArray[i] = i;
        }
        return indexArray;
    }

    private static Class<?> getParameterClass(ClassLoader classLoader, ParameterDescription pd) throws ClassNotFoundException {
        String typeName = pd.getType().getTypeName();
        // TODO convert type
        if ("[Ljava.lang.Object;".equals(typeName)) {
            return Object[].class;
        }
        return classLoader.loadClass(typeName);
    }

    public static <T> Class<?> createAdaptedAdviceClass(Class<T> originalClass, Class<?> adviceSupportClass, String interceptorClass) throws Exception {
        return copyAdviceClass(originalClass, adviceSupportClass, interceptorClass, RandomString.hashOf(interceptorClass.hashCode()))
                .defineMethod(GET_INTERCEPTOR_CLASS_METHOD, String.class, Modifier.PUBLIC | Modifier.STATIC)
                .intercept(FixedValue.value(interceptorClass))
                .make()
                .load(originalClass.getClassLoader(), ClassLoadingStrategy.Default.INJECTION.opened())
                .getLoaded();
    }

}
