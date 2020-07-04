package dev.pje.bots.apoiadorrequisitante.utils;

import java.io.StringReader;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

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

	public static String getVersionFromPomXML(String pomxml) {
		String version = "";
		try {
	        DocumentBuilderFactory dbf =
	            DocumentBuilderFactory.newInstance();
	        DocumentBuilder db = dbf.newDocumentBuilder();
	        InputSource is = new InputSource();
	        is.setCharacterStream(new StringReader(pomxml));
	
	        Document doc = db.parse(is);
	        NodeList project = doc.getElementsByTagName("project");
	        
	        if(project.getLength() > 0) {
	        	Element element = (Element) project.item(0);
	        	NodeList versionNode = element.getElementsByTagName("version");
	        	if(versionNode.getLength() > 0) {
		        	Element line = (Element) versionNode.item(0);
		        	version = getCharacterDataFromElement(line);
	        	}
	        }
	    }
	    catch (Exception e) {
	        e.printStackTrace();
	    }
		return version;
	}
	
	public static String getCharacterDataFromElement(Element e) {
	    Node child = e.getFirstChild();
	    if (child instanceof CharacterData) {
	       CharacterData cd = (CharacterData) child;
	       return cd.getData();
	    }
	    return "?";
	  }
}
