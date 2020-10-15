package dev.pje.bots.apoiadorrequisitante.utils.textModels;

import dev.pje.bots.apoiadorrequisitante.utils.markdown.MarkdownInterface;

public interface AbstractTextModel {
	public String convert(MarkdownInterface markdown);
}
