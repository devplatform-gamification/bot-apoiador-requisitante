package dev.pje.bots.apoiadorrequisitante.utils.markdown;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;

public class TelegramMarkdownHtml implements MarkdownInterface{
	
	public static final String NAME = "TelegramHtml";

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
		return newLine() + bold(text) + newLine();
	}

	@Override
	public String head2(String text) {
		return head1(text);
	}

	@Override
	public String head3(String text) {
		return head2(text);
	}

	@Override
	public String head4(String text) {
		return head3(text);
	}

	@Override
	public String bold(String text) {
		return "<b>" + text + "</b>";
	}

	@Override
	public String italic(String text) {
		return "<i>" + text + "</i>";
	}

	@Override
	public String code(String text) {
		return "<code>" + text + "</code>" + newLine();
	}

	@Override
	public String code(String text, String language) {
		return code(text);
	}

	@Override
	public String code(String text, String language, String title) {
		return newLine() + bold(title) + code(text);
	}
	
	@Override
	public String underline(String text) {
		return "<u>" + text + "</u>";
	}

	@Override
	public String strike(String text) {
		return "<s>" + text + "</s>";
	}
	
	@Override
	public String citation(String text) {
		return newLine() + "---" + newLine() + italic(text);
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
		return newLine() + "---" + newLine() + italic(text) + newLine() + "---" + newLine();
	}

	@Override
	public String quote(String text, String author, String reference) {
		return quote(text) + newLine() + citation(author) + newLine() + italic(reference) + newLine();
	}

	@Override
	public String block(String text) {
		return italic(text) + newLine();
	}

	@Override
	public String block(String title, String text) {
		return newLine() + "---" + bold(title) + newLine() + italic(text) + "---" + newLine();
	}
	
	@Override
	public String color(String text, String color) {
		return normal(text);
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
		return newLine() + "----" + newLine();
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
		return "<a href=\"" + url + "\">" + text + "</a>";
	}
	
	@Override
	public String link(String url) {
		return link(url, url);
	}

	@Override
	public String image(String path, String alternativeText, String height, String width, String title) {
		return normal(alternativeText);
	}

	@Override
	public String image(String path, Map<String, String> options) {
		String name = "image";
		if(options != null && options.get("alt") != null) {
			name = options.get("alt");
		}
		return link(path, name);
	}

	@Override
	public String substitution(String text) {
		return text;
	}

	@Override
	public String firstPlaceIco() {
		return "[1]";
	}

	@Override
	public String secondPlaceIco() {
		return "[2]";
	}

	@Override
	public String thirdPlaceIco() {
		return "[3]";
	}

	@Override
	public String MVPIco() {
		return " [T]";
	}

	@Override
	public String error(String text) {
		return normal("ERROR " + text);
	}

	@Override
	public String info(String text) {
		return normal(text);
	}

	@Override
	public String warning(String text) {
		return normal("WARN " + text);
	}
}
