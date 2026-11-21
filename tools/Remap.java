import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/**
 * The bots' remapper (build time only). The Classic and Indev jars are obfuscated, and their package names make
 * javac unable to name some classes (net.minecraft.a.a is a class and a package). So the bots are written against
 * Alpha's Feather names, the same sources as Alpha PT's bots:
 *
 *   named  <game.jar> <mappings> <out.jar>            the game jar with Alpha's names (to compile against), plus stub
 *                                                     members for what the old game lacks (filled in by a compat class)
 *   back   <game.jar> <mappings> <classesDir> <compat> the compiled bot classes back to the game's own names; uses
 *                                                     of stub members become static calls to the compat class
 *
 * Mappings, one per line (# comments; obf names on the left, names after; descriptors in obf names):
 *   CLASS  obf named
 *   FIELD  obfOwner obfName obfDesc named
 *   METHOD obfOwner obfName obfDesc named
 *   STUB   namedOwner name namedDesc [static] [protected]  field if the descriptor has no "(", else method: not in the
 *                                                   game; a field read becomes compat.get_name(owner)T, a write
 *                                                   compat.set_name(owner, T)V, a call compat.name(owner, args)
 *   ABSENT named [namedSuper]                        a class the game doesn't have: becomes the class Absent next to
 *                                                   the compat class (instanceof is always false)
 *   TEST   named [namedSuper]                        as ABSENT, but instanceof asks compat.is<SimpleName>(Object)
 *                                                   (a kind the game has without a class for it: Classic's monsters)
 *   SHIM   named                                     a class the game doesn't have, written in the bots' sources
 *                                                   under its Alpha name; it moves to <compat package>/shim/
 */
public class Remap {
    static final Map<String, String> cls = new HashMap<>(), clsBack = new HashMap<>();
    static final Map<String, String> mem = new HashMap<>(), memBack = new HashMap<>(); // owner.name.desc -> name
    static final Set<String> stubs = new LinkedHashSet<>(), staticStubs = new HashSet<>(), protectedStubs = new HashSet<>(); // namedOwner.name.namedDesc
    static final Set<String> absent = new LinkedHashSet<>();
    static final Map<String, String> absentSuper = new HashMap<>();
    static final Set<String> tests = new HashSet<>(), shims = new HashSet<>();
    static final Map<String, String[]> supers = new HashMap<>(); // class -> super + interfaces (in the naming being read)

    public static void main(String[] a) throws Exception {
        Map<String, byte[]> game = readJar(a[1]);
        readMappings(Paths.get(a[2]));
        if (a[0].equals("named")) named(game, a[3]);
        else back(game, Paths.get(a[3]), a[4]);
    }

    // ------------------------------------------------------------------ mappings

    static void readMappings(Path p) throws IOException {
        List<String[]> members = new ArrayList<>();
        for (String line : Files.readAllLines(p)) {
            line = line.replaceAll("#.*", "").trim();
            if (line.isEmpty()) continue;
            String[] t = line.split("\\s+");
            switch (t[0]) {
                case "CLASS": cls.put(t[1], t[2]); clsBack.put(t[2], t[1]); break;
                case "FIELD": case "METHOD": members.add(t); break;
                case "STUB": {
                    String k = t[1] + "." + t[2] + "." + t[3];
                    stubs.add(k);
                    for (int i = 4; i < t.length; i++) if (t[i].equals("static")) staticStubs.add(k); else if (t[i].equals("protected")) protectedStubs.add(k);
                    break;
                }
                case "TEST": tests.add(t[1]); // and absent
                case "ABSENT": absent.add(t[1]); absentSuper.put(t[1], t.length > 2 ? t[2] : "java/lang/Object"); break;
                case "SHIM": shims.add(t[1]); break;
                default: throw new IOException("bad mapping line: " + line);
            }
        }
        Remapper fwd = classesOnly(cls);
        for (String[] t : members) {
            mem.put(t[1] + "." + t[2] + "." + t[3], t[4]);
            String namedDesc = t[0].equals("FIELD") ? fwd.mapDesc(t[3]) : fwd.mapMethodDesc(t[3]);
            memBack.put(cls.getOrDefault(t[1], t[1]) + "." + t[4] + "." + namedDesc, t[2]);
        }
    }

    static Remapper classesOnly(Map<String, String> m) {
        return new Remapper() { @Override public String map(String n) { return m.getOrDefault(n, n); } };
    }

