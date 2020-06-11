package dev.pje.bots.apoiadorrequisitante.utils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

public class Utils {
	public static boolean compareAsciiIgnoreCase(String valueA, String valueB) {
	    String valueAprepared = StringUtils.stripAccents(valueA);
	    String valueBprepared = StringUtils.stripAccents(valueB);
	    
	    return valueAprepared.equalsIgnoreCase(valueBprepared);
	}

	public static boolean compareListValues(List<String> listA, List<String> listB) {
		if(listA != null && listB != null) {
			return listA.containsAll(listB) && listA.size() == listB.size();
		}
		return (listA == null && listB == null);
	}
	
	public static String escapeTelegramMarkup(String text) {
		Pattern regexCharacters = Pattern.compile("[(_\\*\\[\\]\\(\\)\\~`\\>\\#\\+\\-\\=\\|\\{\\}\\.\\!)?*+.]");
		return replaceTokens(text, regexCharacters);
	}
	
	public static String replaceTokens(String original, Pattern regexCharacters) {
		int lastIndex = 0;
		StringBuilder output = new StringBuilder();
		Matcher matcher = regexCharacters.matcher(original);
		while (matcher.find()) {
		    output.append(original, lastIndex, matcher.start())
		      .append("\\" + matcher.group(0));
		 
		    lastIndex = matcher.end();
		}
		if (lastIndex < original.length()) {
		    output.append(original, lastIndex, original.length());
		}
		return output.toString();
	}

}
