package dev.pje.bots.apoiadorrequisitante.utils.markdown;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

public class RocketchatMarkdown implements MarkdownInterface{
	
	public static final String NAME = "RocketchatMarkdown";

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
		return newLine() + "# " + text + " #" + newLine();
	}

	@Override
	public String head2(String text) {
		return newLine() + "## " + text + " ##" + newLine();
	}

	@Override
	public String head3(String text) {
		return newLine() + "### " + text + " ###" + newLine();
	}

	@Override
	public String head4(String text) {
		return newLine() + "#### " + text + " ####" + newLine();
	}

	@Override
	public String bold(String text) {
		return "**" + text + "**";
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
		if(StringUtils.isNotBlank(title)) {
			sb.append(head1(title))
				.append(newLine());
		}
		sb.append("```");
		if(StringUtils.isNotBlank(language)) {
			sb.append(language);
		}
		sb.append(text)
			.append(newLine())
			.append("```")
			.append(newLine());
		
		return sb.toString();
	}

	@Override
	public String underline(String text) {
		return bold(text);
	}

	@Override
	public String strike(String text) {
		return "~" + text + "~";
	}

	@Override
	public String citation(String text) {
		return "> " + text;
	}

	@Override
	public String highlight(String text) {
		StringBuilder sb = new StringBuilder();
		sb.append(newLine())
		.append("```")
			.append(newLine())
			.append(text)
			.append(newLine())
			.append("```")
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
		sb.append(newLine());
		if(StringUtils.isNotBlank(author)) {
			sb.append(bold(author));
			if(StringUtils.isNotBlank(reference)) {
				sb.append(", ").append(italic(reference));
			}
		}
		sb.append(citation(text));
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
			sb.append(head1(title));
		}
		sb.append(highlight(text));
		return null;
	}

	@Override
	public String color(String text, String color) {
		return "`" + (text) + "`";
	}

	@Override
	public String newLine() {
		return "\n";
	}
	
	@Override
	public String newParagraph() {
		return newLine() + newLine();
	}

	@Override
	public String ruler() {
		return "______" + newLine();
	}
	
	@Override
	public String listItem(String text) {
		return newLine() + "* " + text;
	}
	
	@Override
	public String link(String url, String text) {
		if(StringUtils.isBlank(text)) {
			text = url;
		}
			
		StringBuilder sb = new StringBuilder();
		sb.append("[")
			.append(text)
			.append("]")
			.append("(")
			.append(url)
			.append(")");

		return sb.toString();
	}

	@Override
	public String link(String url) {
		return link(url, url);
	}

	@Override
	public String image(String path, String alternativeText, String height, String width, String title) {
		Map<String, String> options = new HashMap<>();
		options.put("alt", alternativeText);
		
		return image(path, options);
	}

	@Override
	public String image(String path, Map<String, String> options) {
		StringBuilder sb = new StringBuilder();
		
		sb.append("[");
		if(options != null) {
			String text = null;
			if(StringUtils.isNotBlank(options.get("alt"))) {
				text = options.get("alt");
			}
			if(StringUtils.isNotBlank(text)) {
					sb.append(text);
			}
		}			
			sb.append("]");
			sb.append("(");
			sb.append(path);
			sb.append(")");
		return sb.toString();
	}

	@Override
	public String substitution(String text) {
		return normal(text);
	}

	@Override
	public String firstPlaceIco() {
		return ":star2:";
	}

	@Override
	public String secondPlaceIco() {
		return ":star:";
	}

	@Override
	public String thirdPlaceIco() {
		return ":rocket:";
	}

	@Override
	public String MVPIco() {
		return " :trophy:";
	}

	@Override
	public String error(String text) {
		return color(text, "red");
	}

	@Override
	public String info(String text) {
		return normal(text);
	}

	@Override
	public String warning(String text) {
		return color(text, "yellow");
	}
}