    /** Remaps classes and members, looking members up through the class's supertypes (in the naming being read). */
    static Remapper remapper(Map<String, String> classes, Map<String, String> members) {
        return new Remapper() {
            @Override public String map(String n) { return classes.getOrDefault(n, n); }
            @Override public String mapFieldName(String owner, String name, String desc) { return find(owner, name + "." + desc, name); }
            @Override public String mapMethodName(String owner, String name, String desc) {
                if (name.startsWith("<")) return name;
                if (overridesStub(owner, name, desc)) return OWN + name;
                return find(owner, name + "." + desc, name);
            }
            String find(String owner, String key, String name) {
                Deque<String> todo = new ArrayDeque<>(Collections.singleton(owner));
                Set<String> seen = new HashSet<>();
                while (!todo.isEmpty()) {
                    String c = todo.pop();
                    if (!seen.add(c)) continue;
                    String m = members.get(c + "." + key);
                    if (m != null) return m;
                    String[] s = supers.get(c);
                    if (s != null) for (String x : s) if (x != null) todo.add(x);
                }
                // named jar: an unmapped game member (declared somewhere up the hierarchy, not only in the JDK)
                if (prefixUnmapped) for (String c : seen) if (declared.contains(c + "." + key)) return OBF + name;
                if (name.startsWith(OBF)) return name.substring(OBF.length()); // back: an unmapped game member
                return name;
            }
        };
    }

    /**
     * A bots' method that overrides a stub (BotEntity.tick over Entity's): the game never calls a stub, and its own
     * method of that name may be final (Classic's Mob.tick), so the bots' one is renamed, its calls too.
     */
    static final String OWN = "bots$";

    static boolean overridesStub(String owner, String name, String desc) {
        if (ownMethods.isEmpty()) return false; // named jar
        Deque<String> todo = new ArrayDeque<>(Collections.singleton(owner));
        Set<String> seen = new HashSet<>();
        boolean own = false;
        while (!todo.isEmpty()) {
            String c = todo.pop();
            if (!seen.add(c)) continue;
            if (ownMethods.contains(c + "." + name)) own = true;
            if (own && stubs.contains(c + "." + name + "." + desc)) return true;
            String[] s = supers.get(c);
            if (s != null) for (String x : s) if (x != null) todo.add(x);
        }
        return false;
    }

    /** Unmapped game members get this prefix in the named jar, so they never clash with Alpha's names (fields x, y, z). */
    static final String OBF = "obf$";
    static boolean prefixUnmapped;
    static final Set<String> declared = new HashSet<>(); // game class.name.desc (obf)

    // ------------------------------------------------------------------ named jar

