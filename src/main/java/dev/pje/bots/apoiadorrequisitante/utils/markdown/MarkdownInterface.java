package dev.pje.bots.apoiadorrequisitante.utils.markdown;

import java.util.Map;

public interface MarkdownInterface {

	public String getName();
	
	public String normal(String text);
	
	public String head1(String text);
	
	public String head2(String text);
	
	public String head3(String text);
	
	public String head4(String text);

	public String bold(String text);
	
	public String italic(String text);
	
	public String code(String text);

	public String code(String text, String language);

	public String code(String text, String language, String title);

	public String underline(String text);

	public String strike(String text);
	
	public String citation(String text);

	public String highlight(String text);
	
	public String quote(String text);

	public String quote(String text, String author, String reference);
	
	public String block(String text);

	public String block(String title, String text);
	
	public String color(String text, String color);

	public String newLine();

	public String ruler();
	
	public String listItem(String text);
	
	public String paragraph(String text);

	public String link(String url, String text);

	public String link(String url);
	
	public String image(String path, String alternativeText, String height, String width, String title);

	public String image(String path, Map<String, String> options);

	public String substitution(String text);
	
	public String firstPlaceIco();

	public String secondPlaceIco();

	public String thirdPlaceIco();

	public String MVPIco();

}
