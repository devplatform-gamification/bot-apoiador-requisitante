package dev.pje.bots.apoiadorrequisitante.utils;

import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
	
	public static String changePomXMLVersion(String actualVersion, String newVersion, String pomxml) {
		String newContent = pomxml.replaceAll("(<version>)" + actualVersion + "(</version>)", "$1"+ newVersion +"$2");
		return newContent;
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
	
	public static String getPathFromFilePath(String filePath) {
		String[] scriptPath = filePath.split("/");
		List<String> dirs = new ArrayList<String>();
		if(scriptPath.length > 0) {
			for (int i=0; (i < (scriptPath.length - 1)); i++) {
				dirs.add(scriptPath[i]);
			}
		}
		return String.join("/", dirs);
	}

	public static String getFileNameFromFilePath(String filePath) {
		String[] scriptPath = filePath.split("/");
		String fileName = "";
		if(scriptPath.length > 0) {
			fileName = scriptPath[scriptPath.length - 1];
		}
		return fileName;
	}
	
	public static String urlEncode(String text) throws UnsupportedEncodingException {
		return URLEncoder.encode(text, StandardCharsets.UTF_8.toString());
	}
	
	public static int compareVersionsDesc(List<Integer> versionNumbersA, List<Integer> versionNumbersB) {
		int diff = 0;
		if(versionNumbersA != null && versionNumbersB != null) {
			for (int i=0; i < versionNumbersA.size(); i++) {
				if(i < versionNumbersB.size()) {
					diff = (versionNumbersA.get(i) - versionNumbersB.get(i));
				}else {
					diff = versionNumbersA.get(i) - 0; // versionB will be considered 0 in this case
				}
				if(diff != 0) {
					break;
				}
			}
		}
		return (-1) * diff; // order DESC
	}

	public static List<Integer> getVersionFromString(String version){
		return getVersionFromString(version, "\\.");
	}
	
	public static List<Integer> getVersionFromString(String version, String delimiter){
		List<Integer> versionNumbers = new ArrayList<>();
		boolean isValid = false;
		if(version != null && !version.isEmpty()) {
			String[] versionParts = version.split(delimiter);
			for(int i=0; i < versionParts.length; i++) {
				if(!StringUtils.isNumericSpace(versionParts[i])) {
					isValid = false;
					break;
				}
				versionNumbers.add(Integer.valueOf(versionParts[i]));
				isValid = true;
			}
		}
		return isValid ? versionNumbers : null;
	}
	
	public static String getIssueKeyFromCommitMessage(String commitMessage) {
		String issueKey = null;
		List<String> issueKeys = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\[([A-Za-z]+\\-[0-9]+)\\]");

        Matcher matcher = pattern.matcher(commitMessage);
        while(matcher.find()) {
        	String k = matcher.group();
        	issueKeys.add(k);
        	if(StringUtils.isBlank(issueKey)) {
        		issueKey = k;
        	}
        }
        
        return issueKey;
	}
}
	