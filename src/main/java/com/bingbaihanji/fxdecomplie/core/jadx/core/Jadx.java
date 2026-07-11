package com.bingbaihanji.fxdecomplie.core.jadx.core;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.Manifest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bingbaihanji.fxdecomplie.core.jadx.api.CommentsLevel;
import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxArgs;
import com.bingbaihanji.fxdecomplie.core.jadx.core.deobf.DeobfuscatorVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.deobf.SaveDeobfMapping;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.AdjustForIfMergeVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.AnonymousClassVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.ApplyVariableNames;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.AttachCommentsVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.AttachMethodDetails;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.AttachTryCatchVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.CheckCode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.ClassModifier;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.ConstInlineVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.ConstructorVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.DeboxingVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.DotGraphVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.EnumVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.ExtractFieldInit;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.FallbackModeVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.FixSwitchOverEnum;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.GenericTypesVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.IDexTreeVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.InitCodeVariables;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.InlineMethods;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.MarkMethodsForInline;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.MethodInvokeVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.MethodThrowsVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.MethodVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.ModVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.MoveInlineVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.OverrideMethodVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.PrepareForCodeGen;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.ProcessAnonymous;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.ProcessInstructionsVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.ProcessMethodsForInline;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.ReplaceNewArray;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.ShadowFieldVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.SignatureProcessor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.SimplifyVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.blocks.BlockFinisher;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.blocks.BlockProcessor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.blocks.BlockSplitter;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.debuginfo.DebugInfoApplyVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.debuginfo.DebugInfoAttachVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.MarkFinallyVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.fixaccessmodifiers.FixAccessModifiers;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.gradle.NonFinalResIdsVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.kotlin.ProcessKotlinInternals;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.prepare.AddAndroidConstants;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.prepare.CollectConstValues;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions.CheckRegions;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions.CleanRegions;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions.IfRegionVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions.LoopRegionVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions.RegionMakerVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions.ReturnVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions.SwitchBreakVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions.SwitchOverStringVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions.variables.ProcessVariables;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.rename.CodeRenameVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.rename.RenameVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.rename.SourceFileRename;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.shrink.CodeShrinkVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.ssa.SSATransform;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference.FinishTypeInference;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference.FixTypesVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference.TypeInferenceVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.usage.UsageInfoVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;

public class Jadx {
	private static final Logger LOG = LoggerFactory.getLogger(Jadx.class);

	private Jadx() {
	}

	public static List<IDexTreeVisitor> getPassesList(JadxArgs args) {
		switch (args.getDecompilationMode()) {
			case AUTO:
			case RESTRUCTURE:
				return getRegionsModePasses(args);
			case SIMPLE:
				return getSimpleModePasses(args);
			case FALLBACK:
				return getFallbackPassesList();
			default:
				throw new JadxRuntimeException("Unknown decompilation mode: " + args.getDecompilationMode());
		}
	}

	public static List<IDexTreeVisitor> getPreDecompilePassesList() {
		List<IDexTreeVisitor> passes = new ArrayList<>();
		passes.add(new SignatureProcessor());
		passes.add(new OverrideMethodVisitor());
		passes.add(new AddAndroidConstants());

		// rename and deobfuscation
		passes.add(new DeobfuscatorVisitor());
		passes.add(new SourceFileRename());
		passes.add(new RenameVisitor());
		passes.add(new SaveDeobfMapping());

		passes.add(new UsageInfoVisitor());
		passes.add(new CollectConstValues());
		passes.add(new ProcessAnonymous());
		passes.add(new ProcessMethodsForInline());
		return passes;
	}

