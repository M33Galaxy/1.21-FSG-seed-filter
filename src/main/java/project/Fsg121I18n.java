package project;

import java.util.EnumMap;
import java.util.Map;

/** GUI 中英文字符串。默认中文。 */
public final class Fsg121I18n {
	public enum Lang { ZH, EN }

	private static volatile Lang lang = Lang.ZH;

	private Fsg121I18n() {
	}

	public static Lang lang() {
		return lang;
	}

	public static void setLang(Lang l) {
		lang = l != null ? l : Lang.ZH;
	}

	public static String t(String key) {
		Map<String, String> m = TABLES.get(lang);
		String v = m.get(key);
		if (v != null) {
			return v;
		}
		return TABLES.get(Lang.ZH).getOrDefault(key, key);
	}

	public static String format(String key, Object... args) {
		return String.format(t(key), args);
	}

	private static final Map<Lang, Map<String, String>> TABLES = new EnumMap<>(Lang.class);

	static {
		Map<String, String> zh = new java.util.LinkedHashMap<>();
		zh.put("title", "1.21+ FSG自动筛种程序");
		zh.put("lang", "语言");
		zh.put("lang.zh", "中文");
		zh.put("lang.en", "English");
		zh.put("stop.title", "停止条件");
		zh.put("stop.one", "找到 1 个即停止");
		zh.put("stop.count", "找到 N 个后停止");
		zh.put("stop.unit", "个");
		zh.put("stop.cont", "连续筛选（手动停止）");
		zh.put("opts.title", "运行参数");
		zh.put("opts.threads", "线程数");
		zh.put("opts.writeFile", "同时写入文件");
		zh.put("opts.browse", "浏览…");
		zh.put("btn.start", "开始筛选");
		zh.put("btn.stop", "停止");
		zh.put("btn.copy", "复制全部种子");
		zh.put("status.ready", "就绪");
		zh.put("status.struct", "已筛结构种子: %s");
		zh.put("status.stopping", "正在停止…");
		zh.put("status.stopped", "  — 已停止");
		zh.put("status.copied", "已复制 %d 个种子到剪贴板");
		zh.put("hits.title", "命中种子（可选中复制）");
		zh.put("hits.tip", "可选中种子后 Ctrl+C 复制，或点「复制全部种子」");
		zh.put("log.start", "--- 开始筛选符合条件的种子 ---");
		zh.put("log.writeFile", "同时写入文件: %s");
		zh.put("dlg.copyTitle", "复制");
		zh.put("dlg.noSeeds", "暂无可复制的种子");
		zh.put("dlg.tip", "提示");
		zh.put("dlg.needFile", "请填写输出文件名");
		zh.put("dlg.startFail", "启动失败");
		TABLES.put(Lang.ZH, zh);

		Map<String, String> en = new java.util.LinkedHashMap<>();
		en.put("title", "1.21+ FSG Seed Finder");
		en.put("lang", "Language");
		en.put("lang.zh", "中文");
		en.put("lang.en", "English");
		en.put("stop.title", "Stop condition");
		en.put("stop.one", "Stop after 1 hit");
		en.put("stop.count", "Stop after N hits");
		en.put("stop.unit", "");
		en.put("stop.cont", "Run until stopped manually");
		en.put("opts.title", "Options");
		en.put("opts.threads", "Threads");
		en.put("opts.writeFile", "Also write to file");
		en.put("opts.browse", "Browse…");
		en.put("btn.start", "Start");
		en.put("btn.stop", "Stop");
		en.put("btn.copy", "Copy all seeds");
		en.put("status.ready", "Ready");
		en.put("status.struct", "Structure seeds tried: %s");
		en.put("status.stopping", "Stopping…");
		en.put("status.stopped", "  — stopped");
		en.put("status.copied", "Copied %d seeds to clipboard");
		en.put("hits.title", "Hits (select to copy)");
		en.put("hits.tip", "Select seeds and Ctrl+C, or use Copy all seeds");
		en.put("log.start", "--- Searching for matching seeds ---");
		en.put("log.writeFile", "Also writing file: %s");
		en.put("dlg.copyTitle", "Copy");
		en.put("dlg.noSeeds", "No seeds to copy");
		en.put("dlg.tip", "Notice");
		en.put("dlg.needFile", "Please enter an output file name");
		en.put("dlg.startFail", "Failed to start");
		TABLES.put(Lang.EN, en);
	}
}
