package dev.pje.bots.apoiadorrequisitante.utils.markdown;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import dev.pje.bots.apoiadorrequisitante.utils.Utils;

public class GitlabMarkdown implements MarkdownInterface{
	
	public static final String NAME = "GitlabMarkdown";

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
		return newLine() + "# " + text + newLine();
	}

	@Override
	public String head2(String text) {
		return newLine() + "## " + text + newLine();
	}

	@Override
	public String head3(String text) {
		return newLine() + "### " + text + newLine();
	}

	@Override
	public String head4(String text) {
		return newLine() + "#### " + text + newLine();
	}

	@Override
	public String bold(String text) {
		return "**" + text + "**";
	}

	@Override
	public String italic(String text) {
		return "*" + text + "*";
	}

	@Override
	public String code(String text) {
		return newLine() + "```" + newLine() + text + newLine() + "```" + newLine();
	}

	@Override
	public String code(String text, String language) {
		return newLine() + "```" + language + newLine() + text + newLine() + "```" + newLine();
	}

	@Override
	public String code(String text, String language, String title) {
		return head3(title) + code(text, language);
	}
	
	@Override
	public String underline(String text) {
		return "_" + text + "_";
	}

	@Override
	public String strike(String text) {
		return "~~" + text + "~~";
	}
	
	@Override
	public String citation(String text) {
		return block(text);
	}
	
	@Override
	public String referUser(String username) {
		return "@" + username.trim();
	}

	@Override
	public String highlight(String text) {
		return block(text);
	}

	@Override
	public String quote(String text) {
		return newLine() + ">" + text + newLine();
	}

	@Override
	public String quote(String text, String author, String reference) {
		return quote(text) + newLine() + citation(author) + newLine() + italic(reference) + newLine();
	}

	@Override
	public String block(String text) {
		return newLine() + ">>>" + newLine() +
				text + newLine() +
				">>>" + newLine();
	}

	@Override
	public String block(String title, String text) {
		return head3(title) + block(text);
	}
	
	@Override
	public String color(String text, String color) {
		return color + " " + text + " " + color;
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
		return newLine() + "---" + newLine();
	}
	
	@Override
	public String listItem(String text) {
		return newLine() + "- " + text;
	}
	
	@Override
	public String link(String url, String text) {
		if(StringUtils.isBlank(text)) {
			text = url;
		}
		String urlEncoded = url;
		try {
			urlEncoded = Utils.escapeGitlabMarkup(url);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return "[" + text + "](" + urlEncoded + ")";
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
		options.put("alt", alternativeText);
		
		return image(path, options);
	}

	@Override
	public String image(String path, Map<String, String> options) {
		StringBuilder imgSB = new StringBuilder();
		String pathEncoded = path;
		try {
			pathEncoded = Utils.escapeGitlabMarkup(path);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		imgSB.append("<img src=\"").append(pathEncoded).append("\"");
		if(StringUtils.isNotBlank(options.get("alt"))) {
			imgSB.append(" alt=\"").append(options.get("alter-text")).append("\"");
		}
		if(StringUtils.isNotBlank(options.get("title"))) {
			imgSB.append(" title=\"").append(options.get("title")).append("\"");
		}
		if(StringUtils.isNotBlank(options.get("width"))) {
			imgSB.append(" width=\"").append(options.get("width")).append("\"");
		}
		if(StringUtils.isNotBlank(options.get("height"))) {
			imgSB.append(" height=\"").append(options.get("height")).append("\"");
		}
		imgSB.append(">");
		return imgSB.toString();
	}

	@Override
	public String substitution(String text) {
		return text;
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
		return color(text, "&#x274E;");
	}

	@Override
	public String info(String text) {
		return normal(text);
	}

	@Override
	public String warning(String text) {
		return color(text, "&#x2757;");
	}

}
