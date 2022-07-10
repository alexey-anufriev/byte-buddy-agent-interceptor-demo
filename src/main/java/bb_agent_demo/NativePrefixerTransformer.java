package bb_agent_demo;

import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Type;

import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static net.bytebuddy.jar.asm.Opcodes.ACC_FINAL;
import static net.bytebuddy.jar.asm.Opcodes.ACC_NATIVE;
import static net.bytebuddy.jar.asm.Opcodes.ACC_PRIVATE;
import static net.bytebuddy.jar.asm.Opcodes.ACC_STATIC;
import static net.bytebuddy.jar.asm.Opcodes.ALOAD;
import static net.bytebuddy.jar.asm.Opcodes.ASM7;
import static net.bytebuddy.jar.asm.Opcodes.ILOAD;
import static net.bytebuddy.jar.asm.Opcodes.INVOKESPECIAL;
import static net.bytebuddy.jar.asm.Opcodes.INVOKESTATIC;
import static net.bytebuddy.jar.asm.Opcodes.IRETURN;

final class NativePrefixerTransformer implements ClassFileTransformer {

    static String PREFIX = "$$_DEMO_AGENT_$$_";

    private final Map<Class<?>, Set<Method>> disallowedMethods;

    NativePrefixerTransformer(Map<Class<?>, Set<Method>> disallowedMethods) {
        this.disallowedMethods = disallowedMethods;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {

        // comparison of Classes is not allowed here as it will lead to eager load of definition
        Set<Method> disallowedMethodsOfClass = this.disallowedMethods.entrySet().stream()
                .filter(entry -> Type.getInternalName(entry.getKey()).equals(className))
                .flatMap(entry -> entry.getValue().stream())
                .collect(Collectors.toSet());

        if (disallowedMethodsOfClass.isEmpty()) {
            return null; // no transformation
        }

        var classReader = new ClassReader(classfileBuffer);
        var classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS);

        var visitor = new NativeWrappingClassVisitor(classWriter, disallowedMethodsOfClass, className);
        classReader.accept(visitor, 0);

        return classWriter.toByteArray();
    }

    static class NativeWrappingClassVisitor extends ClassVisitor {

        private static final int OP_CODES_VERSION = ASM7; // Java 11
        private final String className;
        private final Set<Method> disallowedMethods;

        NativeWrappingClassVisitor(ClassVisitor cw, Set<Method> disallowedMethods, String internalClassName) {
            super(OP_CODES_VERSION, cw);

            this.className = internalClassName;
            this.disallowedMethods = disallowedMethods;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            // skip non-native methods
            if ((access & ACC_NATIVE) == 0) {
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }

            Set<String> descriptors = this.disallowedMethods.stream()
                    .map(Type::getMethodDescriptor)
                    .collect(Collectors.toSet());

            // skip non-required methods
            if (!descriptors.contains(descriptor)) {
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }

            // adjust the name of the found method
            int accessFlags = ACC_PRIVATE | (access & ACC_STATIC) | ACC_FINAL | ACC_NATIVE;
            super.visitMethod(accessFlags, PREFIX + name, descriptor, signature, exceptions);

            // construct proxy-method
            MethodVisitor proxyMethodVisitor = super.visitMethod(access & ~ACC_NATIVE, name, descriptor, signature, exceptions);
            proxyMethodVisitor.visitCode(); // define method start

            // build proxy-method body
            return new ProxyMethodVisitor(OP_CODES_VERSION, proxyMethodVisitor, this.className, access, name, descriptor);
        }

        static class ProxyMethodVisitor extends MethodVisitor {

            private final String className;
            final int access;
            final String name;
            final String descriptor;

            protected ProxyMethodVisitor(int api, MethodVisitor methodVisitor, String className, int access,
                                         String name, String descriptor) {

                super(api, methodVisitor);

                this.className = className;
                this.access = access;
                this.name = name;
                this.descriptor = descriptor;
            }

            @Override
            public void visitEnd() {
                boolean isStatic = (this.access & ACC_STATIC) != 0;

                if (!isStatic) {
                    // push `this` to the stack
                    visitVarInsn(ALOAD, 0);
                }

                // shift variable offset
                int index = isStatic ? 0 : 1;

                // load arguments to be used for the proxy call
                Type[] argumentTypes = Type.getArgumentTypes(this.descriptor);
                for (Type argumentType : argumentTypes) {
                    visitVarInsn(argumentType.getOpcode(ILOAD), index);
                    index += argumentType.getSize();
                }

                // call original method
                int invokeOpCode = isStatic ? INVOKESTATIC : INVOKESPECIAL;
                visitMethodInsn(invokeOpCode, this.className, PREFIX + this.name, this.descriptor, false);

                // return result of original method
                Type returnType = Type.getReturnType(this.descriptor);
                visitInsn(returnType.getOpcode(IRETURN));

                // align stack for the method, define method end
                visitMaxs(0, 0);

                super.visitEnd();
            }

        }

    }

}