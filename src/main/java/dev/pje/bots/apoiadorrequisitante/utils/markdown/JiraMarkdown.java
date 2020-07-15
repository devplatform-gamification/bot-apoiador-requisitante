package dev.pje.bots.apoiadorrequisitante.utils.markdown;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

public class JiraMarkdown implements MarkdownInterface{

	@Override
	public String normal(String text) {
		return text;
	}

	@Override
	public String head1(String text) {
		return newLine() + "h1. " + text + newLine();
	}

	@Override
	public String head2(String text) {
		return newLine() + "h2. " + text + newLine();
	}

	@Override
	public String head3(String text) {
		return newLine() + "h3. " + text + newLine();
	}

	@Override
	public String head4(String text) {
		return newLine() + "h4. " + text + newLine();
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
		return "{code}" + text + "{code}" + newLine();
	}

	@Override
	public String code(String text, String language) {
		return "{code:language=" + language +"}" + text + "{code}" + newLine();
	}

	@Override
	public String code(String text, String language, String title) {
		return "{code:language=" + language +"|title=" + title + "}" + text + "{code}" + newLine();
	}
	
	@Override
	public String underline(String text) {
		return "+" + text + "+";
	}

	@Override
	public String strike(String text) {
		return "-" + text + "-";
	}
	
	@Override
	public String citation(String text) {
		return "??" + text + "??";
	}

	@Override
	public String highlight(String text) {
		return block(text);
	}

	@Override
	public String quote(String text) {
		return "{quote}" + text + "{quote}" + newLine();
	}

	@Override
	public String quote(String text, String author, String reference) {
		return quote(text) + newLine() + citation(author) + newLine() + italic(reference) + newLine();
	}

	@Override
	public String block(String text) {
		return "{panel}" + text + "{panel}" + newLine();
	}

	@Override
	public String block(String title, String text) {
		return "{panel:title=" + title + "}" + text + "{panel}" + newLine();
	}
	
	@Override
	public String color(String text, String color) {
		return "{color:" + color + "}" + text + "{color}";
	}

	@Override
	public String newLine() {
		return "\n";
	}

	@Override
	public String ruler() {
		return "----";
	}
	
	@Override
	public String paragraph(String text) {
		return "\n" + text;
	}

	@Override
	public String link(String url, String text) {
		if(StringUtils.isBlank(text)) {
			text = url;
		}
		return "[" + text + "|" + url + "]";
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
		List<String> imageOptionsList = new ArrayList<>();
		for (String key : options.keySet()) {
			String option = key;
			if(!key.equalsIgnoreCase("thumbnail")) {
				option += "=" + options.get(key);
			}
			imageOptionsList.add(option);
		}
		String imageOptions = String.join("|", imageOptionsList);
		if(StringUtils.isNotBlank(imageOptions)) {
			imageOptions = "|" + imageOptions;
		}
		return "!" + path + imageOptions + "!";
	}

	@Override
	public String substitution(String text) {
		return text;
	}

	@Override
	public String firstPlaceIco() {
		return "(*y)";
	}

	@Override
	public String secondPlaceIco() {
		return "(*b)";
	}

	@Override
	public String thirdPlaceIco() {
		return "(*g)";
	}

	@Override
	public String MVPIco() {
		return " (y)";
	}
}
