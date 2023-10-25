package xyz.wagyourtail.jvmdg.version;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureWriter;
import org.objectweb.asm.tree.*;
import xyz.wagyourtail.jvmdg.*;
import xyz.wagyourtail.jvmdg.util.Function;
import xyz.wagyourtail.jvmdg.util.IOFunction;
import xyz.wagyourtail.jvmdg.util.Lazy;
import xyz.wagyourtail.jvmdg.version.map.ClassMapping;
import xyz.wagyourtail.jvmdg.version.map.FullyQualifiedMemberNameAndDesc;
import xyz.wagyourtail.jvmdg.version.map.MemberNameAndDesc;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Callable;

public abstract class VersionProvider {

    private final Map<Type, Type> classStubs = new HashMap<>();
    private final Map<Type, ClassMapping> stubMappings = new HashMap<>();
    public final int inputVersion;
    public final int outputVersion;

    private volatile boolean initialized = false;

    protected VersionProvider(int inputVersion, int outputVersion) {
        this.inputVersion = inputVersion;
        this.outputVersion = outputVersion;
    }

    public void afterInit() {
        if (Constants.DEBUG) {
//            for (Map.Entry<Type, Pair<Type, Stub>> entry : classStubs.entrySet()) {
//                System.out.println(entry.getKey().getInternalName() + " -> " + entry.getValue().getFirst().getInternalName());
//            }
//            for (Map.Entry<String, Pair<Method, Stub>> entry : methodStubs.entrySet()) {
//                System.out.println(entry.getKey() + " -> " + entry.getValue().getFirst().getDeclaringClass().getCanonicalName().replace('.', '/') + ";" + entry.getValue().getFirst().getName() + Type.getMethodDescriptor(entry.getValue()
//                    .getFirst()));
//            }
        }
    }

    public abstract void init();

    public static void main(String[] args) {
        System.out.println(Type.getType(boolean.class).getDescriptor());
    }

    public synchronized ClassMapping getStubMapper(Type type) throws IOException {
        return getStubMapper(type, new IOFunction<Type, List<Type>>() {

            @Override
            public List<Type> apply(Type o) throws IOException {
                return ClassDowngrader.currentVersionDowngrader.getSupertypes(outputVersion, o);
            }

        });
    }

