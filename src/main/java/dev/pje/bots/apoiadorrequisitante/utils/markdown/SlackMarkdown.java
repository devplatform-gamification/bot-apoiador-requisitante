package dev.pje.bots.apoiadorrequisitante.utils.markdown;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;


public class SlackMarkdown implements MarkdownInterface{
	
	public static final String NAME = MARKDOWN_SLACK;

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
		return " *" + text + "* ";
	}//ok

	@Override
	public String italic(String text) {
		return " _" + text + "_ ";
	}//ok

	@Override
	public String code(String text) {
		return block(text);
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
		return "+" + text + "+";
	}

	@Override
	public String strike(String text) {
		return "~" + text + "~";
	}// ok
	
	@Override
	public String citation(String text) {
		return newParagraph() + italic(text) + newParagraph();
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
		return "```" + text + "```" + newLine();
	}

	@Override
	public String block(String title, String text) {
		return bold(title) + newLine() + block(text);
	}
	
	@Override
	public String color(String text, String color) {
		return "`" + text + "`";
	}

	@Override
	public String newLine() {
		return "\n";
	}//ok

	@Override
	public String newParagraph() {
		return newLine() + newLine();
	}

	@Override
	public String ruler() {
		return "----";
	}
	
	@Override
	public String listItem(String text) {
		return newLine() + "• " + text;
	}
	
	@Override
	public String link(String url, String text) {
		if(StringUtils.isBlank(text)) {
			text = url;
		}
		return "<" + url + "|" + text + ">";
	}
	
	@Override
	public String link(String url) {
		return link(url, url);
	}

	@Override
	public String image(String path, String alternativeText, String height, String width, String title) {
		Map<String, String> options = new HashMap<>();
		return image(path, options);
	}

	@Override
	public String image(String path, Map<String, String> options) {
		return path;
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
