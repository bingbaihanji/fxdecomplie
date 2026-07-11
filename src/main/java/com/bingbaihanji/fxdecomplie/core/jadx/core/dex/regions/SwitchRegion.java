package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.regions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.bingbaihanji.fxdecomplie.core.jadx.api.ICodeWriter;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.RegionGen;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IBranchRegion;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IContainer;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IRegion;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.CodegenException;

public final class SwitchRegion extends AbstractRegion implements IBranchRegion {

	public static final Object DEFAULT_CASE_KEY = new Object() {
		@Override
		public String toString() {
			return "default";
		}
	};

	private final BlockNode header;

	private final List<CaseInfo> cases;

	public SwitchRegion(IRegion parent, BlockNode header) {
		super(parent);
		this.header = header;
		this.cases = new ArrayList<>();
	}

	public static final class CaseInfo {
		private final List<Object> keys;
		private final IContainer container;

		public CaseInfo(List<Object> keys, IContainer container) {
			this.keys = keys;
			this.container = container;
		}

		public boolean isDefaultCase() {
			return keys.size() == 1 && keys.get(0) == DEFAULT_CASE_KEY;
		}

		public List<Object> getKeys() {
			return keys;
		}

		public IContainer getContainer() {
			return container;
		}
	}

	public BlockNode getHeader() {
		return header;
	}

	public void addCase(List<Object> keysList, IContainer c) {
		cases.add(new CaseInfo(keysList, c));
	}

	public List<CaseInfo> getCases() {
		return cases;
	}

	public List<IContainer> getCaseContainers() {
		return Utils.collectionMap(cases, caseInfo -> caseInfo.container);
	}

	@Override
	public List<IContainer> getSubBlocks() {
		List<IContainer> all = new ArrayList<>(cases.size() + 1);
		all.add(header);
		for (CaseInfo caseInfo : cases) {
			all.add(caseInfo.container);
		}
		return Collections.unmodifiableList(all);
	}

	@Override
	public List<IContainer> getBranches() {
		return Collections.unmodifiableList(getCaseContainers());
	}

	@Override
	public void generate(RegionGen regionGen, ICodeWriter code) throws CodegenException {
		regionGen.makeSwitch(this, code);
	}

	@Override
	public String baseString() {
		return "SW:" + header.baseString();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Switch: ").append(header.baseString());
		for (CaseInfo caseInfo : cases) {
			List<String> keyStrings = Utils.collectionMap(caseInfo.getKeys(),
					k -> k == DEFAULT_CASE_KEY ? "default" : k.toString());
			sb.append("\n case ")
					.append(Utils.listToString(keyStrings))
					.append(" -> ").append(caseInfo.getContainer());
		}
		return sb.toString();
	}
}
