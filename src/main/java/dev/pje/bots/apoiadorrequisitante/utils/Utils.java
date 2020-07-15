package dev.pje.bots.apoiadorrequisitante.utils;

import java.io.IOException;
import java.io.StringReader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
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

import com.devplatform.model.jira.JiraVersionReleaseNotes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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

	public static String calculateNextOrdinaryVersion(String currentVersion, int changeIndex) {
		String[] currentVersionArr = currentVersion.split("\\.");
		String nextVersion = "";
		
		for(int i=0; i < currentVersionArr.length && i < changeIndex; i++) {
			if(i > 0) {
				nextVersion += ".";
			}
			nextVersion += currentVersionArr[i];
		}
		
		if(changeIndex > 0) {
			nextVersion += ".";
		}
		Integer changedItem = 0;
		if(currentVersionArr.length >= changeIndex) {
			String changedItemStr = currentVersionArr[changeIndex];
			changedItem = Integer.valueOf(changedItemStr) + 1;
		}
		nextVersion += changedItem.toString();
		
		switch (changeIndex) {
		case 0:
			nextVersion += ".0.0.0";
			break;
		case 1:
			nextVersion += ".0.0";
			break;
		case 2:
			nextVersion += ".0";
			break;
		}
		
		return nextVersion;
	}
	
	public static String convertObjectToJson(Object obj) {
		ObjectMapper mapper = new ObjectMapper(); 
		String jsonStr = null;
        try { 
            jsonStr = mapper.writeValueAsString(obj); 
        }catch (IOException e) { 
            e.printStackTrace(); 
        }
        return jsonStr;
	}
	
	public static JiraVersionReleaseNotes convertJsonToJiraReleaseNotes(String jsonString) {
		JiraVersionReleaseNotes  obj = null;
		try {
			obj = new ObjectMapper().readValue(jsonString, JiraVersionReleaseNotes.class);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		return obj;
	}
	
	public static String cleanSummary(String summary) {
		String summaryCleaned = summary.replaceAll("\\[.*\\]", "").replaceAll("^[ ]*\\-", "").trim();
		if(StringUtils.isNotBlank(summaryCleaned)) {
			summaryCleaned = summaryCleaned.substring(0, 1).toUpperCase() + summaryCleaned.substring(1);
		}
		
		return summaryCleaned;
	}
	
	public static Date stringToDate(String stringDate, String pattern) throws ParseException {
		DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
		if(StringUtils.isNotBlank(pattern)) {
			formatter = DateTimeFormatter.ofPattern(pattern);
		}
		ZonedDateTime zonedDateTime = ZonedDateTime.parse(stringDate, formatter);
		
		return Date.from(zonedDateTime.toInstant());
	}
	
	public static String dateToStringPattern(Date date, String pattern) {
		DateFormat df = new SimpleDateFormat(pattern);
		return df.format(date);
	}
}
