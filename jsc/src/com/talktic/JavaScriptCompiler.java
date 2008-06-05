package com.talktic;

import org.mozilla.javascript.*;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.util.Vector;

public class JavaScriptCompiler {
	protected Context context;

	protected Scriptable global;

	protected boolean isSystemLibraryLoaded;

	public JavaScriptCompiler(boolean loadSystemLibrary) throws Exception {
		context = Context.enter();
		global = context.initStandardObjects();

		ScriptableObject.defineClass(global, com.talktic.JSFile.class);
		ScriptableObject.putProperty(global, "stdout", Context.javaToJS(System.out, global));
		ScriptableObject.putProperty(global, "stderr", Context.javaToJS(System.err, global));
		if (loadSystemLibrary) {
			InputStream is = ClassLoader.getSystemResourceAsStream("jsc.js");
			if (is != null) {
				context.evaluateReader(global, new InputStreamReader(is), "<base>", 1, null);
				isSystemLibraryLoaded = true;
			}
		}
	}

	public void loadScript(File script) throws IOException {
		try {
			context.evaluateReader(global, new FileReader(script), script.getPath(), 1, null);
		} catch (FileNotFoundException e) {
			System.err.println(e);
		}
	}

	public void load(String path) throws IOException {
		File files = new File(path);
		if (files.isDirectory()) {
			for (File f : files.listFiles(new FileFilter() {
				public boolean accept(File f) {
					return f.getName().endsWith(".js");
				}
			})) {
				loadScript(f);
			}
		} else {
			loadScript(files);
		}
	}

	public String compile(String src, String asm, String bc) {
		String result = null;
        Object func = global.get("JSC$compile_file", global);
        if (func instanceof Function) {
            Object args[] = {src, "JSC$FLAG_VERBOSE", asm, bc};
            Function f = (Function)func;
            result = Context.toString(f.call(context, global, global, args));
        }
        return result;
	}

	public static void main(String[] args) {
		boolean loadSystemLibrary = true;
		boolean putBytecodeToStdout = false;
		String asmPath = null, bytecodePath = null, sourcePath = null;
		Vector<String> baseLibraryPathList = new Vector<String>();

		for (String arg : args) {
			if (arg.startsWith("-L")) {
				baseLibraryPathList.addElement(arg.substring(2));
			} else if (arg.startsWith("-A")) {
				asmPath = arg.substring(2);
			} else if (arg.startsWith("-B")) {
				bytecodePath = arg.substring(2);
			} else if (arg.equals("-n")) {
				loadSystemLibrary = false;
			} else if (arg.equals("-")) {
				putBytecodeToStdout = true;
			}else {
				sourcePath = arg;
			}
		}

		if (sourcePath == null) {
			System.out.println("java -jar jsc.jar [-n] [-Lpath] [-Afile] [-Bfile] [-] source");
			System.out.println("\t-               put compiled bytecode to stdout");
			System.out.println("\t-n              don't load system library");
			System.out.println("\t-Llibrary_path  additional library dir/file path");
			System.out.println("\t-Lasm_file      asm output file path");
			System.out.println("\t-Lbc_file       bytecode ouput file path");
			return;
		}

		try {
			JavaScriptCompiler jsc = new JavaScriptCompiler(loadSystemLibrary);
			for(int i=0; i<baseLibraryPathList.size(); i++) {
				jsc.load(baseLibraryPathList.get(i));
			}
			String bc = jsc.compile(sourcePath, asmPath, bytecodePath);
			if(putBytecodeToStdout && bc != null) {
				System.out.println(bc);
			}
		} catch (Exception e) {
			System.err.println(e);
		}
	}
}