    static void named(Map<String, byte[]> game, String out) throws IOException {
        for (byte[] b : game.values()) {
            addSupers(b);
            ClassReader cr = new ClassReader(b);
            cr.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public FieldVisitor visitField(int acc, String n, String d, String sig, Object v) { declared.add(cr.getClassName() + "." + n + "." + d); return null; }
                @Override public MethodVisitor visitMethod(int acc, String n, String d, String sig, String[] ex) { declared.add(cr.getClassName() + "." + n + "." + d); return null; }
            }, ClassReader.SKIP_CODE);
        }
        prefixUnmapped = true;
        Remapper r = remapper(cls, mem);
        Map<String, List<String[]>> stubsBy = new HashMap<>();
        for (String s : stubs) { String[] p = split(s); stubsBy.computeIfAbsent(p[0], k -> new ArrayList<>()).add(p); }
        try (JarOutputStream jo = new JarOutputStream(new FileOutputStream(out))) {
            Set<String> written = new HashSet<>();
            for (Map.Entry<String, byte[]> e : game.entrySet()) {
                ClassReader cr = new ClassReader(e.getValue());
                ClassWriter cw = new ClassWriter(0);
                String named = r.map(cr.getClassName());
                List<String[]> add = stubsBy.getOrDefault(named, Collections.emptyList());
                cr.accept(new ClassRemapper(new ClassVisitor(Opcodes.ASM9, cw) {
                    // access stays as the game has it (javac then refuses what the game would refuse at run time)
                    boolean itf;
                    @Override public void visit(int v, int acc, String n, String sig, String sup, String[] it) { itf = (acc & Opcodes.ACC_INTERFACE) != 0; super.visit(v, acc, n, sig, sup, it); }
                    @Override public void visitEnd() {
                        for (String[] s : add) stub(cv, s, itf);
                        super.visitEnd();
                    }
                }, r), 0);
                put(jo, named, cw.toByteArray());
                written.add(named);
            }
            for (String c : stubsBy.keySet()) if (!written.contains(c)) throw new IOException("stub on a class the jar doesn't have: " + c);
            for (String c : absent) { // empty classes, only to compile against
                ClassWriter cw = new ClassWriter(0);
                cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, c, null, absentSuper.get(c), null);
                cw.visitEnd();
                put(jo, c, cw.toByteArray());
            }
        }
    }

    static void stub(ClassVisitor cv, String[] s, boolean itf) {
        boolean st = staticStubs.contains(s[0] + "." + s[1] + "." + s[2]);
        int acc = (protectedStubs.contains(s[0] + "." + s[1] + "." + s[2]) ? Opcodes.ACC_PROTECTED : Opcodes.ACC_PUBLIC) | (st ? Opcodes.ACC_STATIC : 0);
        if (!s[2].startsWith("(")) { cv.visitField(acc | (itf ? Opcodes.ACC_STATIC | Opcodes.ACC_FINAL : 0), s[1], s[2], null, null).visitEnd(); return; }
        if (itf && !st) { cv.visitMethod(acc | Opcodes.ACC_ABSTRACT, s[1], s[2], null, null).visitEnd(); return; } // class files before Java 8: no default methods
        MethodVisitor mv = cv.visitMethod(acc, s[1], s[2], null, null);
        mv.visitCode();
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/AbstractMethodError");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/AbstractMethodError", "<init>", "()V", false);
        mv.visitInsn(Opcodes.ATHROW);
        mv.visitMaxs(2, Type.getArgumentsAndReturnSizes(s[2]) >> 2 + 1);
        mv.visitEnd();
    }

    // ------------------------------------------------------------------ back to the game's names

    static void back(Map<String, byte[]> game, Path dir, String compat) throws IOException {
        // supertypes in Alpha's names: the game's (renamed) plus the bot classes themselves
        Remapper fwd = classesOnly(cls);
        for (byte[] b : game.values()) {
            ClassReader cr = new ClassReader(b);
            String[] it = cr.getInterfaces();
            String[] s = new String[it.length + 1];
            s[0] = cr.getSuperName() == null ? null : fwd.map(cr.getSuperName());
            for (int i = 0; i < it.length; i++) s[i + 1] = fwd.map(it[i]);
            supers.put(fwd.map(cr.getClassName()), s);
        }
        List<Path> files = new ArrayList<>();
        try (java.util.stream.Stream<Path> st = Files.walk(dir)) { st.filter(p -> p.toString().endsWith(".class")).forEach(files::add); }
        for (Path p : files) {
            byte[] b = Files.readAllBytes(p);
            addSupers(b);
            ClassReader cr = new ClassReader(b);
            cr.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int a, String n, String d, String sig, String[] ex) { ownMethods.add(cr.getClassName() + "." + n); return null; }
            }, ClassReader.SKIP_CODE);
        }
        Map<String, String> classesBack = new HashMap<>(clsBack);
        String pkg = compat.substring(0, compat.lastIndexOf('/') + 1);
        for (String c : absent) classesBack.put(c, pkg + "Absent");
        for (String c : shims) classesBack.put(c, pkg + "shim/" + c.substring(c.lastIndexOf('/') + 1));
        Remapper r = remapper(classesBack, memBack);
        List<Path> out = new ArrayList<>();
        for (Path p : files) {
            ClassReader cr = new ClassReader(Files.readAllBytes(p));
            ClassWriter cw = new ClassWriter(0);
            ClassVisitor v = new ClassRemapper(new ClassVisitor(Opcodes.ASM9, cw) {
                @Override public MethodVisitor visitMethod(int acc, String n, String d, String sig, String[] ex) {
                    return new Redirect(super.visitMethod(acc, n, d, sig, ex), compat, r);
                }
            }, r);
            cr.accept(new ClassVisitor(Opcodes.ASM9, v) { // before the remapper: instanceof a TEST class (in Alpha's names)
                @Override public MethodVisitor visitMethod(int acc, String n, String d, String sig, String[] ex) {
                    return new MethodVisitor(Opcodes.ASM9, super.visitMethod(acc, n, d, sig, ex)) {
                        @Override public void visitTypeInsn(int op, String t) {
                            if (op == Opcodes.INSTANCEOF && tests.contains(t)) super.visitMethodInsn(Opcodes.INVOKESTATIC, compat, "is" + t.substring(t.lastIndexOf('/') + 1), "(Ljava/lang/Object;)Z", false);
                            else super.visitTypeInsn(op, t);
                        }
                    };
                }
            }, 0);
            String name = r.map(cr.getClassName());
            Path to = dir.resolve(name + ".class");
            if (!to.equals(p)) { Files.delete(p); Files.createDirectories(to.getParent()); }
            Files.write(to, cw.toByteArray());
            out.add(to);
        }
        files = out;
        compatClass = compat;
        check(game, files);
    }

    static String compatClass = "";
    /** The bots' own methods (class.name): a call to one that overrides a stub stays a call (BotEntity.aiTick). */
    static final Set<String> ownMethods = new HashSet<>();

    /** Every reference to the game in the remapped classes must name a class and member the game has (else: a missing mapping). */
    static void check(Map<String, byte[]> game, List<Path> files) throws IOException {
        Map<String, String[]> gameSupers = new HashMap<>();
        Set<String> members = new HashSet<>();
        for (byte[] b : game.values()) {
            ClassReader cr = new ClassReader(b);
            String[] it = cr.getInterfaces(); String[] s = new String[it.length + 1]; s[0] = cr.getSuperName(); System.arraycopy(it, 0, s, 1, it.length);
            gameSupers.put(cr.getClassName(), s);
            cr.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public FieldVisitor visitField(int a, String n, String d, String sig, Object v) { members.add(cr.getClassName() + "." + n + "." + d); return null; }
                @Override public MethodVisitor visitMethod(int a, String n, String d, String sig, String[] ex) { members.add(cr.getClassName() + "." + n + "." + d); return null; }
            }, ClassReader.SKIP_CODE);
        }
        Set<String> own = new HashSet<>();
        Map<String, byte[]> ownBytes = new HashMap<>();
        for (Path p : files) { byte[] b = Files.readAllBytes(p); String n = new ClassReader(b).getClassName(); own.add(n); ownBytes.put(n, b); }
        Set<String> ownMembers = new HashSet<>();
        for (Map.Entry<String, byte[]> e : ownBytes.entrySet())
            new ClassReader(e.getValue()).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int a, String n, String d, String sig, String[] ex) { ownMembers.add(e.getKey() + "." + n + "." + d); return null; }
            }, ClassReader.SKIP_CODE);
        List<String> bad = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : ownBytes.entrySet()) {
            String self = e.getKey();
            new ClassReader(e.getValue()).accept(new ClassVisitor(Opcodes.ASM9) {
                void ref(String owner, String name, String desc) {
                    if (owner.startsWith("[")) return;
                    if (owner.equals(compatClass) && name != null && desc.startsWith("(") && !ownMembers.contains(owner + "." + name + "." + desc)) { bad.add(self + ": compat has no " + name + desc); return; }
                    if (own.contains(owner) || owner.startsWith("java/") || owner.startsWith("javax/") || owner.startsWith("org/lwjgl/")) return;
                    if (!gameSupers.containsKey(owner)) { bad.add(self + ": no class " + owner); return; }
                    if (name == null) return;
                    Deque<String> todo = new ArrayDeque<>(Collections.singleton(owner));
                    while (!todo.isEmpty()) {
                        String c = todo.pop();
                        if (members.contains(c + "." + name + "." + desc)) return;
                        String[] s = gameSupers.get(c);
                        if (s == null) { if (c.startsWith("java/")) return; continue; } // a JDK method (Object.getClass and so on)
                        for (String x : s) if (x != null) todo.add(x);
                    }
                    bad.add(self + ": no member " + owner + "." + name + desc);
                }
                @Override public MethodVisitor visitMethod(int a, String n, String d, String sig, String[] ex) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitFieldInsn(int op, String o, String n, String d) { ref(o, n, d); }
                        @Override public void visitMethodInsn(int op, String o, String n, String d, boolean i) { ref(o, n, d); }
                        @Override public void visitTypeInsn(int op, String t) { ref(t, null, null); }
                    };
                }
            }, 0);
        }
        if (!bad.isEmpty()) { new TreeSet<>(bad).forEach(System.err::println); throw new IOException(bad.size() + " references the game doesn't have"); }
        System.out.println("remapped " + files.size() + " classes; every game reference resolves");
    }

    /**
     * Runs inside the remapper's method visitor, so it sees the game's names already; stub members are recognised
     * by their Alpha-named key, kept through the remapper's own unmapped names (stubs have no mapping, so their
     * names and descriptors stay Alpha's, only their owner and types are mapped). The owner is found again by
     * looking up which class in the (Alpha-named) hierarchy declares the stub.
     */
    static final class Redirect extends MethodVisitor {
        final String compat;
        final Remapper r;
        Redirect(MethodVisitor mv, String compat, Remapper r) { super(Opcodes.ASM9, mv); this.compat = compat; this.r = r; }

        @Override public void visitFieldInsn(int op, String owner, String name, String desc) {
            String stub = stubOwner(owner, name, desc, false);
            if (stub == null) { super.visitFieldInsn(op, owner, name, desc); return; }
            boolean st = op == Opcodes.GETSTATIC || op == Opcodes.PUTSTATIC;
            String o = st ? "" : "L" + clsBack.getOrDefault(stub, stub) + ";"; // the declaring class, as compat takes it
            boolean get = op == Opcodes.GETFIELD || op == Opcodes.GETSTATIC;
            super.visitMethodInsn(Opcodes.INVOKESTATIC, compat, (get ? "get_" : "set_") + name, get ? "(" + o + ")" + desc : "(" + o + desc + ")V", false);
        }

        @Override public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
            String stub = op == Opcodes.INVOKESPECIAL || op == Opcodes.INVOKESTATIC ? stubOwner(owner, name, desc, false) : stubOwner(owner, name, desc, true);
            if (stub == null) { super.visitMethodInsn(op, owner, name, desc, itf); return; }
            String d = op == Opcodes.INVOKESTATIC ? desc : "(L" + clsBack.getOrDefault(stub, stub) + ";" + desc.substring(1);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, compat, name, d, false);
        }

        /** The Alpha-named class declaring this stub, or null. owner/desc arrive in the game's names here. */
        String stubOwner(String owner, String name, String desc, boolean virtual) {
            Deque<String> todo = new ArrayDeque<>(Collections.singleton(owner));
            Set<String> seen = new HashSet<>();
            while (!todo.isEmpty()) {
                String c = todo.pop();
                if (!seen.add(c)) continue;
                String named = cls.getOrDefault(c, c);
                if (virtual && ownMethods.contains(named + "." + name)) return null; // overridden by the bots: a real call
                for (String s : stubs) {
                    String[] p = split(s);
                    if (p[0].equals(named) && p[1].equals(name)
                            && (p[2].startsWith("(") ? r.mapMethodDesc(p[2]) : r.mapDesc(p[2])).equals(desc)) return p[0];
                }
                String[] s = supers.get(named);
                if (s != null) for (String x : s) if (x != null) todo.add(clsBack.getOrDefault(x, x));
            }
            return null;
        }
    }

    // ------------------------------------------------------------------ helpers

    static String[] split(String key) { // owner.name.desc (owner has no dots: internal names)
        int a = key.indexOf('.'), b = key.indexOf('.', a + 1);
        return new String[]{key.substring(0, a), key.substring(a + 1, b), key.substring(b + 1)};
    }

    static void addSupers(byte[] b) {
        ClassReader cr = new ClassReader(b);
        String[] it = cr.getInterfaces();
        String[] s = new String[it.length + 1];
        s[0] = cr.getSuperName();
        System.arraycopy(it, 0, s, 1, it.length);
        supers.put(cr.getClassName(), s);
    }

    static Map<String, byte[]> readJar(String f) throws IOException {
        Map<String, byte[]> m = new LinkedHashMap<>();
        try (JarFile j = new JarFile(f)) {
            for (Enumeration<JarEntry> e = j.entries(); e.hasMoreElements(); ) {
                JarEntry je = e.nextElement();
                if (!je.getName().endsWith(".class") || je.getName().startsWith("paulscode/") || je.getName().startsWith("com/jcraft/")) continue;
                try (InputStream in = j.getInputStream(je)) { m.put(je.getName(), in.readAllBytes()); }
            }
        }
        return m;
    }

    static void put(JarOutputStream jo, String name, byte[] b) throws IOException {
        jo.putNextEntry(new JarEntry(name + ".class"));
        jo.write(b);
        jo.closeEntry();
    }
}
