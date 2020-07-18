package dev.pje.bots.apoiadorrequisitante.utils.markdown;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;

public class AsciiDocMarkdown implements MarkdownInterface{
	
	public static final String NAME = "AsciiDocMarkdown";

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public String normal(String text) {
		return text;
	}

	@Override
	public String head1(String text) {
		return newLine() + "= " + text + newLine();
	}

	@Override
	public String head2(String text) {
		return newLine() + "== " + text + newLine();
	}

	@Override
	public String head3(String text) {
		return newLine() + "=== " + text + newLine();
	}

	@Override
	public String head4(String text) {
		return newLine() + "==== " + text + newLine();
	}

	@Override
	public String bold(String text) {
		return "*" + text + "*";
	}

	@Override
	public String italic(String text) {
		return "_" + text + "_";
	}

	@Override
	public String code(String text) {
		return code(text, null, null);
	}

	@Override
	public String code(String text, String language) {
		return code(text, language, null);
	}

	@Override
	public String code(String text, String language, String title) {
		StringBuilder sb = new StringBuilder();
		if(StringUtils.isNoneBlank(title)) {
			sb.append("." + title)
				.append(newLine());
		}
		if(StringUtils.isNoneBlank(language)) {
			sb.append("[source, " + language + "]")
				.append(newLine());
		}
		sb.append("----")
			.append(newLine())
			.append(text)
			.append(newLine())
			.append("----")
			.append(newLine());
		
		return sb.toString();
	}

	@Override
	public String underline(String text) {
		return "+++<u>" + text + "</u>+++";
	}

	@Override
	public String strike(String text) {
		return "[line-through]#" + text + "#";
	}

	@Override
	public String citation(String text) {
		return "--" + text;
	}

	@Override
	public String highlight(String text) {
		StringBuilder sb = new StringBuilder();
		sb.append(newLine())
			.append("****")
			.append(newLine())
			.append(text)
			.append(newLine())
			.append("****")
			.append(newLine());
		return sb.toString();
	}

	@Override
	public String quote(String text) {
		return quote(text, null, null);
	}

	@Override
	public String quote(String text, String author, String reference) {
		StringBuilder sb = new StringBuilder();
		sb.append(newLine())
			.append("[quote");
		if(StringUtils.isNotBlank(author)) {
			sb.append(",")
				.append(author);
			if(StringUtils.isNotBlank(reference)) {
				sb.append(",").append(reference);
			}
		}
		sb.append("]")
			.append(newLine())
			.append("____")
			.append(newLine())
			.append(text)
			.append(newLine())
			.append("____")
			.append(newLine());
		
		return sb.toString();
	}

	@Override
	public String block(String text) {
		return block(null, text);
	}

	@Override
	public String block(String title, String text) {
		StringBuilder sb = new StringBuilder();
		if(StringUtils.isNotBlank(title)) {
			sb.append(".").append(title);
		}
		sb.append("****")
			.append(newLine())
			.append(text)
			.append(newLine())
			.append("****")
			.append(newLine());
		
		return null;
	}

	@Override
	public String color(String text, String color) {
		return "[" + color + "]#" + text + "#";
	}

	@Override
	public String newLine() {
		return "\n";
	}

	@Override
	public String ruler() {
		return "'''" + newLine();
	}
	
	@Override
	public String listItem(String text) {
		return newLine() + "* " + text;
	}
	
	@Override
	public String paragraph(String text) {
		return newLine() + newLine() + normal(text) + newLine();
	}

	@Override
	public String link(String url, String text) {
		if(text == null || text.equals(url)) {
			text = "";
		}
		StringBuilder sb = new StringBuilder();
		sb.append("link:")
			.append("+++")
			.append(url.replaceAll("\\\"", "'"))
			.append("+++")
			.append("[")
			.append(text)
			.append(",window=_blank")
			.append("]");

		return sb.toString();
	}

	@Override
	public String link(String url) {
		return link(url, url);
	}

	@Override
	public String image(String path, String alternativeText, String height, String width, String title) {
		Map<String, String> options = new HashMap<>();
		options.put("height", height);
		options.put("width", width);
		options.put("title", title);
		
		return image(path, options);
	}

	@Override
	public String image(String path, Map<String, String> options) {
		StringBuilder sb = new StringBuilder();
		String imageId = UUID.randomUUID().toString();
		sb.append("[#img-")
			.append(imageId)
			.append("]")
			.append(newLine());
		
		StringBuilder sbPath = new StringBuilder();
		sbPath.append("image::").append(path);

		if(options != null) {
			if(options.get("thumbnail") != null) {
				options.put("height", "150");
				options.put("width", "150");
				options.remove("thumbnail");
			}
			
			List<String> pathOptions = new ArrayList<>();
			if(StringUtils.isNotBlank(options.get("alternativeText"))) {
				pathOptions.add(options.get("alternativeText"));
				options.remove("alternativeText");
			}
			if(StringUtils.isNotBlank(options.get("height"))) {
				pathOptions.add(options.get("height"));
				options.remove("height");
				if(StringUtils.isNotBlank(options.get("width"))) {
					pathOptions.add(options.get("width"));
					options.remove("width");
				}
			}
			if(pathOptions.size() > 0) {
				sbPath.append("[")
					.append(String.join(",", pathOptions))
					.append("]");
			}

			List<String> imageOptionsList = new ArrayList<>();
			for (String key : options.keySet()) {
				String option = key;
				option += "=" + options.get(key);
				imageOptionsList.add(option);
			}
			sb.append("[")
				.append(String.join(",", imageOptionsList))
				.append("]")
				.append(newLine());
		}
		sb.append(sbPath.toString())
			.append(newLine());
		
		return sb.toString();
	}

	@Override
	public String substitution(String text) {
		return "{" + text + "}";
	}

	@Override
	public String firstPlaceIco() {
		return substitution("firstPlace");
	}

	@Override
	public String secondPlaceIco() {
		return substitution("secondPlace");
	}

	@Override
	public String thirdPlaceIco() {
		return substitution("thirdPlace");
	}

	@Override
	public String MVPIco() {
		return substitution("versionMVP");
	}

}