    public synchronized ClassMapping getStubMapper(final Type type, final IOFunction<Type, List<Type>> superTypeResolver) throws IOException {
        if (stubMappings.containsKey(type)) {
            return stubMappings.get(type);
        }
        if (type.getSort() == Type.ARRAY) {
            return new ClassMapping(new Lazy<List<ClassMapping>>() {
                @Override
                public List<ClassMapping> init() {
                     try {
                         return Collections.singletonList(getStubMapper(Type.getObjectType("java/lang/Object"), superTypeResolver));
                     } catch (IOException e) {
                         throw new RuntimeException(e);
                     }
                 }
            }, type);
        }
        if (type.getInternalName().equals("java/lang/Object")) {
            ClassMapping mapping = new ClassMapping(new Lazy <List<ClassMapping>>() {
                @Override
                public List<ClassMapping> init() {
                    return Collections.emptyList();
                }
            }, type);
            stubMappings.put(type, mapping);
            return mapping;
        }
        ClassMapping mapping = new ClassMapping(new Lazy<List<ClassMapping>>() {
            @Override
            public List<ClassMapping> init() {
                try {
                    List<Type> types = superTypeResolver.apply(type);
                    if (types == null) {
    //            throw new IllegalArgumentException("Could not find class " + type.getInternalName());
                        System.err.println("Could not find class " + type.getInternalName());
                        Thread.dumpStack();
                        types = Collections.emptyList();
                    }
                    List<ClassMapping> superTypes = new ArrayList<>();
                    for (Type superType : types) {
                        superTypes.add(getStubMapper(superType, superTypeResolver));
                    }
                    return superTypes;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }, type);
        stubMappings.put(type, mapping);
        return mapping;
    }

    public void stub(Class<?> clazz) {
        if (clazz.isAnnotationPresent(Stub.class)) {
            Stub stub = clazz.getAnnotation(Stub.class);
            if (stub.ref().value().isEmpty()) {
                throw new IllegalArgumentException("Class " + clazz.getName() + ", @Stub must have a ref");
            } else {
                Type type;
                if (stub.ref().value().startsWith("L") && stub.ref().value().endsWith(";")) {
                    type = Type.getType(stub.ref().value());
                } else {
                    type = Type.getObjectType(stub.ref().value());
                }
//                if (classStubs.containsKey(type)) {
//                    throw new IllegalArgumentException("Class " + clazz.getName() + ", @Stub ref " + type.getInternalName() + " already exists");
//                }
                classStubs.put(type, Type.getType(clazz));
            }
        }
        try {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Stub.class)) {
                    Stub stub = method.getAnnotation(Stub.class);
                    FullyQualifiedMemberNameAndDesc target = resolveStubTarget(method, stub.ref());
                    Type owner = target.getOwner();
                    MemberNameAndDesc member = target.toMemberNameAndDesc();
                    getStubMapper(owner).addStub(member, method);
                } else if (method.isAnnotationPresent(Modify.class)) {
                    Modify modify = method.getAnnotation(Modify.class);
                    FullyQualifiedMemberNameAndDesc target = resolveModifyTarget(method, modify.ref());
                    Type owner = target.getOwner();
                    MemberNameAndDesc member = target.toMemberNameAndDesc();
                    // ensure method parameters are valid
                    Class<?>[] params = method.getParameterTypes();
                    for (int i = 0; i < params.length; i++) {
                        if (i >= Modify.MODIFY_SIG.length) {
                            throw new IllegalArgumentException("Class " + clazz.getName() + ", @Modify method " + method.getName() + " has too many parameters");
                        }
                        if (params[i] != Modify.MODIFY_SIG[i]) {
                            throw new IllegalArgumentException("Class " + clazz.getName() + ", @Modify method " + method.getName() + " parameter " + i + " must be of type " + Modify.MODIFY_SIG[i].getName());
                        }
                    }
                    getStubMapper(owner).addModify(member, method);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("failed to create stub " + clazz.getName(), e);
        }
        // inner classes
        for (Class<?> inner : clazz.getDeclaredClasses()) {
            stub(inner);
        }
    }

    public Type stubClass(Type desc) {
        switch (desc.getSort()) {
            case Type.METHOD:
                Type[] args = desc.getArgumentTypes();
                Type ret = desc.getReturnType();
                for (int i = 0; i < args.length; i++) {
                    args[i] = stubClass(args[i]);
                }
                ret = stubClass(ret);
                return Type.getMethodType(ret, args);
            case Type.ARRAY:
                Type type = desc.getElementType();
                if (classStubs.containsKey(type)) {
                    type = classStubs.get(type);
                }
                return Type.getType(desc.getDescriptor().substring(0, desc.getDimensions()) + type.getDescriptor());
            case Type.OBJECT:
                if (classStubs.containsKey(desc)) {
                    return classStubs.get(desc);
                }
                return desc;
            default:
                return desc;
        }
    }

    public ClassNode stubMethods(ClassNode owner, Set<ClassNode> extra, IOFunction<Type, List<Type>> superTypeResolver) throws IOException {
        for (MethodNode method : new ArrayList<>(owner.methods)) {
            MethodNode newMethod = stubMethods(method, owner, extra, superTypeResolver);
            if (newMethod != method) {
                owner.methods.set(owner.methods.indexOf(method), newMethod);
            }
        }
        return owner;
    }

    public MethodNode stubMethods(MethodNode method, ClassNode owner, Set<ClassNode> extra, IOFunction<Type, List<Type>> superTypeResolver) throws IOException {
        for (int i = 0; i < method.instructions.size(); i++) {
            AbstractInsnNode insn = method.instructions.get(i);
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode min = (MethodInsnNode) insn;
                min.owner = stubClass(Type.getObjectType(min.owner)).getInternalName();
                min.desc = stubClass(Type.getMethodType(min.desc)).getDescriptor();
                getStubMapper(Type.getObjectType(min.owner), superTypeResolver).transform(method, i, owner, extra);
            } else if (insn instanceof TypeInsnNode) {
                TypeInsnNode tin = (TypeInsnNode) insn;
                tin.desc = stubClass(Type.getObjectType(tin.desc)).getInternalName();
            } else if (insn instanceof FieldInsnNode) {
                FieldInsnNode fin = (FieldInsnNode) insn;
                fin.owner = stubClass(Type.getObjectType(fin.owner)).getInternalName();
                fin.desc = stubClass(Type.getType(fin.desc)).getDescriptor();
                //TODO: field stubs (upgrade to method)
            } else if (insn instanceof InvokeDynamicInsnNode) {
                InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode) insn;
                indy.desc = stubClass(Type.getMethodType(indy.desc)).getDescriptor();
                indy.bsm = new Handle(
                        indy.bsm.getTag(),
                        stubClass(Type.getObjectType(indy.bsm.getOwner())).getInternalName(),
                        indy.bsm.getName(),
                        stubClass(Type.getMethodType(indy.bsm.getDesc())).getDescriptor(),
                        indy.bsm.isInterface()
                );
                for (int j = 0; j < indy.bsmArgs.length; j++) {
                    Object arg = indy.bsmArgs[j];
                    if (arg instanceof Handle) {
                        Handle handle = (Handle) arg;
                        handle = new Handle(
                                handle.getTag(),
                                stubClass(Type.getObjectType(handle.getOwner())).getInternalName(),
                                handle.getName(),
                                stubClass(Type.getType(handle.getDesc())).getDescriptor(),
                                handle.isInterface()
                        );
                        indy.bsmArgs[j] = handle;
                        switch (handle.getTag()) {
                            case Opcodes.H_GETFIELD:
                            case Opcodes.H_GETSTATIC:
                            case Opcodes.H_PUTFIELD:
                            case Opcodes.H_PUTSTATIC:
                                //TODO
                                break;
                            case Opcodes.H_INVOKEVIRTUAL:
                            case Opcodes.H_INVOKESTATIC:
                            case Opcodes.H_INVOKESPECIAL:
                            case Opcodes.H_NEWINVOKESPECIAL:
                            case Opcodes.H_INVOKEINTERFACE:
                                Type hOwner = Type.getObjectType(handle.getOwner());
                                MemberNameAndDesc member = new MemberNameAndDesc(handle.getName(), Type.getMethodType(handle.getDesc()));
                                MethodInsnNode min = getStubMapper(hOwner, superTypeResolver).getStubFor(member, handle.getTag() == Opcodes.H_INVOKESTATIC);
                                if (min != null) {
                                    indy.bsmArgs[j] = new Handle(
                                            Opcodes.H_INVOKESTATIC,
                                            min.owner,
                                            min.name,
                                            min.desc,
                                            min.itf
                                    );
                                }
                                break;
                        }

                    } else if (arg instanceof Type) {
                        Type type = (Type) arg;
                        indy.bsmArgs[j] = stubClass(type);
                    }
                }
                getStubMapper(Type.getObjectType(indy.bsm.getOwner()), superTypeResolver).transform(method, i, owner, extra);
            } else if (insn instanceof MultiANewArrayInsnNode) {
                MultiANewArrayInsnNode manain = (MultiANewArrayInsnNode) insn;
                manain.desc = stubClass(Type.getType(manain.desc)).getDescriptor();
            } else if (insn instanceof LdcInsnNode) {
                LdcInsnNode ldc = (LdcInsnNode) insn;
                if (ldc.cst instanceof Type) {
                    ldc.cst = stubClass((Type) ldc.cst);
                }
            }
        }
        return method;
    }

    public static FullyQualifiedMemberNameAndDesc resolveStubTarget(Member member, Ref ref) {
        if (member instanceof Method) {
            Method method = (Method) member;
            Type owner;
            String name;
            List<Type> params = new ArrayList<>(Arrays.asList(Type.getArgumentTypes(method)));
            Type ret = Type.getReturnType(method);
            if (ref.value().isEmpty()) {
                owner = params.remove(0);
            } else {
                if (ref.value().startsWith("L") && ref.value().endsWith(";")) {
                    owner = Type.getType(ref.value());
                } else {
                    owner = Type.getObjectType(ref.value());
                }
            }
            if (ref.member().isEmpty()) {
                name = method.getName();
            } else {
                name = ref.member();
            }
            Type desc;
            if (ref.desc().isEmpty()) {
                if (name.equals("<init>")) {
                    ret = Type.VOID_TYPE;
                }
                desc = Type.getMethodType(ret, params.toArray(new Type[0]));
            } else {
                desc = Type.getMethodType(ref.desc());
            }
            // re-assemble desc
            return new FullyQualifiedMemberNameAndDesc(owner, name, desc);
        } else if (member instanceof Field) {
//            Field field = (Field) member;
            throw new UnsupportedOperationException("Not implemented yet");
        } else {
            throw new IllegalArgumentException("member must be a method or field");
        }
    }

    public static FullyQualifiedMemberNameAndDesc resolveModifyTarget(Member member, Ref ref) {
        if (member instanceof Method) {
            Method method = (Method) member;
            Type owner;
            String name;
            Type desc;
            if (ref.value().isEmpty()) {
                throw new IllegalArgumentException("ref must have a value");
            } else {
                if (ref.value().startsWith("L") && ref.value().endsWith(";")) {
                    owner = Type.getType(ref.value());
                } else {
                    owner = Type.getObjectType(ref.value());
                }
            }
            if (ref.member().isEmpty()) {
                throw new IllegalArgumentException("ref must have a member");
            } else {
                name = ref.member();
            }
            if (ref.desc().isEmpty()) {
                throw new IllegalArgumentException("ref must have a desc");
            } else {
                desc = Type.getMethodType(ref.desc());
            }
            return new FullyQualifiedMemberNameAndDesc(owner, name, desc);
        } else {
            throw new IllegalArgumentException("member must be a method");
        }
    }

    public void ensureInit() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    init();
                    initialized = true;
                    afterInit();
                }
            }
        }
    }

    public ClassNode downgrade(ClassNode clazz, Set<ClassNode> extra, final Function<String, ClassNode> getReadOnly) throws InvocationTargetException, IllegalAccessException, IOException {
        if (clazz.version != inputVersion)
            throw new IllegalArgumentException("Class " + clazz.name + " is not version " + inputVersion);

        ensureInit();

        clazz = stubClasses(clazz);
        clazz = stubMethods(clazz, extra, new IOFunction<Type, List<Type>>() {

            @Override
            public List<Type> apply(Type o) throws IOException {
                ClassNode ro = getReadOnly.apply(o.getInternalName());
                if (ro != null) {
                    List<Type> types = new ArrayList<>();
                    types.add(Type.getObjectType(ro.superName));
                    for (String anInterface : ro.interfaces) {
                        types.add(Type.getObjectType(anInterface));
                    }
                    return types;
                }
                return ClassDowngrader.currentVersionDowngrader.getSupertypes(outputVersion, o);
            }
        });
        clazz = otherTransforms(clazz, extra, getReadOnly);
        clazz.version = inputVersion - 1;
        return clazz;
    }

    public ClassNode otherTransforms(ClassNode clazz, Set<ClassNode> extra, Function<String, ClassNode> getReadOnly) {
        clazz = otherTransforms(clazz, extra);
        return clazz;
    }

    public ClassNode otherTransforms(ClassNode clazz, Set<ClassNode> extra) {
        clazz = otherTransforms(clazz);
        return clazz;
    }

    public ClassNode otherTransforms(ClassNode clazz) {
        return clazz;
    }

    public ClassNode stubClasses(ClassNode clazz) {
        // super
        Type type = Type.getObjectType(clazz.superName);
        if (classStubs.containsKey(type)) {
            clazz.superName = classStubs.get(type).getInternalName();
        }

        // interface
        if (clazz.interfaces != null) {
            for (int i = 0; i < clazz.interfaces.size(); i++) {
                type = Type.getObjectType(clazz.interfaces.get(i));
                if (classStubs.containsKey(type)) {
                    clazz.interfaces.set(i, classStubs.get(type).getInternalName());
                }
            }
        }

        // signature
        if (clazz.signature != null) {
            clazz.signature = transformSignature(clazz.signature);
        }

        // field descriptor
        if (clazz.fields != null) {
            for (FieldNode field : clazz.fields) {
                type = Type.getType(field.desc);
                field.desc = stubClass(type).getDescriptor();
                if (field.signature != null) {
                    field.signature = transformSignature(field.signature);
                }
            }
        }

        // method descriptor
        if (clazz.methods != null) {
            for (MethodNode method : clazz.methods) {
                type = Type.getMethodType(method.desc);
                method.desc = stubClass(type).getDescriptor();
                if (method.signature != null) {
                    method.signature = transformSignature(method.signature);
                }
            }
        }
        return clazz;
    }

    public String transformSignature(String signature) {
        SignatureReader reader = new SignatureReader(signature);
        SignatureWriter writer = new SignatureWriter() {
            @Override
            public void visitClassType(String name) {
                super.visitClassType(stubClass(Type.getObjectType(name)).getInternalName());
            }

            @Override
            public void visitInnerClassType(String name) {
                // TODO: fix to support this
                super.visitInnerClassType(name);
            }
        };
        reader.accept(writer);
        return writer.toString();
    }

}
