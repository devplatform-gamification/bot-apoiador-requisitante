package dev.pje.bots.apoiadorrequisitante.utils.textModels;

import dev.pje.bots.apoiadorrequisitante.utils.markdown.MarkdownInterface;

abstract class AbstractTextModel {
	abstract public String convert(MarkdownInterface markdown);
}