	public static List<IDexTreeVisitor> getRegionsModePasses(JadxArgs args) {
		List<IDexTreeVisitor> passes = new ArrayList<>();
		// instructions IR
		passes.add(new CheckCode());
		if (args.isDebugInfo()) {
			passes.add(new DebugInfoAttachVisitor());
		}
		passes.add(new AttachTryCatchVisitor());
		if (args.getCommentsLevel() != CommentsLevel.NONE) {
			passes.add(new AttachCommentsVisitor());
		}
		passes.add(new AttachMethodDetails());
		passes.add(new ProcessInstructionsVisitor());

		// blocks IR
		passes.add(new BlockSplitter());
		passes.add(new BlockProcessor());
		passes.add(new BlockFinisher());
		if (args.isRawCFGOutput()) {
			passes.add(DotGraphVisitor.dumpRaw());
		}

		passes.add(new SSATransform());
		passes.add(new MoveInlineVisitor());
		passes.add(new ConstructorVisitor());
		passes.add(new InitCodeVariables());
		if (args.isExtractFinally()) {
			passes.add(new MarkFinallyVisitor());
		}
		passes.add(new ConstInlineVisitor());
		passes.add(new TypeInferenceVisitor());
		if (args.isDebugInfo()) {
			passes.add(new DebugInfoApplyVisitor());
		}
		passes.add(new FixTypesVisitor());
		passes.add(new FinishTypeInference());

		passes.add(new AdjustForIfMergeVisitor());

		if (args.getUseKotlinMethodsForVarNames() != JadxArgs.UseKotlinMethodsForVarNames.DISABLE) {
			passes.add(new ProcessKotlinInternals());
		}
		passes.add(new CodeRenameVisitor());
		if (args.isInlineMethods()) {
			passes.add(new InlineMethods());
		}
		passes.add(new GenericTypesVisitor());
		passes.add(new ShadowFieldVisitor());
		passes.add(new DeboxingVisitor());
		passes.add(new AnonymousClassVisitor());
		passes.add(new ModVisitor());
		passes.add(new CodeShrinkVisitor());
		passes.add(new ReplaceNewArray());
		if (args.isCfgOutput()) {
			passes.add(DotGraphVisitor.dump());
		}

		// regions IR
		passes.add(new RegionMakerVisitor());
		passes.add(new IfRegionVisitor());
		if (args.isRestoreSwitchOverString()) {
			passes.add(new SwitchOverStringVisitor());
		}
		passes.add(new ReturnVisitor());
		passes.add(new CleanRegions());

		passes.add(new MethodThrowsVisitor());

		passes.add(new CodeShrinkVisitor());
		passes.add(new MethodInvokeVisitor());
		passes.add(new SimplifyVisitor());
		passes.add(new CheckRegions());

		passes.add(new EnumVisitor());
		passes.add(new FixSwitchOverEnum());
		passes.add(new NonFinalResIdsVisitor());
		passes.add(new ExtractFieldInit());
		passes.add(new FixAccessModifiers());
		passes.add(new ClassModifier());
		passes.add(new LoopRegionVisitor());
		passes.add(new SwitchBreakVisitor());

		if (args.isInlineMethods()) {
			passes.add(new MarkMethodsForInline());
		}
		passes.add(new ProcessVariables());
		passes.add(new ApplyVariableNames());

		passes.add(new PrepareForCodeGen());
		if (args.isCfgOutput()) {
			passes.add(DotGraphVisitor.dumpRegions());
		}
		return passes;
	}

	public static List<IDexTreeVisitor> getSimpleModePasses(JadxArgs args) {
		List<IDexTreeVisitor> passes = new ArrayList<>();
		if (args.isDebugInfo()) {
			passes.add(new DebugInfoAttachVisitor());
		}
		passes.add(new AttachTryCatchVisitor());
		if (args.getCommentsLevel() != CommentsLevel.NONE) {
			passes.add(new AttachCommentsVisitor());
		}
		passes.add(new AttachMethodDetails());
		passes.add(new ProcessInstructionsVisitor());

		passes.add(new BlockSplitter());
		if (args.isRawCFGOutput()) {
			passes.add(DotGraphVisitor.dumpRaw());
		}
		passes.add(new MethodVisitor("DisableBlockLock", mth -> mth.add(AFlag.DISABLE_BLOCKS_LOCK)));
		passes.add(new BlockProcessor());
		passes.add(new SSATransform());
		passes.add(new MoveInlineVisitor());
		passes.add(new ConstructorVisitor());
		passes.add(new InitCodeVariables());
		passes.add(new ConstInlineVisitor());
		passes.add(new TypeInferenceVisitor());
		if (args.isDebugInfo()) {
			passes.add(new DebugInfoApplyVisitor());
		}
		passes.add(new FixTypesVisitor());
		passes.add(new FinishTypeInference());
		passes.add(new CodeRenameVisitor());
		passes.add(new DeboxingVisitor());
		passes.add(new ModVisitor());
		passes.add(new CodeShrinkVisitor());
		passes.add(new ReplaceNewArray());
		passes.add(new SimplifyVisitor());
		passes.add(new MethodVisitor("ForceGenerateAll", mth -> mth.remove(AFlag.DONT_GENERATE)));
		if (args.isCfgOutput()) {
			passes.add(DotGraphVisitor.dump());
		}
		return passes;
	}

	public static List<IDexTreeVisitor> getFallbackPassesList() {
		List<IDexTreeVisitor> passes = new ArrayList<>();
		passes.add(new AttachTryCatchVisitor());
		passes.add(new AttachCommentsVisitor());
		passes.add(new ProcessInstructionsVisitor());
		passes.add(new FallbackModeVisitor());
		return passes;
	}

	public static final String VERSION_DEV = "dev";

	private static String version;

	public static String getVersion() {
		if (version == null) {
			version = searchJadxVersion();
		}
		return version;
	}

	public static boolean isDevVersion() {
		return getVersion().equals(VERSION_DEV);
	}

	private static String searchJadxVersion() {
		try {
			ClassLoader classLoader = Jadx.class.getClassLoader();
			if (classLoader != null) {
				Enumeration<URL> resources = classLoader.getResources("META-INF/MANIFEST.MF");
				while (resources.hasMoreElements()) {
					try (InputStream is = resources.nextElement().openStream()) {
						Manifest manifest = new Manifest(is);
						String ver = manifest.getMainAttributes().getValue("jadx-version");
						if (ver != null) {
							return ver;
						}
					}
				}
			}
		} catch (Exception e) {
			LOG.error("Can't get manifest file", e);
		}
		return VERSION_DEV;
	}
}
