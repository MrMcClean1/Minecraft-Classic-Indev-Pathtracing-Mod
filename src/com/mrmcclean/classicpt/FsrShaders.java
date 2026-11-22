package com.mrmcclean.classicpt;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Loads FSR 3.1's compute shaders (assets/classicpt/fsr3/, AMD's MIT code as the other tracers have it), edited first. */
final class FsrShaders {
    private FsrShaders() {}

    static int load(String name, java.util.function.UnaryOperator<String> edit) {
        String source;
        try (InputStream in = FsrShaders.class.getResourceAsStream("/assets/classicpt/" + name)) {
            if (in == null) throw new java.io.IOException("missing shader " + name);
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) b.write(buf, 0, n);
            source = edit.apply(new String(b.toByteArray(), "UTF-8"));
        } catch (Exception e) {
            Hooks.log("could not read shader " + name + ": " + e);
            return 0;
        }
        int shader = GL20.glCreateShader(GL43.GL_COMPUTE_SHADER);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            Hooks.log("shader " + name + " failed to compile:\n" + GL20.glGetShaderInfoLog(shader, 8192));
            GL20.glDeleteShader(shader);
            return 0;
        }
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, shader);
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(shader);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            Hooks.log("shader " + name + " failed to link:\n" + GL20.glGetProgramInfoLog(program, 8192));
            GL20.glDeleteProgram(program);
            return 0;
        }
        return program;
    }
}
