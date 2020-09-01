package dev.pje.bots.apoiadorrequisitante.utils.textModels;


import java.util.List;

import org.springframework.stereotype.Component;

import com.devplatform.model.bot.ProcessingMessage;
import com.devplatform.model.bot.ProcessingTypeEnum;

import dev.pje.bots.apoiadorrequisitante.utils.markdown.MarkdownInterface;

@Component
public class MessagesTextModel extends AbstractTextModel{
	
	private List<ProcessingMessage> messages;
	
	public MessagesTextModel(List<ProcessingMessage> messages) {
//		super(markdown);
		this.messages = messages;
	}
	
	public String convert(MarkdownInterface markdown) {
		StringBuilder markdownText = new StringBuilder();
		if(messages != null && !messages.isEmpty()) {
			for (ProcessingMessage processingMessage : messages) {
				String dateTime = processingMessage.getDateTime();
				String type = null;
				String text = markdown.normal(processingMessage.getText());
				if(ProcessingTypeEnum.ERROR.equals(processingMessage.getType())) {
					type = markdown.error(processingMessage.getType().toString());
				}else if(ProcessingTypeEnum.WARNING.equals(processingMessage.getType())) {
					type = markdown.warning(processingMessage.getType().toString());
				}else {
					type = markdown.info(processingMessage.getType().toString());
				}
				markdownText.append(markdown.listItem(dateTime + " |" + type + "| --- " + text + markdown.newLine()));
			}
		}
		
		return markdownText.toString();
	}
}
