package com.bingbaihanji.fxdecomplie.core.jadx.core;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bingbaihanji.fxdecomplie.core.jadx.api.DecompilationMode;
import com.bingbaihanji.fxdecomplie.core.jadx.api.ICodeInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxArgs;
import com.bingbaihanji.fxdecomplie.core.jadx.api.impl.SimpleCodeInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.CodeGen;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.DecompileModeOverrideAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.LoadStage;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.DepthTraversal;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.IDexTreeVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;

import static com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ProcessState.GENERATED_AND_UNLOADED;
import static com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ProcessState.LOADED;
import static com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ProcessState.NOT_LOADED;
import static com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ProcessState.PROCESS_COMPLETE;
import static com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ProcessState.PROCESS_STARTED;

public class ProcessClass {
	private static final Logger LOG = LoggerFactory.getLogger(ProcessClass.class);

	private static final ICodeInfo NOT_GENERATED = new SimpleCodeInfo("");

	private final List<IDexTreeVisitor> passes;

	public ProcessClass(List<IDexTreeVisitor> passesList) {
		this.passes = passesList;
	}

	@Nullable
	private ICodeInfo process(ClassNode cls, boolean codegen) {
		if (!codegen && cls.getState() == PROCESS_COMPLETE) {
			// nothing to do
			return null;
		}
		Utils.checkThreadInterrupt();
		synchronized (cls.getClassInfo()) {
			try {
				if (cls.contains(AFlag.CLASS_DEEP_RELOAD)) {
					cls.remove(AFlag.CLASS_DEEP_RELOAD);
					cls.deepUnload();
					cls.add(AFlag.CLASS_UNLOADED);
				}
				if (cls.contains(AFlag.CLASS_UNLOADED)) {
					cls.root().runPreDecompileStageForClass(cls);
					cls.remove(AFlag.CLASS_UNLOADED);
				}
				if (cls.getState() == GENERATED_AND_UNLOADED) {
					// force loading code again
					cls.setState(NOT_LOADED);
				}
				if (codegen) {
					cls.setLoadStage(LoadStage.CODEGEN_STAGE);
					if (cls.contains(AFlag.RELOAD_AT_CODEGEN_STAGE)) {
						cls.remove(AFlag.RELOAD_AT_CODEGEN_STAGE);
						cls.unload();
					}
				} else {
					cls.setLoadStage(LoadStage.PROCESS_STAGE);
				}
				if (cls.getState() == NOT_LOADED) {
					cls.load();
				}
				if (cls.getState() == LOADED) {
					cls.setState(PROCESS_STARTED);
					for (IDexTreeVisitor visitor : passes) {
						DepthTraversal.visit(visitor, cls);
					}
					cls.setState(PROCESS_COMPLETE);
				}
				if (codegen) {
					Utils.checkThreadInterrupt();
					ICodeInfo code = CodeGen.generate(cls);
					if (!cls.contains(AFlag.DONT_UNLOAD_CLASS)) {
						cls.unload();
						cls.setState(GENERATED_AND_UNLOADED);
					}
					return code;
				}
				return null;
			} catch (StackOverflowError | Exception e) {
				if (codegen) {
					throw e;
				}
				cls.addError("Class process error: " + e.getClass().getSimpleName(), e);
				return null;
			}
		}
	}

	@NotNull
	public ICodeInfo generateCode(ClassNode cls) {
		ClassNode topParentClass = cls.getTopParentClass();
		if (topParentClass != cls) {
			return generateCode(topParentClass);
		}
		try {
			if (cls.contains(AFlag.DONT_GENERATE)) {
				process(cls, false);
				return NOT_GENERATED;
			}
			for (ClassNode depCls : cls.getDependencies()) {
				process(depCls, false);
			}
			if (!cls.getCodegenDeps().isEmpty()) {
				process(cls, false);
				for (ClassNode codegenDep : cls.getCodegenDeps()) {
					process(codegenDep, false);
				}
			}
			ICodeInfo code = process(cls, true);
			if (code == null) {
				throw new JadxRuntimeException("Codegen failed");
			}
			return code;
		} catch (StackOverflowError | Exception e) {
			throw new JadxRuntimeException("Failed to generate code for class: " + cls.getFullName(), e);
		}
	}

