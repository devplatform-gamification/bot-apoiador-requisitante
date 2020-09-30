package dev.pje.bots.apoiadorrequisitante.utils;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.UriUtils;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.devplatform.model.bot.VersionReleaseNotes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.pje.bots.apoiadorrequisitante.services.GitlabService;
import dev.pje.bots.apoiadorrequisitante.services.JiraService;

public class Utils {
	public static final String DATE_PATTERN_SIMPLE = "yyyy-MM-dd";
	public static final String DATE_PATTERN_PORTUGUESE = "HH:mm dd/MM/yyyy";
	
	public static boolean compareAsciiIgnoreCase(String valueA, String valueB) {
		if((StringUtils.isNotBlank(valueA) && StringUtils.isBlank(valueB)) || (StringUtils.isBlank(valueA) && StringUtils.isNotBlank(valueB))) {
			return false;
		}else if(StringUtils.isBlank(valueA) && StringUtils.isBlank(valueB)) {
			return true;
		}
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

	public static List<String> getValuesOnlyInA(List<String> listA, List<String> listB) {
		List<String> valuesOnlyInA = new ArrayList<>();
		if(listA != null && listB != null) {
			for (String itemA : listA) {
				if(!listB.contains(itemA)) {
					valuesOnlyInA.add(itemA);
				}
			}
		}else if(listB == null) {
			valuesOnlyInA = listA;
		}
		return valuesOnlyInA;
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

	/**
	 * Changes the version number index
	 * 
	 * @param currentVersion
	 * @param changeIndex
	 * @param versionNumDigits
	 * @return
	 */
	public static String calculateNextOrdinaryVersion(String currentVersion, int changeIndex, int numDigits) {
		String[] currentVersionArr = currentVersion.split("\\.");
		List<String> nextVersionList = new ArrayList<>();
		
		String itemToChange = "0";
		for(int i=0; i < currentVersionArr.length; i++) {
			if(i == changeIndex) {
				itemToChange = currentVersionArr[i];
				break;
			}else {
				nextVersionList.add(currentVersionArr[i]);
			}
		}

		if(StringUtils.isNotBlank(itemToChange) && StringUtils.isNumeric(itemToChange)) {
			Integer changedItem = Integer.valueOf(itemToChange) + 1;
			nextVersionList.add(changedItem.toString());
		}
		nextVersionList.addAll(getListOfZeroDigits(numDigits - nextVersionList.size()));
		String nextVersion = String.join(".", nextVersionList);
		
		return nextVersion;
	}

	private static List<String> getListOfZeroDigits(int numDigits) {
		List<String> zeroDigits = new ArrayList<>();
		for(int i=0; i < numDigits; i++) {
			zeroDigits.add("0");
		}
		return zeroDigits;
	}
	
	public static String clearVersionNumber(String version) {
		return version.replaceAll("\\-SNAPSHOT", "").replaceAll("\\-RELEASE-CANDIDATE", "");
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
	
	public static VersionReleaseNotes convertJsonToJiraReleaseNotes(String jsonString) {
		return convertJsonToObject(jsonString, VersionReleaseNotes.class);
	}

	public static <T> T convertJsonToObject(String jsonString, Class<T> valueType) {
		try {
			return (T) new ObjectMapper().readValue(jsonString, valueType);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static String clearSummary(String summary) {
		String summaryCleaned = summary.replaceAll("\\[[^\\]]*\\]", "").replaceAll("^[ ]*\\-", "").trim();
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
	
	public static Date getDateFromString(String releaseDateStr) {
		Date date = null;
		try {
			date = Utils.stringToDate(releaseDateStr, null);
		}catch (Exception e) {
			try {
				date = Utils.stringToDate(releaseDateStr, JiraService.JIRA_DATETIME_PATTERN);
			}catch (Exception e1) {
				try {
					date = Utils.stringToDate(releaseDateStr, GitlabService.GITLAB_DATETIME_PATTERN);
				}catch (Exception e2) {
					try {
						date = Utils.stringToDate(releaseDateStr, DATE_PATTERN_SIMPLE);						
					}catch (Exception e3) {
						e3.getStackTrace();
					}
				}
			}
		}
		return date;
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
	
	public static String normalizePaths(String filePath) {
		String normalizedPath = null;
		
		if(StringUtils.isNotBlank(filePath)) {
			String changedPath = filePath;
			while(!changedPath.equals(normalizedPath)) {
				normalizedPath = changedPath;
				changedPath = changedPath.replaceAll("//", "/");
			}
		}
		
		return normalizedPath;
	}

	public static String urlEncode(String text) throws UnsupportedEncodingException {
		String urlEncoded = null;
		if(StringUtils.isNotEmpty(text)) {
			String[] paths = text.split("/");
			List<String> pathsList = new ArrayList<>();
			if(paths != null && paths.length > 0) {
				for (String path : paths) {
					String pathEncoded = UriUtils.encodePath(path, StandardCharsets.UTF_8.toString());
					pathsList.add(pathEncoded);
				}
			}
			String separator = URLEncoder.encode("/", StandardCharsets.UTF_8.toString());
			urlEncoded = String.join(separator, pathsList);
		}
		return urlEncoded;
	}
	
	public static String escapeGitlabMarkup(String text) throws UnsupportedEncodingException {
		String transformedText = text;
		if(StringUtils.isNotBlank(text)) {
			transformedText = text.replaceAll(" ", urlEncode(" "));
		}
		return transformedText;
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

		Pattern pattern = Pattern.compile("([A-Za-z]+\\-[0-9]+)");

        Matcher matcher = pattern.matcher(commitMessage);
        while(matcher.find()) {
        	String k = matcher.group(1);
    		issueKeys.add(k);
    		if(StringUtils.isBlank(issueKey)) {// está recuperando a primeira issue encontrada em caso de haver mais de uma
    			issueKey = k;
    		}
        }
        
        return issueKey;
	}
	
	public static String getPathFromAsciidocLink(String adocLink) {
		String path = null;
		
        Pattern pattern = Pattern.compile("link:([\\w\\-_\\s/\\d]+.html)\\[.*\\]");
        path = pattern.matcher(adocLink).replaceAll("$1");
        
        return path;
	}
	
	public static void waitSeconds(Integer numSeconds) {
		try {
			TimeUnit.SECONDS.sleep(numSeconds);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	public static boolean isNumeric(String strNum) {
	    if (StringUtils.isBlank(strNum)) {
	        return false;
	    }
	    try {
	        Integer.valueOf(strNum);
	    } catch (NumberFormatException nfe) {
	        return false;
	    }
	    return true;
	}
	
	public static String addOption(String actualContent, String newOption) {
		List<String> newContentOptions = new ArrayList<>();
		boolean isDuplicate = false;
		if(StringUtils.isNotBlank(actualContent)) {
			String[] actualOptions = actualContent.split(",");
			for (String option: actualOptions) {
				if(StringUtils.isNotBlank(option) && !newContentOptions.contains(option)) {
					newContentOptions.add(option.trim());
					if(!isDuplicate && StringUtils.isNotBlank(newOption) && Utils.compareAsciiIgnoreCase(option, newOption)) {
						isDuplicate = true;
					}
				}
			}
		}
		if(!isDuplicate && StringUtils.isNotBlank(newOption)) {
			newContentOptions.add(newOption.trim());
		}
		return String.join(", ", newContentOptions);
	}
	
	public static String textToBase64(String text) {
		String textEncoded = byteArrToBase64(text.getBytes());
		return textEncoded;
	}
	
	public static String byteArrToBase64(byte[] data) {
		String textEncoded = Base64.getEncoder().encodeToString(data);
		return textEncoded;
	}

	public static String decodeFromBase64(String textEndoded) {
		byte[] decodedBytes = Base64
				.getDecoder()
				.decode(textEndoded);
		return new String(decodedBytes);
	}
	
	public static void writeByteArrayIntoFile(byte[] data) {
		String outputFilePath = "/tmp/saida.png";
		File outputFile = new File(outputFilePath);

		try {
			FileUtils.writeByteArrayToFile(outputFile, data);
			System.out.println("Criou o arquivo: "+ outputFilePath);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static String getElementFromXML(String xml, String tagPath) {
		String elementValue = "";
		try {
	        DocumentBuilderFactory dbf =
	            DocumentBuilderFactory.newInstance();
	        DocumentBuilder db = dbf.newDocumentBuilder();
	        InputSource is = new InputSource();
	        is.setCharacterStream(new StringReader(xml));
	
	        Document doc = db.parse(is);
	        
	        XPathFactory xpathFactory = XPathFactory.newInstance();
	        XPath xpath = xpathFactory.newXPath();
	        
	        if(StringUtils.isNotBlank(tagPath)) {
	        	NodeList nodeList = (NodeList) xpath.evaluate(tagPath, doc,
	        			XPathConstants.NODESET);	        	

	        	if(nodeList.getLength() > 0) {
	        		Element line = (Element) nodeList.item(0);
	        		elementValue = getCharacterDataFromElement(line);	        	
	        	}
	        }
	    }
	    catch (Exception e) {
	        e.printStackTrace();
	    }
		return elementValue;
	}
	
	public static String getCharacterDataFromElement(Element e) {
		Node child = e.getFirstChild();
		if (child instanceof CharacterData) {
			CharacterData cd = (CharacterData) child;
			return cd.getData();
		}
		return "?";
	}
	
	public static String changeElementValueFromXML(String xml, String tagPath, String actualValue, String newValue) {
		String xmlProcessed = xml;
		if(StringUtils.isNotBlank(actualValue) && StringUtils.isNotBlank(newValue) && !actualValue.equals(newValue)) {
			try {
				DocumentBuilderFactory dbf =
						DocumentBuilderFactory.newInstance();
				DocumentBuilder db = dbf.newDocumentBuilder();
				InputSource is = new InputSource();
				is.setCharacterStream(new StringReader(xml));
				
				Document doc = db.parse(is);
				
				XPathFactory xpathFactory = XPathFactory.newInstance();
				XPath xpath = xpathFactory.newXPath();
				
				if(StringUtils.isNotBlank(tagPath)) {
					NodeList nodeList = (NodeList) xpath.evaluate(tagPath, doc,
							XPathConstants.NODESET);
					
					for (int i=0; i < nodeList.getLength(); i++) {
						Node node = (Node) nodeList.item(i);
						if(node != null && node.getChildNodes() != null && node.getChildNodes().getLength() > 0 
								&& actualValue.equals(node.getChildNodes().item(0).getTextContent())) {
							node.setTextContent(newValue);
							break;
						}
					}
				}
				
		       TransformerFactory transformerFactory = TransformerFactory.newInstance();
		       Transformer transformer = transformerFactory.newTransformer();
		       DOMSource source = new DOMSource(doc);
		       StringWriter sw = new StringWriter();
		       StreamResult result = new StreamResult(sw);
		       transformer.transform(source, result);
		       xmlProcessed = sw.toString();
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		return xmlProcessed;
	}

	public static long checkDifferenceInHoursBetweenTwoDates(Date firstDate, Date secondDate) {
	    long diffInMillies = Math.subtractExact(firstDate.getTime(), secondDate.getTime());
	    long diff = TimeUnit.HOURS.convert(diffInMillies, TimeUnit.MILLISECONDS);
	    
	    return diff;
	}
	
	public static long checkDifferenceInDaysBetweenTwoDates(Date firstDate, Date secondDate) {
	    long diffInMillies = Math.subtractExact(firstDate.getTime(), secondDate.getTime());
	    long diff = TimeUnit.DAYS.convert(diffInMillies, TimeUnit.MILLISECONDS);
	    
	    return diff;
	}
	
	public static Date calculateDaysFromNow(long daysToAdd) {
		LocalDate now = LocalDate.now();
		LocalDate pretendedDay = now.plusDays(daysToAdd);
		
	    return java.util.Date.from(pretendedDay.atStartOfDay()
	    	      .atZone(ZoneId.systemDefault())
	    	      .toInstant());
	}
	
	public static String doubleToStringAsPercent(Double percent) {
		NumberFormat percentFormatter;
		String percentOut;

		percentFormatter = NumberFormat.getPercentInstance();
		percentOut = percentFormatter.format(percent);
		return percentOut;
	}
	
	public static String concatenaItensUnicosStrings(String stringA, String stringB) {
		if(StringUtils.isBlank(stringA)) {
			stringA = stringB;
		}else  if(!stringA.contains(stringB)) {
			stringA += ", " + stringB;
		}
		
		return stringA;
	}
}
	