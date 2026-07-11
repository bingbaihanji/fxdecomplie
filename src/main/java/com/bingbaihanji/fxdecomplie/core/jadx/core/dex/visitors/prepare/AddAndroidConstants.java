package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.prepare;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.ClassInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.ConstStorage;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.FieldInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.AbstractVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.JadxVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.android.AndroidResourcesMap;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;

// TODO: move this pass to separate "Android plugin"
@JadxVisitor(
		name = "AddAndroidConstants",
		desc = "Insert Android constants from resource mapping file",
		runBefore = {
				CollectConstValues.class
		}
)
public class AddAndroidConstants extends AbstractVisitor {

	private static final String R_CLS = "android.R";
	private static final String R_INNER_CLS = R_CLS + '$';

	@Override
	public void init(RootNode root) throws JadxException {
		if (!root.getArgs().isReplaceConsts()) {
			return;
		}
		if (root.resolveClass(R_CLS) != null) {
			// Android R class already loaded
			return;
		}
		ConstStorage constStorage = root.getConstValues();
		AndroidResourcesMap.getMap().forEach((resId, path) -> {
			int sep = path.indexOf('/');
			String clsName = R_INNER_CLS + path.substring(0, sep);
			String resName = path.substring(sep + 1);
			ClassInfo cls = ClassInfo.fromName(root, clsName);
			FieldInfo field = FieldInfo.from(root, cls, resName, ArgType.INT);
			constStorage.addGlobalConstField(field, resId);
		});
	}
}