	/**
	 * Load and process class without its deps
	 */
	public void forceProcess(ClassNode cls) {
		ClassNode topParentClass = cls.getTopParentClass();
		if (topParentClass != cls) {
			forceProcess(topParentClass);
			return;
		}
		try {
			process(cls, false);
		} catch (StackOverflowError | Exception e) {
			throw new JadxRuntimeException("Failed to process class: " + cls.getFullName(), e);
		}
	}

	/**
	 * Generate code for class without processing its deps
	 */
	public @Nullable ICodeInfo forceGenerateCode(ClassNode cls) {
		try {
			return process(cls, true);
		} catch (StackOverflowError | Exception e) {
			throw new JadxRuntimeException("Failed to generate code for class: " + cls.getFullName(), e);
		}
	}

	private final Map<DecompilationMode, ProcessClass> modesMap = new EnumMap<>(DecompilationMode.class);

	public @Nullable ICodeInfo forceGenerateCodeForMode(ClassNode cls, DecompilationMode mode) {
		synchronized (modesMap) {
			ProcessClass prCls = modesMap.computeIfAbsent(mode, m -> {
				RootNode root = cls.root();
				ProcessClass newPrCls = new ProcessClass(getPassesForMode(root.getArgs(), m));
				newPrCls.initPasses(root);
				return newPrCls;
			});
			try {
				cls.addAttr(new DecompileModeOverrideAttr(mode));
				return prCls.forceGenerateCode(cls);
			} finally {
				cls.remove(AType.DECOMPILE_MODE_OVERRIDE);
			}
		}
	}

	private static List<IDexTreeVisitor> getPassesForMode(JadxArgs baseArgs, DecompilationMode mode) {
		switch (mode) {
			case FALLBACK:
				return Jadx.getFallbackPassesList();

			case SIMPLE:
				// copy properties into new args
				// keep in sync with properties usage in Jadx.getSimpleModePasses method
				JadxArgs args = new JadxArgs();
				args.setDebugInfo(baseArgs.isDebugInfo());
				args.setCommentsLevel(baseArgs.getCommentsLevel());
				return Jadx.getSimpleModePasses(args);

			default:
				throw new JadxRuntimeException("Unexpected decompilation mode: " + mode);
		}
	}

	public void initPasses(RootNode root) {
		for (IDexTreeVisitor pass : passes) {
			try {
				pass.init(root);
			} catch (Exception e) {
				LOG.error("Visitor init failed: {}", pass.getClass().getSimpleName(), e);
			}
		}
	}

	public boolean processMethodUntilVisitor(MethodNode mth, String visitorName, boolean includeVisitor) {
		IDexTreeVisitor foundPass = null;
		IDexTreeVisitor prevPass = null;
		for (IDexTreeVisitor pass : passes) {
			if (pass.getName().equals(visitorName)) {
				if (includeVisitor) {
					foundPass = pass;
				} else {
					foundPass = prevPass;
				}
				break;
			}
			prevPass = pass;
		}
		if (foundPass == null) {
			return false;
		}
		return processMethodToVisitor(mth, foundPass);
	}

	public boolean processMethodToVisitor(MethodNode mth, IDexTreeVisitor lastPassToProcess) {
		synchronized (mth.getTopParentClass().getClassInfo()) {
			try {
				mth.unload();
				mth.load();
				for (IDexTreeVisitor pass : passes) {
					DepthTraversal.visit(pass, mth);
					if (pass == lastPassToProcess) {
						return true;
					}
				}
			} catch (Exception e) {
				throw new JadxRuntimeException("Failed to process method to visitor: " + lastPassToProcess, e);
			}
			return false;
		}
	}

	// TODO: make passes list private and not visible
	public List<IDexTreeVisitor> getPasses() {
		return passes;
	}
}
